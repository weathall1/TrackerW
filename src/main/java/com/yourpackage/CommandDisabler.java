package com.yourpackage;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class CommandDisabler implements Listener {
    private final JavaPlugin plugin;
    private final TrackingManager trackingManager;
    private String teleportBlockedMessage;

    public CommandDisabler(JavaPlugin plugin, TrackingManager trackingManager) {
        this.plugin = plugin;
        this.trackingManager = trackingManager;
        reloadConfigValues();
    }

    public void reloadConfigValues() {
        teleportBlockedMessage = plugin.getConfig().getString("messages.teleport-blocked", "您正在被追蹤，無法使用傳送指令。");
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (trackingManager.isTracking(uuid) || trackingManager.isTracked(uuid)) {
            String cmd = event.getMessage().toLowerCase();
            if (cmd.startsWith("/home") || cmd.startsWith("/spawn") || cmd.startsWith("/warp") || cmd.startsWith("/dhome") || cmd.startsWith("/track") || cmd.startsWith("/rtp") || cmd.startsWith("/tpa")) {
                event.setCancelled(true);
                player.sendMessage(teleportBlockedMessage);
            }
        }
    }
}