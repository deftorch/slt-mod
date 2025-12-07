package com.sltmod;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Comprehensive metrics tracking with percentile support
 */
public class Metrics {

    // Basic metrics
    static final AtomicLong chunksProcessed = new AtomicLong(0);
    private static final AtomicLong chunksSkipped = new AtomicLong(0);
    private static final LongAdder totalProcessingTime = new LongAdder();
    private static final LongAdder totalBlocksPlaced = new LongAdder();

    // Percentile tracking (synchronized list)
    private static final List<Long> processingTimes =
        Collections.synchronizedList(new ArrayList<>(10000));
    private static final int MAX_PERCENTILE_SAMPLES = 10000;

    // Periodic reporting
    private static long lastReport = System.currentTimeMillis();
    private static long initTime = System.currentTimeMillis();

    public static void initialize() {
        initTime = System.currentTimeMillis();
        LayeredTerrainMod.LOGGER.debug("Metrics system initialized");
    }

    /**
     * Record chunk processed with timing and blocks placed
     */
    public static void recordChunkProcessed(long durationNanos, int blocksPlaced) {
        chunksProcessed.incrementAndGet();
        totalProcessingTime.add(durationNanos);
        totalBlocksPlaced.add(blocksPlaced);

        // Track percentiles if enabled
        if (LayeredTerrainConfig.ENABLE_PERCENTILE_TRACKING.get()) {
            synchronized (processingTimes) {
                processingTimes.add(durationNanos);

                // Limit size to prevent memory issues
                if (processingTimes.size() > MAX_PERCENTILE_SAMPLES) {
                    processingTimes.remove(0);
                }
            }
        }

        // Periodic auto-reporting
        checkPeriodicReport();
    }

    /**
     * Record chunk skipped
     */
    public static void recordChunkSkipped() {
        chunksSkipped.incrementAndGet();
    }

    /**
     * Check if periodic report should be printed
     */
    private static void checkPeriodicReport() {
        int intervalSeconds = LayeredTerrainConfig.METRICS_INTERVAL_SECONDS.get();
        if (intervalSeconds == 0) return;

        long now = System.currentTimeMillis();
        if (now - lastReport >= intervalSeconds * 1000L) {
            lastReport = now;
            printReport();
        }
    }

    /**
     * Print current metrics report
     */
    public static void printReport() {
        long processed = chunksProcessed.get();
        long skipped = chunksSkipped.get();

        if (processed == 0) {
            LayeredTerrainMod.LOGGER.info("No chunks processed yet");
            return;
        }

        double avgMs = (totalProcessingTime.sum() / 1_000_000.0) / processed;
        double avgBlocks = (double) totalBlocksPlaced.sum() / processed;
        long uptime = System.currentTimeMillis() - initTime;
        double chunksPerSecond = processed / (uptime / 1000.0);

        LayeredTerrainMod.LOGGER.info("╔════════════════════════════════════════════════════╗");
        LayeredTerrainMod.LOGGER.info("║  LAYERED TERRAIN METRICS                           ║");
        LayeredTerrainMod.LOGGER.info("╠════════════════════════════════════════════════════╣");
        LayeredTerrainMod.LOGGER.info("║  Chunks Processed: {:>32} ║", processed);
        LayeredTerrainMod.LOGGER.info("║  Chunks Skipped:   {:>32} ║", skipped);
        LayeredTerrainMod.LOGGER.info("║  Avg Processing:   {:>29.2f}ms ║", avgMs);
        LayeredTerrainMod.LOGGER.info("║  Total Blocks:     {:>32} ║", totalBlocksPlaced.sum());
        LayeredTerrainMod.LOGGER.info("║  Avg Blocks/Chunk: {:>29.1f} ║", avgBlocks);
        LayeredTerrainMod.LOGGER.info("║  Chunks/Second:    {:>29.2f} ║", chunksPerSecond);

        // Print percentiles if available
        if (LayeredTerrainConfig.ENABLE_PERCENTILE_TRACKING.get() && !processingTimes.isEmpty()) {
            PercentileData percentiles = calculatePercentiles();
            LayeredTerrainMod.LOGGER.info("╠════════════════════════════════════════════════════╣");
            LayeredTerrainMod.LOGGER.info("║  Percentiles:                                      ║");
            LayeredTerrainMod.LOGGER.info("║    P50 (Median): {:>30.2f}ms ║", percentiles.p50);
            LayeredTerrainMod.LOGGER.info("║    P95:          {:>30.2f}ms ║", percentiles.p95);
            LayeredTerrainMod.LOGGER.info("║    P99:          {:>30.2f}ms ║", percentiles.p99);
            LayeredTerrainMod.LOGGER.info("║    Min:          {:>30.2f}ms ║", percentiles.min);
            LayeredTerrainMod.LOGGER.info("║    Max:          {:>30.2f}ms ║", percentiles.max);
        }

        LayeredTerrainMod.LOGGER.info("╚════════════════════════════════════════════════════╝");

        // Print additional stats
        printAdditionalStats();
    }

