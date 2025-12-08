package com.sltmod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import net.minecraft.util.Mth;

/**
 * Advanced multi-algorithm smoother with parallel processing support
 * Supports 6 smoothing algorithms and optional GPU acceleration
 */
public class Smoother {

    // Thread pool for parallel smoothing
    private static ExecutorService smoothingPool;
    private static final Object poolLock = new Object();

    // Constants
    private static final float BILATERAL_SPATIAL_SIGMA = 1.0f;
    private static final float BILATERAL_RANGE_SIGMA = 2.0f;
    private static final float ANISOTROPIC_ALIGNMENT_WEIGHT = 2.0f;
    private static final int MAX_CLAMPING_ITERATIONS = 5;

    /**
     * Initialize smoothing thread pool
     */
    public static void initialize() {
        if (!LayeredTerrainConfig.ENABLE_MULTI_THREADED_SMOOTHING.get()) {
            return;
        }

        synchronized (poolLock) {
            if (smoothingPool != null) {
                return;
            }

            int threads = LayeredTerrainConfig.SMOOTHING_THREAD_POOL_SIZE.get();
            if (threads == 0) {
                threads = Math.max(2, Runtime.getRuntime().availableProcessors() / 4);
            }

            smoothingPool = Executors.newFixedThreadPool(threads, r -> {
                Thread t = new Thread(r, "LayeredTerrain-Smoother");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY);
                return t;
            });

            LayeredTerrainMod.LOGGER.info(
                "Smoothing thread pool initialized: {} threads", threads
            );
        }
    }

    /**
     * Apply smoothing based on configured algorithm
     *
     * @param input Input thickness array
     * @param passes Number of smoothing passes
     * @param cache Heightmap cache (for gradient calculation)
     * @return Smoothed thickness array
     */
    public static int[][] smoothThickness(int[][] input, int passes, HeightmapCache cache) {
        // Validate inputs
        if (input == null || input.length != 16) {
            throw new IllegalArgumentException("Invalid input array");
        }
        if (passes < 1 || passes > 5) {
            LayeredTerrainMod.LOGGER.warn(
                "Invalid passes {}, clamping to 1-5", passes
            );
            passes = Mth.clamp(passes, 1, 5);
        }

        // Get configured algorithm
        LayeredTerrainConfig.SmoothingType type =
            LayeredTerrainConfig.SMOOTHING_TYPE.get();

        // Try GPU acceleration first if enabled
        if (LayeredTerrainConfig.ENABLE_GPU_ACCELERATION.get() &&
            GPUAccelerator.isAvailable()) {
            try {
                int[][] gpuResult = GPUAccelerator.gpuSmooth(input, passes);
                if (gpuResult != null) {
                    return gpuResult;
                }
            } catch (Exception e) {
                LayeredTerrainMod.LOGGER.debug("GPU smoothing failed, using CPU", e);
            }
        }

        // CPU smoothing with selected algorithm
        return switch (type) {
            case GAUSSIAN -> smoothGaussian(input, passes);
            case BILATERAL -> smoothBilateral(input, passes);
            case ANISOTROPIC -> smoothAnisotropic(input, passes, cache);
            case MEDIAN -> smoothMedian(input, passes);
            case ADAPTIVE -> smoothAdaptive(input, passes, cache);
            case MULTI_SCALE -> smoothMultiScale(input, passes);
        };
    }

    /**
     * Gaussian smoothing - fast standard algorithm
     */
    private static int[][] smoothGaussian(int[][] input, int passes) {
        int[][] current = deepCopy(input);
        int[][] next = new int[16][16];

        for (int pass = 0; pass < passes; pass++) {
            if (shouldUseParallel(pass)) {
                smoothGaussianParallel(current, next);
            } else {
                smoothGaussianSerial(current, next);
            }

            // Swap arrays
            int[][] temp = current;
            current = next;
            next = temp;
        }

        return current;
    }

    /**
     * Serial Gaussian smoothing
     */
    private static void smoothGaussianSerial(int[][] input, int[][] output) {
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                output[x][z] = gaussianKernel(input, x, z);
            }
        }
    }

    /**
     * Parallel Gaussian smoothing (for multi-pass)
     */
    private static void smoothGaussianParallel(int[][] input, int[][] output) {
        if (smoothingPool == null) {
            smoothGaussianSerial(input, output);
            return;
        }

        List<Future<?>> futures = new ArrayList<>();

        // Split work into rows
        for (int z = 0; z < 16; z++) {
            final int rowZ = z;
            futures.add(smoothingPool.submit(() -> {
                for (int x = 0; x < 16; x++) {
                    output[x][rowZ] = gaussianKernel(input, x, rowZ);
                }
            }));
        }

        // Wait for completion
        for (Future<?> future : futures) {
            try {
                future.get(1, TimeUnit.SECONDS);
            } catch (Exception e) {
                LayeredTerrainMod.LOGGER.error("Parallel smoothing error", e);
            }
        }
    }

    /**
     * Gaussian kernel application
     */
    private static int gaussianKernel(int[][] data, int x, int z) {
        int sum = 0;
        int weight = 0;

        // 3×3 Gaussian kernel
        // [1 2 1]
        // [2 4 2]
        // [1 2 1]

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int nz = z + dz;

                if (nx < 0 || nx >= 16 || nz < 0 || nz >= 16) continue;

                int w = getGaussianWeight(dx, dz);
                sum += data[nx][nz] * w;
                weight += w;
            }
        }

        float strength = LayeredTerrainConfig.SMOOTHING_STRENGTH.get().floatValue();
        int smoothed = Math.round((float) sum / weight);

        // Apply strength (blend with original)
        int original = data[x][z];
        return Math.round(original * (1 - strength) + smoothed * strength);
    }

    /**
     * Get Gaussian kernel weight
     */
    private static int getGaussianWeight(int dx, int dz) {
        if (dx == 0 && dz == 0) return 4; // Center
        if (dx == 0 || dz == 0) return 2; // Edges
        return 1; // Corners
    }

    /**
     * Bilateral filter - edge-preserving smoothing
     */
    private static int[][] smoothBilateral(int[][] input, int passes) {
        int[][] current = deepCopy(input);
        int[][] next = new int[16][16];

        for (int pass = 0; pass < passes; pass++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    next[x][z] = bilateralFilter(current, x, z);
                }
            }

            int[][] temp = current;
            current = next;
            next = temp;
        }

        return current;
    }

    /**
     * Bilateral filter kernel
     */
    private static int bilateralFilter(int[][] data, int x, int z) {
        int centerValue = data[x][z];
        float sum = 0;
        float weight = 0;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int nz = z + dz;

                if (nx < 0 || nx >= 16 || nz < 0 || nz >= 16) continue;

                int neighborValue = data[nx][nz];

                // Spatial weight (distance-based)
                float spatialDist = (float) Math.sqrt(dx * dx + dz * dz);
                float spatialWeight = (float) Math.exp(
                    -spatialDist * spatialDist / (2 * BILATERAL_SPATIAL_SIGMA * BILATERAL_SPATIAL_SIGMA)
                );

                // Range weight (value similarity)
                float rangeDist = Math.abs(centerValue - neighborValue);
                float rangeWeight = (float) Math.exp(
                    -rangeDist * rangeDist / (2 * BILATERAL_RANGE_SIGMA * BILATERAL_RANGE_SIGMA)
                );

                float w = spatialWeight * rangeWeight;
                sum += neighborValue * w;
                weight += w;
            }
        }

        return Math.round(sum / weight);
    }

    /**
     * Anisotropic smoothing - ridge-preserving
     */
    private static int[][] smoothAnisotropic(int[][] input, int passes, HeightmapCache cache) {
        if (cache == null) {
            LayeredTerrainMod.LOGGER.warn(
                "No heightmap cache for anisotropic smoothing, using Gaussian"
            );
            return smoothGaussian(input, passes);
        }

        // Calculate gradients once
        SlopeCalculator.GradientInfo[][] gradients =
            SlopeCalculator.calculateGradients(cache.getRawData());

        int[][] current = deepCopy(input);
        int[][] next = new int[16][16];

        for (int pass = 0; pass < passes; pass++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    next[x][z] = anisotropicKernel(current, gradients, x, z);
                }
            }

            int[][] temp = current;
            current = next;
            next = temp;
        }

        return current;
    }

    /**
     * Anisotropic kernel - preserves edges along gradient direction
     */
    private static int anisotropicKernel(int[][] data,
                                        SlopeCalculator.GradientInfo[][] gradients,
                                        int x, int z) {
        SlopeCalculator.GradientInfo grad = gradients[x][z];

        float sum = 0;
        float weight = 0;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int nz = z + dz;

                if (nx < 0 || nx >= 16 || nz < 0 || nz >= 16) continue;

                // Calculate alignment with gradient
                float dot = (dx * grad.directionX + dz * grad.directionZ);

                // Weight inversely proportional to alignment
                // Smooth perpendicular to gradient, preserve along gradient
                float w = 1.0f / (1.0f + Math.abs(dot) * ANISOTROPIC_ALIGNMENT_WEIGHT);

                sum += data[nx][nz] * w;
                weight += w;
            }
        }

        return Math.round(sum / weight);
    }

    /**
     * Median filter - noise reduction
     */
    private static int[][] smoothMedian(int[][] input, int passes) {
        int[][] current = deepCopy(input);
        int[][] next = new int[16][16];

        for (int pass = 0; pass < passes; pass++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    next[x][z] = medianFilter(current, x, z);
                }
            }

            int[][] temp = current;
            current = next;
            next = temp;
        }

        return current;
    }

    /**
     * Median filter kernel
     */
    private static int medianFilter(int[][] data, int x, int z) {
        List<Integer> values = new ArrayList<>(9);

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int nz = z + dz;
                if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16) {
                    values.add(data[nx][nz]);
                }
            }
        }

        Collections.sort(values);
        return values.get(values.size() / 2);
    }

    /**
     * Adaptive smoothing - adjusts based on local variance
     */
    private static int[][] smoothAdaptive(int[][] input, int passes, HeightmapCache cache) {
        int[][] current = deepCopy(input);
        int[][] next = new int[16][16];

        for (int pass = 0; pass < passes; pass++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    float variance = calculateLocalVariance(current, x, z);

                    // High variance = use edge-preserving (bilateral)
                    // Low variance = use standard Gaussian
                    if (variance > 2.0f) {
                        next[x][z] = bilateralFilter(current, x, z);
                    } else {
                        next[x][z] = gaussianKernel(current, x, z);
                    }
                }
            }

            int[][] temp = current;
            current = next;
            next = temp;
        }

        return current;
    }

    /**
     * Calculate local variance for adaptive smoothing
     */
    private static float calculateLocalVariance(int[][] data, int x, int z) {
        float mean = 0;
        int count = 0;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int nz = z + dz;
                if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16) {
                    mean += data[nx][nz];
                    count++;
                }
            }
        }

        mean /= count;

        float variance = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int nz = z + dz;
                if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16) {
                    float diff = data[nx][nz] - mean;
                    variance += diff * diff;
                }
            }
        }

        return variance / count;
    }

    /**
     * Multi-scale smoothing - varies kernel size across passes
     */
    private static int[][] smoothMultiScale(int[][] input, int passes) {
        int[][] current = deepCopy(input);

        // Pass 1: Large kernel (5×5)
        if (passes >= 1) {
            current = smoothWithKernelSize(current, 2);
        }

        // Pass 2-3: Medium kernel (3×3)
        for (int p = 1; p < Math.min(passes, 3); p++) {
            current = smoothWithKernelSize(current, 1);
        }

        // Pass 4-5: Small kernel refinement
        for (int p = 3; p < passes; p++) {
            current = smoothGaussian(current, 1);
        }

        return current;
    }

    /**
     * Smooth with specified kernel radius
     */
    private static int[][] smoothWithKernelSize(int[][] input, int radius) {
        int[][] output = new int[16][16];

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int sum = 0;
                int count = 0;

                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        int nx = x + dx;
                        int nz = z + dz;
                        if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16) {
                            sum += input[nx][nz];
                            count++;
                        }
                    }
                }

                output[x][z] = Math.round((float) sum / count);
            }
        }

        return output;
    }

    /**
     * Differential clamping - ensures smooth transitions
     */
    public static int[][] clampDifferentials(int[][] input, int maxDiff) {
        // Validate inputs
        if (input == null || input.length != 16) {
            throw new IllegalArgumentException("Invalid input array");
        }
        if (maxDiff < 1 || maxDiff > 4) {
            LayeredTerrainMod.LOGGER.warn(
                "Invalid maxDiff {}, clamping to 1-4", maxDiff
            );
            maxDiff = Mth.clamp(maxDiff, 1, 4);
        }

        int[][] clamped = deepCopy(input);
        boolean changed = true;
        int iterations = 0;

        while (changed && iterations < MAX_CLAMPING_ITERATIONS) {
            changed = false;
            iterations++;

            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int current = clamped[x][z];

                    // Check cardinal neighbors
                    if (x > 0 && Math.abs(current - clamped[x-1][z]) > maxDiff) {
                        clamped[x][z] = adjustToward(current, clamped[x-1][z], maxDiff);
                        changed = true;
                    }
                    if (x < 15 && Math.abs(current - clamped[x+1][z]) > maxDiff) {
                        clamped[x][z] = adjustToward(current, clamped[x+1][z], maxDiff);
                        changed = true;
                    }
                    if (z > 0 && Math.abs(current - clamped[x][z-1]) > maxDiff) {
                        clamped[x][z] = adjustToward(current, clamped[x][z-1], maxDiff);
                        changed = true;
                    }
                    if (z < 15 && Math.abs(current - clamped[x][z+1]) > maxDiff) {
                        clamped[x][z] = adjustToward(current, clamped[x][z+1], maxDiff);
                        changed = true;
                    }
                }
            }
        }

        if (LayeredTerrainConfig.DEBUG_MODE.get() && iterations >= MAX_CLAMPING_ITERATIONS) {
            LayeredTerrainMod.LOGGER.debug(
                "Differential clamping reached max iterations ({})", iterations
            );
        }

        return clamped;
    }

    /**
     * Adjust value toward neighbor within maxDiff
     */
    private static int adjustToward(int current, int neighbor, int maxDiff) {
        if (current > neighbor) {
            return neighbor + maxDiff;
        } else {
            return neighbor - maxDiff;
        }
    }

    /**
     * Deep copy array
     */
    private static int[][] deepCopy(int[][] input) {
        int[][] copy = new int[16][16];
        for (int i = 0; i < 16; i++) {
            System.arraycopy(input[i], 0, copy[i], 0, 16);
        }
        return copy;
    }

    /**
     * Decide if parallel processing is worth it
     */
    private static boolean shouldUseParallel(int currentPass) {
        return LayeredTerrainConfig.ENABLE_MULTI_THREADED_SMOOTHING.get() &&
               smoothingPool != null &&
               currentPass >= 2; // Only parallelize pass 3+
    }

    /**
     * Shutdown smoothing pool
     */
    public static void shutdown() {
        synchronized (poolLock) {
            if (smoothingPool != null) {
                smoothingPool.shutdown();
                try {
                    if (!smoothingPool.awaitTermination(5, TimeUnit.SECONDS)) {
                        smoothingPool.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    smoothingPool.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                smoothingPool = null;
            }
        }
    }
}
