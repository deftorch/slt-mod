package com.sltmod;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Comprehensive edge case filtering system
 * Determines which positions should NOT receive layered blocks
 */
public class EdgeCaseFilter {

    // Constants
    private static final int WATER_CHECK_RADIUS = 2;
    private static final int CAVE_DEPTH_CHECK = 8;
    private static final int DEFAULT_OVERHANG_THRESHOLD = 3;

    // Statistics
    private static final AtomicLong totalChecks = new AtomicLong(0);
    private static final AtomicLong structureSkips = new AtomicLong(0);
    private static final AtomicLong waterSkips = new AtomicLong(0);
    private static final AtomicLong caveSkips = new AtomicLong(0);
    private static final AtomicLong overhangSkips = new AtomicLong(0);
    private static final AtomicLong otherSkips = new AtomicLong(0);

    /**
     * Master filter - checks all enabled filters
     *
     * @param chunk Current chunk
     * @param pos Surface block position
     * @param surfaceState Surface block state
     * @param cache Heightmap cache
     * @param localX Local X coordinate
     * @param localZ Local Z coordinate
     * @return true if layers should be placed, false if should skip
     */
    public static boolean shouldPlaceLayer(
        LevelChunk chunk,
        BlockPos pos,
        BlockState surfaceState,
        HeightmapCache cache,
        int localX,
        int localZ
    ) {
        totalChecks.incrementAndGet();

        // Fast rejections first (cheapest checks)

        // 1. Invalid surface block
        if (surfaceState.isAir() || !surfaceState.getFluidState().isEmpty()) {
            return false;
        }

        Block block = surfaceState.getBlock();

        // 2. Blacklisted blocks
        if (block == Blocks.BEDROCK || block == Blocks.BARRIER || block == Blocks.COMMAND_BLOCK) {
            return false;
        }

        // 3. Snow layers (if enabled)
        if (LayeredTerrainConfig.SKIP_SNOW_LAYERS.get() && isSnowLayer(chunk, pos)) {
            otherSkips.incrementAndGet();
            return false;
        }

        // 4. Preserve farmland (if enabled)
        if (LayeredTerrainConfig.PRESERVE_FARMLAND.get() && isFarmland(surfaceState)) {
            otherSkips.incrementAndGet();
            return false;
        }

        // 5. Preserve paths (if enabled)
        if (LayeredTerrainConfig.PRESERVE_PATHS.get() && isPath(surfaceState)) {
            otherSkips.incrementAndGet();
            return false;
        }

        // More expensive checks

        // 6. Structure check
        if (LayeredTerrainConfig.SKIP_STRUCTURES.get() && isPartOfStructure(chunk, pos)) {
            structureSkips.incrementAndGet();
            return false;
        }

        // 7. Water adjacent check
        if (LayeredTerrainConfig.SKIP_WATER_ADJACENT.get() && isNearWater(chunk, pos)) {
            waterSkips.incrementAndGet();
            return false;
        }

        // 8. Cave opening check
        if (LayeredTerrainConfig.SKIP_CAVE_OPENINGS.get() && isCaveOpening(chunk, pos)) {
            caveSkips.incrementAndGet();
            return false;
        }

        // 9. Overhang check
        if (LayeredTerrainConfig.SKIP_OVERHANGS.get() && isOverhang(cache, localX, localZ)) {
            overhangSkips.incrementAndGet();
            return false;
        }

        // 10. Steep slope check (if enabled)
        if (LayeredTerrainConfig.SKIP_STEEP_SLOPES.get() && isSteepSlope(cache, localX, localZ)) {
            otherSkips.incrementAndGet();
            return false;
        }

        // 11. Decoration check (flowers, tall grass, etc.)
        if (hasDecoration(chunk, pos)) {
            otherSkips.incrementAndGet();
            return false;
        }

        // All checks passed
        return true;
    }

