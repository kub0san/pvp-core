/*
 * Copyright 2022-2026 Noah Ross
 *
 * This file is part of Kits.
 *
 * Kits is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Kits is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Kits. If not, see <https://www.gnu.org/licenses/>.
 */
package org.pvpcore.storage.kits.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.Plugin;
import org.pvpcore.storage.kits.BackupCapable;
import org.pvpcore.storage.kits.KitStorageException;
import org.pvpcore.storage.kits.StorageConnectionException;
import org.pvpcore.storage.kits.StorageManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import static org.pvpcore.storage.kits.KitStorageException.Operation.*;

/**
 * SQLite-backed kits storage (single backend). Owns connection, schema, and queries.
 */
public class SQLite implements StorageManager, BackupCapable {

    private static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS kits (KITID VARCHAR(100), KITDATA TEXT(15000), PRIMARY KEY (KITID))";
    private static final String UPSERT = "REPLACE INTO kits (KITID, KITDATA) VALUES (?,?)";

    private final Plugin plugin;
    private final String databasePath;
    // getConnection() may be called from the async storage worker and from main-thread reads;
    // both can observe "not connected" at once and must not each create a pool.
    private volatile HikariDataSource dataSource;

    public SQLite(Plugin plugin) {
        this.plugin = plugin;
        this.databasePath = plugin.getDataFolder().getAbsolutePath().replace('\\', '/') + "/database.db";
    }

    @Override
    public boolean isConnected() {
        return (dataSource != null && !dataSource.isClosed());
    }

    @Override
    public synchronized void connect() throws StorageConnectionException {
        if (isConnected()) {
            return;
        }
        try {
            // Ensure plugin data folder exists
            plugin.getDataFolder().mkdirs();

            HikariConfig config = new HikariConfig();

            // Modern SQLite JDBC URL with optimized parameters (2025 best practices)
            config.setJdbcUrl("jdbc:sqlite:" + databasePath +
                    "?journal_mode=WAL" + // WAL mode for better concurrency
                    "&synchronous=NORMAL" + // Balance between safety and performance
                    "&cache_size=10000" + // 10MB cache (negative = KB, positive = pages)
                    "&temp_store=MEMORY" + // Use memory for temp tables
                    "&mmap_size=268435456" + // 256MB memory-mapped I/O
                    "&foreign_keys=ON" + // Enable foreign key constraints
                    "&busy_timeout=30000"); // 30 second busy timeout

            config.setDriverClassName("org.sqlite.JDBC");
            config.setPoolName("SQLite-Pool");

            // SQLite-specific pool configuration (optimized for single-writer architecture)
            config.setMaximumPoolSize(1); // SQLite is single-writer, use 1 connection
            config.setMinimumIdle(1); // Keep one connection alive
            config.setConnectionTimeout(30000); // 30 seconds connection timeout
            // No idleTimeout: with maximumPoolSize == minimumIdle the pool is fixed size,
            // so idle connections are never retired and HikariCP warns if it is set
            config.setMaxLifetime(1800000); // 30 minutes max connection lifetime
            config.setLeakDetectionThreshold(60000); // 60 seconds leak detection
            config.setKeepaliveTime(30000); // 30 seconds keepalive (HikariCP 4.0+)

            // Modern HikariCP settings for better performance
            config.setInitializationFailTimeout(10000); // 10 seconds initialization timeout
            config.setValidationTimeout(5000); // 5 seconds validation timeout
            config.setConnectionTestQuery("SELECT 1"); // Lightweight connection test
            config.setAutoCommit(true); // SQLite default
            config.setReadOnly(false); // Allow writes
            config.setIsolateInternalQueries(false); // Don't isolate internal queries
            config.setRegisterMbeans(false); // Disable JMX for performance
            config.setAllowPoolSuspension(true); // Allow pool suspension for maintenance

            // SQLite-specific connection properties (optimized for modern usage)
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "500"); // Increased from 250
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "4096"); // Increased from 2048
            config.addDataSourceProperty("useServerPrepStmts", "false"); // SQLite doesn't support server-side prepared statements
            config.addDataSourceProperty("rewriteBatchedStatements", "true"); // Optimize batch operations
            config.addDataSourceProperty("cacheResultSetMetadata", "true"); // Cache metadata for performance
            config.addDataSourceProperty("cacheServerConfiguration", "true"); // Cache server config
            config.addDataSourceProperty("elideSetAutoCommits", "true"); // Optimize auto-commit calls
            config.addDataSourceProperty("maintainTimeStats", "false"); // Disable time stats for performance

