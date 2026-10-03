package ru.magnetism.registry;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import ru.magnetism.Magnetism;

/**
 * Persistent player gameplay state owned by Magnetism.
 *
 * Attachments are intentionally immutable primitive values. This avoids shared
 * mutable state and lets Fabric persist the values and copy them on player death.
 */
public final class ModAttachments {
    public static final AttachmentType<Integer> SMOKE_EXPOSURE_TICKS = AttachmentRegistry.create(
            id("smoke_exposure_ticks"),
            builder -> builder
                    .initializer(() -> 0)
                    .persistent(Codec.INT)
                    .copyOnDeath()
    );

    public static final AttachmentType<Integer> SMOKE_CLEAN_AIR_TICKS = AttachmentRegistry.create(
            id("smoke_clean_air_ticks"),
            builder -> builder
                    .initializer(() -> 0)
                    .persistent(Codec.INT)
                    .copyOnDeath()
    );

    public static final AttachmentType<Integer> SMOKE_HEART_PENALTY = AttachmentRegistry.create(
            id("smoke_heart_penalty"),
            builder -> builder
                    .initializer(() -> 0)
                    .persistent(Codec.INT)
                    .copyOnDeath()
    );

    private ModAttachments() {
    }

    public static void initialize() {
        // Static field initialization performs registration. This method exists
        // only as an explicit bootstrap boundary for the registry architecture.
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Magnetism.MOD_ID, path);
    }
}
