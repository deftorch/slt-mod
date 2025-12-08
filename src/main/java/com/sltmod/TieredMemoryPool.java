package com.sltmod;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Arrays;

public class TieredMemoryPool {

    // Internal interface for testability
    interface ConfigProvider {
        boolean isPoolingEnabled();
        boolean isTieredPoolingEnabled();
        int getHotTierSize();
        int getWarmTierSize();
        int getColdTierSize();
        int getSimplePoolSize();
        int getCleanupIntervalSeconds();
        boolean isDebugMode();
    }

    private static ConfigProvider config = new ConfigProvider() {
        @Override public boolean isPoolingEnabled() { return LayeredTerrainConfig.ENABLE_MEMORY_POOLING.get(); }
        @Override public boolean isTieredPoolingEnabled() { return LayeredTerrainConfig.ENABLE_TIERED_POOLING.get(); }
        @Override public int getHotTierSize() { return LayeredTerrainConfig.POOL_HOT_TIER_SIZE.get(); }
        @Override public int getWarmTierSize() { return LayeredTerrainConfig.POOL_WARM_TIER_SIZE.get(); }
        @Override public int getColdTierSize() { return LayeredTerrainConfig.POOL_COLD_TIER_SIZE.get(); }
        @Override public int getSimplePoolSize() { return LayeredTerrainConfig.MEMORY_POOL_SIZE.get(); }
        @Override public int getCleanupIntervalSeconds() { return LayeredTerrainConfig.POOL_CLEANUP_INTERVAL_SECONDS.get(); }
        @Override public boolean isDebugMode() { return LayeredTerrainConfig.DEBUG_MODE.get(); }
    };

    // Three-tier pool: Hot (frequently used) -> Warm -> Cold (rarely used)
    private static final ConcurrentLinkedQueue<PooledHeightmap> hotTier = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<PooledHeightmap> warmTier = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<PooledHeightmap> coldTier = new ConcurrentLinkedQueue<>();

    private static final AtomicInteger hotTierSize = new AtomicInteger(0);
    private static final AtomicInteger warmTierSize = new AtomicInteger(0);
    private static final AtomicInteger coldTierSize = new AtomicInteger(0);

    private static final AtomicLong totalAllocations = new AtomicLong(0);
    private static final AtomicLong hotTierHits = new AtomicLong(0);
    private static final AtomicLong warmTierHits = new AtomicLong(0);
    private static final AtomicLong coldTierHits = new AtomicLong(0);
    private static final AtomicLong poolMisses = new AtomicLong(0);
    private static final AtomicLong promotions = new AtomicLong(0);
    private static final AtomicLong demotions = new AtomicLong(0);

    private static int maxHotTierSize;
    private static int maxWarmTierSize;
    private static int maxColdTierSize;
    private static boolean enabled;
    private static boolean tieredEnabled;

    private static long lastCleanup = System.currentTimeMillis();

    static class PooledHeightmap {
        final int[][] data;
        long lastAccessed;
        int accessCount;

        PooledHeightmap() {
            this.data = new int[16][16];
            this.lastAccessed = System.currentTimeMillis();
            this.accessCount = 0;
        }

        void reset() {
            for (int i = 0; i < 16; i++) {
                Arrays.fill(data[i], 0);
            }
            this.lastAccessed = System.currentTimeMillis();
            this.accessCount++;
        }
    }

