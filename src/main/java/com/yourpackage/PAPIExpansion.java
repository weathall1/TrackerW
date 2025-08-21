package com.yourpackage;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PAPIExpansion extends PlaceholderExpansion {
    private final TrackingManager trackingManager;

    public PAPIExpansion(TrackingManager trackingManager) {
        this.trackingManager = trackingManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "tracking";
    }

    @Override
    public @NotNull String getAuthor() {
        return "YourName"; // 請替換為您的作者名稱
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0"; // 請根據您的插件版本調整
    }

    @Override
    public boolean persist() {
        return true; // 確保擴展在伺服器重載時保持註冊
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) {
            return "";
        }

        switch (identifier.toLowerCase()) {
            case "isbeingtracked":
                return String.valueOf(trackingManager.isTracked(player.getUniqueId()));
            case "istracking":
                return String.valueOf(trackingManager.isTracking(player.getUniqueId()));
            case "tracktime":
                Long startTime = trackingManager.getTrackingStartTime(player.getUniqueId());
                if (startTime == null) {
                    return "0";
                }
                long elapsedMillis = System.currentTimeMillis() - startTime;
                long remainingMillis = trackingManager.getTrackingDurationMillis() - elapsedMillis;
                return String.valueOf(Math.max(0, remainingMillis / 1000));
            default:
                return null;
        }
    }
}