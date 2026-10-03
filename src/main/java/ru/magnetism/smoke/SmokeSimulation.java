package ru.magnetism.smoke;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import ru.magnetism.config.ModConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure-ish server-thread smoke stepping over a coarse sparse grid. */
public final class SmokeSimulation {
    private static final int[][] DIRECTIONS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    private final AirConnectivityService airConnectivityService;

    public SmokeSimulation(AirConnectivityService airConnectivityService) {
        this.airConnectivityService = airConnectivityService;
    }

    public void rebuildTopology(ServerLevel level, SmokeRegion region, long tick) {
        airConnectivityService.clearCache();

        Set<SmokeCell.CellPos> topologyCells = new HashSet<>();
        for (BlockPos source : region.sources()) {
            SmokeCell.CellPos sourceCell = region.cellAt(source);
            topologyCells.add(sourceCell);
            for (int[] direction : DIRECTIONS) {
                topologyCells.add(sourceCell.offset(direction[0], direction[1], direction[2]));
            }
        }

        // Existing active cells must retain their density, but their geometry
        // properties are recalculated whenever topology is marked dirty.
        for (SmokeCell.CellPos cellPos : region.cells().keySet()) {
            topologyCells.add(cellPos);
        }

        // Exterior flags come from bounded BFS. The result is cached for this
        // topology generation rather than recomputed on every simulation step.
        Set<SmokeCell.CellPos> exterior = new HashSet<>();
        for (BlockPos source : region.sources()) {
            AirConnectivityService.ConnectivityResult result =
                    airConnectivityService.findConnectedSources(
                            level,
                            region,
                            source,
                            region.sources()
                    );
            exterior.addAll(result.exteriorCells());
        }

        for (SmokeCell.CellPos cellPos : topologyCells) {
            if (!region.containsCell(cellPos)) {
                continue;
            }

            SmokeCell cell = region.getOrCreateCell(cellPos);
            float permeability = airConnectivityService.getPermeability(level, region, cellPos);
            cell.setPermeability(permeability);
            cell.setExterior(exterior.contains(cellPos));
            if (permeability < 0.20F) {
                cell.clear();
            }
        }

        region.markTopologyClean(tick);
        region.refreshLoadedChunkIndex();
    }

