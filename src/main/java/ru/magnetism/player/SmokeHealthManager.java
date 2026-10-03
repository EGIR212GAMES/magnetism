package ru.magnetism.player;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import ru.magnetism.config.ModConfig;
import ru.magnetism.registry.ModAttachments;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies the persistent smoke-heart penalty as one additive max-health modifier.
 *
 * The modifier is rebuilt only when the penalty changes and during periodic
 * reconciliation. Reconciliation temporarily removes the Magnetism modifier so
 * the amount is calculated against all other max-health modifiers accurately.
 */
public final class SmokeHealthManager {
    private static final Identifier MODIFIER_ID = Identifier.fromNamespaceAndPath(
            "magnetism",
            "smoke_heart_penalty"
    );

    private static final int RECONCILE_INTERVAL_TICKS = 20;
    private static final double HEART_HEALTH = 2.0D;

    private static long lastReconcileTick = -1L;
    private static final Map<UUID, Double> LAST_EFFECTIVE_MAX_HEALTH = new HashMap<>();

    private SmokeHealthManager() {
    }

    public static int getHeartPenalty(ServerPlayer player) {
        return Math.max(0, player.getAttachedOrElse(ModAttachments.SMOKE_HEART_PENALTY, 0));
    }

    public static boolean tryLoseHeart(ServerPlayer player) {
        int currentPenalty = getHeartPenalty(player);
        double maxWithoutSmoke = getMaxHealthWithoutSmokePenalty(player);
        int maximumPenalty = maximumAllowedPenalty(maxWithoutSmoke);

        if (currentPenalty >= maximumPenalty) {
            return false;
        }

        player.setAttached(ModAttachments.SMOKE_HEART_PENALTY, currentPenalty + 1);
        reconcile(player);
        SmokeSneeze.triggerSmokeSneeze(player);
        return true;
    }

    public static boolean recoverOneHeart(ServerPlayer player) {
        int currentPenalty = getHeartPenalty(player);
        if (currentPenalty <= 0) {
            return false;
        }

        player.setAttached(ModAttachments.SMOKE_HEART_PENALTY, currentPenalty - 1);
        reconcile(player);
        return true;
    }

    public static void reconcile(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }

        AttributeModifier current = attribute.getModifier(MODIFIER_ID);
        if (current != null) {
            attribute.removeModifier(current);
        }

        double maxWithoutSmoke = attribute.getValue();
        int hearts = getHeartPenalty(player);
        double requestedPenalty = hearts * HEART_HEALTH;
        double appliedPenalty = Math.min(
                requestedPenalty,
                Math.max(0.0D, maxWithoutSmoke - minimumMaxHealth())
        );

        if (appliedPenalty > 0.0D) {
            attribute.addPermanentModifier(new AttributeModifier(
                    MODIFIER_ID,
                    -appliedPenalty,
                    AttributeModifier.Operation.ADD_VALUE
            ));
        }

        double effectiveMax = attribute.getValue();
        LAST_EFFECTIVE_MAX_HEALTH.put(player.getUUID(), effectiveMax);
        if (player.getHealth() > effectiveMax) {
            player.setHealth((float) effectiveMax);
        }
    }

    public static void reconcileAll(MinecraftServer server, long currentTick) {
        if (lastReconcileTick >= 0L && currentTick - lastReconcileTick < RECONCILE_INTERVAL_TICKS) {
            return;
        }

        lastReconcileTick = currentTick;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            int penalty = getHeartPenalty(player);
            if (penalty <= 0) {
                LAST_EFFECTIVE_MAX_HEALTH.remove(player.getUUID());
                continue;
            }

            AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
            if (attribute == null) {
                continue;
            }

            AttributeModifier current = attribute.getModifier(MODIFIER_ID);
            double effectiveMax = attribute.getValue();
            Double lastMax = LAST_EFFECTIVE_MAX_HEALTH.get(player.getUUID());

            if (current == null || lastMax == null || Math.abs(lastMax - effectiveMax) > 1.0E-6D) {
                reconcile(player);
            }
        }
    }

    public static void resetReconcileClock() {
        lastReconcileTick = -1L;
        LAST_EFFECTIVE_MAX_HEALTH.clear();
    }

    private static double getMaxHealthWithoutSmokePenalty(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return HEART_HEALTH;
        }

        AttributeModifier current = attribute.getModifier(MODIFIER_ID);
        if (current != null) {
            attribute.removeModifier(current);
        }

        double value = attribute.getValue();

        if (current != null) {
            attribute.addPermanentModifier(current);
        }

        return value;
    }

    private static int maximumAllowedPenalty(double maxWithoutSmoke) {
        if (maxWithoutSmoke <= minimumMaxHealth()) {
            return 0;
        }
        return Math.max(0, (int) Math.floor((maxWithoutSmoke - minimumMaxHealth()) / HEART_HEALTH));
    }

    private static double minimumMaxHealth() {
        return Math.max(HEART_HEALTH, ModConfig.get().smokeExposure().minMaxHealth());
    }
}
