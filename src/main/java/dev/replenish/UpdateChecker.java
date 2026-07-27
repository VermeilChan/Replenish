package dev.replenish;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("UnstableApiUsage")
public final class UpdateChecker {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final String API_URL      = "https://api.github.com/repos/Mitra-88/Replenish/releases/latest";
    private static final String RELEASES_URL = "https://github.com/Mitra-88/Replenish/releases/latest";

    private static final Pattern TAG_PATTERN =
            Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private static final String PREFIX = "<dark_gray>[<yellow>Replenish<dark_gray>] <dark_gray>» <gray>";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36";

    private final String  currentVersion;
    private final boolean enabled;

    private volatile String  latestVersion   = "Unknown";
    private volatile boolean updateAvailable = false;
    private volatile boolean checkCompleted  = false;

    public UpdateChecker(Plugin plugin, boolean enabled) {
        this.enabled = enabled;
        this.currentVersion = normalize(plugin.getPluginMeta().getVersion());
    }

    public void check() {
        if (!enabled) return;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofSeconds(4))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(this::handleResponse)
                .exceptionally(e -> {
                    console(PREFIX + "<red>Update check failed: " + e.getMessage());
                    return null;
                });
    }

    private void handleResponse(HttpResponse<String> response) {
        if (response.statusCode() == 403) {
            console(PREFIX + "<red>Update check failed: GitHub API rate-limited.");
            return;
        }
        if (response.statusCode() == 404) {
            console(PREFIX + "<red>Update check failed: No releases found on GitHub.");
            return;
        }
        if (response.statusCode() != 200) {
            console(PREFIX + "<red>Update check failed: HTTP " + response.statusCode());
            return;
        }

        Matcher matcher = TAG_PATTERN.matcher(response.body());
        if (matcher.find()) {
            latestVersion   = normalize(matcher.group(1));
            updateAvailable = isNewer(currentVersion, latestVersion);
            checkCompleted  = true;
            logResult();
        } else {
            console(PREFIX + "<red>Update check failed: Malformed GitHub response.");
        }
    }

    private void logResult() {
        if (updateAvailable) {
            console(PREFIX + "<gray>Update available: <yellow>" + latestVersion
                    + " <gray>(you're on <white>" + currentVersion + "<gray>).");
            console(PREFIX + "<gray>Download: <aqua>" + RELEASES_URL);
        } else if (isLocalNewer(currentVersion, latestVersion)) {
            console(PREFIX + "<gray>Update Status: <light_purple>Running unreleased/dev build "
                    + "<dark_gray>(<white>" + currentVersion + "<dark_gray>)");
        } else {
            console(PREFIX + "<gray>Update Status: <green>Up to date "
                    + "<dark_gray>(<white>" + currentVersion + "<dark_gray>)");
        }
    }

    public boolean isCheckCompleted()  { return checkCompleted; }
    public boolean isUpdateAvailable() { return updateAvailable; }
    public String  getCurrentVersion() { return currentVersion; }
    public String  getLatestVersion()  { return latestVersion; }
    public boolean isEnabled()         { return enabled; }

    public boolean isLocalNewer() {
        if (!checkCompleted || latestVersion.equals("Unknown")) return false;
        return compareVersions(currentVersion, latestVersion) > 0;
    }

    private void console(String message) {
        Bukkit.getConsoleSender().sendMessage(MM.deserialize(message));
    }

    private static String normalize(String version) {
        if (version == null) return "";
        String v = version.trim();
        while (!v.isEmpty() && (v.charAt(0) == 'v' || v.charAt(0) == 'V'))
            v = v.substring(1);
        return v.split("[-+]", 2)[0];
    }

    private static boolean isNewer(String current, String latest) {
        return compareVersions(current, latest) < 0;
    }

    private static boolean isLocalNewer(String current, String latest) {
        if (latest.equals("Unknown")) return false;
        return compareVersions(current, latest) > 0;
    }

    private static int compareVersions(String v1, String v2) {
        if (v1.equals(v2)) return 0;
        String[] c = v1.split("\\.");
        String[] l = v2.split("\\.");
        int len = Math.max(c.length, l.length);
        for (int i = 0; i < len; i++) {
            int cv = i < c.length ? parseSafe(c[i]) : 0;
            int lv = i < l.length ? parseSafe(l[i]) : 0;
            if (cv > lv) return 1;
            if (cv < lv) return -1;
        }
        return 0;
    }

    private static int parseSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
