package com.yourpackage;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CompassHandler {
    private final JavaPlugin plugin;
    private final Map<UUID, BukkitRunnable> compassTasks = new HashMap<>();

    public CompassHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void giveTrackingCompass(Player tracker, Player target) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        updateCompass(compass, target.getLocation());
        tracker.getInventory().addItem(compass);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!compassTasks.containsKey(tracker.getUniqueId())) {
                    this.cancel();
                    return;
                }
                for (ItemStack item : tracker.getInventory().getContents()) {
                    if (item != null && item.getType() == Material.COMPASS) {
                        updateCompass(item, target.getLocation());
                    }
                }
            }
        };
        task.runTaskTimer(plugin, 0, 20);
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
        if (task != null) task.cancel();
    }
}