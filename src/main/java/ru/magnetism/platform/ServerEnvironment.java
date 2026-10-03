package ru.magnetism.platform;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/** Server/client environment checks kept behind one tiny abstraction. */
public final class ServerEnvironment {
    private ServerEnvironment() {
    }

    public static boolean isServerSide() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
    }
}
