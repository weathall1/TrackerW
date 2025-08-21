package com.yourpackage;

import com.earth2me.essentials.Essentials;
import com.yourpackage.trackprotection.HomeProtectionHandler;
import com.yourpackage.trackprotection.WorldProtectionHandler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerTrackerPlugin extends JavaPlugin implements CommandExecutor {
    private Essentials essentials;
    private HomeProtectionHandler homeProtectionHandler;
    private WorldProtectionHandler worldProtectionHandler;
    private TrackingGUI trackingGUI;
    private TrackingManager trackingManager;
    private CompassHandler compassHandler;
    private CommandDisabler commandDisabler;
    private TeleportHandler teleportHandler;
    private EconomyHandler economyHandler;
    private Set<UUID> trackablePlayers;
    private BukkitRunnable guiUpdateTask;
    private int guiUpdateIntervalTicks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("lang/zh_TW.yml", false);
        loadConfigValues();

        essentials = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");

        String protectedWorldName = getConfig().getString("protected-world", "world");
        Location protectedCenter = Bukkit.getWorld(protectedWorldName) != null ?
                Bukkit.getWorld(protectedWorldName).getSpawnLocation() :
                Bukkit.getWorlds().get(0).getSpawnLocation();
        trackablePlayers = new HashSet<>(); // Initialize before any usage

        economyHandler = new EconomyHandler(this);
        homeProtectionHandler = new HomeProtectionHandler(this, essentials, protectedCenter);
        worldProtectionHandler = new WorldProtectionHandler(this);
        compassHandler = new CompassHandler(this);
        teleportHandler = new TeleportHandler(this); // Initialize TeleportHandler with plugin instance
        trackingManager = new TrackingManager(this, compassHandler, economyHandler);
        trackingGUI = new TrackingGUI(this, homeProtectionHandler, worldProtectionHandler, trackingManager, compassHandler, economyHandler, trackablePlayers);
        commandDisabler = new CommandDisabler(this, trackingManager);

        getServer().getPluginManager().registerEvents(trackingGUI, this);
        getServer().getPluginManager().registerEvents(commandDisabler, this);
        getServer().getPluginManager().registerEvents(trackingManager, this);
        getCommand("track").setExecutor(this);
        getCommand("stoptrack").setExecutor(this);
        getCommand("canceltrack").setExecutor(this);
        getCommand("playertracker").setExecutor(this);

        // Register PlaceholderAPI expansion if PlaceholderAPI is present
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PAPIExpansion(trackingManager).register();
            getLogger().info("PlaceholderAPI expansion registered successfully.");
        } else {
            getLogger().warning("PlaceholderAPI not found, placeholders will not be available.");
        }

        startGuiUpdateTask();
    }

    private void loadConfigValues() {
        guiUpdateIntervalTicks = getConfig().getInt("gui-update-interval-seconds", 60) * 20;
    }

    private void startGuiUpdateTask() {
        if (guiUpdateTask != null) {
            guiUpdateTask.cancel();
        }
        guiUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateTrackablePlayers();
            }
        };
        guiUpdateTask.runTaskTimer(this, 0, guiUpdateIntervalTicks);
        updateTrackablePlayers();
    }

    private void updateTrackablePlayers() {
        trackablePlayers.clear();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!homeProtectionHandler.isProtected(target) && !worldProtectionHandler.isInUntrackableWorld(target)) {
                trackablePlayers.add(target.getUniqueId());
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("track")) {
            trackingGUI.openTrackingGUI(player);
        } else if (cmd.getName().equalsIgnoreCase("stoptrack")) {
            trackingManager.stopTracking(player);
        } else if (cmd.getName().equalsIgnoreCase("canceltrack")) {
            trackingManager.cancelTracking(player);
        } else if (cmd.getName().equalsIgnoreCase("playertracker") && args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("playertracker.admin")) {
                player.sendMessage(getConfig().getString("messages.no-permission", "您無權執行此命令。"));
                return true;
            }
            reloadPluginConfig();
            player.sendMessage(getConfig().getString("messages.config-reloaded", "PlayerTracker 配置已重新載入。"));
        }
        return true;
    }

    private void reloadPluginConfig() {
        reloadConfig();
        loadConfigValues();
        homeProtectionHandler.reloadConfigValues();
        worldProtectionHandler.reloadConfigValues();
        trackingManager.reloadConfigValues();
        trackingGUI.reloadConfigValues();
        commandDisabler.reloadConfigValues();
        economyHandler.reloadConfigValues();
        updateTrackablePlayers();
        startGuiUpdateTask();
    }
}