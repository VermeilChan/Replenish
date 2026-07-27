package dev.replenish.util;

import java.util.Locale;

/**
 * Text formatting helpers.
 */
public final class TextUtil {

    private TextUtil() {}

    /** Converts an enum-style name (e.g. "NETHER_WART") to title case ("Nether Wart"). */
    public static String prettyName(String enumName) {
        String raw = enumName.replace('_', ' ').toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(raw.length());
        boolean capitalizeNext = true;
        for (char c : raw.toCharArray()) {
            if (capitalizeNext && Character.isLetter(c)) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
            if (c == ' ') capitalizeNext = true;
        }
        return result.toString();
    }
}