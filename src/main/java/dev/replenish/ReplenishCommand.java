package dev.replenish;

import org.bukkit.Material;
import org.bukkit.command.*;

import java.util.*;

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
        return PREFIX
                + ARROW
                + "&cUnknown subcommand. &7Use &f/"
                + base
                + " &7for a list of commands.";
    }

    private boolean isDenied(CommandSender sender, String perm) {
        if (!sender.hasPermission(perm)) {
            send(
                    sender,
                    PREFIX
                            + ARROW
                            + "&cYou don't have permission to do that. &8(&7requires "
                            + perm
                            + "&8)");
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
            send(sender, "&e/replenish status &8- &7Shows current settings and enabled crops.");
            send(sender, "&e/replenish reload &8- &7Reloads config.yml without restarting.");
            send(sender, "&e/replenish toggle &8- &7Turns replanting on or off for everyone.");
            send(sender, "&e/replenish version &8- &7Shows version and update info.");
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
                String detail =
                        nowEnabled
                                ? "Crops will replant themselves again."
                                : "Crops will no longer replant. Harvests behave like vanilla.";
                sendPrefixed(sender, "Replenish is now " + state + "&7. " + detail);
                return true;
            }
            case "reload" -> {
                if (isDenied(sender, "replenish.reload")) return true;

                plugin.reloadLocalConfig();
                var cfg = plugin.getConfigCache();

                send(sender, "");
                send(sender, "&8&m      &8[ &e&lConfig Reloaded &8]&m      &r");
                send(sender, "");
                send(sender, "  " + DOT + "&7Replanting: " + (cfg.enabled ? "&aOn" : "&cOff"));
                send(
                        sender,
                        "  " + DOT + "&7Replant delay: &f" + cfg.replantDelayTicks + " tick(s)");
                send(
                        sender,
                        "  "
                                + DOT
                                + "&7Give drops directly to player: "
                                + (cfg.directPickup ? "&aYes" : "&cNo, drop on ground"));
                send(
                        sender,
                        "  "
                                + DOT
                                + "&7Require a seed to replant: "
                                + (cfg.requirePlayerSeed ? "&aYes" : "&cNo"));
                send(
                        sender,
                        "  "
                                + DOT
                                + "&7Sounds: &f"
                                + countEnabled(
                                        cfg.soundPickup,
                                        cfg.soundInventoryFull,
                                        cfg.soundDeniedTool,
                                        cfg.soundDeniedSeed)
                                + "/4 &7enabled");
                send(sender, "");
                send(sender, "&7Your config.yml changes are now live.");
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

                send(
                        sender,
                        (cfg.enabled
                                ? "&a✔ &fReplanting is active"
                                : "&c✘ &fReplanting is disabled"));
                send(
                        sender,
                        (cfg.requirePlayerSeed
                                ? "&a✔ &fPlayers must have a spare seed to replant"
                                : "&c✘ &fNo seed needed to replant"));
                send(
                        sender,
                        (cfg.directPickup
                                ? "&a✔ &fHarvested crops go straight to inventory"
                                : "&c✘ &fHarvested crops drop on the ground"));
                send(sender, "");

                send(sender, "&eTiming");
                send(
                        sender,
                        "  " + DOT + "&7Replants after: &f" + cfg.replantDelayTicks + " tick(s)");
                send(
                        sender,
                        "  " + DOT + "&7Replant limit: &f" + cfg.maxReplantsPerTick + " per tick");
                send(sender, "");

                send(sender, "&eCrops that auto-replant");
                send(sender, "");
                appendCropLine(sender, Material.WHEAT, "Wheat");
                appendCropLine(sender, Material.CARROTS, "Carrots");
                appendCropLine(sender, Material.POTATOES, "Potatoes");
                appendCropLine(sender, Material.NETHER_WART, "Nether Wart");
                appendCropLine(sender, Material.COCOA, "Cocoa");
                appendCropLine(sender, Material.BEETROOTS, "Beetroots");
                send(sender, "");

                send(sender, "&eSounds");
                appendSoundLine(sender, "Pickup", cfg.soundPickup);
                appendSoundLine(sender, "Inventory full", cfg.soundInventoryFull);
                appendSoundLine(sender, "Denied (tool)", cfg.soundDeniedTool);
                appendSoundLine(sender, "Denied (seed)", cfg.soundDeniedSeed);
                send(sender, "");

                send(sender, "&7Tip: &8/&7replenish reload &7after editing config.yml.");
                send(sender, "");
                send(sender, LINE);
                return true;
            }
            case "version" -> {
                if (isDenied(sender, "replenish.version")) return true;

                send(sender, "");
                send(sender, "&8&m      &8[ &e&lVersion Info &8]&m      &r");
                send(sender, "");

                UpdateChecker uc = plugin.getUpdateChecker();
                if (uc == null || !uc.isEnabled()) {
                    send(
                            sender,
                            "  "
                                    + DOT
                                    + "&7You're running: &fv"
                                    + plugin.getDescription().getVersion());
                    send(sender, "  " + DOT + "&7Update checks: &cDisabled in config.yml");
                } else if (!uc.isCheckCompleted()) {
                    send(
                            sender,
                            "  "
                                    + DOT
                                    + "&7You're running: &fv"
                                    + plugin.getDescription().getVersion());
                    send(
                            sender,
                            "  " + DOT + "&7Update check: &fStill checking, try again shortly");
                } else if (uc.isUpdateAvailable()) {
                    send(
                            sender,
                            "  "
                                    + DOT
                                    + "&eA new version is available! &8(&fv"
                                    + uc.getCurrentVersion()
                                    + " &7➟ &ev"
                                    + uc.getLatestVersion()
                                    + "&8)");
                    send(
                            sender,
                            "  "
                                    + DOT
                                    + "&7Get it here:"
                                    + " &bhttps://github.com/Mitra-88/Replenish/releases/latest");
                } else if (uc.isLocalNewer()) {
                    send(
                            sender,
                            "  "
                                    + DOT
                                    + "&dYou're on a development build &8(&fv"
                                    + uc.getCurrentVersion()
                                    + "&8, newer than the latest release &7v"
                                    + uc.getLatestVersion()
                                    + "&8)");
                } else {
                    send(
                            sender,
                            "  "
                                    + DOT
                                    + "&aYou're up to date! &8(&fv"
                                    + uc.getCurrentVersion()
                                    + "&8)");
                }

                send(sender, "");
                send(sender, "&7Server details");
                send(sender, "  " + DOT + "&7Server: &f" + plugin.getServer().getVersion());
                send(
                        sender,
                        "  "
                                + DOT
                                + "&7Java: &f"
                                + System.getProperty("java.version")
                                + " &8(&7"
                                + System.getProperty("java.vendor")
                                + "&8)");

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

    private void appendCropLine(CommandSender sender, Material material, String displayName) {
        boolean on = plugin.isCropEnabled(material);
        send(sender, "  " + (on ? "&a✔" : "&c✖") + " &7" + displayName);
    }

    private void appendSoundLine(CommandSender sender, String label, SoundEffect sound) {
        if (sound == null || !sound.enabled()) {
            send(sender, "  " + DOT + "&7" + label + ": &cDISABLED");
            return;
        }
        String soundName = sound.sound() != null ? sound.sound().name() : "UNKNOWN";
        send(
                sender,
                "  "
                        + DOT
                        + "&7"
                        + label
                        + ": &a"
                        + soundName
                        + " &8(&7vol &f"
                        + fmt(sound.volume())
                        + "&8, &7pitch &f"
                        + fmt(sound.pitch())
                        + "&8)");
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static int countEnabled(SoundEffect... sounds) {
        int n = 0;
        for (SoundEffect s : sounds) {
            if (s != null && s.enabled()) n++;
        }
        return n;
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
