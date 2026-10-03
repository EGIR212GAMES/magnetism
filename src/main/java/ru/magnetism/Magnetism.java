package ru.magnetism;

import net.fabricmc.api.ModInitializer;
import ru.magnetism.registry.ModAttachments;
import ru.magnetism.enchantment.ModEnchantments;
import ru.magnetism.util.ModLogger;
import ru.magnetism.platform.ServerEnvironment;

/**
 * Main Fabric entrypoint for Magnetism.
 *
 * The mod metadata marks Magnetism as server-only, so no client classes are
 * loaded by Fabric Loader at all. This initializer handles shared registry
 * bootstrap (attachments, enchantments) while server-specific initialization
 * (blocks, items, gameplay systems) lives in {@link ru.magnetism.server.MagnetismServer}.
 */
public final class Magnetism implements ModInitializer {
    public static final String MOD_ID = "magnetism";

    @Override
    public void onInitialize() {
        if (!ServerEnvironment.isServerSide()) {
            // Defensive guard for unusual launchers or test harnesses.
            ModLogger.error("Magnetism was initialized outside the server environment; aborting bootstrap.");
            return;
        }

        ModLogger.info("Initializing Magnetism shared registries.");
        ModAttachments.initialize();
        ModEnchantments.initialize();
        ModLogger.info("Magnetism shared registry bootstrap complete.");
    }

}
