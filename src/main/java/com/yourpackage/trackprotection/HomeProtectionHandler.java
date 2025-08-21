package com.yourpackage.trackprotection;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class HomeProtectionHandler {
    private final JavaPlugin plugin;
    private final Essentials essentials;
    private final Location protectedCenter;
    private double protectedRadius;
    private int chunkRange;
    private double yLevelThreshold;

    public HomeProtectionHandler(JavaPlugin plugin, Essentials essentials, Location protectedCenter) {
        this.plugin = plugin;
        this.essentials = essentials;
        this.protectedCenter = protectedCenter;
        reloadConfigValues();
    }

    public void reloadConfigValues() {
        protectedRadius = plugin.getConfig().getDouble("protected-radius", 100.0);
        chunkRange = plugin.getConfig().getInt("home-protection-chunk-range", 1);
        yLevelThreshold = plugin.getConfig().getDouble("y-level-threshold", -13.0);
    }

    public boolean isProtected(Player target) {
        if (isInProtectedArea(target)) return true;

        if (essentials == null) {
            return false; // No EssentialsX, skip home checks
        }

        User targetUser = essentials.getUser(target.getUniqueId());
        if (targetUser != null) {
            List<String> homes = targetUser.getHomes();
            if (!homes.isEmpty()) {
                Location targetLoc = target.getLocation();
                Chunk targetChunk = targetLoc.getChunk();
                int targetChunkX = targetChunk.getX();
                int targetChunkZ = targetChunk.getZ();
                double targetY = targetLoc.getY();

                for (String homeName : homes) {
                    try {
                        Location homeLoc = targetUser.getHome(homeName);
                        if (homeLoc != null) {
                            if (!homeLoc.getWorld().equals(targetLoc.getWorld())) {
                                continue;
                            }
                            Chunk homeChunk = homeLoc.getChunk();
                            int homeChunkX = homeChunk.getX();
                            int homeChunkZ = homeChunk.getZ();

                            if (Math.abs(targetChunkX - homeChunkX) <= chunkRange &&
                                    Math.abs(targetChunkZ - homeChunkZ) <= chunkRange &&
                                    targetY < yLevelThreshold) {
                                return true;
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("無法獲取 " + target.getName() + " 的家位置 '" + homeName + "': " + e.getMessage());
                    }
                }
            }
        }
        return false;
    }

    public boolean isInAnyPlayerHome(Player target) {
        if (essentials == null) {
            return false; // No EssentialsX, skip home checks
        }

        Location targetLoc = target.getLocation();
        Chunk targetChunk = targetLoc.getChunk();
        int targetChunkX = targetChunk.getX();
        int targetChunkZ = targetChunk.getZ();
        double targetY = targetLoc.getY();

        for (Player other : Bukkit.getOnlinePlayers()) {
            User otherUser = essentials.getUser(other.getUniqueId());
            if (otherUser != null) {
                List<String> homes = otherUser.getHomes();
                for (String homeName : homes) {
                    try {
                        Location homeLoc = otherUser.getHome(homeName);
                        if (homeLoc != null) {
                            if (!homeLoc.getWorld().equals(targetLoc.getWorld())) {
                                continue;
                            }
                            Chunk homeChunk = homeLoc.getChunk();
                            int homeChunkX = homeChunk.getX();
                            int homeChunkZ = homeChunk.getZ();

                            if (Math.abs(targetChunkX - homeChunkX) <= chunkRange &&
                                    Math.abs(targetChunkZ - homeChunkZ) <= chunkRange &&
                                    targetY < yLevelThreshold) {
                                return true;
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("無法獲取 " + other.getName() + " 的家位置 '" + homeName + "': " + e.getMessage());
                    }
                }
            }
        }
        return false;
    }

    private boolean isInProtectedArea(Player player) {
        Location playerLoc = player.getLocation();
        if (!playerLoc.getWorld().equals(protectedCenter.getWorld())) {
            return false;
        }
        return playerLoc.distance(protectedCenter) <= protectedRadius;
    }
}