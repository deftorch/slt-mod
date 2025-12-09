package com.sltmod;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.sltmod.async.AsyncProcessor;
import com.sltmod.async.LoadBalancer;
import com.sltmod.config.LayeredTerrainConfig;
import com.sltmod.integration.LayerRegistry;
import com.sltmod.integration.LayeredTerrainSystem;
import com.sltmod.memory.TieredMemoryPool;
import com.sltmod.monitoring.Metrics;
import com.sltmod.monitoring.ProfilingMetrics;
import com.sltmod.monitoring.PrometheusExporter;
import com.sltmod.monitoring.SystemDiagnostics;
import com.sltmod.processing.GPUAccelerator;
import com.sltmod.processing.MLPredictor;
import com.sltmod.reliability.CircuitBreakerAdvanced;

/**
 * Main mod class for the Layered Terrain System.
 *
 * <p>This class initializes the mod, registers event listeners, and manages the lifecycle
 * of the terrain generation system. It handles startup, configuration loading, and graceful shutdown.
 *
 * <p>The mod transforms vanilla Minecraft terrain into smooth, layered landscapes using
 * sophisticated algorithms and efficient processing.</p>
 */
@Mod(Reference.MOD_ID)
public class LayeredTerrainMod {

    public static final Logger LOGGER = LogManager.getLogger();

    private static long initStartTime;

    /**
     * Constructs the main mod instance and registers basic event listeners.
     *
     * @param context The FML Java mod loading context provided by Forge.
     */
    public LayeredTerrainMod(FMLJavaModLoadingContext context) {
        initStartTime = System.currentTimeMillis();

        IEventBus modEventBus = context.getModEventBus();

        // Register configuration
        ModLoadingContext.get().registerConfig(
            ModConfig.Type.COMMON,
            LayeredTerrainConfig.SPEC,
            "layered-terrain.toml"
        );

        // Initialize file watcher
        LayeredTerrainConfig.initFileWatcher();

        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(LayeredTerrainSystem.class);

        // Defer detailed logging to commonSetup where config is available
        LOGGER.info("Layered Terrain System v{} initializing...", Reference.VERSION);
    }

    /**
     * Performs common setup tasks during the FML common setup phase.
     *
     * <p>Initializes core systems such as the registry, memory pool, circuit breaker,
     * and async processors in dependency order.</p>
     *
     * @param event The common setup event.
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                // Initialize all systems in dependency order
                LayerRegistry.initialize();
                TieredMemoryPool.getInstance().initialize();
                CircuitBreakerAdvanced.initialize();
                Metrics.initialize();
                ProfilingMetrics.initialize();
                AsyncProcessor.initialize();
                LoadBalancer.initialize();

                // Optional systems
                if (LayeredTerrainConfig.ENABLE_GPU_ACCELERATION.get()) {
                    GPUAccelerator.initialize();
                }

                if (LayeredTerrainConfig.ENABLE_ML_PREDICTION.get()) {
                    MLPredictor.initialize();
                }

                if (LayeredTerrainConfig.ENABLE_PROMETHEUS.get()) {
                    PrometheusExporter.initialize();
                }

                long initTime = System.currentTimeMillis() - initStartTime;

                if (!LayeredTerrainConfig.QUIET_STARTUP.get()) {
                    LOGGER.info("╔════════════════════════════════════════════════════╗");
                    LOGGER.info("║  INITIALIZATION COMPLETE                           ║");
                    LOGGER.info("╠════════════════════════════════════════════════════╣");
                    LOGGER.info("║  ✅ Layer Registry                                 ║");
                    LOGGER.info("║  ✅ Tiered Memory Pool                             ║");
                    LOGGER.info("║  ✅ Advanced Circuit Breaker                       ║");
                    LOGGER.info("║  ✅ Metrics & Profiling                            ║");
                    LOGGER.info("║  ✅ Async Processor                                ║");
                    LOGGER.info("║  ✅ Load Balancer                                  ║");

                    if (LayeredTerrainConfig.ENABLE_GPU_ACCELERATION.get()) {
                        LOGGER.info("║  ✅ GPU Acceleration                               ║");
                    }
                    if (LayeredTerrainConfig.ENABLE_ML_PREDICTION.get()) {
                        LOGGER.info("║  ✅ ML Prediction Engine                           ║");
                    }
                    if (LayeredTerrainConfig.ENABLE_PROMETHEUS.get()) {
                        LOGGER.info("║  ✅ Prometheus Metrics                             ║");
                    }

                    LOGGER.info("╠════════════════════════════════════════════════════╣");
                    LOGGER.info("║  Initialization Time: {}ms                      ║", initTime);
                    LOGGER.info("╚════════════════════════════════════════════════════╝");
                } else {
                    LOGGER.info("Layered Terrain System initialized in {}ms.", initTime);
                }

            } catch (Exception e) {
                LOGGER.error("❌ CRITICAL: Initialization failed!", e);
                throw new RuntimeException("Failed to initialize Layered Terrain System", e);
            }
        });
    }

    /**
     * Handles the server started event.
     *
     * <p>Logs configuration details and performs startup diagnostics.</p>
     *
     * @param event The server started event.
     */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        if (!LayeredTerrainConfig.QUIET_STARTUP.get()) {
            LOGGER.info("╔════════════════════════════════════════════════════╗");
            LOGGER.info("║  LAYERED TERRAIN SYSTEM v{} STARTED            ║", Reference.VERSION);
            LOGGER.info("╠════════════════════════════════════════════════════╣");
            LOGGER.info("║  Edition: ULTIMATE HYBRID                          ║");
            LOGGER.info("╠════════════════════════════════════════════════════╣");
            LOGGER.info("║  CONFIGURATION:                                    ║");
            LOGGER.info("║  • Enabled: {}                                   ║",
                formatBoolean(LayeredTerrainConfig.ENABLED.get()));
            LOGGER.info("║  • Async Processing: {}                          ║",
                formatBoolean(LayeredTerrainConfig.ASYNC_PROCESSING.get()));
            LOGGER.info("║  • Smoothing Algorithm: {:>20}         ║",
                LayeredTerrainConfig.SMOOTHING_TYPE.get());
            LOGGER.info("║  • Smoothing Passes: {:>2}                          ║",
                LayeredTerrainConfig.SMOOTHING_PASSES.get());
            LOGGER.info("║  • Worker Threads: {:>2}                            ║",
                getActualThreadCount());
            LOGGER.info("╠════════════════════════════════════════════════════╣");
            LOGGER.info("║  FEATURES:                                         ║");
            LOGGER.info("║  • Memory Pooling: {}                            ║",
                formatBoolean(LayeredTerrainConfig.ENABLE_MEMORY_POOLING.get()));
            LOGGER.info("║  • Hot-Reload: {}                                ║",
                formatBoolean(LayeredTerrainConfig.ENABLE_HOT_RELOAD.get()));
            LOGGER.info("║  • Circuit Breaker: {}                           ║",
                formatBoolean(LayeredTerrainConfig.ENABLE_CIRCUIT_BREAKER.get()));
            LOGGER.info("║  • Load Balancing: {}                            ║",
                formatBoolean(LayeredTerrainConfig.ENABLE_LOAD_BALANCING.get()));
            LOGGER.info("║  • Adaptive Biome Blending: {}                   ║",
                formatBoolean(LayeredTerrainConfig.ADAPTIVE_BIOME_BLENDING.get()));
            LOGGER.info("║  • GPU Acceleration: {}                          ║",
                formatBoolean(LayeredTerrainConfig.ENABLE_GPU_ACCELERATION.get()));
            LOGGER.info("║  • ML Prediction: {}                             ║",
                formatBoolean(LayeredTerrainConfig.ENABLE_ML_PREDICTION.get()));
            LOGGER.info("║  • Prometheus Metrics: {}                        ║",
                formatBoolean(LayeredTerrainConfig.ENABLE_PROMETHEUS.get()));
            LOGGER.info("╠════════════════════════════════════════════════════╣");
            LOGGER.info("║  OPTIMIZATION LEVEL: {}                         ║",
                getOptimizationLevel());
            LOGGER.info("╚════════════════════════════════════════════════════╝");
        } else {
            LOGGER.info("Layered Terrain System v{} started.", Reference.VERSION);
        }

