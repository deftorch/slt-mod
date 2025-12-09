package com.sltmod.reliability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.sltmod.LayeredTerrainMod;
import com.sltmod.config.LayeredTerrainConfig;

/**
 * Validates chunks to determine if they are suitable for terrain processing.
 *
 * <p>Performs checks against blacklists (dimensions, biomes) and ensures chunks
 * are loaded and non-empty. This prevents processing on invalid or unwanted terrain.</p>
 */
public class ChunkValidator {

    // Cached blacklists
    private static final Set<ResourceLocation> BLACKLISTED_DIMENSIONS = new HashSet<>();
    private static final Set<ResourceLocation> BLACKLISTED_BIOMES = new HashSet<>();

    // Statistics
    private static final AtomicLong totalValidations = new AtomicLong(0);
    private static final AtomicLong dimensionRejects = new AtomicLong(0);
    private static final AtomicLong biomeRejects = new AtomicLong(0);
    private static final AtomicLong emptyChunkRejects = new AtomicLong(0);
    private static final AtomicLong otherRejects = new AtomicLong(0);

    static {
        loadBlacklists();
    }

    /**
     * Load blacklists from configuration
     */
    private static void loadBlacklists() {
        // Load dimension blacklist
        List<? extends String> dimBlacklist = LayeredTerrainConfig.BLACKLISTED_DIMENSIONS.get();
        for (String dim : dimBlacklist) {
            try {
                ResourceLocation loc = ResourceLocation.tryParse(dim);
                if (loc != null) {
                    BLACKLISTED_DIMENSIONS.add(loc);
                } else {
                    LayeredTerrainMod.LOGGER.warn("Invalid dimension ID format: {}", dim);
                }
            } catch (Exception e) {
                LayeredTerrainMod.LOGGER.warn("Invalid dimension ID: {}", dim);
            }
        }

        // Load biome blacklist
        List<? extends String> biomeBlacklist = LayeredTerrainConfig.BLACKLISTED_BIOMES.get();
        for (String biome : biomeBlacklist) {
            try {
                ResourceLocation loc = ResourceLocation.tryParse(biome);
                if (loc != null) {
                    BLACKLISTED_BIOMES.add(loc);
                } else {
                    LayeredTerrainMod.LOGGER.warn("Invalid biome ID format: {}", biome);
                }
            } catch (Exception e) {
                LayeredTerrainMod.LOGGER.warn("Invalid biome ID: {}", biome);
            }
        }

        if (!BLACKLISTED_DIMENSIONS.isEmpty()) {
            LayeredTerrainMod.LOGGER.info("Blacklisted dimensions: {}", BLACKLISTED_DIMENSIONS);
        }
        if (!BLACKLISTED_BIOMES.isEmpty()) {
            LayeredTerrainMod.LOGGER.info("Blacklisted biomes: {}", BLACKLISTED_BIOMES);
        }
    }

    /**
     * Determines if a chunk is valid for processing.
     *
     * <p>Runs a series of checks:
     * <ol>
     *   <li>Null/Empty check.</li>
     *   <li>Dimension blacklist check.</li>
     *   <li>Biome blacklist check.</li>
     *   <li>Custom validation hooks.</li>
     * </ol>
     * </p>
     *
     * @param chunk The chunk to validate.
     * @return True if the chunk should be processed, false otherwise.
     */
    public static boolean isValidForProcessing(LevelChunk chunk) {
        totalValidations.incrementAndGet();

        // 1. Null check
        if (chunk == null) {
            otherRejects.incrementAndGet();
            return false;
        }

        // 2. Empty chunk check
        if (chunk.isEmpty()) {
            emptyChunkRejects.incrementAndGet();
            return false;
        }

        // 3. Dimension check
        if (!isValidDimension(chunk)) {
            dimensionRejects.incrementAndGet();
            return false;
        }

        // 4. Biome check
        if (!isValidBiome(chunk)) {
            biomeRejects.incrementAndGet();
            return false;
        }

        // 5. Custom validation hooks (for future extensibility)
        if (!runCustomValidations(chunk)) {
            otherRejects.incrementAndGet();
            return false;
        }

        return true;
    }