            dataSource = new HikariDataSource(config);
        } catch (RuntimeException e) {
            throw new StorageConnectionException("Failed to connect to the database", e);
        }
    }

    @Override
    public void init() throws StorageConnectionException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(CREATE_TABLE)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageConnectionException("Failed to initialize the database", e);
        }
    }

    @Override
    public synchronized void close() throws StorageConnectionException {
        if (!isConnected()) {
            return;
        }
        try {
            dataSource.close();
        } catch (RuntimeException e) {
            throw new StorageConnectionException("Failed to close the database connection", e);
        }
    }

    @Override
    public boolean supportsNativeBackup() {
        return true;
    }

    @Override
    public void keepAlive() throws StorageConnectionException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1")) {
            ps.executeQuery();
        } catch (SQLException e) {
            throw new StorageConnectionException("Failed to keep the connection alive", e);
        }
    }

    @Override
    public void saveKitDataByID(String kitID, String data) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT)) {
            ps.setString(1, kitID);
            ps.setString(2, data);
            ps.executeUpdate();
        } catch (SQLException | StorageConnectionException e) {
            throw new KitStorageException(SAVE, kitID, e);
        }
    }

    @Override
    public String getKitDataByID(String kitID) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT KITDATA FROM kits WHERE KITID=?")) {
            ps.setString(1, kitID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("KITDATA");
                }
            }
        } catch (SQLException | StorageConnectionException e) {
            throw new KitStorageException(READ, kitID, e);
        }
        return null; // no row is a normal miss; real failures throw KitStorageException above
    }

    @Override
    public boolean doesKitExistByID(String kitID) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT KITID FROM kits WHERE KITID=?")) {
            ps.setString(1, kitID);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException | StorageConnectionException e) {
            throw new KitStorageException(EXISTS, kitID, e);
        }
    }

    @Override
    public void deleteKitByID(String kitID) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM kits WHERE KITID=?")) {
            ps.setString(1, kitID);
            ps.executeUpdate();
        } catch (SQLException | StorageConnectionException e) {
            throw new KitStorageException(DELETE, kitID, e);
        }
    }

    @Override
    public Set<String> getAllKitIDs() {
        Set<String> kitIDs = new HashSet<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT KITID FROM kits");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                kitIDs.add(rs.getString("KITID"));
            }
        } catch (SQLException | StorageConnectionException e) {
            throw new KitStorageException(LIST_IDS, null, e);
        }
        return kitIDs;
    }

    private Connection getConnection() throws SQLException, StorageConnectionException {
        if (!isConnected()) {
            connect();
        }
        return dataSource.getConnection();
    }

    /**
     * Snapshot the database with {@code VACUUM INTO} (SQLite 3.27+). SQLite reads
     * the database inside a transaction and writes a fresh, defragmented copy that
     * already contains everything sitting in the WAL, so the result is consistent
     * without pausing the server and without copying the -wal/-shm sidecars.
     */
    @Override
    public void backupTo(Path target) throws StorageConnectionException {
        // VACUUM INTO refuses to overwrite, so clear the target first.
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new StorageConnectionException("Failed to prepare SQLite backup target " + target, e);
        }

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("VACUUM INTO ?")) {
            ps.setString(1, target.toAbsolutePath().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            // Do not leave a half-written snapshot behind for the retention
            // sweep to hand back to someone as a valid backup.
            try {
                Files.deleteIfExists(target);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw new StorageConnectionException("Failed to back up the database to " + target, e);
        } catch (StorageConnectionException e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }
}