package com.sltmod;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enhanced async processor with timeout, priority queue, and load balancer integration
 */
public class AsyncProcessor {

    private static ExecutorService CALCULATOR;
    private static final ResultCache RESULTS = new ResultCache();

    // Statistics
    private static final AtomicLong calculationsStarted = new AtomicLong(0);
    private static final AtomicLong calculationsCompleted = new AtomicLong(0);
    private static final AtomicLong calculationsTimedOut = new AtomicLong(0);
    private static final AtomicLong calculationsFailed = new AtomicLong(0);

    public static void initialize() {
        int threads = LayeredTerrainConfig.WORKER_THREADS.get();
        if (threads == 0) {
            threads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        }

        CALCULATOR = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "LayeredTerrain-Calculator");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY + 1);
            return t;
        });

        // Initialize smoother thread pool
        Smoother.initialize();

        LayeredTerrainMod.LOGGER.info("Async processor initialized with {} calculator threads", threads);
    }

    /**
     * Submit chunk for async calculation with priority
     * Routes through load balancer if enabled
     */
    public static void submitChunkForCalculation(LevelChunk chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }

        // Check circuit breaker
        if (!CircuitBreakerAdvanced.shouldProcess()) {
            if (LayeredTerrainConfig.DEBUG_MODE.get()) {
                LayeredTerrainMod.LOGGER.debug(
                    "Circuit breaker open, skipping chunk {}", chunk.getPos()
                );
            }
            return;
        }

        // Route through load balancer if enabled
        if (LayeredTerrainConfig.ENABLE_LOAD_BALANCING.get()) {
            LoadBalancer.submitChunk(chunk, getNearbyPlayers(chunk));
        } else {
            submitChunkDirect(chunk);
        }
    }

    /**
     * Direct submission (without load balancer)
     */
    private static void submitChunkDirect(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();

        calculationsStarted.incrementAndGet();

        CompletableFuture.runAsync(() -> {
            try {
                calculateThicknessMapInternal(chunk, pos);
                calculationsCompleted.incrementAndGet();
                CircuitBreakerAdvanced.recordSuccess();

            } catch (Exception e) {
                calculationsFailed.incrementAndGet();
                CircuitBreakerAdvanced.recordFailure();
                LayeredTerrainMod.LOGGER.error("Calculation failed for chunk {}", pos, e);
            }
        }, CALCULATOR);
    }

    /**
     * Internal calculation method with timeout protection
     * Package-private for LoadBalancer access
     */
    static void calculateThicknessMapInternal(LevelChunk chunk, ChunkPos pos) {
        long startTime = System.nanoTime();
        long timeoutMs = LayeredTerrainConfig.CALCULATION_TIMEOUT_MS.get();

        HeightmapCache cache = null;

        try {
            // Create future for timeout handling
            CompletableFuture<int[][]> calculation = CompletableFuture.supplyAsync(() -> {
                return performCalculation(chunk, pos);
            }, CALCULATOR);

            // Wait with timeout
            int[][] finalThickness = calculation.get(timeoutMs, TimeUnit.MILLISECONDS);

            // Store result
            long duration = System.nanoTime() - startTime;
            RESULTS.put(pos, finalThickness, duration);

            // Log slow calculations
            if (LayeredTerrainConfig.LOG_SLOW_CHUNKS.get()) {
                int threshold = LayeredTerrainConfig.SLOW_CHUNK_THRESHOLD_MS.get();
                if (duration > threshold * 1_000_000L) {
                    LayeredTerrainMod.LOGGER.warn(
                        "⚠ Slow calculation: {:.2f}ms for chunk {}",
                        duration / 1_000_000.0, pos
                    );
                }
            }

        } catch (TimeoutException e) {
            calculationsTimedOut.incrementAndGet();
            CircuitBreakerAdvanced.recordFailure();
            LayeredTerrainMod.LOGGER.error(
                "Calculation timeout ({}ms) for chunk {}", timeoutMs, pos
            );

        } catch (Exception e) {
            calculationsFailed.incrementAndGet();
            CircuitBreakerAdvanced.recordFailure();
            LayeredTerrainMod.LOGGER.error("Calculation error for chunk {}", pos, e);
        }
    }

    /**
     * Perform the actual thickness calculation
     */
    private static int[][] performCalculation(LevelChunk chunk, ChunkPos pos) {
        // Step 1: Heightmap cache
        // ProfilingMetrics.measure returns the result.
        // We construct it via measure.
        final HeightmapCache finalCache = ProfilingMetrics.measure("heightmap_cache",
            () -> new HeightmapCache(chunk));

        try {

            // Step 2: Calculate slopes
            int[][] rawSlopes = ProfilingMetrics.measure("slope_calculation",
                () -> SlopeCalculator.calculateAllSlopes(finalCache));

            // Step 3: Normalize slopes
            float[][] normalized = ProfilingMetrics.measure("slope_normalization",
                () -> SlopeCalculator.normalizeSlopes(rawSlopes));

            // Step 4: Convert to thickness (with ML if enabled)
            int[][] rawThickness = ProfilingMetrics.measure("thickness_conversion",
                () -> ThicknessConverter.convertToThickness(normalized, chunk));

            // Step 5: Smooth
            int passes = LayeredTerrainConfig.SMOOTHING_PASSES.get();
            int[][] smoothed = rawThickness;

            for (int pass = 0; pass < passes; pass++) {
                final int[][] input = smoothed;
                // finalCache is already available

                smoothed = ProfilingMetrics.measure("smoothing_pass_" + (pass + 1),
                    () -> Smoother.smoothThickness(input, 1, finalCache));
            }

            // Step 6: Clamp differentials
            int maxDiff = LayeredTerrainConfig.MAX_DIFFERENTIAL.get();
            final int[][] finalSmoothed = smoothed;
            int[][] finalThickness = ProfilingMetrics.measure("differential_clamping",
                () -> Smoother.clampDifferentials(finalSmoothed, maxDiff));

            return finalThickness;

        } finally {
            // Always release cache
            if (finalCache != null) {
                finalCache.release();
            }
        }
    }

    /**
     * Get nearby players for priority calculation
     */
    private static java.util.Collection<net.minecraft.server.level.ServerPlayer> getNearbyPlayers(LevelChunk chunk) {
        if (chunk.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            ChunkPos pos = chunk.getPos();
            int radius = LayeredTerrainConfig.PLAYER_CHUNK_PRIORITY_RADIUS.get();

            return serverLevel.players().stream()
                .filter(player -> {
                    ChunkPos playerChunk = new ChunkPos(player.blockPosition());
                    int dx = Math.abs(playerChunk.x - pos.x);
                    int dz = Math.abs(playerChunk.z - pos.z);
                    return Math.max(dx, dz) <= radius;
                })
                .toList();
        }
        return java.util.Collections.emptyList();
    }

    public static ResultCache getResults() {
        return RESULTS;
    }

    /**
     * Get processor statistics
     */
    public static ProcessorStats getStats() {
        return new ProcessorStats(
            calculationsStarted.get(),
            calculationsCompleted.get(),
            calculationsTimedOut.get(),
            calculationsFailed.get(),
            RESULTS.size(),
            RESULTS.getAverageCalculationTime()
        );
    }

    public static class ProcessorStats {
        public final long started;
        public final long completed;
        public final long timedOut;
        public final long failed;
        public final int queuedResults;
        public final double avgTimeMs;

        ProcessorStats(long s, long c, long t, long f, int q, double avg) {
            this.started = s;
            this.completed = c;
            this.timedOut = t;
            this.failed = f;
            this.queuedResults = q;
            this.avgTimeMs = avg;
        }

        public double getSuccessRate() {
            return started > 0 ? (double) completed / started * 100 : 0;
        }
    }

    public static void shutdown() {
        if (CALCULATOR != null && !CALCULATOR.isShutdown()) {
            LayeredTerrainMod.LOGGER.info("Shutting down async processor...");

            CALCULATOR.shutdown();
            try {
                if (!CALCULATOR.awaitTermination(5, TimeUnit.SECONDS)) {
                    CALCULATOR.shutdownNow();
                    if (!CALCULATOR.awaitTermination(5, TimeUnit.SECONDS)) {
                        LayeredTerrainMod.LOGGER.error("Executor did not terminate");
                    }
                }
            } catch (InterruptedException e) {
                CALCULATOR.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // Shutdown smoother pool
            Smoother.shutdown();

            RESULTS.clear();

            // Print final stats
            ProcessorStats stats = getStats();
            LayeredTerrainMod.LOGGER.info("Async Processor Final Stats:");
            LayeredTerrainMod.LOGGER.info("  - Calculations: {} started, {} completed, {} failed",
                stats.started, stats.completed, stats.failed);
            LayeredTerrainMod.LOGGER.info("  - Success Rate: {:.1f}%", stats.getSuccessRate());
            LayeredTerrainMod.LOGGER.info("  - Avg Time: {:.2f}ms", stats.avgTimeMs);

            LayeredTerrainMod.LOGGER.info("✅ Async processor shutdown complete");
        }
    }
}