    /**
     * Check if block is part of a structure
     */
    private static boolean isPartOfStructure(LevelChunk chunk, BlockPos pos) {
        // TODO: Fix structure checking for 1.20.1
        // The legacy code used chunk.getAllReferences() which returns Map<Structure, LongSet>
        // but finding bounding box from LongSet (packed positions) is not straightforward without looking up StructureStart
        return false;
        /*
        try {
            // Check structure references
            return chunk.getAllReferences().values().stream()
                .flatMap(java.util.Set::stream)
                .anyMatch(ref -> ref.getBoundingBox().isInside(pos));
        } catch (Exception e) {
            // Fail safe - don't skip if check errors
            if (LayeredTerrainConfig.DEBUG_MODE.get()) {
                LayeredTerrainMod.LOGGER.debug("Structure check error at {}", pos, e);
            }
            return false;
        }
        */
    }

    /**
     * Check if near water (expanded radius)
     */
    private static boolean isNearWater(LevelChunk chunk, BlockPos pos) {
        for (int dz = -WATER_CHECK_RADIUS; dz <= WATER_CHECK_RADIUS; dz++) {
            for (int dx = -WATER_CHECK_RADIUS; dx <= WATER_CHECK_RADIUS; dx++) {
                BlockPos checkPos = pos.offset(dx, 0, dz);
                BlockState state = chunk.getBlockState(checkPos);

                if (!state.getFluidState().isEmpty()) {
                    return true;
                }

                // Also check one block down (shoreline detection)
                BlockState below = chunk.getBlockState(checkPos.below());
                if (!below.getFluidState().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if this is a cave opening
     * Detects significant air gaps below surface
     */
    private static boolean isCaveOpening(LevelChunk chunk, BlockPos pos) {
        int airCount = 0;
        int solidCount = 0;

        for (int dy = 1; dy <= CAVE_DEPTH_CHECK; dy++) {
            BlockPos checkPos = pos.below(dy);
            BlockState state = chunk.getBlockState(checkPos);

            if (state.isAir()) {
                airCount++;
            } else if (!state.getFluidState().isEmpty()) {
                // Water/lava filled cave - not a cave opening
                return false;
            } else {
                solidCount++;
            }
        }

        // If more than 60% air, it's likely a cave opening
        return airCount > (CAVE_DEPTH_CHECK * 0.6);
    }

    /**
     * Check if position is an overhang or cliff edge
     */
    private static boolean isOverhang(HeightmapCache cache, int x, int z) {
        if (cache == null) return false;

        int centerHeight = cache.getHeight(x, z);
        int threshold = LayeredTerrainConfig.OVERHANG_THRESHOLD.get();

        int lowerNeighbors = 0;
        int extremeDrop = 0;

        // Check all 8 neighbors
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;

                int neighborHeight = cache.getHeight(x + dx, z + dz);
                int drop = centerHeight - neighborHeight;

                if (drop >= threshold) {
                    lowerNeighbors++;
                }

                extremeDrop = Math.max(extremeDrop, drop);
            }
        }

        // Overhang if 3+ neighbors are significantly lower
        // OR if any neighbor has extreme drop
        return lowerNeighbors >= 3 || extremeDrop >= (threshold * 2);
    }

    /**
     * Check if slope is too steep for realistic layering
     */
    private static boolean isSteepSlope(HeightmapCache cache, int x, int z) {
        if (cache == null) return false;

        int centerHeight = cache.getHeight(x, z);
        int threshold = LayeredTerrainConfig.STEEP_SLOPE_THRESHOLD.get();

        // Check all 8 neighbors
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;

                int neighborHeight = cache.getHeight(x + dx, z + dz);
                int heightDiff = Math.abs(centerHeight - neighborHeight);

                // If any neighbor differs by more than threshold, it's too steep
                if (heightDiff >= threshold) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if block has snow layer on top
     */
    private static boolean isSnowLayer(LevelChunk chunk, BlockPos pos) {
        BlockPos above = pos.above();
        BlockState aboveState = chunk.getBlockState(above);
        return aboveState.getBlock() == Blocks.SNOW;
    }

    /**
     * Check if block is farmland
     */
    private static boolean isFarmland(BlockState state) {
        return state.getBlock() == Blocks.FARMLAND;
    }

    /**
     * Check if block is a path
     */
    private static boolean isPath(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.DIRT_PATH; // Fixed: GRASS_PATH -> DIRT_PATH
    }

    /**
     * Check if position has decoration (flowers, tall grass, etc.)
     */
    private static boolean hasDecoration(LevelChunk chunk, BlockPos pos) {
        BlockPos above = pos.above();
        BlockState aboveState = chunk.getBlockState(above);

        // Check for plants and decorations
        if (aboveState.is(BlockTags.FLOWERS) ||
            aboveState.is(BlockTags.TALL_FLOWERS) ||
            aboveState.is(BlockTags.SAPLINGS) ||
            aboveState.is(BlockTags.CROPS)) {
            return true;
        }

        Block aboveBlock = aboveState.getBlock();

        // Additional decoration checks
        return aboveBlock == Blocks.GRASS ||
               aboveBlock == Blocks.TALL_GRASS ||
               aboveBlock == Blocks.FERN ||
               aboveBlock == Blocks.LARGE_FERN ||
               aboveBlock == Blocks.DEAD_BUSH ||
               aboveBlock == Blocks.SUGAR_CANE ||
               aboveBlock == Blocks.CACTUS ||
               aboveBlock == Blocks.BROWN_MUSHROOM ||
               aboveBlock == Blocks.RED_MUSHROOM;
    }

    /**
     * Get filter statistics
     */
    public static FilterStats getStats() {
        long total = totalChecks.get();
        long structures = structureSkips.get();
        long water = waterSkips.get();
        long caves = caveSkips.get();
        long overhangs = overhangSkips.get();
        long other = otherSkips.get();
        long totalSkips = structures + water + caves + overhangs + other;

        double skipRate = total > 0 ? (double) totalSkips / total * 100 : 0;

        return new FilterStats(total, structures, water, caves, overhangs, other, skipRate);
    }

    public static class FilterStats {
        public final long totalChecks;
        public final long structureSkips;
        public final long waterSkips;
        public final long caveSkips;
        public final long overhangSkips;
        public final long otherSkips;
        public final double skipRate;

        FilterStats(long total, long structures, long water, long caves,
                   long overhangs, long other, double rate) {
            this.totalChecks = total;
            this.structureSkips = structures;
            this.waterSkips = water;
            this.caveSkips = caves;
            this.overhangSkips = overhangs;
            this.otherSkips = other;
            this.skipRate = rate;
        }

        public long getTotalSkips() {
            return structureSkips + waterSkips + caveSkips + overhangSkips + otherSkips;
        }
    }

    /**
     * Reset statistics
     */
    public static void resetStats() {
        totalChecks.set(0);
        structureSkips.set(0);
        waterSkips.set(0);
        caveSkips.set(0);
        overhangSkips.set(0);
        otherSkips.set(0);
    }

    /**
     * Print statistics
     */
    public static void printStats() {
        FilterStats stats = getStats();

        if (stats.totalChecks == 0) return;

        LayeredTerrainMod.LOGGER.info("╔════════════════════════════════════════════════════╗");
        LayeredTerrainMod.LOGGER.info("║  EDGE CASE FILTER STATISTICS                       ║");
        LayeredTerrainMod.LOGGER.info("╠════════════════════════════════════════════════════╣");
        LayeredTerrainMod.LOGGER.info("║  Total Checks: {:>36} ║", stats.totalChecks);
        LayeredTerrainMod.LOGGER.info("║  Total Skips:  {:>36} ║", stats.getTotalSkips());
        LayeredTerrainMod.LOGGER.info("║  Skip Rate:    {:>35.1f}% ║", stats.skipRate);
        LayeredTerrainMod.LOGGER.info("╠════════════════════════════════════════════════════╣");
        LayeredTerrainMod.LOGGER.info("║  Breakdown:                                        ║");
        LayeredTerrainMod.LOGGER.info("║    Structures:   {:>34} ║", stats.structureSkips);
        LayeredTerrainMod.LOGGER.info("║    Water:        {:>34} ║", stats.waterSkips);
        LayeredTerrainMod.LOGGER.info("║    Caves:        {:>34} ║", stats.caveSkips);
        LayeredTerrainMod.LOGGER.info("║    Overhangs:    {:>34} ║", stats.overhangSkips);
        LayeredTerrainMod.LOGGER.info("║    Other:        {:>34} ║", stats.otherSkips);
        LayeredTerrainMod.LOGGER.info("╚════════════════════════════════════════════════════╝");
    }
}
