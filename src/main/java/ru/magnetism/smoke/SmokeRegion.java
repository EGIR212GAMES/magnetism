package ru.magnetism.smoke;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import ru.magnetism.config.ModConfig;
import ru.magnetism.util.ModLogger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Runtime smoke simulation region. The region is intentionally not a saved
 * world object: it is derived from loaded campfires and rebuilt after restart.
 */
public final class SmokeRegion {
    private final ServerLevel level;
    private final int cellSize;
    private final Set<BlockPos> sources = new HashSet<>();
    private final Map<SmokeCell.CellPos, SmokeCell> cells = new HashMap<>();
    private final Set<Long> loadedChunks = new HashSet<>();

    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;
    private boolean topologyDirty = true;
    private long lastActivityTick;
    private long lastTopologyTick;

    public SmokeRegion(ServerLevel level, Collection<BlockPos> sources, int cellSize, long tick) {
        this.level = level;
        this.cellSize = cellSize;
        this.sources.addAll(sources);
        recomputeBounds(ModConfig.get().smoke().maxRegionSize());
        this.lastActivityTick = tick;
    }

    public ServerLevel level() {
        return level;
    }

    public Set<BlockPos> sources() {
        return Set.copyOf(sources);
    }

    public Map<SmokeCell.CellPos, SmokeCell> cells() {
        return cells;
    }

    public Set<Long> loadedChunks() {
        return Set.copyOf(loadedChunks);
    }

    public int cellSize() {
        return cellSize;
    }

    public long lastActivityTick() {
        return lastActivityTick;
    }

    public long lastTopologyTick() {
        return lastTopologyTick;
    }

    public boolean topologyDirty() {
        return topologyDirty;
    }

    public void markTopologyDirty() {
        topologyDirty = true;
    }

    public void markTopologyClean(long tick) {
        topologyDirty = false;
        lastTopologyTick = tick;
    }

    public boolean updateSources(Collection<BlockPos> newSources) {
        Set<BlockPos> normalized = new HashSet<>(newSources);
        if (normalized.equals(sources)) {
            return false;
        }

        sources.clear();
        sources.addAll(normalized);
        recomputeBounds(ModConfig.get().smoke().maxRegionSize());
        topologyDirty = true;
        return true;
    }

    public void recordActivity(long tick) {
        lastActivityTick = tick;
    }

    public boolean hasEnoughSources(int minimum) {
        return sources.size() >= minimum;
    }

    public boolean containsBlock(BlockPos pos) {
        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public boolean containsCell(SmokeCell.CellPos cell) {
        int bx = cell.blockX(cellSize);
        int by = cell.blockY(cellSize);
        int bz = cell.blockZ(cellSize);
        return bx >= minX && bx <= maxX
                && by >= minY && by <= maxY
                && bz >= minZ && bz <= maxZ;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    public SmokeCell getOrCreateCell(SmokeCell.CellPos pos) {
        return cells.computeIfAbsent(pos, SmokeCell::new);
    }

    public void removeInactiveCells(float threshold) {
        cells.values().removeIf(cell -> cell.density() <= threshold);
    }

    public SmokeCell.CellPos cellAt(BlockPos pos) {
        return new SmokeCell.CellPos(
                Math.floorDiv(pos.getX(), cellSize),
                Math.floorDiv(pos.getY(), cellSize),
                Math.floorDiv(pos.getZ(), cellSize)
        );
    }

    public BlockPos cellCenter(SmokeCell.CellPos pos) {
        int half = cellSize / 2;
        return new BlockPos(pos.blockX(cellSize) + half, pos.blockY(cellSize) + half, pos.blockZ(cellSize) + half);
    }

    public void refreshLoadedChunkIndex() {
        loadedChunks.clear();
        for (SmokeCell cell : cells.values()) {
            if (!cell.active()) {
                continue;
            }
            BlockPos center = cellCenter(cell.pos());
            loadedChunks.add(chunkKey(center.getX() >> 4, center.getZ() >> 4));
        }
    }

    public int activeCellCount() {
        int count = 0;
        for (SmokeCell cell : cells.values()) {
            if (cell.active()) {
                count++;
            }
        }
        return count;
    }

    public void clearIfInactive() {
        if (sources.isEmpty() || activeCellCount() == 0) {
            cells.clear();
        }
    }

    public void trimToBudget(int maxCells) {
        if (cells.size() <= maxCells) {
            return;
        }

        ArrayList<SmokeCell.CellPos> positions = new ArrayList<>(cells.keySet());
        positions.sort((left, right) -> Float.compare(cells.get(right).density(), cells.get(left).density()));
        Set<SmokeCell.CellPos> keep = new HashSet<>(positions.subList(0, Math.max(0, maxCells)));
        cells.keySet().removeIf(pos -> !keep.contains(pos));

        if (cells.size() >= maxCells) {
            ModLogger.debug("SmokeRegion hit maxCells budget: {}", maxCells);
        }
    }

    public void dispose() {
        cells.clear();
        sources.clear();
        loadedChunks.clear();
    }

    private void recomputeBounds(int maxRegionSize) {
        if (sources.isEmpty()) {
            minX = minY = minZ = maxX = maxY = maxZ = 0;
            return;
        }

        int rawMinX = Integer.MAX_VALUE;
        int rawMinY = Integer.MAX_VALUE;
        int rawMinZ = Integer.MAX_VALUE;
        int rawMaxX = Integer.MIN_VALUE;
        int rawMaxY = Integer.MIN_VALUE;
        int rawMaxZ = Integer.MIN_VALUE;

        for (BlockPos pos : sources) {
            rawMinX = Math.min(rawMinX, pos.getX());
            rawMinY = Math.min(rawMinY, pos.getY());
            rawMinZ = Math.min(rawMinZ, pos.getZ());
            rawMaxX = Math.max(rawMaxX, pos.getX());
            rawMaxY = Math.max(rawMaxY, pos.getY());
            rawMaxZ = Math.max(rawMaxZ, pos.getZ());
        }

        int padding = Math.min(8, Math.max(2, cellSize));
        int half = Math.max(1, maxRegionSize / 2);
        int centerX = (rawMinX + rawMaxX) / 2;
        int centerY = (rawMinY + rawMaxY) / 2;
        int centerZ = (rawMinZ + rawMaxZ) / 2;

        minX = Math.max(rawMinX - padding, centerX - half);
        maxX = Math.min(rawMaxX + padding, centerX + half);
        minY = Math.max(level.getMinY(), Math.max(rawMinY - padding, centerY - half));
        maxY = Math.min(level.getMaxY(), Math.min(rawMaxY + padding, centerY + half));
        minZ = Math.max(rawMinZ - padding, centerZ - half);
        maxZ = Math.min(rawMaxZ + padding, centerZ + half);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }
}
