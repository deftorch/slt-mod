package com.sltmod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Optimized block placer with batch operations
 */
public class BlockPlacer {

    // Block update flags
    private static final int SEND_TO_CLIENT = 2;
    private static final int NO_RERENDER = 4;
    private static final int UPDATE_NEIGHBORS = 16;
    private static final int INTERMEDIATE_FLAGS = SEND_TO_CLIENT | NO_RERENDER;
    private static final int FINAL_FLAGS = SEND_TO_CLIENT | UPDATE_NEIGHBORS;

    // Statistics
    private static final AtomicLong totalPlacements = new AtomicLong(0);
    private static final AtomicLong failedPlacements = new AtomicLong(0);

    /**
     * Place layers optimized with batch processing
     *
     * @param chunk Chunk to modify
     * @param cache Heightmap cache
     * @param thicknessMap Calculated thickness values
     * @return Number of blocks placed
     */
    public static int placeLayersOptimized(
        LevelChunk chunk,
        HeightmapCache cache,
        int[][] thicknessMap
    ) {
        if (chunk == null || cache == null || thicknessMap == null) {
            LayeredTerrainMod.LOGGER.error("Null parameter in placeLayersOptimized");
            return 0;
        }

        ChunkPos chunkPos = chunk.getPos();
        List<BlockPlacement> placements = new ArrayList<>();

        // Phase 1: Collect all valid placements
        long collectStart = System.nanoTime();

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                BlockPos worldPos = new BlockPos(
                    chunkPos.getMinBlockX() + x,
                    cache.getHeight(x, z),
                    chunkPos.getMinBlockZ() + z
                );

                BlockState surfaceState = chunk.getBlockState(worldPos);

                // Check if should place layer
                if (!EdgeCaseFilter.shouldPlaceLayer(chunk, worldPos, surfaceState, cache, x, z)) {
                    continue;
                }

                int thickness = thicknessMap[x][z];

                // Skip full-thickness blocks (no change needed)
                if (thickness == 8) {
                    continue;
                }

                // Get layer block from registry
                Block layerBlock = LayerRegistry.getLayerBlock(surfaceState.getBlock(), thickness);

                if (layerBlock != null) {
                    placements.add(new BlockPlacement(worldPos, layerBlock.defaultBlockState()));
                }
            }
        }

        if (LayeredTerrainConfig.ENABLE_PROFILING.get()) {
            ProfilingMetrics.record("block_placement_collect", System.nanoTime() - collectStart);
        }

        // Phase 2: Bulk placement
        if (!placements.isEmpty()) {
            long placeStart = System.nanoTime();
            bulkPlaceBlocks(chunk, placements);

            if (LayeredTerrainConfig.ENABLE_PROFILING.get()) {
                ProfilingMetrics.record("block_placement_place", System.nanoTime() - placeStart);
            }
        }

        totalPlacements.addAndGet(placements.size());
        return placements.size();
    }

    /**
     * Bulk place blocks with optimized flags
     */
    private static void bulkPlaceBlocks(LevelChunk chunk, List<BlockPlacement> placements) {
        int total = placements.size();
        ServerLevel level = (ServerLevel) chunk.getLevel();

        for (int i = 0; i < total; i++) {
            BlockPlacement placement = placements.get(i);
            boolean isLast = (i == total - 1);

            // Use different flags for intermediate vs final blocks
            int flags = isLast ? FINAL_FLAGS : INTERMEDIATE_FLAGS;

            try {
                // Validate state before placing
                if (placement.state == null || placement.state.isAir()) {
                    LayeredTerrainMod.LOGGER.warn("Invalid block state at {}", placement.pos);
                    continue;
                }

                level.setBlock(placement.pos, placement.state, flags);

            } catch (Exception e) {
                failedPlacements.incrementAndGet();
                LayeredTerrainMod.LOGGER.error("Failed to place block at {}", placement.pos, e);
            }
        }
    }

    /**
     * Block placement data holder
     */
    private static class BlockPlacement {
        final BlockPos pos;
        final BlockState state;

        BlockPlacement(BlockPos pos, BlockState state) {
            this.pos = pos.immutable();
            this.state = state;
        }
    }

    /**
     * Get placement statistics
     */
    public static PlacementStats getStats() {
        long total = totalPlacements.get();
        long failed = failedPlacements.get();
        double failRate = total > 0 ? (double) failed / total * 100 : 0;

        return new PlacementStats(total, failed, failRate);
    }

    public static class PlacementStats {
        public final long totalPlacements;
        public final long failedPlacements;
        public final double failureRate;

        PlacementStats(long total, long failed, double rate) {
            this.totalPlacements = total;
            this.failedPlacements = failed;
            this.failureRate = rate;
        }
    }
}
