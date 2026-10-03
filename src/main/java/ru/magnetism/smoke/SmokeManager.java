package ru.magnetism.smoke;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import ru.magnetism.config.ModConfig;
import ru.magnetism.server.ServerTickManager;
import ru.magnetism.util.ModLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns all runtime smoke state for all loaded server levels.
 *
 * No smoke state is persisted: regions are derived from campfire sources on
 * chunk load and rebuilt after server restart. This avoids serializing a large,
 * highly dynamic voxel field.
 */
public final class SmokeManager {
    private static final Map<ServerLevel, LevelSmokeState> LEVELS = new HashMap<>();
    private static final Map<ResourceKey<Level>, ServerLevel> LEVEL_BY_KEY = new HashMap<>();
    private static boolean initialized;

    private static final SmokeExposureService EXPOSURE_SERVICE = new SmokeExposureService();

    private SmokeManager() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }

        ServerLevelEvents.LOAD.register(SmokeManager::onLevelLoad);
        ServerLevelEvents.UNLOAD.register(SmokeManager::onLevelUnload);
        ServerChunkEvents.CHUNK_LOAD.register(SmokeManager::onChunkLoad);
        ServerChunkEvents.CHUNK_UNLOAD.register(SmokeManager::onChunkUnload);
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register(SmokeManager::onBlockEntityLoad);
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register(SmokeManager::onBlockEntityUnload);

        ServerTickManager.registerEndTickTask(SmokeManager::tick);
        initialized = true;
        ModLogger.info("Smoke Simulation Engine initialized.");
    }

    public static SmokeExposureService exposureService() {
        return EXPOSURE_SERVICE;
    }

    public static void clear() {
        for (LevelSmokeState state : LEVELS.values()) {
            state.dispose();
        }
        LEVELS.clear();
        LEVEL_BY_KEY.clear();
    }

    public static boolean isActiveCampfire(net.minecraft.world.level.block.state.BlockState state) {
        if (!(state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE))) {
            return false;
        }
        return state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT);
    }

    public static float sampleDensity(ServerPlayer player) {
        LevelSmokeState state = LEVELS.get((ServerLevel) player.level());
        if (state == null) {
            return 0.0F;
        }
        return state.sampleDensity(player.blockPosition());
    }

    private static void onLevelLoad(MinecraftServer server, ServerLevel level) {
        LevelSmokeState state = new LevelSmokeState(level);
        LEVELS.put(level, state);
        LEVEL_BY_KEY.put(level.dimension(), level);
    }

    private static void onLevelUnload(MinecraftServer server, ServerLevel level) {
        LevelSmokeState state = LEVELS.remove(level);
        LEVEL_BY_KEY.remove(level.dimension());
        if (state != null) {
            state.dispose();
        }
    }

    private static void onChunkLoad(ServerLevel level, LevelChunk chunk, boolean worldGen) {
        LevelSmokeState state = LEVELS.get(level);
        if (state == null) {
            state = new LevelSmokeState(level);
            LEVELS.put(level, state);
            LEVEL_BY_KEY.put(level.dimension(), level);
        }
        state.onChunkLoad(chunk);
    }

    private static void onChunkUnload(ServerLevel level, LevelChunk chunk) {
        LevelSmokeState state = LEVELS.get(level);
        if (state != null) {
            state.onChunkUnload(chunk);
        }
    }

    private static void onBlockEntityLoad(BlockEntity blockEntity, ServerLevel level) {
        if (!(blockEntity instanceof CampfireBlockEntity)) {
            return;
        }
        LevelSmokeState state = LEVELS.get(level);
        if (state == null) {
            state = new LevelSmokeState(level);
            LEVELS.put(level, state);
            LEVEL_BY_KEY.put(level.dimension(), level);
        }
        state.onPotentialSourceLoaded(blockEntity.getBlockPos());
    }

    private static void onBlockEntityUnload(BlockEntity blockEntity, ServerLevel level) {
        if (!(blockEntity instanceof CampfireBlockEntity)) {
            return;
        }
        LevelSmokeState state = LEVELS.get(level);
        if (state != null) {
            state.onPotentialSourceUnloaded(blockEntity.getBlockPos());
        }
    }

    private static void tick(MinecraftServer server) {
        ModConfig.SmokeConfig config = ModConfig.get().smoke();
        if (!ModConfig.get().enabled() || !config.enabled()) {
            return;
        }

        long tick = ServerTickManager.getTickCount();
        for (LevelSmokeState state : List.copyOf(LEVELS.values())) {
            state.tick(tick, config);
        }
    }

    private static final class LevelSmokeState {
        private final ServerLevel level;
        private final Map<Long, Set<BlockPos>> sourceIndex = new HashMap<>();
        private final Set<Long> dirtySourceChunks = new HashSet<>();
        private final List<SmokeRegion> regions = new ArrayList<>();
        private final AirConnectivityService connectivity = new AirConnectivityService();
        private final SmokeSimulation simulation = new SmokeSimulation(connectivity);
        private final SmokeParticleService particles = new SmokeParticleService();

        private long lastSourceRefreshTick;
        private long lastParticleTick;
        private int regionCursor;

        private LevelSmokeState(ServerLevel level) {
            this.level = level;
        }

        private void onChunkLoad(LevelChunk chunk) {
            long key = chunkKey(chunk.getPos().x(), chunk.getPos().z());
            removeChunkSources(key);

            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (blockEntity instanceof CampfireBlockEntity) {
                    trackPotentialSource(blockEntity.getBlockPos());
                }
            }

            dirtySourceChunks.add(key);
            markNearbyRegionsDirty(chunk.getPos().x(), chunk.getPos().z());
        }

        private void onChunkUnload(LevelChunk chunk) {
            long key = chunkKey(chunk.getPos().x(), chunk.getPos().z());
            removeChunkSources(key);
            dirtySourceChunks.add(key);
            markNearbyRegionsDirty(chunk.getPos().x(), chunk.getPos().z());
        }

        private void onPotentialSourceLoaded(BlockPos pos) {
            if (!level.hasChunkAt(pos)) {
                return;
            }
            trackPotentialSource(pos);
            dirtySourceChunks.add(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
            markNearbyRegionsDirty(pos.getX() >> 4, pos.getZ() >> 4);
        }

        private void onPotentialSourceUnloaded(BlockPos pos) {
            removeSource(pos);
            dirtySourceChunks.add(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
            markNearbyRegionsDirty(pos.getX() >> 4, pos.getZ() >> 4);
        }

        private void tick(long tick, ModConfig.SmokeConfig config) {
            if (tick - lastSourceRefreshTick >= config.sourceRefreshInterval()) {
                refreshSourceStates(tick, config);
                lastSourceRefreshTick = tick;
            }

            if (!dirtySourceChunks.isEmpty()) {
                rebuildDirtyRegions(tick, config);
            }

            if (regions.isEmpty()) {
                return;
            }

            int maxRegions = Math.min(config.maxActiveRegions(), regions.size());
            int processed = 0;
            while (processed < maxRegions && !regions.isEmpty()) {
                if (regionCursor >= regions.size()) {
                    regionCursor = 0;
                }

                SmokeRegion region = regions.get(regionCursor);
                regionCursor++;
                processed++;

                if (!hasLoadedSource(region)) {
                    region.dispose();
                    regions.remove(region);
                    if (regionCursor > 0) {
                        regionCursor--;
                    }
                    continue;
                }

                if (tick % config.updateInterval() == 0) {
                    simulation.step(level, region, tick);
                }

                if (tick % config.particleUpdateInterval() == 0 && tick != lastParticleTick) {
                    particles.emit(level, region, tick);
                }

                if (region.activeCellCount() == 0 && tick - region.lastActivityTick() > config.regionIdleTimeout()) {
                    region.dispose();
                    regions.remove(region);
                    if (regionCursor > 0) {
                        regionCursor--;
                    }
                }
            }

            lastParticleTick = tick;
        }

        private void refreshSourceStates(long tick, ModConfig.SmokeConfig config) {
            int budget = Math.max(1, config.sourceRefreshBudget());
            int checked = 0;

            outer:
            for (Set<BlockPos> positions : sourceIndex.values()) {
                for (BlockPos pos : positions) {
                    if (checked++ >= budget) {
                        break outer;
                    }
                    dirtySourceChunks.add(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
                    if (!level.hasChunkAt(pos)) {
                        continue;
                    }
                    // The periodic state check is deliberately sparse and budgeted.
                    // Block-entity load/unload events handle most source changes immediately;
                    // this catches lit/unlit transitions of an existing campfire BE.
                    isActiveCampfire(level.getBlockState(pos));
                }
            }
        }

        private void rebuildDirtyRegions(long tick, ModConfig.SmokeConfig config) {
            Set<Long> dirty = Set.copyOf(dirtySourceChunks);
            dirtySourceChunks.clear();

            for (long chunkKey : dirty) {
                int chunkX = (int) (chunkKey >> 32);
                int chunkZ = (int) chunkKey;

                Set<BlockPos> candidateSources = collectSourcesAround(chunkX, chunkZ, config.maxRegionSize());
                refreshExistingRegionsAround(candidateSources, chunkX, chunkZ, config, tick);

                if (candidateSources.size() < config.minCampfires()) {
                    continue;
                }

                discoverAndApplyRegions(candidateSources, tick, config);
            }
        }

        private void refreshExistingRegionsAround(Set<BlockPos> activeSources, int chunkX, int chunkZ, ModConfig.SmokeConfig config, long tick) {
            int radius = Math.max(1, (config.maxRegionSize() + 15) >> 4);
            for (SmokeRegion region : List.copyOf(regions)) {
                boolean nearby = false;
                for (BlockPos source : region.sources()) {
                    int sx = source.getX() >> 4;
                    int sz = source.getZ() >> 4;
                    if (Math.abs(sx - chunkX) <= radius && Math.abs(sz - chunkZ) <= radius) {
                        nearby = true;
                        break;
                    }
                }
                if (!nearby) {
                    continue;
                }

                Set<BlockPos> remaining = new HashSet<>();
                for (BlockPos source : region.sources()) {
                    if (activeSources.contains(source)) {
                        remaining.add(source);
                    }
                }

                if (remaining.size() < config.minCampfires()) {
                    region.dispose();
                    regions.remove(region);
                } else {
                    region.updateSources(remaining);
                    region.markTopologyDirty();
                }
            }
        }

        private void discoverAndApplyRegions(Set<BlockPos> candidates, long tick, ModConfig.SmokeConfig config) {
            Set<BlockPos> unresolved = new HashSet<>(candidates);
            while (!unresolved.isEmpty()) {
                BlockPos seed = unresolved.iterator().next();
                unresolved.remove(seed);

                SmokeRegion probe = new SmokeRegion(level, Set.of(seed), config.cellSize(), tick);
                AirConnectivityService.ConnectivityResult result =
                        connectivity.findConnectedSources(level, probe, seed, candidates);
                probe.dispose();

                Set<BlockPos> connected = new HashSet<>(result.connectedSources());
                connected.retainAll(candidates);
                if (connected.size() < config.minCampfires()) {
                    continue;
                }

                removeOverlappingRegions(connected);
                SmokeRegion region = new SmokeRegion(level, connected, config.cellSize(), tick);
                regions.add(region);
                region.markTopologyDirty();

                unresolved.removeAll(connected);
                if (regions.size() >= config.maxActiveRegions()) {
                    break;
                }
            }
        }

        private void removeOverlappingRegions(Set<BlockPos> sources) {
            regions.removeIf(region -> {
                boolean overlaps = !Set.copyOf(region.sources()).stream().noneMatch(sources::contains);
                if (overlaps) {
                    region.dispose();
                }
                return overlaps;
            });
        }

        private Set<BlockPos> collectSourcesAround(int chunkX, int chunkZ, int radiusBlocks) {
            int chunkRadius = Math.max(1, (radiusBlocks + 15) >> 4);
            Set<BlockPos> result = new HashSet<>();

            for (int x = chunkX - chunkRadius; x <= chunkX + chunkRadius; x++) {
                for (int z = chunkZ - chunkRadius; z <= chunkZ + chunkRadius; z++) {
                    Set<BlockPos> positions = sourceIndex.get(chunkKey(x, z));
                    if (positions == null) {
                        continue;
                    }
                    for (BlockPos pos : positions) {
                        if (level.hasChunkAt(pos) && isActiveCampfire(level.getBlockState(pos))) {
                            result.add(pos);
                        }
                    }
                }
            }
            return result;
        }

        private boolean hasLoadedSource(SmokeRegion region) {
            for (BlockPos source : region.sources()) {
                if (level.hasChunkAt(source) && isActiveCampfire(level.getBlockState(source))) {
                    return true;
                }
            }
            return false;
        }

        private float sampleDensity(BlockPos pos) {
            float max = 0.0F;
            for (SmokeRegion region : regions) {
                if (!region.containsBlock(pos)) {
                    continue;
                }
                SmokeCell cell = region.cells().get(region.cellAt(pos));
                if (cell != null) {
                    max = Math.max(max, cell.density());
                }
            }
            return max;
        }

        private void trackPotentialSource(BlockPos pos) {
            long key = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
            sourceIndex.computeIfAbsent(key, ignored -> new HashSet<>()).add(pos.immutable());
        }

        private void removeSource(BlockPos pos) {
            long key = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
            Set<BlockPos> positions = sourceIndex.get(key);
            if (positions == null) {
                return;
            }
            positions.remove(pos);
            if (positions.isEmpty()) {
                sourceIndex.remove(key);
            }
        }

        private void removeChunkSources(long key) {
            sourceIndex.remove(key);
        }

        private void markNearbyRegionsDirty(int chunkX, int chunkZ) {
            int radius = Math.max(1, (ModConfig.get().smoke().maxRegionSize() + 15) >> 4);
            for (SmokeRegion region : regions) {
                for (long loadedChunk : region.loadedChunks()) {
                    int rx = (int) (loadedChunk >> 32);
                    int rz = (int) loadedChunk;
                    if (Math.abs(rx - chunkX) <= radius && Math.abs(rz - chunkZ) <= radius) {
                        region.markTopologyDirty();
                        break;
                    }
                }
            }
        }

        private void dispose() {
            for (SmokeRegion region : regions) {
                region.dispose();
            }
            regions.clear();
            sourceIndex.clear();
            dirtySourceChunks.clear();
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }
}
