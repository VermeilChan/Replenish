package dev.replenishplusplus.crop;

import org.bukkit.Material;

public enum HarvestTool {
    HOE,
    AXE;

    public boolean matches(Material material) {
        if (material == null) return false;
        return switch (this) {
            case HOE -> material.name().endsWith("_HOE");
            case AXE -> material.name().endsWith("_AXE");
        };
    }

    public String displayName() {
        return switch (this) {
            case HOE -> "Hoe";
            case AXE -> "Axe";
        };
    }
}