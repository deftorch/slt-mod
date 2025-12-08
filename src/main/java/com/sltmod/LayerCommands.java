package com.sltmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.concurrent.CompletableFuture;

/**
 * Comprehensive admin command system
 */
@Mod.EventBusSubscriber
public class LayerCommands {

    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            Commands.literal("layerterrain")
                .requires(source -> source.hasPermission(2))

                // ========================================
                // /layerterrain stats
                // ========================================
                .then(Commands.literal("stats")
                    .executes(ctx -> {
                        Metrics.printReport();
                        ctx.getSource().sendSuccess(
                            () -> Component.literal("✅ Check console for detailed stats"),
                            false
                        );
                        return 1;
                    })
                )

                // ========================================
                // /layerterrain profiling
                // ========================================
                .then(Commands.literal("profiling")
                    .executes(ctx -> {
                        if (LayeredTerrainConfig.ENABLE_PROFILING.get()) {
                            ProfilingMetrics.printDetailedReport();
                            ctx.getSource().sendSuccess(
                                () -> Component.literal("✅ Check console for profiling report"),
                                false
                            );
                        } else {
                            ctx.getSource().sendFailure(
                                Component.literal("❌ Profiling is disabled in config")
                            );
                        }
                        return 1;
                    })
                )

                // ========================================
                // /layerterrain health
                // ========================================
                .then(Commands.literal("health")
                    .executes(ctx -> {
                        SystemDiagnostics.runHealthCheck();
                        ctx.getSource().sendSuccess(
                            () -> Component.literal("✅ Health check complete - see console"),
                            false
                        );
                        return 1;
                    })
                )

                // ========================================
                // /layerterrain pool
                // ========================================
                .then(Commands.literal("pool")
                    .executes(ctx -> {
                        if (LayeredTerrainConfig.ENABLE_MEMORY_POOLING.get()) {
                            TieredMemoryPool.PoolStats stats = TieredMemoryPool.getStats();

                            ctx.getSource().sendSuccess(
                                () -> Component.literal(String.format(
                                    "Memory Pool: %d total | Hit Rate: %.1f%%",
                                    stats.getTotalSize(), stats.getHitRate()
                                )),
                                false
                            );

                            TieredMemoryPool.printStats();
                        } else {
                            ctx.getSource().sendFailure(
                                Component.literal("❌ Memory pooling is disabled")
                            );
                        }
                        return 1;
                    })
                )

                // ========================================
                // /layerterrain queue
                // ========================================
                .then(Commands.literal("queue")
                    .executes(ctx -> {
                        int queueSize = QueueManager.getQueueSize();
                        int cacheSize = AsyncProcessor.getResults().size();
                        int maxQueue = LayeredTerrainConfig.MAX_QUEUE_SIZE.get();

                        ctx.getSource().sendSuccess(
                            () -> Component.literal(String.format(
                                "📊 Queue: %d/%d pending | Cache: %d results",
                                queueSize, maxQueue, cacheSize
                            )),
                            false
                        );
                        return 1;
                    })
                )

                // ========================================
                // /layerterrain circuit
                // ========================================
                .then(Commands.literal("circuit")
                    .executes(ctx -> {
                        if (LayeredTerrainConfig.ENABLE_CIRCUIT_BREAKER.get()) {
                            CircuitBreakerAdvanced.printStats();
                            ctx.getSource().sendSuccess(
                                () -> Component.literal(
                                    "Circuit Breaker: " + CircuitBreakerAdvanced.getState()
                                ),
                                false
                            );
                        } else {
                            ctx.getSource().sendFailure(
                                Component.literal("❌ Circuit breaker is disabled")
                            );
                        }
                        return 1;
                    })
                )

                // ========================================
                // /layerterrain loadbalancer
                // ========================================
                .then(Commands.literal("loadbalancer")
                    .executes(ctx -> {
                        if (LayeredTerrainConfig.ENABLE_LOAD_BALANCING.get()) {
                            String stats = LoadBalancer.getStats();
                            ctx.getSource().sendSuccess(
                                () -> Component.literal(stats),
                                false
                            );
                        } else {
                            ctx.getSource().sendFailure(
                                Component.literal("❌ Load balancing is disabled")
                            );
                        }
                        return 1;
                    })
                )

