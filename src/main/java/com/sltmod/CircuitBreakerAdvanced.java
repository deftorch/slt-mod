package com.sltmod;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Arrays;

public class CircuitBreakerAdvanced {

    public enum State {
        CLOSED,    // Normal operation
        OPEN,      // Failing - reject all requests
        HALF_OPEN  // Testing - allow limited requests
    }

    private static final AtomicReference<State> currentState = new AtomicReference<>(State.CLOSED);
    private static final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private static final AtomicInteger halfOpenSuccesses = new AtomicInteger(0);
    private static final AtomicLong lastFailureTime = new AtomicLong(0);
    private static final AtomicLong lastStateChange = new AtomicLong(System.currentTimeMillis());

    private static final AtomicInteger totalFailures = new AtomicInteger(0);
    private static final AtomicInteger totalSuccesses = new AtomicInteger(0);
    private static final AtomicInteger rejectedRequests = new AtomicInteger(0);
    private static final AtomicInteger halfOpenAttempts = new AtomicInteger(0);

    // Rolling window for error rate calculation
    private static final int WINDOW_SIZE = 100;
    private static final boolean[] recentResults = new boolean[WINDOW_SIZE];
    private static final AtomicInteger windowIndex = new AtomicInteger(0);
    private static final Object windowLock = new Object();

    public static void initialize() {
        currentState.set(State.CLOSED);
        consecutiveFailures.set(0);
        halfOpenSuccesses.set(0);
        totalFailures.set(0);
        totalSuccesses.set(0);
        rejectedRequests.set(0);
        Arrays.fill(recentResults, true);

        LayeredTerrainMod.LOGGER.info("Advanced circuit breaker initialized");
    }

    /**
     * Check if processing should be allowed
     */
    public static boolean shouldProcess() {
        if (!LayeredTerrainConfig.ENABLE_CIRCUIT_BREAKER.get()) {
            return true;
        }

        State state = currentState.get();

        switch (state) {
            case CLOSED:
                return true;

            case OPEN:
                // Check if enough time has passed to try half-open
                long resetTime = LayeredTerrainConfig.CIRCUIT_BREAKER_RESET_TIME_MS.get();
                long timeSinceFailure = System.currentTimeMillis() - lastFailureTime.get();

                if (timeSinceFailure >= resetTime) {
                    if (currentState.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                        LayeredTerrainMod.LOGGER.info(
                            "Circuit breaker entering HALF-OPEN state"
                        );
                        halfOpenSuccesses.set(0);
                        halfOpenAttempts.set(0);
                        lastStateChange.set(System.currentTimeMillis());
                    }
                    return true;
                }

                rejectedRequests.incrementAndGet();
                return false;

            case HALF_OPEN:
                // Allow limited requests to test recovery
                int maxAttempts = LayeredTerrainConfig.CIRCUIT_BREAKER_HALF_OPEN_ATTEMPTS.get();
                int attempts = halfOpenAttempts.incrementAndGet();

                if (attempts <= maxAttempts) {
                    return true;
                } else {
                    // Too many attempts, go back to open
                    if (currentState.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                        LayeredTerrainMod.LOGGER.warn(
                            "Circuit breaker returning to OPEN state (max attempts exceeded)"
                        );
                        lastStateChange.set(System.currentTimeMillis());
                    }
                    rejectedRequests.incrementAndGet();
                    return false;
                }

            default:
                return true;
        }
    }

    /**
     * Record a failure
     */
    public static void recordFailure() {
        if (!LayeredTerrainConfig.ENABLE_CIRCUIT_BREAKER.get()) {
            return;
        }

        totalFailures.incrementAndGet();
        int failures = consecutiveFailures.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());

        // Update rolling window
        updateWindow(false);

        State state = currentState.get();