        // Perform self-diagnostics
        SystemDiagnostics.runStartupCheck();
    }

    /**
     * Handles the server stopping event.
     *
     * <p>Performs graceful shutdown of all systems, releasing resources and printing
     * final metrics reports.</p>
     *
     * @param event The server stopping event.
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (!LayeredTerrainConfig.QUIET_STARTUP.get()) {
            LOGGER.info("╔════════════════════════════════════════════════════╗");
            LOGGER.info("║  Shutting down Layered Terrain System v{}...   ║", Reference.VERSION);
            LOGGER.info("╚════════════════════════════════════════════════════╝");
        } else {
            LOGGER.info("Shutting down Layered Terrain System v{}...", Reference.VERSION);
        }

        try {
            // Graceful shutdown in reverse dependency order
            AsyncProcessor.shutdown();
            LoadBalancer.shutdown();
            TieredMemoryPool.getInstance().shutdown();

            if (LayeredTerrainConfig.ENABLE_GPU_ACCELERATION.get()) {
                GPUAccelerator.shutdown();
            }

            if (LayeredTerrainConfig.ENABLE_ML_PREDICTION.get()) {
                MLPredictor.shutdown();
            }

            if (LayeredTerrainConfig.ENABLE_PROMETHEUS.get()) {
                PrometheusExporter.shutdown();
            }

            // Print final reports
            Metrics.printFinalReport();
            ProfilingMetrics.printDetailedReport();
            CircuitBreakerAdvanced.printStats();
            TieredMemoryPool.getInstance().printStats();

            LOGGER.info("✅ Layered Terrain System shutdown complete");

        } catch (Exception e) {
            LOGGER.error("❌ Error during shutdown", e);
        }
    }

    private static String formatBoolean(boolean value) {
        return value ? "✅ YES" : "❌ NO ";
    }

    private static int getActualThreadCount() {
        int configured = LayeredTerrainConfig.WORKER_THREADS.get();
        return configured == 0
            ? Math.max(2, Runtime.getRuntime().availableProcessors() / 2)
            : configured;
    }

    private static String getOptimizationLevel() {
        int score = 0;

        if (LayeredTerrainConfig.ENABLE_MEMORY_POOLING.get()) score += 2;
        if (LayeredTerrainConfig.OPTIMIZE_LIGHTING_UPDATES.get()) score += 2;
        if (LayeredTerrainConfig.ADAPTIVE_BIOME_BLENDING.get()) score += 1;
        if (LayeredTerrainConfig.ENABLE_LOAD_BALANCING.get()) score += 2;
        if (LayeredTerrainConfig.ENABLE_GPU_ACCELERATION.get()) score += 3;
        if (LayeredTerrainConfig.ADAPTIVE_QUALITY.get()) score += 1;

        if (score >= 8) return "🔥 MAXIMUM";
        if (score >= 6) return "⚡ HIGH   ";
        if (score >= 4) return "✅ MEDIUM ";
        return "⚠️ LOW    ";
    }
}