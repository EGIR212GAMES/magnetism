package ru.magnetism.magnet;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import ru.magnetism.util.ModLogger;

/**
 * Server-side rule: the Magnet can only be broken with a copper pickaxe or better.
 *
 * <p>Two layers, both pure server logic (so vanilla clients are fine):
 * <ul>
 *   <li>{@link MagnetBlock#getDestroyProgress} keeps mining progress at zero for a wrong tool;</li>
 *   <li>{@link PlayerBlockBreakEvents#BEFORE} is the hard guarantee that a break which still
 *       reaches the server (modified clients, other mods) is cancelled;</li>
 *   <li>{@link AttackBlockCallback} tells the player once when they start mining with a wrong tool.</li>
 * </ul>
 */
public final class MagnetBreakHandler {
    private static final Component HINT =
            Component.literal("Магнит можно сломать только медной киркой или лучше");

    private MagnetBreakHandler() {
    }

    public static void initialize() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (state.getBlock() instanceof MagnetBlock && !MagnetBlock.canBreak(player)) {
                return false;
            }
            return true;
        });

        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!level.isClientSide()
                    && player instanceof ServerPlayer serverPlayer
                    && level.getBlockState(pos).getBlock() instanceof MagnetBlock
                    && !MagnetBlock.canBreak(player)) {
                serverPlayer.sendSystemMessage(HINT, true);
            }
            return InteractionResult.PASS;
        });

        ModLogger.info("Magnet break rule initialized (copper pickaxe or better).");
    }
}
