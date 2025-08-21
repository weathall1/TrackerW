package com.yourpackage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class TeleportHandler {
    private final JavaPlugin plugin;

    public TeleportHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void teleportNearTarget(Player tracker, Player target) {
        FileConfiguration config = plugin.getConfig();
        int startRadius = config.getInt("teleport.startRadius", 350); // 預設值為 0，如果 config 未設定
        int endRadius = config.getInt("teleport.endRadius", 500); // 預設值為 500，如果 config 未設定

        Location targetLoc = target.getLocation();
        World world = targetLoc.getWorld();
        String worldName = world.getName();
        String playerName = tracker.getName();
        double originX = targetLoc.getX();
        double originZ = targetLoc.getZ();

        // 以控制台身份執行 /customrtp 指令
        String command = String.format("customrtp %s %s %d %d %.0f %.0f false false",
                playerName, worldName, startRadius, endRadius, originX, originZ);
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);

        tracker.sendMessage("已傳送至目標附近500格。");
    }
}