package ru.magnetism.smoke;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import ru.magnetism.config.ModConfig;

import java.util.Random;

/** Server-side visual emission only; gameplay never reads particle state. */
public final class SmokeParticleService {
    private final Random random = new Random();

    public void emit(ServerLevel level, SmokeRegion region, long tick) {
        ModConfig.SmokeConfig config = ModConfig.get().smoke();
        if (config.particleDensity() <= 0.0D || region.activeCellCount() == 0) {
            return;
        }

        int budget = Math.max(1, config.maxParticlesPerRegionPerUpdate());
        int emitted = 0;
        int stride = Math.max(1, region.cells().size() / Math.max(1, budget));
        int index = 0;

        for (SmokeCell cell : region.cells().values()) {
            if (!cell.active() || cell.density() < config.particleThreshold()) {
                continue;
            }
            if (index++ % stride != 0 || emitted >= budget) {
                continue;
            }

            BlockPos center = region.cellCenter(cell.pos());
            double flowScale = Math.min(0.55D, Math.sqrt(
                    cell.flowX() * cell.flowX()
                            + cell.flowY() * cell.flowY()
                            + cell.flowZ() * cell.flowZ()
            ));

            double x = center.getX() + 0.5D + cell.flowX() * flowScale * 0.75D;
            double y = center.getY() + 0.6D + cell.flowY() * flowScale * 0.75D;
            double z = center.getZ() + 0.5D + cell.flowZ() * flowScale * 0.75D;
            int count = cell.exterior() ? 2 : (cell.density() > 0.6F ? 2 : 1);

            level.sendParticles(
                    ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    x,
                    y,
                    z,
                    count,
                    0.35D,
                    0.25D,
                    0.35D,
                    0.01D + random.nextDouble() * 0.02D
            );

            if (cell.exterior() && cell.density() > 0.35F && emitted + 2 < budget) {
                level.sendParticles(
                        ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                        x,
                        y,
                        z,
                        1,
                        0.20D,
                        0.20D,
                        0.20D,
                        0.01D
                );
                emitted += 2;
            } else {
                emitted++;
            }
        }
    }
}
