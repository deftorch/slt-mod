package com.sltmod;

import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Converts normalized slopes to block thickness values
 * Supports ML prediction for faster processing
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
     * Convert single normalized slope to thickness
     *
     * @param normalizedSlope Normalized slope value (0.0 to ~20.0)
     * @return Thickness value (1-8)
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
     * Convert entire chunk with biome-aware scaling
     * Supports ML prediction if enabled and available
     *
     * @param normalizedSlopes 16×16 array of normalized slopes
     * @param chunk Chunk for biome lookups
     * @return 16×16 array of thickness values
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
     * Get conversion statistics
     */
    public static ConversionStats getStats() {
        long total = totalConversions.get();
        long ml = mlPredictions.get();
        long fallback = mlFallbacks.get();

        double mlRate = total > 0 ? (double) ml / total * 100 : 0;

        return new ConversionStats(total, ml, fallback, mlRate);
    }

    public static class ConversionStats {
        public final long totalConversions;
        public final long mlPredictions;
        public final long mlFallbacks;
        public final double mlUsageRate;

        ConversionStats(long total, long ml, long fallback, double rate) {
            this.totalConversions = total;
            this.mlPredictions = ml;
            this.mlFallbacks = fallback;
            this.mlUsageRate = rate;
        }
    }

    /**
     * Reset statistics
     */
    public static void resetStats() {
        mlPredictions.set(0);
        mlFallbacks.set(0);
        totalConversions.set(0);
    }
}
