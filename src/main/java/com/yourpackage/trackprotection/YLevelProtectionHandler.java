package com.yourpackage.trackprotection;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class YLevelProtectionHandler {
    private final JavaPlugin plugin;
    private double yLevelThreshold;
    private boolean enableYLevelProtection;
    private List<String> protectedWorlds;

    public YLevelProtectionHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadConfigValues();
    }

    public void reloadConfigValues() {
        yLevelThreshold = plugin.getConfig().getDouble("y-level-protection.y-level-threshold", -13.0);
        enableYLevelProtection = plugin.getConfig().getBoolean("y-level-protection.enable", true);
        protectedWorlds = plugin.getConfig().getStringList("y-level-protection.protected-worlds");
    }

    public boolean isInProtectedYLevel(Player player) {
        if (!enableYLevelProtection) return false;
        if (!protectedWorlds.contains(player.getWorld().getName())) return false;
        return player.getLocation().getY() < yLevelThreshold;
    }
}