    public static void initialize() {
        enabled = config.isPoolingEnabled();
        tieredEnabled = config.isTieredPoolingEnabled();

        if (!enabled) {
            LayeredTerrainMod.LOGGER.info("Memory pooling disabled");
            return;
        }

        if (tieredEnabled) {
            maxHotTierSize = config.getHotTierSize();
            maxWarmTierSize = config.getWarmTierSize();
            maxColdTierSize = config.getColdTierSize();

            // Pre-allocate hot tier
            for (int i = 0; i < maxHotTierSize / 2; i++) {
                hotTier.offer(new PooledHeightmap());
                hotTierSize.incrementAndGet();
            }

            LayeredTerrainMod.LOGGER.info(
                "Tiered memory pool initialized: Hot={}, Warm={}, Cold={}",
                maxHotTierSize, maxWarmTierSize, maxColdTierSize
            );
        } else {
            maxHotTierSize = config.getSimplePoolSize();
            maxWarmTierSize = 0;
            maxColdTierSize = 0;

            for (int i = 0; i < maxHotTierSize / 2; i++) {
                hotTier.offer(new PooledHeightmap());
                hotTierSize.incrementAndGet();
            }

            LayeredTerrainMod.LOGGER.info(
                "Simple memory pool initialized: Size={}",
                maxHotTierSize
            );
        }
    }

    /**
     * Acquire heightmap from pool
     */
    public static int[][] acquire() {
        if (!enabled) {
            totalAllocations.incrementAndGet();
            return new int[16][16];
        }

        // Try hot tier first (most frequently used)
        PooledHeightmap cached = hotTier.poll();
        if (cached != null) {
            hotTierSize.decrementAndGet();
            hotTierHits.incrementAndGet();
            cached.reset();
            return cached.data;
        }

        if (tieredEnabled) {
            // Try warm tier
            cached = warmTier.poll();
            if (cached != null) {
                warmTierSize.decrementAndGet();
                warmTierHits.incrementAndGet();
                cached.reset();

                // Promote to hot tier on access
                if (cached.accessCount > 3) {
                    promotions.incrementAndGet();
                    if (hotTierSize.get() < maxHotTierSize) {
                        hotTier.offer(cached);
                        hotTierSize.incrementAndGet();
                    }
                }

                return cached.data;
            }

            // Try cold tier
            cached = coldTier.poll();
            if (cached != null) {
                coldTierSize.decrementAndGet();
                coldTierHits.incrementAndGet();
                cached.reset();

                // Promote to warm tier on access
                if (cached.accessCount > 1) {
                    promotions.incrementAndGet();
                    if (warmTierSize.get() < maxWarmTierSize) {
                        warmTier.offer(cached);
                        warmTierSize.incrementAndGet();
                    }
                }

                return cached.data;
            }
        }

        // Pool miss - allocate new
        poolMisses.incrementAndGet();
        totalAllocations.incrementAndGet();
        return new int[16][16];
    }

    /**
     * Release heightmap back to pool with intelligent tiering
     */
    public static void release(int[][] data) {
        if (!enabled || data == null) {
            return;
        }

        PooledHeightmap pooled = new PooledHeightmap();
        pooled.lastAccessed = System.currentTimeMillis();
        pooled.accessCount = 1;

        // Copy data back
        for (int i = 0; i < 16; i++) {
            System.arraycopy(data[i], 0, pooled.data[i], 0, 16);
        }

        if (!tieredEnabled) {
            // Simple pooling - just add to hot tier
            if (hotTierSize.get() < maxHotTierSize) {
                hotTier.offer(pooled);
                hotTierSize.incrementAndGet();
            }
            return;
        }

        // Tiered pooling - add to appropriate tier
        // Hot tier = recently/frequently used
        if (pooled.accessCount > 5 && hotTierSize.get() < maxHotTierSize) {
            hotTier.offer(pooled);
            hotTierSize.incrementAndGet();
        }
        // Warm tier = moderately used
        else if (pooled.accessCount > 2 && warmTierSize.get() < maxWarmTierSize) {
            warmTier.offer(pooled);
            warmTierSize.incrementAndGet();
        }
        // Cold tier = rarely used
        else if (coldTierSize.get() < maxColdTierSize) {
            coldTier.offer(pooled);
            coldTierSize.incrementAndGet();
        }
        // If all tiers full, let GC handle it

        // Periodic cleanup
        periodicCleanup();
    }

