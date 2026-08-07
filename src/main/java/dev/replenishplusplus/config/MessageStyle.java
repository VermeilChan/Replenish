package dev.replenishplusplus.config;

import java.util.Locale;

public enum MessageStyle {
    CHAT,
    ACTION_BAR,
    NONE;

    public static MessageStyle from(String value) {
        if (value == null) return CHAT;
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "ACTION_BAR" -> ACTION_BAR;
            case "NONE"       -> NONE;
            default           -> CHAT;
        };
    }
}