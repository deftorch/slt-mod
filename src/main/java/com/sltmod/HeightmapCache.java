package com.sltmod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced heightmap cache with tiered memory pool integration
 * Supports multiple sampling grid sizes (3x3, 5x5, 7x7)
 * Thread-safe with neighbor chunk access
 */
public class HeightmapCache {

    // Grid size constants
    private static final int GRID_3X3 = 3;
    private static final int GRID_5X5 = 5;
    private static final int GRID_7X7 = 7;

    private int[][] heightMap;
    private final ChunkAccess chunk;
    private final ServerLevel level;
    private final ChunkPos chunkPos;
    private final int minY;
    private final int maxY;
    private final boolean fromPool;
    private final int gridSize;

    // Neighbor chunk cache (thread-safe)
    private final ConcurrentHashMap<ChunkPos, int[][]> neighborCache = new ConcurrentHashMap<>();

    // Statistics
    private long creationTime;
    private int neighborLookups = 0;
    private int cacheHits = 0;

    /**
     * Create heightmap cache from chunk
     * Automatically acquires from tiered memory pool
     *
     * @param chunk The chunk to cache (must be LevelChunk to access Level)
     */
    public HeightmapCache(LevelChunk chunk) {
        this.chunk = chunk;
        this.level = (ServerLevel) chunk.getLevel();
        this.chunkPos = chunk.getPos();
        this.minY = chunk.getMinBuildHeight();
        this.maxY = chunk.getMaxBuildHeight();
        this.creationTime = System.nanoTime();

        // Determine grid size from config
        if (LayeredTerrainConfig.USE_7X7_SAMPLING.get()) {
            this.gridSize = GRID_7X7;
        } else if (LayeredTerrainConfig.USE_5X5_SAMPLING.get()) {
            this.gridSize = GRID_5X5;
        } else {
            this.gridSize = GRID_3X3;
        }

        // Acquire from pool
        this.heightMap = TieredMemoryPool.acquire();
        this.fromPool = LayeredTerrainConfig.ENABLE_MEMORY_POOLING.get();

        // Compute heights
        computeHeights();
    }

