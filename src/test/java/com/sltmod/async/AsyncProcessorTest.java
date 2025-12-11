package com.sltmod.async;

import com.sltmod.config.LayeredTerrainConfig;
import com.sltmod.memory.HeightmapCache;
import com.sltmod.memory.ResultCache;
import com.sltmod.memory.TieredMemoryPool;
import com.sltmod.monitoring.ProfilingMetrics;
import com.sltmod.processing.SlopeCalculator;
import com.sltmod.processing.Smoother;
import com.sltmod.processing.ThicknessConverter;
import com.sltmod.reliability.CircuitBreakerAdvanced;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.AbstractExecutorService;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AsyncProcessorTest {

    private MockedStatic<LayeredTerrainConfig> configMock;
    private MockedStatic<CircuitBreakerAdvanced> circuitMock;
    private MockedStatic<ProfilingMetrics> profilingMock;
    private MockedStatic<SlopeCalculator> slopeMock;
    private MockedStatic<ThicknessConverter> thicknessMock;
    private MockedStatic<Smoother> smootherMock;

    @BeforeEach
    void setUp() {
        circuitMock = mockStatic(CircuitBreakerAdvanced.class);
        profilingMock = mockStatic(ProfilingMetrics.class);
        slopeMock = mockStatic(SlopeCalculator.class);
        thicknessMock = mockStatic(ThicknessConverter.class);
        smootherMock = mockStatic(Smoother.class);

        // Config Provider
        AsyncProcessor.ConfigProvider config = new AsyncProcessor.ConfigProvider() {
            @Override public int getWorkerThreads() { return 2; }
            @Override public boolean isLoadBalancingEnabled() { return false; }
            @Override public boolean isDebugMode() { return false; }
            @Override public long getCalculationTimeoutMs() { return 1000; }
            @Override public boolean isLogSlowChunks() { return false; }
            @Override public int getSlowChunkThresholdMs() { return 100; }
            @Override public int getSmoothingPasses() { return 1; }
            @Override public int getMaxDifferential() { return 2; }
            @Override public int getPlayerChunkPriorityRadius() { return 8; }
        };
        AsyncProcessor.setConfigProvider(config);

        // HeightmapCache Config Provider
        HeightmapCache.setConfigProvider(new HeightmapCache.ConfigProvider() {
            @Override public boolean use7x7Sampling() { return false; }
            @Override public boolean use5x5Sampling() { return false; }
            @Override public boolean isPoolingEnabled() { return false; }
            @Override public boolean isProfilingEnabled() { return false; }
            @Override public boolean isDebugMode() { return false; }
        });

        // Setup Circuit Breaker
        circuitMock.when(CircuitBreakerAdvanced::shouldProcess).thenReturn(true);

        // Setup Profiling
        profilingMock.when(() -> ProfilingMetrics.measure(anyString(), any(java.util.function.Supplier.class))).thenAnswer(invocation -> {
            String stage = invocation.getArgument(0);
            // Mock heightmap cache creation to avoid real logic
            if ("heightmap_cache".equals(stage)) {
                return mock(HeightmapCache.class);
            }
            // Execute the supplier for other stages
            return ((java.util.function.Supplier<?>) invocation.getArgument(1)).get();
        });

        // Initialize AsyncProcessor
        AsyncProcessor.initialize();

        // Use direct executor for testing to ensure mockStatic works
        AsyncProcessor.setExecutor(new AbstractExecutorService() {
            private boolean shutdown = false;

            @Override
            public void execute(Runnable command) {
                command.run();
            }

            @Override
            public void shutdown() { shutdown = true; }

            @Override
            public List<Runnable> shutdownNow() { shutdown = true; return Collections.emptyList(); }

            @Override
            public boolean isShutdown() { return shutdown; }

            @Override
            public boolean isTerminated() { return shutdown; }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
        });

        // Inject ConfigProvider into TieredMemoryPool
        TieredMemoryPool.getInstance().setConfigProvider(new TieredMemoryPool.ConfigProvider() {
            @Override public boolean isPoolingEnabled() { return false; }
            @Override public boolean isTieredPoolingEnabled() { return false; }
            @Override public int getHotTierSize() { return 10; }
            @Override public int getWarmTierSize() { return 10; }
            @Override public int getColdTierSize() { return 10; }
            @Override public int getSimplePoolSize() { return 10; }
            @Override public int getCleanupIntervalSeconds() { return 300; }
            @Override public boolean isDebugMode() { return false; }
        });
        TieredMemoryPool.getInstance().initialize();
    }

    @AfterEach
    void tearDown() {
        AsyncProcessor.shutdown();
        circuitMock.close();
        profilingMock.close();
        slopeMock.close();
        thicknessMock.close();
        smootherMock.close();
    }

    @Test
    void testCalculateThicknessMapInternal_Success() {
        // Arrange
        LevelChunk chunk = mock(LevelChunk.class);
        ChunkPos pos = new ChunkPos(0, 0);
        when(chunk.getPos()).thenReturn(pos);
        when(chunk.getMinBuildHeight()).thenReturn(0);
        when(chunk.getMaxBuildHeight()).thenReturn(256);

        // Mock processing steps
        int[][] rawSlopes = new int[16][16];
        float[][] normalized = new float[16][16];
        int[][] thickness = new int[16][16];
        int[][] smoothed = new int[16][16];
        int[][] finalMap = new int[16][16];
        finalMap[0][0] = 5; // Marker

        slopeMock.when(() -> SlopeCalculator.calculateAllSlopes(any())).thenReturn(rawSlopes);
        slopeMock.when(() -> SlopeCalculator.normalizeSlopes(any())).thenReturn(normalized);
        thicknessMock.when(() -> ThicknessConverter.convertToThickness(any(), any())).thenReturn(thickness);
        smootherMock.when(() -> Smoother.smoothThickness(any(), anyInt(), any())).thenReturn(smoothed);
        smootherMock.when(() -> Smoother.clampDifferentials(any(), anyInt())).thenReturn(finalMap);

        // Config override handled by ConfigProvider

        // Act
        // We invoke the package-private method
        AsyncProcessor.calculateThicknessMapInternal(chunk, pos);

        // Assert
        ResultCache results = AsyncProcessor.getResults();
        assertTrue(results.size() > 0, "Results should contain the calculation");
        assertArrayEquals(finalMap, results.cache.get(pos).thicknessMap, "Result should match the calculated map");
    }

    // Timeout test disabled because it requires async execution which conflicts with thread-local static mocks
    // @Test
    // void testCalculateThicknessMapInternal_Timeout() { ... }
}
