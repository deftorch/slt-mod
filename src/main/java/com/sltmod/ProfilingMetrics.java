package com.sltmod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.ArrayList;
import java.util.List;

/**
 * Detailed performance profiling system
 */
public class ProfilingMetrics {

    private static final Map<String, LongAdder> timings = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> counts = new ConcurrentHashMap<>();
    private static boolean enabled = false;

    public static void initialize() {
        enabled = LayeredTerrainConfig.ENABLE_PROFILING.get();

        if (enabled) {
            // Pre-register all stages
            String[] stages = {
                "heightmap_cache",
                "heightmap_cache_compute",
                "slope_calculation",
                "slope_normalization",
                "biome_blending",
                "thickness_conversion",
                "smoothing_pass_1",
                "smoothing_pass_2",
                "smoothing_pass_3",
                "smoothing_pass_4",
                "smoothing_pass_5",
                "differential_clamping",
                "block_placement",
                "block_placement_collect",
                "block_placement_place",
                "lighting_update"
            };

            for (String stage : stages) {
                timings.put(stage, new LongAdder());
                counts.put(stage, new AtomicLong(0));
            }

            LayeredTerrainMod.LOGGER.info("Performance profiling enabled");
        }
    }

    /**
     * Record timing for a stage
     */
    public static void record(String stage, long nanos) {
        if (!enabled) return;

        timings.computeIfAbsent(stage, k -> new LongAdder()).add(nanos);
        counts.computeIfAbsent(stage, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Measure execution time of a supplier
     */
    public static <T> T measure(String stage, java.util.function.Supplier<T> task) {
        if (!enabled) {
            return task.get();
        }

        long start = System.nanoTime();
        try {
            return task.get();
        } finally {
            record(stage, System.nanoTime() - start);
        }
    }

    /**
     * Measure execution time of a runnable
     */
    public static void measure(String stage, Runnable task) {
        if (!enabled) {
            task.run();
            return;
        }

        long start = System.nanoTime();
        try {
            task.run();
        } finally {
            record(stage, System.nanoTime() - start);
        }
    }

    /**
     * Print detailed profiling report
     */
    public static void printDetailedReport() {
        if (!enabled || timings.isEmpty()) {
            LayeredTerrainMod.LOGGER.info("Profiling is disabled or no data available");
            return;
        }

        LayeredTerrainMod.LOGGER.info("╔════════════════════════════════════════════════════════╗");
        LayeredTerrainMod.LOGGER.info("║        DETAILED PERFORMANCE PROFILING REPORT          ║");
        LayeredTerrainMod.LOGGER.info("╠════════════════════════════════════════════════════════╣");

        // Calculate totals
        long totalTime = 0;

        for (Map.Entry<String, LongAdder> entry : timings.entrySet()) {
            totalTime += entry.getValue().sum();
        }

        // Sort by time consumed
        List<Map.Entry<String, LongAdder>> sortedEntries = new ArrayList<>(timings.entrySet());
        sortedEntries.sort((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum()));

        for (Map.Entry<String, LongAdder> entry : sortedEntries) {
            String stage = entry.getKey();
            long time = entry.getValue().sum();
            long count = counts.get(stage).get();

            if (count == 0) continue;

            double avgMs = (time / 1_000_000.0) / count;
            double percentage = totalTime > 0 ? (double) time / totalTime * 100 : 0;

            String bar = createBar(percentage);

            LayeredTerrainMod.LOGGER.info(String.format(
                "║ %-25s %6.2fms  %5.1f%%  %s",
                stage, avgMs, percentage, bar
            ));
        }

        LayeredTerrainMod.LOGGER.info("╠════════════════════════════════════════════════════════╣");
        LayeredTerrainMod.LOGGER.info(String.format(
            "║ Total Time: %43.2fms ║", totalTime / 1_000_000.0
        ));
        LayeredTerrainMod.LOGGER.info("╚════════════════════════════════════════════════════════╝");
    }

    private static String createBar(double percentage) {
        int bars = (int) (percentage / 5); // 20 bars max
        bars = Math.min(bars, 20);

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < bars; i++) sb.append("█");
        for (int i = bars; i < 20; i++) sb.append("░");
        sb.append("]");
        return sb.toString();
    }

    public static void reset() {
        timings.clear();
        counts.clear();
    }
}
