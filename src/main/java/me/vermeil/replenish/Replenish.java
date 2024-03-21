package me.vermeil.replenish;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class Replenish extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onCropBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        switch (block.getType()) {
            case WHEAT:
            case POTATOES:
            case CARROTS:
            case NETHER_WART:
                Material seedMaterial = materialToSeed(block.getType());
                if (seedMaterial != null && event.getPlayer().getInventory().contains(seedMaterial)) {
                    event.setCancelled(true);
                    block.getWorld().dropItem(block.getLocation(), new ItemStack(seedMaterial));
                    block.setType(block.getType());
                    Ageable ageable = (Ageable) block.getBlockData();
                    ageable.setAge(0);
                    block.setBlockData(ageable);
                    event.getPlayer().getInventory().removeItem(new ItemStack(seedMaterial));
                }
                break;
            case COCOA:
                Directional directional = (Directional) block.getBlockData();
                BlockFace facing = directional.getFacing();
                if (facing != BlockFace.DOWN) {
                    Material cocoaSeed = Material.COCOA_BEANS;
                    if (event.getPlayer().getInventory().contains(cocoaSeed)) {
                        event.setCancelled(true);
                        block.getWorld().dropItem(block.getLocation(), new ItemStack(cocoaSeed));
                        block.setType(Material.COCOA);
                        Directional newDirectional = (Directional) block.getBlockData();
                        newDirectional.setFacing(facing);
                        block.setBlockData(newDirectional);
                        event.getPlayer().getInventory().removeItem(new ItemStack(cocoaSeed));
                    }
                }
                break;
            default:
                break;
        }
    }

    private Material materialToSeed(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case POTATOES -> Material.POTATO;
            case CARROTS -> Material.CARROT;
            case NETHER_WART -> Material.NETHER_WART;
            default -> null;
        };
    }
}
