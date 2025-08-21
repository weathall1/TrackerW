package com.yourpackage.trackprotection;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class WorldProtectionHandler {
    private final JavaPlugin plugin;
    private List<String> untrackableWorlds;
    private boolean enableWorldProtection;

    public WorldProtectionHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadConfigValues();
    }

    public void reloadConfigValues() {
        enableWorldProtection = plugin.getConfig().getBoolean("world-protection.enable", true);
        untrackableWorlds = plugin.getConfig().getStringList("world-protection.untrackable-worlds");
    }

    public boolean isInUntrackableWorld(Player player) {
        if (!enableWorldProtection) {
            return false;
        }
        String worldName = player.getWorld().getName();
        return untrackableWorlds.stream().anyMatch(world -> world.equalsIgnoreCase(worldName));
    }
}