package ru.magnetism.smoke;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import ru.magnetism.config.ModConfig;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bounded connectivity analysis for the smoke grid.
 *
 * The search never walks the infinite world. It is constrained by both the
 * configured block radius and the maximum number of visited coarse cells.
 */
public final class AirConnectivityService {
    private static final int[][] DIRECTIONS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    private final Map<SmokeCell.CellPos, Float> permeabilityCache = new HashMap<>();
    private final Map<SmokeCell.CellPos, Boolean> exteriorCache = new HashMap<>();

    public ConnectivityResult findConnectedSources(
            ServerLevel level,
            SmokeRegion region,
            BlockPos seed,
            Set<BlockPos> candidates
    ) {
        ModConfig.SmokeConfig config = ModConfig.get().smoke();
        SmokeCell.CellPos seedCell = region.cellAt(seed);

        int maxCellDistance = Math.max(1, config.maxRegionSize() / (2 * region.cellSize()));
        SearchBounds bounds = SearchBounds.around(seedCell, maxCellDistance);

        ArrayDeque<SmokeCell.CellPos> queue = new ArrayDeque<>();
        Set<SmokeCell.CellPos> visited = new HashSet<>();
        Set<BlockPos> connectedSources = new HashSet<>();
        Set<SmokeCell.CellPos> exteriorCells = new HashSet<>();

        queue.add(seedCell);
        visited.add(seedCell);

        exteriorCache.clear();

        boolean budgetExceeded = false;
        while (!queue.isEmpty()) {
            if (visited.size() >= config.maxAirSearch()) {
                budgetExceeded = true;
                break;
            }

            SmokeCell.CellPos current = queue.removeFirst();
            if (!bounds.contains(current)) {
                continue;
            }

            if (!isCellLoaded(level, region, current)) {
                continue;
            }

            float permeability = getPermeability(level, region, current);
            if (permeability < 0.20F) {
                continue;
            }

            BlockPos center = region.cellCenter(current);
            for (BlockPos candidate : candidates) {
                if (region.cellAt(candidate).equals(current)) {
                    connectedSources.add(candidate);
                }
            }

            // Check if this cell connects to outside air
            if (isExteriorCell(level, region, current, bounds)) {
                exteriorCells.add(current);
            }

            for (int[] direction : DIRECTIONS) {
                SmokeCell.CellPos next = current.offset(direction[0], direction[1], direction[2]);
                if (visited.add(next)) {
                    queue.addLast(next);
                }
            }
        }

        return new ConnectivityResult(connectedSources, exteriorCells, budgetExceeded, permeabilityCache);
    }

    public void clearCache() {
        permeabilityCache.clear();
        exteriorCache.clear();
    }

    public float getPermeability(ServerLevel level, SmokeRegion region, SmokeCell.CellPos cell) {
        return getPermeability(level, region, cell, ModConfig.get().smoke().maxAirSearch());
    }