                // ========================================
                // /layerterrain benchmark
                // ========================================
                .then(Commands.literal("benchmark")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(
                            () -> Component.literal("⏱ Running benchmark... (check console)"),
                            true
                        );

                        // Run async to not block server
                        CompletableFuture.runAsync(() -> {
                            PerformanceBenchmark.runBenchmark();
                        });

                        return 1;
                    })
                )

                // ========================================
                // /layerterrain reload
                // ========================================
                .then(Commands.literal("reload")
                    .executes(ctx -> {
                        LayeredTerrainConfig.checkConfigReload();
                        NBTHelper.updateConfigHash();
                        ChunkValidator.reloadBlacklists();
                        BiomeBlender.clearCache();

                        ctx.getSource().sendSuccess(
                            () -> Component.literal("🔄 Config reloaded!"),
                            true
                        );
                        return 1;
                    })
                )

                // ========================================
                // /layerterrain reprocess <radius>
                // ========================================
                .then(Commands.literal("reprocess")
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 10))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            int radius = IntegerArgumentType.getInteger(ctx, "radius");

                            ChunkPos center = new ChunkPos(player.blockPosition());
                            int count = 0;

                            for (int x = -radius; x <= radius; x++) {
                                for (int z = -radius; z <= radius; z++) {
                                    ChunkPos pos = new ChunkPos(center.x + x, center.z + z);
                                    LevelChunk chunk = player.serverLevel().getChunk(pos.x, pos.z);

                                    if (chunk != null && !chunk.isEmpty()) {
                                        NBTHelper.clearProcessedFlag(chunk);
                                        QueueManager.submit(chunk);
                                        count++;
                                    }
                                }
                            }

                            final int finalCount = count;
                            ctx.getSource().sendSuccess(
                                () -> Component.literal("🔄 Reprocessing " + finalCount + " chunks"),
                                true
                            );
                            return 1;
                        })
                    )
                )

                // ========================================
                // /layerterrain reset
                // ========================================
                .then(Commands.literal("reset")
                    .executes(ctx -> {
                        Metrics.reset();
                        ProfilingMetrics.reset();
                        QueueManager.clear();
                        EdgeCaseFilter.resetStats();

                        if (LayeredTerrainConfig.ENABLE_CIRCUIT_BREAKER.get()) {
                            CircuitBreakerAdvanced.forceReset();
                        }

                        ctx.getSource().sendSuccess(
                            () -> Component.literal("🔄 Metrics and queue reset"),
                            true
                        );
                        return 1;
                    })
                )

                // ========================================
                // /layerterrain debug
                // ========================================
                .then(Commands.literal("debug")
                    .executes(ctx -> {
                        printDebugInfo(ctx);
                        return 1;
                    })
                )

                // ========================================
                // /layerterrain version
                // ========================================
                .then(Commands.literal("version")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(
                            () -> Component.literal(
                                "Layered Terrain System v" + LayeredTerrainMod.VERSION +
                                " - Ultimate Hybrid Edition"
                            ),
                            false
                        );
                        return 1;
                    })
                )

                // ========================================
                // /layerterrain export (Prometheus)
                // ========================================
                .then(Commands.literal("export")
                    .executes(ctx -> {
                        if (LayeredTerrainConfig.ENABLE_PROMETHEUS.get()) {
                            String metrics = PrometheusExporter.exportMetrics();
                            ctx.getSource().sendSuccess(
                                () -> Component.literal("Metrics exported (see console)"),
                                false
                            );
                            LayeredTerrainMod.LOGGER.info("Prometheus Metrics:\n{}", metrics);
                        } else {
                            ctx.getSource().sendFailure(
                                Component.literal("❌ Prometheus export is disabled")
                            );
                        }
                        return 1;
                    })
                )

                // ========================================
                // /layerterrain diagnostics
                // ========================================
                .then(Commands.literal("diagnostics")
                    .executes(ctx -> {
                        SystemDiagnostics.runStartupCheck();
                        ctx.getSource().sendSuccess(
                            () -> Component.literal("✅ Diagnostics complete - see console"),
                            false
                        );
                        return 1;
                    })
                )
        );
    }

    /**
     * Print comprehensive debug information
     */
    private static void printDebugInfo(CommandContext<CommandSourceStack> ctx) {
        LayeredTerrainMod.LOGGER.info("╔════════════════════════════════════════════════════╗");
        LayeredTerrainMod.LOGGER.info("║  DEBUG INFORMATION                                 ║");
        LayeredTerrainMod.LOGGER.info("╠════════════════════════════════════════════════════╣");

        // System info
        LayeredTerrainMod.LOGGER.info("║  Version: {}                                    ",
            LayeredTerrainMod.VERSION);
        LayeredTerrainMod.LOGGER.info("║  Enabled: {}                                      ",
            LayeredTerrainConfig.ENABLED.get());

        // Component status
        LayeredTerrainMod.LOGGER.info("╠════════════════════════════════════════════════════╣");
        LayeredTerrainMod.LOGGER.info("║  Component Status:                                 ║");

        if (LayeredTerrainConfig.ENABLE_MEMORY_POOLING.get()) {
            TieredMemoryPool.PoolStats poolStats = TieredMemoryPool.getStats();
            LayeredTerrainMod.LOGGER.info("║    Memory Pool: {} items, {:.1f}% hit rate      ",
                poolStats.getTotalSize(), poolStats.getHitRate());
        }

        if (LayeredTerrainConfig.ENABLE_CIRCUIT_BREAKER.get()) {
            LayeredTerrainMod.LOGGER.info("║    Circuit Breaker: {}                         ",
                CircuitBreakerAdvanced.getState());
        }

        if (LayeredTerrainConfig.ENABLE_LOAD_BALANCING.get()) {
            LayeredTerrainMod.LOGGER.info("║    Load Balancer: Active                           ║");
        }

        // Queue status
        LayeredTerrainMod.LOGGER.info("╠════════════════════════════════════════════════════╣");
        LayeredTerrainMod.LOGGER.info("║  Queue: {} pending, {} results                    ",
            QueueManager.getQueueSize(), AsyncProcessor.getResults().size());

        // Async processor
        AsyncProcessor.ProcessorStats procStats = AsyncProcessor.getStats();
        LayeredTerrainMod.LOGGER.info("║  Async: {} started, {} completed ({:.1f}%)         ",
            procStats.started, procStats.completed, procStats.getSuccessRate());

        // Registry
        LayerRegistry.RegistryStats regStats = LayerRegistry.getStats();
        LayeredTerrainMod.LOGGER.info("║  Registry: {} blocks registered                   ",
            regStats.totalBlocks);

        // Validator
        ChunkValidator.ValidationStats valStats = ChunkValidator.getStats();
        LayeredTerrainMod.LOGGER.info("║  Validator: {} checks, {:.1f}% reject rate        ",
            valStats.totalValidations, valStats.rejectRate);

        LayeredTerrainMod.LOGGER.info("╚════════════════════════════════════════════════════╝");

        ctx.getSource().sendSuccess(
            () -> Component.literal("✅ Debug info printed to console"),
            false
        );
    }
}
