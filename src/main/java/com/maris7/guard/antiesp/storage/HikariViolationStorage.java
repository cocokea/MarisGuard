package com.maris7.guard.antiesp.storage;

import com.maris7.guard.MarisGuard;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class HikariViolationStorage implements ViolationStorage {
    private final MarisGuard plugin;
    private HikariDataSource dataSource;
    private ExecutorService executor;
    private String storageType;

    public HikariViolationStorage(MarisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start() {
        FileConfiguration config = plugin.getConfig();
        this.storageType = config.getString("storage.type", "sqlite").toLowerCase(Locale.ROOT);

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("Esper");
        hikari.setMaximumPoolSize(Math.max(2, config.getInt("storage.pool.maximum-pool-size", 4)));
        hikari.setMinimumIdle(Math.max(1, config.getInt("storage.pool.minimum-idle", 1)));
        hikari.setConnectionTimeout(Math.max(2500L, config.getLong("storage.pool.connection-timeout-ms", 10000L)));
        hikari.setLeakDetectionThreshold(0L);

        if ("mysql".equals(storageType)) {
            String host = config.getString("storage.mysql.host", "127.0.0.1");
            int port = config.getInt("storage.mysql.port", 3306);
            String database = config.getString("storage.mysql.database", "antiesp");
            String params = config.getString("storage.mysql.properties", "useSSL=false&characterEncoding=utf8&serverTimezone=UTC");
            hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?" + params);
            hikari.setUsername(config.getString("storage.mysql.username", "root"));
            hikari.setPassword(config.getString("storage.mysql.password", ""));
            hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
        } else {
            File dbFile = new File(plugin.getDataFolder(), config.getString("storage.sqlite.file", "violations.db"));
            hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setMaximumPoolSize(1);
            plugin.getLogger().warning("Esper violation storage is using SQLite with a single Hikari connection; MySQL is recommended for busy servers.");
        }

        this.dataSource = new HikariDataSource(hikari);
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Esper-VL-Save");
            thread.setDaemon(true);
            return thread;
        });
        createTable();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS antiesp_violations (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "name VARCHAR(16) NOT NULL," +
                "violations INT NOT NULL DEFAULT 0," +
                "updated_at BIGINT NOT NULL" +
                ")";
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create antiesp_violations table", exception);
        }
    }

    @Override
    public int loadViolations(UUID playerId) {
        String sql = "SELECT violations FROM antiesp_violations WHERE uuid = ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Math.max(0, resultSet.getInt("violations"));
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to load violations for " + playerId + ": " + exception.getMessage());
        }
        return 0;
    }

    @Override
    public void saveViolationsAsync(UUID playerId, String playerName, int violations) {
        if (executor == null || dataSource == null) {
            return;
        }
        executor.execute(() -> saveNow(playerId, playerName, violations));
    }

    private void saveNow(UUID playerId, String playerName, int violations) {
        String sql = "mysql".equals(storageType)
                ? "INSERT INTO antiesp_violations (uuid, name, violations, updated_at) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name), violations = VALUES(violations), updated_at = VALUES(updated_at)"
                : "INSERT INTO antiesp_violations (uuid, name, violations, updated_at) VALUES (?, ?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, violations = excluded.violations, updated_at = excluded.updated_at";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, playerName == null ? "unknown" : playerName);
            statement.setInt(3, Math.max(0, violations));
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to save violations for " + playerId + ": " + exception.getMessage());
        }
    }

    @Override
    public void stop() {
        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }
}
