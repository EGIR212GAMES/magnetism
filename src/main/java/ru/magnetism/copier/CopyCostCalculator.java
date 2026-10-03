package ru.magnetism.copier;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import ru.magnetism.config.ModConfig;

public final class CopyCostCalculator {
    private CopyCostCalculator() {
    }

    public static int calculate(ItemStack source) {
        if (source.is(Items.WRITTEN_BOOK)) {
            WrittenBookContent content = source.get(DataComponents.WRITTEN_BOOK_CONTENT);
            int pages = content == null ? 0 : content.pages().size();
            ModConfig.CopierConfig config = ModConfig.get().copier();
            return clamp(config.writtenBookBaseCost() + pages * config.writtenBookPageCost(), config.maxCopyCost());
        }

        if (source.is(Items.ENCHANTED_BOOK)) {
            ItemEnchantments enchantments = source.getOrDefault(
                    DataComponents.STORED_ENCHANTMENTS,
                    ItemEnchantments.EMPTY
            );
            int levelSum = 0;
            for (var entry : enchantments.entrySet()) {
                levelSum += entry.getIntValue();
            }

            ModConfig.CopierConfig config = ModConfig.get().copier();
            return clamp(
                    config.enchantedBookBaseCost() + levelSum * config.enchantedBookLevelCost(),
                    config.maxCopyCost()
            );
        }

        return 0;
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(value, Math.max(0, max)));
    }
}
