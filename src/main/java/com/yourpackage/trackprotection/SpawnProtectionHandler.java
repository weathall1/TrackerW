package com.yourpackage.trackprotection;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class SpawnProtectionHandler {
    private final JavaPlugin plugin;
    private final Location protectedCenter;
    private double protectedRadius;
    private boolean enableSpawnProtection;
    private String protectedWorld;

    public SpawnProtectionHandler(JavaPlugin plugin, Location protectedCenter) {
        this.plugin = plugin;
        this.protectedCenter = protectedCenter;
        reloadConfigValues();
    }

    public void reloadConfigValues() {
        protectedRadius = plugin.getConfig().getDouble("spawn-protection.protected-radius", 100.0);
        enableSpawnProtection = plugin.getConfig().getBoolean("spawn-protection.enable", true);
        protectedWorld = plugin.getConfig().getString("spawn-protection.protected-world", "world");
    }

    public boolean isInProtectedArea(Player player) {
        if (!enableSpawnProtection) return false;
        Location playerLoc = player.getLocation();
        if (!playerLoc.getWorld().getName().equals(protectedWorld)) {
            return false;
        }
        return playerLoc.distance(protectedCenter) <= protectedRadius;
    }
}