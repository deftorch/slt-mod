package com.sltmod;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the queue of chunks waiting for terrain processing.
 *
 * <p>This class acts as a buffer between the main thread (where chunks are loaded)
 * and the {@link AsyncProcessor}. It uses weak references to hold chunks to prevent
 * memory leaks if chunks are unloaded while in the queue.</p>
 */
public class QueueManager {

    private static final ConcurrentLinkedQueue<ChunkTask> QUEUE = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger queueSize = new AtomicInteger(0);

    private static class ChunkTask {
        final WeakReference<LevelChunk> chunkRef;
        final ChunkPos pos;
        final long submitTime;

        ChunkTask(LevelChunk chunk) {
            this.chunkRef = new WeakReference<>(chunk);
            this.pos = chunk.getPos();
            this.submitTime = System.currentTimeMillis();
        }

        boolean isValid() {
            LevelChunk chunk = chunkRef.get();
            // isDiscarded() check removed for 1.20.1 compatibility
            return chunk != null; // && !chunk.isDiscarded();
        }

        LevelChunk getChunk() {
            return chunkRef.get();
        }

        long getWaitTime() {
            return System.currentTimeMillis() - submitTime;
        }
    }

    /**
     * Submits a chunk to the processing queue.
     *
     * <p>Rejects the chunk if the queue is full (configured via {@code MAX_QUEUE_SIZE}).</p>
     *
     * @param chunk The chunk to process.
     * @return True if submitted, false if the queue was full.
     */
    public static boolean submit(LevelChunk chunk) {
        int maxSize = LayeredTerrainConfig.MAX_QUEUE_SIZE.get();

        if (queueSize.get() >= maxSize) {
            if (LayeredTerrainConfig.DEBUG_MODE.get()) {
                LayeredTerrainMod.LOGGER.debug("Queue full ({}), skipping chunk {}",
                    maxSize, chunk.getPos());
            }
            return false;
        }

        QUEUE.offer(new ChunkTask(chunk));
        queueSize.incrementAndGet();
        return true;
    }

    /**
     * Processes items from the queue and submits them to the {@link AsyncProcessor}.
     *
     * <p>Called every tick. Processes up to {@code MAX_CHUNKS_PER_TICK} items.
     * Skips chunks that are no longer valid (unloaded).</p>
     *
     * @return The number of chunks processed.
     */
    public static int processQueue() {
        int maxPerTick = LayeredTerrainConfig.MAX_CHUNKS_PER_TICK.get();
        int processed = 0;

        while (processed < maxPerTick) {
            ChunkTask task = QUEUE.poll();
            if (task == null) break;

            queueSize.decrementAndGet();

            if (!task.isValid()) {
                continue;
            }

            // Log excessive wait times
            if (task.getWaitTime() > 5000 && LayeredTerrainConfig.DEBUG_MODE.get()) {
                LayeredTerrainMod.LOGGER.debug(
                    "Chunk {} waited {}ms in queue",
                    task.pos, task.getWaitTime()
                );
            }

            LevelChunk chunk = task.getChunk();
            if (chunk != null) {
                AsyncProcessor.submitChunkForCalculation(chunk);
                processed++;
            }
        }

        return processed;
    }

    /**
     * Returns the current number of items in the queue.
     * @return Queue size.
     */
    public static int getQueueSize() {
        return queueSize.get();
    }

    /**
     * Clears the queue.
     */
    public static void clear() {
        QUEUE.clear();
        queueSize.set(0);
    }
}
