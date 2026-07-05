package dev.replenish;

import org.bukkit.Material;
import org.bukkit.command.*;

import java.util.*;

@SuppressWarnings("NullableProblems")
public class ReplenishCommand implements CommandExecutor, TabCompleter {
    private final ReplenishPlugin plugin;

    private static final String PREFIX = "&8[&eReplenish&8] &7";
    private static final String ARROW = "&8» ";
    private static final String DOT = "&8• ";
    private static final String LINE = "&8&m                                   ";

    public ReplenishCommand(ReplenishPlugin plugin) {
        this.plugin = plugin;
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(ColorUtils.color(message));
    }

    private static void sendPrefixed(CommandSender sender, String message) {
        send(sender, PREFIX + message);
    }

    private static String usage(String base) {
        return PREFIX + ARROW + "&cUsage: &7/" + base + " &f<status | toggle | reload | version>";
    }

    private boolean isDenied(CommandSender sender, String perm) {
        if (!sender.hasPermission(perm)) {
            send(sender, PREFIX + ARROW + "&cPermission denied. &8(&7" + perm + "&8)");
            return true;
        }
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            send(sender, "");
            send(
                    sender,
                    "&8&m      &8[ &e&lReplenish &7v"
                            + plugin.getDescription().getVersion()
                            + " &8]&m      &r");
            send(sender, "");
            send(sender, "&e/replenish status &8- &7Shows plugin information.");
            send(sender, "&e/replenish reload &8- &7Reload configuration.");
            send(sender, "&e/replenish toggle &8- &7Enable or disable the plugin.");
            send(sender, "&e/replenish version &8- &7Shows version information.");
            send(sender, "");
            send(sender, LINE);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "toggle" -> {
                if (isDenied(sender, "replenish.toggle")) return true;

                boolean nowEnabled = !plugin.isEnabledGlobally();
                plugin.getConfig().set("enabled", nowEnabled);
                plugin.saveConfig();

                plugin.setGloballyEnabled(nowEnabled);

                String state = nowEnabled ? "&a&lENABLED" : "&c&lDISABLED";
                sendPrefixed(sender, "Global replenish is now " + state + "&7.");
                return true;
            }
            case "reload" -> {
                if (isDenied(sender, "replenish.reload")) return true;

                plugin.reloadLocalConfig();
                var cfg = plugin.getConfigCache();

                send(sender, "");
                send(sender, "&8&m      &8[ &e&lReload Complete &8]&m      &r");
                send(sender, "");
                send(sender, "  " + DOT + "&7Enabled: " + (cfg.enabled ? "&atrue" : "&cfalse"));
                send(sender, "  " + DOT + "&7Delay: &f" + cfg.replantDelayTicks + " tick");
                send(
                        sender,
                        "  "
                                + DOT
                                + "&7Direct Pickup: "
                                + (cfg.directPickup ? "&atrue" : "&cfalse"));
                send(
                        sender,
                        "  "
                                + DOT
                                + "&7Seeds Required: "
                                + (cfg.requirePlayerSeed ? "&atrue" : "&cfalse"));
                send(sender, "");
                send(sender, LINE);
                return true;
            }
            case "status" -> {
                if (isDenied(sender, "replenish.status")) return true;

                var cfg = plugin.getConfigCache();
                String version = plugin.getDescription().getVersion();

                send(sender, "");
                send(sender, "&8&m      &8[ &e&lReplenish &7v" + version + " &8]&m      &r");
                send(sender, "");

                send(sender, (cfg.enabled ? "&a✔ &fPlugin Enabled" : "&c✘ &fPlugin Disabled"));
                send(
                        sender,
                        (cfg.requirePlayerSeed ? "&a✔ &fRequire Seeds" : "&c✘ &fRequire Seeds"));
                send(sender, (cfg.directPickup ? "&a✔ &fDirect Pickup" : "&c✘ &fDirect Pickup"));
                send(sender, "");

                send(sender, "&eReplant Delay: &f" + cfg.replantDelayTicks + " tick");
                send(sender, "&eQueue Limit: &f" + cfg.maxReplantsPerTick + "/tick");
                send(sender, "");

                send(sender, "&eSupported Crops");
                send(sender, "");
                send(
                        sender,
                        "  " + (plugin.isCropEnabled(Material.WHEAT) ? "&a✔" : "&c✖") + " &7Wheat");
                send(
                        sender,
                        "  "
                                + (plugin.isCropEnabled(Material.CARROTS) ? "&a✔" : "&c✖")
                                + " &7Carrots");
                send(
                        sender,
                        "  "
                                + (plugin.isCropEnabled(Material.POTATOES) ? "&a✔" : "&c✖")
                                + " &7Potatoes");
                send(
                        sender,
                        "  "
                                + (plugin.isCropEnabled(Material.NETHER_WART) ? "&a✔" : "&c✖")
                                + " &7Nether Wart");
                send(
                        sender,
                        "  " + (plugin.isCropEnabled(Material.COCOA) ? "&a✔" : "&c✖") + " &7Cocoa");
                send(
                        sender,
                        "  "
                                + (plugin.isCropEnabled(Material.BEETROOTS) ? "&a✔" : "&c✖")
                                + " &7Beetroots");
                send(sender, "");

                send(sender, LINE);
                return true;
            }
            case "version" -> {
                if (isDenied(sender, "replenish.version")) return true;

                send(sender, "");
                send(sender, "&8&m      &8[ &e&lVersion Info &8]&m      &r");
                send(sender, "");
                send(
                        sender,
                        "  " + DOT + "&7Plugin Version: &f" + plugin.getDescription().getVersion());
                send(sender, "  " + DOT + "&7Server Version: &f" + plugin.getServer().getVersion());
                send(
                        sender,
                        "  "
                                + DOT
                                + "&7Java Version: &f"
                                + System.getProperty("java.version")
                                + " &7("
                                + System.getProperty("java.vendor")
                                + " "
                                + System.getProperty("java.vm.name")
                                + ")");

                UpdateChecker uc = plugin.getUpdateChecker();
                if (uc == null || !uc.isEnabled()) {
                    send(sender, "  " + DOT + "&7Update Check: &cDisabled");
                    send(sender, "  " + DOT + "&7Build Type: &eUnknown");
                } else if (!uc.isCheckCompleted()) {
                    send(sender, "  " + DOT + "&7Update Check: &fChecking...");
                    send(sender, "  " + DOT + "&7Build Type: &eUnknown");
                } else if (uc.isUpdateAvailable()) {
                    send(
                            sender,
                            "  "
                                    + DOT
                                    + "&7Update Status: &eUpdate available &8(&f"
                                    + uc.getCurrentVersion()
                                    + " &7➟ &e"
                                    + uc.getLatestVersion()
                                    + "&8)");
                    send(
                            sender,
                            "  "
                                    + DOT
                                    + "&7Download:"
                                    + " &bhttps://github.com/Mitra-88/Replenish/releases/latest");
                    send(sender, "  " + DOT + "&7Build Type: &cStable (Outdated)");
                } else if (uc.isLocalNewer()) {
                    send(
                            sender,
                            "  "
                                    + DOT
                                    + "&7Update Status: &dDev Build &8(&f"
                                    + uc.getCurrentVersion()
                                    + " &7➟ newer than &e"
                                    + uc.getLatestVersion()
                                    + "&8)");
                    send(sender, "  " + DOT + "&7Build Type: &dDevelopment Build");
                } else {
                    send(
                            sender,
                            "  "
                                    + DOT
                                    + "&7Update Status: &aUp to date &8(&f"
                                    + uc.getCurrentVersion()
                                    + "&8)");
                    send(sender, "  " + DOT + "&7Build Type: &aStable Release");
                }

                send(sender, "");
                send(sender, LINE);
                return true;
            }
            default -> {
                send(sender, usage(label));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> allowed = new ArrayList<>(4);

            if (sender.hasPermission("replenish.status") && "status".startsWith(prefix)) {
                allowed.add("status");
            }
            if (sender.hasPermission("replenish.toggle") && "toggle".startsWith(prefix)) {
                allowed.add("toggle");
            }
            if (sender.hasPermission("replenish.reload") && "reload".startsWith(prefix)) {
                allowed.add("reload");
            }
            if (sender.hasPermission("replenish.version") && "version".startsWith(prefix)) {
                allowed.add("version");
            }

            return allowed;
        }
        return Collections.emptyList();
    }
}
