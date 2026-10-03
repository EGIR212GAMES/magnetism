package ru.magnetism.magnet;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import ru.magnetism.config.ModConfig;
import org.jetbrains.annotations.Nullable;

/**
 * The Magnet. Vanilla clients never learn about this block: Polymer replaces it
 * with a vanilla block in every packet (see {@link #getPolymerBlockState}).
 */
public final class MagnetBlock extends Block implements PolymerBlock {
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

    /**
     * What clients (vanilla, Fabric or Geyser/Bedrock) see instead of the Magnet.
     * ATTRACT looks like cobbled deepslate (dark slate), REPEL like blackstone,
     * so the polarity stays visible without any resource pack.
     */
    @Override
    public BlockState getPolymerBlockState(BlockState state, @org.jspecify.annotations.Nullable PacketContext context) {
        return state.getValue(POLARITY) == MagnetPolarity.REPEL
                ? Blocks.BLACKSTONE.defaultBlockState()
                : Blocks.COBBLED_DEEPSLATE.defaultBlockState();
    }

    /** Copper pickaxe or better (tag magnetism:magnet_breaking_tools); creative players always can. */
    public static boolean canBreak(Player player) {
        return player.isCreative() || player.getMainHandItem().is(MagnetTags.MAGNET_BREAKING_TOOLS);
    }

    /** Mining progress is zero with a wrong tool, so the break bar never fills. */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!canBreak(player)) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos);
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
