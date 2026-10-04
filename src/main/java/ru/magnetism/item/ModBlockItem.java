package ru.magnetism.item;

import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Block item that vanilla clients see as {@code clientItem}, with a name that
 * is always readable without any resource pack or language file.
 *
 * <p>The name stored in the item's default components is not reliable: the vanilla
 * item-properties pipeline replaces it with a plain translation key, which a vanilla
 * client cannot resolve. So the name is written into the client-side copy of the stack
 * at send time, as plain literal text (no translation lookup is needed on the client,
 * and nothing in between can turn it into a raw key).
 *
 * <p>It is set both as {@code ITEM_NAME} and as a non-italic {@code CUSTOM_NAME}:
 * the client prefers CUSTOM_NAME, so the name survives even if something later resets
 * ITEM_NAME. A name the player gave the stack themselves (anvil rename) is kept.
 */
public final class ModBlockItem extends PolymerBlockItem {
    private final Component clientName;

    /**
     * @param nameKey      translation key, kept for resource-pack/Geyser use and documentation
     * @param fallbackName the text vanilla clients actually display
     */
    public ModBlockItem(Block block, Properties properties, Item clientItem, String nameKey, String fallbackName) {
        super(block, properties, clientItem);
        this.clientName = Component.literal(fallbackName);
    }

    @Override
    public void modifyBasePolymerItemStack(ItemStack out, ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
        super.modifyBasePolymerItemStack(out, stack, context, lookup);
        out.set(DataComponents.ITEM_NAME, clientName);
        if (!out.has(DataComponents.CUSTOM_NAME)) {
            out.set(DataComponents.CUSTOM_NAME,
                    Component.empty().append(clientName).setStyle(Style.EMPTY.withItalic(false)));
        }
    }
}
