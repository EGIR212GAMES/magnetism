package ru.magnetism.magnet;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import ru.magnetism.Magnetism;

public final class MagnetTags {
    public static final TagKey<Item> MAGNETIC_ITEMS = itemTag("magnetic_items");
    public static final TagKey<Item> MAGNETIC_IRON = itemTag("magnetic_iron");
    public static final TagKey<Item> MAGNETIC_DIAMOND = itemTag("magnetic_diamond");
    public static final TagKey<Item> MAGNETIC_ANCIENT_DEBRIS = itemTag("magnetic_ancient_debris");
    public static final TagKey<Item> MAGNETIC_NETHERITE = itemTag("magnetic_netherite");
    public static final TagKey<Item> MAGNETIC_SPEARS = itemTag("magnetic_spears");

    /** Tools allowed to break the Magnet (copper pickaxe and better). Data-driven, extendable by packs. */
    public static final TagKey<Item> MAGNET_BREAKING_TOOLS = itemTag("magnet_breaking_tools");

    public static final TagKey<Item> CHAINMAIL_ARMOR = itemTag("magnetic_armor/chainmail");
    public static final TagKey<Item> IRON_ARMOR = itemTag("magnetic_armor/iron");
    public static final TagKey<Item> DIAMOND_ARMOR = itemTag("magnetic_armor/diamond");
    public static final TagKey<Item> NETHERITE_ARMOR = itemTag("magnetic_armor/netherite");

    private MagnetTags() {
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(
                Registries.ITEM,
                Identifier.fromNamespaceAndPath(Magnetism.MOD_ID, path)
        );
    }
}
