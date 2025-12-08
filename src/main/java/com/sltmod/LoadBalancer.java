package com.sltmod;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

/**
 * Manages load balancing of chunk processing tasks across multiple worker threads.
 *
 * <p>This class implements several strategies to distribute work efficiently:
 * <ul>
 *   <li>{@link LayeredTerrainConfig.LoadBalancingStrategy#ROUND_ROBIN}: Sequential distribution.</li>
 *   <li>{@link LayeredTerrainConfig.LoadBalancingStrategy#LEAST_LOADED}: Assigns to thread with fewest tasks.</li>
 *   <li>{@link LayeredTerrainConfig.LoadBalancingStrategy#WORK_STEALING}: Allows idle threads to take work.</li>
 *   <li>{@link LayeredTerrainConfig.LoadBalancingStrategy#PRIORITY_BASED}: Dedicates threads to high-priority chunks.</li>
 *   <li>{@link LayeredTerrainConfig.LoadBalancingStrategy#ADAPTIVE}: Dynamically switches strategies based on load.</li>
 * </ul>
 * </p>
 *
 * <p>It maintains separate queues for each worker thread to minimize contention.</p>
 */
public class LoadBalancer {

    public static ExecutorService[] workerPools;
    public static AtomicInteger[] threadLoads; // Removed final
    private static Queue<ChunkTask>[] threadQueues; // Removed final
    private static final Map<ChunkPos, Integer> chunkPriorities = new ConcurrentHashMap<>();

    private static LayeredTerrainConfig.LoadBalancingStrategy currentStrategy;
    private static final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    private static class ChunkTask implements Comparable<ChunkTask> {
        final LevelChunk chunk;
        final int priority;
        final long submitTime;

        ChunkTask(LevelChunk chunk, int priority) {
            this.chunk = chunk;
            this.priority = priority;
            this.submitTime = System.currentTimeMillis();
        }

        @Override
        public int compareTo(ChunkTask other) {
            // Higher priority first
            return Integer.compare(other.priority, this.priority);
        }
    }

