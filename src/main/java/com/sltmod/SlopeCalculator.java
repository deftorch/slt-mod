package com.sltmod;

import net.minecraft.util.Mth;

/**
 * Advanced slope calculator with validation and multiple sampling grids
 * Thread-safe and NaN/Infinity protected
 */
public class SlopeCalculator {

    // Constants for magic number elimination
    private static final int MIN_HEIGHT_DIFF = 0;
    private static final int MAX_HEIGHT_DIFF = 256; // Reasonable max for slope calculation
    private static final double MIN_DAMPING = 0.1;
    private static final double MAX_DAMPING = 1000.0;

    /**
     * Calculate local slope at position using configured grid size
     *
     * @param cache Heightmap cache
     * @param x Local X coordinate
     * @param z Local Z coordinate
     * @return Raw slope value (max height difference in neighborhood)
     */
    public static int calculateLocalSlope(HeightmapCache cache, int x, int z) {
        // Validate inputs
        if (cache == null) {
            throw new IllegalArgumentException("HeightmapCache cannot be null");
        }

        int gridSize = cache.getGridSize();

        return switch (gridSize) {
            case 3 -> calculateSlope3x3(cache, x, z);
            case 5 -> calculateSlope5x5(cache, x, z);
            case 7 -> calculateSlope7x7(cache, x, z);
            default -> {
                LayeredTerrainMod.LOGGER.warn(
                    "Invalid grid size {}, falling back to 3x3", gridSize
                );
                yield calculateSlope3x3(cache, x, z);
            }
        };
    }

