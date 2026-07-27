package dev.replenish;

import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public final class WarningThrottle {

    private static final long SUPPRESS_DURATION_MS = 3000L;

    private static final Map<String, State> STATES = new HashMap<>();

    private static class State {
        String lastCategory;
        String lastFormattedMessage;
        long lastLogTime;
        int suppressedCount;
    }

    public static synchronized void log(
            Plugin plugin, Level level, String category, String formattedMessage) {
        State state = STATES.computeIfAbsent(category, _ -> new State());

        long now = System.currentTimeMillis();
        if (category.equals(state.lastCategory)) {
            if (now - state.lastLogTime < SUPPRESS_DURATION_MS) {
                state.suppressedCount++;
            } else {
                if (state.suppressedCount > 0) {
                    plugin.getLogger()
                            .log(
                                    level,
                                    "Suppressed {0} identical warnings: {1}",
                                    new Object[] {
                                        state.suppressedCount, state.lastFormattedMessage
                                    });
                }
                state.lastCategory = category;
                state.lastFormattedMessage = formattedMessage;
                state.lastLogTime = now;
                state.suppressedCount = 0;
                plugin.getLogger().log(level, formattedMessage);
            }
        } else {
            if (state.suppressedCount > 0) {
                plugin.getLogger()
                        .log(
                                level,
                                "Suppressed {0} identical warnings: {1}",
                                new Object[] {state.suppressedCount, state.lastFormattedMessage});
            }
            state.lastCategory = category;
            state.lastFormattedMessage = formattedMessage;
            state.lastLogTime = now;
            state.suppressedCount = 0;
            plugin.getLogger().log(level, formattedMessage);
        }
    }

    public static final String CAT_ABANDONED = "abandoned_replant";
    public static final String CAT_REPLANT_FAIL = "replant_failed";
    public static final String CAT_AGE_DATA_MISSING = "age_data_missing";
    public static final String CAT_DELAY_TRUNCATION = "delay_truncation";
}
