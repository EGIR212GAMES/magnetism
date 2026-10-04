package ru.magnetism.magnet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import ru.magnetism.config.ModConfig;
import ru.magnetism.server.ServerTickManager;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Applies magnet forces to players and dropped items.
 *
 * <p>Why this class looks the way it does:
 * <ul>
 *   <li><b>Players</b> are simulated by their own client. Changing the server-side
 *       delta movement of a player does nothing unless the new velocity is sent to
 *       that client, which is what {@link Entity#hurtMarked} triggers (the same
 *       mechanism vanilla knockback uses). The server also does not track a
 *       player's real velocity, so it is estimated from the position change of the
 *       last tick.</li>
 *   <li><b>Item entities</b> have a very long vanilla tracking interval, so without
 *       forcing a sync every tick the client simulates the item alone and gets
 *       snapped to the server position once per second, which looks jerky.
 *       Setting {@code hurtMarked} sends the new velocity every tick, so client
 *       and server run the same physics.</li>
 *   <li>The magnet is ticked <b>every tick</b> (not every 2nd). Applying the force
 *       every other tick made velocity oscillate (push, coast, push).</li>
 *   <li>The vertical pull cancels gravity, otherwise a pull weaker than gravity
 *       (0.04 for items, 0.08 for living entities per tick) can never lift anything.</li>
 * </ul>
 */
public final class MagnetSystem {
    /** Vanilla per-tick gravity of item entities. */
    private static final double ITEM_GRAVITY = 0.04D;
    /** Vanilla per-tick gravity of players. */
    private static final double LIVING_GRAVITY = 0.08D;
    /** Vanilla per-tick gravity of minecarts. */
    private static final double MINECART_GRAVITY = 0.04D;
    /** Vanilla vertical drag applied after gravity. */
    private static final double VERTICAL_DRAG = 0.98D;
    /** Default block friction 0.6 times the 0.91 air multiplier used by living entities. */
    private static final double GROUND_HORIZONTAL_FRICTION = 0.546D;
    private static final double AIR_HORIZONTAL_FRICTION = 0.91D;
    /** A position jump larger than this in one tick is a teleport, not movement. */
    private static final double MAX_PLAUSIBLE_STEP = 2.5D;
    /** Blend factor of the newest movement sample (reduces packet-timing noise). */
    private static final double MOTION_SMOOTHING = 0.6D;
    private static final int PURGE_INTERVAL_TICKS = 400;

    /** Server-thread only. Entries are dropped lazily once a player leaves every magnet field. */
    private static final Map<UUID, PlayerMotion> PLAYER_MOTION = new HashMap<>();
    private static long lastPurgeTick = Long.MIN_VALUE;

    private MagnetSystem() {
    }

    public static void applyAttraction(ServerLevel level, BlockPos pos, BlockState state) {
        ModConfig rootConfig = ModConfig.get();
        ModConfig.MagnetConfig config = rootConfig.magnet();
        if (!rootConfig.enabled() || !config.enabled()) {
            return;
        }

        long tick = ServerTickManager.getTickCount();
        purgeStaleMotion(tick);

        // ATTRACT pulls towards the magnet, REPEL pushes away from it.
        double polarity = state.hasProperty(MagnetBlock.POLARITY)
                && state.getValue(MagnetBlock.POLARITY) == MagnetPolarity.REPEL ? -1.0D : 1.0D;

        Vec3 magnetCenter = Vec3.atCenterOf(pos);
        AABB area = new AABB(pos).inflate(config.radius());

        for (Player player : level.getEntitiesOfClass(Player.class, area, Entity::isAlive)) {
            if (player.isSpectator() || player.isPassenger()) {
                continue;
            }
            double weight = MagneticWeightResolver.playerWeight(player);
            if (weight <= 0.0D) {
                continue;
            }
            Vec3 acceleration = computeAcceleration(player, magnetCenter, polarity, weight, LIVING_GRAVITY, config);
            if (acceleration != null && canReach(level, pos, magnetCenter, player, config)) {
                pushPlayer(player, acceleration, fieldStrength(player, magnetCenter, config), tick, config);
            }
        }

        for (ItemEntity itemEntity : level.getEntitiesOfClass(
                ItemEntity.class,
                area,
                entity -> entity.isAlive() && !entity.getItem().isEmpty())) {
            double weight = MagneticWeightResolver.itemEntityWeight(itemEntity.getItem());
            if (weight <= 0.0D) {
                continue;
            }
            Vec3 acceleration = computeAcceleration(itemEntity, magnetCenter, polarity, weight, ITEM_GRAVITY, config);
            if (acceleration != null && canReach(level, pos, magnetCenter, itemEntity, config)) {
                pushItem(itemEntity, acceleration, fieldStrength(itemEntity, magnetCenter, config), config);
            }
        }

        // All minecart types (tag magnetism:magnetic_minecarts), found by entity type so no
        // minecart class has to be referenced.
        for (Entity cart : level.getEntities(
                (Entity) null,
                area,
                entity -> entity.isAlive() && isMagneticMinecart(entity))) {
            if (canReach(level, pos, magnetCenter, cart, config)) {
                pushMinecart(level, cart, magnetCenter, polarity, config);
            }
        }
    }

