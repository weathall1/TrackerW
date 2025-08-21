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
    private int chunkRange;
    private boolean enableHomeProtection;

    public HomeProtectionHandler(JavaPlugin plugin, Essentials essentials) {
        this.plugin = plugin;
        this.essentials = essentials;
        reloadConfigValues();
    }

    public void reloadConfigValues() {
        chunkRange = plugin.getConfig().getInt("home-protection.chunk-range", 1);
        enableHomeProtection = plugin.getConfig().getBoolean("home-protection.enable", true);
    }

    public boolean isInAnyPlayerHome(Player target) {
        if (!enableHomeProtection) return false;
        if (essentials == null) {
            return false; // No EssentialsX, skip home checks
        }

        Location targetLoc = target.getLocation();
        Chunk targetChunk = targetLoc.getChunk();
        int targetChunkX = targetChunk.getX();
        int targetChunkZ = targetChunk.getZ();

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
                                    Math.abs(targetChunkZ - homeChunkZ) <= chunkRange) {
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
}