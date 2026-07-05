package dev.replenish;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.logging.Level;

public final class ReplantQueue {

    private static final int TIME_WHEEL_BITS = 13;
    private static final int TIME_WHEEL_SIZE = 1 << TIME_WHEEL_BITS;
    private static final int TIME_WHEEL_MASK = TIME_WHEEL_SIZE - 1;
    private static final int INITIAL_POOL_SIZE = 1 << 14;

    private static final int AGE_MASK = 0xFF;
    private static final int FACE_SHIFT = 8;
    private static final int FACE_MASK = 0x3;
    private static final int RETRY_SHIFT = 10;
    private static final int RETRY_MASK = 0xFF;
    private static final int MAX_UNLOAD_RETRIES = 20;

    private Block[] poolBlocks;
    private Material[] poolMaterials;
    private int[] poolMeta;
    private int[] poolNext;

    private final int[] wheelHeads = new int[TIME_WHEEL_SIZE];
    private int freeHead = -1;

    private final Plugin plugin;
    private final AgeMetaRegistry ageMetaRegistry;
    private final int maxPerTick;

    private int cursor = 0;
    private int taskId = -1;
    private volatile boolean started = false;

    public ReplantQueue(Plugin plugin, int maxPerTick, AgeMetaRegistry ageMetaRegistry) {
        this.plugin = plugin;
        this.ageMetaRegistry = ageMetaRegistry;
        this.maxPerTick = Math.max(256, maxPerTick);

        Arrays.fill(wheelHeads, -1);
        primePool();
    }

