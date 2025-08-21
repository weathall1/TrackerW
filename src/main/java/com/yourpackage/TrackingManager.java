package com.yourpackage;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TrackingManager implements Listener {
    private final JavaPlugin plugin;
    private final CompassHandler compassHandler;
    private final TeleportHandler teleportHandler;
    private final EconomyHandler economyHandler;
    private final Map<UUID, UUID> trackingMap = new HashMap<>();
    private final Set<UUID> trackedPlayers = new HashSet<>();
    private final Map<UUID, Long> immobilityTimers = new HashMap<>();
    private final Map<UUID, BukkitRunnable> countdownTasks = new HashMap<>();
    private final Map<UUID, Long> trackingStartTimes = new HashMap<>();
    private int teleportDelayMillis;
    private boolean soundEnabled;
    private String countdownSound;
    private float soundVolume;
    private float soundPitch;
    private boolean interruptedSoundEnabled;
    private String interruptedSound;
    private float interruptedVolume;
    private float interruptedPitch;
    private int trackingDurationMillis;
    private boolean actionBarEnabled;
    private String actionBarMessage;
    private String teleportCancelledMessage;
    private String trackingStartedMessage;
    private String trackingStartedNotifyTargetMessage;
    private String trackingStoppedMessage;
    private String trackingTimeoutMessage;
    private String trackingCompletedMessage;
    private String teleportSuccessMessage;

    public TrackingManager(JavaPlugin plugin, CompassHandler compassHandler, EconomyHandler economyHandler) {
        this.plugin = plugin;
        this.compassHandler = compassHandler;
        this.teleportHandler = new TeleportHandler(this.plugin);
        this.economyHandler = economyHandler;
        reloadConfigValues();
        startTimerCheckTask();
        startTrackingDurationCheckTask();
    }

    public void reloadConfigValues() {
        teleportDelayMillis = plugin.getConfig().getInt("teleport-delay-seconds", 5) * 1000;
        soundEnabled = plugin.getConfig().getBoolean("countdown-sound.enabled", true);
        countdownSound = plugin.getConfig().getString("countdown-sound.sound", "BLOCK_NOTE_BLOCK_PLING");
        soundVolume = (float) plugin.getConfig().getDouble("countdown-sound.volume", 1.0);
        soundPitch = (float) plugin.getConfig().getDouble("countdown-sound.pitch", 1.0);
        interruptedSoundEnabled = plugin.getConfig().getBoolean("interrupted-sound.enabled", true);
        interruptedSound = plugin.getConfig().getString("interrupted-sound.sound", "BLOCK_NOTE_BLOCK_BASS");
        interruptedVolume = (float) plugin.getConfig().getDouble("interrupted-sound.volume", 1.0);
        interruptedPitch = (float) plugin.getConfig().getDouble("interrupted-sound.pitch", 0.5);
        trackingDurationMillis = plugin.getConfig().getInt("tracking-duration-seconds", 300) * 1000; // Changed to seconds
        actionBarEnabled = plugin.getConfig().getBoolean("actionbar.enabled", true);
        actionBarMessage = plugin.getConfig().getString("messages.actionbar-countdown", "傳送倒計時：%seconds%秒");
        teleportCancelledMessage = plugin.getConfig().getString("messages.teleport-cancelled", "傳送已取消，請重新選擇目標以再次嘗試。");
        trackingStartedMessage = plugin.getConfig().getString("messages.tracking-started", "已選擇追蹤 {target}。請保持不動{seconds}秒以傳送。");
        trackingStartedNotifyTargetMessage = plugin.getConfig().getString("messages.tracking-started-notify-target", "您已被 {tracker} 追蹤。");
        trackingStoppedMessage = plugin.getConfig().getString("messages.tracking-stopped", "已停止追蹤。");
        trackingTimeoutMessage = plugin.getConfig().getString("messages.tracking-timeout", "追蹤時間已過，自動停止追蹤。");
        trackingCompletedMessage = plugin.getConfig().getString("messages.tracking-completed", "追蹤已完結。");
        teleportSuccessMessage = plugin.getConfig().getString("messages.teleport-success", "已傳送至目標附近500格。");
    }

    public void startTracking(Player tracker, Player target) {
        trackingMap.put(tracker.getUniqueId(), target.getUniqueId());
        trackedPlayers.add(target.getUniqueId());
        immobilityTimers.put(tracker.getUniqueId(), System.currentTimeMillis());
        tracker.sendMessage(trackingStartedMessage.replace("{target}", target.getName()).replace("{seconds}", String.valueOf(teleportDelayMillis / 1000)));
        target.sendMessage(trackingStartedNotifyTargetMessage.replace("{tracker}", tracker.getName()));
        startCountdown(tracker);
    }

    public void confirmTracking(Player tracker, Player target) {
        if (economyHandler.deductTrackCost(tracker)) {
            trackingStartTimes.put(tracker.getUniqueId(), System.currentTimeMillis());
            compassHandler.giveTrackingCompass(tracker, target);
            tracker.sendMessage(teleportSuccessMessage);
        }
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
        compassHandler.stopCompassTask(trackerUUID);
        stopCountdownTask(trackerUUID);
        if (actionBarEnabled) {
            tracker.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
        }
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
        return trackingMap.get(trackerUUID);
    }

    public Long getTrackingStartTime(UUID playerUUID) {
        return trackingStartTimes.get(playerUUID);
    }

    public int getTrackingDurationMillis() {
        return trackingDurationMillis;
    }

    public UUID getTrackerUUID(UUID targetUUID) {
        for (Map.Entry<UUID, UUID> entry : trackingMap.entrySet()) {
            if (entry.getValue().equals(targetUUID)) {
                return entry.getKey();
            }
        }
        return null;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (trackingMap.containsKey(uuid) && event.getFrom().distance(event.getTo()) > 0.1) {
            if (immobilityTimers.containsKey(uuid)) {
                immobilityTimers.remove(uuid);
                playInterruptedSound(event.getPlayer());
                stopCountdownTask(uuid);
                if (actionBarEnabled) {
                    event.getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
                }
                event.getPlayer().sendMessage(teleportCancelledMessage);
            }
        }
    }

    private void startCountdown(Player player) {
        UUID uuid = player.getUniqueId();
        stopCountdownTask(uuid);
        BukkitRunnable task = new BukkitRunnable() {
            long startTime = immobilityTimers.getOrDefault(uuid, 0L);
            int secondsLeft = teleportDelayMillis / 1000;

            @Override
            public void run() {
                if (!trackingMap.containsKey(uuid) || !immobilityTimers.containsKey(uuid) || immobilityTimers.get(uuid) != startTime) {
                    this.cancel();
                    return;
                }
                if (secondsLeft <= 0) {
                    this.cancel();
                    return;
                }
                if (soundEnabled) {
                    player.playSound(player.getLocation(), Sound.valueOf(countdownSound), soundVolume, soundPitch);
                }
                if (actionBarEnabled) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionBarMessage.replace("%seconds%", String.valueOf(secondsLeft))));
                }
                secondsLeft--;
            }
        };
        task.runTaskTimer(plugin, 0, 20);
        countdownTasks.put(uuid, task);
    }

    private void stopCountdownTask(UUID uuid) {
        BukkitRunnable task = countdownTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    private void playInterruptedSound(Player player) {
        if (interruptedSoundEnabled) {
            player.playSound(player.getLocation(), Sound.valueOf(interruptedSound), interruptedVolume, interruptedPitch);
        }
    }

    private void startTimerCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, Long> entry : new HashMap<>(immobilityTimers).entrySet()) {
                    UUID trackerId = entry.getKey();
                    Player tracker = Bukkit.getPlayer(trackerId);
                    if (tracker == null) continue;
                    if (System.currentTimeMillis() - entry.getValue() >= teleportDelayMillis) {
                        UUID targetId = getTarget(trackerId);
                        Player target = Bukkit.getPlayer(targetId);
                        if (target != null) {
                            teleportHandler.teleportNearTarget(tracker, target);
                            confirmTracking(tracker, target);
                            immobilityTimers.remove(trackerId);
                            stopCountdownTask(trackerId);
                            if (actionBarEnabled) {
                                tracker.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    private void startTrackingDurationCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, Long> entry : new HashMap<>(trackingStartTimes).entrySet()) {
                    UUID trackerId = entry.getKey();
                    Player tracker = Bukkit.getPlayer(trackerId);
                    if (tracker != null && System.currentTimeMillis() - entry.getValue() >= trackingDurationMillis) {
                        tracker.sendMessage(trackingCompletedMessage);
                        for (ItemStack item : tracker.getInventory().getContents()) {
                            if (item != null && item.getType() == Material.COMPASS) {
                                tracker.getInventory().removeItem(item);
                            }
                        }
                        stopTracking(tracker);
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 20);
    }
}