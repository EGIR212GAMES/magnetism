package ru.magnetism.magnet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.magnetism.config.ModConfig;

public final class MagnetSystem {
    private MagnetSystem() {
    }

    public static void applyAttraction(ServerLevel level, BlockPos pos, BlockState state) {
        ModConfig rootConfig = ModConfig.get();
        ModConfig.MagnetConfig config = rootConfig.magnet();
        if (!rootConfig.enabled() || !config.enabled()) {
            return;
        }

        Vec3 magnetCenter = Vec3.atCenterOf(pos);
        double radius = config.radius();
        AABB area = new AABB(pos).inflate(radius);

        for (Player player : level.getEntitiesOfClass(Player.class, area, Entity::isAlive)) {
            double weight = MagneticWeightResolver.playerWeight(player);
            if (weight <= 0.0D || player.isSpectator()) {
                continue;
            }

            applyForce(player, magnetCenter, state, weight, config);
        }

        for (ItemEntity itemEntity : level.getEntitiesOfClass(
                ItemEntity.class,
                area,
                entity -> entity.isAlive() && !entity.getItem().isEmpty())) {
            double weight = MagneticWeightResolver.itemEntityWeight(itemEntity.getItem());
            if (weight <= 0.0D) {
                continue;
            }

            applyForce(itemEntity, magnetCenter, state, weight, config);
        }
    }

    private static void applyForce(Entity entity,
                                   Vec3 magnetCenter,
                                   BlockState state,
                                   double magneticWeight,
                                   ModConfig.MagnetConfig config) {
        Vec3 delta = magnetCenter.subtract(entity.position());
        double distanceSqr = delta.lengthSqr();
        double minDistance = config.minimumDistance();

        if (!Double.isFinite(distanceSqr) || distanceSqr <= minDistance * minDistance) {
            return;
        }

        double distance = Math.sqrt(distanceSqr);
        double radius = config.radius();
        if (distance > radius) {
            return;
        }

        // Continuous falloff removes the hard edge produced by a binary radius check.
        double normalized = 1.0D - (distance / radius);
        double falloff = Math.pow(Math.max(0.0D, normalized), config.falloffExponent());
        double forceMagnitude = config.baseStrength() * magneticWeight * falloff;
        if (forceMagnitude <= 0.0D) {
            return;
        }

        Vec3 direction = delta.scale(1.0D / distance);
        if (state.getValue(MagnetBlock.POLARITY) == MagnetPolarity.REPEL) {
            direction = direction.scale(-1.0D);
        }

        direction = new Vec3(
                direction.x,
                direction.y * config.verticalForceMultiplier(),
                direction.z
        );

        double directionLength = direction.length();
        if (directionLength <= 1.0E-8D) {
            return;
        }

        Vec3 acceleration = direction.scale(forceMagnitude / directionLength);
        double accelerationLength = acceleration.length();
        if (accelerationLength > config.maxAccelerationPerUpdate()) {
            acceleration = acceleration.scale(config.maxAccelerationPerUpdate() / accelerationLength);
        }

        Vec3 velocity = entity.getDeltaMovement().add(acceleration);
        double maxVelocity = config.maxResultingVelocity();
        double velocityLength = velocity.length();
        if (velocityLength > maxVelocity) {
            velocity = velocity.scale(maxVelocity / velocityLength);
        }

        entity.setDeltaMovement(velocity);
    }
}
