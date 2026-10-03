package ru.magnetism.magnet;

import net.minecraft.util.StringRepresentable;

public enum MagnetPolarity implements StringRepresentable {
    ATTRACT("attract"),
    REPEL("repel");

    private final String serializedName;

    MagnetPolarity(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public MagnetPolarity opposite() {
        return this == ATTRACT ? REPEL : ATTRACT;
    }
}