    /**
     * Compute all heights in chunk
     */
    private void computeHeights() {
        long startTime = System.nanoTime();

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                heightMap[x][z] = computeHeight(chunk, x, z);
            }
        }

        if (LayeredTerrainConfig.ENABLE_PROFILING.get()) {
            long duration = System.nanoTime() - startTime;
            ProfilingMetrics.record("heightmap_cache_compute", duration);
        }
    }

    /**
     * Get height at local coordinates
     * Handles out-of-bounds by accessing neighbor chunks
     *
     * @param localX Local X coordinate (can be outside 0-15)
     * @param localZ Local Z coordinate (can be outside 0-15)
     * @return Height at position
     */
    public int getHeight(int localX, int localZ) {
        // Fast path: within chunk bounds
        if (localX >= 0 && localX < 16 && localZ >= 0 && localZ < 16) {
            return heightMap[localX][localZ];
        }

        // Slow path: neighbor chunk access
        return getHeightFromNeighbor(localX, localZ);
    }

    /**
     * Get height from neighbor chunk with caching
     */
    private int getHeightFromNeighbor(int localX, int localZ) {
        neighborLookups++;

        if (level == null) {
            return getEdgeFallback(localX, localZ);
        }

        // Calculate world coordinates
        int worldX = chunkPos.getMinBlockX() + localX;
        int worldZ = chunkPos.getMinBlockZ() + localZ;
        ChunkPos neighborPos = new ChunkPos(worldX >> 4, worldZ >> 4);

        // Check cache first
        int[][] neighborHeights = neighborCache.get(neighborPos);
        if (neighborHeights != null) {
            cacheHits++;
            int nx = worldX & 15;
            int nz = worldZ & 15;
            return neighborHeights[nx][nz];
        }

        // Load neighbor chunk
        ChunkAccess neighborChunk = level.getChunk(
            neighborPos.x,
            neighborPos.z,
            ChunkStatus.FULL,
            false
        );

        if (neighborChunk != null && neighborChunk.getStatus().isOrAfter(ChunkStatus.FULL)) {
            // Compute and cache neighbor heights
            neighborHeights = new int[16][16];
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    neighborHeights[x][z] = computeHeight(neighborChunk, x, z);
                }
            }
            neighborCache.put(neighborPos, neighborHeights);

            int nx = worldX & 15;
            int nz = worldZ & 15;
            return neighborHeights[nx][nz];
        }

        // Fallback if neighbor unavailable
        return getEdgeFallback(localX, localZ);
    }

    /**
     * Fallback for edge cases - clamp to nearest valid position
     */
    private int getEdgeFallback(int localX, int localZ) {
        int clampedX = Mth.clamp(localX, 0, 15);
        int clampedZ = Mth.clamp(localZ, 0, 15);
        return heightMap[clampedX][clampedZ];
    }

    /**
     * Compute height at specific position within chunk
     *
     * @param chunk Chunk to search
     * @param localX Local X coordinate
     * @param localZ Local Z coordinate
     * @return Highest solid non-bedrock block Y coordinate
     */
    private int computeHeight(ChunkAccess chunk, int localX, int localZ) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // Scan from top down for first solid block
        for (int y = maxY - 1; y >= minY; y--) {
            pos.set(localX, y, localZ);
            BlockState state = chunk.getBlockState(pos);

            // Skip air, fluids, and bedrock
            if (!state.isAir() &&
                state.getFluidState().isEmpty() &&
                state.getBlock() != Blocks.BEDROCK) {
                return y;
            }
        }

        return minY;
    }

    /**
     * Get grid size being used for sampling
     */
    public int getGridSize() {
        return gridSize;
    }

    /**
     * Get raw heightmap data
     * WARNING: Direct access - be careful with modifications
     */
    public int[][] getRawData() {
        return heightMap;
    }

    /**
     * Get cache statistics
     */
    public CacheStats getStats() {
        long lifetime = System.nanoTime() - creationTime;
        double hitRate = neighborLookups > 0
            ? (double) cacheHits / neighborLookups * 100
            : 0;

        return new CacheStats(
            neighborLookups,
            cacheHits,
            hitRate,
            lifetime / 1_000_000.0, // Convert to ms
            neighborCache.size()
        );
    }

    public static class CacheStats {
        public final int neighborLookups;
        public final int cacheHits;
        public final double hitRate;
        public final double lifetimeMs;
        public final int neighborsCached;

        CacheStats(int lookups, int hits, double rate, double lifetime, int neighbors) {
            this.neighborLookups = lookups;
            this.cacheHits = hits;
            this.hitRate = rate;
            this.lifetimeMs = lifetime;
            this.neighborsCached = neighbors;
        }
    }

    /**
     * Release cache back to pool
     * MUST be called when done to prevent memory leaks
     */
    public void release() {
        if (fromPool && heightMap != null) {
            TieredMemoryPool.release(heightMap);
            heightMap = null;
        }

        // Clear neighbor cache
        neighborCache.clear();

        // Log statistics if debug enabled
        if (LayeredTerrainConfig.DEBUG_MODE.get()) {
            CacheStats stats = getStats();
            if (stats.neighborLookups > 0) {
                LayeredTerrainMod.LOGGER.debug(
                    "HeightmapCache released: {} neighbor lookups, {:.1f}% hit rate, {:.2f}ms lifetime",
                    stats.neighborLookups, stats.hitRate, stats.lifetimeMs
                );
            }
        }
    }

    /**
     * Validate cache integrity (for testing)
     */
    public boolean validate() {
        if (heightMap == null) return false;
        if (heightMap.length != 16) return false;

        for (int x = 0; x < 16; x++) {
            if (heightMap[x] == null || heightMap[x].length != 16) {
                return false;
            }

            for (int z = 0; z < 16; z++) {
                int height = heightMap[x][z];
                if (height < minY || height >= maxY) {
                    return false;
                }
            }
        }

        return true;
    }
}
