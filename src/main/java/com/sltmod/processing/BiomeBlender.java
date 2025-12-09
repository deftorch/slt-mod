package com.sltmod.processing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.util.Mth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sltmod.LayeredTerrainMod;
import com.sltmod.config.LayeredTerrainConfig;

/**
 * Handles the blending of terrain properties across biome boundaries.
 *
 * <p>This class calculates scale factors for terrain thickness by interpolating
 * values from nearby biomes. It ensures smooth transitions between distinct areas,
 * such as flat plains and steep mountains.</p>
 *
 * <p>Features include:</p>
 * <ul>
 *   <li>**Distance-weighted Blending**: Uses inverse square falloff for natural gradients.</li>
 *   <li>**Adaptive Radius**: Automatically increases blend radius near extreme transitions
 *       (e.g., ocean to mountain) if configured.</li>
 *   <li>**Caching**: Caches biome scale factors to minimize tag lookup overhead.</li>
 * </ul>
 */
public class BiomeBlender {

    // Cache for biome scale factors (thread-safe)
    private static final Map<Biome, Float> scaleFactorCache = new ConcurrentHashMap<>();

    // Constants
    private static final float DEFAULT_SCALE = 1.2f;
    private static final int MAX_BLEND_RADIUS = 4;

    /**
     * Calculates the blended scale factor for a specific block within a chunk.
     *
     * <p>This method samples the biomes in a neighborhood around the target position
     * and computes a weighted average of their scale factors. The size of the
     * neighborhood (radius) can adapt dynamically based on terrain complexity.</p>
     *
     * @param chunk The chunk containing the block.
     * @param localX The local X coordinate (0-15).
     * @param localZ The local Z coordinate (0-15).
     * @return The blended scale factor to apply to the terrain thickness.
     */
    public static float getBlendedScaleFactor(LevelChunk chunk, int localX, int localZ) {
        if (chunk == null) {
            LayeredTerrainMod.LOGGER.warn("Null chunk in getBlendedScaleFactor");
            return DEFAULT_SCALE;
        }

        int worldX = chunk.getPos().getMinBlockX() + localX;
        int worldZ = chunk.getPos().getMinBlockZ() + localZ;

        // Determine optimal blend radius
        int radius = determineBlendRadius(chunk, localX, localZ, worldX, worldZ);

        // Perform blending with determined radius
        return blendWithRadius(chunk, worldX, worldZ, radius);
    }

    /**
     * Determine optimal blend radius based on nearby biome transitions.
     * Uses gradient analysis to detect sharp biome boundaries.
     */
    private static int determineBlendRadius(LevelChunk chunk, int localX, int localZ,
                                           int worldX, int worldZ) {
        int configRadius = LayeredTerrainConfig.BIOME_BLEND_RADIUS.get();

        // Validate config radius
        if (configRadius < 1 || configRadius > MAX_BLEND_RADIUS) {
            LayeredTerrainMod.LOGGER.warn(
                "Invalid biome blend radius {}, clamping to 1-{}",
                configRadius, MAX_BLEND_RADIUS
            );
            configRadius = Mth.clamp(configRadius, 1, MAX_BLEND_RADIUS);
        }

        if (!LayeredTerrainConfig.ADAPTIVE_BIOME_BLENDING.get()) {
            return configRadius;
        }

        // Detect extreme biome transitions
        if (hasExtremeBiomeTransition(chunk, worldX, worldZ)) {
            // Increase radius for smoother transition
            return Math.min(configRadius + 1, MAX_BLEND_RADIUS);
        }

        // Detect biome boundaries using gradient
        if (LayeredTerrainConfig.DETECT_BIOME_BOUNDARIES.get() &&
            isNearBiomeBoundary(chunk, worldX, worldZ)) {
            return Math.min(configRadius + 1, MAX_BLEND_RADIUS);
        }

        return configRadius;
    }

    /**
     * Check if there's an extreme biome transition nearby.
     * "Extreme" means large difference in scale factors (e.g., ocean to mountain).
     */
    private static boolean hasExtremeBiomeTransition(LevelChunk chunk, int worldX, int worldZ) {
        BlockPos centerPos = new BlockPos(worldX, 64, worldZ);
        Holder<Biome> centerBiome = chunk.getLevel().getBiome(centerPos);
        float centerFactor = getBiomeScaleFactor(centerBiome);

        double threshold = LayeredTerrainConfig.BIOME_TRANSITION_THRESHOLD.get();

        // Check 4 cardinal directions at distance 2
        int[] dx = {-2, 2, 0, 0};
        int[] dz = {0, 0, -2, 2};

        for (int i = 0; i < 4; i++) {
            BlockPos neighborPos = new BlockPos(worldX + dx[i], 64, worldZ + dz[i]);
            Holder<Biome> neighborBiome = chunk.getLevel().getBiome(neighborPos);
            float neighborFactor = getBiomeScaleFactor(neighborBiome);

            if (Math.abs(centerFactor - neighborFactor) > threshold) {
                return true;
            }
        }

        return false;
    }

