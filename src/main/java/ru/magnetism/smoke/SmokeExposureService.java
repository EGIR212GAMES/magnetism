package ru.magnetism.smoke;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import ru.magnetism.config.ModConfig;
import ru.magnetism.enchantment.SecondWindRules;
import ru.magnetism.player.SmokeHealthManager;
import ru.magnetism.registry.ModAttachments;
import ru.magnetism.server.ServerTickManager;

/**
 * Converts Smoke Engine density into persistent player exposure.
 *
 * The service never searches for nearby campfires. It queries the existing
 * SmokeManager field, so gameplay responds to actual simulated density.
 */
public final class SmokeExposureService {
    private static boolean initialized;

    public void initialize() {
        if (initialized) {
            return;
        }

        ServerTickManager.registerEndTickTask(this::tick);
        initialized = true;
    }

    public float sampleDensity(ServerPlayer player) {
        return SmokeManager.sampleDensity(player);
    }

    public boolean isDangerous(ServerPlayer player) {
        ModConfig.SmokeExposureConfig config = ModConfig.get().smokeExposure();
        return sampleDensity(player) >= config.dangerousDensityThreshold();
    }

    public void tick(MinecraftServer server) {
        ModConfig configRoot = ModConfig.get();
        if (!configRoot.enabled() || !configRoot.smoke().enabled() || !configRoot.smokeExposure().enabled()) {
            return;
        }

        ModConfig.SmokeExposureConfig config = configRoot.smokeExposure();
        long tick = ServerTickManager.getTickCount();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            processPlayer(player, config);
        }

        SmokeHealthManager.reconcileAll(server, tick);
    }

    public static void reset() {
        initialized = false;
        SmokeHealthManager.resetReconcileClock();
    }

    private static void processPlayer(ServerPlayer player, ModConfig.SmokeExposureConfig config) {
        if (!player.isAlive() || player.isSpectator()) {
            player.setAttached(ModAttachments.SMOKE_EXPOSURE_TICKS, 0);
            player.setAttached(ModAttachments.SMOKE_CLEAN_AIR_TICKS, 0);
            return;
        }

        boolean dangerous = SmokeManager.sampleDensity(player) >= config.dangerousDensityThreshold();

        if (dangerous) {
            player.setAttached(ModAttachments.SMOKE_CLEAN_AIR_TICKS, 0);

            int exposure = Math.max(0, player.getAttachedOrElse(ModAttachments.SMOKE_EXPOSURE_TICKS, 0));
            int nextExposure = Math.min(config.maximumExposureCounter(), exposure + 1);
            player.setAttached(ModAttachments.SMOKE_EXPOSURE_TICKS, nextExposure);

            int threshold = getExposureThresholdTicks(player, config);
            if (nextExposure >= threshold) {
                boolean lost = SmokeHealthManager.tryLoseHeart(player);
                // Reset after an interval whether a heart was lost or the
                // player had already reached the minimum health boundary. This
                // prevents repeated attempts every tick at the lower bound.
                player.setAttached(ModAttachments.SMOKE_EXPOSURE_TICKS, 0);
            }
            return;
        }

        // Clean air immediately interrupts the current dangerous-smoke timer.
        player.setAttached(ModAttachments.SMOKE_EXPOSURE_TICKS, 0);

        int clean = Math.max(0, player.getAttachedOrElse(ModAttachments.SMOKE_CLEAN_AIR_TICKS, 0));
        int nextClean = Math.min(config.maximumCleanAirCounter(), clean + 1);
        player.setAttached(ModAttachments.SMOKE_CLEAN_AIR_TICKS, nextClean);

        if (nextClean >= config.cleanAirRecoveryTicks()) {
            if (SmokeHealthManager.recoverOneHeart(player)) {
                player.setAttached(ModAttachments.SMOKE_CLEAN_AIR_TICKS, 0);
            } else {
                // Do not retain a full recovery interval once there is no
                // penalty left to recover.
                player.setAttached(ModAttachments.SMOKE_CLEAN_AIR_TICKS, 0);
            }
        }
    }

    private static int getExposureThresholdTicks(
            ServerPlayer player,
            ModConfig.SmokeExposureConfig config
    ) {
        int level = SecondWindRules.getLevel(player);
        return switch (level) {
            case 1 -> config.secondWindLevel1Ticks();
            case 2 -> config.secondWindLevel2Ticks();
            case 3 -> config.secondWindLevel3Ticks();
            default -> config.baseExposureTicks();
        };
    }
}
