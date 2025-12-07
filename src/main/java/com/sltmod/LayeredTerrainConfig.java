package com.sltmod;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.atomic.AtomicBoolean;

public class LayeredTerrainConfig {

    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // ==================== GENERAL ====================
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue USE_5X5_SAMPLING;
    public static final ForgeConfigSpec.BooleanValue USE_7X7_SAMPLING;
    public static final ForgeConfigSpec.BooleanValue DEBUG_MODE;

    // ==================== SMOOTHING ====================
    public static final ForgeConfigSpec.EnumValue<SmoothingType> SMOOTHING_TYPE;
    public static final ForgeConfigSpec.IntValue SMOOTHING_PASSES;
    public static final ForgeConfigSpec.IntValue MAX_DIFFERENTIAL;
    public static final ForgeConfigSpec.DoubleValue SMOOTHING_STRENGTH;

    // ==================== SLOPE ====================
    public static final ForgeConfigSpec.DoubleValue DAMPING_FACTOR;
    public static final ForgeConfigSpec.DoubleValue SCALE_FACTOR;
    public static final ForgeConfigSpec.DoubleValue SCALE_FACTOR_BEACH;
    public static final ForgeConfigSpec.DoubleValue SCALE_FACTOR_PLAINS;
    public static final ForgeConfigSpec.DoubleValue SCALE_FACTOR_FOREST;
    public static final ForgeConfigSpec.DoubleValue SCALE_FACTOR_HILLS;
    public static final ForgeConfigSpec.DoubleValue SCALE_FACTOR_MOUNTAINS;
    public static final ForgeConfigSpec.DoubleValue SCALE_FACTOR_OCEAN;
    public static final ForgeConfigSpec.DoubleValue SCALE_FACTOR_RIVER;

    // ==================== BIOME BLENDING ====================
    public static final ForgeConfigSpec.IntValue BIOME_BLEND_RADIUS;
    public static final ForgeConfigSpec.BooleanValue ADAPTIVE_BIOME_BLENDING;
    public static final ForgeConfigSpec.DoubleValue BIOME_TRANSITION_THRESHOLD;
    public static final ForgeConfigSpec.BooleanValue DETECT_BIOME_BOUNDARIES;

    // ==================== PERFORMANCE ====================
    public static final ForgeConfigSpec.BooleanValue ASYNC_PROCESSING;
    public static final ForgeConfigSpec.IntValue WORKER_THREADS;
    public static final ForgeConfigSpec.IntValue MAX_QUEUE_SIZE;
    public static final ForgeConfigSpec.IntValue MAX_CHUNKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue CALCULATION_TIMEOUT_MS;
    public static final ForgeConfigSpec.BooleanValue ADAPTIVE_QUALITY;
    public static final ForgeConfigSpec.BooleanValue USE_CACHE_FRIENDLY_LAYOUT;

    // ==================== MEMORY (Enhanced) ====================
    public static final ForgeConfigSpec.BooleanValue ENABLE_MEMORY_POOLING;
    public static final ForgeConfigSpec.IntValue MEMORY_POOL_SIZE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_TIERED_POOLING;
    public static final ForgeConfigSpec.IntValue POOL_HOT_TIER_SIZE;
    public static final ForgeConfigSpec.IntValue POOL_WARM_TIER_SIZE;
    public static final ForgeConfigSpec.IntValue POOL_COLD_TIER_SIZE;
    public static final ForgeConfigSpec.IntValue POOL_CLEANUP_INTERVAL_SECONDS;

    // ==================== LIGHTING (Enhanced) ====================
    public static final ForgeConfigSpec.BooleanValue OPTIMIZE_LIGHTING_UPDATES;
    public static final ForgeConfigSpec.BooleanValue UPDATE_ALL_HEIGHTMAPS;
    public static final ForgeConfigSpec.BooleanValue BATCH_LIGHTING_UPDATES;
    public static final ForgeConfigSpec.IntValue LIGHTING_BATCH_SIZE;
    public static final ForgeConfigSpec.BooleanValue DEFER_NEIGHBOR_LIGHTING;

    // ==================== FILTERS (Enhanced) ====================
    public static final ForgeConfigSpec.BooleanValue SKIP_STRUCTURES;
    public static final ForgeConfigSpec.BooleanValue SKIP_WATER_ADJACENT;
    public static final ForgeConfigSpec.BooleanValue SKIP_CAVE_OPENINGS;
    public static final ForgeConfigSpec.BooleanValue SKIP_OVERHANGS;
    public static final ForgeConfigSpec.IntValue OVERHANG_THRESHOLD;
    public static final ForgeConfigSpec.BooleanValue SKIP_SNOW_LAYERS;
    public static final ForgeConfigSpec.BooleanValue SKIP_STEEP_SLOPES;
    public static final ForgeConfigSpec.IntValue STEEP_SLOPE_THRESHOLD;
    public static final ForgeConfigSpec.BooleanValue PRESERVE_PATHS;
    public static final ForgeConfigSpec.BooleanValue PRESERVE_FARMLAND;

