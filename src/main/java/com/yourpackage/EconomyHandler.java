package com.yourpackage;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class EconomyHandler {
    private final JavaPlugin plugin;
    private Economy economy;
    private int trackCost;
    private int cancelCost;
    private String insufficientFundsMessage;
    private String trackCostDeductedMessage;
    private String cancelCostDeductedMessage;
    private String noTrackingToCancelMessage;

    public EconomyHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        initializeEconomy();
        reloadConfigValues();
    }

    private void initializeEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault 未找到，經濟功能無法啟用。");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("經濟提供者未找到，經濟功能無法啟用。");
            return;
        }
        economy = rsp.getProvider();
        plugin.getLogger().info("Vault 經濟系統已整合。");
    }

    public void reloadConfigValues() {
        trackCost = plugin.getConfig().getInt("economy.track-cost", 100);
        cancelCost = plugin.getConfig().getInt("economy.cancel-cost", 200);
        insufficientFundsMessage = plugin.getConfig().getString("messages.insufficient-funds", "您的餘額不足，無法進行此操作。");
        trackCostDeductedMessage = plugin.getConfig().getString("messages.track-cost-deducted", "已扣除 {cost} 貨幣進行追蹤。");
        cancelCostDeductedMessage = plugin.getConfig().getString("messages.cancel-cost-deducted", "已扣除 {cost} 貨幣取消追蹤。");
        noTrackingToCancelMessage = plugin.getConfig().getString("messages.no-tracking-to-cancel", "您目前沒有被追蹤。");
    }

    public boolean isEconomyEnabled() {
        return economy != null;
    }

    public boolean canAffordTrackCost(Player player) {
        if (!isEconomyEnabled()) {
            return true; // No economy, allow tracking
        }
        return economy.getBalance(player) >= trackCost;
    }

    public boolean deductTrackCost(Player player) {
        if (!isEconomyEnabled() || economy.getBalance(player) >= trackCost) {
            if (isEconomyEnabled()) {
                economy.withdrawPlayer(player, trackCost);
                player.sendMessage(trackCostDeductedMessage.replace("{cost}", String.valueOf(trackCost)));
            }
            return true;
        }
        player.sendMessage(insufficientFundsMessage);
        return false;
    }

    public boolean deductCancelCost(Player player) {
        if (!isEconomyEnabled() || economy.getBalance(player) >= cancelCost) {
            if (isEconomyEnabled()) {
                economy.withdrawPlayer(player, cancelCost);
                player.sendMessage(cancelCostDeductedMessage.replace("{cost}", String.valueOf(cancelCost)));
            }
            return true;
        }
        player.sendMessage(insufficientFundsMessage);
        return false;
    }

    public String getNoTrackingToCancelMessage() {
        return noTrackingToCancelMessage;
    }
}