    /** Tag lookup through the registry holder: EntityType has no direct is(TagKey) in 26.2. */
    private static boolean isMagneticMinecart(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType()).is(MagnetTags.MAGNETIC_MINECARTS);
    }

    // ---------------------------------------------------------------- line of sight

    /**
     * The Magnet only works on what it can "see": if any block with a collision shape lies on the
     * straight line between the magnet and the target, the magnet does nothing to it.
     * Two rays are tried (body centre, then the top of the hitbox) so that a target is not
     * rejected just because its centre is clipped by a block edge; a wall or floor still blocks both.
     */
    private static boolean canReach(ServerLevel level,
                                    BlockPos magnetPos,
                                    Vec3 magnetCenter,
                                    Entity target,
                                    ModConfig.MagnetConfig config) {
        if (!config.requireLineOfSight()) {
            return true;
        }
        AABB box = target.getBoundingBox();
        Vec3 center = box.getCenter();
        if (!isBlocked(level, magnetPos, magnetCenter, center)) {
            return true;
        }
        Vec3 top = new Vec3(center.x, box.maxY - 0.05D, center.z);
        return !isBlocked(level, magnetPos, magnetCenter, top);
    }

    /**
     * Walks the grid cells crossed by the segment {@code from -> to} (voxel traversal) and tests
     * each cell's collision shape against the segment. The magnet's own cell is ignored.
     * Uses only getBlockState / getCollisionShape / VoxelShape.clip, so partial blocks
     * (slabs, fences...) are handled exactly and non-solid blocks (rails, grass, open doors)
     * never block.
     */
    private static boolean isBlocked(ServerLevel level, BlockPos magnetPos, Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;

        int x = (int) Math.floor(from.x);
        int y = (int) Math.floor(from.y);
        int z = (int) Math.floor(from.z);
        int endX = (int) Math.floor(to.x);
        int endY = (int) Math.floor(to.y);
        int endZ = (int) Math.floor(to.z);

        int stepX = Integer.signum((int) Math.signum(dx));
        int stepY = Integer.signum((int) Math.signum(dy));
        int stepZ = Integer.signum((int) Math.signum(dz));

        double tDeltaX = dx == 0.0D ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dx);
        double tDeltaY = dy == 0.0D ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dy);
        double tDeltaZ = dz == 0.0D ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dz);
        double tMaxX = dx == 0.0D ? Double.POSITIVE_INFINITY
                : (stepX > 0 ? (x + 1 - from.x) : (from.x - x)) * tDeltaX;
        double tMaxY = dy == 0.0D ? Double.POSITIVE_INFINITY
                : (stepY > 0 ? (y + 1 - from.y) : (from.y - y)) * tDeltaY;
        double tMaxZ = dz == 0.0D ? Double.POSITIVE_INFINITY
                : (stepZ > 0 ? (z + 1 - from.z) : (from.z - z)) * tDeltaZ;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int maxSteps = Math.abs(endX - x) + Math.abs(endY - y) + Math.abs(endZ - z) + 1;

        for (int i = 0; i < maxSteps; i++) {
            cursor.set(x, y, z);
            if (!(x == magnetPos.getX() && y == magnetPos.getY() && z == magnetPos.getZ())) {
                BlockState state = level.getBlockState(cursor);
                if (!state.isAir()) {
                    VoxelShape shape = state.getCollisionShape(level, cursor);
                    if (!shape.isEmpty() && shape.clip(from, to, cursor) != null) {
                        return true;
                    }
                }
            }

            if (x == endX && y == endY && z == endZ) {
                break;
            }

            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- force model

    /** 0..1 field strength: smooth fall-off from the magnet to the edge of its radius. */
    private static double fieldStrength(Entity entity, Vec3 magnetCenter, ModConfig.MagnetConfig config) {
        double distance = entity.getBoundingBox().getCenter().distanceTo(magnetCenter);
        double radius = config.radius();
        if (!Double.isFinite(distance) || distance >= radius) {
            return 0.0D;
        }
        return Math.pow(Math.max(0.0D, 1.0D - distance / radius), config.falloffExponent());
    }

    /**
     * Returns the acceleration (blocks/tick^2) this magnet exerts on the entity this tick,
     * or {@code null} if the entity is out of range or too close to the magnet centre.
     */
    private static Vec3 computeAcceleration(Entity entity,
                                            Vec3 magnetCenter,
                                            double polarity,
                                            double magneticWeight,
                                            double gravity,
                                            ModConfig.MagnetConfig config) {
        // Use the body centre, not the feet: otherwise a magnet at head height pulls "down".
        Vec3 delta = magnetCenter.subtract(entity.getBoundingBox().getCenter());
        double distanceSqr = delta.lengthSqr();
        double minDistance = config.minimumDistance();
        if (!Double.isFinite(distanceSqr) || distanceSqr <= minDistance * minDistance) {
            return null;
        }

        double distance = Math.sqrt(distanceSqr);
        double radius = config.radius();
        if (distance >= radius) {
            return null;
        }

        double field = Math.pow(Math.max(0.0D, 1.0D - distance / radius), config.falloffExponent());

        // Soften the pull right next to the block so entities settle on its face instead of vibrating.
        double near = clamp((distance - 0.5D) / 1.0D, 0.0D, 1.0D);
        near = near * near * (3.0D - 2.0D * near);
        double nearScale = 0.35D + 0.65D * near;

        double strength = config.baseStrength() * magneticWeight * field * nearScale;
        if (strength <= 0.0D) {
            return null;
        }

        double inv = polarity / distance;
        double dirX = delta.x * inv;
        double dirY = delta.y * inv;
        double dirZ = delta.z * inv;

        // Extra vertical authority when moving up.
        double upBoost = dirY > 0.0D ? config.verticalForceMultiplier() : 1.0D;
        Vec3 pull = new Vec3(dirX * strength, dirY * strength * upBoost, dirZ * strength);

        double pullLength = pull.length();
        double maxAcceleration = config.maxAccelerationPerUpdate();
        if (pullLength > maxAcceleration && pullLength > 0.0D) {
            pull = pull.scale(maxAcceleration / pullLength);
        }

        // Cancel gravity while being drawn upwards so that any non-zero pull actually lifts.
        double lift = 0.0D;
        if (dirY > 0.0D) {
            lift = gravity * config.gravityCompensation()
                    * Math.min(1.0D, dirY * 2.0D)
                    * Math.min(1.0D, field * 4.0D);
        }

        return pull.add(0.0D, lift, 0.0D);
    }

    // ---------------------------------------------------------------- items

    private static void pushItem(ItemEntity item, Vec3 acceleration, double field, ModConfig.MagnetConfig config) {
        Vec3 velocity = item.getDeltaMovement().scale(damping(field, config)).add(acceleration);
        velocity = limit(velocity, config.maxResultingVelocity());
        item.setDeltaMovement(velocity);
        // Send the velocity to clients every tick; see class comment.
        item.hurtMarked = true;
    }

    // ---------------------------------------------------------------- minecarts

    /**
     * On rails the cart may only be accelerated along the track: the pull is reduced to its
     * component along the rail direction, with no vertical force, so it can never lift the cart
     * off the rails. Once there is no rail under the cart (the track ended, the cart was
     * derailed, or it was never on rails) it is a free body and flies towards the magnet.
     */
    private static void pushMinecart(ServerLevel level,
                                     Entity cart,
                                     Vec3 magnetCenter,
                                     double polarity,
                                     ModConfig.MagnetConfig config) {
        RailShape shape = railShapeUnder(level, cart);

        if (shape == null) {
            Vec3 acceleration = computeAcceleration(
                    cart, magnetCenter, polarity, config.minecartFreeWeight(), MINECART_GRAVITY, config);
            if (acceleration == null) {
                return;
            }
            double field = fieldStrength(cart, magnetCenter, config);
            Vec3 velocity = cart.getDeltaMovement().scale(damping(field, config)).add(acceleration);
            cart.setDeltaMovement(limit(velocity, config.maxResultingVelocity()));
            cart.hurtMarked = true;
            return;
        }

        // gravity = 0: no lift on rails.
        Vec3 pull = computeAcceleration(cart, magnetCenter, polarity, config.minecartRailWeight(), 0.0D, config);
        if (pull == null) {
            return;
        }

        Vec3 along = alongRail(shape, pull);
        if (along.lengthSqr() > 0.0D) {
            // Vanilla re-projects the velocity onto the track every tick, so this speeds the
            // cart up or slows it down along the rails and cannot push it sideways.
            cart.setDeltaMovement(cart.getDeltaMovement().add(along));
        }
    }

    /** Rail shape of the rail the cart is on, or null if the cart is not on a rail. */
    private static RailShape railShapeUnder(ServerLevel level, Entity cart) {
        BlockPos pos = cart.blockPosition();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BaseRailBlock)) {
            // Only look one block down when the cart is hugging the bottom of its block
            // (sloped rails); otherwise a cart flying above some rail would count as "on rails".
            if (cart.getY() - pos.getY() > 0.3D) {
                return null;
            }
            state = level.getBlockState(pos.below());
            if (!(state.getBlock() instanceof BaseRailBlock)) {
                return null;
            }
        }
        BaseRailBlock rail = (BaseRailBlock) state.getBlock();
        return state.getValue(rail.getShapeProperty());
    }

    /**
     * Projects the horizontal pull onto the track. Each rail shape has two exits; the pull acts
     * towards whichever exit points closer to the pull direction, scaled by how aligned they are.
     */
    private static Vec3 alongRail(RailShape shape, Vec3 pull) {
        Vec3 exitA;
        Vec3 exitB;
        switch (shape) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> {
                exitA = new Vec3(0.0D, 0.0D, -1.0D);
                exitB = new Vec3(0.0D, 0.0D, 1.0D);
            }
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> {
                exitA = new Vec3(-1.0D, 0.0D, 0.0D);
                exitB = new Vec3(1.0D, 0.0D, 0.0D);
            }
            case SOUTH_EAST -> {
                exitA = new Vec3(0.0D, 0.0D, 1.0D);
                exitB = new Vec3(1.0D, 0.0D, 0.0D);
            }
            case SOUTH_WEST -> {
                exitA = new Vec3(0.0D, 0.0D, 1.0D);
                exitB = new Vec3(-1.0D, 0.0D, 0.0D);
            }
            case NORTH_WEST -> {
                exitA = new Vec3(0.0D, 0.0D, -1.0D);
                exitB = new Vec3(-1.0D, 0.0D, 0.0D);
            }
            default -> { // NORTH_EAST
                exitA = new Vec3(0.0D, 0.0D, -1.0D);
                exitB = new Vec3(1.0D, 0.0D, 0.0D);
            }
        }

        double dotA = pull.x * exitA.x + pull.z * exitA.z;
        double dotB = pull.x * exitB.x + pull.z * exitB.z;
        if (dotA >= dotB) {
            return dotA > 0.0D ? exitA.scale(dotA) : Vec3.ZERO;
        }
        return dotB > 0.0D ? exitB.scale(dotB) : Vec3.ZERO;
    }

    // ---------------------------------------------------------------- players

    private static void pushPlayer(Player player,
                                   Vec3 acceleration,
                                   double field,
                                   long tick,
                                   ModConfig.MagnetConfig config) {
        PlayerMotion motion = PLAYER_MOTION.computeIfAbsent(player.getUUID(), id -> new PlayerMotion());

        if (motion.tick != tick) {
            // First magnet touching this player this tick: sample the real movement once.
            motion.velocity = estimateVelocity(player, motion, tick);
            motion.accumulated = Vec3.ZERO;
            motion.tick = tick;
            motion.dampingApplied = false;
        }

        motion.accumulated = motion.accumulated.add(acceleration);
        Vec3 base = motion.velocity;
        if (!motion.dampingApplied) {
            motion.velocity = base.scale(damping(field, config));
            base = motion.velocity;
            motion.dampingApplied = true;
        }

        // Several magnets in range add their forces; the result is set once, idempotently.
        Vec3 velocity = limit(base.add(motion.accumulated), config.maxPlayerVelocity());
        player.setDeltaMovement(velocity);
        // Without this the client never learns about the new velocity and ignores the magnet.
        player.hurtMarked = true;
    }

    /**
     * The server does not know a player's real velocity (the client simulates movement), so it
     * is reconstructed from the distance travelled during the previous tick and converted from
     * "distance moved" to "velocity stored after this tick's friction/gravity".
     */
    private static Vec3 estimateVelocity(Player player, PlayerMotion motion, long tick) {
        Vec3 moved = new Vec3(
                player.getX() - player.xo,
                player.getY() - player.yo,
                player.getZ() - player.zo
        );

        if (!Double.isFinite(moved.lengthSqr()) || moved.length() > MAX_PLAUSIBLE_STEP) {
            moved = Vec3.ZERO; // teleport, respawn or dimension change
        }

        if (tick - motion.lastSampleTick == 1) {
            moved = motion.smoothedMovement.scale(1.0D - MOTION_SMOOTHING).add(moved.scale(MOTION_SMOOTHING));
        }
        motion.smoothedMovement = moved;
        motion.lastSampleTick = tick;

        boolean onGround = player.onGround();
        double friction = onGround ? GROUND_HORIZONTAL_FRICTION : AIR_HORIZONTAL_FRICTION;
        double vy = onGround ? 0.0D : (moved.y - LIVING_GRAVITY) * VERTICAL_DRAG;
        return new Vec3(moved.x * friction, vy, moved.z * friction);
    }

    private static void purgeStaleMotion(long tick) {
        if (lastPurgeTick != Long.MIN_VALUE && tick - lastPurgeTick < PURGE_INTERVAL_TICKS) {
            return;
        }
        lastPurgeTick = tick;
        Iterator<PlayerMotion> iterator = PLAYER_MOTION.values().iterator();
        while (iterator.hasNext()) {
            if (tick - iterator.next().tick > PURGE_INTERVAL_TICKS) {
                iterator.remove();
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private static double damping(double field, ModConfig.MagnetConfig config) {
        // Damping fades in with field strength, so entities outside the field are untouched.
        return 1.0D - (1.0D - config.velocityDamping()) * Math.min(1.0D, field * 2.0D);
    }

    private static Vec3 limit(Vec3 velocity, double max) {
        double length = velocity.length();
        if (!Double.isFinite(length)) {
            return Vec3.ZERO;
        }
        return length > max ? velocity.scale(max / length) : velocity;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class PlayerMotion {
        long tick = Long.MIN_VALUE;
        long lastSampleTick = Long.MIN_VALUE;
        Vec3 velocity = Vec3.ZERO;
        Vec3 accumulated = Vec3.ZERO;
        Vec3 smoothedMovement = Vec3.ZERO;
        boolean dampingApplied;
    }
}