    // ==================== CIRCUIT BREAKER (Advanced) ====================
    public static final ForgeConfigSpec.BooleanValue ENABLE_CIRCUIT_BREAKER;
    public static final ForgeConfigSpec.IntValue CIRCUIT_BREAKER_THRESHOLD;
    public static final ForgeConfigSpec.IntValue CIRCUIT_BREAKER_RESET_TIME_MS;
    public static final ForgeConfigSpec.IntValue CIRCUIT_BREAKER_HALF_OPEN_ATTEMPTS;
    public static final ForgeConfigSpec.DoubleValue CIRCUIT_BREAKER_ERROR_RATE_THRESHOLD;

    // ==================== LOAD BALANCING (New) ====================
    public static final ForgeConfigSpec.BooleanValue ENABLE_LOAD_BALANCING;
    public static final ForgeConfigSpec.EnumValue<LoadBalancingStrategy> LOAD_BALANCING_STRATEGY;
    public static final ForgeConfigSpec.IntValue MAX_CONCURRENT_CALCULATIONS;
    public static final ForgeConfigSpec.BooleanValue PRIORITIZE_PLAYER_CHUNKS;
    public static final ForgeConfigSpec.IntValue PLAYER_CHUNK_PRIORITY_RADIUS;

    // ==================== MONITORING (Enhanced) ====================
    public static final ForgeConfigSpec.BooleanValue ENABLE_METRICS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_PROFILING;
    public static final ForgeConfigSpec.BooleanValue ENABLE_PERCENTILE_TRACKING;
    public static final ForgeConfigSpec.IntValue METRICS_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.BooleanValue LOG_SLOW_CHUNKS;
    public static final ForgeConfigSpec.IntValue SLOW_CHUNK_THRESHOLD_MS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_HEALTH_CHECKS;
    public static final ForgeConfigSpec.IntValue HEALTH_CHECK_INTERVAL_SECONDS;

    // ==================== HOT-RELOAD (Enhanced) ====================
    public static final ForgeConfigSpec.BooleanValue ENABLE_HOT_RELOAD;
    public static final ForgeConfigSpec.IntValue HOT_RELOAD_CHECK_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.BooleanValue HOT_RELOAD_RESET_METRICS;

    // ==================== ADVANCED FEATURES (New) ====================
    public static final ForgeConfigSpec.BooleanValue ENABLE_GPU_ACCELERATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_ML_PREDICTION;
    public static final ForgeConfigSpec.DoubleValue ML_PREDICTION_CONFIDENCE_THRESHOLD;
    public static final ForgeConfigSpec.BooleanValue ENABLE_MULTI_THREADED_SMOOTHING;
    public static final ForgeConfigSpec.IntValue SMOOTHING_THREAD_POOL_SIZE;

    // ==================== PROMETHEUS (New) ====================
    public static final ForgeConfigSpec.BooleanValue ENABLE_PROMETHEUS;
    public static final ForgeConfigSpec.IntValue PROMETHEUS_PORT;
    public static final ForgeConfigSpec.ConfigValue<String> PROMETHEUS_ENDPOINT;

    // ==================== COMPATIBILITY ====================
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> BLACKLISTED_DIMENSIONS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> BLACKLISTED_BIOMES;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> BLACKLISTED_MODS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> WHITELISTED_BLOCKS;

    // Hot-reload tracking
    private static long lastReloadCheck = 0;
    private static FileTime lastConfigModified = null;
    private static WatchService watchService = null;
    private static WatchKey watchKey = null;
    private static final AtomicBoolean reloadInProgress = new AtomicBoolean(false);

    public enum SmoothingType {
        GAUSSIAN("Standard Gaussian smoothing - fast and reliable"),
        BILATERAL("Edge-preserving bilateral filter - 20% slower"),
        ANISOTROPIC("Ridge-preserving anisotropic diffusion - 30% slower"),
        MEDIAN("Median filter for noise reduction - experimental"),
        ADAPTIVE("Adaptive smoothing based on terrain variance - recommended"),
        MULTI_SCALE("Multi-scale smoothing for best quality - 50% slower");

        private final String description;

