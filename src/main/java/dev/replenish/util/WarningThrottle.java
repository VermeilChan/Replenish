package dev.replenish.util;

import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Suppresses repeated warnings of the same category within a short time window.
 * After the window expires, the suppressed count is logged, then the new message.
 */
public final class WarningThrottle {

    private static final long SUPPRESS_DURATION_MS = 3000L;
    private static final Map<String, State> STATES = new ConcurrentHashMap<>();

    private WarningThrottle() {}

    public enum Category {
        ABANDONED_REPLANT  ("abandoned_replant"),
        REPLANT_FAILED     ("replant_failed"),
        AGE_DATA_MISSING   ("age_data_missing"),
        DELAY_TRUNCATION   ("delay_truncation");

        private final String key;
        Category(String key) { this.key = key; }
        public String key() { return key; }
    }

    private static final class State {
        String lastMessage = "";
        long lastLogTime = 0;
        int suppressedCount = 0;
    }

    public static void log(Plugin plugin, Level level, Category category, String message) {
        State state = STATES.computeIfAbsent(category.key(), _ -> new State());
        synchronized (state) {
            long now = System.currentTimeMillis();
            if (now - state.lastLogTime < SUPPRESS_DURATION_MS) {
                state.suppressedCount++;
                return;
            }
            flushSuppressed(plugin, level, state);
            state.lastMessage = message;
            state.lastLogTime = now;
            plugin.getLogger().log(level, message);
        }
    }

    private static void flushSuppressed(Plugin plugin, Level level, State state) {
        if (state.suppressedCount > 0) {
            plugin.getLogger().log(level,
                    "Suppressed {0} warnings since last report: {1}",
                    new Object[] { state.suppressedCount, state.lastMessage });
            state.suppressedCount = 0;
        }
    }
}