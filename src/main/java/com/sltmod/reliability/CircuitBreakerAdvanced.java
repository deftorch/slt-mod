package com.sltmod.reliability;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Arrays;

import com.sltmod.LayeredTerrainMod;
import com.sltmod.config.LayeredTerrainConfig;

/**
 * Implements the Circuit Breaker pattern to improve system resilience.
 *
 * <p>This component monitors the success and failure rates of terrain processing tasks.
 * If failures exceed a threshold, the circuit "opens," stopping further processing
 * to prevent cascading failures and allowing the system to recover.</p>
 *
 * <p>States:
 * <ul>
 *   <li><b>CLOSED:</b> Normal operation. Failures are tracked.</li>
 *   <li><b>OPEN:</b> Processing blocked. Enters HALF_OPEN after a reset timeout.</li>
 *   <li><b>HALF_OPEN:</b> Allows a limited number of "test" requests to check stability.</li>
 * </ul>
 * </p>
 */
public class CircuitBreakerAdvanced {

    /**
     * Enum representing the possible states of the circuit breaker.
     */
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

    // Configuration Provider for testing
    public interface CircuitBreakerConfigProvider {
        boolean isEnabled();
        int getThreshold();
        long getResetTimeMs();
        int getHalfOpenAttempts();
        double getErrorRateThreshold();
    }

    private static CircuitBreakerConfigProvider configProvider = new CircuitBreakerConfigProvider() {
        @Override
        public boolean isEnabled() {
            return LayeredTerrainConfig.ENABLE_CIRCUIT_BREAKER.get();
        }

        @Override
        public int getThreshold() {
            return LayeredTerrainConfig.CIRCUIT_BREAKER_THRESHOLD.get();
        }

        @Override
        public long getResetTimeMs() {
            return LayeredTerrainConfig.CIRCUIT_BREAKER_RESET_TIME_MS.get();
        }

        @Override
        public int getHalfOpenAttempts() {
            return LayeredTerrainConfig.CIRCUIT_BREAKER_HALF_OPEN_ATTEMPTS.get();
        }

        @Override
        public double getErrorRateThreshold() {
            return LayeredTerrainConfig.CIRCUIT_BREAKER_ERROR_RATE_THRESHOLD.get();
        }
    };

    /**
     * Sets the configuration provider. Used for testing.
     * @param provider The new configuration provider.
     */
    public static void setConfigProvider(CircuitBreakerConfigProvider provider) {
        configProvider = provider;
    }

    /**
     * Initializes the circuit breaker state and counters.
     */
    public static void initialize() {
        currentState.set(State.CLOSED);
        consecutiveFailures.set(0);
        halfOpenSuccesses.set(0);
        totalFailures.set(0);
        totalSuccesses.set(0);
        rejectedRequests.set(0);
        Arrays.fill(recentResults, true);
        windowIndex.set(0);

        LayeredTerrainMod.LOGGER.info("Advanced circuit breaker initialized");
    }

    /**
     * Checks if a new processing task should be allowed.
     *
     * <p>If the circuit is CLOSED, it returns true. If OPEN, it checks if the reset timeout
     * has passed to transition to HALF_OPEN. If HALF_OPEN, it limits the number of concurrent attempts.</p>
     *
     * @return True if processing is allowed, false otherwise.
     */
    public static boolean shouldProcess() {
        if (!configProvider.isEnabled()) {
            return true;
        }

        State state = currentState.get();

        switch (state) {
            case CLOSED:
                return true;

            case OPEN:
                // Check if enough time has passed to try half-open
                long resetTime = configProvider.getResetTimeMs();
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
                int maxAttempts = configProvider.getHalfOpenAttempts();
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
     * Records a failure event.
     *
     * <p>Increments failure counters and updates the rolling error rate window.
     * If thresholds are exceeded, the circuit trips to the OPEN state.</p>
     */
    public static void recordFailure() {
        if (!configProvider.isEnabled()) {
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
                int threshold = configProvider.getThreshold();

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
                            configProvider.getResetTimeMs()
                        );
                        LayeredTerrainMod.LOGGER.error(
                            "╚════════════════════════════════════════════════════╝"
                        );
                        lastStateChange.set(System.currentTimeMillis());
                    }
                }

                // Also check error rate
                double errorRate = getErrorRate();
                double threshold_rate = configProvider.getErrorRateThreshold();
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
     * Records a success event.
     *
     * <p>Resets consecutive failure counters. If in HALF_OPEN state, successful attempts
     * contribute towards closing the circuit again.</p>
     */
    public static void recordSuccess() {
        if (!configProvider.isEnabled()) {
            return;
        }

        totalSuccesses.incrementAndGet();
        consecutiveFailures.set(0);

        // Update rolling window
        updateWindow(true);

        State state = currentState.get();

        if (state == State.HALF_OPEN) {
            int successes = halfOpenSuccesses.incrementAndGet();
            int required = configProvider.getHalfOpenAttempts();

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
     * Checks if the circuit is currently open (blocking requests).
     * @return True if state is OPEN.
     */
    public static boolean isOpen() {
        return currentState.get() == State.OPEN;
    }

    /**
     * Gets the current state of the circuit breaker.
     * @return The current {@link State}.
     */
    public static State getState() {
        return currentState.get();
    }

    /**
     * Returns a detailed string representation of the circuit breaker's status.
     * @return Formatted status string.
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
     * Prints detailed statistics to the log.
     */
    public static void printStats() {
        if (!configProvider.isEnabled()) {
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
     * Forces the circuit breaker to reset to the CLOSED state.
     *
     * <p>Intended for manual administrative intervention.</p>
     */
    public static void forceReset() {
        currentState.set(State.CLOSED);
        consecutiveFailures.set(0);
        halfOpenSuccesses.set(0);
        lastStateChange.set(System.currentTimeMillis());

        LayeredTerrainMod.LOGGER.info("Circuit breaker FORCE RESET to CLOSED state");
    }
}