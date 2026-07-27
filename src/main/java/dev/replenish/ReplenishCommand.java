package dev.replenish;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ReplenishCommand extends Command {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ReplenishPlugin plugin;

    private static final String PREFIX = "<dark_gray>[<yellow>Replenish<dark_gray>] <gray>";
    private static final String ARROW  = "<dark_gray>» ";
    private static final String DOT    = "<dark_gray>• ";
    private static final String LINE   = "<dark_gray><strikethrough>                                     ";

    public ReplenishCommand(ReplenishPlugin plugin) {
        super("replenish");
        this.plugin = plugin;

        this.setDescription("Replenish admin command");
        this.setUsage("/replenish <toggle|reload|status|version>");
        this.setPermission("replenish.use");
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(MM.deserialize(message));
    }

    private static void sendPrefixed(CommandSender sender, String message) {
        send(sender, PREFIX + message);
    }

    private static String usage(String base) {
        return PREFIX + ARROW
                + "<red>Unknown subcommand. <gray>Use <white>/" + base
                + " <gray>for a list of commands.";
    }

    private boolean isDenied(CommandSender sender, String perm) {
        if (!sender.hasPermission(perm)) {
            send(sender, PREFIX + ARROW
                    + "<red>You don't have permission to do that. "
                    + "<dark_gray>(<gray>requires " + perm + "<dark_gray>)");
            return true;
        }
        return false;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String [] args) {
        if (isDenied(sender, "replenish.use")) return true;

        if (args.length == 0) {
            sendMainMenu(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help"    -> handleHelp(sender);
            case "toggle"  -> handleToggle(sender);
            case "reload"  -> handleReload(sender);
            case "status"  -> handleStatus(sender);
            case "version" -> handleVersion(sender);
            default        -> send(sender, usage(label));
        }
        return true;
    }

    private void sendMainMenu(CommandSender sender) {
        send(sender, "");
        send(sender, "<dark_gray><strikethrough>      [ <yellow><bold>Replenish <gray>v"
                + plugin.getPluginMeta().getVersion()
                + " <dark_gray>]       <reset>");
        send(sender, "");
        send(sender, "<yellow>/replenish help <dark_gray>- <gray>Shows a detailed guide on how to use the plugin.");
        send(sender, "<yellow>/replenish status <dark_gray>- <gray>Shows current settings and enabled crops.");
        send(sender, "<yellow>/replenish reload <dark_gray>- <gray>Reloads config.yml without restarting.");
        send(sender, "<yellow>/replenish toggle <dark_gray>- <gray>Turns replanting on or off for everyone.");
        send(sender, "<yellow>/replenish version <dark_gray>- <gray>Shows version and update info.");
        send(sender, "");
        send(sender, LINE);
    }

    private void handleHelp(CommandSender sender) {
        if (isDenied(sender, "replenish.use")) return;

        send(sender, "");
        send(sender, "<dark_gray><strikethrough>      [ <yellow><bold>Replenish <gray>Help Guide <dark_gray>]       <reset>");
        send(sender, "");
        send(sender, "<yellow>How it works:");
        send(sender, "  " + DOT + "<gray>Use a <white>Hoe <gray>for normal crops, or an <white>Axe <gray>for Cocoa.");
        send(sender, "  " + DOT + "<gray>Break the crop, and it will auto-replant instantly.");
        send(sender, "  " + DOT + "<gray>If seeds are required, 1 seed is taken from your inventory.");
        send(sender, "");
        send(sender, "<yellow><bold>Pro Tip:");
        send(sender, "  " + DOT + "<gray>It is best to have at least <white>4x <gray>of the seed of the crop to");
        send(sender, "    <gray>avoid replanting it too fast and running out, making it think");
        send(sender, "    <gray>you don't have enough seeds!");
        send(sender, "");
        send(sender, "<yellow>Commands:");
        send(sender, "  " + DOT + "<white>/replenish status <dark_gray>- <gray>Shows current settings and enabled crops.");
        send(sender, "  " + DOT + "<white>/replenish reload <dark_gray>- <gray>Reloads config.yml without restarting.");
        send(sender, "  " + DOT + "<white>/replenish toggle <dark_gray>- <gray>Turns replanting on or off for everyone.");
        send(sender, "  " + DOT + "<white>/replenish version <dark_gray>- <gray>Shows version and update info.");
        send(sender, "");
        send(sender, LINE);
    }

    private void handleToggle(CommandSender sender) {
        if (isDenied(sender, "replenish.toggle")) return;

        boolean nowEnabled = !plugin.isEnabledGlobally();
        plugin.getConfig().set("enabled", nowEnabled);
        plugin.saveConfig();
        plugin.setGloballyEnabled(nowEnabled);

        String state  = nowEnabled ? "<green><bold>ENABLED" : "<red><bold>DISABLED";
        String detail = nowEnabled
                ? "Crops will replant themselves again."
                : "Crops will no longer replant. Harvests behave like vanilla.";
        sendPrefixed(sender, "Replenish is now " + state + "<gray>. " + detail);
    }

    private void handleReload(CommandSender sender) {
        if (isDenied(sender, "replenish.reload")) return;

        plugin.reloadLocalConfig();
        var cfg = plugin.getConfigCache();

        send(sender, "");
        send(sender, "<dark_gray><strikethrough>      [ <yellow><bold>Config Reloaded <dark_gray>]       <reset>");
        send(sender, "");
        send(sender, "  " + DOT + "<gray>Replanting: " + (cfg.enabled ? "<green>On" : "<red>Off"));
        send(sender, "  " + DOT + "<gray>Replant delay: <white>" + cfg.replantDelayTicks + " tick(s)");
        send(sender, "  " + DOT + "<gray>Give drops directly to player: "
                + (cfg.directPickup ? "<green>Yes" : "<red>No, drop on ground"));
        send(sender, "  " + DOT + "<gray>Require a seed to replant: "
                + (cfg.requirePlayerSeed ? "<green>Yes" : "<red>No"));
        send(sender, "  " + DOT + "<gray>Sounds: <white>" + countEnabled(
                cfg.soundPickup, cfg.soundInventoryFull,
                cfg.soundDeniedTool, cfg.soundDeniedSeed) + "/4 <gray>enabled");
        send(sender, "");
        send(sender, "<gray>Your config.yml changes are now live.");
        send(sender, "");
        send(sender, LINE);
    }

    private void handleStatus(CommandSender sender) {
        if (isDenied(sender, "replenish.status")) return;

        var cfg = plugin.getConfigCache();
        String version = plugin.getPluginMeta().getVersion();

        send(sender, "");
        send(sender, "<dark_gray><strikethrough>      [ <yellow><bold>Replenish <gray>v"
                + version + " <dark_gray>]       <reset>");
        send(sender, "");

        send(sender, cfg.enabled
                ? "<green>✔ <white>Replanting is active"
                : "<red>✘ <white>Replanting is disabled");
        send(sender, cfg.requirePlayerSeed
                ? "<green>✔ <white>Players must have a spare seed to replant"
                : "<red>✘ <white>No seed needed to replant");
        send(sender, cfg.directPickup
                ? "<green>✔ <white>Harvested crops go straight to inventory"
                : "<red>✘ <white>Harvested crops drop on the ground");
        send(sender, "");

        send(sender, "<yellow>Timing");
        send(sender, "  " + DOT + "<gray>Replants after: <white>" + cfg.replantDelayTicks + " tick(s)");
        send(sender, "  " + DOT + "<gray>Replant limit: <white>" + cfg.maxReplantsPerTick + " per tick");
        send(sender, "");

        send(sender, "<yellow>Crops that auto-replant");
        send(sender, "");
        appendCropLine(sender, Material.WHEAT,       "Wheat");
        appendCropLine(sender, Material.CARROTS,     "Carrots");
        appendCropLine(sender, Material.POTATOES,    "Potatoes");
        appendCropLine(sender, Material.NETHER_WART, "Nether Wart");
        appendCropLine(sender, Material.COCOA,       "Cocoa");
        appendCropLine(sender, Material.BEETROOTS,   "Beetroots");
        send(sender, "");

        send(sender, "<yellow>Sounds");
        appendSoundLine(sender, "Pickup",         cfg.soundPickup);
        appendSoundLine(sender, "Inventory full", cfg.soundInventoryFull);
        appendSoundLine(sender, "Denied (tool)",  cfg.soundDeniedTool);
        appendSoundLine(sender, "Denied (seed)",  cfg.soundDeniedSeed);
        send(sender, "");

        send(sender, "<gray>Tip: <dark_gray>/<gray>replenish reload <gray>after editing config.yml.");
        send(sender, "");
        send(sender, LINE);
    }

    private void handleVersion(CommandSender sender) {
        if (isDenied(sender, "replenish.version")) return;

        send(sender, "");
        send(sender, "<dark_gray><strikethrough>      [ <yellow><bold>Version Info <dark_gray>]       <reset>");
        send(sender, "");

        UpdateChecker uc = plugin.getUpdateChecker();
        if (uc == null || !uc.isEnabled()) {
            send(sender, "  " + DOT + "<gray>You're running: <white>v" + plugin.getPluginMeta().getVersion());
            send(sender, "  " + DOT + "<gray>Update checks: <red>Disabled in config.yml");
        } else if (!uc.isCheckCompleted()) {
            send(sender, "  " + DOT + "<gray>You're running: <white>v" + plugin.getPluginMeta().getVersion());
            send(sender, "  " + DOT + "<gray>Update check: <white>Still checking, try again shortly");
        } else if (uc.isUpdateAvailable()) {
            send(sender, "  " + DOT + "<yellow>A new version is available! <dark_gray>(<white>v"
                    + uc.getCurrentVersion() + " <gray>➟ <yellow>v" + uc.getLatestVersion() + "<dark_gray>)");
            send(sender, "  " + DOT + "<gray>Get it here: <aqua>https://github.com/Mitra-88/Replenish/releases/latest");
        } else if (uc.isLocalNewer()) {
            send(sender, "  " + DOT + "<light_purple>You're on a development build <dark_gray>(<white>v"
                    + uc.getCurrentVersion() + "<dark_gray>, newer than the latest release <gray>v"
                    + uc.getLatestVersion() + "<dark_gray>)");
        } else {
            send(sender, "  " + DOT + "<green>You're up to date! <dark_gray>(<white>v"
                    + uc.getCurrentVersion() + "<dark_gray>)");
        }

        send(sender, "");
        send(sender, "<gray>Server details");
        send(sender, "  " + DOT + "<gray>Server: <white>" + plugin.getServer().getVersion());
        send(sender, "  " + DOT + "<gray>Java: <white>" + System.getProperty("java.version")
                + " <dark_gray>(<gray>" + System.getProperty("java.vendor") + "<dark_gray>)");
        send(sender, "");
        send(sender, LINE);
    }

    private void appendCropLine(CommandSender sender, Material material, String displayName) {
        boolean on = plugin.isCropEnabled(material);
        send(sender, "  " + (on ? "<green>✔" : "<red>✖") + " <gray>" + displayName);
    }

    private void appendSoundLine(CommandSender sender, String label, SoundEffect sound) {
        if (sound == null || !sound.enabled()) {
            send(sender, "  " + DOT + "<gray>" + label + ": <red>DISABLED");
            return;
        }
        String soundName = (sound.sound() != null && Registry.SOUNDS.getKey(sound.sound()) != null)
                ? Objects.requireNonNull(Registry.SOUNDS.getKey(sound.sound())).asString()
                : "UNKNOWN";
        send(sender, "  " + DOT + "<gray>" + label + ": <green>" + soundName
                + " <dark_gray>(<gray>vol <white>" + fmt(sound.volume())
                + "<dark_gray>, <gray>pitch <white>" + fmt(sound.pitch()) + "<dark_gray>)");
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
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> allowed = new ArrayList<>(5);

            if (sender.hasPermission("replenish.use")     && "help".startsWith(prefix))    allowed.add("help");
            if (sender.hasPermission("replenish.status")  && "status".startsWith(prefix))  allowed.add("status");
            if (sender.hasPermission("replenish.toggle")  && "toggle".startsWith(prefix))  allowed.add("toggle");
            if (sender.hasPermission("replenish.reload")  && "reload".startsWith(prefix))  allowed.add("reload");
            if (sender.hasPermission("replenish.version") && "version".startsWith(prefix)) allowed.add("version");

            return allowed;
        }
        return Collections.emptyList();
    }
}