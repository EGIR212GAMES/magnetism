package ru.magnetism.magnet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import ru.magnetism.config.ModConfig;
import org.jetbrains.annotations.Nullable;

public final class MagnetBlock extends Block {
    public static final EnumProperty<MagnetPolarity> POLARITY =
            EnumProperty.create("polarity", MagnetPolarity.class);

    public MagnetBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POLARITY, MagnetPolarity.ATTRACT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POLARITY);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState();
    }

    @Override
    protected void onPlace(BlockState state,
                           Level level,
                           BlockPos pos,
                           BlockState oldState,
                           boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);

        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            level.scheduleTick(pos, this, ModConfig.get().magnet().updateIntervalTicks());
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        MagnetSystem.applyAttraction(level, pos, state);
        level.scheduleTick(pos, this, ModConfig.get().magnet().updateIntervalTicks());
    }
}