        switch (state) {
            case CLOSED:
                int threshold = LayeredTerrainConfig.CIRCUIT_BREAKER_THRESHOLD.get();

                // Check consecutive failures
                if (failures >= threshold) {
                    if (currentState.compareAndSet(State.CLOSED, State.OPEN)) {
                        LayeredTerrainMod.LOGGER.error(
                            "╔════════════════════════════════════════════════════╗"
                        );
                        LayeredTerrainMod.LOGGER.error(
                            "║  ⚠️  CIRCUIT BREAKER OPENED                        ║"
                        );
                        LayeredTerrainMod.LOGGER.error(
                            "╠════════════════════════════════════════════════════╣"
                        );
                        LayeredTerrainMod.LOGGER.error(
                            "║  Consecutive Failures: {}                         ║",
                            failures
                        );
                        LayeredTerrainMod.LOGGER.error(
                            "║  Error Rate: {:.1f}%                               ║",
                            getErrorRate()
                        );
                        LayeredTerrainMod.LOGGER.error(
                            "║  Processing suspended for {} ms                   ║",
                            LayeredTerrainConfig.CIRCUIT_BREAKER_RESET_TIME_MS.get()
                        );
                        LayeredTerrainMod.LOGGER.error(
                            "╚════════════════════════════════════════════════════╝"
                        );
                        lastStateChange.set(System.currentTimeMillis());
                    }
                }

                // Also check error rate
                double errorRate = getErrorRate();
                double threshold_rate = LayeredTerrainConfig.CIRCUIT_BREAKER_ERROR_RATE_THRESHOLD.get();
                if (errorRate > threshold_rate * 100) {
                    if (currentState.compareAndSet(State.CLOSED, State.OPEN)) {
                        LayeredTerrainMod.LOGGER.error(
                            "Circuit breaker OPENED due to high error rate: {:.1f}%",
                            errorRate
                        );
                        lastStateChange.set(System.currentTimeMillis());
                    }
                }
                break;

            case HALF_OPEN:
                // Any failure in half-open = back to open
                if (currentState.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                    LayeredTerrainMod.LOGGER.warn(
                        "Circuit breaker returning to OPEN state (failure in half-open)"
                    );
                    lastStateChange.set(System.currentTimeMillis());
                }
                break;

            case OPEN:
                // Already open, just track
                break;
        }
    }

    /**
     * Record a success
     */
    public static void recordSuccess() {
        if (!LayeredTerrainConfig.ENABLE_CIRCUIT_BREAKER.get()) {
            return;
        }

        totalSuccesses.incrementAndGet();
        consecutiveFailures.set(0);

        // Update rolling window
        updateWindow(true);

        State state = currentState.get();

        if (state == State.HALF_OPEN) {
            int successes = halfOpenSuccesses.incrementAndGet();
            int required = LayeredTerrainConfig.CIRCUIT_BREAKER_HALF_OPEN_ATTEMPTS.get();

            if (successes >= required) {
                if (currentState.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    LayeredTerrainMod.LOGGER.info(
                        "╔════════════════════════════════════════════════════╗"
                    );
                    LayeredTerrainMod.LOGGER.info(
                        "║  ✅ CIRCUIT BREAKER CLOSED                         ║"
                    );
                    LayeredTerrainMod.LOGGER.info(
                        "╠════════════════════════════════════════════════════╣"
                    );
                    LayeredTerrainMod.LOGGER.info(
                        "║  Successful recovery after {} attempts            ║",
                        successes
                    );
                    LayeredTerrainMod.LOGGER.info(
                        "║  Normal processing resumed                         ║"
                    );
                    LayeredTerrainMod.LOGGER.info(
                        "╚════════════════════════════════════════════════════╝"
                    );
                    lastStateChange.set(System.currentTimeMillis());
                }
            }
        } else if (state == State.OPEN) {
            // Success while open? Someone bypassed check, close circuit
            if (currentState.compareAndSet(State.OPEN, State.CLOSED)) {
                LayeredTerrainMod.LOGGER.info(
                    "Circuit breaker CLOSED after unexpected success"
                );
                lastStateChange.set(System.currentTimeMillis());
            }
        }
    }

    /**
     * Update rolling window for error rate calculation
     */
    private static void updateWindow(boolean success) {
        synchronized (windowLock) {
            int index = windowIndex.getAndIncrement() % WINDOW_SIZE;
            recentResults[index] = success;
        }
    }

    /**
     * Calculate error rate from rolling window
     */
    private static double getErrorRate() {
        synchronized (windowLock) {
            int failures = 0;
            for (boolean result : recentResults) {
                if (!result) failures++;
            }
            return (double) failures / WINDOW_SIZE * 100;
        }
    }

    /**
     * Check if circuit is currently open
     */
    public static boolean isOpen() {
        return currentState.get() == State.OPEN;
    }

    /**
     * Get current state
     */
    public static State getState() {
        return currentState.get();
    }

    /**
     * Get detailed statistics
     */
    public static String getStats() {
        State state = currentState.get();
        long stateAge = System.currentTimeMillis() - lastStateChange.get();

        return String.format(
            "Circuit Breaker - State: %s (for %dms), " +
            "Consecutive Failures: %d, Total Failures: %d, Total Successes: %d, " +
            "Error Rate: %.1f%%, Rejected: %d",
            state,
            stateAge,
            consecutiveFailures.get(),
            totalFailures.get(),
            totalSuccesses.get(),
            getErrorRate(),
            rejectedRequests.get()
        );
    }

    /**
     * Print detailed statistics
     */
    public static void printStats() {
        if (!LayeredTerrainConfig.ENABLE_CIRCUIT_BREAKER.get()) {
            return;
        }

        State state = currentState.get();
        long stateAge = System.currentTimeMillis() - lastStateChange.get();

        LayeredTerrainMod.LOGGER.info("╔════════════════════════════════════════════════════╗");
        LayeredTerrainMod.LOGGER.info("║  CIRCUIT BREAKER STATISTICS                        ║");
        LayeredTerrainMod.LOGGER.info("╠════════════════════════════════════════════════════╣");
        LayeredTerrainMod.LOGGER.info("║  Current State: {:>35} ║",
            formatState(state));
        LayeredTerrainMod.LOGGER.info("║  Time in State: {:>27}ms ║",
            stateAge);
        LayeredTerrainMod.LOGGER.info("╠════════════════════════════════════════════════════╣");
        LayeredTerrainMod.LOGGER.info("║  Consecutive Failures: {:>27} ║",
            consecutiveFailures.get());
        LayeredTerrainMod.LOGGER.info("║  Total Failures: {:>33} ║",
            totalFailures.get());
        LayeredTerrainMod.LOGGER.info("║  Total Successes: {:>32} ║",
            totalSuccesses.get());
        LayeredTerrainMod.LOGGER.info("║  Rejected Requests: {:>30} ║",
            rejectedRequests.get());
        LayeredTerrainMod.LOGGER.info("╠════════════════════════════════════════════════════╣");
        LayeredTerrainMod.LOGGER.info("║  Error Rate (Rolling): {:>23.1f}% ║",
            getErrorRate());

        if (state == State.HALF_OPEN) {
            LayeredTerrainMod.LOGGER.info("║  Half-Open Successes: {:>28} ║",
                halfOpenSuccesses.get());
            LayeredTerrainMod.LOGGER.info("║  Half-Open Attempts: {:>29} ║",
                halfOpenAttempts.get());
        }

        LayeredTerrainMod.LOGGER.info("╚════════════════════════════════════════════════════╝");
    }

    private static String formatState(State state) {
        return switch (state) {
            case CLOSED -> "✅ CLOSED (Normal)";
            case OPEN -> "🔴 OPEN (Failing)";
            case HALF_OPEN -> "🟡 HALF-OPEN (Testing)";
        };
    }

    /**
     * Force reset (admin command)
     */
    public static void forceReset() {
        currentState.set(State.CLOSED);
        consecutiveFailures.set(0);
        halfOpenSuccesses.set(0);
        lastStateChange.set(System.currentTimeMillis());

        LayeredTerrainMod.LOGGER.info("Circuit breaker FORCE RESET to CLOSED state");
    }
}
