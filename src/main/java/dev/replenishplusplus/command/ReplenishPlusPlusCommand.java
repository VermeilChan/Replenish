package dev.replenishplusplus.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.replenishplusplus.ReplenishPlusPlus;
import dev.replenishplusplus.config.ConfigCache;
import dev.replenishplusplus.config.Messages;
import dev.replenishplusplus.config.SoundEffect;
import dev.replenishplusplus.crop.CropType;
import dev.replenishplusplus.update.UpdateChecker;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Registry;
import org.bukkit.command.CommandSender;

import java.util.Locale;
import java.util.function.Consumer;

public final class ReplenishPlusPlusCommand {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private final ReplenishPlusPlus plugin;

    public ReplenishPlusPlusCommand(ReplenishPlusPlus plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("replenishplusplus")
                .executes(ctx -> execute(ctx.getSource(), "replenishplusplus.use", this::sendMainMenu))
                .then(Commands.literal("help")
                        .executes(ctx -> execute(ctx.getSource(), "replenishplusplus.use", this::sendHelp)))
                .then(Commands.literal("status")
                        .executes(ctx -> execute(ctx.getSource(), "replenishplusplus.status", this::sendStatus)))
                .then(Commands.literal("reload")
                        .executes(ctx -> execute(ctx.getSource(), "replenishplusplus.reload", this::handleReload)))
                .then(Commands.literal("toggle")
                        .executes(ctx -> execute(ctx.getSource(), "replenishplusplus.toggle", this::handleToggle)))
                .then(Commands.literal("version")
                        .executes(ctx -> execute(ctx.getSource(), "replenishplusplus.version", this::sendVersion)));

        commands.register(root.build(), "ReplenishPlusPlus admin command");
    }

    private int execute(CommandSourceStack source, String permission, Consumer<CommandSender> action) {
        CommandSender sender = source.getSender();
        if (!sender.hasPermission(permission)) {
            send(sender, Messages.PREFIX + Messages.ARROW
                    + "<red>You don't have permission to do that. " + "<dark_gray>(<gray>requires " + permission + "<dark_gray>)");
            return 0;
        }
        action.accept(sender);
        return Command.SINGLE_SUCCESS;
    }

    private void sendMainMenu(CommandSender sender) {
        String version = plugin.getPluginMeta().getVersion();
        send(sender, "");
        send(sender, "<dark_gray>      [ <yellow><bold>ReplenishPlusPlus <gray>v"
                + version + " <dark_gray>]       <reset>");
        send(sender, "");
        send(sender, "<yellow>/replenishplusplus help <dark_gray>- <gray>Shows a detailed guide on how to use the plugin.");
        send(sender, "<yellow>/replenishplusplus status <dark_gray>- <gray>Shows current settings and enabled crops.");
        send(sender, "<yellow>/replenishplusplus reload <dark_gray>- <gray>Reloads config.yml without restarting.");
        send(sender, "<yellow>/replenishplusplus toggle <dark_gray>- <gray>Turns replanting on or off for everyone.");
        send(sender, "<yellow>/replenishplusplus version <dark_gray>- <gray>Shows version and update info.");
        send(sender, "");
        send(sender, Messages.LINE);
    }

    private void sendHelp(CommandSender sender) {
        send(sender, "");
        send(sender, "<dark_gray>      [ <yellow><bold>ReplenishPlusPlus <gray>Help Guide <dark_gray>]       <reset>");
        send(sender, "");
        send(sender, "<yellow>How it works:");
        send(sender, "  " + Messages.DOT + "<gray>Use a <white>Hoe <gray>for normal crops, or an <white>Axe <gray>for Cocoa.");
        send(sender, "  " + Messages.DOT + "<gray>Break the crop, and it will auto-replant instantly.");
        send(sender, "  " + Messages.DOT + "<gray>If seeds are required, 1 seed is taken from your inventory.");
        send(sender, "");
        send(sender, "<yellow><bold>Pro Tip:");
        send(sender, "  " + Messages.DOT + "<gray>It is best to have at least <white>4x <gray>of the seed of the crop to");
        send(sender, "    <gray>avoid replanting it too fast and running out, making it think");
        send(sender, "    <gray>you don't have enough seeds!");
        send(sender, "");
        send(sender, "<yellow>Commands:");
        send(sender, "  " + Messages.DOT + "<white>/replenishplusplus status <dark_gray>- <gray>Shows current settings and enabled crops.");
        send(sender, "  " + Messages.DOT + "<white>/replenishplusplus reload <dark_gray>- <gray>Reloads config.yml without restarting.");
        send(sender, "  " + Messages.DOT + "<white>/replenishplusplus toggle <dark_gray>- <gray>Turns replanting on or off for everyone.");
        send(sender, "  " + Messages.DOT + "<white>/replenishplusplus version <dark_gray>- <gray>Shows version and update info.");
        send(sender, "");
        send(sender, Messages.LINE);
    }

