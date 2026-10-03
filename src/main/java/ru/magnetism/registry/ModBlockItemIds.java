package ru.magnetism.registry;

import net.minecraft.references.BlockItemId;
import net.minecraft.resources.Identifier;
import ru.magnetism.Magnetism;

public final class ModBlockItemIds {
    public static final BlockItemId MAGNET = create("magnet");
    public static final BlockItemId COPIER = create("copier");

    private ModBlockItemIds() {
    }

    private static BlockItemId create(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(Magnetism.MOD_ID, name);
        return BlockItemId.create(id, id);
    }
}
