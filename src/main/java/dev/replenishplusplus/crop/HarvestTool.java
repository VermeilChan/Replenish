package dev.replenishplusplus.crop;

import org.bukkit.Material;
import java.util.EnumSet;
import java.util.Set;

public enum HarvestTool {
    HOE,
    AXE;

    private static final Set<Material> HOES = EnumSet.of(
            Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE,
            Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE,
            Material.COPPER_HOE
    );
    private static final Set<Material> AXES = EnumSet.of(
            Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
            Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE,
            Material.COPPER_AXE
    );

    public boolean matches(Material material) {
        if (material == null) return false;
        return switch (this) {
            case HOE -> HOES.contains(material);
            case AXE -> AXES.contains(material);
        };
    }

    public String displayName() {
        return switch (this) {
            case HOE -> "Hoe";
            case AXE -> "Axe";
        };
    }
}