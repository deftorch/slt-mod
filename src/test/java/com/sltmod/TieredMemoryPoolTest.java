package com.sltmod;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

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

        // Inject mock config into TieredMemoryPool
        setStaticField(TieredMemoryPool.class, "config", mockConfig);

        // Reset TieredMemoryPool state
        setStaticField(TieredMemoryPool.class, "enabled", true);
        setStaticField(TieredMemoryPool.class, "tieredEnabled", true);
        setStaticField(TieredMemoryPool.class, "maxHotTierSize", 10);
        setStaticField(TieredMemoryPool.class, "maxWarmTierSize", 10);
        setStaticField(TieredMemoryPool.class, "maxColdTierSize", 10);

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
        int[][] data = TieredMemoryPool.acquire();
        assertNotNull(data);
        assertEquals(16, data.length);
        assertEquals(16, data[0].length);

        TieredMemoryPool.PoolStats stats = TieredMemoryPool.getStats();
        assertEquals(1, stats.totalAllocations);
        assertEquals(1, stats.misses);
    }

    @Test
    public void testReleaseAndAcquire() {
        // Acquire (miss)
        int[][] data = TieredMemoryPool.acquire();

        // Release
        TieredMemoryPool.release(data);

        // With tieredEnabled=true and fresh release (accessCount=1), it goes to COLD tier
        TieredMemoryPool.PoolStats stats = TieredMemoryPool.getStats();
        assertEquals(1, stats.coldSize, "Should be in cold tier");

        // Acquire (should hit cold tier)
        int[][] data2 = TieredMemoryPool.acquire();

        // When acquired from cold, it should be removed from cold
        stats = TieredMemoryPool.getStats();
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
        int[][] data = TieredMemoryPool.acquire();
        assertNotNull(data);

        TieredMemoryPool.PoolStats stats = TieredMemoryPool.getStats();
        assertEquals(1, stats.coldHits);
        assertEquals(1, stats.promotions);
        assertEquals(1, stats.warmSize, "Should be promoted to warm tier");
        assertEquals(0, stats.coldSize);
    }

    // Helper methods for reflection
    private void setStaticField(Class<?> clazz, String fieldName, Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    private Queue<?> getQueue(String fieldName) throws Exception {
        Field field = TieredMemoryPool.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Queue<?>) field.get(null);
    }

    private AtomicInteger getAtomicInt(String fieldName) throws Exception {
        Field field = TieredMemoryPool.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (AtomicInteger) field.get(null);
    }
     private AtomicLong getAtomicLong(String fieldName) throws Exception {
        Field field = TieredMemoryPool.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (AtomicLong) field.get(null);
    }
}