    /**
     * Check if dimension is valid
     */
    private static boolean isValidDimension(LevelChunk chunk) {
        if (BLACKLISTED_DIMENSIONS.isEmpty()) {
            return true;
        }

        try {
            ResourceLocation dimId = chunk.getLevel().dimension().location();
            return !BLACKLISTED_DIMENSIONS.contains(dimId);
        } catch (Exception e) {
            LayeredTerrainMod.LOGGER.debug("Error checking dimension", e);
            return true; // Fail open
        }
    }

    /**
     * Check if biomes in chunk are valid
     */
    private static boolean isValidBiome(LevelChunk chunk) {
        if (BLACKLISTED_BIOMES.isEmpty()) {
            return true;
        }

        try {
            // Sample a few positions in the chunk
            int[] sampleX = {0, 8, 15};
            int[] sampleZ = {0, 8, 15};

            for (int x : sampleX) {
                for (int z : sampleZ) {
                    BlockPos pos = new BlockPos(
                        chunk.getPos().getMinBlockX() + x,
                        64,
                        chunk.getPos().getMinBlockZ() + z
                    );

                    Holder<Biome> biomeHolder = chunk.getLevel().getBiome(pos);
                    ResourceLocation biomeId = biomeHolder.unwrapKey()
                        .map(key -> key.location())
                        .orElse(null);

                    if (biomeId != null && BLACKLISTED_BIOMES.contains(biomeId)) {
                        if (LayeredTerrainConfig.DEBUG_MODE.get()) {
                            LayeredTerrainMod.LOGGER.debug(
                                "Chunk {} rejected due to blacklisted biome: {}",
                                chunk.getPos(), biomeId
                            );
                        }
                        return false;
                    }
                }
            }

        } catch (Exception e) {
            LayeredTerrainMod.LOGGER.debug("Error checking biomes", e);
            return true; // Fail open
        }

        return true;
    }

    /**
     * Run custom validation hooks
     * Placeholder for future extensibility
     */
    private static boolean runCustomValidations(LevelChunk chunk) {
        // Future: Allow mods to register custom validators
        return true;
    }

    /**
     * Retrieves statistics about validation outcomes.
     * @return A {@link ValidationStats} object.
     */
    public static ValidationStats getStats() {
        long total = totalValidations.get();
        long rejected = dimensionRejects.get() + biomeRejects.get() +
                       emptyChunkRejects.get() + otherRejects.get();
        double rejectRate = total > 0 ? (double) rejected / total * 100 : 0;

        return new ValidationStats(
            total,
            dimensionRejects.get(),
            biomeRejects.get(),
            emptyChunkRejects.get(),
            otherRejects.get(),
            rejectRate
        );
    }

    /**
     * Data class for validation statistics.
     */
    public static class ValidationStats {
        public final long totalValidations;
        public final long dimensionRejects;
        public final long biomeRejects;
        public final long emptyChunkRejects;
        public final long otherRejects;
        public final double rejectRate;

        ValidationStats(long total, long dim, long biome, long empty, long other, double rate) {
            this.totalValidations = total;
            this.dimensionRejects = dim;
            this.biomeRejects = biome;
            this.emptyChunkRejects = empty;
            this.otherRejects = other;
            this.rejectRate = rate;
        }

        /**
         * Gets the total number of rejected chunks.
         * @return Total rejections.
         */
        public long getTotalRejects() {
            return dimensionRejects + biomeRejects + emptyChunkRejects + otherRejects;
        }
    }

    /**
     * Reloads blacklist configurations.
     *
     * <p>Called when the configuration file is hot-reloaded.</p>
     */
    public static void reloadBlacklists() {
        BLACKLISTED_DIMENSIONS.clear();
        BLACKLISTED_BIOMES.clear();
        loadBlacklists();
    }
}