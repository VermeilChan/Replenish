package dev.replenishplusplus.update;

import dev.replenishplusplus.ReplenishPlusPlus;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class UpdateNotificationListener implements Listener {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private final ReplenishPlusPlus plugin;

    public UpdateNotificationListener(ReplenishPlusPlus plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!event.getPlayer().hasPermission("replenishplusplus.update")) return;

        UpdateChecker uc = plugin.getUpdateChecker();
        if (uc == null || !uc.isEnabled() || !uc.isCheckCompleted()) return;

        if (uc.isUpdateAvailable()) {
            plugin.getServer().getAsyncScheduler().runDelayed(plugin, _ -> {
                if (!event.getPlayer().isOnline()) return;
                event.getPlayer().sendMessage(MINI_MESSAGE.deserialize(
                        "<dark_gray>[<yellow>ReplenishPlusPlus<dark_gray>] <dark_gray>» " +
                                "<yellow>A new version is available! <dark_gray>(<white>v" + uc.getCurrentVersion() +
                                " <gray>➟ <yellow>v" + uc.getLatestVersion() + "<dark_gray>)"));
                event.getPlayer().sendMessage(MINI_MESSAGE.deserialize(
                        "<dark_gray>[<yellow>ReplenishPlusPlus<dark_gray>] <dark_gray>» " +
                                "<gray>Download: <aqua><click:open_url:'https://github.com/Mitra-88/ReplenishPlusPlus/releases/latest'>" +
                                "<hover:show_text:'<gray>Click to open release page'><u>github.com/Mitra-88/ReplenishPlusPlus</u></click>"));
            }, 2, java.util.concurrent.TimeUnit.SECONDS);
        }
    }
}