    private float getPermeability(ServerLevel level, SmokeRegion region, SmokeCell.CellPos cell, int ignored) {
        Float cached = permeabilityCache.get(cell);
        if (cached != null) {
            return cached;
        }

        if (!isCellLoaded(level, region, cell)) {
            return 0.0F;
        }

        int size = region.cellSize();
        int ox = cell.blockX(size);
        int oy = cell.blockY(size);
        int oz = cell.blockZ(size);
        double openVolume = 0.0D;
        int samples = size * size * size;

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    BlockPos pos = new BlockPos(ox + x, oy + y, oz + z);
                    if (!level.hasChunkAt(pos)) {
                        return 0.0F;
                    }

                    BlockState state = level.getBlockState(pos);
                    VoxelShape shape = state.getCollisionShape(level, pos);
                    if (shape.isEmpty()) {
                        openVolume += 1.0D;
                    } else {
                        // Check for partial blocks that allow airflow (doors, trapdoors, fences, etc.)
                        if (isAirPermeableBlock(state)) {
                            openVolume += 0.5D; // Partial permeability for doors, fences, etc.
                        } else {
                            AABB bounds = shape.bounds();
                            double occupied = Math.max(0.0D, Math.min(1.0D,
                                    bounds.getXsize() * bounds.getYsize() * bounds.getZsize()));
                            openVolume += 1.0D - occupied;
                        }
                    }
                }
            }
        }

        float permeability = (float) (openVolume / samples);
        permeabilityCache.put(cell, permeability);
        return permeability;
    }

    /**
     * Checks if a block allows some airflow (doors, trapdoors, fences, bars, etc.)
     */
    private boolean isAirPermeableBlock(BlockState state) {
        // Check for common permeable blocks
        if (state.is(Blocks.OAK_DOOR) || state.is(Blocks.SPRUCE_DOOR) || state.is(Blocks.BIRCH_DOOR) ||
            state.is(Blocks.JUNGLE_DOOR) || state.is(Blocks.ACACIA_DOOR) || state.is(Blocks.DARK_OAK_DOOR) ||
            state.is(Blocks.MANGROVE_DOOR) || state.is(Blocks.CHERRY_DOOR) || state.is(Blocks.BAMBOO_DOOR) ||
            state.is(Blocks.IRON_DOOR) || state.is(Blocks.CRIMSON_DOOR) || state.is(Blocks.WARPED_DOOR)) {
            // Doors allow airflow when open
            return state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
        }
        if (state.is(Blocks.OAK_TRAPDOOR) || state.is(Blocks.SPRUCE_TRAPDOOR) || state.is(Blocks.BIRCH_TRAPDOOR) ||
            state.is(Blocks.JUNGLE_TRAPDOOR) || state.is(Blocks.ACACIA_TRAPDOOR) || state.is(Blocks.DARK_OAK_TRAPDOOR) ||
            state.is(Blocks.MANGROVE_TRAPDOOR) || state.is(Blocks.CHERRY_TRAPDOOR) || state.is(Blocks.BAMBOO_TRAPDOOR) ||
            state.is(Blocks.IRON_TRAPDOOR) || state.is(Blocks.CRIMSON_TRAPDOOR) || state.is(Blocks.WARPED_TRAPDOOR)) {
            return state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
        }
        if (state.is(Blocks.OAK_FENCE) || state.is(Blocks.SPRUCE_FENCE) || state.is(Blocks.BIRCH_FENCE) ||
            state.is(Blocks.JUNGLE_FENCE) || state.is(Blocks.ACACIA_FENCE) || state.is(Blocks.DARK_OAK_FENCE) ||
            state.is(Blocks.MANGROVE_FENCE) || state.is(Blocks.CHERRY_FENCE) || state.is(Blocks.BAMBOO_FENCE) ||
            state.is(Blocks.CRIMSON_FENCE) || state.is(Blocks.WARPED_FENCE) ||
            state.is(Blocks.NETHER_BRICK_FENCE) || state.is(Blocks.IRON_BARS)) {
            return true; // Fences and bars always allow some airflow
        }
        return false;
    }

    private boolean isCellLoaded(ServerLevel level, SmokeRegion region, SmokeCell.CellPos cell) {
        return region.containsCell(cell) && level.hasChunkAt(region.cellCenter(cell));
    }

    private boolean isOutsideWorldHeight(ServerLevel level, SmokeRegion region, SmokeCell.CellPos cell) {
        int bottom = cell.blockY(region.cellSize());
        int top = bottom + region.cellSize() - 1;
        return bottom <= level.getMinY() || top >= level.getMaxY();
    }

    /**
     * Determines if a cell connects to outside air by checking for genuine
     * openings to the outside world. More conservative to keep smoke inside
     * closed rooms.
     */
    private boolean isExteriorCell(ServerLevel level, SmokeRegion region, SmokeCell.CellPos cell, SearchBounds bounds) {
        Boolean cached = exteriorCache.get(cell);
        if (cached != null) {
            return cached;
        }

        // Cell is at world height boundary (top/bottom of world)
        if (isOutsideWorldHeight(level, region, cell)) {
            exteriorCache.put(cell, true);
            return true;
        }

        // Check if cell has direct line of sight to sky through a genuine opening
        // Only count as exterior if there's a clear vertical path to sky
        BlockPos center = region.cellCenter(cell);
        if (hasDirectSkyAccess(level, center)) {
            exteriorCache.put(cell, true);
            return true;
        }

        // Check if any block in this cell is a genuinely open passage to outside
        // (open doors, trapdoors, fence gates, broken walls)
        int size = region.cellSize();
        int ox = cell.blockX(size);
        int oy = cell.blockY(size);
        int oz = cell.blockZ(size);

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    BlockPos pos = new BlockPos(ox + x, oy + y, oz + z);
                    BlockState state = level.getBlockState(pos);
                    if (isGenuineOpening(state)) {
                        // Verify this opening actually leads outside by checking
                        // if there's a path from here to world boundary or sky
                        if (hasPathToOutside(level, region, pos)) {
                            exteriorCache.put(cell, true);
                            return true;
                        }
                    }
                }
            }
        }

        exteriorCache.put(cell, false);
        return false;
    }

    /**
     * Checks if a position has a direct vertical path to the sky (no blocks above).
     */
    private boolean hasDirectSkyAccess(ServerLevel level, BlockPos pos) {
        // Check column above for any solid block
        for (int y = pos.getY() + 1; y <= level.getMaxY(); y++) {
            BlockPos above = new BlockPos(pos.getX(), y, pos.getZ());
            BlockState state = level.getBlockState(above);
            if (!state.getCollisionShape(level, above).isEmpty()) {
                return false; // Blocked by a solid block
            }
        }
        return true; // Clear path to sky
    }

    /**
     * Checks if a block state represents a genuine opening to outside air.
     * Only counts truly open passages, not just permeable blocks.
     */
    private boolean isGenuineOpening(BlockState state) {
        // Open doors
        if (state.is(Blocks.OAK_DOOR) || state.is(Blocks.SPRUCE_DOOR) || state.is(Blocks.BIRCH_DOOR) ||
            state.is(Blocks.JUNGLE_DOOR) || state.is(Blocks.ACACIA_DOOR) || state.is(Blocks.DARK_OAK_DOOR) ||
            state.is(Blocks.MANGROVE_DOOR) || state.is(Blocks.CHERRY_DOOR) || state.is(Blocks.BAMBOO_DOOR) ||
            state.is(Blocks.IRON_DOOR) || state.is(Blocks.CRIMSON_DOOR) || state.is(Blocks.WARPED_DOOR)) {
            return state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
        }
        // Open trapdoors
        if (state.is(Blocks.OAK_TRAPDOOR) || state.is(Blocks.SPRUCE_TRAPDOOR) || state.is(Blocks.BIRCH_TRAPDOOR) ||
            state.is(Blocks.JUNGLE_TRAPDOOR) || state.is(Blocks.ACACIA_TRAPDOOR) || state.is(Blocks.DARK_OAK_TRAPDOOR) ||
            state.is(Blocks.MANGROVE_TRAPDOOR) || state.is(Blocks.CHERRY_TRAPDOOR) || state.is(Blocks.BAMBOO_TRAPDOOR) ||
            state.is(Blocks.IRON_TRAPDOOR) || state.is(Blocks.CRIMSON_TRAPDOOR) || state.is(Blocks.WARPED_TRAPDOOR)) {
            return state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
        }
        // Fence gates (when open)
        if (state.is(Blocks.OAK_FENCE_GATE) || state.is(Blocks.SPRUCE_FENCE_GATE) || state.is(Blocks.BIRCH_FENCE_GATE) ||
            state.is(Blocks.JUNGLE_FENCE_GATE) || state.is(Blocks.ACACIA_FENCE_GATE) || state.is(Blocks.DARK_OAK_FENCE_GATE) ||
            state.is(Blocks.MANGROVE_FENCE_GATE) || state.is(Blocks.CHERRY_FENCE_GATE) || state.is(Blocks.BAMBOO_FENCE_GATE) ||
            state.is(Blocks.CRIMSON_FENCE_GATE) || state.is(Blocks.WARPED_FENCE_GATE)) {
            return state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
        }
        // Air/gaps (missing blocks)
        return state.isAir();
    }

    /**
     * Quick check if a position has a reasonable path to outside.
     * Uses a limited BFS to avoid expensive computation.
     */
    private boolean hasPathToOutside(ServerLevel level, SmokeRegion region, BlockPos startPos) {
        // Simple raycast in 6 directions to see if we can reach world boundary or sky
        int[][] directions = {{1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}};
        int maxDist = Math.min(region.maxX() - region.minX(), region.maxZ() - region.minZ()) / 2;
        maxDist = Math.max(8, Math.min(maxDist, 32));

        for (int[] dir : directions) {
            BlockPos pos = startPos;
            for (int d = 0; d < maxDist; d++) {
                pos = pos.offset(dir[0], dir[1], dir[2]);
                if (!level.hasChunkAt(pos)) {
                    return true; // Reached unloaded chunk = outside
                }
                BlockState state = level.getBlockState(pos);
                if (!state.getCollisionShape(level, pos).isEmpty()) {
                    break; // Hit a solid block in this direction
                }
                // Check if we can see sky from here
                if (level.canSeeSky(pos) && pos.getY() > startPos.getY()) {
                    return true;
                }
            }
        }
        return false;
    }

    public record ConnectivityResult(
            Set<BlockPos> connectedSources,
            Set<SmokeCell.CellPos> exteriorCells,
            boolean budgetExceeded,
            Map<SmokeCell.CellPos, Float> permeability
    ) {
    }

    private record SearchBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        static SearchBounds around(SmokeCell.CellPos center, int radius) {
            return new SearchBounds(
                    center.x() - radius,
                    center.y() - radius,
                    center.z() - radius,
                    center.x() + radius,
                    center.y() + radius,
                    center.z() + radius
            );
        }

        boolean contains(SmokeCell.CellPos cell) {
            return cell.x() >= minX && cell.x() <= maxX
                    && cell.y() >= minY && cell.y() <= maxY
                    && cell.z() >= minZ && cell.z() <= maxZ;
        }

        boolean isBoundary(SmokeCell.CellPos cell) {
            return cell.x() == minX || cell.x() == maxX
                    || cell.y() == minY || cell.y() == maxY
                    || cell.z() == minZ || cell.z() == maxZ;
        }
    }
}
