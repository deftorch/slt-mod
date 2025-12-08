package com.sltmod;

import net.minecraft.world.level.ChunkPos;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe cache for storing the results of asynchronous thickness calculations.
 *
 * <p>This cache holds completed {@link CachedResult} objects until they are picked up
 * by the main thread. It includes a time-to-live (TTL) mechanism to expire old results
 * and prevents memory leaks.</p>
 */
public class ResultCache {

    public final ConcurrentHashMap<ChunkPos, CachedResult> cache = new ConcurrentHashMap<>();
    private final AtomicLong totalCalculations = new AtomicLong(0);
    private final AtomicLong totalDuration = new AtomicLong(0);

    // Result TTL (time to live)
    private static final long RESULT_TTL_MS = 30000; // 30 seconds

    /**
     * Value object representing a single cached result.
     */
    public static class CachedResult {
        /** The calculated thickness map. */
        public final int[][] thicknessMap;
        /** Duration of the calculation in nanoseconds. */
        public final long calculationTime;
        /** Timestamp when the result was created. */
        public final long timestamp;

        /**
         * Constructs a new CachedResult.
         * @param thickness The thickness data.
         * @param duration Calculation duration.
         */
        public CachedResult(int[][] thickness, long duration) {
            this.thicknessMap = thickness;
            this.calculationTime = duration;
            this.timestamp = System.currentTimeMillis();
        }

        /**
         * Checks if the result is older than the configured TTL.
         * @return True if expired.
         */
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > RESULT_TTL_MS;
        }
    }

    /**
     * Stores a calculation result in the cache.
     *
     * <p>Also updates internal statistics and triggers periodic cleanup of expired entries.</p>
     *
     * @param pos The position of the chunk.
     * @param thickness The calculated thickness map.
     * @param duration The time taken for calculation in nanoseconds.
     */
    public void put(ChunkPos pos, int[][] thickness, long duration) {
        cache.put(pos, new CachedResult(thickness, duration));
        totalCalculations.incrementAndGet();
        totalDuration.addAndGet(duration);

        // Periodic cleanup of expired results
        if (totalCalculations.get() % 100 == 0) {
            cleanupExpired();
        }
    }

    /**
     * Retrieves and removes a result from the cache.
     *
     * <p>If the result has expired (exceeded TTL), it returns null.</p>
     *
     * @param pos The chunk position.
     * @return The cached result, or null if not found or expired.
     */
    public CachedResult poll(ChunkPos pos) {
        CachedResult result = cache.remove(pos);

        if (result != null && result.isExpired()) {
            // Expired result
            return null;
        }

        return result;
    }

    /**
     * Returns the number of items currently in the cache.
     * @return Cache size.
     */
    public int size() {
        return cache.size();
    }

    /**
     * Calculates the average time taken for calculations processed so far.
     * @return Average duration in milliseconds.
     */
    public double getAverageCalculationTime() {
        long count = totalCalculations.get();
        if (count == 0) return 0;
        return (totalDuration.get() / 1_000_000.0) / count;
    }

    /**
     * Remove expired results
     */
    private void cleanupExpired() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Clears all entries from the cache.
     */
    public void clear() {
        cache.clear();
    }
}
