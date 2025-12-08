package com.sltmod;

import net.minecraft.world.level.ChunkPos;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cache for calculated thickness results
 */
public class ResultCache {

    public final ConcurrentHashMap<ChunkPos, CachedResult> cache = new ConcurrentHashMap<>();
    private final AtomicLong totalCalculations = new AtomicLong(0);
    private final AtomicLong totalDuration = new AtomicLong(0);

    // Result TTL (time to live)
    private static final long RESULT_TTL_MS = 30000; // 30 seconds

    public static class CachedResult {
        public final int[][] thicknessMap;
        public final long calculationTime;
        public final long timestamp;

        public CachedResult(int[][] thickness, long duration) {
            this.thicknessMap = thickness;
            this.calculationTime = duration;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > RESULT_TTL_MS;
        }
    }

    public void put(ChunkPos pos, int[][] thickness, long duration) {
        cache.put(pos, new CachedResult(thickness, duration));
        totalCalculations.incrementAndGet();
        totalDuration.addAndGet(duration);

        // Periodic cleanup of expired results
        if (totalCalculations.get() % 100 == 0) {
            cleanupExpired();
        }
    }

    public CachedResult poll(ChunkPos pos) {
        CachedResult result = cache.remove(pos);

        if (result != null && result.isExpired()) {
            // Expired result
            return null;
        }

        return result;
    }

    public int size() {
        return cache.size();
    }

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

    public void clear() {
        cache.clear();
    }
}