        SmoothingType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum LoadBalancingStrategy {
        ROUND_ROBIN("Distribute work evenly across threads"),
        LEAST_LOADED("Assign to thread with least current work"),
        WORK_STEALING("Allow threads to steal work from busy threads"),
        PRIORITY_BASED("Prioritize chunks near players"),
        ADAPTIVE("Dynamically choose best strategy");

        private final String description;

        LoadBalancingStrategy(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    static {
        BUILDER.comment(
            "════════════════════════════════════════════════════════════════",
            "  Layered Terrain System v3.2 - Ultimate Hybrid Configuration  ",
            "════════════════════════════════════════════════════════════════",
            "",
            "This configuration combines the best features from multiple",
            "versions and adds advanced optimizations for maximum performance",
            "and quality.",
            "",
            "For detailed documentation, visit:",
            "https://github.com/deft-orchestrator/layered-terrain/wiki",
            "",
            "════════════════════════════════════════════════════════════════"
        ).push("layered_terrain");

        // ============================================================
        // GENERAL
        // ============================================================
        BUILDER.comment(
            "────────────────────────────────────────────────────────────────",
            "  GENERAL SETTINGS",
            "────────────────────────────────────────────────────────────────"
        ).push("general");

        ENABLED = BUILDER
            .comment("Enable the layered terrain system globally")
            .define("enabled", true);

        USE_5X5_SAMPLING = BUILDER
            .comment(
                "Use 5×5 grid sampling instead of 3×3",
                "Better quality but ~2.5× slower",
                "Recommended for high-quality screenshots"
            )
            .define("use_5x5_sampling", false);

        USE_7X7_SAMPLING = BUILDER
            .comment(
                "Use 7×7 grid sampling (experimental)",
                "Best quality but ~5× slower",
                "Only for extreme quality requirements"
            )
            .define("use_7x7_sampling", false);

        DEBUG_MODE = BUILDER
            .comment(
                "Enable debug mode",
                "Enables verbose logging and diagnostic output",
                "WARNING: Significant performance impact"
            )
            .define("debug_mode", false);

        BUILDER.pop();

        // ============================================================
        // SMOOTHING
        // ============================================================
        BUILDER.comment(
            "────────────────────────────────────────────────────────────────",
            "  SMOOTHING SETTINGS",
            "────────────────────────────────────────────────────────────────"
        ).push("smoothing");

        SMOOTHING_TYPE = BUILDER
            .comment(
                "Smoothing algorithm type:",
                "  • GAUSSIAN: Fast, standard smoothing",
                "  • BILATERAL: Edge-preserving, 20% slower",
                "  • ANISOTROPIC: Ridge-preserving, 30% slower",
                "  • MEDIAN: Noise reduction, experimental",
                "  • ADAPTIVE: Auto-adjusts based on terrain (recommended)",
                "  • MULTI_SCALE: Best quality, 50% slower"
            )
            .defineEnum("smoothing_type", SmoothingType.ADAPTIVE);

        SMOOTHING_PASSES = BUILDER
            .comment(
                "Number of smoothing passes (1-5)",
                "  1 = Fast but rough",
                "  2 = Recommended balance (default)",
                "  3 = Smoother transitions",
                "  4+ = Very smooth but slower"
            )
            .defineInRange("smoothing_passes", 2, 1, 5);

        MAX_DIFFERENTIAL = BUILDER
            .comment(
                "Maximum thickness difference between adjacent blocks (1-4)",
                "  1 = Very smooth, gradual transitions",
                "  2 = Recommended (default)",
                "  3 = More variation allowed",
                "  4 = Maximum variation"
            )
            .defineInRange("max_differential", 2, 1, 4);

        SMOOTHING_STRENGTH = BUILDER
            .comment(
                "Smoothing strength multiplier (0.5-2.0)",
                "Higher values = more aggressive smoothing",
                "1.0 = default strength"
            )
            .defineInRange("smoothing_strength", 1.0, 0.5, 2.0);

        BUILDER.pop();

        // ============================================================
        // SLOPE CALCULATION
        // ============================================================
        BUILDER.comment(
            "────────────────────────────────────────────────────────────────",
            "  SLOPE CALCULATION SETTINGS",
            "────────────────────────────────────────────────────────────────"
        ).push("slope");

        DAMPING_FACTOR = BUILDER
            .comment(
                "Exponential damping factor (3.0-10.0)",
                "Controls compression of steep slopes",
                "  Lower (3-5): Aggressive compression, very smooth",
                "  Default (6): Balanced, natural appearance",
                "  Higher (7-10): Less compression, preserves steepness"
            )
            .defineInRange("damping_factor", 6.0, 3.0, 10.0);

        SCALE_FACTOR = BUILDER
            .comment("Default scale factor for thickness conversion (0.5-3.0)")
            .defineInRange("scale_factor", 1.2, 0.5, 3.0);

        SCALE_FACTOR_BEACH = BUILDER
            .comment("Beach/coastal areas - smoother sand dunes")
            .defineInRange("scale_factor_beach", 0.8, 0.5, 3.0);

        SCALE_FACTOR_PLAINS = BUILDER
            .comment("Plains - balanced smoothing")
            .defineInRange("scale_factor_plains", 1.0, 0.5, 3.0);

        SCALE_FACTOR_FOREST = BUILDER
            .comment("Forests - gentle rolling terrain")
            .defineInRange("scale_factor_forest", 1.1, 0.5, 3.0);

        SCALE_FACTOR_HILLS = BUILDER
            .comment("Hills - defined slopes")
            .defineInRange("scale_factor_hills", 1.3, 0.5, 3.0);

        SCALE_FACTOR_MOUNTAINS = BUILDER
            .comment("Mountains - sharp definition")
            .defineInRange("scale_factor_mountains", 1.5, 0.5, 3.0);

        SCALE_FACTOR_OCEAN = BUILDER
            .comment("Ocean floor - very smooth")
            .defineInRange("scale_factor_ocean", 0.7, 0.5, 3.0);

        SCALE_FACTOR_RIVER = BUILDER
            .comment("River beds - smooth gradient")
            .defineInRange("scale_factor_river", 0.9, 0.5, 3.0);

        BUILDER.pop();

        // ============================================================
        // BIOME BLENDING
        // ============================================================
        BUILDER.comment(
            "────────────────────────────────────────────────────────────────",
            "  BIOME BLENDING SETTINGS",
            "────────────────────────────────────────────────────────────────"
        ).push("biome_blending");

        BIOME_BLEND_RADIUS = BUILDER
            .comment(
                "Blending radius for biome transitions (1-4)",
                "  1 = 3×3 grid (fast)",
                "  2 = 5×5 grid (smooth, recommended)",
                "  3 = 7×7 grid (very smooth)",
                "  4 = 9×9 grid (ultra smooth, slower)"
            )
            .defineInRange("biome_blend_radius", 2, 1, 4);

        ADAPTIVE_BIOME_BLENDING = BUILDER
            .comment(
                "Automatically increase blend radius near extreme transitions",
                "Detects large biome differences and adjusts accordingly"
            )
            .define("adaptive_biome_blending", true);

        BIOME_TRANSITION_THRESHOLD = BUILDER
            .comment(
                "Threshold for detecting extreme biome transitions (0.3-1.0)",
                "Scale factor difference that triggers adaptive blending",
                "Lower = more sensitive to biome changes"
            )
            .defineInRange("biome_transition_threshold", 0.5, 0.3, 1.0);

        DETECT_BIOME_BOUNDARIES = BUILDER
            .comment(
                "Enable advanced biome boundary detection",
                "Uses gradient analysis to find biome edges",
                "Slight performance cost but better transitions"
            )
            .define("detect_biome_boundaries", true);

        BUILDER.pop();

        // ============================================================
        // PERFORMANCE
        // ============================================================
        BUILDER.comment(
            "────────────────────────────────────────────────────────────────",
            "  PERFORMANCE SETTINGS",
            "────────────────────────────────────────────────────────────────"
        ).push("performance");

        ASYNC_PROCESSING = BUILDER
            .comment("Enable asynchronous chunk processing (highly recommended)")
            .define("async_processing", true);

        WORKER_THREADS = BUILDER
            .comment(
                "Number of worker threads for async processing",
                "0 = Auto-detect (recommended: CPU cores / 2)",
                "Manual: 2-8 threads depending on CPU"
            )
            .defineInRange("worker_threads", 0, 0, 16);

        MAX_QUEUE_SIZE = BUILDER
            .comment(
                "Maximum chunks in processing queue",
                "Higher = more memory, lower = potential chunk skipping",
                "Recommended: 100-200 for most servers"
            )
            .defineInRange("max_queue_size", 150, 50, 500);

        MAX_CHUNKS_PER_TICK = BUILDER
            .comment(
                "Maximum chunks to process per server tick",
                "Balance between throughput and TPS impact",
                "Recommended: 4-6 for most servers"
            )
            .defineInRange("max_chunks_per_tick", 4, 1, 16);

        CALCULATION_TIMEOUT_MS = BUILDER
            .comment(
                "Timeout for async calculations (milliseconds)",
                "Prevents hung threads from blocking system"
            )
            .defineInRange("calculation_timeout_ms", 5000, 1000, 30000);

        ADAPTIVE_QUALITY = BUILDER
            .comment(
                "Automatically reduce quality when server TPS drops",
                "Helps maintain server stability during heavy load"
            )
            .define("adaptive_quality", true);

        USE_CACHE_FRIENDLY_LAYOUT = BUILDER
            .comment(
                "Use flat arrays for better CPU cache performance",
                "Minor performance improvement (~5-10%)"
            )
            .define("use_cache_friendly_layout", true);

        BUILDER.pop();

        // ============================================================
        // MEMORY (Enhanced Tiered Pooling)
        // ============================================================
        BUILDER.comment(
            "────────────────────────────────────────────────────────────────",
            "  MEMORY OPTIMIZATION SETTINGS",
            "────────────────────────────────────────────────────────────────",
            "Tiered memory pooling reduces GC pressure by 60-80%"
        ).push("memory");

        ENABLE_MEMORY_POOLING = BUILDER
            .comment("Enable object pooling to reduce GC pressure")
            .define("enable_memory_pooling", true);

        MEMORY_POOL_SIZE = BUILDER
            .comment(
                "Total size of memory pool (simple mode)",
                "Only used if tiered pooling is disabled"
            )
            .defineInRange("memory_pool_size", 100, 20, 500);

        ENABLE_TIERED_POOLING = BUILDER
            .comment(
                "Enable tiered memory pooling (recommended)",
                "Separates pool into hot/warm/cold tiers",
                "Hot tier: Recently used, fast access",
                "Warm tier: Occasionally used",
                "Cold tier: Rarely used, eligible for cleanup"
            )
            .define("enable_tiered_pooling", true);

        POOL_HOT_TIER_SIZE = BUILDER
            .comment("Size of hot tier (frequently accessed)")
            .defineInRange("pool_hot_tier_size", 50, 10, 200);

        POOL_WARM_TIER_SIZE = BUILDER
            .comment("Size of warm tier (moderately accessed)")
            .defineInRange("pool_warm_tier_size", 30, 10, 150);

        POOL_COLD_TIER_SIZE = BUILDER
            .comment("Size of cold tier (rarely accessed)")
            .defineInRange("pool_cold_tier_size", 20, 5, 100);

        POOL_CLEANUP_INTERVAL_SECONDS = BUILDER
            .comment("How often to clean up cold tier (seconds)")
            .defineInRange("pool_cleanup_interval_seconds", 300, 60, 1800);

        BUILDER.pop();

        // ============================================================
        // LIGHTING (Enhanced)
        // ============================================================
        BUILDER.comment(
            "────────────────────────────────────────────────────────────────",
            "  LIGHTING OPTIMIZATION SETTINGS",
            "────────────────────────────────────────────────────────────────"
        ).push("lighting");

        OPTIMIZE_LIGHTING_UPDATES = BUILDER
            .comment(
                "Only update relevant heightmap types",
                "Performance gain: ~30% faster lighting updates"
            )
            .define("optimize_lighting_updates", true);

        UPDATE_ALL_HEIGHTMAPS = BUILDER
            .comment(
                "Update all heightmap types (slower but safer)",
                "Enable if experiencing lighting bugs"
            )
            .define("update_all_heightmaps", false);

        BATCH_LIGHTING_UPDATES = BUILDER
            .comment(
                "Batch multiple lighting updates together",
                "Reduces overhead for chunk clusters"
            )
            .define("batch_lighting_updates", true);

        LIGHTING_BATCH_SIZE = BUILDER
            .comment("Number of chunks to batch together")
            .defineInRange("lighting_batch_size", 4, 1, 16);

        DEFER_NEIGHBOR_LIGHTING = BUILDER
            .comment(
                "Defer neighbor chunk lighting updates",
                "Improves throughput but may cause brief lighting glitches"
            )
            .define("defer_neighbor_lighting", false);

        BUILDER.pop();

        // ============================================================
        // FILTERS (Enhanced)
        // ============================================================
        BUILDER.comment(
            "────────────────────────────────────────────────────────────────",
            "  EDGE CASE FILTERS",
            "────────────────────────────────────────────────────────────────",
            "Control which blocks/areas should NOT receive layers"
        ).push("filters");

        SKIP_STRUCTURES = BUILDER
            .comment("Skip placing layers inside structure bounding boxes")
            .define("skip_structures", true);

        SKIP_WATER_ADJACENT = BUILDER
            .comment("Skip placing layers adjacent to water")
            .define("skip_water_adjacent", true);

        SKIP_CAVE_OPENINGS = BUILDER
            .comment("Skip placing layers on cave openings")
            .define("skip_cave_openings", true);

        SKIP_OVERHANGS = BUILDER
            .comment("Skip placing layers on cliff edges and overhangs")
            .define("skip_overhangs", true);

        OVERHANG_THRESHOLD = BUILDER
            .comment(
                "Minimum height difference to be considered an overhang",
                "Higher = only skip extreme cliffs"
            )
            .defineInRange("overhang_threshold", 3, 2, 10);

        SKIP_SNOW_LAYERS = BUILDER
            .comment("Skip placing layers under existing snow layers")
            .define("skip_snow_layers", true);

        SKIP_STEEP_SLOPES = BUILDER
            .comment(
                "Skip placing layers on very steep slopes",
                "Prevents unrealistic layering on near-vertical surfaces"
            )
            .define("skip_steep_slopes", true);

        STEEP_SLOPE_THRESHOLD = BUILDER
            .comment("Slope angle threshold (blocks per horizontal block)")
            .defineInRange("steep_slope_threshold", 8, 4, 16);

        PRESERVE_PATHS = BUILDER
            .comment(
                "Preserve player-made paths and dirt paths",
                "Prevents layers from appearing on intentional paths"
            )
            .define("preserve_paths", true);

        PRESERVE_FARMLAND = BUILDER
            .comment("Preserve farmland blocks")
            .define("preserve_farmland", true);

        BUILDER.pop();

        // ============================================================
        // CIRCUIT BREAKER (Advanced with Half-Open State)
        // ============================================================
        BUILDER.comment(
            "────────────────────────────────────────────────────────────────",
            "  CIRCUIT BREAKER PROTECTION",
            "────────────────────────────────────────────────────────────────",
            "Prevents cascading failures during high error rates"
        ).push("circuit_breaker");

        ENABLE_CIRCUIT_BREAKER = BUILDER
            .comment("Enable circuit breaker pattern for fault tolerance")
            .define("enable_circuit_breaker", true);

        CIRCUIT_BREAKER_THRESHOLD = BUILDER
            .comment(
                "Number of consecutive failures before opening circuit",
                "Higher = more tolerant of errors"
            )
            .defineInRange("circuit_breaker_threshold", 10, 5, 50);

        CIRCUIT_BREAKER_RESET_TIME_MS = BUILDER
            .comment(
                "Time to wait before attempting to close circuit (milliseconds)",
                "Circuit enters half-open state after this time"
            )
            .defineInRange("circuit_breaker_reset_time_ms", 30000, 10000, 300000);

        CIRCUIT_BREAKER_HALF_OPEN_ATTEMPTS = BUILDER
            .comment(
                "Number of successful attempts needed in half-open state",
                "Before fully closing the circuit"
            )
            .defineInRange("circuit_breaker_half_open_attempts", 3, 1, 10);

        CIRCUIT_BREAKER_ERROR_RATE_THRESHOLD = BUILDER
            .comment(
                "Error rate threshold for opening circuit (0.0-1.0)",
                "0.2 = open if 20% of requests fail",
                "Only applies if circuit supports error rate tracking"
            )
            .defineInRange("circuit_breaker_error_rate_threshold", 0.2, 0.05, 0.5);

        BUILDER.pop();

        // ============================================================
        // LOAD BALANCING (New)
        // ============================================================
        BUILDER.comment(
            "────────────────────────────────────────────────────────────────",
            "  LOAD BALANCING SETTINGS",
            "────────────────────────────────────────────────────────────────",
            "Intelligent work distribution across worker threads"
        ).push("load_balancing");

        ENABLE_LOAD_BALANCING = BUILDER
            .comment("Enable intelligent load balancing")
            .define("enable_load_balancing", true);

        LOAD_BALANCING_STRATEGY = BUILDER
            .comment(
                "Load balancing strategy:",
                "  • ROUND_ROBIN: Simple round-robin distribution",
                "  • LEAST_LOADED: Assign to least busy thread",
                "  • WORK_STEALING: Threads steal work from busy threads",
                "  • PRIORITY_BASED: Prioritize chunks near players",
                "  • ADAPTIVE: Dynamically choose best strategy (recommended)"
            )
            .defineEnum("load_balancing_strategy", LoadBalancingStrategy.ADAPTIVE);

        MAX_CONCURRENT_CALCULATIONS = BUILDER
            .comment(
                "Maximum concurrent calculations allowed",
                "Prevents thread pool saturation",
                "0 = unlimited (uses worker_threads * 2)"
            )
            .defineInRange("max_concurrent_calculations", 0, 0, 100);

        PRIORITIZE_PLAYER_CHUNKS = BUILDER
            .comment(
                "Give priority to chunks near players",
                "Improves perceived performance"
            )
            .define("prioritize_player_chunks", true);

        PLAYER_CHUNK_PRIORITY_RADIUS = BUILDER
            .comment("Radius around players for priority processing (chunks)")
            .defineInRange("player_chunk_priority_radius", 8, 4, 16);

        BUILDER.pop();

        // ============================================================
        // MONITORING (Enhanced)
        // ============================================================
        BUILDER.comment(
            "────────────────────────────────────────────────────────────────",
            "  MONITORING & DIAGNOSTICS",
            "────────────────────────────────────────────────────────────────"
        ).push("monitoring");

        ENABLE_METRICS = BUILDER
            .comment("Enable performance metrics collection")
            .define("enable_metrics", true);

        ENABLE_PROFILING = BUILDER
            .comment(
                "Enable detailed performance profiling",
                "WARNING: Adds ~5-10% overhead",
                "Only enable for debugging/optimization"
            )
            .define("enable_profiling", false);

        ENABLE_PERCENTILE_TRACKING = BUILDER
            .comment(
                "Track P50, P95, P99 performance percentiles",
                "Slight memory overhead but valuable for diagnostics"
            )
            .define("enable_percentile_tracking", true);

        METRICS_INTERVAL_SECONDS = BUILDER
            .comment(
                "How often to print metrics to log (seconds)",
                "0 = only on server stop"
            )
            .defineInRange("metrics_interval_seconds", 300, 0, 3600);

        LOG_SLOW_CHUNKS = BUILDER
            .comment("Log warning for chunks exceeding threshold")
            .define("log_slow_chunks", true);

        SLOW_CHUNK_THRESHOLD_MS = BUILDER
            .comment("Threshold for slow chunk warning (milliseconds)")
            .defineInRange("slow_chunk_threshold_ms", 10, 5, 100);

        ENABLE_HEALTH_CHECKS = BUILDER
            .comment(
                "Enable periodic system health checks",
                "Monitors queue size, memory usage, thread health"
            )
            .define("enable_health_checks", true);

        HEALTH_CHECK_INTERVAL_SECONDS = BUILDER
            .comment("How often to run health checks (seconds)")
            .defineInRange("health_check_interval_seconds", 60, 30, 300);

        BUILDER.pop();

        // ============================================================
        // HOT-RELOAD (Enhanced with File Watching)
        // ============================================================
        BUILDER.comment(
            "────────────────────────────────────────────────────────────────",
            "  CONFIGURATION HOT-RELOAD",
            "────────────────────────────────────────────────────────────────"
        ).push("hot_reload");

        ENABLE_HOT_RELOAD = BUILDER
            .comment(
                "Enable automatic config reload when file changes",
                "No server restart needed for most settings"
            )
            .define("enable_hot_reload", true);

        HOT_RELOAD_CHECK_INTERVAL_SECONDS = BUILDER
            .comment(
                "How often to check for config changes (seconds)",
                "Lower = faster detection but more overhead"
            )
            .defineInRange("hot_reload_check_interval_seconds", 60, 10, 300);

        HOT_RELOAD_RESET_METRICS = BUILDER
            .comment(
                "Reset metrics after config reload",
                "Useful for comparing before/after performance"
            )
            .define("hot_reload_reset_metrics", true);

        BUILDER.pop();

        // ============================================================
        // ADVANCED FEATURES (New)
        // ============================================================
        BUILDER.comment(
            "────────────────────────────────────────────────────────────────",
            "  ADVANCED FEATURES (Experimental)",
            "────────────────────────────────────────────────────────────────",
            "These features are experimental and may not be available on all systems"
        ).push("advanced");

        ENABLE_GPU_ACCELERATION = BUILDER
            .comment(
                "Enable GPU acceleration for smoothing operations",
                "EXPERIMENTAL: Requires compatible GPU and drivers",
                "Can provide 2-5× speedup on supported systems",
                "Automatically disabled if GPU unavailable"
            )
            .define("enable_gpu_acceleration", false);

        ENABLE_ML_PREDICTION = BUILDER
            .comment(
                "Enable machine learning thickness prediction",
                "EXPERIMENTAL: Learns from processed chunks",
                "Can reduce calculation time by up to 30%",
                "Requires training period (1000+ chunks)"
            )
            .define("enable_ml_prediction", false);

        ML_PREDICTION_CONFIDENCE_THRESHOLD = BUILDER
            .comment(
                "Minimum confidence for ML predictions (0.0-1.0)",
                "Higher = only use high-confidence predictions",
                "Lower = use predictions more often (less accurate)"
            )
            .defineInRange("ml_prediction_confidence_threshold", 0.8, 0.5, 0.99);

        ENABLE_MULTI_THREADED_SMOOTHING = BUILDER
            .comment(
                "Use multiple threads for smoothing operations",
                "Can speed up multi-pass smoothing",
                "Most effective with 3+ smoothing passes"
            )
            .define("enable_multi_threaded_smoothing", true);

        SMOOTHING_THREAD_POOL_SIZE = BUILDER
            .comment(
                "Number of threads for parallel smoothing",
                "0 = auto-detect (CPU cores / 4)"
            )
            .defineInRange("smoothing_thread_pool_size", 0, 0, 8);

        BUILDER.pop();

        // ============================================================
        // PROMETHEUS METRICS (New)
        // ============================================================
        BUILDER.comment(
            "────────────────────────────────────────────────────────────────",
            "  PROMETHEUS METRICS EXPORT",
            "────────────────────────────────────────────────────────────────",
            "Export metrics for Prometheus/Grafana monitoring"
        ).push("prometheus");

        ENABLE_PROMETHEUS = BUILDER
            .comment(
                "Enable Prometheus metrics endpoint",
                "Exposes metrics at http://localhost:<port>/<endpoint>"
            )
            .define("enable_prometheus", false);

        PROMETHEUS_PORT = BUILDER
            .comment("Port for Prometheus HTTP server")
            .defineInRange("prometheus_port", 9090, 1024, 65535);

        PROMETHEUS_ENDPOINT = BUILDER
            .comment("Endpoint path for metrics")
            .define("prometheus_endpoint", "/metrics");

        BUILDER.pop();

        // ============================================================
        // COMPATIBILITY
        // ============================================================
        BUILDER.comment(
            "────────────────────────────────────────────────────────────────",
            "  MOD COMPATIBILITY SETTINGS",
            "────────────────────────────────────────────────────────────────"
        ).push("compatibility");

        BLACKLISTED_DIMENSIONS = BUILDER
            .comment(
                "Dimensions where layered terrain is disabled",
                "Format: 'modid:dimension_name'"
            )
            .defineList("blacklisted_dimensions",
                java.util.Arrays.asList("minecraft:the_nether", "minecraft:the_end"),
                obj -> obj instanceof String);

        BLACKLISTED_BIOMES = BUILDER
            .comment(
                "Biomes where layered terrain is disabled",
                "Format: 'modid:biome_name'"
            )
            .defineList("blacklisted_biomes",
                java.util.Collections.emptyList(),
                obj -> obj instanceof String);

        BLACKLISTED_MODS = BUILDER
            .comment(
                "Mods whose blocks should never get layers",
                "Format: 'modid'"
            )
            .defineList("blacklisted_mods",
                java.util.Collections.emptyList(),
                obj -> obj instanceof String);

        WHITELISTED_BLOCKS = BUILDER
            .comment(
                "Additional blocks that should receive layers",
                "Format: 'modid:block_name'",
                "Useful for modded terrain blocks"
            )
            .defineList("whitelisted_blocks",
                java.util.Collections.emptyList(),
                obj -> obj instanceof String);

        BUILDER.pop();

        BUILDER.pop(); // layered_terrain
        SPEC = BUILDER.build();

        // Initialize file watcher for hot-reload
        initializeFileWatcher();
    }

    /**
     * Initialize file watcher for real-time config changes
     */
    private static void initializeFileWatcher() {
        if (!ENABLE_HOT_RELOAD.get()) return;

        try {
            Path configDir = FMLPaths.CONFIGDIR.get();
            watchService = FileSystems.getDefault().newWatchService();
            watchKey = configDir.register(
                watchService,
                StandardWatchEventKinds.ENTRY_MODIFY
            );

            LayeredTerrainMod.LOGGER.info("File watcher initialized for hot-reload");

        } catch (IOException e) {
            LayeredTerrainMod.LOGGER.warn("Could not initialize file watcher", e);
            watchService = null;
        }
    }

    /**
     * Enhanced config reload with file watching
     */
    public static void checkConfigReload() {
        if (!ENABLE_HOT_RELOAD.get()) return;

        // Prevent concurrent reloads
        if (!reloadInProgress.compareAndSet(false, true)) {
            return;
        }

        try {
            boolean shouldReload = false;

            // Method 1: File watcher (real-time)
            if (watchService != null) {
                WatchKey key = watchService.poll();
                if (key != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.context().toString().equals("layered-terrain.toml")) {
                            shouldReload = true;
                            break;
                        }
                    }
                    key.reset();
                }
            }

            // Method 2: Periodic check (fallback)
            if (!shouldReload) {
                long now = System.currentTimeMillis();
                long interval = HOT_RELOAD_CHECK_INTERVAL_SECONDS.get() * 1000L;

                if (now - lastReloadCheck < interval) {
                    return;
                }
                lastReloadCheck = now;

                Path configPath = FMLPaths.CONFIGDIR.get().resolve("layered-terrain.toml");
                if (Files.exists(configPath)) {
                    FileTime currentModified = Files.getLastModifiedTime(configPath);

                    if (lastConfigModified == null) {
                        lastConfigModified = currentModified;
                        return;
                    }

                    if (currentModified.compareTo(lastConfigModified) > 0) {
                        shouldReload = true;
                        lastConfigModified = currentModified;
                    }
                }
            }

            // Perform reload
            if (shouldReload) {
                performConfigReload();
            }

        } catch (IOException e) {
            LayeredTerrainMod.LOGGER.debug("Error checking config file", e);
        } finally {
            reloadInProgress.set(false);
        }
    }

