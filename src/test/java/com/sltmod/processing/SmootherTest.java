package com.sltmod.processing;

import com.sltmod.config.LayeredTerrainConfig;
import com.sltmod.memory.HeightmapCache;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SmootherTest {

    @Test
    public void testSmoothThicknessGaussian() {
        int[][] input = new int[16][16];
        // Create a spike in the middle
        input[8][8] = 8;

        // Gaussian smoothing with strength 1.0 should reduce the spike
        int[][] result = Smoother.smoothThickness(input, 1, null, LayeredTerrainConfig.SmoothingType.GAUSSIAN, 1.0f);

        assertNotNull(result);
        assertEquals(16, result.length);
        assertEquals(16, result[0].length);

        // The spike should be lower
        assertTrue(result[8][8] < 8);

        // Neighbors should be higher than 0
        assertTrue(result[7][8] > 0);
        assertTrue(result[9][8] > 0);
        assertTrue(result[8][7] > 0);
        assertTrue(result[8][9] > 0);
    }

    @Test
    public void testSmoothThicknessMedian() {
        int[][] input = new int[16][16];
        // Noisy input
        for(int x=0; x<16; x++) {
            for(int z=0; z<16; z++) {
                input[x][z] = 5;
            }
        }
        input[8][8] = 100; // Outlier

        int[][] result = Smoother.smoothThickness(input, 1, null, LayeredTerrainConfig.SmoothingType.MEDIAN, 1.0f);

        // Median filter should remove the outlier
        assertEquals(5, result[8][8]);
    }

    @Test
    public void testSmoothThicknessBilateral() {
        int[][] input = new int[16][16];
        // Edge
        for(int x=0; x<16; x++) {
            for(int z=0; z<16; z++) {
                input[x][z] = (x < 8) ? 2 : 8;
            }
        }

        int[][] result = Smoother.smoothThickness(input, 1, null, LayeredTerrainConfig.SmoothingType.BILATERAL, 1.0f);

        // Bilateral should preserve the edge
        // Check near edge
        assertTrue(result[7][8] <= 4); // Still low
        assertTrue(result[8][8] >= 6); // Still high
    }

    @Test
    public void testSmoothThicknessAnisotropicFallback() {
        int[][] input = new int[16][16];
        input[8][8] = 8;

        // Without cache, should fallback to Gaussian
        int[][] result = Smoother.smoothThickness(input, 1, null, LayeredTerrainConfig.SmoothingType.ANISOTROPIC, 1.0f);

        assertTrue(result[8][8] < 8);
    }

    @Test
    public void testSmoothThicknessAdaptive() {
        int[][] input = new int[16][16];
        input[8][8] = 8;

        int[][] result = Smoother.smoothThickness(input, 1, null, LayeredTerrainConfig.SmoothingType.ADAPTIVE, 1.0f);

        assertNotNull(result);
    }

    @Test
    public void testSmoothThicknessMultiScale() {
        int[][] input = new int[16][16];
        input[8][8] = 8;

        int[][] result = Smoother.smoothThickness(input, 1, null, LayeredTerrainConfig.SmoothingType.MULTI_SCALE, 1.0f);

        assertNotNull(result);
        assertTrue(result[8][8] < 8);
    }

    @Test
    public void testClampDifferentials() {
        int[][] input = new int[16][16];
        input[0][0] = 1;
        input[1][0] = 8; // Diff is 7

        int maxDiff = 2;
        int[][] result = Smoother.clampDifferentials(input, maxDiff);

        // Should be adjusted
        int diff = Math.abs(result[0][0] - result[1][0]);
        assertTrue(diff <= maxDiff);
    }
}
