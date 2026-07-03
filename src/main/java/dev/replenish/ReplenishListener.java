package dev.replenish;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class ReplenishListener implements Listener {

    private final ReplenishPlugin plugin;
    private final Map<Material, Integer> maxAges;
    private final Map<UUID, Long> lastInvalidation;
    private final Map<UUID, Long> messageCooldown;

    private static final long INVALIDATION_COOLDOWN_MS = 50L;
    private static final long MESSAGE_COOLDOWN_MS = 2000L;

    private static final Set<Material> SUPPORTED_CROPS =
            EnumSet.of(
                    Material.WHEAT,
                    Material.CARROTS,
                    Material.POTATOES,
                    Material.NETHER_WART,
                    Material.COCOA,
                    Material.BEETROOTS);

    private static final Set<Material> SEED_TYPES =
            EnumSet.of(
                    Material.WHEAT_SEEDS,
                    Material.CARROT,
                    Material.POTATO,
                    Material.NETHER_WART,
                    Material.COCOA_BEANS,
                    Material.BEETROOT_SEEDS);

    private static final BlockFace[] HORIZONTAL_FACES = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    private static final EnumSet<Material> HOE_TOOLS =
            EnumSet.of(
                    Material.WOODEN_HOE,
                    Material.STONE_HOE,
                    Material.COPPER_HOE,
                    Material.IRON_HOE,
                    Material.GOLDEN_HOE,
                    Material.DIAMOND_HOE,
                    Material.NETHERITE_HOE);
    private static final EnumSet<Material> AXE_TOOLS =
            EnumSet.of(
                    Material.WOODEN_AXE,
                    Material.STONE_AXE,
                    Material.COPPER_AXE,
                    Material.IRON_AXE,
                    Material.GOLDEN_AXE,
                    Material.DIAMOND_AXE,
                    Material.NETHERITE_AXE);

    public ReplenishListener(ReplenishPlugin plugin, AgeMetaRegistry ageMetaRegistry) {
        this.plugin = plugin;
        this.lastInvalidation = new HashMap<>();
        this.messageCooldown = new HashMap<>();

        this.maxAges = new EnumMap<>(Material.class);
        for (Material crop : SUPPORTED_CROPS) {
            AgeMetaRegistry.AgeMeta meta = ageMetaRegistry.get(crop);
            if (meta != null) {
                maxAges.put(crop, meta.maximumAge);
            } else {
                maxAges.put(crop, 0);
                plugin.getLogger()
                        .warning(
                                "Missing AgeMeta for " + crop + ", replant may not work correctly");
            }
        }
    }

    private boolean isRelevantSeed(ItemStack item) {
        return item != null && SEED_TYPES.contains(item.getType());
    }

    private void invalidateWithCooldown(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastInvalidation.get(player.getUniqueId());
        if (last == null || (now - last) >= INVALIDATION_COOLDOWN_MS) {
            lastInvalidation.put(player.getUniqueId(), now);
            SeedIndex.invalidate(player);
        }
    }

    private boolean canSendMessage(Player player) {
        long now = System.currentTimeMillis();
        Long last = messageCooldown.get(player.getUniqueId());
        if (last == null || (now - last) >= MESSAGE_COOLDOWN_MS) {
            messageCooldown.put(player.getUniqueId(), now);
            return true;
        }
        return false;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastInvalidation.remove(event.getPlayer().getUniqueId());
        messageCooldown.remove(event.getPlayer().getUniqueId());
        SeedIndex.invalidate(event.getPlayer());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) invalidateWithCooldown(player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (isRelevantSeed(event.getOldCursor()) || isRelevantSeed(event.getCursor()))
                invalidateWithCooldown(player);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isRelevantSeed(event.getMainHandItem()) || isRelevantSeed(event.getOffHandItem()))
            invalidateWithCooldown(event.getPlayer());
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (isRelevantSeed(event.getItem().getItemStack())) {
                plugin.getServer()
                        .getScheduler()
                        .runTask(
                                plugin,
                                () -> {
                                    if (player.isOnline()) invalidateWithCooldown(player);
                                });
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isRelevantSeed(event.getItemDrop().getItemStack()))
            invalidateWithCooldown(event.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && isRelevantSeed(event.getItem()))
            invalidateWithCooldown(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)
            return;
        if (player.isSneaking()) return;

        ReplenishPlugin.ConfigCache config = plugin.getConfigCache();
        if (!config.enabled) return;

        Block block = event.getBlock();
        Material cropType = block.getType();

        if (!SUPPORTED_CROPS.contains(cropType) || !plugin.isCropEnabled(cropType)) return;

        ItemStack toolInHand = player.getInventory().getItemInMainHand();
        Material toolInHandType = toolInHand.getType();

        boolean hasRequiredTool = false;
        if (cropType == Material.COCOA) {
            hasRequiredTool = AXE_TOOLS.contains(toolInHandType);
        } else if (cropType == Material.WHEAT
                || cropType == Material.CARROTS
                || cropType == Material.POTATOES
                || cropType == Material.NETHER_WART
                || cropType == Material.BEETROOTS) {
            hasRequiredTool = HOE_TOOLS.contains(toolInHandType);
        }

        if (!hasRequiredTool) {
            if (canSendMessage(player)) {
                String cropName = cropType.name().replace("_", " ");
                cropName =
                        cropName.substring(0, 1).toUpperCase()
                                + cropName.substring(1).toLowerCase();
                String toolName = cropType == Material.COCOA ? "Axe" : "Hoe";
                String msg =
                        config.msgRequiresTool
                                .replace("{crop}", cropName)
                                .replace("{tool}", toolName);
                player.sendMessage(ColorUtils.color(msg));
                player.playSound(
                        player.getLocation(),
                        Sound.ENTITY_VILLAGER_NO,
                        SoundCategory.PLAYERS,
                        1.0f,
                        1.0f);
            }
            return;
        }

        if (cropType == Material.COCOA) {
            if (findAdjacentJungle(block) == null) return;
        } else {
            Material belowType = block.getRelative(BlockFace.DOWN).getType();
            if (cropType == Material.NETHER_WART) {
                if (belowType != Material.SOUL_SAND) return;
            } else if (belowType != Material.FARMLAND) {
                return;
            }
        }

        BlockData originalBlockData = block.getBlockData();
        if (!(originalBlockData instanceof Ageable ageable)) return;

        int maxAge = maxAges.getOrDefault(cropType, 0);
        if (maxAge <= 0) return;

        int originalAge = ageable.getAge();
        boolean wasMature = originalAge >= maxAge;
        int replantedAge = wasMature ? 0 : originalAge;

        Material seedMaterial = seedFor(cropType);

        if (wasMature && config.requirePlayerSeed) {
            if (seedMaterial == null) return;
            if (!SeedIndex.consume(player, seedMaterial)) {
                if (canSendMessage(player)) {
                    String seedName = seedMaterial.name().replace("_", " ");
                    seedName =
                            seedName.substring(0, 1).toUpperCase()
                                    + seedName.substring(1).toLowerCase();
                    String msg = config.msgNeedSeed.replace("{seed}", seedName);
                    player.sendMessage(ColorUtils.color(msg));
                    player.playSound(
                            player.getLocation(),
                            Sound.ENTITY_VILLAGER_NO,
                            SoundCategory.PLAYERS,
                            1.0f,
                            1.0f);
                }
                return;
            }
        }

        event.setDropItems(false);
        Collection<ItemStack> drops =
                wasMature ? block.getDrops(toolInHand, player) : Collections.emptyList();

        BlockFace originalCocoaFacing = null;
        if (cropType == Material.COCOA && originalBlockData instanceof Directional directional) {
            originalCocoaFacing = directional.getFacing();
        }

        if (!drops.isEmpty()) {
            Location dropLocation = DropPickupManager.centeredDropLocation(block.getLocation());
            if (config.directPickup) {
                DropPickupManager.giveToPlayerOrDrop(
                        player, dropLocation, drops, config.msgInventoryFull);
            } else {
                World world = block.getWorld();
                for (ItemStack drop : drops) {
                    if (drop == null || drop.getAmount() <= 0) continue;
                    world.dropItemNaturally(dropLocation, drop);
                }
            }
        }

        int delay = Math.max(1, config.replantDelayTicks);
        if (cropType == Material.COCOA) {
            BlockFace chosenFacing = determineCocoaFacing(block, originalCocoaFacing, player);
            if (chosenFacing != null) {
                plugin.enqueueReplant(block, Material.COCOA, delay, replantedAge, chosenFacing);
            }
        } else {
            plugin.enqueueReplant(block, cropType, delay, replantedAge, null);
        }
    }

    private BlockFace determineCocoaFacing(Block block, BlockFace originalFacing, Player player) {
        if (originalFacing != null && isJungle(block.getRelative(originalFacing).getType()))
            return originalFacing;
        BlockFace horizontalFacing = getPlayerHorizontalFace(player);
        if (horizontalFacing != null && isJungle(block.getRelative(horizontalFacing).getType()))
            return horizontalFacing;
        return findAdjacentJungle(block);
    }

    private BlockFace getPlayerHorizontalFace(Player player) {
        float yaw = player.getLocation().getYaw();
        float normalized = (yaw % 360 + 360) % 360;
        if (normalized < 45 || normalized >= 315) return BlockFace.SOUTH;
        if (normalized < 135) return BlockFace.WEST;
        if (normalized < 225) return BlockFace.NORTH;
        return BlockFace.EAST;
    }

    private BlockFace findAdjacentJungle(Block block) {
        for (BlockFace face : HORIZONTAL_FACES) {
            if (isJungle(block.getRelative(face).getType())) return face;
        }
        return null;
    }

    private boolean isJungle(Material material) {
        return CropConstants.JUNGLE_ANCHOR_BLOCKS.contains(material);
    }

    private static Material seedFor(Material crop) {
        if (crop == Material.WHEAT) return Material.WHEAT_SEEDS;
        if (crop == Material.CARROTS) return Material.CARROT;
        if (crop == Material.POTATOES) return Material.POTATO;
        if (crop == Material.NETHER_WART) return Material.NETHER_WART;
        if (crop == Material.COCOA) return Material.COCOA_BEANS;
        if (crop == Material.BEETROOTS) return Material.BEETROOT_SEEDS;
        return null;
    }
}
