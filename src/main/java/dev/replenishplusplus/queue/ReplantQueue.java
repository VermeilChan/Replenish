package dev.replenishplusplus.queue;

import dev.replenishplusplus.crop.AgeMetaRegistry;
import dev.replenishplusplus.crop.CocoaCropInfo;
import dev.replenishplusplus.crop.CropAnchors;
import dev.replenishplusplus.crop.CropInfo;
import dev.replenishplusplus.crop.SimpleCropInfo;
import dev.replenishplusplus.util.LocationUtil;
import dev.replenishplusplus.util.WarningThrottle;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.logging.Level;

/**
 * Time-wheel scheduler for delayed crop replants.
 * Uses a pooled array-based node structure to avoid GC pressure.
 * Each node packs target age, cocoa facing, and retry count into a single int.
 */
public final class ReplantQueue {

    private static final int WHEEL_BITS       = 13;
    private static final int WHEEL_SIZE       = 1 << WHEEL_BITS;
    private static final int WHEEL_MASK       = WHEEL_SIZE - 1;
    private static final int INITIAL_POOL_SIZE = 1 << 14;
    private static final int MAX_UNLOAD_RETRIES = 20;

    // Metadata bit layout:  [retries:8][facing:2][age:8]
    private static final int AGE_MASK      = 0xFF;
    private static final int FACE_SHIFT    = 8;
    private static final int FACE_MASK     = 0x3;
    private static final int RETRY_SHIFT   = 10;
    private static final int RETRY_MASK    = 0xFF;

    private final Plugin plugin;
    private final AgeMetaRegistry ageMetaRegistry;
    private final int maxPerTick;

    private final int[] wheelHeads = new int[WHEEL_SIZE];

    private Block[]    poolBlocks;
    private Material[] poolMaterials;
    private int[]      poolMeta;
    private int[]      poolNext;

    private int freeHead = -1;
    private int cursor   = 0;
    private ScheduledTask scheduledTask;
    private volatile boolean started = false;

    public ReplantQueue(Plugin plugin, int maxPerTick, AgeMetaRegistry ageMetaRegistry) {
        this.plugin = plugin;
        this.ageMetaRegistry = ageMetaRegistry;
        this.maxPerTick = Math.max(256, maxPerTick);
        Arrays.fill(wheelHeads, -1);
        primePool();
    }

    // --------------------------- Lifecycle ---------------------------

    public synchronized void start() {
        if (started) return;
        started = true;
        scheduledTask = plugin.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(plugin, _ -> tick(), 1L, 1L);
    }

    public synchronized void stop() {
        if (!started) return;
        started = false;
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
        resetPool();
        Arrays.fill(wheelHeads, -1);
        cursor = 0;
    }

    public synchronized int pendingCount() {
        int count = 0;
        for (int i = 0; i < WHEEL_SIZE; i++) {
            for (int head = wheelHeads[i]; head != -1; head = poolNext[head]) {
                count++;
            }
        }
        return count;
    }

    // --------------------------- Enqueue ---------------------------

    public synchronized void enqueue(
            Block block, Material material, int delayTicks,
            int targetAge, BlockFace cocoaFacing) {

        int delay = clampDelay(delayTicks, block);
        int slot  = (cursor + delay) & WHEEL_MASK;
        int index = acquire();

        poolBlocks[index]    = block;
        poolMaterials[index] = material;
        poolMeta[index]      = packMeta(targetAge, cocoaFacing);
        poolNext[index]      = wheelHeads[slot];
        wheelHeads[slot]     = index;
    }

    // --------------------------- Tick ---------------------------

    private synchronized void tick() {
        if (!started) return;

        int head = wheelHeads[cursor];
        if (head == -1) {
            cursor = (cursor + 1) & WHEEL_MASK;
            return;
        }
        wheelHeads[cursor] = -1;

        int processed    = 0;
        int deferredHead = -1;
        int deferredTail = -1;

        while (head != -1) {
            int next = poolNext[head];
            poolNext[head] = -1;

            // Cap work per tick — defer the rest to the next slot.
            if (processed >= maxPerTick) {
                deferredTail = appendDeferred(deferredHead, deferredTail, head);
                if (deferredHead == -1) deferredHead = head;
                head = next;
                continue;
            }

            Block block = poolBlocks[head];
            if (block == null) {
                release(head);
                head = next;
                continue;
            }

            if (tryReplant(head, block)) {
                release(head);
                processed++;
            } else if (retryCount(head) >= MAX_UNLOAD_RETRIES) {
                WarningThrottle.log(plugin, Level.WARNING,
                        WarningThrottle.Category.ABANDONED_REPLANT,
                        "Abandoning replant at " + LocationUtil.describe(block)
                                + " — chunk remained unloaded.");
                release(head);
                processed++;
            } else {
                incrementRetry(head);
                deferredTail = appendDeferred(deferredHead, deferredTail, head);
                if (deferredHead == -1) deferredHead = head;
            }
            head = next;
        }

        int nextSlot = (cursor + 1) & WHEEL_MASK;
        if (deferredHead != -1) {
            poolNext[deferredTail] = wheelHeads[nextSlot];
            wheelHeads[nextSlot]   = deferredHead;
        }
        cursor = nextSlot;
    }

    private int appendDeferred(int deferredHead, int deferredTail, int node) {
        if (deferredHead != -1) {
            poolNext[deferredTail] = node;
        }
        return node;
    }

    // --------------------------- Replant Logic ---------------------------

