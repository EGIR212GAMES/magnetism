package ru.magnetism.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import ru.magnetism.util.ModLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Central server tick dispatcher for Magnetism subsystems.
 *
 * Systems added in later stages register small server-thread tasks here instead
 * of creating a large number of independent global tick listeners.
 */
public final class ServerTickManager {
    private static final List<Consumer<MinecraftServer>> END_TICK_TASKS = new ArrayList<>();

    private static boolean initialized;
    private static long tickCount;

    private ServerTickManager() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }

        ServerTickEvents.END_SERVER_TICK.register(ServerTickManager::onEndServerTick);
        initialized = true;
        ModLogger.info("Server tick infrastructure initialized.");
    }

    public static void registerEndTickTask(Consumer<MinecraftServer> task) {
        if (task == null) {
            throw new IllegalArgumentException("task cannot be null");
        }

        END_TICK_TASKS.add(task);
    }

    public static long getTickCount() {
        return tickCount;
    }

    public static void shutdown() {
        tickCount = 0;
        END_TICK_TASKS.clear();
    }

    private static void onEndServerTick(MinecraftServer server) {
        tickCount++;

        for (Consumer<MinecraftServer> task : List.copyOf(END_TICK_TASKS)) {
            try {
                task.accept(server);
            } catch (RuntimeException exception) {
                // One subsystem must not silently prevent every other subsystem
                // from receiving its server tick. The failing task remains
                // registered so its owner can be fixed without restarting logic.
                ModLogger.error("A Magnetism server-tick task failed on tick " + tickCount, exception);
            }
        }
    }
}
