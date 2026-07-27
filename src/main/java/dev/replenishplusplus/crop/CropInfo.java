package dev.replenishplusplus.crop;

public sealed interface CropInfo permits SimpleCropInfo, CocoaCropInfo {
    int maximumAge();
}