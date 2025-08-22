package com.yourpackage;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TrackingManager {
    private final JavaPlugin plugin;
    private final CompassHandler compassHandler;
    private final EconomyHandler economyHandler;
    private final TrackingTimer trackingTimer; // 新增
    private final Map<UUID, UUID> trackingMap = new HashMap<>();
    private final Set<UUID> trackedPlayers = new HashSet<>();
    private final Map<UUID, Long> immobilityTimers = new HashMap<>();
    private final Map<UUID, UUID> pendingTargets = new HashMap<>();
    private final Map<UUID, Long> trackingStartTimes = new HashMap<>();
    private String trackingStartedMessage;
    private String trackingStartedNotifyTargetMessage;
    private String trackingStoppedMessage;
    private String teleportSuccessMessage;

    public TrackingManager(JavaPlugin plugin, CompassHandler compassHandler, EconomyHandler economyHandler, TrackingTimer trackingTimer) {
        this.plugin = plugin;
        this.compassHandler = compassHandler;
        this.economyHandler = economyHandler;
        this.trackingTimer = trackingTimer; // 新增
        reloadConfigValues();
    }

    public void reloadConfigValues() {
        trackingStartedMessage = plugin.getConfig().getString("messages.tracking-started", "已選擇追蹤 {target}。請保持不動{seconds}秒以傳送。");
        trackingStartedNotifyTargetMessage = plugin.getConfig().getString("messages.tracking-started-notify-target", "您已被 {tracker} 追蹤。");
        trackingStoppedMessage = plugin.getConfig().getString("messages.tracking-stopped", "已停止追蹤。");
        teleportSuccessMessage = plugin.getConfig().getString("messages.teleport-success", "已傳送至目標附近500格。");
    }

    public void startTracking(Player tracker, Player target) {
        UUID trackerUUID = tracker.getUniqueId();
        immobilityTimers.put(trackerUUID, System.currentTimeMillis());
        pendingTargets.put(trackerUUID, target.getUniqueId());
        tracker.sendMessage(trackingStartedMessage.replace("{target}", target.getName()).replace("{seconds}", String.valueOf(plugin.getConfig().getInt("teleport-delay-seconds", 5))));
        trackingTimer.startCountdown(tracker); // 更新：調用 trackingTimer
    }

    public void confirmTracking(Player tracker, Player target) {
        UUID trackerUUID = tracker.getUniqueId();
        UUID targetUUID = target.getUniqueId();
        if (!economyHandler.deductTrackCost(tracker)) {
            tracker.sendMessage(plugin.getConfig().getString("lang.messages.insufficient-funds", "您的餘額不足，無法進行此操作。"));
            pendingTargets.remove(trackerUUID);
            return;
        }
        trackingMap.put(trackerUUID, targetUUID);
        trackedPlayers.add(targetUUID);
        trackingStartTimes.put(trackerUUID, System.currentTimeMillis());
        pendingTargets.remove(trackerUUID);
        compassHandler.giveTrackingCompass(tracker, target);
        target.sendMessage(trackingStartedNotifyTargetMessage.replace("{tracker}", tracker.getName()));
        tracker.sendMessage(teleportSuccessMessage);
    }

    public void stopTracking(Player tracker) {
        UUID trackerUUID = tracker.getUniqueId();
        UUID targetUUID = trackingMap.remove(trackerUUID);
        if (targetUUID != null) {
            trackedPlayers.remove(targetUUID);
            Player target = Bukkit.getPlayer(targetUUID);
            if (target != null) {
                target.sendMessage(trackingStoppedMessage);
            }
        }
        immobilityTimers.remove(trackerUUID);
        trackingStartTimes.remove(trackerUUID);
        pendingTargets.remove(trackerUUID);
        compassHandler.stopCompassTask(trackerUUID);
        trackingTimer.stopCountdownTask(trackerUUID); // 更新：調用 trackingTimer
        tracker.sendMessage(trackingStoppedMessage);
    }

    public void cancelTracking(Player trackedPlayer) {
        UUID trackedUUID = trackedPlayer.getUniqueId();
        UUID trackerUUID = null;
        for (Map.Entry<UUID, UUID> entry : trackingMap.entrySet()) {
            if (entry.getValue().equals(trackedUUID)) {
                trackerUUID = entry.getKey();
                break;
            }
        }
        if (trackerUUID == null) {
            trackedPlayer.sendMessage(economyHandler.getNoTrackingToCancelMessage());
            return;
        }

        if (economyHandler.deductCancelCost(trackedPlayer)) {
            Player tracker = Bukkit.getPlayer(trackerUUID);
            if (tracker != null) {
                stopTracking(tracker);
            } else {
                trackingMap.remove(trackerUUID);
                trackedPlayers.remove(trackedUUID);
                immobilityTimers.remove(trackerUUID);
                trackingStartTimes.remove(trackerUUID);
                pendingTargets.remove(trackerUUID);
            }
        }
    }

    public boolean isTracked(UUID playerUUID) {
        return trackedPlayers.contains(playerUUID);
    }

    public boolean isTracking(UUID playerUUID) {
        return trackingMap.containsKey(playerUUID);
    }

    public UUID getTarget(UUID trackerUUID) {
        UUID targetUUID = trackingMap.get(trackerUUID);
        if (targetUUID == null) {
            targetUUID = pendingTargets.get(trackerUUID);
        }
        return targetUUID;
    }

    public Long getTrackingStartTime(UUID playerUUID) {
        return trackingStartTimes.get(playerUUID);
    }

    // 新增：委託給 TrackingTimer
    public int getTrackingDurationMillis() {
        return trackingTimer.getTrackingDurationMillis();
    }

    public Map<UUID, UUID> getTrackingMap() {
        return trackingMap;
    }

    public Set<UUID> getTrackedPlayers() {
        return trackedPlayers;
    }

    public Map<UUID, Long> getImmobilityTimers() {
        return immobilityTimers;
    }

    public Map<UUID, UUID> getPendingTargets() {
        return pendingTargets;
    }

    public Map<UUID, Long> getTrackingStartTimes() {
        return trackingStartTimes;
    }

    public UUID getTrackerUUID(UUID targetUUID) {
        for (Map.Entry<UUID, UUID> entry : trackingMap.entrySet()) {
            if (entry.getValue().equals(targetUUID)) {
                return entry.getKey();
            }
        }
        return null;
    }
}