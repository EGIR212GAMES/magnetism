package ru.magnetism.registry;

import ru.magnetism.util.ModLogger;

/**
 * Single bootstrap point for Magnetism's registries.
 *
 * No gameplay content is registered in Stage 2 yet. Keeping the bootstrap
 * point now avoids scattering future block/item/entity registration calls among
 * unrelated entrypoints.
 */
public final class ModRegistries {
    private static boolean initialized;

    private ModRegistries() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }

        ModBlocks.initialize();
        ModAttachments.initialize();
        ru.magnetism.enchantment.ModEnchantments.initialize();
        initialized = true;
        ModLogger.info("Magnetism registry bootstrap complete.");
    }
}
