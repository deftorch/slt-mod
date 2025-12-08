package com.sltmod;

import net.minecraft.util.Mth;

/**
 * Advanced slope calculator that provides methods for analyzing terrain geometry.
 *
 * <p>This class implements various algorithms to calculate slopes and gradients from
 * heightmap data. It supports configurable grid sizes (3x3, 5x5, 7x7) to trade off
 * performance for accuracy.</p>
 *
 * <p>It is designed to be thread-safe and robust against NaN/Infinity values, ensuring
 * stability during terrain generation.</p>
 */
public class SlopeCalculator {

    // Constants for magic number elimination
    private static final int MIN_HEIGHT_DIFF = 0;
    private static final int MAX_HEIGHT_DIFF = 256; // Reasonable max for slope calculation
    private static final double MIN_DAMPING = 0.1;
    private static final double MAX_DAMPING = 1000.0;

    /**
     * Calculates the local slope at a specific position using the configured grid size.
     *
     * <p>The slope is defined as the difference between the maximum and minimum height
     * within the neighborhood. The neighborhood size is determined by the
     * {@link HeightmapCache#getGridSize()}.</p>
     *
     * @param cache The heightmap cache containing terrain data.
     * @param x The local X coordinate (0-15).
     * @param z The local Z coordinate (0-15).
     * @return The raw slope value (height difference).
     * @throws IllegalArgumentException if the cache is null.
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
     * Apply exponential damping to raw slope.
     *
     * <p>Formula: {@code dampedSlope = rawSlope / (1 + rawSlope / dampingFactor)}</p>
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
     * Calculates slopes for the entire 16x16 chunk.
     *
     * @param cache The heightmap cache for the chunk.
     * @return A 16x16 2D array containing raw slope values for each block.
     * @throws IllegalArgumentException if the cache is null.
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
     * Normalizes a grid of raw slopes using the damping function.
     *
     * @param rawSlopes A 16x16 array of raw slope values.
     * @return A 16x16 array of normalized (damped) slope values.
     * @throws IllegalArgumentException if the input array is invalid.
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
     * Calculates gradient information for the entire chunk using the Sobel operator.
     *
     * <p>Gradients provide direction and magnitude of the slope, which is useful for
     * anisotropic smoothing algorithms that preserve ridges.</p>
     *
     * @param heightMap The 16x16 heightmap array.
     * @return A 16x16 array of {@link GradientInfo} objects.
     * @throws IllegalArgumentException if the heightmap is invalid.
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
     * Value object holding gradient direction and magnitude.
     */
    public static class GradientInfo {
        /** The normalized X component of the gradient direction. */
        public final float directionX;
        /** The normalized Z component of the gradient direction. */
        public final float directionZ;
        /** The magnitude (strength) of the gradient. */
        public final float magnitude;

        /**
         * Constructs a new GradientInfo object.
         *
         * @param dx The normalized X direction.
         * @param dz The normalized Z direction.
         * @param mag The magnitude of the gradient.
         */
        public GradientInfo(float dx, float dz, float mag) {
            this.directionX = dx;
            this.directionZ = dz;
            this.magnitude = mag;
        }

        /**
         * Checks if the gradient magnitude exceeds a threshold, indicating an edge or ridge.
         *
         * @param threshold The magnitude threshold.
         * @return True if magnitude is greater than threshold, false otherwise.
         */
        public boolean isEdge(float threshold) {
            return magnitude > threshold;
        }
    }
}
