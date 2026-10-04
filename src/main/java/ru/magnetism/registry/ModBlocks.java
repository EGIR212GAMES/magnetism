package ru.magnetism.registry;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.ResourceKey;
import ru.magnetism.item.ModBlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import ru.magnetism.copier.CopierBlock;
import ru.magnetism.magnet.MagnetBlock;
import ru.magnetism.util.ModLogger;

public final class ModBlocks {
    public static final Block MAGNET = register(
            ModBlockItemIds.MAGNET,
            Items.COBBLED_DEEPSLATE,
            "block.magnetism.magnet",
            "Магнит",
            MagnetBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE)
                    .sound(SoundType.STONE)
    );

    public static final Block COPIER = register(
            ModBlockItemIds.COPIER,
            Items.IRON_BLOCK,
            "block.magnetism.copier",
            "Копир",
            CopierBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.ANVIL)
                    .sound(SoundType.ANVIL)
    );

    private static boolean initialized;

    private ModBlocks() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(creativeTab -> {
            creativeTab.accept(MAGNET.asItem());
            creativeTab.accept(COPIER.asItem());
        });

        initialized = true;
        ModLogger.info("Registered Magnet and Copier blocks.");
    }

    private static Block register(ResourceKey<Block> id,
                                  Function<BlockBehaviour.Properties, Block> blockFactory,
                                  BlockBehaviour.Properties properties) {
        Block block = blockFactory.apply(properties.setId(id));
        return Registry.register(BuiltInRegistries.BLOCK, id, block);
    }

    private static Block register(BlockItemId id,
                                  Item clientItem,
                                  String nameKey,
                                  String fallbackName,
                                  Function<BlockBehaviour.Properties, Block> blockFactory,
                                  BlockBehaviour.Properties properties) {
        Block block = register(id.block(), blockFactory, properties);
        // ModBlockItem makes clients see `clientItem` and gives it a readable name.
        ModBlockItem blockItem = new ModBlockItem(
                block,
                new Item.Properties()
                        .useBlockDescriptionPrefix()
                        .setId(id.item()),
                clientItem,
                nameKey,
                fallbackName
        );
        Registry.register(BuiltInRegistries.ITEM, id.item(), blockItem);
        return block;
    }
}