    /**
     * Initializes the load balancer thread pools and data structures.
     *
     * <p>Creates dedicated single-thread executors for each worker and initializes
     * task queues. Takes no action if load balancing is disabled in config.</p>
     */
    @SuppressWarnings("unchecked")
    public static void initialize() {
        if (!LayeredTerrainConfig.ENABLE_LOAD_BALANCING.get()) {
            LayeredTerrainMod.LOGGER.info("Load balancing disabled");
            return;
        }

        int threads = LayeredTerrainConfig.WORKER_THREADS.get();
        if (threads == 0) {
            threads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        }

        workerPools = new ExecutorService[threads];
        threadLoads = new AtomicInteger[threads];
        threadQueues = new Queue[threads];

        for (int i = 0; i < threads; i++) {
            final int threadId = i;
            workerPools[i] = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "LayeredTerrain-LoadBalanced-" + threadId);
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });

            threadLoads[i] = new AtomicInteger(0);
            threadQueues[i] = new PriorityBlockingQueue<>();
        }

        currentStrategy = LayeredTerrainConfig.LOAD_BALANCING_STRATEGY.get();

        LayeredTerrainMod.LOGGER.info(
            "Load balancer initialized: {} threads, strategy={}",
            threads, currentStrategy
        );
    }

    /**
     * Submits a chunk for processing, routing it to the optimal thread.
     *
     * <p>Calculates the priority of the chunk based on player proximity and assigns
     * it to a worker thread according to the active load balancing strategy.</p>
     *
     * @param chunk The chunk to process.
     * @param nearbyPlayers A collection of players near this chunk, used for priority calculation.
     */
    public static void submitChunk(LevelChunk chunk, Collection<ServerPlayer> nearbyPlayers) {
        if (!LayeredTerrainConfig.ENABLE_LOAD_BALANCING.get()) {
            // Fallback to simple async processing
            AsyncProcessor.submitChunkForCalculation(chunk);
            return;
        }

        // Calculate priority
        int priority = calculateChunkPriority(chunk, nearbyPlayers);
        chunkPriorities.put(chunk.getPos(), priority);

        // Select thread based on strategy
        int threadIndex = selectThread(chunk, priority);

        // Create task and submit
        ChunkTask task = new ChunkTask(chunk, priority);
        threadQueues[threadIndex].offer(task);
        threadLoads[threadIndex].incrementAndGet();

        // Submit to executor
        workerPools[threadIndex].submit(() -> {
            try {
                processChunkTask(task);
            } finally {
                threadLoads[threadIndex].decrementAndGet();
                chunkPriorities.remove(chunk.getPos());
            }
        });
    }

    /**
     * Calculate chunk priority based on player proximity
     */
    private static int calculateChunkPriority(LevelChunk chunk, Collection<ServerPlayer> nearbyPlayers) {
        if (!LayeredTerrainConfig.PRIORITIZE_PLAYER_CHUNKS.get() || nearbyPlayers == null) {
            return 0; // Default priority
        }

        ChunkPos chunkPos = chunk.getPos();
        int maxPriority = 0;
        int priorityRadius = LayeredTerrainConfig.PLAYER_CHUNK_PRIORITY_RADIUS.get();

        for (ServerPlayer player : nearbyPlayers) {
            ChunkPos playerChunk = new ChunkPos(player.blockPosition());
            int dx = Math.abs(chunkPos.x - playerChunk.x);
            int dz = Math.abs(chunkPos.z - playerChunk.z);
            int distance = Math.max(dx, dz);

            if (distance <= priorityRadius) {
                // Priority decreases with distance
                int priority = (priorityRadius - distance) * 10;
                maxPriority = Math.max(maxPriority, priority);
            }
        }

        return maxPriority;
    }

    /**
     * Select optimal thread based on strategy
     */
    private static int selectThread(LevelChunk chunk, int priority) {
        currentStrategy = LayeredTerrainConfig.LOAD_BALANCING_STRATEGY.get();

        return switch (currentStrategy) {
            case ROUND_ROBIN -> roundRobinSelection();
            case LEAST_LOADED -> leastLoadedSelection();
            case WORK_STEALING -> workStealingSelection();
            case PRIORITY_BASED -> priorityBasedSelection(priority);
            case ADAPTIVE -> adaptiveSelection(chunk, priority);
        };
    }

    /**
     * Simple round-robin distribution
     */
    private static int roundRobinSelection() {
        int index = roundRobinCounter.getAndIncrement();
        return Math.abs(index % workerPools.length);
    }

    /**
     * Assign to least loaded thread
     */
    private static int leastLoadedSelection() {
        int minLoad = Integer.MAX_VALUE;
        int minIndex = 0;

        for (int i = 0; i < threadLoads.length; i++) {
            int load = threadLoads[i].get();
            if (load < minLoad) {
                minLoad = load;
                minIndex = i;
            }
        }

        return minIndex;
    }

    /**
     * Work stealing - assign to least loaded or allow stealing
     */
    private static int workStealingSelection() {
        // Similar to least loaded, but enables work stealing
        int minLoad = Integer.MAX_VALUE;
        int minIndex = 0;

        for (int i = 0; i < threadLoads.length; i++) {
            int load = threadLoads[i].get() + threadQueues[i].size();
            if (load < minLoad) {
                minLoad = load;
                minIndex = i;
            }
        }

        // Trigger work stealing if imbalanced
        triggerWorkStealingIfNeeded();

        return minIndex;
    }

    /**
     * Priority-based selection - high priority to dedicated threads
     */
    private static int priorityBasedSelection(int priority) {
        if (priority > 50) {
            // High priority - use first threads
            return leastLoadedInRange(0, workerPools.length / 2);
        } else if (priority > 20) {
            // Medium priority - use middle threads
            return leastLoadedInRange(workerPools.length / 4, 3 * workerPools.length / 4);
        } else {
            // Low priority - use last threads
            return leastLoadedInRange(workerPools.length / 2, workerPools.length);
        }
    }

    /**
     * Adaptive strategy - choose best based on current conditions
     */
    private static int adaptiveSelection(LevelChunk chunk, int priority) {
        // Analyze current system state
        int totalLoad = 0;
        int maxLoad = 0;
        int minLoad = Integer.MAX_VALUE;

        for (AtomicInteger load : threadLoads) {
            int l = load.get();
            totalLoad += l;
            maxLoad = Math.max(maxLoad, l);
            minLoad = Math.min(minLoad, l);
        }

        double avgLoad = (double) totalLoad / threadLoads.length;
        double loadImbalance = maxLoad - minLoad;

        // Decision logic
        if (loadImbalance > avgLoad * 0.5) {
            // High imbalance - use work stealing
            return workStealingSelection();
        } else if (priority > 30) {
            // High priority chunk - use priority-based
            return priorityBasedSelection(priority);
        } else {
            // Normal conditions - use least loaded
            return leastLoadedSelection();
        }
    }

    /**
     * Find least loaded thread in range
     */
    private static int leastLoadedInRange(int start, int end) {
        int minLoad = Integer.MAX_VALUE;
        int minIndex = start;

        for (int i = start; i < end && i < threadLoads.length; i++) {
            int load = threadLoads[i].get();
            if (load < minLoad) {
                minLoad = load;
                minIndex = i;
            }
        }

        return minIndex;
    }

    /**
     * Trigger work stealing if threads are imbalanced
     */
    private static void triggerWorkStealingIfNeeded() {
        // Find most and least loaded threads
        int maxLoad = -1;
        int maxIndex = -1;
        int minLoad = Integer.MAX_VALUE;
        int minIndex = -1;

        for (int i = 0; i < threadLoads.length; i++) {
            int load = threadLoads[i].get() + threadQueues[i].size();
            if (load > maxLoad) {
                maxLoad = load;
                maxIndex = i;
            }
            if (load < minLoad) {
                minLoad = load;
                minIndex = i;
            }
        }

        // If imbalance is significant, steal work
        if (maxLoad - minLoad > 5 && maxIndex != -1 && minIndex != -1) {
            int stolen = 0;
            while (stolen < 3 && !threadQueues[maxIndex].isEmpty()) {
                ChunkTask task = (ChunkTask) threadQueues[maxIndex].poll();
                if (task != null) {
                    threadQueues[minIndex].offer(task);
                    threadLoads[maxIndex].decrementAndGet();
                    threadLoads[minIndex].incrementAndGet();
                    stolen++;
                }
            }

            if (stolen > 0 && LayeredTerrainConfig.DEBUG_MODE.get()) {
                LayeredTerrainMod.LOGGER.debug(
                    "Work stealing: {} tasks from thread {} to thread {}",
                    stolen, maxIndex, minIndex
                );
            }
        }
    }

    /**
     * Process chunk task
     */
    private static void processChunkTask(ChunkTask task) {
        long startTime = System.nanoTime();

        try {
            AsyncProcessor.calculateThicknessMapInternal(task.chunk, task.chunk.getPos());

            // Log if task waited too long
            long waitTime = startTime - task.submitTime * 1_000_000L;
            if (waitTime > 100_000_000L && LayeredTerrainConfig.DEBUG_MODE.get()) { // 100ms
                LayeredTerrainMod.LOGGER.debug(
                    "High queue wait time for chunk {}: {}ms",
                    task.chunk.getPos(), waitTime / 1_000_000.0
                );
            }

        } catch (Exception e) {
            LayeredTerrainMod.LOGGER.error(
                "Error processing chunk {} in load balancer",
                task.chunk.getPos(), e
            );
        }
    }

    /**
     * Reconfigures the load balancer when settings change.
     *
     * <p>Updates the active strategy. If load balancing is disabled, it shuts down
     * the system.</p>
     */
    public static void reconfigure() {
        LayeredTerrainMod.LOGGER.info("Reconfiguring load balancer...");

        if (!LayeredTerrainConfig.ENABLE_LOAD_BALANCING.get()) {
            shutdown();
            return;
        }

        currentStrategy = LayeredTerrainConfig.LOAD_BALANCING_STRATEGY.get();

        LayeredTerrainMod.LOGGER.info("Load balancer reconfigured: strategy={}", currentStrategy);
    }

    /**
     * Returns a string representation of the current load statistics.
     *
     * @return A formatted string showing current strategy and load per thread.
     */
    public static String getStats() {
        if (threadLoads == null) return "Load balancer not initialized";

        StringBuilder sb = new StringBuilder();
        sb.append("Load Balancer - Strategy: ").append(currentStrategy).append(", Threads: [");

        for (int i = 0; i < threadLoads.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(threadLoads[i].get());
        }
        sb.append("]");

        return sb.toString();
    }

    /**
     * Shuts down all worker threads and cleans up resources.
     */
    public static void shutdown() {
        if (workerPools == null) return;

        LayeredTerrainMod.LOGGER.info("Shutting down load balancer...");

        for (ExecutorService pool : workerPools) {
            if (pool != null) {
                pool.shutdown();
                try {
                    if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                        pool.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    pool.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }

        LayeredTerrainMod.LOGGER.info("Load balancer shutdown complete");
    }
}
