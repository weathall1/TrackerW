package com.yourpackage;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TrackingTimer {
    private final JavaPlugin plugin;
    private TrackingManager trackingManager; // 修改為非 final，允許後期設置
    private final CompassHandler compassHandler;
    private final TeleportHandler teleportHandler;
    private final Map<UUID, BukkitRunnable> countdownTasks = new HashMap<>();
    private int teleportDelayMillis;
    private boolean soundEnabled;
    private String countdownSound;
    private float soundVolume;
    private float soundPitch;
    private int trackingDurationMillis;
    private boolean actionBarEnabled;
    private String actionBarMessage;
    private String trackingCompletedMessage;

    public TrackingTimer(JavaPlugin plugin, TrackingManager trackingManager, CompassHandler compassHandler, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.trackingManager = trackingManager;
        this.compassHandler = compassHandler;
        this.teleportHandler = teleportHandler;
        reloadConfigValues();
        startTimerCheckTask();
        startTrackingDurationCheckTask();
    }

    // 新增：設置 TrackingManager
    public void setTrackingManager(TrackingManager trackingManager) {
        this.trackingManager = trackingManager;
    }

    public void reloadConfigValues() {
        teleportDelayMillis = plugin.getConfig().getInt("teleport-delay-seconds", 5) * 1000;
        soundEnabled = plugin.getConfig().getBoolean("countdown-sound.enabled", true);
        countdownSound = plugin.getConfig().getString("countdown-sound.sound", "BLOCK_NOTE_BLOCK_PLING");
        soundVolume = (float) plugin.getConfig().getDouble("countdown-sound.volume", 1.0);
        soundPitch = (float) plugin.getConfig().getDouble("countdown-sound.pitch", 1.0);
        trackingDurationMillis = plugin.getConfig().getInt("tracking-duration-seconds", 300) * 1000;
        actionBarEnabled = plugin.getConfig().getBoolean("actionbar.enabled", true);
        actionBarMessage = plugin.getConfig().getString("messages.actionbar-countdown", "傳送倒計時：%seconds%秒");
        trackingCompletedMessage = plugin.getConfig().getString("messages.tracking-completed", "追蹤已完結。");
    }

    public void startCountdown(Player player) {
        UUID uuid = player.getUniqueId();
        stopCountdownTask(uuid);
        BukkitRunnable task = new BukkitRunnable() {
            long startTime = trackingManager.getImmobilityTimers().getOrDefault(uuid, 0L);
            int secondsLeft = teleportDelayMillis / 1000;

            @Override
            public void run() {
                if (!trackingManager.getImmobilityTimers().containsKey(uuid) || trackingManager.getImmobilityTimers().get(uuid) != startTime) {
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

    public void stopCountdownTask(UUID uuid) {
        BukkitRunnable task = countdownTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    private void startTimerCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, Long> entry : new HashMap<>(trackingManager.getImmobilityTimers()).entrySet()) {
                    UUID trackerId = entry.getKey();
                    Player tracker = Bukkit.getPlayer(trackerId);
                    if (tracker == null) continue;
                    if (System.currentTimeMillis() - entry.getValue() >= teleportDelayMillis) {
                        UUID targetId = trackingManager.getTarget(trackerId);
                        if (targetId == null) continue;
                        Player target = Bukkit.getPlayer(targetId);
                        if (target != null) {
                            teleportHandler.teleportNearTarget(tracker, target);
                            trackingManager.confirmTracking(tracker, target);
                            trackingManager.getImmobilityTimers().remove(trackerId);
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
                for (Map.Entry<UUID, Long> entry : new HashMap<>(trackingManager.getTrackingStartTimes()).entrySet()) {
                    UUID trackerId = entry.getKey();
                    Player tracker = Bukkit.getPlayer(trackerId);
                    if (tracker != null && System.currentTimeMillis() - entry.getValue() >= trackingDurationMillis) {
                        tracker.sendMessage(trackingCompletedMessage);
                        for (ItemStack item : tracker.getInventory().getContents()) {
                            if (item != null && item.getType() == Material.COMPASS) {
                                tracker.getInventory().removeItem(item);
                            }
                        }
                        trackingManager.stopTracking(tracker);
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    public int getTrackingDurationMillis() {
        return trackingDurationMillis;
    }
}