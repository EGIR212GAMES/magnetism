package ru.magnetism.server;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import ru.magnetism.config.ModConfig;
import ru.magnetism.magnet.MagnetLightningHandler;
import ru.magnetism.platform.GeyserDetector;
import ru.magnetism.platform.ServerEnvironment;
import ru.magnetism.registry.ModRegistries;
import ru.magnetism.state.PlayerStateManager;
import ru.magnetism.smoke.SmokeManager;
import ru.magnetism.smoke.SmokeExposureService;
import ru.magnetism.util.ModLogger;
import ru.magnetism.player.SmokeHealthManager;

/** Dedicated-server lifecycle entrypoint for server-only Magnetism services. */
public final class MagnetismServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        if (!ServerEnvironment.isServerSide()) {
            throw new IllegalStateException("MagnetismServer was invoked outside the server environment");
        }

        ModConfig.load();
        ServerTickManager.initialize();
        PlayerStateManager.initialize();
        
        // Register blocks/items early for registry sync
        ModRegistries.initialize();
        
        SmokeManager.initialize();
        SmokeManager.exposureService().initialize();
        MagnetLightningHandler.initialize();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (GeyserDetector.isGeyserLoaded()) {
                ModLogger.info("Geyser detected. Magnetism core remains Geyser-independent; presentation integration is not enabled yet.");
            } else {
                ModLogger.info("Geyser not detected. Running Magnetism core without Geyser integration.");
            }

            ModLogger.info("Magnetism dedicated server started.");
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ModLogger.info("Stopping Magnetism server services.");
            SmokeManager.clear();
            SmokeExposureService.reset();
            PlayerStateManager.shutdown();
            ServerTickManager.shutdown();
            ModConfig.save();
            ModConfig.reset();
        });

        ModLogger.info("Dedicated-server lifecycle hooks initialized.");
    }
}
