package com.yourpackage;

import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CompassHandler {
    private final JavaPlugin plugin;
    private TrackingManager trackingManager;
    private final Map<UUID, BukkitRunnable> compassTasks = new HashMap<>();
    private final NamespacedKey expirationKey;
    private final String compassName;

    public CompassHandler(JavaPlugin plugin, TrackingManager trackingManager) {
        this.plugin = plugin;
        this.trackingManager = trackingManager;
        this.expirationKey = new NamespacedKey(plugin, "tracking_compass_expiration");
        this.compassName = plugin.getConfig().getString("lang.compass-name", "追蹤指南針");
        startCompassExpirationTask();
    }

    public void setTrackingManager(TrackingManager trackingManager) {
        this.trackingManager = trackingManager;
    }

    public void giveTrackingCompass(Player tracker, Player target) {
        if (trackingManager == null) {
            plugin.getLogger().warning("TrackingManager is null in CompassHandler.giveTrackingCompass");
            return;
        }
        ItemStack compass = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        meta.setDisplayName(compassName);
        updateCompass(compass, target.getLocation());

        // Add expiration timestamp to NBT
        Long startTime = trackingManager.getTrackingStartTime(tracker.getUniqueId());
        if (startTime != null) {
            long expirationTime = startTime + trackingManager.getTrackingDurationMillis();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(expirationKey, PersistentDataType.LONG, expirationTime);
        }

        compass.setItemMeta(meta);
        tracker.getInventory().addItem(compass);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!compassTasks.containsKey(tracker.getUniqueId()) || !tracker.isOnline()) {
                    this.cancel();
                    removeCompass(tracker);
                    return;
                }
                for (ItemStack item : tracker.getInventory().getContents()) {
                    if (item != null && item.getType() == Material.COMPASS) {
                        updateCompass(item, target.getLocation());
                    }
                }
            }
        };
        task.runTaskTimer(plugin, 0, 20); // Update every second (20 ticks)
        compassTasks.put(tracker.getUniqueId(), task);
    }

    private void updateCompass(ItemStack compass, Location targetLoc) {
        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        meta.setLodestone(targetLoc);
        meta.setLodestoneTracked(false);
        compass.setItemMeta(meta);
    }

    public void stopCompassTask(UUID trackerUUID) {
        BukkitRunnable task = compassTasks.remove(trackerUUID);
        if (task != null) {
            task.cancel();
        }
        Player tracker = plugin.getServer().getPlayer(trackerUUID);
        if (tracker != null) {
            removeCompass(tracker);
        }
    }

    private void removeCompass(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isTrackingCompass(item)) {
                player.getInventory().removeItem(item);
            }
        }
    }

    private boolean isTrackingCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) {
            return false;
        }
        CompassMeta meta = (CompassMeta) item.getItemMeta();
        return meta.getPersistentDataContainer().has(expirationKey, PersistentDataType.LONG);
    }

    private void startCompassExpirationTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Check all online players' inventories
                for (Player player : Bukkit.getOnlinePlayers()) {
                    for (ItemStack item : player.getInventory().getContents()) {
                        if (isTrackingCompass(item)) {
                            CompassMeta meta = (CompassMeta) item.getItemMeta();
                            PersistentDataContainer pdc = meta.getPersistentDataContainer();
                            Long expirationTime = pdc.get(expirationKey, PersistentDataType.LONG);
                            if (expirationTime != null && System.currentTimeMillis() >= expirationTime) {
                                player.getInventory().removeItem(item);
                            }
                        }
                    }
                }

                // Check all loaded containers in loaded chunks
                for (org.bukkit.World world : Bukkit.getWorlds()) {
                    for (Chunk chunk : world.getLoadedChunks()) {
                        for (BlockState blockState : chunk.getTileEntities()) {
                            if (blockState instanceof org.bukkit.block.Container) {
                                Inventory inventory = ((org.bukkit.block.Container) blockState).getInventory();
                                for (ItemStack item : inventory.getContents()) {
                                    if (isTrackingCompass(item)) {
                                        CompassMeta meta = (CompassMeta) item.getItemMeta();
                                        PersistentDataContainer pdc = meta.getPersistentDataContainer();
                                        Long expirationTime = pdc.get(expirationKey, PersistentDataType.LONG);
                                        if (expirationTime != null && System.currentTimeMillis() >= expirationTime) {
                                            inventory.removeItem(item);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 20 * 60); // Run every minute (20 ticks * 60 seconds)
    }
}