    /**
     * Perform the actual config reload
     */
    private static void performConfigReload() {
        LayeredTerrainMod.LOGGER.info("╔════════════════════════════════════════════════════╗");
        LayeredTerrainMod.LOGGER.info("║  CONFIG FILE CHANGED - RELOADING...                ║");
        LayeredTerrainMod.LOGGER.info("╚════════════════════════════════════════════════════╝");

        try {
            // Reload the config spec
            SPEC.afterReload();

            // Reset systems if configured
            if (HOT_RELOAD_RESET_METRICS.get()) {
                Metrics.reset();
                ProfilingMetrics.reset();
            }

            // Re-initialize affected systems
            if (ENABLE_MEMORY_POOLING.get()) {
                TieredMemoryPool.reconfigure();
            }

            if (ENABLE_LOAD_BALANCING.get()) {
                LoadBalancer.reconfigure();
            }

            // Log changes
            LayeredTerrainMod.LOGGER.info("✅ Config reloaded successfully!");
            LayeredTerrainMod.LOGGER.info("Updated settings:");
            LayeredTerrainMod.LOGGER.info("  • Smoothing Type: {}", SMOOTHING_TYPE.get());
            LayeredTerrainMod.LOGGER.info("  • Smoothing Passes: {}", SMOOTHING_PASSES.get());
            LayeredTerrainMod.LOGGER.info("  • Max Differential: {}", MAX_DIFFERENTIAL.get());
            LayeredTerrainMod.LOGGER.info("  • Biome Blend Radius: {}", BIOME_BLEND_RADIUS.get());
            LayeredTerrainMod.LOGGER.info("  • Worker Threads: {}", WORKER_THREADS.get());
            LayeredTerrainMod.LOGGER.info("  • Load Balancing: {}", LOAD_BALANCING_STRATEGY.get());

            // Trigger health check
            if (ENABLE_HEALTH_CHECKS.get()) {
                SystemDiagnostics.runHealthCheck();
            }

        } catch (Exception e) {
            LayeredTerrainMod.LOGGER.error("❌ Failed to reload config", e);
        }
    }

