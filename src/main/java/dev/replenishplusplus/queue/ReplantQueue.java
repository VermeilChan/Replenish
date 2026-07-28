package dev.replenishplusplus.queue;

import dev.replenishplusplus.crop.AgeMetaRegistry;
import dev.replenishplusplus.crop.CocoaCropInfo;
import dev.replenishplusplus.crop.CropAnchors;
import dev.replenishplusplus.crop.CropInfo;
import dev.replenishplusplus.crop.SimpleCropInfo;
import dev.replenishplusplus.util.LocationUtil;
import dev.replenishplusplus.util.WarningThrottle;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.logging.Level;

public final class ReplantQueue {

    private static final int WHEEL_BITS         = 13;
    private static final int WHEEL_SIZE         = 1 << WHEEL_BITS;
    private static final int WHEEL_MASK         = WHEEL_SIZE - 1;
    private static final int INITIAL_POOL_SIZE  = 1 << 10;
    private static final int MAX_UNLOAD_RETRIES = 20;

    private static final int AGE_MASK      = 0xFF;
    private static final int FACE_SHIFT    = 8;
    private static final int FACE_MASK     = 0x3;
    private static final int RETRY_SHIFT   = 10;
    private static final int RETRY_MASK    = 0xFF;

    private final Plugin plugin;
    private final AgeMetaRegistry ageMetaRegistry;
    private final int maxPerTick;
    private final int maxPoolSize;

    private final int[] wheelHeads = new int[WHEEL_SIZE];

    private Block[]    poolBlocks;
    private Material[] poolMaterials;
    private int[]      poolMeta;
    private int[]      poolNext;

    private int freeHead = -1;
    private int cursor   = 0;
    private int pendingCount = 0;
    private ScheduledTask scheduledTask;
    private volatile boolean started = false;

    public ReplantQueue(Plugin plugin, int maxPerTick, int maxPoolSize, AgeMetaRegistry ageMetaRegistry) {
        this.plugin          = plugin;
        this.ageMetaRegistry = ageMetaRegistry;
        this.maxPerTick      = Math.max(256, maxPerTick);
        this.maxPoolSize     = Math.max(256, maxPoolSize);
        Arrays.fill(wheelHeads, -1);
        primePool();
    }

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
        pendingCount = 0;
    }

    public synchronized int pendingCount() {
        return pendingCount;
    }

    public synchronized void enqueue(
            Block block, Material material, int delayTicks,
            int targetAge, BlockFace cocoaFacing) {

        if (pendingCount >= maxPoolSize) {
            WarningThrottle.log(plugin, Level.WARNING,
                    WarningThrottle.Category.QUEUE_BACKPRESSURE,
                    "Replant queue saturated (" + pendingCount + "/" + maxPoolSize + ") - dropping replant at " + LocationUtil.describe(block) + " to prevent unbounded growth.");
            return;
        }

        int delay = clampDelay(delayTicks, block);
        int slot  = (cursor + delay) & WHEEL_MASK;
        int index = acquire();

        poolBlocks[index]    = block;
        poolMaterials[index] = material;
        poolMeta[index]      = packMeta(targetAge, cocoaFacing);
        poolNext[index]      = wheelHeads[slot];
        wheelHeads[slot]     = index;
        pendingCount++;
    }

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
                        "Abandoning replant at " + LocationUtil.describe(block) + " - chunk remained unloaded.");
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

    private boolean tryReplant(int index, Block block) {
        if (!isChunkLoaded(block)) return false;

        Material material = poolMaterials[index];
        if (material == null) return true;

        CropInfo info = ageMetaRegistry.get(material);
        if (info == null) {
            WarningThrottle.log(plugin, Level.WARNING, WarningThrottle.Category.AGE_DATA_MISSING,
                    "No age data found for plant: " + material + ", skipping replant at " + LocationUtil.describe(block));
            return true;
        }

        int metadata = poolMeta[index];
        int targetAge = metadata & AGE_MASK;
        int faceOrdinal = (metadata >>> FACE_SHIFT) & FACE_MASK;
        Location loc = block.getLocation();

        Runnable action = () -> {
            try {
                switch (info) {
                    case CocoaCropInfo cocoa -> replantCocoa(block, cocoa, targetAge, faceOrdinal);
                    case SimpleCropInfo simple -> replantNormal(block, simple, targetAge);
                    default -> {}
                }
            } catch (Exception e) {
                WarningThrottle.log(plugin, Level.WARNING, WarningThrottle.Category.REPLANT_FAILED,
                        "Failed to replant crop at " + LocationUtil.describe(block) + ": " + e.getMessage());
            }
        };

        if (plugin.getServer().isOwnedByCurrentRegion(loc)) {
            action.run();
        } else {
            plugin.getServer().getRegionScheduler().execute(plugin, loc, action);
        }
        return true;
    }

    private void replantNormal(Block block, SimpleCropInfo info, int targetAge) {
        if (!block.getType().isAir()) return;

        Material below = block.getRelative(BlockFace.DOWN).getType();
        if (info.requiresFarmland() && below != Material.FARMLAND)   return;
        if (info.requiresSoulSand() && below != Material.SOUL_SAND) return;

        block.setBlockData(info.stateFor(targetAge), false);
    }

    private void replantCocoa(Block block, CocoaCropInfo info, int targetAge, int faceOrdinal) {
        if (!block.getType().isAir()) return;

        BlockFace face  = AgeMetaRegistry.COCOA_FACES.get(faceOrdinal);
        Block attached = block.getRelative(face);
        if (!CropAnchors.JUNGLE_LOGS.contains(attached.getType())) return;

        block.setBlockData(info.stateFor(targetAge, faceOrdinal), false);
    }

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
            WarningThrottle.log(plugin, Level.WARNING, WarningThrottle.Category.DELAY_TRUNCATION,
                    "Replant delay truncation triggered for block at " + LocationUtil.describe(block));
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
        return 0;
    }

    private void primePool() {
        int size = Math.min(INITIAL_POOL_SIZE, maxPoolSize);
        poolBlocks    = new Block[size];
        poolMaterials = new Material[size];
        poolMeta      = new int[size];
        poolNext      = new int[size];
        for (int i = size - 1; i >= 0; i--) {
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
        pendingCount = 0;
    }

    private void growPool() {
        int oldSize = poolBlocks.length;
        int newSize = Math.min(oldSize << 1, maxPoolSize);
        if (newSize <= oldSize) {
            throw new IllegalStateException("Replant pool exhausted (max=" + maxPoolSize + ")");
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
        pendingCount--;
    }
}