    /**
     * Periodic cleanup of cold tier
     */
    private static void periodicCleanup() {
        long now = System.currentTimeMillis();
        long interval = config.getCleanupIntervalSeconds() * 1000L;

        if (now - lastCleanup < interval) {
            return;
        }

        lastCleanup = now;

        if (!tieredEnabled) return;

        // Clean up cold tier - remove least recently used
        int removed = 0;
        long threshold = now - (interval * 2); // Unused for 2x cleanup interval

        PooledHeightmap item;
        while ((item = coldTier.peek()) != null) {
            if (item.lastAccessed < threshold) {
                coldTier.poll();
                coldTierSize.decrementAndGet();
                removed++;
            } else {
                break; // Queue is time-ordered
            }
        }

        if (removed > 0 && config.isDebugMode()) {
            LayeredTerrainMod.LOGGER.debug(
                "Cleaned up {} items from cold tier",
                removed
            );
        }

        // Demote items from warm to cold if underutilized
        performDemotions(threshold);
    }

    /**
     * Demote underutilized items from warm to cold tier
     */
    private static void performDemotions(long threshold) {
        if (!tieredEnabled) return;

        int demoted = 0;

        // Check warm tier for items to demote
        for (int i = 0; i < warmTierSize.get() / 4; i++) {
            PooledHeightmap item = warmTier.poll();
            if (item == null) break;

            warmTierSize.decrementAndGet();

            if (item.lastAccessed < threshold && item.accessCount < 3) {
                // Demote to cold tier
                if (coldTierSize.get() < maxColdTierSize) {
                    coldTier.offer(item);
                    coldTierSize.incrementAndGet();
                    demoted++;
                    demotions.incrementAndGet();
                }
            } else {
                // Keep in warm tier
                warmTier.offer(item);
                warmTierSize.incrementAndGet();
            }
        }

        if (demoted > 0 && config.isDebugMode()) {
            LayeredTerrainMod.LOGGER.debug(
                "Demoted {} items from warm to cold tier",
                demoted
            );
        }
    }

    /**
     * Reconfigure pool on config reload
     */
    public static void reconfigure() {
        LayeredTerrainMod.LOGGER.info("Reconfiguring memory pool...");

        enabled = config.isPoolingEnabled();
        tieredEnabled = config.isTieredPoolingEnabled();

        if (!enabled) {
            shutdown();
            return;
        }

        maxHotTierSize = tieredEnabled
            ? config.getHotTierSize()
            : config.getSimplePoolSize();
        maxWarmTierSize = tieredEnabled ? config.getWarmTierSize() : 0;
        maxColdTierSize = tieredEnabled ? config.getColdTierSize() : 0;

        LayeredTerrainMod.LOGGER.info("Memory pool reconfigured");
    }

    /**
     * Get pool statistics
     */
    public static PoolStats getStats() {
        return new PoolStats(
            hotTierSize.get(),
            warmTierSize.get(),
            coldTierSize.get(),
            totalAllocations.get(),
            hotTierHits.get(),
            warmTierHits.get(),
            coldTierHits.get(),
            poolMisses.get(),
            promotions.get(),
            demotions.get()
        );
    }

    public static class PoolStats {
        public final int hotSize;
        public final int warmSize;
        public final int coldSize;
        public final long totalAllocations;
        public final long hotHits;
        public final long warmHits;
        public final long coldHits;
        public final long misses;
        public final long promotions;
        public final long demotions;

        public PoolStats(int hot, int warm, int cold, long total,
                        long hotHits, long warmHits, long coldHits, long misses,
                        long promotions, long demotions) {
            this.hotSize = hot;
            this.warmSize = warm;
            this.coldSize = cold;
            this.totalAllocations = total;
            this.hotHits = hotHits;
            this.warmHits = warmHits;
            this.coldHits = coldHits;
            this.misses = misses;
            this.promotions = promotions;
            this.demotions = demotions;
        }

        public int getTotalSize() {
            return hotSize + warmSize + coldSize;
        }

        public long getTotalHits() {
            return hotHits + warmHits + coldHits;
        }

        public double getHitRate() {
            long total = getTotalHits() + misses;
            return total > 0 ? (double) getTotalHits() / total * 100 : 0;
        }

