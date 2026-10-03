package ru.magnetism.smoke;

import net.minecraft.core.BlockPos;

/** A tracked campfire source. SmokeSource is runtime state and is never persisted. */
public record SmokeSource(BlockPos pos) {
    public SmokeSource {
        pos = pos.immutable();
    }
}
