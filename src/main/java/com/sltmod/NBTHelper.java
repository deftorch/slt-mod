package com.sltmod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper for chunk processing metadata.
 * Note: NBT persistence disabled for compatibility - using runtime tracking.
 */
public class NBTHelper {

    private static final Set<ChunkPos> PROCESSED_CHUNKS = ConcurrentHashMap.newKeySet();
    private static final String NBT_ROOT_KEY = "sltmod";
    private static final String NBT_PROCESSED_KEY = "processed";
    private static int currentConfigHash = 0;

    static {
        updateConfigHash();
    }

    /**
     * Check if chunk has been processed
     */
    public static boolean isProcessed(LevelChunk chunk) {
        return PROCESSED_CHUNKS.contains(chunk.getPos());
    }

    /**
     * Mark chunk as processed
     */
    public static void markAsProcessed(LevelChunk chunk) {
        PROCESSED_CHUNKS.add(chunk.getPos());
        chunk.setUnsaved(true);
    }

    /**
     * Clear processed flag (for reprocessing)
     */
    public static void clearProcessedFlag(LevelChunk chunk) {
        PROCESSED_CHUNKS.remove(chunk.getPos());
    }

    public static void saveToNBT(LevelChunk chunk, CompoundTag tag) {
        if (isProcessed(chunk)) {
            CompoundTag modTag = tag.getCompound(NBT_ROOT_KEY);
            modTag.putBoolean(NBT_PROCESSED_KEY, true);
            // Add other metadata if needed, like version/timestamp
            tag.put(NBT_ROOT_KEY, modTag);
        }
    }

    public static void loadFromNBT(LevelChunk chunk, CompoundTag tag) {
        if (tag.contains(NBT_ROOT_KEY)) {
            CompoundTag modTag = tag.getCompound(NBT_ROOT_KEY);
            if (modTag.getBoolean(NBT_PROCESSED_KEY)) {
                PROCESSED_CHUNKS.add(chunk.getPos());
                // We don't mark as unsaved here as we just loaded it
            }
        }
    }

    /**
     * Get processing metadata (Stubbed)
     */
    public static ProcessingMetadata getMetadata(LevelChunk chunk) {
        return null; // Not supported in runtime-only mode
    }

    public static class ProcessingMetadata {
        public final int version;
        public final long timestamp;
        public final String modVersion;
        public final int configHash;

        ProcessingMetadata(int version, long timestamp, String modVersion, int configHash) {
            this.version = version;
            this.timestamp = timestamp;
            this.modVersion = modVersion;
            this.configHash = configHash;
        }

        public boolean isUpToDate() {
            return false;
        }

        public long getAgeDays() {
            return 0;
        }
    }

    /**
     * Update config hash when configuration changes
     */
    public static void updateConfigHash() {
        try {
            // Hash important config values that would require reprocessing
            int hash = Objects.hash(
                LayeredTerrainConfig.SMOOTHING_TYPE.get(),
                LayeredTerrainConfig.SMOOTHING_PASSES.get(),
                LayeredTerrainConfig.MAX_DIFFERENTIAL.get(),
                LayeredTerrainConfig.USE_5X5_SAMPLING.get(),
                LayeredTerrainConfig.USE_7X7_SAMPLING.get(),
                LayeredTerrainConfig.DAMPING_FACTOR.get(),
                LayeredTerrainConfig.SCALE_FACTOR.get()
            );

            if (hash != currentConfigHash) {
                LayeredTerrainMod.LOGGER.debug("Config hash updated: {} -> {}", currentConfigHash, hash);
                currentConfigHash = hash;
            }
        } catch (Exception e) {
            // Config might not be loaded yet or we are in a test environment
        }
    }
}