    /**
     * Calculate percentile data
     */
    private static PercentileData calculatePercentiles() {
        List<Long> sortedTimes;
        synchronized (processingTimes) {
            sortedTimes = new ArrayList<>(processingTimes);
        }
        Collections.sort(sortedTimes);

        int size = sortedTimes.size();
        if (size == 0) {
            return new PercentileData(0, 0, 0, 0, 0);
        }

        double p50 = sortedTimes.get((int)(size * 0.50)) / 1_000_000.0;
        double p95 = sortedTimes.get((int)(size * 0.95)) / 1_000_000.0;
        double p99 = sortedTimes.get((int)(size * 0.99)) / 1_000_000.0;
        double min = sortedTimes.get(0) / 1_000_000.0;
        double max = sortedTimes.get(size - 1) / 1_000_000.0;

        return new PercentileData(p50, p95, p99, min, max);
    }

    private static class PercentileData {
        final double p50, p95, p99, min, max;

        PercentileData(double p50, double p95, double p99, double min, double max) {
            this.p50 = p50;
            this.p95 = p95;
            this.p99 = p99;
            this.min = min;
            this.max = max;
        }
    }

    /**
     * Print additional subsystem statistics
     */
    private static void printAdditionalStats() {
        // Memory pool stats
        if (LayeredTerrainConfig.ENABLE_MEMORY_POOLING.get()) {
            TieredMemoryPool.PoolStats poolStats = TieredMemoryPool.getStats();
            LayeredTerrainMod.LOGGER.info("Memory Pool Hit Rate: {:.1f}%", poolStats.getHitRate());
        }

        // Circuit breaker status
        if (LayeredTerrainConfig.ENABLE_CIRCUIT_BREAKER.get()) {
            LayeredTerrainMod.LOGGER.info("Circuit Breaker: {}",
                CircuitBreakerAdvanced.getState());
        }

        // Load balancer status
        if (LayeredTerrainConfig.ENABLE_LOAD_BALANCING.get()) {
            LayeredTerrainMod.LOGGER.info(LoadBalancer.getStats());
        }

        // Async processor stats
        AsyncProcessor.ProcessorStats procStats = AsyncProcessor.getStats();
        LayeredTerrainMod.LOGGER.info("Async Success Rate: {:.1f}%", procStats.getSuccessRate());

        // Edge filter stats
        EdgeCaseFilter.FilterStats filterStats = EdgeCaseFilter.getStats();
        if (filterStats.totalChecks > 0) {
            LayeredTerrainMod.LOGGER.info("Filter Skip Rate: {:.1f}%", filterStats.skipRate);
        }
    }

    /**
     * Print final report on shutdown
     */
    public static void printFinalReport() {
        LayeredTerrainMod.LOGGER.info("═══════════════════════════════════════════════════════");
        LayeredTerrainMod.LOGGER.info("  FINAL METRICS REPORT");
        LayeredTerrainMod.LOGGER.info("═══════════════════════════════════════════════════════");
        printReport();

        // Print all subsystem stats
        EdgeCaseFilter.printStats();

        if (LayeredTerrainConfig.ENABLE_PROFILING.get()) {
            ProfilingMetrics.printDetailedReport();
        }
    }

    public static double getAverageProcessingTime() {
        long processed = chunksProcessed.get();
        if (processed == 0) return 0;
        return (totalProcessingTime.sum() / 1_000_000.0) / processed;
    }

    public static void reset() {
        chunksProcessed.set(0);
        chunksSkipped.set(0);
        totalProcessingTime.reset();
        totalBlocksPlaced.reset();

        synchronized (processingTimes) {
            processingTimes.clear();
        }

        EdgeCaseFilter.resetStats();
        ThicknessConverter.resetStats();

        initTime = System.currentTimeMillis();
        lastReport = System.currentTimeMillis();

        LayeredTerrainMod.LOGGER.info("Metrics reset");
    }
}