    private void handleToggle(CommandSender sender) {
        boolean nowEnabled = !plugin.isEnabledGlobally();
        plugin.getConfig().set("enabled", nowEnabled);
        plugin.saveConfig();
        plugin.setGloballyEnabled(nowEnabled);

        String state  = nowEnabled ? "<green><bold>ENABLED" : "<red><bold>DISABLED";
        String detail = nowEnabled
                ? "Crops will replant themselves again."
                : "Crops will no longer replant. Harvests behave like vanilla.";
        sendPrefixed(sender, "ReplenishPlusPlus is now " + state + "<gray>. " + detail);
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadLocalConfig();
        ConfigCache cfg = plugin.getConfigCache();

        send(sender, "");
        send(sender, "<dark_gray>      [ <yellow><bold>Config Reloaded <dark_gray>]       <reset>");
        send(sender, "");
        send(sender, "  " + Messages.DOT + "<gray>Replanting: " + onOff(cfg.enabled()));
        send(sender, "  " + Messages.DOT + "<gray>Replant delay: <white>" + cfg.replantDelayTicks() + " tick(s)");
        send(sender, "  " + Messages.DOT + "<gray>Replants per tick: <white>" + cfg.maxReplantsPerTick());
        send(sender, "  " + Messages.DOT + "<gray>Queue capacity: <white>" + cfg.maxReplantsQueued());
        send(sender, "  " + Messages.DOT + "<gray>Give drops directly to player: "
                + yesNo(cfg.directPickup(), "No, drop on ground"));
        send(sender, "  " + Messages.DOT + "<gray>Require a seed to replant: "
                + yesNo(cfg.requirePlayerSeed(), "No"));
        send(sender, "  " + Messages.DOT + "<gray>Sounds: <white>"
                + countEnabledSounds(cfg) + "/4 <gray>enabled");
        send(sender, "");
        send(sender, "<gray>Your config.yml changes are now live.");
        send(sender, "");
        send(sender, Messages.LINE);
    }

    private void sendStatus(CommandSender sender) {
        ConfigCache cfg = plugin.getConfigCache();
        String version = plugin.getPluginMeta().getVersion();

        send(sender, "");
        send(sender, "<dark_gray>      [ <yellow><bold>ReplenishPlusPlus <gray>v"
                + version + " <dark_gray>]       <reset>");
        send(sender, "");
        send(sender, cfg.enabled()
                ? "<green>✔ <white>Replanting is active"
                : "<red>✘ <white>Replanting is disabled");
        send(sender, cfg.requirePlayerSeed()
                ? "<green>✔ <white>Players must have a spare seed to replant"
                : "<red>✘ <white>No seed needed to replant");
        send(sender, cfg.directPickup()
                ? "<green>✔ <white>Harvested crops go straight to inventory"
                : "<red>✘ <white>Harvested crops drop on the ground");
        send(sender, "");

        send(sender, "<yellow>Timing");
        send(sender, "  " + Messages.DOT + "<gray>Replants after: <white>" + cfg.replantDelayTicks() + " tick(s)");
        send(sender, "  " + Messages.DOT + "<gray>Replants per tick: <white>" + cfg.maxReplantsPerTick());
        send(sender, "  " + Messages.DOT + "<gray>Queue capacity: <white>" + cfg.maxReplantsQueued());
        send(sender, "");

        send(sender, "<yellow>Crops that auto-replant");
        send(sender, "");
        for (CropType crop : CropType.values()) {
            boolean on = plugin.isCropEnabled(crop);
            send(sender, "  " + (on ? "<green>✔" : "<red>✖") + " <gray>" + cropDisplayName(crop));
        }
        send(sender, "");

        send(sender, "<yellow>Sounds");
        appendSoundLine(sender, "Pickup",         cfg.pickupSound());
        appendSoundLine(sender, "Inventory full", cfg.inventoryFullSound());
        appendSoundLine(sender, "Denied (tool)",  cfg.deniedToolSound());
        appendSoundLine(sender, "Denied (seed)",  cfg.deniedSeedSound());
        send(sender, "");

        send(sender, "<gray>Tip: <dark_gray>/<gray>replenishplusplus reload <gray>after editing config.yml.");
        send(sender, "");
        send(sender, Messages.LINE);
    }

