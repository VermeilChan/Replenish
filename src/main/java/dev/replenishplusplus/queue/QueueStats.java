package dev.replenishplusplus.queue;

public record QueueStats(int pendingCount, int maxPoolSize, int currentPoolSize) {}