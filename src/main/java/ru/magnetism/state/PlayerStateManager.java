package ru.magnetism.state;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;
import ru.magnetism.util.ModLogger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Lightweight runtime player-state registry.
 *
 * Persistent gameplay state will move to Fabric Data Attachments in a later
 * stage. This manager only tracks which player identities are currently online,
 * giving those systems a stable lifecycle hook without prematurely inventing
 * persistence APIs.
 */
public final class PlayerStateManager {
    private static final Set<UUID> ONLINE_PLAYERS = new HashSet<>();
    private static boolean initialized;

    private PlayerStateManager() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }

        ServerPlayerEvents.JOIN.register(PlayerStateManager::onPlayerJoin);
        ServerPlayerEvents.LEAVE.register(PlayerStateManager::onPlayerLeave);

        initialized = true;
        ModLogger.info("Player state lifecycle hooks initialized.");
    }

    public static boolean isOnline(UUID playerId) {
        return ONLINE_PLAYERS.contains(playerId);
    }

    public static int getOnlinePlayerCount() {
        return ONLINE_PLAYERS.size();
    }

    public static void shutdown() {
        ONLINE_PLAYERS.clear();
    }

    private static void onPlayerJoin(ServerPlayer player) {
        ONLINE_PLAYERS.add(player.getUUID());
    }

    private static void onPlayerLeave(ServerPlayer player) {
        ONLINE_PLAYERS.remove(player.getUUID());
    }
}
