package com.sltmod;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Queue manager for pending chunk calculations
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

    public static int getQueueSize() {
        return queueSize.get();
    }

    public static void clear() {
        QUEUE.clear();
        queueSize.set(0);
    }
}
