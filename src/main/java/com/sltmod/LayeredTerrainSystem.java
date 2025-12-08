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
 * Main system orchestrator that coordinates all components of the Layered Terrain System.
 *
 * <p>This class acts as the central hub, listening to Forge events to trigger chunk processing.
 * It manages the flow from chunk loading to validation, queue submission, and final
 * application of terrain layers.</p>
 *
 * <p>It handles:</p>
 * <ul>
 *   <li>{@link ChunkDataEvent.Load}: Validates and queues chunks for processing.</li>
 *   <li>{@link ChunkDataEvent.Save}: Persists processing state to NBT.</li>
 *   <li>{@link TickEvent.ServerTickEvent}: Drives the async queue and applies results.</li>
 * </ul>
 *
 * <p>The class also manages health checks and integrates with the {@link AsyncProcessor}
 * to handle off-thread calculations.</p>
 */
public class LayeredTerrainSystem {

    private static int healthCheckCounter = 0;

    /**
     * Handles the chunk load event to initiate terrain processing.
     *
     * <p>Checks if the chunk is valid, not yet processed, and if the system is enabled.
     * If all checks pass, the chunk is submitted to the {@link QueueManager} for
     * asynchronous processing.</p>
     *
     * @param event The chunk load event triggered by Forge.
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkDataEvent.Load event) {
        // Validate event
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        if (chunk.getLevel().isClientSide()) return;
        if (!LayeredTerrainConfig.ENABLED.get()) return;

        // Restore processing state from NBT
        NBTHelper.loadFromNBT(chunk, event.getData());

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
     * Handles the chunk save event to persist state.
     *
     * <p>Saves the processing status (whether the chunk has already been layered)
     * to the chunk's NBT data.</p>
     *
     * @param event The chunk save event triggered by Forge.
     */
    @SubscribeEvent
    public static void onChunkSave(ChunkDataEvent.Save event) {
        if (event.getChunk() instanceof LevelChunk chunk) {
            NBTHelper.saveToNBT(chunk, event.getData());
        }
    }

    /**
     * Handles the server tick event to drive the processing loop.
     *
     * <p>Performs the following actions every tick (or periodically):
     * <ul>
     *   <li>Checks for configuration hot-reloads.</li>
     *   <li>Processes the chunk queue via {@link QueueManager}.</li>
     *   <li>Applies pending calculations to chunks in the world.</li>
     *   <li>Runs system health checks at configured intervals.</li>
     * </ul>
     * </p>
     *
     * @param event The server tick event.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!LayeredTerrainConfig.ENABLED.get()) return;

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
     * Applies pending calculations from the async processor to the world.
     *
     * <p>Polls the {@link ResultCache} for completed thickness maps and applies them
     * to the corresponding chunks. Limits the number of applications per tick
     * to prevent lag.</p>
     *
     * @param level The server level to apply changes to.
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
     * Applies the calculated thickness map to a chunk.
     *
     * <p>This method performs the final block placement and lighting updates.
     * It tracks performance metrics for block placement and lighting separately.</p>
     *
     * @param chunk The chunk to modify.
     * @param thicknessMap The calculated thickness values for the chunk.
     * @param calculationTime The time taken to calculate the thickness map (in nanoseconds).
     * @throws Exception If an error occurs during block placement.
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
     * Processes a chunk synchronously.
     *
     * <p>This is a fallback or testing method that performs all steps (slope calculation,
     * smoothing, block placement) on the current thread. It bypasses the async queue.</p>
     *
     * @param chunk The chunk to process.
     * @throws IllegalArgumentException if the chunk is null or empty.
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
