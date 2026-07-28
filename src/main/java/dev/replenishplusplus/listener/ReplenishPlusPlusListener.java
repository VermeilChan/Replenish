package dev.replenishplusplus.listener;

import dev.replenishplusplus.ReplenishPlusPlus;
import dev.replenishplusplus.config.ConfigCache;
import dev.replenishplusplus.crop.AgeMetaRegistry;
import dev.replenishplusplus.crop.CropAnchors;
import dev.replenishplusplus.crop.CropInfo;
import dev.replenishplusplus.crop.CropType;
import dev.replenishplusplus.util.DropPickupManager;
import dev.replenishplusplus.util.LocationUtil;
import dev.replenishplusplus.util.SeedIndex;
import dev.replenishplusplus.util.TextUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
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

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class ReplenishPlusPlusListener implements Listener {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final long INVALIDATION_COOLDOWN_MS = 50L;
    private static final long MESSAGE_COOLDOWN_MS      = 2000L;

    private static final BlockFace[] HORIZONTAL_FACES = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    private static final Set<Material> SEED_TYPES;
    static {
        Set<Material> seeds = EnumSet.noneOf(Material.class);
        for (CropType crop : CropType.values()) {
            seeds.add(crop.seed());
        }
        SEED_TYPES = Collections.unmodifiableSet(seeds);
    }

    private final ReplenishPlusPlus plugin;
    private final AgeMetaRegistry ageMetaRegistry;
    private final Map<UUID, Long> lastInvalidation = new ConcurrentHashMap<>();
    private final Map<UUID, Long> messageCooldown  = new ConcurrentHashMap<>();

    public ReplenishPlusPlusListener(ReplenishPlusPlus plugin, AgeMetaRegistry ageMetaRegistry) {
        this.plugin = plugin;
        this.ageMetaRegistry = ageMetaRegistry;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastInvalidation.remove(uuid);
        messageCooldown.remove(uuid);
        SeedIndex.invalidate(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            invalidateWithCooldown(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (isRelevantSeed(event.getOldCursor()) || isRelevantSeed(event.getCursor())) {
            invalidateWithCooldown(player);
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (isRelevantSeed(event.getMainHandItem()) || isRelevantSeed(event.getOffHandItem())) {
            invalidateWithCooldown(event.getPlayer());
        }
    }

    @EventHandler
    public void onPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isRelevantSeed(event.getItem().getItemStack())) return;
        invalidateWithCooldown(player);
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        if (isRelevantSeed(event.getItemDrop().getItemStack())) {
            invalidateWithCooldown(event.getPlayer());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && isRelevantSeed(event.getItem())) {
            invalidateWithCooldown(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        try {
            handleBlockBreak(event);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Unexpected error during crop break handling for player " + event.getPlayer().getName() + " at " + LocationUtil.describe(event.getBlock()), e);
        }
    }

    private void handleBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (isInNonSurvivalMode(player) || player.isSneaking()) return;

        ConfigCache config = plugin.getConfigCache();
        if (!config.enabled()) return;

        Block block = event.getBlock();
        CropType crop = CropType.fromMaterial(block.getType());
        if (crop == null || !plugin.isCropEnabled(crop)) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!crop.requiredTool().matches(tool.getType())) {
            notifyWrongTool(player, config, crop);
            return;
        }

        if (!hasValidAnchor(block, crop)) return;

        BlockData blockData = block.getBlockData();
        if (!(blockData instanceof Ageable ageable)) return;

        CropInfo info = ageMetaRegistry.get(crop.material());
        if (info == null || info.maximumAge() <= 0) return;

        int originalAge = ageable.getAge();
        boolean wasMature = originalAge >= info.maximumAge();
        int replantedAge = wasMature ? 0 : originalAge;

        if (wasMature && config.requirePlayerSeed()) {
            if (!consumeSeed(player, config, crop)) return;
        }

        event.setDropItems(false);
        Collection<ItemStack> drops = wasMature ? block.getDrops(tool, player) : Collections.emptyList();

        distributeDrops(player, block, config, drops);
        scheduleReplant(player, block, crop, config, replantedAge, blockData);
    }

    private boolean isInNonSurvivalMode(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR || mode == GameMode.ADVENTURE;
    }

    private boolean hasValidAnchor(Block block, CropType crop) {
        if (crop.isCocoa()) {
            return findAdjacentJungle(block) != null;
        }
        Material below = block.getRelative(BlockFace.DOWN).getType();
        if (crop.isNetherWart()) return below == Material.SOUL_SAND;
        return below == Material.FARMLAND;
    }

    private void notifyWrongTool(Player player, ConfigCache config, CropType crop) {
        if (!canSendMessage(player)) return;
        String message = config.requiresToolMessage()
                .replace("{crop}", TextUtil.prettyName(crop.material().name()))
                .replace("{tool}", crop.requiredTool().displayName());
        player.sendMessage(MINI_MESSAGE.deserialize(message));
        config.deniedToolSound().play(player);
    }

    private boolean consumeSeed(Player player, ConfigCache config, CropType crop) {
        Material seed = crop.seed();
        if (seed == null) return false;
        if (SeedIndex.consume(player, seed)) return true;

        if (canSendMessage(player)) {
            String message = config.needSeedMessage()
                    .replace("{count}", "1")
                    .replace("{seed}", TextUtil.prettyName(seed.name()));
            player.sendMessage(MINI_MESSAGE.deserialize(message));
            config.deniedSeedSound().play(player);
        }
        return false;
    }

    private void distributeDrops(
            Player player, Block block, ConfigCache config, Collection<ItemStack> drops) {
        if (drops.isEmpty()) return;

        Location dropLocation = LocationUtil.centerOf(block.getLocation());
        if (config.directPickup()) {
            DropPickupManager.giveOrDrop(
                    player, dropLocation, drops,
                    config.inventoryFullMessage(),
                    config.pickupSound(),
                    config.inventoryFullSound());
            return;
        }
        World world = block.getWorld();
        for (ItemStack drop : drops) {
            if (drop == null || drop.getAmount() <= 0) continue;
            world.dropItemNaturally(dropLocation, drop);
        }
    }

    private void scheduleReplant(
            Player player, Block block, CropType crop, ConfigCache config,
            int replantedAge, BlockData originalData) {

        int delay = Math.max(1, config.replantDelayTicks());
        if (crop.isCocoa()) {
            BlockFace facing = determineCocoaFacing(block, originalData, player);
            if (facing != null) {
                plugin.enqueueReplant(block, Material.COCOA, delay, replantedAge, facing);
            }
        } else {
            plugin.enqueueReplant(block, crop.material(), delay, replantedAge, null);
        }
    }

    private BlockFace determineCocoaFacing(Block block, BlockData originalData, Player player) {
        BlockFace originalFacing = originalData instanceof Directional directional
                ? directional.getFacing()
                : null;

        if (originalFacing != null && isJungle(block.getRelative(originalFacing).getType())) {
            return originalFacing;
        }

        BlockFace playerFacing = getPlayerHorizontalFace(player);
        if (playerFacing != null && isJungle(block.getRelative(playerFacing).getType())) {
            return playerFacing;
        }

        return findAdjacentJungle(block);
    }

    private BlockFace getPlayerHorizontalFace(Player player) {
        float yaw = player.getLocation().getYaw();
        float normalized = (yaw % 360 + 360) % 360;
        if (normalized < 45  || normalized >= 315) return BlockFace.SOUTH;
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
        return CropAnchors.JUNGLE_LOGS.contains(material);
    }

    private boolean isRelevantSeed(ItemStack item) {
        return item != null && SEED_TYPES.contains(item.getType());
    }

    private void invalidateWithCooldown(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastInvalidation.get(player.getUniqueId());
        if (last == null || now - last >= INVALIDATION_COOLDOWN_MS) {
            lastInvalidation.put(player.getUniqueId(), now);
            SeedIndex.invalidate(player);
        }
    }

    private boolean canSendMessage(Player player) {
        long now = System.currentTimeMillis();
        Long last = messageCooldown.get(player.getUniqueId());
        if (last == null || now - last >= MESSAGE_COOLDOWN_MS) {
            messageCooldown.put(player.getUniqueId(), now);
            return true;
        }
        return false;
    }
}