    public void step(ServerLevel level, SmokeRegion region, long tick) {
        ModConfig.SmokeConfig config = ModConfig.get().smoke();

        if (!region.hasEnoughSources(config.minCampfires())) {
            region.cells().clear();
            return;
        }

        if (region.topologyDirty() || tick - region.lastTopologyTick() >= config.topologyRefreshInterval()) {
            rebuildTopology(level, region, tick);
        }

        Map<SmokeCell.CellPos, Float> nextDensity = new HashMap<>();
        Map<SmokeCell.CellPos, float[]> nextFlow = new HashMap<>();
        Set<SmokeCell.CellPos> candidates = new HashSet<>();

        for (SmokeCell cell : region.cells().values()) {
            if (!cell.active()) {
                continue;
            }

            candidates.add(cell.pos());
            for (int[] direction : DIRECTIONS) {
                candidates.add(cell.pos().offset(direction[0], direction[1], direction[2]));
            }
        }

        for (BlockPos source : region.sources()) {
            candidates.add(region.cellAt(source));
        }

        for (SmokeCell.CellPos cellPos : candidates) {
            if (!region.containsCell(cellPos)) {
                continue;
            }
            if (!level.hasChunkAt(region.cellCenter(cellPos))) {
                continue;
            }
            SmokeCell cell = region.getOrCreateCell(cellPos);
            if (cell.permeability() < 0.20F) {
                continue;
            }
            nextDensity.putIfAbsent(cellPos, 0.0F);
            nextFlow.putIfAbsent(cellPos, new float[3]);
        }

        double diffusion = Math.max(0.0D, Math.min(1.0D, config.diffusionRate()));
        double decay = Math.max(0.0D, Math.min(1.0D, config.decayRate()));
        double upwardWeight = Math.max(0.0D, config.upwardWeight());
        double lateralWeight = Math.max(0.0D, config.lateralWeight());
        double downwardWeight = Math.max(0.0D, config.downwardWeight());
        double exitRate = Math.max(0.0D, Math.min(1.0D, config.exitRate()));

        // Iterate over a snapshot to avoid ConcurrentModificationException when getOrCreateCell adds new cells
        List<SmokeCell> cellSnapshot = new ArrayList<>(region.cells().values());
        for (SmokeCell cell : cellSnapshot) {
            float density = cell.density();
            if (density <= 0.001F || !cell.active()) {
                continue;
            }

            if (cell.permeability() < 0.20F) {
                continue;
            }

            double weightSum = 0.0D;
            List<Neighbour> neighbours = new ArrayList<>(6);
            for (int[] direction : DIRECTIONS) {
                SmokeCell.CellPos neighbourPos = cell.pos().offset(direction[0], direction[1], direction[2]);
                SmokeCell neighbour = region.cells().get(neighbourPos);
                if (neighbour == null) {
                    neighbour = region.getOrCreateCell(neighbourPos);
                    neighbour.setPermeability(airConnectivityService.getPermeability(level, region, neighbourPos));
                    if (neighbour.permeability() < 0.20F) {
                        continue;
                    }
                }

                if (!region.containsCell(neighbourPos) || !level.hasChunkAt(region.cellCenter(neighbourPos))) {
                    continue;
                }

                double weight;
                if (direction[1] > 0) {
                    weight = upwardWeight;
                } else if (direction[1] < 0) {
                    weight = downwardWeight;
                } else {
                    weight = lateralWeight;
                }

                if (weight <= 0.0D) {
                    continue;
                }

                weight *= Math.max(0.05D, Math.min(1.0D, neighbour.permeability()));
                weightSum += weight;
                neighbours.add(new Neighbour(neighbourPos, direction[0], direction[1], direction[2], weight));
            }

            double transferable = density * diffusion;
            double retained = density * (1.0D - diffusion) * (1.0D - decay);
            addDensity(nextDensity, cell.pos(), (float) retained);

            if (cell.exterior()) {
                double exitLoss = density * exitRate;
                addDensity(nextDensity, cell.pos(), (float) -exitLoss);
                transferable = Math.max(0.0D, transferable - exitLoss);
            }

            if (weightSum > 0.0D && transferable > 0.0D) {
                for (Neighbour neighbour : neighbours) {
                    float amount = (float) (transferable * (neighbour.weight() / weightSum));
                    addDensity(nextDensity, neighbour.pos(), amount);
                    float[] flow = nextFlow.computeIfAbsent(neighbour.pos(), ignored -> new float[3]);
                    flow[0] += amount * neighbour.dx();
                    flow[1] += amount * neighbour.dy();
                    flow[2] += amount * neighbour.dz();
                }
            }
        }

        // Re-inject smoke at active source cells. Injection happens after the
        // transport step so a source remains visibly stable even at low density.
        for (BlockPos source : region.sources()) {
            if (!level.hasChunkAt(source)) {
                continue;
            }
            if (!SmokeManager.isActiveCampfire(level.getBlockState(source))) {
                continue;
            }

            SmokeCell.CellPos sourceCell = region.cellAt(source);
            SmokeCell cell = region.getOrCreateCell(sourceCell);
            if (cell.permeability() < 0.20F) {
                continue;
            }

            addDensity(nextDensity, sourceCell, (float) config.sourceDensityPerStep());
        }

        for (Map.Entry<SmokeCell.CellPos, SmokeCell> entry : region.cells().entrySet()) {
            SmokeCell cell = entry.getValue();
            float density = nextDensity.getOrDefault(entry.getKey(), 0.0F);
            cell.setDensity(density);

            float[] flow = nextFlow.get(entry.getKey());
            if (flow != null) {
                cell.setFlow(
                        cell.flowX() * 0.60F + clamp(flow[0], -1.0F, 1.0F) * 0.40F,
                        cell.flowY() * 0.60F + clamp(flow[1], -1.0F, 1.0F) * 0.40F,
                        cell.flowZ() * 0.60F + clamp(flow[2], -1.0F, 1.0F) * 0.40F
                );
            }

            cell.markUpdated(tick);
        }

        // Remove dead cells and keep memory bounded. A source's cell is kept by
        // re-injection on the next step, so we do not need a separate pin set.
        region.removeInactiveCells((float) config.cellRemovalThreshold());
        region.trimToBudget(config.maxCells());
        region.refreshLoadedChunkIndex();

        if (region.activeCellCount() > 0) {
            region.recordActivity(tick);
        }
    }

    private static void addDensity(Map<SmokeCell.CellPos, Float> map, SmokeCell.CellPos pos, float amount) {
        map.merge(pos, amount, Float::sum);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Neighbour(SmokeCell.CellPos pos, int dx, int dy, int dz, double weight) {
    }
}
