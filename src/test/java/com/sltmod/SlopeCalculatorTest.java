package com.sltmod;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sltmod.memory.HeightmapCache;
import com.sltmod.processing.SlopeCalculator;

/**
 * Unit tests for the {@link SlopeCalculator} class.
 *
 * <p>Verifies slope calculation logic, including grid sampling, damping, and edge case handling.</p>
 */
@ExtendWith(MockitoExtension.class)
public class SlopeCalculatorTest {

    @Mock
    HeightmapCache cache;

    @Test
    public void testCalculateLocalSlope3x3_Flat() {
        when(cache.getGridSize()).thenReturn(3);
        // Mock getHeight to return 10 for all calls
        when(cache.getHeight(anyInt(), anyInt())).thenReturn(10);

        int slope = SlopeCalculator.calculateLocalSlope(cache, 8, 8);
        assertEquals(0, slope);
    }

    @Test
    public void testCalculateLocalSlope3x3_Slope() {
        when(cache.getGridSize()).thenReturn(3);
        // Center is 10. One neighbor is 20. Slope should be 10.
        when(cache.getHeight(anyInt(), anyInt())).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            int z = invocation.getArgument(1);
            if (x == 9 && z == 9) return 20;
            return 10;
        });

        int slope = SlopeCalculator.calculateLocalSlope(cache, 8, 8);
        // Max height 20, min height 10. Diff is 10.
        assertEquals(10, slope);
    }

    @Test
    public void testCalculateAllSlopes() {
        when(cache.getGridSize()).thenReturn(3);
        when(cache.getHeight(anyInt(), anyInt())).thenReturn(10);

        int[][] slopes = SlopeCalculator.calculateAllSlopes(cache);
        assertNotNull(slopes);
        assertEquals(16, slopes.length);
        assertEquals(0, slopes[0][0]);
    }
}