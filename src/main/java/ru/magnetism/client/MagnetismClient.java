package ru.magnetism.client;

import net.fabricmc.api.ClientModInitializer;
import ru.magnetism.registry.ModBlocks;
import ru.magnetism.util.ModLogger;

/** Client-side entrypoint for registry synchronization.
 *
 * Registers blocks and items on the client so Fabric's registry sync
 * can match server-side entries. All gameplay logic remains server-side. */
public final class MagnetismClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModLogger.info("Initializing Magnetism client-side registries.");
        ModBlocks.initialize();
        ModLogger.info("Magnetism client-side bootstrap complete.");
    }
}