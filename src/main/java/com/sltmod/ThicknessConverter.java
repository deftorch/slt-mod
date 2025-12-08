package com.sltmod;

import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles the conversion of terrain slope data into block thickness values.
 *
 * <p>This class translates normalized slope values into an integer thickness (1-8),
 * where 1 is the thinnest layer and 8 is a full block. It incorporates biome-specific
 * scaling factors and optional Machine Learning predictions to optimize the process.</p>
 */
public class ThicknessConverter {

    // Constants
    private static final int MIN_THICKNESS = 1;
    private static final int MAX_THICKNESS = 8;
    private static final float DEFAULT_RAW_THICKNESS = 8.0f;

    // Statistics
    private static final AtomicLong mlPredictions = new AtomicLong(0);
    private static final AtomicLong mlFallbacks = new AtomicLong(0);
    private static final AtomicLong totalConversions = new AtomicLong(0);

    /**
     * Converts a single normalized slope value into a block thickness.
     *
     * <p>The conversion uses the configured global scale factor.
     * Formula: {@code thickness = 8 - (slope * scale)}.</p>
     *
     * @param normalizedSlope The normalized slope value (typically 0.0 to ~20.0).
     * @return The calculated thickness (1-8).
     */
    public static int slopeToThickness(float normalizedSlope) {
        // Validate input
        if (!Float.isFinite(normalizedSlope)) {
            LayeredTerrainMod.LOGGER.warn(
                "Non-finite slope in slopeToThickness: {}", normalizedSlope
            );
            return MAX_THICKNESS; // Default to full thickness
        }

        if (normalizedSlope < 0) {
            LayeredTerrainMod.LOGGER.warn(
                "Negative normalized slope: {}", normalizedSlope
            );
            normalizedSlope = Math.abs(normalizedSlope);
        }

        float scaleFactor = LayeredTerrainConfig.SCALE_FACTOR.get().floatValue();

        // Validate scale factor
        if (!Float.isFinite(scaleFactor) || scaleFactor < 0.1f) {
            LayeredTerrainMod.LOGGER.error(
                "Invalid scale factor: {}, using default", scaleFactor
            );
            scaleFactor = 1.2f;
        }

        // Formula: thickness = 8 - (slope * scale)
        float rawThickness = DEFAULT_RAW_THICKNESS - (normalizedSlope * scaleFactor);
        int thickness = Math.round(rawThickness);

        totalConversions.incrementAndGet();

        return Mth.clamp(thickness, MIN_THICKNESS, MAX_THICKNESS);
    }

    /**
     * Converts a grid of normalized slopes into a grid of thickness values for a chunk.
     *
     * <p>This method supports biome-aware scaling (via {@link BiomeBlender}) and
     * can optionally offload prediction to an ML model if enabled.</p>
     *
     * @param normalizedSlopes The 16x16 array of normalized slopes.
     * @param chunk The level chunk, used for biome lookup.
     * @return A 16x16 array of integer thickness values.
     * @throws IllegalArgumentException if inputs are null or invalid.
     */
    public static int[][] convertToThickness(float[][] normalizedSlopes, LevelChunk chunk) {
        // Validate inputs
        if (normalizedSlopes == null || normalizedSlopes.length != 16) {
            throw new IllegalArgumentException("Invalid normalizedSlopes array");
        }
        if (chunk == null) {
            throw new IllegalArgumentException("Chunk cannot be null");
        }

        // Try ML prediction first if enabled
        if (LayeredTerrainConfig.ENABLE_ML_PREDICTION.get() && MLPredictor.isAvailable()) {
            int[][] predicted = tryMLPrediction(normalizedSlopes, chunk);
            if (predicted != null) {
                mlPredictions.incrementAndGet();
                return predicted;
            }
            mlFallbacks.incrementAndGet();
        }

        // Fallback to traditional calculation
        return convertTraditional(normalizedSlopes, chunk);
    }

