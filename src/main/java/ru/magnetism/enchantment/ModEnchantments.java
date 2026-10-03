package ru.magnetism.enchantment;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import ru.magnetism.Magnetism;

/** Resource keys for Magnetism's data-driven enchantments. */
public final class ModEnchantments {
    public static final ResourceKey<Enchantment> SECOND_WIND = key("second_wind");
    public static final ResourceKey<Enchantment> SPEED_FLYER = key("speed_flyer");

    private ModEnchantments() {
    }

    public static void initialize() {
        // Enchantments are data-driven dynamic registry entries. Their actual
        // definitions live under data/magnetism/enchantment/*.json.
    }

    private static ResourceKey<Enchantment> key(String path) {
        return ResourceKey.create(
                Registries.ENCHANTMENT,
                Identifier.fromNamespaceAndPath(Magnetism.MOD_ID, path)
        );
    }
}
