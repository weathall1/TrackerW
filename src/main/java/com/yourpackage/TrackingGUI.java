package com.yourpackage;

import com.earth2me.essentials.Essentials;
import com.yourpackage.trackprotection.HomeProtectionHandler;
import com.yourpackage.trackprotection.SpawnProtectionHandler;
import com.yourpackage.trackprotection.WorldProtectionHandler;
import com.yourpackage.trackprotection.YLevelProtectionHandler;
import com.yourpackage.trackprotection.PVPManagerProtectionHandler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;

public class TrackingGUI implements Listener {
    private final JavaPlugin plugin;
    private final TrackingManager trackingManager;
    private final CompassHandler compassHandler;
    private final EconomyHandler economyHandler;
    private final Set<UUID> trackablePlayers;
    private final HomeProtectionHandler homeProtectionHandler;
    private final SpawnProtectionHandler spawnProtectionHandler;
    private final WorldProtectionHandler worldProtectionHandler;
    private final YLevelProtectionHandler yLevelProtectionHandler;
    private final PVPManagerProtectionHandler pvpManagerProtectionHandler;
    private String cannotTrackMessage;
    private String cannotTrackTrackingMessage;

    public TrackingGUI(JavaPlugin plugin, Essentials essentials, TrackingManager trackingManager, CompassHandler compassHandler, EconomyHandler economyHandler, Set<UUID> trackablePlayers, Location protectedCenter) {
        this.plugin = plugin;
        this.trackingManager = trackingManager;
        this.compassHandler = compassHandler;
        this.economyHandler = economyHandler;
        this.trackablePlayers = trackablePlayers;
        this.homeProtectionHandler = new HomeProtectionHandler(plugin, essentials);
        this.spawnProtectionHandler = new SpawnProtectionHandler(plugin, protectedCenter);
        this.worldProtectionHandler = new WorldProtectionHandler(plugin);
        this.yLevelProtectionHandler = new YLevelProtectionHandler(plugin);
        this.pvpManagerProtectionHandler = new PVPManagerProtectionHandler(plugin);
        reloadConfigValues();
    }

    public void reloadConfigValues() {
        cannotTrackMessage = plugin.getConfig().getString("messages.cannot-track", "無法追蹤 {target}，該玩家位於生成點保護範圍、不可追蹤世界、Y座標保護範圍、家保護範圍或已關閉PvP。");
        cannotTrackTrackingMessage = plugin.getConfig().getString("messages.cannot-track-tracking", "無法追蹤 {target}，該玩家正在追蹤他人或被追蹤。");
        homeProtectionHandler.reloadConfigValues();
        spawnProtectionHandler.reloadConfigValues();
        worldProtectionHandler.reloadConfigValues();
        yLevelProtectionHandler.reloadConfigValues();
        pvpManagerProtectionHandler.reloadConfigValues();
    }

    public void openTrackingGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, "選擇追蹤目標");
        int slot = 0;
        for (UUID targetUUID : trackablePlayers) {
            Player target = Bukkit.getPlayer(targetUUID);
            if (target == null || target.equals(player)) continue;
            if (trackingManager.isTracking(targetUUID) || trackingManager.isTracked(targetUUID)) continue;

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(target);
            meta.setDisplayName(target.getName());
            skull.setItemMeta(meta);
            gui.setItem(slot++, skull);
        }
        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("選擇追蹤目標")) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() != Material.PLAYER_HEAD) return;
        Player tracker = (Player) event.getWhoClicked();
        String targetName = event.getCurrentItem().getItemMeta().getDisplayName();
        Player target = Bukkit.getPlayer(targetName);
        if (target != null) {
            if (trackingManager.isTracking(target.getUniqueId()) || trackingManager.isTracked(target.getUniqueId())) {
                tracker.sendMessage(cannotTrackTrackingMessage.replace("{target}", targetName));
                tracker.closeInventory();
                return;
            }
            if (isPlayerProtected(target)) {
                tracker.sendMessage(cannotTrackMessage.replace("{target}", targetName));
                tracker.closeInventory();
                return;
            }
            if (!economyHandler.canAffordTrackCost(tracker)) {
                tracker.sendMessage(plugin.getConfig().getString("messages.insufficient-funds", "您的餘額不足，無法進行此操作。"));
                tracker.closeInventory();
                return;
            }
            trackingManager.startTracking(tracker, target);
            tracker.closeInventory();
        }
    }

    public boolean isPlayerProtected(Player target) {
        return homeProtectionHandler.isInAnyPlayerHome(target) ||
                spawnProtectionHandler.isInProtectedArea(target) ||
                worldProtectionHandler.isInUntrackableWorld(target) ||
                yLevelProtectionHandler.isInProtectedYLevel(target) ||
                pvpManagerProtectionHandler.isPvpDisabled(target);
    }
}