        public double getHotTierEfficiency() {
            long total = getTotalHits();
            return total > 0 ? (double) hotHits / total * 100 : 0;
        }
    }

    /**
     * Print detailed statistics
     */
    public static void printStats() {
        if (!enabled) return;

        PoolStats stats = getStats();

        LayeredTerrainMod.LOGGER.info("╔════════════════════════════════════════════════════╗");
        LayeredTerrainMod.LOGGER.info("║  MEMORY POOL STATISTICS                            ║");
        LayeredTerrainMod.LOGGER.info("╠════════════════════════════════════════════════════╣");

        if (tieredEnabled) {
            LayeredTerrainMod.LOGGER.info("║  Tier Sizes:                                       ║");
            LayeredTerrainMod.LOGGER.info("║    Hot:  {:>4} / {:>4}  ({:>5.1f}% full)              ║",
                stats.hotSize, maxHotTierSize,
                (double) stats.hotSize / maxHotTierSize * 100);
            LayeredTerrainMod.LOGGER.info("║    Warm: {:>4} / {:>4}  ({:>5.1f}% full)              ║",
                stats.warmSize, maxWarmTierSize,
                (double) stats.warmSize / maxWarmTierSize * 100);
            LayeredTerrainMod.LOGGER.info("║    Cold: {:>4} / {:>4}  ({:>5.1f}% full)              ║",
                stats.coldSize, maxColdTierSize,
                (double) stats.coldSize / maxColdTierSize * 100);
            LayeredTerrainMod.LOGGER.info("╠════════════════════════════════════════════════════╣");
            LayeredTerrainMod.LOGGER.info("║  Hit Distribution:                                 ║");
            LayeredTerrainMod.LOGGER.info("║    Hot Tier Hits:  {:>10}  ({:>5.1f}%)              ║",
                stats.hotHits, stats.getHotTierEfficiency());
            LayeredTerrainMod.LOGGER.info("║    Warm Tier Hits: {:>10}                          ║",
                stats.warmHits);
            LayeredTerrainMod.LOGGER.info("║    Cold Tier Hits: {:>10}                          ║",
                stats.coldHits);
            LayeredTerrainMod.LOGGER.info("║    Misses:         {:>10}                          ║",
                stats.misses);
            LayeredTerrainMod.LOGGER.info("╠════════════════════════════════════════════════════╣");
            LayeredTerrainMod.LOGGER.info("║  Tier Mobility:                                    ║");
            LayeredTerrainMod.LOGGER.info("║    Promotions:     {:>10}                          ║",
                stats.promotions);
            LayeredTerrainMod.LOGGER.info("║    Demotions:      {:>10}                          ║",
                stats.demotions);
        } else {
            LayeredTerrainMod.LOGGER.info("║  Pool Size: {:>4} / {:>4}  ({:>5.1f}% full)           ║",
                stats.hotSize, maxHotTierSize,
                (double) stats.hotSize / maxHotTierSize * 100);
            LayeredTerrainMod.LOGGER.info("║  Total Hits: {:>10}                                ║",
                stats.getTotalHits());
            LayeredTerrainMod.LOGGER.info("║  Misses:     {:>10}                                ║",
                stats.misses);
        }

        LayeredTerrainMod.LOGGER.info("╠════════════════════════════════════════════════════╣");
        LayeredTerrainMod.LOGGER.info("║  Overall Hit Rate: {:>5.1f}%                          ║",
            stats.getHitRate());
        LayeredTerrainMod.LOGGER.info("║  Total Allocations: {:>10}                         ║",
            stats.totalAllocations);
        LayeredTerrainMod.LOGGER.info("╚════════════════════════════════════════════════════╝");
    }

    /**
     * Shutdown and cleanup
     */
    public static void shutdown() {
        hotTier.clear();
        warmTier.clear();
        coldTier.clear();
        hotTierSize.set(0);
        warmTierSize.set(0);
        coldTierSize.set(0);

        printStats();

        LayeredTerrainMod.LOGGER.info("Memory pool shutdown complete");
    }
}
