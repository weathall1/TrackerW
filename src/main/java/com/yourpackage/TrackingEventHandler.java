package com.yourpackage;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class TrackingEventHandler implements Listener {
    private final JavaPlugin plugin;
    private final TrackingManager trackingManager;
    private final CompassHandler compassHandler;
    private final EconomyHandler economyHandler;
    private final TeleportHandler teleportHandler;
    private boolean actionBarEnabled;
    private String teleportCancelledMessage;
    private String targetQuitTrackingEndedMessage;
    private String targetQuitPenaltyMessage;
    private String targetNaturalDeathTrackingEndedMessage;
    private String targetNaturalDeathPenaltyMessage;
    private String targetKilledByPlayerTrackingEndedMessage;
    private String trackerQuitTrackingEndedMessage;
    private String trackerDeathTrackingEndedMessage;
    private boolean interruptedSoundEnabled;
    private String interruptedSound;
    private float interruptedVolume;
    private float interruptedPitch;

    public TrackingEventHandler(JavaPlugin plugin, TrackingManager trackingManager, CompassHandler compassHandler,
                                EconomyHandler economyHandler, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.trackingManager = trackingManager;
        this.compassHandler = compassHandler;
        this.economyHandler = economyHandler;
        this.teleportHandler = teleportHandler;
        reloadConfigValues();
    }

    public void reloadConfigValues() {
        actionBarEnabled = plugin.getConfig().getBoolean("actionbar.enabled", true);
        teleportCancelledMessage = plugin.getConfig().getString("messages.teleport-cancelled", "傳送已取消，請重新選擇目標以再次嘗試。");
        targetQuitTrackingEndedMessage = plugin.getConfig().getString("messages.target-quit-tracking-ended", "目標 {target} 已退出遊戲，追蹤結束，已退款 {cost} 元。");
        targetQuitPenaltyMessage = plugin.getConfig().getString("messages.target-quit-penalty", "您因退出遊戲被扣減 {deducted} 元（追蹤罰款）。");
        targetNaturalDeathTrackingEndedMessage = plugin.getConfig().getString("messages.target-natural-death-tracking-ended", "目標 {target} 已自然死亡，追蹤結束，已退款 {cost} 元。");
        targetNaturalDeathPenaltyMessage = plugin.getConfig().getString("messages.target-natural-death-penalty", "您因自然死亡被扣減 {deducted} 元（追蹤罰款）。");
        targetKilledByPlayerTrackingEndedMessage = plugin.getConfig().getString("messages.target-killed-by-player-tracking-ended", "目標 {target} 被玩家殺死，追蹤結束。");
        trackerQuitTrackingEndedMessage = plugin.getConfig().getString("messages.tracker-quit-tracking-ended", "追蹤者 {tracker} 已退出遊戲，您的追蹤狀態已結束。");
        trackerDeathTrackingEndedMessage = plugin.getConfig().getString("messages.tracker-death-tracking-ended", "追蹤者 {tracker} 已死亡，您的追蹤狀態已結束。");
        interruptedSoundEnabled = plugin.getConfig().getBoolean("interrupted-sound.enabled", true);
        interruptedSound = plugin.getConfig().getString("interrupted-sound.sound", "BLOCK_NOTE_BLOCK_BASS");
        interruptedVolume = (float) plugin.getConfig().getDouble("interrupted-sound.volume", 1.0);
        interruptedPitch = (float) plugin.getConfig().getDouble("interrupted-sound.pitch", 0.5);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (trackingManager.getImmobilityTimers().containsKey(uuid) && event.getFrom().distance(event.getTo()) > 0.1) {
            trackingManager.getImmobilityTimers().remove(uuid);
            trackingManager.getPendingTargets().remove(uuid);
            playInterruptedSound(event.getPlayer());
            if (actionBarEnabled) {
                event.getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
            }
            event.getPlayer().sendMessage(teleportCancelledMessage);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // 處理被追蹤者退出
        if (trackingManager.isTracked(playerUUID)) {
            UUID trackerUUID = trackingManager.getTrackerUUID(playerUUID);
            if (trackerUUID == null) return;

            OfflinePlayer trackerOffline = Bukkit.getOfflinePlayer(trackerUUID);
            Player tracker = trackerOffline.getPlayer();

            double refundAmount = economyHandler.getTrackCost();
            economyHandler.deposit(trackerOffline, refundAmount);

            if (tracker != null) {
                tracker.sendMessage(targetQuitTrackingEndedMessage.replace("{target}", player.getName()).replace("{cost}", String.valueOf(refundAmount)));
            }

            double penalty = economyHandler.getCancelCost();
            double deducted = economyHandler.deductMax(player, penalty);
            player.sendMessage(targetQuitPenaltyMessage.replace("{deducted}", String.valueOf(deducted)));

            if (tracker != null) {
                trackingManager.stopTracking(tracker);
            } else {
                trackingManager.getTrackingMap().remove(trackerUUID);
                trackingManager.getTrackedPlayers().remove(playerUUID);
                trackingManager.getImmobilityTimers().remove(trackerUUID);
                trackingManager.getTrackingStartTimes().remove(trackerUUID);
                trackingManager.getPendingTargets().remove(trackerUUID);
                compassHandler.stopCompassTask(trackerUUID);
            }
        }

        // 處理追蹤者退出
        if (trackingManager.isTracking(playerUUID)) {
            UUID targetUUID = trackingManager.getTarget(playerUUID);
            if (targetUUID == null) return;

            Player target = Bukkit.getPlayer(targetUUID);
            if (target != null) {
                target.sendMessage(trackerQuitTrackingEndedMessage.replace("{tracker}", player.getName()));
            }

            trackingManager.getTrackingMap().remove(playerUUID);
            trackingManager.getTrackedPlayers().remove(targetUUID);
            trackingManager.getImmobilityTimers().remove(playerUUID);
            trackingManager.getTrackingStartTimes().remove(playerUUID);
            trackingManager.getPendingTargets().remove(playerUUID);
            compassHandler.stopCompassTask(playerUUID);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerUUID = player.getUniqueId();

        // 處理被追蹤者死亡
        if (trackingManager.isTracked(playerUUID)) {
            UUID trackerUUID = trackingManager.getTrackerUUID(playerUUID);
            if (trackerUUID == null) return;

            OfflinePlayer trackerOffline = Bukkit.getOfflinePlayer(trackerUUID);
            Player tracker = trackerOffline.getPlayer();

            if (event.getEntity().getKiller() instanceof Player) {
                // 被玩家殺死
                if (tracker != null) {
                    tracker.sendMessage(targetKilledByPlayerTrackingEndedMessage.replace("{target}", player.getName()));
                    trackingManager.stopTracking(tracker);
                } else {
                    trackingManager.getTrackingMap().remove(trackerUUID);
                    trackingManager.getTrackedPlayers().remove(playerUUID);
                    trackingManager.getImmobilityTimers().remove(trackerUUID);
                    trackingManager.getTrackingStartTimes().remove(trackerUUID);
                    trackingManager.getPendingTargets().remove(trackerUUID);
                    compassHandler.stopCompassTask(trackerUUID);
                }
            } else {
                // 自然死亡
                double refundAmount = economyHandler.getTrackCost();
                economyHandler.deposit(trackerOffline, refundAmount);

                if (tracker != null) {
                    tracker.sendMessage(targetNaturalDeathTrackingEndedMessage.replace("{target}", player.getName()).replace("{cost}", String.valueOf(refundAmount)));
                }

                double penalty = economyHandler.getNaturalDeathPenalty();
                double deducted = economyHandler.deductMax(player, penalty);
                player.sendMessage(targetNaturalDeathPenaltyMessage.replace("{deducted}", String.valueOf(deducted)));

                if (tracker != null) {
                    trackingManager.stopTracking(tracker);
                } else {
                    trackingManager.getTrackingMap().remove(trackerUUID);
                    trackingManager.getTrackedPlayers().remove(playerUUID);
                    trackingManager.getImmobilityTimers().remove(trackerUUID);
                    trackingManager.getTrackingStartTimes().remove(trackerUUID);
                    trackingManager.getPendingTargets().remove(trackerUUID);
                    compassHandler.stopCompassTask(trackerUUID);
                }
            }
        }

        // 處理追蹤者死亡
        if (trackingManager.isTracking(playerUUID)) {
            UUID targetUUID = trackingManager.getTarget(playerUUID);
            if (targetUUID == null) return;

            Player target = Bukkit.getPlayer(targetUUID);
            if (target != null) {
                target.sendMessage(trackerDeathTrackingEndedMessage.replace("{tracker}", player.getName()));
            }

            trackingManager.getTrackingMap().remove(playerUUID);
            trackingManager.getTrackedPlayers().remove(targetUUID);
            trackingManager.getImmobilityTimers().remove(playerUUID);
            trackingManager.getTrackingStartTimes().remove(playerUUID);
            trackingManager.getPendingTargets().remove(playerUUID);
            compassHandler.stopCompassTask(playerUUID);
        }
    }

    private void playInterruptedSound(Player player) {
        if (interruptedSoundEnabled) {
            player.playSound(player.getLocation(), Sound.valueOf(interruptedSound), interruptedVolume, interruptedPitch);
        }
    }
}