package com.yourpackage;

import com.earth2me.essentials.Essentials;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerTrackerPlugin extends JavaPlugin implements CommandExecutor, Listener {
    private Essentials essentials;
    private TrackingGUI trackingGUI;
    private TrackingManager trackingManager;
    private TrackingEventHandler trackingEventHandler;
    private TrackingTimer trackingTimer;
    private CompassHandler compassHandler;
    private CommandDisabler commandDisabler;
    private TeleportHandler teleportHandler;
    private EconomyHandler economyHandler;
    private InsuranceManager insuranceManager;
    private Set<UUID> trackablePlayers;
    private BukkitRunnable guiUpdateTask;
    private int guiUpdateIntervalTicks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("lang/zh_TW.yml", false);
        loadConfigValues();

        essentials = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");

        String protectedWorldName = getConfig().getString("spawn-protection.protected-world", "world");
        Location protectedCenter = Bukkit.getWorld(protectedWorldName) != null ?
                Bukkit.getWorld(protectedWorldName).getSpawnLocation() :
                Bukkit.getWorlds().get(0).getSpawnLocation();
        trackablePlayers = new HashSet<>();

        economyHandler = new EconomyHandler(this);
        insuranceManager = new InsuranceManager(this, economyHandler);
        teleportHandler = new TeleportHandler(this);
        compassHandler = new CompassHandler(this, null);
        trackingTimer = new TrackingTimer(this, null, compassHandler, teleportHandler); // 先初始化 trackingTimer
        trackingManager = new TrackingManager(this, compassHandler, economyHandler, trackingTimer); // 注入 trackingTimer
        trackingEventHandler = new TrackingEventHandler(this, trackingManager, compassHandler, economyHandler, teleportHandler);
        compassHandler.setTrackingManager(trackingManager);
        trackingTimer.setTrackingManager(trackingManager); // 設置 trackingManager
        trackingGUI = new TrackingGUI(this, essentials, trackingManager, compassHandler, economyHandler, insuranceManager, trackablePlayers, protectedCenter);
        commandDisabler = new CommandDisabler(this, trackingManager);

        getServer().getPluginManager().registerEvents(trackingGUI, this);
        getServer().getPluginManager().registerEvents(commandDisabler, this);
        getServer().getPluginManager().registerEvents(trackingEventHandler, this);
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("tracker").setExecutor(this);
        getCommand("stoptrack").setExecutor(this);
        getCommand("canceltrack").setExecutor(this);
        getCommand("playertracker").setExecutor(this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PAPIExpansion(trackingManager).register();
            getLogger().info("PlaceholderAPI expansion registered successfully.");
        } else {
            getLogger().warning("PlaceholderAPI not found, placeholders will not be available.");
        }

        startGuiUpdateTask();
    }

    @Override
    public void onDisable() {
        insuranceManager.close();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        insuranceManager.registerPlayer(event.getPlayer());
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
            if (!trackingGUI.isPlayerProtected(target)) {
                trackablePlayers.add(target.getUniqueId());
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("tracker")) {
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
        trackingManager.reloadConfigValues();
        trackingGUI.reloadConfigValues();
        commandDisabler.reloadConfigValues();
        economyHandler.reloadConfigValues();
        insuranceManager.reloadConfigValues();
        trackingEventHandler.reloadConfigValues();
        trackingTimer.reloadConfigValues();
        updateTrackablePlayers();
        startGuiUpdateTask();
    }
}