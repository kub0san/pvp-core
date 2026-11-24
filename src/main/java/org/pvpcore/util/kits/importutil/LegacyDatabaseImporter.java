package org.pvpcore.util.kits.importutil;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.util.kits.Serializer;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class LegacyDatabaseImporter {

    private final File oldDbFile;
    private final CommandSender sender;

    public LegacyDatabaseImporter(File oldDbFile, CommandSender sender) {
        this.oldDbFile = oldDbFile;
        this.sender = sender;
    }

    public void importData() {
        if (!oldDbFile.exists()) {
            sender.sendMessage("\u00A7cLegacy database not found at: " + oldDbFile.getAbsolutePath());
            return;
        }

        KitsContext kits = kitsContext();
        if (kits == null || kits.plugin() == null || kits.storageManager() == null) {
            sender.sendMessage("§cKits module is not available; cannot import legacy database.");
            return;
        }

        sender.sendMessage("\u00A7eStarting legacy database import from " + oldDbFile.getName() + "...");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + oldDbFile.getAbsolutePath())) {
            // PerPlayerKit used a table called 'kits' with 'id' and 'data' columns.
            // Some versions used 'kit_data'. We'll try both.
            String tableName = findTableName(conn);
            if (tableName == null) {
                sender.sendMessage("§cCould not find the kits table in the legacy database!");
                return;
            }

            int count = 0;
            String query = "SELECT id, data FROM " + tableName;

            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    String id = rs.getString("id");
                    String data = rs.getString("data");

                    // Skip legacy deleted strings
                    if (data == null || data.equals("!delete") || data.equals("deleted")) {
                        continue;
                    }

                    // Ensure the data can actually be deserialized before saving it to the new DB
                    try {
                        Serializer.itemStackArrayFromBase64(data);
                    } catch (Exception e) {
                        kits.plugin().getLogger().warning("Skipping unreadable legacy kit: " + id);
                        continue;
                    }

                    // Write to the new database
                    kits.storageManager().saveKitDataByID(id, data);
                    count++;
                }
            }

            sender.sendMessage("\u00A7aImport complete! Successfully transferred " + count + " kits/enderchests.");
            kits.plugin().getLogger().info("[LegacyImporter] Migrated " + count + " entries from " + oldDbFile.getName());

        } catch (Exception e) {
            sender.sendMessage("§cAn error occurred during import. Check console.");
            kits.plugin().getLogger().severe("Failed to import legacy database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String findTableName(Connection conn) {
        try {
            ResultSet rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"});
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (tableName.equalsIgnoreCase("kits") || tableName.equalsIgnoreCase("kit_data") || tableName.equalsIgnoreCase("player_kits")) {
                    return tableName;
                }
            }
        } catch (Exception ignored) {}
        return "kits"; // Default fallback
    }

    private static KitsContext kitsContext() {
        try {
            PvPCorePlugin plugin = JavaPlugin.getPlugin(PvPCorePlugin.class);
            return plugin == null ? null : plugin.kitsContext();
        } catch (Throwable ignored) {
            return null;
        }
    }
}