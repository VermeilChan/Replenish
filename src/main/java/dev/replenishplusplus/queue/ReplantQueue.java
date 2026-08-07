package dev.replenishplusplus.queue;

import dev.replenishplusplus.ReplenishPlusPlus;
import dev.replenishplusplus.crop.AgeMetaRegistry;
import dev.replenishplusplus.crop.CocoaCropInfo;
import dev.replenishplusplus.crop.CropAnchors;
import dev.replenishplusplus.crop.CropInfo;
import dev.replenishplusplus.crop.CropType;
import dev.replenishplusplus.crop.SimpleCropInfo;
import dev.replenishplusplus.util.LocationUtil;
import dev.replenishplusplus.util.WarningThrottle;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.UUID;
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
    private static final int SEED_FLAG_SHIFT = 18;
    private static final int SEED_FLAG_MASK  = 0x1;

    private final ReplenishPlusPlus plugin;
    private final AgeMetaRegistry ageMetaRegistry;
    private final int maxPerTick;
    private final int maxPoolSize;

    private final int[] wheelHeads = new int[WHEEL_SIZE];

    private Block[]    poolBlocks;
    private Material[] poolMaterials;
    private int[]      poolMeta;
    private int[]      poolNext;
    private UUID[]     poolPlayerIds;

    private int freeHead = -1;
    private int cursor   = 0;
    private int pendingCount = 0;
    private ScheduledTask scheduledTask;
    private volatile boolean started = false;

    public ReplantQueue(ReplenishPlusPlus plugin, int maxPerTick, int maxPoolSize, AgeMetaRegistry ageMetaRegistry) {
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
        scheduledTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, _ -> tick(), 1L, 1L);
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

    public synchronized int flush() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
        started = false;

        int flushed = 0;
        for (int slot = 0; slot < WHEEL_SIZE; slot++) {
            int head = wheelHeads[slot];
            if (head == -1) continue;
            wheelHeads[slot] = -1;

            while (head != -1) {
                int next = poolNext[head];
                poolNext[head] = -1;

                Block block = poolBlocks[head];
                if (block != null) {
                    try {
                        if (tryReplant(head, block)) {
                            flushed++;
                        } else {
                            handleFailureForUnloadedChunk(poolMaterials[head], poolPlayerIds[head], seedWasConsumed(head));
                        }
                    } catch (Exception e) {
                        WarningThrottle.log(plugin, Level.WARNING, WarningThrottle.Category.REPLANT_FAILED,
                                "Flush error at " + LocationUtil.describe(block) + ": " + e.getMessage());
                        handleReplantFailure(block, poolMaterials[head], poolPlayerIds[head], seedWasConsumed(head));
                    }
                }
                release(head);
                head = next;
            }
        }
        return flushed;
    }

    public synchronized QueueStats getStats() {
        return new QueueStats(pendingCount, maxPoolSize, poolBlocks.length);
    }

    public synchronized int pendingCount() {
        return pendingCount;
    }

    public synchronized void enqueue(
            Block block, Material material, int delayTicks,
            int targetAge, BlockFace cocoaFacing, UUID playerId, boolean seedConsumed) {

        try {
            if (pendingCount >= maxPoolSize) {
                WarningThrottle.log(plugin, Level.WARNING, WarningThrottle.Category.QUEUE_BACKPRESSURE,
                        "Replant queue is full (" + pendingCount + "/" + maxPoolSize + "). Dropping replant at " + LocationUtil.describe(block) + " to prevent server lag.");
                return;
            }

            int delay = clampDelay(delayTicks, block);
            int slot  = (cursor + delay) & WHEEL_MASK;
            int index = acquire();

            poolBlocks[index]    = block;
            poolMaterials[index] = material;
            poolMeta[index]      = packMeta(targetAge, cocoaFacing, seedConsumed);
            poolPlayerIds[index] = playerId;
            poolNext[index]      = wheelHeads[slot];
            wheelHeads[slot]     = index;
            pendingCount++;
        } catch (IllegalStateException e) {
            WarningThrottle.log(plugin, Level.WARNING, WarningThrottle.Category.QUEUE_BACKPRESSURE,
                    "Replant queue ran out of memory slots. Dropping replant at " + LocationUtil.describe(block) + ".");
        }
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

            boolean success;
            try {
                success = tryReplant(head, block);
            } catch (Exception e) {
                WarningThrottle.log(plugin, Level.WARNING, WarningThrottle.Category.REPLANT_FAILED,
                        "Tick processing error at " + LocationUtil.describe(block) + ": " + e.getMessage());
                success = true;
            }

            if (success) {
                release(head);
                processed++;
            } else if (retryCount(head) >= MAX_UNLOAD_RETRIES) {
                WarningThrottle.log(plugin, Level.WARNING, WarningThrottle.Category.ABANDONED_REPLANT,
                        "Abandoning replant at " + LocationUtil.describe(block) + " - chunk remained unloaded.");
                handleFailureForUnloadedChunk(poolMaterials[head], poolPlayerIds[head], seedWasConsumed(head));
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
        UUID playerId = poolPlayerIds[index];
        boolean seedConsumed = seedWasConsumed(index);
        Location loc = block.getLocation();

        Runnable action = () -> {
            try {
                boolean success;
                switch (info) {
                    case CocoaCropInfo cocoa -> success = replantCocoa(block, cocoa, targetAge, faceOrdinal);
                    case SimpleCropInfo simple -> success = replantNormal(block, simple, targetAge);
                    default -> success = true;
                }
                if (!success) {
                    handleReplantFailure(block, material, playerId, seedConsumed);
                }
            } catch (Exception e) {
                WarningThrottle.log(plugin, Level.WARNING, WarningThrottle.Category.REPLANT_FAILED,
                        "Failed to replant crop at " + LocationUtil.describe(block) + ": " + e.getMessage());
                handleReplantFailure(block, material, playerId, seedConsumed);
            }
        };

        if (plugin.getServer().isOwnedByCurrentRegion(loc)) {
            action.run();
        } else {
            plugin.getServer().getRegionScheduler().execute(plugin, loc, action);
        }
        return true;
    }

    private boolean replantNormal(Block block, SimpleCropInfo info, int targetAge) {
        if (!block.getType().isAir()) {
            return false;
        }

        Material below = block.getRelative(BlockFace.DOWN).getType();
        if (info.requiresFarmland() && below != Material.FARMLAND) {
            return false;
        }
        if (info.requiresSoulSand() && below != Material.SOUL_SAND) {
            return false;
        }

        block.setBlockData(info.stateFor(targetAge), false);
        return true;
    }

    private boolean replantCocoa(Block block, CocoaCropInfo info, int targetAge, int faceOrdinal) {
        if (!block.getType().isAir()) {
            return false;
        }

        BlockFace face  = AgeMetaRegistry.COCOA_FACES.get(faceOrdinal);
        Block attached = block.getRelative(face);
        if (!CropAnchors.JUNGLE_LOGS.contains(attached.getType())) {
            return false;
        }

        block.setBlockData(info.stateFor(targetAge, faceOrdinal), false);
        return true;
    }

    private void handleReplantFailure(Block block, Material cropMaterial, UUID playerId, boolean seedConsumed) {
        if (seedConsumed) {
            CropType crop = CropType.fromMaterial(cropMaterial);
            if (crop != null) {
                block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(crop.seed()));
            }
        }

        if (playerId != null) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.getScheduler().execute(plugin, () -> plugin.getConfigCache().replantFailedSound().play(player), null, 1L);
            }
        }
    }

    private void handleFailureForUnloadedChunk(Material cropMaterial, UUID playerId, boolean seedConsumed) {
        if (playerId == null) return;
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        player.getScheduler().execute(plugin, () -> {
            plugin.getConfigCache().replantFailedSound().play(player);

            if (seedConsumed) {
                CropType crop = CropType.fromMaterial(cropMaterial);
                if (crop != null) {
                    Location playerLoc = player.getLocation();
                    player.getWorld().dropItemNaturally(playerLoc, new ItemStack(crop.seed()));
                }
            }
        }, null, 1L);
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

    private static int packMeta(int targetAge, BlockFace face, boolean seedConsumed) {
        int safeAge = Math.max(0, targetAge) & AGE_MASK;
        int faceOrdinal = faceToOrdinal(face) & FACE_MASK;
        int seedFlag = seedConsumed ? 1 : 0;
        return safeAge | (faceOrdinal << FACE_SHIFT) | (seedFlag << SEED_FLAG_SHIFT);
    }

    private boolean seedWasConsumed(int index) {
        return ((poolMeta[index] >>> SEED_FLAG_SHIFT) & SEED_FLAG_MASK) == 1;
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
        poolPlayerIds = new UUID[size];
        for (int i = size - 1; i >= 0; i--) {
            poolNext[i] = freeHead;
            freeHead    = i;
        }
    }

    private void resetPool() {
        freeHead = -1;
        for (int i = poolBlocks.length - 1; i >= 0; i--) {
            poolBlocks[i]    = null;
            poolMaterials[i] = null;
            poolMeta[i]      = 0;
            poolPlayerIds[i] = null;
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
        poolPlayerIds = Arrays.copyOf(poolPlayerIds, newSize);

        for (int i = newSize - 1; i >= oldSize; i--) {
            poolBlocks[i]    = null;
            poolMaterials[i] = null;
            poolMeta[i]      = 0;
            poolPlayerIds[i] = null;
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
        poolPlayerIds[index] = null;
        poolNext[index]      = freeHead;
        freeHead             = index;
        pendingCount--;
    }
}