    private void sendVersion(CommandSender sender) {
        send(sender, "");
        send(sender, "<dark_gray>      [ <yellow><bold>Version Info <dark_gray>]       <reset>");
        send(sender, "");

        UpdateChecker uc = plugin.getUpdateChecker();
        String runningVersion = "v" + plugin.getPluginMeta().getVersion();

        if (uc == null || !uc.isEnabled()) {
            send(sender, "  " + Messages.DOT + "<gray>You're running: <white>" + runningVersion);
            send(sender, "  " + Messages.DOT + "<gray>Update checks: <red>Disabled in config.yml");
        } else if (!uc.isCheckCompleted()) {
            send(sender, "  " + Messages.DOT + "<gray>You're running: <white>" + runningVersion);
            send(sender, "  " + Messages.DOT + "<gray>Update check: <white>Still checking, try again shortly");
        } else if (uc.isUpdateAvailable()) {
            send(sender, "  " + Messages.DOT + "<yellow>A new version is available! <dark_gray>(<white>v"
                    + uc.getCurrentVersion() + " <gray>➟ <yellow>v" + uc.getLatestVersion() + "<dark_gray>)");
            send(sender, "  " + Messages.DOT +
                    "<gray>Download: <aqua><click:open_url:'https://github.com/Mitra-88/ReplenishPlusPlus/releases/latest'><hover:show_text:'<gray>Click to open release page'><u>github.com/Mitra-88/ReplenishPlusPlus</u></click>");
        } else if (uc.isLocalNewer()) {
            send(sender,
                    "  " + Messages.DOT
                            + "<light_purple>You're using a development build\n"
                            + "    <dark_gray>• <gray>Current: <white>v" + uc.getCurrentVersion() + "\n"
                            + "    <dark_gray>• <gray>Latest release: <white>v" + uc.getLatestVersion() + "\n"
                            + "    <dark_gray>• <light_purple>Your build is newer than the latest public release.");
        } else {
            send(sender, "  " + Messages.DOT + "<green>You're up to date! <dark_gray>(<white>v"
                    + uc.getCurrentVersion() + "<dark_gray>)");
        }

        send(sender, "");
        send(sender, "<gray>Server details");
        send(sender, "  " + Messages.DOT + "<gray>Server: <white>" + plugin.getServer().getVersion());
        send(sender, "  " + Messages.DOT + "<gray>Java: <white>" + System.getProperty("java.version")
                + " <dark_gray>(<gray>" + System.getProperty("java.vendor") + "<dark_gray>)");
        send(sender, "");
        send(sender, Messages.LINE);
    }

    private void appendSoundLine(CommandSender sender, String label, SoundEffect sound) {
        if (sound == null || !sound.enabled()) {
            send(sender, "  " + Messages.DOT + "<gray>" + label + ": <red>DISABLED");
            return;
        }
        var key = Registry.SOUNDS.getKey(sound.sound());
        String soundName = key != null ? key.asString() : "UNKNOWN";
        send(sender, "  " + Messages.DOT + "<gray>" + label + ": <green>" + soundName
                + " <dark_gray>(<gray>vol <white>" + formatFloat(sound.volume())
                + "<dark_gray>, <gray>pitch <white>" + formatFloat(sound.pitch()) + "<dark_gray>)");
    }

    private static String cropDisplayName(CropType crop) {
        return switch (crop) {
            case WHEAT       -> "Wheat";
            case CARROTS     -> "Carrots";
            case POTATOES    -> "Potatoes";
            case NETHER_WART -> "Nether Wart";
            case COCOA       -> "Cocoa";
            case BEETROOTS   -> "Beetroots";
        };
    }

    private static String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String onOff(boolean value) {
        return value ? "<green>On" : "<red>Off";
    }

    private static String yesNo(boolean value, String no) {
        return value ? "<green>" + "Yes" : "<red>" + no;
    }

    private static int countEnabledSounds(ConfigCache cfg) {
        int count = 0;
        if (isEnabled(cfg.pickupSound()))         count++;
        if (isEnabled(cfg.inventoryFullSound()))  count++;
        if (isEnabled(cfg.deniedToolSound()))     count++;
        if (isEnabled(cfg.deniedSeedSound()))     count++;
        return count;
    }

    private static boolean isEnabled(SoundEffect sound) {
        return sound != null && sound.enabled();
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(MINI_MESSAGE.deserialize(message));
    }

    private static void sendPrefixed(CommandSender sender, String message) {
        send(sender, Messages.PREFIX + message);
    }
}