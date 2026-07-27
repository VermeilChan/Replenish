package dev.replenish.crop;

/**
 * Precomputed BlockData states for a crop, indexed by age.
 * Sealed interface: implementations differ between simple crops
 * (wheat, carrots, etc.) and cocoa (which also tracks facing direction).
 */
public sealed interface CropInfo permits SimpleCropInfo, CocoaCropInfo {
    int maximumAge();
}