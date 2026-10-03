package ru.magnetism.platform;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Detects Geyser without depending on Geyser classes or APIs.
 *
 * This class is intentionally limited to discovery. Actual Geyser integration
 * will live in a separate adapter/module once mappings are proven insufficient.
 */
public final class GeyserDetector {
    public static final String GEYSER_MOD_ID = "geyser";

    private GeyserDetector() {
    }

    public static boolean isGeyserLoaded() {
        return FabricLoader.getInstance().isModLoaded(GEYSER_MOD_ID);
    }
}
