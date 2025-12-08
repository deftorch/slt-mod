package com.sltmod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Advanced layer registry with tag-based detection and explicit mappings.
 *
 * <p>This class manages the relationship between full blocks and their layered counterparts.
 * It supports:</p>
 * <ul>
 *   <li><b>Explicit Mappings:</b> Hardcoded associations for vanilla blocks.</li>
 *   <li><b>Tag Detection:</b> Auto-detects modded blocks based on tags (e.g. {@code #minecraft:dirt}).</li>
 *   <li><b>Whitelisting/Blacklisting:</b> Configurable inclusions and exclusions.</li>
 * </ul>
 */
public class LayerRegistry {

    // Explicit mappings (base block -> layer blocks array)
    private static final Map<Block, Block[]> EXPLICIT_MAPPINGS = new ConcurrentHashMap<>();

    // Tag-based detection cache
    private static final Map<Block, BlockCategory> CATEGORY_CACHE = new ConcurrentHashMap<>();

    // Mod compatibility
    private static final Set<String> BLACKLISTED_MODS = new HashSet<>();
    private static final Set<Block> WHITELISTED_BLOCKS = new HashSet<>();

    // Statistics
    private static final AtomicInteger registeredBlocks = new AtomicInteger(0);
    private static final AtomicInteger tagDetections = new AtomicInteger(0);
    private static final AtomicInteger explicitMappings = new AtomicInteger(0);

    /**
     * Categories for grouping similar blocks during auto-detection.
     */
    enum BlockCategory {
        GRASS_LIKE,
        DIRT_LIKE,
        STONE_LIKE,
        SAND_LIKE,
        GRAVEL_LIKE,
        SNOW_LIKE,
        UNKNOWN
    }

    /**
     * Initialize layer registry with default mappings and configuration.
     */
    public static void initialize() {
        LayeredTerrainMod.LOGGER.info("Initializing layer registry...");

        // Load mod blacklist from config
        loadModBlacklist();

        // Load block whitelist from config
        loadBlockWhitelist();

        // Register vanilla blocks
        registerVanillaBlocks();

        // Auto-detect compatible blocks using tags
        if (LayeredTerrainConfig.DEBUG_MODE.get()) {
            autoDetectBlocks();
        }

        LayeredTerrainMod.LOGGER.info(
            "Layer registry initialized: {} blocks registered ({} explicit, {} tag-based)",
            registeredBlocks.get(), explicitMappings.get(), tagDetections.get()
        );
    }

    /**
     * Load mod blacklist from configuration
     */
    private static void loadModBlacklist() {
        List<? extends String> blacklist = LayeredTerrainConfig.BLACKLISTED_MODS.get();
        BLACKLISTED_MODS.addAll(blacklist);

        if (!BLACKLISTED_MODS.isEmpty()) {
            LayeredTerrainMod.LOGGER.info("Blacklisted mods: {}", BLACKLISTED_MODS);
        }
    }

    /**
     * Load block whitelist from configuration
     */
    private static void loadBlockWhitelist() {
        List<? extends String> whitelist = LayeredTerrainConfig.WHITELISTED_BLOCKS.get();

        for (String blockId : whitelist) {
            try {
                ResourceLocation loc = ResourceLocation.tryParse(blockId);
                if (loc != null) {
                    Block block = BuiltInRegistries.BLOCK.get(loc);

                    if (block != null && block != Blocks.AIR) {
                        WHITELISTED_BLOCKS.add(block);
                        LayeredTerrainMod.LOGGER.debug("Whitelisted block: {}", blockId);
                    }
                } else {
                    LayeredTerrainMod.LOGGER.warn("Invalid block ID format in whitelist: {}", blockId);
                }
            } catch (Exception e) {
                LayeredTerrainMod.LOGGER.warn("Error processing whitelist block: {}", blockId);
            }
        }

        if (!WHITELISTED_BLOCKS.isEmpty()) {
            LayeredTerrainMod.LOGGER.info("Whitelisted blocks: {}", WHITELISTED_BLOCKS.size());
        }
    }

    /**
     * Register vanilla blocks with explicit layer mappings
     * In production, these would be actual layered block instances
     */
    private static void registerVanillaBlocks() {
        // Grass blocks
        Block[] grassLayers = createLayerArray(Blocks.GRASS_BLOCK);
        registerExplicit(Blocks.GRASS_BLOCK, grassLayers);

        // Dirt blocks
        Block[] dirtLayers = createLayerArray(Blocks.DIRT);
        registerExplicit(Blocks.DIRT, dirtLayers);
        registerExplicit(Blocks.COARSE_DIRT, dirtLayers);
        registerExplicit(Blocks.ROOTED_DIRT, dirtLayers);

        // Stone blocks
        Block[] stoneLayers = createLayerArray(Blocks.STONE);
        registerExplicit(Blocks.STONE, stoneLayers);
        registerExplicit(Blocks.COBBLESTONE, stoneLayers);
        registerExplicit(Blocks.ANDESITE, stoneLayers);
        registerExplicit(Blocks.DIORITE, stoneLayers);
        registerExplicit(Blocks.GRANITE, stoneLayers);

        // Sand blocks
        Block[] sandLayers = createLayerArray(Blocks.SAND);
        registerExplicit(Blocks.SAND, sandLayers);

        Block[] redSandLayers = createLayerArray(Blocks.RED_SAND);
        registerExplicit(Blocks.RED_SAND, redSandLayers);

        // Gravel
        Block[] gravelLayers = createLayerArray(Blocks.GRAVEL);
        registerExplicit(Blocks.GRAVEL, gravelLayers);

        // Snow
        Block[] snowLayers = createLayerArray(Blocks.SNOW_BLOCK);
        registerExplicit(Blocks.SNOW_BLOCK, snowLayers);

        // Mycelium
        Block[] myceliumLayers = createLayerArray(Blocks.MYCELIUM);
        registerExplicit(Blocks.MYCELIUM, myceliumLayers);

        // Podzol
        Block[] podzolLayers = createLayerArray(Blocks.PODZOL);
        registerExplicit(Blocks.PODZOL, podzolLayers);
    }

    /**
     * Create layer array for a base block
     * In production, this would return actual layer block instances
     * For now, we return the base block as placeholder
     */
    private static Block[] createLayerArray(Block baseBlock) {
        Block[] layers = new Block[8];
        for (int i = 0; i < 8; i++) {
            // In production: layers[i] = ModBlocks.getLayerBlock(baseBlock, i + 1);
            layers[i] = baseBlock; // Placeholder
        }
        return layers;
    }

    /**
     * Register explicit mapping
     */
    private static void registerExplicit(Block baseBlock, Block[] layers) {
        if (baseBlock == null || layers == null || layers.length != 8) {
            throw new IllegalArgumentException("Invalid registration parameters");
        }

        // Check mod blacklist
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(baseBlock);
        if (id != null && BLACKLISTED_MODS.contains(id.getNamespace())) {
            LayeredTerrainMod.LOGGER.debug("Skipping blacklisted mod block: {}", id);
            return;
        }

        EXPLICIT_MAPPINGS.put(baseBlock, layers);
        registeredBlocks.incrementAndGet();
        explicitMappings.incrementAndGet();
    }

    /**
     * Auto-detect compatible blocks using tags
     */
    private static void autoDetectBlocks() {
        LayeredTerrainMod.LOGGER.debug("Auto-detecting compatible blocks...");

        int detected = 0;

        for (Block block : BuiltInRegistries.BLOCK) {
            if (block == Blocks.AIR) continue;
            if (EXPLICIT_MAPPINGS.containsKey(block)) continue;

            // Check whitelist
            if (WHITELISTED_BLOCKS.contains(block)) {
                BlockCategory category = detectCategory(block);
                if (category != BlockCategory.UNKNOWN) {
                    Block[] layers = createLayerArray(block);
                    registerExplicit(block, layers);
                    detected++;
                }
            }

            // Cache category for tag-based detection
            BlockCategory category = detectCategory(block);
            if (category != BlockCategory.UNKNOWN) {
                CATEGORY_CACHE.put(block, category);
                tagDetections.incrementAndGet();
            }
        }

        if (detected > 0) {
            LayeredTerrainMod.LOGGER.info("Auto-detected {} compatible blocks", detected);
        }
    }

    /**
     * Detect block category using tags
     */
    private static BlockCategory detectCategory(Block block) {
        // Check block tags
        if (block.defaultBlockState().is(BlockTags.DIRT)) {
            return BlockCategory.DIRT_LIKE;
        }
        if (block.defaultBlockState().is(BlockTags.STONE_ORE_REPLACEABLES)) {
            return BlockCategory.STONE_LIKE;
        }
        if (block.defaultBlockState().is(BlockTags.SAND)) {
            return BlockCategory.SAND_LIKE;
        }

        // Check by block type
        if (block == Blocks.GRASS_BLOCK || block == Blocks.MYCELIUM || block == Blocks.PODZOL) {
            return BlockCategory.GRASS_LIKE;
        }
        if (block == Blocks.GRAVEL) {
            return BlockCategory.GRAVEL_LIKE;
        }
        if (block == Blocks.SNOW_BLOCK || block == Blocks.POWDER_SNOW) {
            return BlockCategory.SNOW_LIKE;
        }

        return BlockCategory.UNKNOWN;
    }

    /**
     * Get layer block for base block and thickness.
     *
     * @param baseBlock Base block.
     * @param thickness Thickness (1-8).
     * @return Layer block, or null if not available.
     */
    public static Block getLayerBlock(Block baseBlock, int thickness) {
        // Validate inputs
        if (baseBlock == null || thickness < 1 || thickness > 8) {
            return null;
        }

        // Check explicit mappings first
        Block[] layers = EXPLICIT_MAPPINGS.get(baseBlock);
        if (layers != null) {
            return layers[thickness - 1];
        }

        // Check whitelist
        if (WHITELISTED_BLOCKS.contains(baseBlock)) {
            // Create on-demand for whitelisted blocks
            Block[] newLayers = createLayerArray(baseBlock);
            EXPLICIT_MAPPINGS.put(baseBlock, newLayers);
            return newLayers[thickness - 1];
        }

        // Not found
        return null;
    }

    /**
     * Check if block has layer support.
     *
     * @param block The block to check.
     * @return True if supported.
     */
    public static boolean hasLayers(Block block) {
        if (block == null) return false;

        return EXPLICIT_MAPPINGS.containsKey(block) ||
               WHITELISTED_BLOCKS.contains(block) ||
               CATEGORY_CACHE.containsKey(block);
    }

    /**
     * Get registry statistics.
     *
     * @return A {@link RegistryStats} object.
     */
    public static RegistryStats getStats() {
        return new RegistryStats(
            registeredBlocks.get(),
            explicitMappings.get(),
            tagDetections.get(),
            BLACKLISTED_MODS.size(),
            WHITELISTED_BLOCKS.size()
        );
    }

    /**
     * Data class for registry statistics.
     */
    public static class RegistryStats {
        public final int totalBlocks;
        public final int explicitMappings;
        public final int tagDetections;
        public final int blacklistedMods;
        public final int whitelistedBlocks;

        RegistryStats(int total, int explicit, int tags, int blacklist, int whitelist) {
            this.totalBlocks = total;
            this.explicitMappings = explicit;
            this.tagDetections = tags;
            this.blacklistedMods = blacklist;
            this.whitelistedBlocks = whitelist;
        }
    }

    /**
     * Clear caches (for hot-reload).
     */
    public static void clearCaches() {
        CATEGORY_CACHE.clear();
        LayeredTerrainMod.LOGGER.debug("Layer registry caches cleared");
    }
}
