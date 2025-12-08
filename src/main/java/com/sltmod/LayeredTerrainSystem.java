package com.sltmod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Main system orchestrator - coordinates all components
 */
public class LayeredTerrainSystem {

    private static int tickCounter = 0;
    private static int healthCheckCounter = 0;

    /**
     * Handle chunk load events
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkDataEvent.Load event) {
        // Validate event
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        if (chunk.getLevel().isClientSide()) return;
        if (!LayeredTerrainConfig.ENABLED.get()) return;

        // Validate chunk
        if (!ChunkValidator.isValidForProcessing(chunk)) {
            return;
        }

        // Check if already processed
        if (NBTHelper.isProcessed(chunk)) {
            return;
        }

        // Submit to queue
        boolean submitted = QueueManager.submit(chunk);
        if (!submitted) {
            Metrics.recordChunkSkipped();

            if (LayeredTerrainConfig.DEBUG_MODE.get()) {
                LayeredTerrainMod.LOGGER.debug(
                    "Queue full, skipped chunk {}", chunk.getPos()
                );
            }
        }
    }

    /**
     * Handle server tick events
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!LayeredTerrainConfig.ENABLED.get()) return;

        tickCounter++;

        // Hot-reload check (every tick is fine, it's lightweight)
        LayeredTerrainConfig.checkConfigReload();

        // Process queued chunks
        QueueManager.processQueue();

        // Apply pending calculations
        for (ServerLevel level : event.getServer().getAllLevels()) {
            applyPendingCalculations(level);
        }

        // Periodic health check
        if (LayeredTerrainConfig.ENABLE_HEALTH_CHECKS.get()) {
            healthCheckCounter++;
            int interval = LayeredTerrainConfig.HEALTH_CHECK_INTERVAL_SECONDS.get() * 20; // Convert to ticks

            if (healthCheckCounter >= interval) {
                healthCheckCounter = 0;
                SystemDiagnostics.runHealthCheck();
            }
        }
    }

    /**
     * Apply pending calculations from async processor
     */
    private static void applyPendingCalculations(ServerLevel level) {
        ResultCache results = AsyncProcessor.getResults();
        int maxPerTick = LayeredTerrainConfig.MAX_CHUNKS_PER_TICK.get();
        int applied = 0;

        // Get all pending results (copy keys to avoid concurrent modification)
        List<ChunkPos> pendingChunks = new ArrayList<>(results.cache.keySet());

        for (ChunkPos pos : pendingChunks) {
            if (applied >= maxPerTick) break;

            ResultCache.CachedResult result = results.poll(pos);
            if (result == null) continue;

            // Get chunk
            LevelChunk chunk = level.getChunk(pos.x, pos.z);
            if (chunk == null || chunk.isEmpty()) {
                if (LayeredTerrainConfig.DEBUG_MODE.get()) {
                    LayeredTerrainMod.LOGGER.debug(
                        "Chunk {} unavailable for result application", pos
                    );
                }
                continue;
            }

            try {
                applyLayersToChunk(chunk, result.thicknessMap, result.calculationTime);
                applied++;

            } catch (Exception e) {
                LayeredTerrainMod.LOGGER.error("Failed to apply layers to {}", pos, e);
            }
        }
    }

    /**
     * Apply calculated layers to chunk
     */
    private static void applyLayersToChunk(
        LevelChunk chunk,
        int[][] thicknessMap,
        long calculationTime
    ) {
        long startTime = System.nanoTime();
        HeightmapCache cache = null;

        try {
            // Create cache
            cache = new HeightmapCache(chunk);
            final HeightmapCache finalCache = cache;

            // Place blocks
            int blocksPlaced = ProfilingMetrics.measure("block_placement",
                () -> BlockPlacer.placeLayersOptimized(chunk, finalCache, thicknessMap));

            // Update lighting
            ProfilingMetrics.measure("lighting_update",
                () -> LightingUpdater.batchUpdateLighting(chunk));

            // Mark as processed
            NBTHelper.markAsProcessed(chunk);

            // Record metrics
            long totalTime = System.nanoTime() - startTime;
            Metrics.recordChunkProcessed(calculationTime + totalTime, blocksPlaced);

            // Debug logging
            if (LayeredTerrainConfig.DEBUG_MODE.get()) {
                LayeredTerrainMod.LOGGER.debug(
                    "✅ Processed chunk {} in {:.2f}ms ({} blocks)",
                    chunk.getPos(),
                    (calculationTime + totalTime) / 1_000_000.0,
                    blocksPlaced
                );
            }

        } catch (Exception e) {
            LayeredTerrainMod.LOGGER.error("Error applying layers to {}", chunk.getPos(), e);
            throw e;

        } finally {
            // Always release cache
            if (cache != null) {
                cache.release();
            }
        }
    }

    /**
     * Synchronous processing (fallback or testing)
     */
    public static void processChunkSync(LevelChunk chunk) {
        if (chunk == null || chunk.isEmpty()) {
            throw new IllegalArgumentException("Invalid chunk for sync processing");
        }

        long startTime = System.nanoTime();
        HeightmapCache cache = null;

        try {
            // Create cache
            cache = new HeightmapCache(chunk);

            // Calculate slopes
            int[][] rawSlopes = SlopeCalculator.calculateAllSlopes(cache);
            float[][] normalized = SlopeCalculator.normalizeSlopes(rawSlopes);

            // Convert to thickness
            int[][] rawThickness = ThicknessConverter.convertToThickness(normalized, chunk);

            // Smooth
            int passes = LayeredTerrainConfig.SMOOTHING_PASSES.get();
            int[][] smoothed = Smoother.smoothThickness(rawThickness, passes, cache);

            // Clamp differentials
            int maxDiff = LayeredTerrainConfig.MAX_DIFFERENTIAL.get();
            int[][] finalThickness = Smoother.clampDifferentials(smoothed, maxDiff);

            // Place blocks
            int blocksPlaced = BlockPlacer.placeLayersOptimized(chunk, cache, finalThickness);

            // Update lighting
            LightingUpdater.batchUpdateLighting(chunk);

            // Mark as processed
            NBTHelper.markAsProcessed(chunk);

            // Record metrics
            long duration = System.nanoTime() - startTime;
            Metrics.recordChunkProcessed(duration, blocksPlaced);

        } finally {
            if (cache != null) {
                cache.release();
            }
        }
    }
}
