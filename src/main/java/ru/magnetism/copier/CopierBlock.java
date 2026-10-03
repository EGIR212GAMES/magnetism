package ru.magnetism.copier;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Copier. Clients see an iron block (full cube, like the real shape), and the
 * GUI is the vanilla anvil screen, so no client mod is needed.
 */
public final class CopierBlock extends Block implements PolymerBlock {
    public CopierBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state, @org.jspecify.annotations.Nullable PacketContext context) {
        return Blocks.IRON_BLOCK.defaultBlockState();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state,
                                               Level level,
                                               BlockPos pos,
                                               Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!player.isSpectator()) {
            MenuProvider provider = createMenuProvider(level, pos);
            player.openMenu(provider);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return createMenuProvider(level, pos);
    }

    private static MenuProvider createMenuProvider(Level level, BlockPos pos) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatableWithFallback("block.magnetism.copier", "Копир");
            }

            @Override
            public @Nullable AbstractContainerMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory, Player player) {
                return new CopierMenu(
                        containerId,
                        inventory,
                        ContainerLevelAccess.create(level, pos)
                );
            }
        };
    }
}