    /**
     * Validate configuration on load
     */
    public static void validateConfig() {
        boolean hasErrors = false;

        // Check for conflicting settings
        if (USE_5X5_SAMPLING.get() && USE_7X7_SAMPLING.get()) {
            LayeredTerrainMod.LOGGER.error("❌ Cannot enable both 5x5 and 7x7 sampling!");
            hasErrors = true;
        }

        if (ENABLE_GPU_ACCELERATION.get() && !GPUAccelerator.isAvailable()) {
            LayeredTerrainMod.LOGGER.warn("⚠️  GPU acceleration enabled but no compatible GPU found");
            LayeredTerrainMod.LOGGER.warn("    Falling back to CPU processing");
        }

        if (ENABLE_ML_PREDICTION.get() && !MLPredictor.isAvailable()) {
            LayeredTerrainMod.LOGGER.warn("⚠️  ML prediction enabled but libraries not available");
        }

        // Check thread configuration
        int threads = WORKER_THREADS.get();
        int available = Runtime.getRuntime().availableProcessors();
        if (threads > available) {
            LayeredTerrainMod.LOGGER.warn(
                "⚠️  Worker threads ({}) exceeds available cores ({})",
                threads, available
            );
        }

        // Check memory pool configuration
        if (ENABLE_TIERED_POOLING.get()) {
            int totalPool = POOL_HOT_TIER_SIZE.get() +
                           POOL_WARM_TIER_SIZE.get() +
                           POOL_COLD_TIER_SIZE.get();
            if (totalPool < 50) {
                LayeredTerrainMod.LOGGER.warn(
                    "⚠️  Total pool size ({}) is very small, may impact performance",
                    totalPool
                );
            }
        }

        if (hasErrors) {
            throw new RuntimeException("Configuration validation failed - check logs");
        }
    }
}