    public synchronized void start() {
        if (started) return;
        started = true;
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 1L, 1L);
    }

    public synchronized void stop() {
        if (!started) return;
        started = false;
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;

        freeHead = -1;
        for (int i = poolBlocks.length - 1; i >= 0; i--) {
            poolBlocks[i] = null;
            poolMaterials[i] = null;
            poolMeta[i] = 0;
            poolNext[i] = freeHead;
            freeHead = i;
        }
        Arrays.fill(wheelHeads, -1);
        cursor = 0;
    }

    public synchronized int getPendingCount() {
        int count = 0;
        for (int i = 0; i < TIME_WHEEL_SIZE; i++) {
            int head = wheelHeads[i];
            while (head != -1) {
                count++;
                head = poolNext[head];
            }
        }
        return count;
    }

    public synchronized void enqueue(
            Block block,
            Material plantMaterial,
            int delayTicks,
            int targetAge,
            BlockFace cocoaFacingDirection) {
        int delay = Math.max(1, delayTicks);
        if (delay >= TIME_WHEEL_SIZE) {
            WarningThrottle.log(
                    plugin,
                    Level.WARNING,
                    WarningThrottle.CAT_DELAY_TRUNCATION,
                    "Replant delay truncation triggered for block at " + locString(block));
            delay = TIME_WHEEL_SIZE - 1;
        }
        int slot = (cursor + delay) & TIME_WHEEL_MASK;
        int index = acquire();

        poolBlocks[index] = block;
        poolMaterials[index] = plantMaterial;

        int safeAge = Math.max(0, targetAge) & AGE_MASK;
        int faceOrd = faceToOrdinal(cocoaFacingDirection) & FACE_MASK;
        poolMeta[index] = safeAge | (faceOrd << FACE_SHIFT);

        poolNext[index] = wheelHeads[slot];
        wheelHeads[slot] = index;
    }

    private synchronized void tick() {
        if (!started) return;

        int head = wheelHeads[cursor];
        if (head == -1) {
            cursor = (cursor + 1) & TIME_WHEEL_MASK;
            return;
        }
        wheelHeads[cursor] = -1;

        int processed = 0;
        int deferredHead = -1;
        int deferredTail = -1;

        while (head != -1) {
            int next = poolNext[head];
            poolNext[head] = -1;

            if (processed >= maxPerTick) {
                if (deferredHead == -1) deferredHead = head;
                else poolNext[deferredTail] = head;
                deferredTail = head;
                head = next;
                continue;
            }

            Block b = poolBlocks[head];
            if (b == null) {
                release(head);
                head = next;
                continue;
            }

            World world = b.getWorld();
            int chunkX = b.getX() >> 4;
            int chunkZ = b.getZ() >> 4;

            boolean loaded;
            try {
                loaded = world.isChunkLoaded(chunkX, chunkZ);
            } catch (Exception e) {
                loaded = false;
            }

            if (loaded) {
                try {
                    Material plant = poolMaterials[head];
                    if (plant != null) {
                        AgeMetaRegistry.CropInfo info = ageMetaRegistry.get(plant);
                        if (info == null) {
                            WarningThrottle.log(
                                    plugin,
                                    Level.WARNING,
                                    WarningThrottle.CAT_AGE_DATA_MISSING,
                                    "No age data found for plant: "
                                            + plant
                                            + ", skipping replant at "
                                            + locString(b));
                        } else if (info.isCocoa) {
                            replantCocoa(head, info);
                        } else {
                            replantNormal(head, info);
                        }
                    }
                } catch (Exception e) {
                    WarningThrottle.log(
                            plugin,
                            Level.WARNING,
                            WarningThrottle.CAT_REPLANT_FAIL,
                            "Failed to replant crop at " + locString(b) + ": " + e.getMessage());
                }
                release(head);
                processed++;
            } else {
                int retries = (poolMeta[head] >>> RETRY_SHIFT) & RETRY_MASK;
                if (retries >= MAX_UNLOAD_RETRIES) {
                    WarningThrottle.log(
                            plugin,
                            Level.WARNING,
                            WarningThrottle.CAT_ABANDONED,
                            "Abandoning replant at "
                                    + locString(b)
                                    + " - chunk remained unloaded.");
                    release(head);
                    processed++;
                } else {
                    poolMeta[head] =
                            (poolMeta[head] & ~(RETRY_MASK << RETRY_SHIFT))
                                    | ((retries + 1) << RETRY_SHIFT);
                    if (deferredHead == -1) deferredHead = head;
                    else poolNext[deferredTail] = head;
                    deferredTail = head;
                }
            }
            head = next;
        }

        int nextSlot = (cursor + 1) & TIME_WHEEL_MASK;
        if (deferredHead != -1) {
            poolNext[deferredTail] = wheelHeads[nextSlot];
            wheelHeads[nextSlot] = deferredHead;
        }

        cursor = nextSlot;
    }

    private void replantNormal(int index, AgeMetaRegistry.CropInfo info) {
        Block block = poolBlocks[index];
        if (block == null || !block.getType().isAir()) return;

        Material plant = poolMaterials[index];
        if (plant == null) return;

        int metadata = poolMeta[index];
        int targetAge = metadata & AGE_MASK;

        Material below = block.getRelative(BlockFace.DOWN).getType();
        if (info.requiresFarmland && below != Material.FARMLAND) return;
        if (info.requiresSoulSand && below != Material.SOUL_SAND) return;

        BlockData data = info.getBlockData(targetAge);
        block.setBlockData(data, false);
    }

    private void replantCocoa(int index, AgeMetaRegistry.CropInfo info) {
        Block block = poolBlocks[index];
        if (block == null || !block.getType().isAir()) return;

        Material plant = poolMaterials[index];
        if (plant != Material.COCOA) return;

        int metadata = poolMeta[index];
        int targetAge = metadata & AGE_MASK;
        int faceOrd = (metadata >>> FACE_SHIFT) & FACE_MASK;

        BlockFace face = ordinalToFace(faceOrd);
        Block attached = block.getRelative(face);
        if (!CropConstants.JUNGLE_ANCHOR_BLOCKS.contains(attached.getType())) {
            return;
        }

        BlockData data = info.getCocoaBlockData(targetAge, faceOrd);
        block.setBlockData(data, false);
    }

    private String locString(Block b) {
        if (b == null) return "null_block";
        World w = b.getWorld();
        String worldName = w.getName();
        return worldName + ":" + b.getX() + "," + b.getY() + "," + b.getZ();
    }

    private void primePool() {
        int size = INITIAL_POOL_SIZE;
        poolBlocks = new Block[size];
        poolMaterials = new Material[size];
        poolMeta = new int[size];
        poolNext = new int[size];

        for (int i = size - 1; i >= 0; i--) {
            poolNext[i] = freeHead;
            freeHead = i;
        }
    }

    private void growPool() {
        int oldSize = poolBlocks.length;
        int newSize = oldSize << 1;
        if (newSize <= oldSize) throw new IllegalStateException("Pool size overflow");

        poolBlocks = Arrays.copyOf(poolBlocks, newSize);
        poolMaterials = Arrays.copyOf(poolMaterials, newSize);
        poolMeta = Arrays.copyOf(poolMeta, newSize);
        poolNext = Arrays.copyOf(poolNext, newSize);

        for (int i = newSize - 1; i >= oldSize; i--) {
            poolBlocks[i] = null;
            poolMaterials[i] = null;
            poolMeta[i] = 0;
            poolNext[i] = freeHead;
            freeHead = i;
        }
    }

    private int acquire() {
        if (freeHead == -1) growPool();
        int index = freeHead;
        freeHead = poolNext[index];
        poolNext[index] = -1;
        return index;
    }

    private void release(int index) {
        poolBlocks[index] = null;
        poolMaterials[index] = null;
        poolMeta[index] = 0;
        poolNext[index] = freeHead;
        freeHead = index;
    }

    private static int faceToOrdinal(BlockFace face) {
        if (face == null) return 0;
        if (face == BlockFace.EAST) return 1;
        if (face == BlockFace.SOUTH) return 2;
        if (face == BlockFace.WEST) return 3;
        return 0;
    }

    private static BlockFace ordinalToFace(int ord) {
        return AgeMetaRegistry.CocoaFaces.FACES[ord & 3];
    }
}
