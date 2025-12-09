package com.sltmod.async;

import com.sltmod.config.LayeredTerrainConfig;
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
        configMock = mockStatic(LayeredTerrainConfig.class);
        circuitMock = mockStatic(CircuitBreakerAdvanced.class);
        profilingMock = mockStatic(ProfilingMetrics.class);
        slopeMock = mockStatic(SlopeCalculator.class);
        thicknessMock = mockStatic(ThicknessConverter.class);
        smootherMock = mockStatic(Smoother.class);

        // Setup default config values
        setupConfigMock(configMock, LayeredTerrainConfig.WORKER_THREADS, 2);
        setupConfigMock(configMock, LayeredTerrainConfig.CALCULATION_TIMEOUT_MS, 1000);
        setupConfigMock(configMock, LayeredTerrainConfig.LOG_SLOW_CHUNKS, false);
        setupConfigMock(configMock, LayeredTerrainConfig.SLOW_CHUNK_THRESHOLD_MS, 100);
        setupConfigMock(configMock, LayeredTerrainConfig.SMOOTHING_PASSES, 1);
        setupConfigMock(configMock, LayeredTerrainConfig.MAX_DIFFERENTIAL, 2);
        setupConfigMock(configMock, LayeredTerrainConfig.ENABLE_MEMORY_POOLING, false);
        setupConfigMock(configMock, LayeredTerrainConfig.USE_7X7_SAMPLING, false);
        setupConfigMock(configMock, LayeredTerrainConfig.USE_5X5_SAMPLING, false);

        // Setup Circuit Breaker
        circuitMock.when(CircuitBreakerAdvanced::shouldProcess).thenReturn(true);

        // Setup Profiling
        profilingMock.when(() -> ProfilingMetrics.measure(anyString(), any())).thenAnswer(invocation -> {
            // Execute the supplier
            return ((java.util.function.Supplier<?>) invocation.getArgument(1)).get();
        });

        // Initialize AsyncProcessor
        AsyncProcessor.initialize();
    }

    private <T> void setupConfigMock(MockedStatic<LayeredTerrainConfig> mock, Object configSpec, T value) {
        // This is a bit tricky because ForgeConfigSpec.ConfigValue is not easily mockable without full Forge setup.
        // But we can try to mock the static fields if they were simple values, but they are ConfigValue objects.
        // However, looking at the code, we are accessing .get() on them.
        // We can mock the field access? No, fields are final.
        // We have to mock the ConfigValue objects themselves?
        // The static fields in LayeredTerrainConfig are initialized in static block.
        // Accessing them might be hard if we don't mock the whole class structure or replace the fields.
        // But since we mocked the whole class LayeredTerrainConfig, accessing static fields on it returns null by default?
        // No, mockStatic mocks static methods. It doesn't affect static fields unless we do something else.

        // Actually, the code uses LayeredTerrainConfig.WORKER_THREADS.get().
        // Since we cannot easily mock the final static fields, we might need a different approach.
        // If the fields are initialized, we can use reflection to replace them with mocks.
    }

    // Helper to mock ConfigValue.get()
    private <T> void mockConfigValue(String fieldName, T value) {
        try {
            java.lang.reflect.Field field = LayeredTerrainConfig.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            net.minecraftforge.common.ForgeConfigSpec.ConfigValue<T> mockValue = mock(net.minecraftforge.common.ForgeConfigSpec.ConfigValue.class);
            when(mockValue.get()).thenReturn(value);

            // We need to remove final modifier to set it
            java.lang.reflect.Field modifiersField = java.lang.reflect.Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);

            field.set(null, mockValue);
        } catch (Exception e) {
            throw new RuntimeException("Failed to mock config field: " + fieldName, e);
        }
    }

    @AfterEach
    void tearDown() {
        AsyncProcessor.shutdown();
        configMock.close();
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

        // We need to reinject the mocks because setupConfigMock didn't work as expected in the comments.
        // Let's do the reflection hack.
        mockConfigValue("WORKER_THREADS", 2);
        mockConfigValue("CALCULATION_TIMEOUT_MS", 1000);
        mockConfigValue("LOG_SLOW_CHUNKS", false);
        mockConfigValue("ENABLE_MEMORY_POOLING", false);
        mockConfigValue("USE_7X7_SAMPLING", false);
        mockConfigValue("USE_5X5_SAMPLING", false);
        mockConfigValue("SMOOTHING_PASSES", 1);
        mockConfigValue("MAX_DIFFERENTIAL", 2);
        mockConfigValue("ENABLE_PROFILING", false);

        // Act
        // We invoke the package-private method
        AsyncProcessor.calculateThicknessMapInternal(chunk, pos);

        // Assert
        ResultCache results = AsyncProcessor.getResults();
        assertTrue(results.size() > 0, "Results should contain the calculation");
        assertArrayEquals(finalMap, results.get(pos), "Result should match the calculated map");
    }

    @Test
    void testCalculateThicknessMapInternal_Timeout() {
        // Arrange
        LevelChunk chunk = mock(LevelChunk.class);
        ChunkPos pos = new ChunkPos(1, 1);
        when(chunk.getPos()).thenReturn(pos);
        when(chunk.getMinBuildHeight()).thenReturn(0);
        when(chunk.getMaxBuildHeight()).thenReturn(256);

        mockConfigValue("WORKER_THREADS", 2);
        mockConfigValue("CALCULATION_TIMEOUT_MS", 50); // Short timeout
        mockConfigValue("LOG_SLOW_CHUNKS", false);
        mockConfigValue("ENABLE_MEMORY_POOLING", false);
        mockConfigValue("USE_7X7_SAMPLING", false);
        mockConfigValue("USE_5X5_SAMPLING", false);
        mockConfigValue("ENABLE_PROFILING", false);

        // Make calculation sleep longer than timeout
        profilingMock.when(() -> ProfilingMetrics.measure(anyString(), any())).thenAnswer(invocation -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {}
            return ((java.util.function.Supplier<?>) invocation.getArgument(1)).get();
        });

        // Act
        AsyncProcessor.calculateThicknessMapInternal(chunk, pos);

        // Assert
        ResultCache results = AsyncProcessor.getResults();
        assertEquals(0, results.size(), "Results should be empty after timeout");

        AsyncProcessor.ProcessorStats stats = AsyncProcessor.getStats();
        assertTrue(stats.timedOut > 0, "Should record a timeout");
    }
}
