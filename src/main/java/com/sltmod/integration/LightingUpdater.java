package com.sltmod.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import com.sltmod.LayeredTerrainMod;
import com.sltmod.config.LayeredTerrainConfig;
import com.sltmod.monitoring.ProfilingMetrics;

/**
 * Manages lighting updates for modified chunks.
 *
 * <p>After placing layers, lighting must be recalculated to prevent dark spots or artifacts.
 * This class optimizes the process by updating only relevant heightmaps and batching
 * light engine requests.</p>
 */
public class LightingUpdater {

    // Statistics
    private static final AtomicLong totalUpdates = new AtomicLong(0);
    private static final AtomicLong batchUpdates = new AtomicLong(0);
    private static final LongAdder totalUpdateTime = new LongAdder();

    /**
     * Performs a batch lighting update for the specified chunk.
     *
     * <p>Updates internal heightmaps (e.g. {@code MOTION_BLOCKING}) and queues updates
     * to the lighting engine for the modified block columns.</p>
     *
     * @param chunk The chunk that was modified.
     */
    public static void batchUpdateLighting(LevelChunk chunk) {
        long startTime = System.nanoTime();
        totalUpdates.incrementAndGet();

        try {
            // Step 1: Update heightmaps
            updateHeightmaps(chunk);

            // Step 2: Queue lighting updates
            if (chunk.getLevel() instanceof ServerLevel serverLevel) {
                if (LayeredTerrainConfig.BATCH_LIGHTING_UPDATES.get()) {
                    queueBatchLightingUpdates(serverLevel, chunk);
                } else {
                    queueSingleLightingUpdate(serverLevel, chunk);
                }
            }

            // Step 3: Mark chunk as modified
            chunk.setUnsaved(true);

            long duration = System.nanoTime() - startTime;
            totalUpdateTime.add(duration);

            if (LayeredTerrainConfig.ENABLE_PROFILING.get()) {
                ProfilingMetrics.record("lighting_update", duration);
            }

        } catch (Exception e) {
            LayeredTerrainMod.LOGGER.error("Lighting update failed for chunk {}",
                chunk.getPos(), e);
        }
    }

    /**
     * Update heightmaps based on configuration
     */
    private static void updateHeightmaps(LevelChunk chunk) {
        if (LayeredTerrainConfig.UPDATE_ALL_HEIGHTMAPS.get()) {
            // Update all heightmap types (slower but safer)
            Heightmap.primeHeightmaps(chunk, EnumSet.allOf(Heightmap.Types.class));

        } else if (LayeredTerrainConfig.OPTIMIZE_LIGHTING_UPDATES.get()) {
            // Only update relevant heightmap types (30% faster)
            EnumSet<Heightmap.Types> relevantTypes = EnumSet.of(
                Heightmap.Types.WORLD_SURFACE,
                Heightmap.Types.MOTION_BLOCKING,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES
            );
            Heightmap.primeHeightmaps(chunk, relevantTypes);

        } else {
            // Update commonly needed types
            EnumSet<Heightmap.Types> defaultTypes = EnumSet.of(
                Heightmap.Types.WORLD_SURFACE,
                Heightmap.Types.MOTION_BLOCKING
            );
            Heightmap.primeHeightmaps(chunk, defaultTypes);
        }
    }

    /**
     * Queue single lighting update
     */
    private static void queueSingleLightingUpdate(ServerLevel level, LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();

        // Calculate center position
        int centerX = chunkPos.getMinBlockX() + 8;
        int centerZ = chunkPos.getMinBlockZ() + 8;
        int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);

        BlockPos centerPos = new BlockPos(centerX, surfaceY, centerZ);
        level.getChunkSource().getLightEngine().checkBlock(centerPos);

        // Update neighbors if not deferred
        if (!LayeredTerrainConfig.DEFER_NEIGHBOR_LIGHTING.get()) {
            updateNeighborLighting(level, chunkPos, surfaceY);
        }
    }

    /**
     * Queue batch lighting updates
     */
    private static void queueBatchLightingUpdates(ServerLevel level, LevelChunk chunk) {
        batchUpdates.incrementAndGet();

        ChunkPos chunkPos = chunk.getPos();
        int batchSize = LayeredTerrainConfig.LIGHTING_BATCH_SIZE.get();

        List<BlockPos> positions = new ArrayList<>();

        // Add center
        int centerX = chunkPos.getMinBlockX() + 8;
        int centerZ = chunkPos.getMinBlockZ() + 8;
        int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
        positions.add(new BlockPos(centerX, surfaceY, centerZ));

        // Add sample points within chunk
        for (int i = 1; i < batchSize && positions.size() < batchSize; i++) {
            int x = chunkPos.getMinBlockX() + (i * 16 / batchSize);
            int z = chunkPos.getMinBlockZ() + (i * 16 / batchSize);
            int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
            positions.add(new BlockPos(x, y, z));
        }

        // Queue all positions
        for (BlockPos pos : positions) {
            level.getChunkSource().getLightEngine().checkBlock(pos);
        }

        // Update neighbors if not deferred
        if (!LayeredTerrainConfig.DEFER_NEIGHBOR_LIGHTING.get()) {
            updateNeighborLighting(level, chunkPos, surfaceY);
        }
    }

    /**
     * Update neighbor chunk lighting
     */
    private static void updateNeighborLighting(ServerLevel level, ChunkPos chunkPos, int surfaceY) {
        // Update 8 surrounding chunks
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;

                BlockPos neighborPos = new BlockPos(
                    (chunkPos.x + dx) * 16 + 8,
                    surfaceY,
                    (chunkPos.z + dz) * 16 + 8
                );

                level.getChunkSource().getLightEngine().checkBlock(neighborPos);
            }
        }
    }

    /**
     * Get lighting update statistics.
     *
     * @return A {@link LightingStats} object.
     */
    public static LightingStats getStats() {
        long total = totalUpdates.get();
        long batch = batchUpdates.get();
        double avgMs = total > 0 ? (totalUpdateTime.sum() / 1_000_000.0) / total : 0;

        return new LightingStats(total, batch, avgMs);
    }

    /**
     * Data class for lighting statistics.
     */
    public static class LightingStats {
        public final long totalUpdates;
        public final long batchUpdates;
        public final double avgTimeMs;

        LightingStats(long total, long batch, double avg) {
            this.totalUpdates = total;
            this.batchUpdates = batch;
            this.avgTimeMs = avg;
        }

        /**
         * Calculates the percentage of updates that used batching.
         * @return Batch rate (0-100).
         */
        public double getBatchRate() {
            return totalUpdates > 0 ? (double) batchUpdates / totalUpdates * 100 : 0;
        }
    }
}