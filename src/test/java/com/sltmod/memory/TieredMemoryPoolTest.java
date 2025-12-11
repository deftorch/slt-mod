package com.sltmod.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link TieredMemoryPool} class.
 *
 * <p>Uses reflection to inject a mock configuration and verify internal state transitions
 * between Hot, Warm, and Cold tiers.</p>
 */
public class TieredMemoryPoolTest {

    // Mock implementation of ConfigProvider
    static class MockConfigProvider implements TieredMemoryPool.ConfigProvider {
        boolean poolingEnabled = true;
        boolean tieredPoolingEnabled = true;
        int hotTierSize = 10;
        int warmTierSize = 10;
        int coldTierSize = 10;
        int simplePoolSize = 100;
        int cleanupIntervalSeconds = 300;
        boolean debugMode = false;

        @Override public boolean isPoolingEnabled() { return poolingEnabled; }
        @Override public boolean isTieredPoolingEnabled() { return tieredPoolingEnabled; }
        @Override public int getHotTierSize() { return hotTierSize; }
        @Override public int getWarmTierSize() { return warmTierSize; }
        @Override public int getColdTierSize() { return coldTierSize; }
        @Override public int getSimplePoolSize() { return simplePoolSize; }
        @Override public int getCleanupIntervalSeconds() { return cleanupIntervalSeconds; }
        @Override public boolean isDebugMode() { return debugMode; }
    }

    private MockConfigProvider mockConfig;

    @BeforeEach
    public void setup() throws Exception {
        // Create mock config
        mockConfig = new MockConfigProvider();

        // Inject mock config into TieredMemoryPool instance
        setInstanceField("config", mockConfig);

        // Reset TieredMemoryPool state
        setInstanceField("enabled", true);
        setInstanceField("tieredEnabled", true);
        setInstanceField("maxHotTierSize", 10);
        setInstanceField("maxWarmTierSize", 10);
        setInstanceField("maxColdTierSize", 10);

        getQueue("hotTier").clear();
        getQueue("warmTier").clear();
        getQueue("coldTier").clear();

        getAtomicInt("hotTierSize").set(0);
        getAtomicInt("warmTierSize").set(0);
        getAtomicInt("coldTierSize").set(0);

        getAtomicLong("totalAllocations").set(0);
        getAtomicLong("hotTierHits").set(0);
        getAtomicLong("warmTierHits").set(0);
        getAtomicLong("coldTierHits").set(0);
        getAtomicLong("poolMisses").set(0);
        getAtomicLong("promotions").set(0);
        getAtomicLong("demotions").set(0);
    }

    @Test
    public void testAcquireNew() {
        int[][] data = TieredMemoryPool.getInstance().acquire();
        assertNotNull(data);
        assertEquals(16, data.length);
        assertEquals(16, data[0].length);

        TieredMemoryPool.PoolStats stats = TieredMemoryPool.getInstance().getStats();
        assertEquals(1, stats.totalAllocations);
        assertEquals(1, stats.misses);
    }

    @Test
    public void testReleaseAndAcquire() {
        // Acquire (miss)
        int[][] data = TieredMemoryPool.getInstance().acquire();

        // Release
        TieredMemoryPool.getInstance().release(data);

        // With tieredEnabled=true and fresh release (accessCount=1), it goes to COLD tier
        TieredMemoryPool.PoolStats stats = TieredMemoryPool.getInstance().getStats();
        assertEquals(1, stats.coldSize, "Should be in cold tier");

        // Acquire (should hit cold tier)
        int[][] data2 = TieredMemoryPool.getInstance().acquire();

        // When acquired from cold, it should be removed from cold
        stats = TieredMemoryPool.getInstance().getStats();
        assertEquals(0, stats.coldSize);
        assertEquals(1, stats.coldHits);
        assertEquals(1, stats.misses); // The first one was a miss
    }

    @Test
    public void testTierPromotion() throws Exception {
        // Mock a pooled item in cold tier with high access count to test promotion
        Queue<Object> coldTier = (Queue<Object>) getQueue("coldTier");

        Class<?> pooledMapClass = null;
        for (Class<?> c : TieredMemoryPool.class.getDeclaredClasses()) {
            if (c.getSimpleName().equals("PooledHeightmap")) {
                pooledMapClass = c;
                break;
            }
        }
        assertNotNull(pooledMapClass);

        Object pooledItem = pooledMapClass.getDeclaredConstructor().newInstance();

        Field accessCountField = pooledMapClass.getDeclaredField("accessCount");
        accessCountField.setAccessible(true); // Package private
        accessCountField.set(pooledItem, 3); // Enough for warm tier (threshold > 1)

        coldTier.offer(pooledItem);
        getAtomicInt("coldTierSize").incrementAndGet();

        // Acquire - should come from cold tier but be promoted to warm
        int[][] data = TieredMemoryPool.getInstance().acquire();
        assertNotNull(data);

        TieredMemoryPool.PoolStats stats = TieredMemoryPool.getInstance().getStats();
        assertEquals(1, stats.coldHits);
        assertEquals(1, stats.promotions);
        assertEquals(1, stats.warmSize, "Should be promoted to warm tier");
        assertEquals(0, stats.coldSize);
    }

    // Helper methods for reflection
    private void setInstanceField(String fieldName, Object value) throws Exception {
        Field field = TieredMemoryPool.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(TieredMemoryPool.getInstance(), value);
    }

    private Queue<?> getQueue(String fieldName) throws Exception {
        Field field = TieredMemoryPool.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Queue<?>) field.get(TieredMemoryPool.getInstance());
    }

    private AtomicInteger getAtomicInt(String fieldName) throws Exception {
        Field field = TieredMemoryPool.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (AtomicInteger) field.get(TieredMemoryPool.getInstance());
    }
     private AtomicLong getAtomicLong(String fieldName) throws Exception {
        Field field = TieredMemoryPool.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (AtomicLong) field.get(TieredMemoryPool.getInstance());
    }
}