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
    private boolean enablePvpOffProtection;
    private boolean enableNewbieProtection;

    public PVPManagerProtectionHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.pvpManager = (PvPManager) Bukkit.getPluginManager().getPlugin("PvPManager");
        reloadConfigValues();
    }

    public void reloadConfigValues() {
        enablePvpProtection = plugin.getConfig().getBoolean("pvpmanager-protection.enable", true);
        enablePvpOffProtection = plugin.getConfig().getBoolean("pvpmanager-protection.pvpmanager-protection-pvpoff", true);
        enableNewbieProtection = plugin.getConfig().getBoolean("pvpmanager-protection.pvpmanager-protection-newbie", true);
    }

    public boolean isPvpDisabled(Player player) {
        if (!enablePvpProtection || pvpManager == null) {
            return false;
        }
        PvPlayer pvPlayer = PvPlayer.get(player);
        if (pvPlayer == null) {
            return false;
        }
        return (enablePvpOffProtection && !pvPlayer.hasPvPEnabled()) ||
                (enableNewbieProtection && pvPlayer.isNewbie());
    }
}