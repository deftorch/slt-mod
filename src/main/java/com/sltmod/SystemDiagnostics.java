package com.sltmod;

import java.util.ArrayList;
import java.util.List;

public class SystemDiagnostics {

    private static long lastHealthCheck = 0;
    private static final List<HealthIssue> activeIssues = new ArrayList<>();

    enum Severity {
        INFO, WARNING, CRITICAL
    }

    static class HealthIssue {
        final Severity severity;
        final String category;
        final String message;
        final long timestamp;

        HealthIssue(Severity severity, String category, String message) {
            this.severity = severity;
            this.category = category;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Run startup diagnostics
     */
    public static void runStartupCheck() {
        LayeredTerrainMod.LOGGER.info("Running startup diagnostics...");

        activeIssues.clear();

        // Check JVM heap
        checkMemory();

        // Check CPU
        checkCPU();

        // Check configuration
        checkConfiguration();

        // Check optional features
        checkOptionalFeatures();

        // Print results
        printDiagnosticResults();
    }

    /**
     * Run periodic health check
     */
    public static void runHealthCheck() {
        if (!LayeredTerrainConfig.ENABLE_HEALTH_CHECKS.get()) {
            return;
        }

        long now = System.currentTimeMillis();
        long interval = LayeredTerrainConfig.HEALTH_CHECK_INTERVAL_SECONDS.get() * 1000L;

        if (now - lastHealthCheck < interval) {
            return;
        }

        lastHealthCheck = now;
        activeIssues.clear();

        // Check queue health
        checkQueueHealth();

        // Check memory pool
        checkMemoryPool();

        // Check circuit breaker
        checkCircuitBreaker();

        // Check thread health
        checkThreadHealth();

        // Check performance
        checkPerformance();

        // Print issues if any
        if (!activeIssues.isEmpty()) {
            printHealthIssues();
        }
    }

    private static void checkMemory() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        double usagePercent = (double) usedMemory / maxMemory * 100;

        if (usagePercent > 90) {
            addIssue(Severity.CRITICAL, "Memory",
                String.format("Very high memory usage: %.1f%%", usagePercent));
        } else if (usagePercent > 75) {
            addIssue(Severity.WARNING, "Memory",
                String.format("High memory usage: %.1f%%", usagePercent));
        } else {
            addIssue(Severity.INFO, "Memory",
                String.format("Memory usage: %.1f%% (%dMB / %dMB)",
                    usagePercent, usedMemory / 1024 / 1024, maxMemory / 1024 / 1024));
        }
    }

    private static void checkCPU() {
        int processors = Runtime.getRuntime().availableProcessors();
        int configuredThreads = LayeredTerrainConfig.WORKER_THREADS.get();
        int actualThreads = configuredThreads == 0
            ? Math.max(2, processors / 2)
            : configuredThreads;

        if (actualThreads > processors) {
            addIssue(Severity.WARNING, "CPU",
                String.format("Worker threads (%d) exceed CPU cores (%d)",
                    actualThreads, processors));
        } else {
            addIssue(Severity.INFO, "CPU",
                String.format("%d cores available, %d worker threads configured",
                    processors, actualThreads));
        }
    }

    private static void checkConfiguration() {
        // Check for conflicting settings
        if (LayeredTerrainConfig.USE_5X5_SAMPLING.get() &&
            LayeredTerrainConfig.USE_7X7_SAMPLING.get()) {
            addIssue(Severity.CRITICAL, "Config",
                "Both 5x5 and 7x7 sampling enabled - this is invalid");
        }

        // Check smoothing configuration
        int passes = LayeredTerrainConfig.SMOOTHING_PASSES.get();
        if (passes > 3) {
            addIssue(Severity.WARNING, "Config",
                String.format("High smoothing passes (%d) may impact performance", passes));
        }
    }

    private static void checkOptionalFeatures() {
        if (LayeredTerrainConfig.ENABLE_GPU_ACCELERATION.get()) {
            if (GPUAccelerator.isAvailable()) {
                addIssue(Severity.INFO, "GPU", "GPU acceleration available and enabled");
            } else {
                addIssue(Severity.WARNING, "GPU",
                    "GPU acceleration enabled but not available");
            }
        }

        if (LayeredTerrainConfig.ENABLE_ML_PREDICTION.get()) {
            if (MLPredictor.isAvailable()) {
                addIssue(Severity.INFO, "ML", "ML prediction available and enabled");
            } else {
                addIssue(Severity.WARNING, "ML",
                    "ML prediction enabled but not available");
            }
        }
    }

    private static void checkQueueHealth() {
        int queueSize = QueueManager.getQueueSize();
        int maxSize = LayeredTerrainConfig.MAX_QUEUE_SIZE.get();

        double fillPercent = (double) queueSize / maxSize * 100;

        if (fillPercent > 90) {
            addIssue(Severity.CRITICAL, "Queue",
                String.format("Queue nearly full: %d/%d (%.1f%%)",
                    queueSize, maxSize, fillPercent));
        } else if (fillPercent > 75) {
            addIssue(Severity.WARNING, "Queue",
                String.format("Queue filling up: %d/%d (%.1f%%)",
                    queueSize, maxSize, fillPercent));
        }
    }

