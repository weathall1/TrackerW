package com.yourpackage.trackprotection;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class WorldProtectionHandler {
    private final JavaPlugin plugin;
    private List<String> untrackableWorlds;

    public WorldProtectionHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadConfigValues();
    }

    public void reloadConfigValues() {
        untrackableWorlds = plugin.getConfig().getStringList("untrackable-worlds");
    }

    public boolean isInUntrackableWorld(Player player) {
        return untrackableWorlds.contains(player.getLocation().getWorld().getName());
    }
}