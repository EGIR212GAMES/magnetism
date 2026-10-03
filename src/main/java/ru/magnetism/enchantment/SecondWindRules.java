package ru.magnetism.enchantment;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Server-side lookup for the current Second Wind level on a player's helmet. */
public final class SecondWindRules {
    private SecondWindRules() {
    }

    public static int getLevel(ServerPlayer player) {
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.isEmpty()) {
            return 0;
        }

        try {
            Holder<Enchantment> enchantment = player.level()
                    .registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(ModEnchantments.SECOND_WIND);

            return EnchantmentHelper.getItemEnchantmentLevel(enchantment, helmet);
        } catch (RuntimeException ignored) {
            // A malformed/partially reloaded datapack must not crash the core
            // gameplay loop; absence of the enchantment simply means level 0.
            return 0;
        }
    }
}
