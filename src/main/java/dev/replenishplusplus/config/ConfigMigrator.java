package dev.replenishplusplus.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigMigrator {

    public static final int CURRENT_VERSION = 7;

    private static final String CONFIG_FILE = "config.yml";
    private static final Pattern KEY_PATTERN = Pattern.compile("^(\\s*)([A-Za-z0-9_-]+):(.*)$");
    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("^(\\s*)-.*$");
    private static final Pattern TRAILING_SPACES = Pattern.compile("(\\s+)$");

    private final Plugin plugin;
    private final FileConfiguration userConfig;
    private final File configFile;

    public ConfigMigrator(Plugin plugin, FileConfiguration userConfig) {
        this.plugin = plugin;
        this.userConfig = userConfig;
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
    }

    public boolean migrate() {
        int from = readVersion();
        if (from >= CURRENT_VERSION) return false;

        plugin.getLogger().info("Migrating config.yml from v" + from + " to v" + CURRENT_VERSION + "...");

        if (!createBackup()) {
            plugin.getLogger().severe("Could not back up config.yml — aborting migration to prevent data loss.");
            return false;
        }

        if (from < 6) {
            userConfig.set("messages", null);
        }
        userConfig.set("config-version", CURRENT_VERSION);

        List<String> template = readResourceLines();
        if (template.isEmpty()) {
            plugin.getLogger().severe("Default config.yml not found inside the jar — cannot migrate.");
            return false;
        }

        List<String> output = generateMigratedLines(template);

        try {
            writeAtomically(output);
            plugin.getLogger().info("Migration complete.");
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to write migrated config.yml", e);
            return false;
        }
    }

    private List<String> generateMigratedLines(List<String> template) {
        List<String> output = new ArrayList<>();
        Set<String> processed = new HashSet<>();
        Deque<String> pathStack = new ArrayDeque<>();
        Deque<Integer> indentStack = new ArrayDeque<>();

        for (int i = 0; i < template.size(); i++) {
            String line = template.get(i);

            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                output.add(line);
                continue;
            }

            Matcher m = KEY_PATTERN.matcher(line);
            if (!m.matches()) {
                output.add(line);
                continue;
            }

            String indent = m.group(1);
            String key = m.group(2);
            String rest = m.group(3);

            int indentLevel = indent.length();
            while (!indentStack.isEmpty() && indentStack.peek() >= indentLevel) {
                indentStack.pop();
                pathStack.pop();
            }
            String fullPath = pathStack.isEmpty() ? key : pathStack.peek() + "." + key;
            processed.add(fullPath);

            if (rest.trim().isEmpty()) {
                boolean hasBlockList = i + 1 < template.size()
                        && isListItemAt(template.get(i + 1), indentLevel);
                if (hasBlockList) {
                    i = handleBlockList(output, template, i, fullPath, indentLevel);
                } else {
                    indentStack.push(indentLevel);
                    pathStack.push(fullPath);
                    output.add(line);
                }
            } else {
                handleInlineLeaf(output, line, indent, key, rest, fullPath);
            }
        }

        appendUnknownKeys(output, processed);
        return output;
    }

    private int handleBlockList(List<String> output, List<String> template,
                                int i, String fullPath, int indentLevel) {
        output.add(template.get(i)); // "key:"

        List<String> templateItems = new ArrayList<>();
        int j = i + 1;
        while (j < template.size() && isListItemAt(template.get(j), indentLevel)) {
            templateItems.add(template.get(j));
            j++;
        }

        List<?> userList = userConfig.isList(fullPath) ? userConfig.getList(fullPath) : null;
        if (userList == null) {
            output.addAll(templateItems);
            return i + templateItems.size();
        }

        if (listsMatch(userList, templateItems)) {
            output.addAll(templateItems);
        } else {
            String itemIndent = templateItems.isEmpty()
                    ? " ".repeat(indentLevel + 2)
                    : extractIndent(templateItems.getFirst());
            for (Object item : userList) {
                output.add(itemIndent + "- " + toYamlString(item));
            }
        }

        return i + templateItems.size();
    }

    private void handleInlineLeaf(List<String> output, String line, String indent,
                                  String key, String rest, String fullPath) {
        if (!userConfig.contains(fullPath, true)) {
            output.add(line); // Use template default
            return;
        }

        String userValStr = toYamlString(userConfig.get(fullPath));
        String comment = extractComment(rest);

        if (comment == null) {
            output.add(indent + key + ": " + userValStr);
            return;
        }

        String beforeComment = rest.substring(0, rest.indexOf(comment));
        String defaultVal = beforeComment.trim();
        String originalSpaces = trailingSpaces(beforeComment);

        int spacesNeeded = Math.max(1,
                defaultVal.length() + originalSpaces.length() - userValStr.length());

        output.add(indent + key + ": " + userValStr + " ".repeat(spacesNeeded) + comment);
    }

    private void appendUnknownKeys(List<String> output, Set<String> processed) {
        YamlConfiguration unknown = new YamlConfiguration();
        for (String key : userConfig.getKeys(true)) {
            if (userConfig.isConfigurationSection(key)) continue;
            if (processed.contains(key)) continue;
            if (hasKnownParent(key, processed)) continue;
            unknown.set(key, userConfig.get(key));
        }

        String yaml = unknown.saveToString();
        if (!yaml.trim().isEmpty()) {
            output.add("");
            output.add("# --------------------------");
            output.add("# Unknown / legacy settings");
            output.add("# (removed from default config or no longer used)");
            output.add("# --------------------------");
            output.add(yaml.trim());
        }
    }

    private static boolean isListItemAt(String line, int minIndent) {
        Matcher m = LIST_ITEM_PATTERN.matcher(line);
        return m.matches() && m.group(1).length() > minIndent;
    }

    private static boolean listsMatch(List<?> userList, List<String> templateItems) {
        if (userList.size() != templateItems.size()) return false;
        for (int k = 0; k < userList.size(); k++) {
            String templateVal = extractListValue(templateItems.get(k));
            if (!userList.get(k).toString().equalsIgnoreCase(templateVal)) return false;
        }
        return true;
    }

    private static String extractListValue(String templateLine) {
        String s = templateLine;
        int hash = s.indexOf('#');
        if (hash >= 0) s = s.substring(0, hash);
        s = s.trim();
        if (s.startsWith("-")) s = s.substring(1).trim();
        return s;
    }

    private static String extractIndent(String line) {
        int dash = line.indexOf('-');
        return dash >= 0 ? line.substring(0, dash) : "";
    }

    private static String extractComment(String rest) {
        boolean inSingle = false, inDouble = false;
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == '"' && !inSingle) inDouble = !inDouble;
            else if (c == '#' && !inSingle && !inDouble
                    && i > 0 && (rest.charAt(i - 1) == ' ' || rest.charAt(i - 1) == '\t')) {
                return rest.substring(i);
            }
        }
        return null;
    }

    private static String trailingSpaces(String s) {
        Matcher m = TRAILING_SPACES.matcher(s);
        return m.find() ? m.group(1) : "";
    }

    private static boolean hasKnownParent(String key, Set<String> known) {
        String parent = key;
        while (parent.contains(".")) {
            parent = parent.substring(0, parent.lastIndexOf('.'));
            if (known.contains(parent)) return true;
        }
        return false;
    }

    private static String toYamlString(Object val) {
        if (val == null) return "";
        if (val instanceof List<?> list) {
            if (list.isEmpty()) return "[]";
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(toYamlString(list.get(i)));
            }
            return sb.append("]").toString();
        }

        YamlConfiguration dummy = new YamlConfiguration();
        dummy.set("v", val);
        String saved = dummy.saveToString();
        int idx = saved.indexOf(':');
        return idx == -1 ? val.toString() : saved.substring(idx + 1).trim();
    }

    private List<String> readResourceLines() {
        try (InputStream stream = plugin.getResource(CONFIG_FILE)) {
            if (stream == null) return List.of();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines().toList();
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read " + CONFIG_FILE + " from jar", e);
            return List.of();
        }
    }

    private boolean createBackup() {
        if (!configFile.exists()) return true;
        File backup = new File(plugin.getDataFolder(), "config.yml.bak");
        int n = 1;
        while (backup.exists()) {
            backup = new File(plugin.getDataFolder(), "config.yml.bak" + n++);
        }
        try {
            Files.copy(configFile.toPath(), backup.toPath());
            plugin.getLogger().info("Backup saved: " + backup.getName());
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to back up config.yml", e);
            return false;
        }
    }

    private void writeAtomically(List<String> lines) throws IOException {
        File temp = new File(plugin.getDataFolder(), "config.yml.tmp");
        try {
            Files.write(temp.toPath(), lines, StandardCharsets.UTF_8);
            try {
                Files.move(temp.toPath(), configFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), configFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(temp.toPath());
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not delete temporary file: " + temp.getName(), e);
            }
        }
    }

    private int readVersion() {
        if (!userConfig.contains("config-version", true)) return 0;
        Object val = userConfig.get("config-version");
        if (val instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}