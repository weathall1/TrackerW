package com.yourpackage.trackprotection;

import me.NoChance.PvPManager.PvPManager;
import me.NoChance.PvPManager.PvPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class PVPManagerProtectionHandler {
    private final JavaPlugin plugin;
    private final PvPManager pvpManager;
    private boolean enablePvpProtection;

    public PVPManagerProtectionHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.pvpManager = (PvPManager) Bukkit.getPluginManager().getPlugin("PvPManager");
        reloadConfigValues();
    }

    public void reloadConfigValues() {
        enablePvpProtection = plugin.getConfig().getBoolean("pvp-protection.enable", true);
    }

    public boolean isPvpDisabled(Player player) {
        if (!enablePvpProtection || pvpManager == null) {
            return false;
        }
        PvPlayer pvPlayer = PvPlayer.get(player);
        return pvPlayer != null && !pvPlayer.hasPvPEnabled();
    }
}