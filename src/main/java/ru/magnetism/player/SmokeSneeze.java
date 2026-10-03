package ru.magnetism.player;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Visual/audio feedback for losing a smoke heart. */
public final class SmokeSneeze {
    private SmokeSneeze() {
    }

    public static void triggerSmokeSneeze(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();
        double lengthSquared = look.lengthSqr();
        if (lengthSquared < 1.0E-6D) {
            look = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            look = look.scale(1.0D / Math.sqrt(lengthSquared));
        }

        // The eye position is close to the mouth; move slightly forward along
        // the gaze and a little downward so the effect does not originate from
        // the player's chest/center of mass.
        Vec3 mouth = player.getEyePosition()
                .add(look.scale(0.28D))
                .add(0.0D, -0.12D, 0.0D);

        level.sendParticles(
                ParticleTypes.ITEM_SLIME,
                mouth.x(), mouth.y(), mouth.z(),
                7,
                0.07D, 0.04D, 0.07D,
                0.02D
        );

        level.sendParticles(
                ParticleTypes.SNEEZE,
                mouth.x(), mouth.y(), mouth.z(),
                2,
                0.04D, 0.03D, 0.04D,
                0.01D
        );

        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.PANDA_SNEEZE,
                SoundSource.PLAYERS,
                0.8F,
                0.95F
        );
    }
}
