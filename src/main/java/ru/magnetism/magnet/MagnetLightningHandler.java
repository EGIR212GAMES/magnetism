package ru.magnetism.magnet;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.state.BlockState;
import ru.magnetism.util.ModLogger;

public final class MagnetLightningHandler {
    private MagnetLightningHandler() {
    }

    public static void initialize() {
        ServerEntityEvents.ENTITY_LOAD.register(MagnetLightningHandler::onEntityLoad);
        ModLogger.info("Magnet lightning interaction initialized.");
    }

    private static void onEntityLoad(Entity entity, ServerLevel level) {
        if (!(entity instanceof LightningBolt)) {
            return;
        }

        BlockPos strikePos = entity.blockPosition();
        if (!toggleIfMagnet(level, strikePos)) {
            toggleIfMagnet(level, strikePos.below());
        }
    }

    private static boolean toggleIfMagnet(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof MagnetBlock)) {
            return false;
        }

        MagnetPolarity current = state.getValue(MagnetBlock.POLARITY);
        MagnetPolarity next = current.opposite();
        level.setBlock(pos, state.setValue(MagnetBlock.POLARITY, next), 3);
        ModLogger.debug("Lightning toggled Magnet at {} from {} to {}.", pos, current, next);
        return true;
    }
}
