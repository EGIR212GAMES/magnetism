package ru.magnetism.copier;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class CopierService {
    private CopierService() {
    }

    public static boolean isSupported(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(Items.WRITTEN_BOOK) || stack.is(Items.ENCHANTED_BOOK));
    }

    public static ItemStack createCopy(ItemStack source) {
        if (!isSupported(source)) {
            return ItemStack.EMPTY;
        }

        // ItemStack.copy() preserves the complete component map. This is
        // deliberate: written-book content, stored enchantments, custom names,
        // custom data and future relevant components must not be whitelisted
        // manually one by one.
        ItemStack result = source.copy();
        result.setCount(1);
        return result;
    }
}