    /**
     * Detect if position is near a biome boundary using gradient analysis.
     */
    private static boolean isNearBiomeBoundary(LevelChunk chunk, int worldX, int worldZ) {
        BlockPos centerPos = new BlockPos(worldX, 64, worldZ);
        Holder<Biome> centerBiome = chunk.getLevel().getBiome(centerPos);

        // Check immediate neighbors for different biomes
        int differentCount = 0;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;

                BlockPos neighborPos = new BlockPos(worldX + dx, 64, worldZ + dz);
                Holder<Biome> neighborBiome = chunk.getLevel().getBiome(neighborPos);

                if (!centerBiome.equals(neighborBiome)) {
                    differentCount++;
                }
            }
        }

        // If 3+ neighbors are different biomes, we're at a boundary
        return differentCount >= 3;
    }

    /**
     * Blend scale factors with specified radius using distance-weighted average.
     */
    private static float blendWithRadius(LevelChunk chunk, int worldX, int worldZ, int radius) {
        float totalFactor = 0.0f;
        float totalWeight = 0.0f;

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                BlockPos samplePos = new BlockPos(worldX + dx, 64, worldZ + dz);
                Holder<Biome> biomeHolder = chunk.getLevel().getBiome(samplePos);

                // Distance-based weight (inverse square falloff)
                float distance = (float) Math.sqrt(dx * dx + dz * dz);
                float weight = 1.0f / (1.0f + distance * distance * 0.5f);

                // Center gets extra weight
                if (dx == 0 && dz == 0) {
                    weight *= 2.0f;
                }

                float factor = getBiomeScaleFactor(biomeHolder);
                totalFactor += factor * weight;
                totalWeight += weight;
            }
        }

        if (totalWeight < 0.001f) {
            return DEFAULT_SCALE;
        }

        float result = totalFactor / totalWeight;

        // Validate result
        if (!Float.isFinite(result)) {
            LayeredTerrainMod.LOGGER.error(
                "Non-finite blend result at ({}, {}): totalFactor={}, totalWeight={}",
                worldX, worldZ, totalFactor, totalWeight
            );
            return DEFAULT_SCALE;
        }

        return result;
    }

    /**
     * Get scale factor for specific biome with caching.
     */
    private static float getBiomeScaleFactor(Holder<Biome> biomeHolder) {
        Biome biome = biomeHolder.value();

        // Check cache first
        Float cached = scaleFactorCache.get(biome);
        if (cached != null) {
            return cached;
        }

        float factor = (float) LayeredTerrainConfig.SCALE_FACTOR.get().doubleValue();

        // Determine from tags
        if (biomeHolder.is(BiomeTags.IS_BEACH)) {
            factor = (float) LayeredTerrainConfig.SCALE_FACTOR_BEACH.get().doubleValue();
        } else if (biomeHolder.is(BiomeTags.IS_OCEAN) || biomeHolder.is(BiomeTags.IS_DEEP_OCEAN)) {
            factor = (float) LayeredTerrainConfig.SCALE_FACTOR_OCEAN.get().doubleValue();
        } else if (biomeHolder.is(BiomeTags.IS_RIVER)) {
            factor = (float) LayeredTerrainConfig.SCALE_FACTOR_RIVER.get().doubleValue();
        } else if (biomeHolder.is(BiomeTags.IS_MOUNTAIN)) {
            factor = (float) LayeredTerrainConfig.SCALE_FACTOR_MOUNTAINS.get().doubleValue();
        } else if (biomeHolder.is(BiomeTags.IS_FOREST) || biomeHolder.is(BiomeTags.IS_TAIGA) || biomeHolder.is(BiomeTags.IS_JUNGLE)) {
            factor = (float) LayeredTerrainConfig.SCALE_FACTOR_FOREST.get().doubleValue();
        } else if (biomeHolder.is(BiomeTags.IS_BADLANDS) || biomeHolder.is(BiomeTags.IS_SAVANNA)) {
            factor = (float) LayeredTerrainConfig.SCALE_FACTOR_HILLS.get().doubleValue();
        } else if (biomeHolder.is(BiomeTags.IS_HILL)) {
            factor = (float) LayeredTerrainConfig.SCALE_FACTOR_HILLS.get().doubleValue();
        } else {
            // Default check for Plains-like if no other tags
            // No strict IS_PLAINS tag, assume default for others or check for Overworld
            factor = (float) LayeredTerrainConfig.SCALE_FACTOR_PLAINS.get().doubleValue();
        }

        // Cache for future use
        scaleFactorCache.put(biome, factor);

        return factor;
    }

    /**
     * Clears the biome scale factor cache.
     *
     * <p>This should be called when the configuration is reloaded, as scale factors
     * for biomes may have changed.</p>
     */
    public static void clearCache() {
        scaleFactorCache.clear();
        LayeredTerrainMod.LOGGER.debug("Biome scale factor cache cleared");
    }

    /**
     * Returns the number of cached biome scale factors.
     *
     * @return The size of the internal cache.
     */
    public static int getCacheSize() {
        return scaleFactorCache.size();
    }
}