    /**
     * Try ML prediction with confidence threshold
     */
    private static int[][] tryMLPrediction(float[][] normalizedSlopes, LevelChunk chunk) {
        try {
            double confidenceThreshold =
                LayeredTerrainConfig.ML_PREDICTION_CONFIDENCE_THRESHOLD.get();

            // Convert slopes to heightmap format for ML
            // (This is a simplified interface - real ML would need more context)
            int[][] predicted = MLPredictor.predictThickness(
                convertSlopesToHeightmap(normalizedSlopes),
                confidenceThreshold
            );

            if (predicted != null && validateThicknessArray(predicted)) {
                return predicted;
            }

        } catch (Exception e) {
            LayeredTerrainMod.LOGGER.debug("ML prediction failed, using fallback", e);
        }

        return null;
    }

    /**
     * Traditional biome-aware thickness conversion
     */
    private static int[][] convertTraditional(float[][] normalizedSlopes, LevelChunk chunk) {
        int[][] thickness = new int[16][16];

        for (int z = 0; z < 16; z++) {
            if (normalizedSlopes[z] == null || normalizedSlopes[z].length != 16) {
                throw new IllegalArgumentException(
                    "Invalid normalizedSlopes structure at z=" + z
                );
            }

            for (int x = 0; x < 16; x++) {
                float normalizedSlope = normalizedSlopes[x][z];

                // Get biome-blended scale factor
                float scaleFactor = BiomeBlender.getBlendedScaleFactor(chunk, x, z);

                // Apply formula with biome scaling
                float rawThickness = DEFAULT_RAW_THICKNESS - (normalizedSlope * scaleFactor);
                int t = Math.round(rawThickness);

                thickness[x][z] = Mth.clamp(t, MIN_THICKNESS, MAX_THICKNESS);
            }
        }

        return thickness;
    }

    /**
     * Convert normalized slopes to heightmap format (for ML)
     */
    private static int[][] convertSlopesToHeightmap(float[][] slopes) {
        int[][] heightmap = new int[16][16];

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                // Simple conversion - in real ML this would be more sophisticated
                heightmap[x][z] = Math.round(slopes[x][z] * 10);
            }
        }

        return heightmap;
    }

    /**
     * Validate thickness array structure and values
     */
    private static boolean validateThicknessArray(int[][] thickness) {
        if (thickness == null || thickness.length != 16) {
            return false;
        }

        for (int x = 0; x < 16; x++) {
            if (thickness[x] == null || thickness[x].length != 16) {
                return false;
            }

            for (int z = 0; z < 16; z++) {
                int t = thickness[x][z];
                if (t < MIN_THICKNESS || t > MAX_THICKNESS) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Returns statistics about the conversion process, including ML usage.
     *
     * @return A snapshot of the current conversion statistics.
     */
    public static ConversionStats getStats() {
        long total = totalConversions.get();
        long ml = mlPredictions.get();
        long fallback = mlFallbacks.get();

        double mlRate = total > 0 ? (double) ml / total * 100 : 0;

        return new ConversionStats(total, ml, fallback, mlRate);
    }

    /**
     * Data class holding statistics about thickness conversions.
     */
    public static class ConversionStats {
        public final long totalConversions;
        public final long mlPredictions;
        public final long mlFallbacks;
        public final double mlUsageRate;

        /**
         * Constructs a new ConversionStats object.
         * @param total Total number of conversions.
         * @param ml Number of ML-predicted conversions.
         * @param fallback Number of times ML failed and fallback was used.
         * @param rate Percentage of conversions handled by ML.
         */
        ConversionStats(long total, long ml, long fallback, double rate) {
            this.totalConversions = total;
            this.mlPredictions = ml;
            this.mlFallbacks = fallback;
            this.mlUsageRate = rate;
        }
    }

    /**
     * Resets the conversion statistics counters.
     */
    public static void resetStats() {
        mlPredictions.set(0);
        mlFallbacks.set(0);
        totalConversions.set(0);
    }
}