    /**
     * Attempts to replant the crop at the given slot.
     * @return true if the slot is fully handled (success or permanent failure)
     *         and should be released; false if it should be deferred.
     */
    private boolean tryReplant(int index, Block block) {
        if (!isChunkLoaded(block)) return false;

        try {
            Material material = poolMaterials[index];
            if (material == null) return true;

            CropInfo info = ageMetaRegistry.get(material);
            switch (info) {
                case null -> {
                    WarningThrottle.log(plugin, Level.WARNING,
                            WarningThrottle.Category.AGE_DATA_MISSING,
                            "No age data found for plant: " + material
                                    + ", skipping replant at " + LocationUtil.describe(block));
                    return true;
                }
                case CocoaCropInfo cocoa -> replantCocoa(index, block, cocoa);
                case SimpleCropInfo simple -> replantNormal(index, block, simple);
                default -> {
                }
            }

        } catch (Exception e) {
            WarningThrottle.log(plugin, Level.WARNING,
                    WarningThrottle.Category.REPLANT_FAILED,
                    "Failed to replant crop at " + LocationUtil.describe(block)
                            + ": " + e.getMessage());
        }
        return true;
    }

    private void replantNormal(int index, Block block, SimpleCropInfo info) {
        if (!block.getType().isAir()) return;

        Material below = block.getRelative(BlockFace.DOWN).getType();
        if (info.requiresFarmland() && below != Material.FARMLAND)   return;
        if (info.requiresSoulSand() && below != Material.SOUL_SAND) return;

        int targetAge = poolMeta[index] & AGE_MASK;
        block.setBlockData(info.stateFor(targetAge), false);
    }

    private void replantCocoa(int index, Block block, CocoaCropInfo info) {
        if (!block.getType().isAir()) return;
        if (poolMaterials[index] != Material.COCOA) return;

        int metadata   = poolMeta[index];
        int targetAge  = metadata & AGE_MASK;
        int faceOrdinal = (metadata >>> FACE_SHIFT) & FACE_MASK;
        BlockFace face = AgeMetaRegistry.COCOA_FACES.get(faceOrdinal);

        Block attached = block.getRelative(face);
        if (!CropAnchors.JUNGLE_LOGS.contains(attached.getType())) return;

        block.setBlockData(info.stateFor(targetAge, faceOrdinal), false);
    }

    // --------------------------- Helpers ---------------------------

    private static boolean isChunkLoaded(Block block) {
        World world = block.getWorld();
        try {
            return world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
        } catch (Exception e) {
            return false;
        }
    }

    private int clampDelay(int delayTicks, Block block) {
        int delay = Math.max(1, delayTicks);
        if (delay >= WHEEL_SIZE) {
            WarningThrottle.log(plugin, Level.WARNING,
                    WarningThrottle.Category.DELAY_TRUNCATION,
                    "Replant delay truncation triggered for block at "
                            + LocationUtil.describe(block));
            delay = WHEEL_SIZE - 1;
        }
        return delay;
    }

    private static int packMeta(int targetAge, BlockFace face) {
        int safeAge = Math.max(0, targetAge) & AGE_MASK;
        int faceOrdinal = faceToOrdinal(face) & FACE_MASK;
        return safeAge | (faceOrdinal << FACE_SHIFT);
    }

    private int retryCount(int index) {
        return (poolMeta[index] >>> RETRY_SHIFT) & RETRY_MASK;
    }

    private void incrementRetry(int index) {
        int retries = retryCount(index) + 1;
        poolMeta[index] = (poolMeta[index] & ~(RETRY_MASK << RETRY_SHIFT))
                | (retries << RETRY_SHIFT);
    }

    private static int faceToOrdinal(BlockFace face) {
        if (face == BlockFace.EAST)  return 1;
        if (face == BlockFace.SOUTH) return 2;
        if (face == BlockFace.WEST)  return 3;
        return 0; // NORTH or null
    }

    // --------------------------- Pool Management ---------------------------

    private void primePool() {
        poolBlocks    = new Block[ReplantQueue.INITIAL_POOL_SIZE];
        poolMaterials = new Material[ReplantQueue.INITIAL_POOL_SIZE];
        poolMeta      = new int[ReplantQueue.INITIAL_POOL_SIZE];
        poolNext      = new int[ReplantQueue.INITIAL_POOL_SIZE];
        for (int i = ReplantQueue.INITIAL_POOL_SIZE - 1; i >= 0; i--) {
            poolNext[i] = freeHead;
            freeHead    = i;
        }
    }

    private void resetPool() {
        for (int i = poolBlocks.length - 1; i >= 0; i--) {
            poolBlocks[i]    = null;
            poolMaterials[i] = null;
            poolMeta[i]      = 0;
            poolNext[i]      = freeHead;
            freeHead         = i;
        }
    }

    private void growPool() {
        int oldSize = poolBlocks.length;
        int newSize = oldSize << 1;
        if (newSize <= oldSize) {
            throw new IllegalStateException("Replant pool size overflow");
        }

        poolBlocks    = Arrays.copyOf(poolBlocks,    newSize);
        poolMaterials = Arrays.copyOf(poolMaterials, newSize);
        poolMeta      = Arrays.copyOf(poolMeta,      newSize);
        poolNext      = Arrays.copyOf(poolNext,      newSize);

        for (int i = newSize - 1; i >= oldSize; i--) {
            poolBlocks[i]    = null;
            poolMaterials[i] = null;
            poolMeta[i]      = 0;
            poolNext[i]      = freeHead;
            freeHead         = i;
        }
    }

    private int acquire() {
        if (freeHead == -1) growPool();
        int index = freeHead;
        freeHead  = poolNext[index];
        poolNext[index] = -1;
        return index;
    }

    private void release(int index) {
        poolBlocks[index]    = null;
        poolMaterials[index] = null;
        poolMeta[index]      = 0;
        poolNext[index]      = freeHead;
        freeHead             = index;
    }
}