package com.sltmod.benchmark;

import com.sltmod.config.LayeredTerrainConfig;
import com.sltmod.processing.Smoother;
import org.openjdk.jmh.annotations.*;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class TerrainProcessingBenchmark {

    private int[][] input;

    @Setup
    public void setup() {
        input = new int[16][16];
        Random rand = new Random(12345);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                input[x][z] = rand.nextInt(8) + 1;
            }
        }

        // Ensure static initialization of config
        try {
            Class.forName(LayeredTerrainConfig.class.getName());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Benchmark
    public int[][] benchmarkGaussian() {
        return Smoother.smoothThickness(input, 2, null, LayeredTerrainConfig.SmoothingType.GAUSSIAN, 1.0f);
    }

    @Benchmark
    public int[][] benchmarkBilateral() {
        return Smoother.smoothThickness(input, 2, null, LayeredTerrainConfig.SmoothingType.BILATERAL, 1.0f);
    }
}