    private static void checkMemoryPool() {
        if (!LayeredTerrainConfig.ENABLE_MEMORY_POOLING.get()) {
            return;
        }

        TieredMemoryPool.PoolStats stats = TieredMemoryPool.getStats();
        double hitRate = stats.getHitRate();

        if (hitRate < 50) {
            addIssue(Severity.WARNING, "Memory Pool",
                String.format("Low hit rate: %.1f%%", hitRate));
        } else if (hitRate < 30) {
            addIssue(Severity.CRITICAL, "Memory Pool",
                String.format("Very low hit rate: %.1f%% - consider increasing pool size",
                    hitRate));
        }
    }

    private static void checkCircuitBreaker() {
        if (!LayeredTerrainConfig.ENABLE_CIRCUIT_BREAKER.get()) {
            return;
        }

        if (CircuitBreakerAdvanced.isOpen()) {
            addIssue(Severity.CRITICAL, "Circuit Breaker",
                "Circuit is OPEN - processing suspended");
        } else if (CircuitBreakerAdvanced.getState() == CircuitBreakerAdvanced.State.HALF_OPEN) {
            addIssue(Severity.WARNING, "Circuit Breaker",
                "Circuit is HALF-OPEN - testing recovery");
        }
    }

    private static void checkThreadHealth() {
        if (LoadBalancer.workerPools == null) return;

        // Check for thread imbalance
        int minLoad = Integer.MAX_VALUE;
        int maxLoad = 0;

        for (java.util.concurrent.atomic.AtomicInteger load : LoadBalancer.threadLoads) {
            int l = load.get();
            minLoad = Math.min(minLoad, l);
            maxLoad = Math.max(maxLoad, l);
        }

        if (maxLoad - minLoad > 10) {
            addIssue(Severity.WARNING, "Load Balancing",
                String.format("Thread imbalance detected: min=%d, max=%d",
                    minLoad, maxLoad));
        }
    }

    private static void checkPerformance() {
        double avgTime = Metrics.getAverageProcessingTime();

        if (avgTime > 10.0) {
            addIssue(Severity.CRITICAL, "Performance",
                String.format("Very slow processing: %.2fms avg", avgTime));
        } else if (avgTime > 7.0) {
            addIssue(Severity.WARNING, "Performance",
                String.format("Slow processing: %.2fms avg", avgTime));
        }
    }

    private static void addIssue(Severity severity, String category, String message) {
        activeIssues.add(new HealthIssue(severity, category, message));
    }

    private static void printDiagnosticResults() {
        LayeredTerrainMod.LOGGER.info("╔════════════════════════════════════════════════════╗");
        LayeredTerrainMod.LOGGER.info("║  STARTUP DIAGNOSTICS                               ║");
        LayeredTerrainMod.LOGGER.info("╠════════════════════════════════════════════════════╣");

        int criticalCount = 0;
        int warningCount = 0;
        int infoCount = 0;

        for (HealthIssue issue : activeIssues) {
            String icon = switch (issue.severity) {
                case CRITICAL -> "❌";
                case WARNING -> "⚠️ ";
                case INFO -> "ℹ️ ";
            };

            LayeredTerrainMod.LOGGER.info("║ {} [{:>12}] {}",
                icon, issue.category, issue.message);

            switch (issue.severity) {
                case CRITICAL -> criticalCount++;
                case WARNING -> warningCount++;
                case INFO -> infoCount++;
            }
        }

        LayeredTerrainMod.LOGGER.info("╠════════════════════════════════════════════════════╣");
        LayeredTerrainMod.LOGGER.info("║  Summary: {} critical, {} warnings, {} info        ║",
            criticalCount, warningCount, infoCount);

        if (criticalCount > 0) {
            LayeredTerrainMod.LOGGER.info("║  ⚠️  CRITICAL ISSUES DETECTED - Review above       ║");
        } else if (warningCount > 0) {
            LayeredTerrainMod.LOGGER.info("║  ⚠️  Some warnings - system will run but review    ║");
        } else {
            LayeredTerrainMod.LOGGER.info("║  ✅ All systems healthy                            ║");
        }

        LayeredTerrainMod.LOGGER.info("╚════════════════════════════════════════════════════╝");
    }

    private static void printHealthIssues() {
        int critical = (int) activeIssues.stream()
            .filter(i -> i.severity == Severity.CRITICAL)
            .count();
        int warnings = (int) activeIssues.stream()
            .filter(i -> i.severity == Severity.WARNING)
            .count();

        if (critical > 0 || warnings > 0) {
            LayeredTerrainMod.LOGGER.warn("Health Check: {} critical, {} warnings",
                critical, warnings);

            for (HealthIssue issue : activeIssues) {
                if (issue.severity != Severity.INFO) {
                    LayeredTerrainMod.LOGGER.warn("  [{}] {}: {}",
                        issue.severity, issue.category, issue.message);
                }
            }
        }
    }
}
