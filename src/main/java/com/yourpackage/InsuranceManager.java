package com.yourpackage;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InsuranceManager {
    private final JavaPlugin plugin;
    private final EconomyHandler economyHandler;
    private Connection connection;
    private final Map<UUID, Double> insuranceTimeCache = new HashMap<>();
    private final Map<UUID, Boolean> insuranceEnabledCache = new HashMap<>();
    private boolean enableInsurance;
    private double costPerHour;
    private double newPlayerHours;
    private Sound enableSound;
    private Sound disableSound;
    private Sound purchaseSuccessSound;
    private Sound purchaseFailSound;

    public InsuranceManager(JavaPlugin plugin, EconomyHandler economyHandler) {
        this.plugin = plugin;
        this.economyHandler = economyHandler;
        reloadConfigValues();
        initializeDatabase();
        if (enableInsurance) {
            startTimer();
        }
    }

    public void reloadConfigValues() {
        enableInsurance = plugin.getConfig().getBoolean("insurance.enable", true);
        costPerHour = plugin.getConfig().getDouble("insurance.cost-per-hour", 1000.0);
        newPlayerHours = plugin.getConfig().getDouble("insurance.new-player-hours", 16.0);
        try {
            enableSound = Sound.valueOf(plugin.getConfig().getString("insurance.sounds.enable", "ENTITY_EXPERIENCE_ORB_PICKUP"));
            disableSound = Sound.valueOf(plugin.getConfig().getString("insurance.sounds.disable", "BLOCK_NOTE_BLOCK_BASS"));
            purchaseSuccessSound = Sound.valueOf(plugin.getConfig().getString("insurance.sounds.purchase-success", "ENTITY_PLAYER_LEVELUP"));
            purchaseFailSound = Sound.valueOf(plugin.getConfig().getString("insurance.sounds.purchase-fail", "BLOCK_ANVIL_LAND"));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("無效的聲音配置，使用預設聲音: " + e.getMessage());
            enableSound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            disableSound = Sound.BLOCK_NOTE_BLOCK_BASS;
            purchaseSuccessSound = Sound.ENTITY_PLAYER_LEVELUP;
            purchaseFailSound = Sound.BLOCK_ANVIL_LAND;
        }
    }

    private void initializeDatabase() {
        String type = plugin.getConfig().getString("Database.Type", "SQLite");
        if ("SQLite".equalsIgnoreCase(type)) {
            try {
                connection = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder() + "/insurance.db");
                createTable();
            } catch (SQLException e) {
                plugin.getLogger().warning("無法初始化 SQLite 資料庫: " + e.getMessage());
            }
        } else if ("MySQL".equalsIgnoreCase(type)) {
            String host = plugin.getConfig().getString("Database.MySQL.Host", "127.0.0.1");
            int port = plugin.getConfig().getInt("Database.MySQL.Port", 3306);
            String user = plugin.getConfig().getString("Database.MySQL.Username", "root");
            String pass = plugin.getConfig().getString("Database.MySQL.Password", "12345");
            String db = plugin.getConfig().getString("Database.MySQL.Database", "playertracker");
            try {
                connection = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false", user, pass);
                createTable();
            } catch (SQLException e) {
                plugin.getLogger().warning("無法初始化 MySQL 資料庫: " + e.getMessage());
            }
        }
    }

    private void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS insurance (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "insurance_time DOUBLE NOT NULL," +
                "is_enabled BOOLEAN NOT NULL" +
                ");";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.execute();
        }
    }

    public void registerPlayer(Player player) {
        if (!enableInsurance) return;
        UUID uuid = player.getUniqueId();
        try {
            String sql = "SELECT * FROM insurance WHERE uuid = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) {
                    // 首次註冊，給予新玩家保險，預設啟用
                    double initialTime = newPlayerHours * 60; // 轉換為分鐘
                    sql = "INSERT INTO insurance (uuid, insurance_time, is_enabled) VALUES (?, ?, ?)";
                    try (PreparedStatement insertStmt = connection.prepareStatement(sql)) {
                        insertStmt.setString(1, uuid.toString());
                        insertStmt.setDouble(2, initialTime);
                        insertStmt.setBoolean(3, true); // 預設啟用保險
                        insertStmt.executeUpdate();
                    }
                    insuranceTimeCache.put(uuid, initialTime);
                    insuranceEnabledCache.put(uuid, true); // 預設啟用
                    // 通知玩家保險已啟用
                    player.sendMessage(plugin.getConfig().getString("lang.messages.insurance-enabled", "保險已啟用。"));
                    player.playSound(player.getLocation(), enableSound, 1.0f, 1.0f);
                } else {
                    insuranceTimeCache.put(uuid, rs.getDouble("insurance_time"));
                    insuranceEnabledCache.put(uuid, rs.getBoolean("is_enabled"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("無法註冊玩家 " + player.getName() + ": " + e.getMessage());
        }
    }

    public double getInsuranceTime(UUID uuid) {
        return enableInsurance ? insuranceTimeCache.getOrDefault(uuid, 0.0) : 0.0;
    }

    public boolean isInsuranceEnabled(UUID uuid) {
        return enableInsurance && insuranceEnabledCache.getOrDefault(uuid, false);
    }

    public void toggleInsurance(Player player) {
        if (!enableInsurance) {
            player.sendMessage(plugin.getConfig().getString("lang.messages.insurance-disabled", "保險已關閉。"));
            player.playSound(player.getLocation(), purchaseFailSound, 1.0f, 1.0f);
            return;
        }
        UUID uuid = player.getUniqueId();
        boolean enabled = !isInsuranceEnabled(uuid);
        insuranceEnabledCache.put(uuid, enabled);
        updateDatabase(uuid);
        String message = enabled ? plugin.getConfig().getString("lang.messages.insurance-enabled", "保險已啟用。") : plugin.getConfig().getString("lang.messages.insurance-disabled", "保險已關閉。");
        player.sendMessage(message);
        player.playSound(player.getLocation(), enabled ? enableSound : disableSound, 1.0f, 1.0f);
    }

    public void buyInsurance(Player player) {
        if (!enableInsurance) {
            player.sendMessage(plugin.getConfig().getString("lang.messages.insurance-disabled", "保險已關閉。"));
            player.playSound(player.getLocation(), purchaseFailSound, 1.0f, 1.0f);
            return;
        }
        if (!economyHandler.canAfford(player, costPerHour)) {
            player.sendMessage(plugin.getConfig().getString("lang.messages.insurance-no-money", "餘額不足，無法購買保險。"));
            player.playSound(player.getLocation(), purchaseFailSound, 1.0f, 1.0f);
            return;
        }
        economyHandler.deduct(player, costPerHour);
        double currentTime = getInsuranceTime(player.getUniqueId());
        double addedTime = 60; // 1 小時 = 60 分鐘
        insuranceTimeCache.put(player.getUniqueId(), currentTime + addedTime);
        updateDatabase(player.getUniqueId());
        player.playSound(player.getLocation(), purchaseSuccessSound, 1.0f, 1.0f);
    }

    public boolean isPlayerInsured(Player player) {
        if (!enableInsurance) return false;
        UUID uuid = player.getUniqueId();
        return isInsuranceEnabled(uuid) && getInsuranceTime(uuid) > 0;
    }

    private void updateDatabase(UUID uuid) {
        if (!enableInsurance) return;
        try {
            String sql = "REPLACE INTO insurance (uuid, insurance_time, is_enabled) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setDouble(2, insuranceTimeCache.getOrDefault(uuid, 0.0));
                stmt.setBoolean(3, insuranceEnabledCache.getOrDefault(uuid, false));
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("無法更新玩家 " + uuid + " 的保險數據: " + e.getMessage());
        }
    }

    private void startTimer() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!enableInsurance) return;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    if (isInsuranceEnabled(uuid) && getInsuranceTime(uuid) > 0) {
                        double remaining = getInsuranceTime(uuid) - 1; // 減 1 分鐘
                        insuranceTimeCache.put(uuid, Math.max(remaining, 0));
                        updateDatabase(uuid);
                        if (remaining <= 0) {
                            player.sendMessage(plugin.getConfig().getString("lang.messages.insurance-zero-time", "您無剩餘保險時間。"));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 1200); // 每分鐘 (1200 ticks = 60 秒)
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("無法關閉資料庫連線: " + e.getMessage());
        }
    }
}