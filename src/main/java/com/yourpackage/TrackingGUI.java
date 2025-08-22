package com.yourpackage;

import com.earth2me.essentials.Essentials;
import com.yourpackage.trackprotection.HomeProtectionHandler;
import com.yourpackage.trackprotection.SpawnProtectionHandler;
import com.yourpackage.trackprotection.WorldProtectionHandler;
import com.yourpackage.trackprotection.YLevelProtectionHandler;
import com.yourpackage.trackprotection.PVPManagerProtectionHandler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;

public class TrackingGUI implements Listener {
    private final JavaPlugin plugin;
    private final TrackingManager trackingManager;
    private final CompassHandler compassHandler;
    private final EconomyHandler economyHandler;
    private final InsuranceManager insuranceManager;
    private final Set<UUID> trackablePlayers;
    private final HomeProtectionHandler homeProtectionHandler;
    private final SpawnProtectionHandler spawnProtectionHandler;
    private final WorldProtectionHandler worldProtectionHandler;
    private final YLevelProtectionHandler yLevelProtectionHandler;
    private final PVPManagerProtectionHandler pvpManagerProtectionHandler;
    private String cannotTrackMessage;
    private String cannotTrackTrackingMessage;
    private boolean enableInsurance;

    public TrackingGUI(JavaPlugin plugin, Essentials essentials, TrackingManager trackingManager, CompassHandler compassHandler, EconomyHandler economyHandler, InsuranceManager insuranceManager, Set<UUID> trackablePlayers, Location protectedCenter) {
        this.plugin = plugin;
        this.trackingManager = trackingManager;
        this.compassHandler = compassHandler;
        this.economyHandler = economyHandler;
        this.insuranceManager = insuranceManager;
        this.trackablePlayers = trackablePlayers;
        this.homeProtectionHandler = new HomeProtectionHandler(plugin, essentials);
        this.spawnProtectionHandler = new SpawnProtectionHandler(plugin, protectedCenter);
        this.worldProtectionHandler = new WorldProtectionHandler(plugin);
        this.yLevelProtectionHandler = new YLevelProtectionHandler(plugin);
        this.pvpManagerProtectionHandler = new PVPManagerProtectionHandler(plugin);
        reloadConfigValues();
    }

    public void reloadConfigValues() {
        cannotTrackMessage = plugin.getConfig().getString("lang.messages.cannot-track", "無法追蹤 {target}，該玩家位於生成點保護範圍、不可追蹤世界、Y座標保護範圍、家保護範圍、已關閉PvP、處於新手保護或啟用了保險。");
        cannotTrackTrackingMessage = plugin.getConfig().getString("lang.messages.cannot-track-tracking", "無法追蹤 {target}，該玩家正在追蹤他人或被追蹤。");
        enableInsurance = plugin.getConfig().getBoolean("insurance.enable", true);
        homeProtectionHandler.reloadConfigValues();
        spawnProtectionHandler.reloadConfigValues();
        worldProtectionHandler.reloadConfigValues();
        yLevelProtectionHandler.reloadConfigValues();
        pvpManagerProtectionHandler.reloadConfigValues();
        insuranceManager.reloadConfigValues();
    }

    public void openTrackingGUI(Player player) {
        if (player == null) {
            plugin.getLogger().warning("嘗試開啟 GUI 時玩家為 null");
            return;
        }
        plugin.getLogger().info("為玩家 " + player.getName() + " (UUID: " + player.getUniqueId() + ") 開啟 TrackingGUI");
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
            if (slot < 45) { // 限於 0-44 槽位
                gui.setItem(slot++, skull);
            } else {
                break;
            }
        }

        if (enableInsurance) {
            // Slot 45: 自身頭顱
            ItemStack selfSkull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta selfMeta = (SkullMeta) selfSkull.getItemMeta();
            selfMeta.setOwner(player.getName()); // 使用玩家名稱設置頭顱
            double minutes = insuranceManager.getInsuranceTime(player.getUniqueId());
            int hours = (int) (minutes / 60);
            int remainingMinutes = (int) (minutes % 60);
            boolean enabled = insuranceManager.isInsuranceEnabled(player.getUniqueId());
            String title = enabled ?
                    plugin.getConfig().getString("lang.messages.insurance-enabled-title", "&a保險：已啟用") :
                    plugin.getConfig().getString("lang.messages.insurance-disabled-title", "&c保險：已關閉");
            selfMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', title));
            selfMeta.setLore(java.util.Arrays.asList(
                    plugin.getConfig().getString("lang.messages.insurance-time-remaining", "保險剩餘時間：{hours} 小時 {minutes} 分鐘。").replace("{hours}", String.valueOf(hours)).replace("{minutes}", String.valueOf(remainingMinutes)),
                    "狀態：" + (enabled ? "啟用" : "關閉"),
                    "點擊切換啟用/關閉"
            ));
            selfSkull.setItemMeta(selfMeta);
            gui.setItem(45, selfSkull);

            // Slot 46: 購買按鈕
            ItemStack buyButton = new ItemStack(Material.GOLD_INGOT);
            ItemMeta buyMeta = buyButton.getItemMeta();
            buyMeta.setDisplayName("購買 1 小時保險");
            buyMeta.setLore(java.util.Arrays.asList(
                    "價格：" + plugin.getConfig().getDouble("insurance.cost-per-hour", 1000.0),
                    "點擊購買"
            ));
            buyButton.setItemMeta(buyMeta);
            gui.setItem(46, buyButton);
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("選擇追蹤目標")) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (enableInsurance && slot == 45) {
            // 切換保險
            insuranceManager.toggleInsurance(player);
            player.closeInventory();
            openTrackingGUI(player); // 重新開啟 GUI 以更新顯示
            return;
        }

        if (enableInsurance && slot == 46) {
            // 購買保險
            insuranceManager.buyInsurance(player);
            player.closeInventory();
            openTrackingGUI(player); // 重新開啟 GUI 以更新顯示
            return;
        }

        if (event.getCurrentItem().getType() != Material.PLAYER_HEAD) return;
        String targetName = event.getCurrentItem().getItemMeta().getDisplayName();
        Player target = Bukkit.getPlayer(targetName);
        if (target != null) {
            if (trackingManager.isTracking(target.getUniqueId()) || trackingManager.isTracked(target.getUniqueId())) {
                player.sendMessage(cannotTrackTrackingMessage.replace("{target}", targetName));
                player.closeInventory();
                return;
            }
            if (isPlayerProtected(target)) {
                player.sendMessage(cannotTrackMessage.replace("{target}", targetName));
                player.closeInventory();
                return;
            }
            if (!economyHandler.canAffordTrackCost(player)) {
                player.sendMessage(plugin.getConfig().getString("lang.messages.insufficient-funds", "您的餘額不足，無法進行此操作。"));
                player.closeInventory();
                return;
            }
            trackingManager.startTracking(player, target);
            player.closeInventory();
        }
    }

    public boolean isPlayerProtected(Player target) {
        return homeProtectionHandler.isInAnyPlayerHome(target) ||
                spawnProtectionHandler.isInProtectedArea(target) ||
                worldProtectionHandler.isInUntrackableWorld(target) ||
                yLevelProtectionHandler.isInProtectedYLevel(target) ||
                pvpManagerProtectionHandler.isPvpDisabled(target) ||
                (enableInsurance && insuranceManager.isPlayerInsured(target));
    }
}