    /**
     * Calculate slope using 3×3 grid (fastest)
     */
    private static int calculateSlope3x3(HeightmapCache cache, int x, int z) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int h = cache.getHeight(x + dx, z + dz);
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        }

        return clampSlope(max - min);
    }

    /**
     * Calculate slope using 5×5 grid (better quality)
     */
    private static int calculateSlope5x5(HeightmapCache cache, int x, int z) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                int h = cache.getHeight(x + dx, z + dz);
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        }

        return clampSlope(max - min);
    }

    /**
     * Calculate slope using 7×7 grid (best quality, slowest)
     */
    private static int calculateSlope7x7(HeightmapCache cache, int x, int z) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                int h = cache.getHeight(x + dx, z + dz);
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        }

        return clampSlope(max - min);
    }

    /**
     * Clamp slope to reasonable range
     */
    private static int clampSlope(int slope) {
        return Mth.clamp(slope, MIN_HEIGHT_DIFF, MAX_HEIGHT_DIFF);
    }

    /**
     * Apply exponential damping to raw slope
     * Formula: dampedSlope = rawSlope / (1 + rawSlope / dampingFactor)
     *
     * @param rawSlope Raw slope value
     * @return Damped slope value (0.0 to infinity, typically 0-20)
     */
    public static float dampSlope(int rawSlope) {
        // Validate input
        if (rawSlope < 0) {
            LayeredTerrainMod.LOGGER.warn("Negative slope {} encountered, using absolute value", rawSlope);
            rawSlope = Math.abs(rawSlope);
        }

        double dampingFactor = LayeredTerrainConfig.DAMPING_FACTOR.get();

        // Validate damping factor
        if (!Double.isFinite(dampingFactor) || dampingFactor < MIN_DAMPING) {
            LayeredTerrainMod.LOGGER.error(
                "Invalid damping factor {}, using default 6.0", dampingFactor
            );
            dampingFactor = 6.0;
        }

        // Apply damping with overflow protection
        double result = rawSlope / (1.0 + rawSlope / dampingFactor);

        // Protect against NaN/Infinity
        if (!Double.isFinite(result)) {
            LayeredTerrainMod.LOGGER.error(
                "Non-finite result in dampSlope: raw={}, damping={}, result={}",
                rawSlope, dampingFactor, result
            );
            return 0.0f;
        }

        return (float) Mth.clamp(result, 0.0, MAX_DAMPING);
    }

    /**
     * Calculate slopes for entire chunk
     *
     * @param cache Heightmap cache
     * @return 16×16 array of raw slope values
     */
    public static int[][] calculateAllSlopes(HeightmapCache cache) {
        if (cache == null) {
            throw new IllegalArgumentException("HeightmapCache cannot be null");
        }

        int[][] slopes = new int[16][16];

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                slopes[x][z] = calculateLocalSlope(cache, x, z);
            }
        }

        return slopes;
    }

    /**
     * Normalize all slopes using damping function
     *
     * @param rawSlopes Raw slope values
     * @return 16×16 array of normalized slopes
     */
    public static float[][] normalizeSlopes(int[][] rawSlopes) {
        if (rawSlopes == null || rawSlopes.length != 16) {
            throw new IllegalArgumentException("Invalid slopes array");
        }

        float[][] normalized = new float[16][16];

        for (int z = 0; z < 16; z++) {
            if (rawSlopes[z] == null || rawSlopes[z].length != 16) {
                throw new IllegalArgumentException("Invalid slopes array structure");
            }

            for (int x = 0; x < 16; x++) {
                normalized[x][z] = dampSlope(rawSlopes[x][z]);
            }
        }

        return normalized;
    }

    /**
     * Calculate gradient information for anisotropic smoothing
     * Uses Sobel operator for edge detection
     *
     * @param heightMap Raw heightmap data
     * @return 16×16 array of gradient information
     */
    public static GradientInfo[][] calculateGradients(int[][] heightMap) {
        if (heightMap == null || heightMap.length != 16) {
            throw new IllegalArgumentException("Invalid heightmap");
        }

        GradientInfo[][] gradients = new GradientInfo[16][16];

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                gradients[x][z] = calculateGradientAt(heightMap, x, z);
            }
        }

        return gradients;
    }

    /**
     * Calculate gradient at specific position using Sobel operator
     */
    private static GradientInfo calculateGradientAt(int[][] heightMap, int x, int z) {
        // Sobel kernels
        // Gx: [-1 0 1]    Gz: [-1 -2 -1]
        //     [-2 0 2]         [ 0  0  0]
        //     [-1 0 1]         [ 1  2  1]

        float gx = 0, gz = 0;

        // Apply Sobel operator (with bounds checking)
        if (x > 0 && x < 15 && z > 0 && z < 15) {
            // Gx (horizontal gradient)
            gx += heightMap[x+1][z-1] - heightMap[x-1][z-1];
            gx += 2 * (heightMap[x+1][z] - heightMap[x-1][z]);
            gx += heightMap[x+1][z+1] - heightMap[x-1][z+1];

            // Gz (vertical gradient)
            gz += heightMap[x-1][z+1] - heightMap[x-1][z-1];
            gz += 2 * (heightMap[x][z+1] - heightMap[x][z-1]);
            gz += heightMap[x+1][z+1] - heightMap[x+1][z-1];
        }

        // Calculate magnitude
        float magnitude = (float) Math.sqrt(gx * gx + gz * gz);

        // Normalize direction
        if (magnitude < 0.001f) {
            return new GradientInfo(0, 0, 0);
        }

        return new GradientInfo(gx / magnitude, gz / magnitude, magnitude);
    }

    /**
     * Gradient information for anisotropic smoothing
     */
    public static class GradientInfo {
        public final float directionX; // Normalized gradient direction X
        public final float directionZ; // Normalized gradient direction Z
        public final float magnitude;  // Gradient strength

        public GradientInfo(float dx, float dz, float mag) {
            this.directionX = dx;
            this.directionZ = dz;
            this.magnitude = mag;
        }

        /**
         * Check if this is a ridge/edge (high magnitude)
         */
        public boolean isEdge(float threshold) {
            return magnitude > threshold;
        }
    }
}
