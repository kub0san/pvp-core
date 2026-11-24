package org.pvpcore.util.kits;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.*;
import java.util.Base64;

public final class Serializer {

    // Hard limits to prevent OutOfMemory (OOM) attacks from corrupted databases
    private static final int MAX_ITEMS = 1000;
    private static final int MAX_ITEM_BYTES = 10 * 1024 * 1024; // 10MB per item is plenty for NBT
    private static final long MAX_TOTAL_BYTES = 64 * 1024 * 1024; // 64MB total

    private Serializer() {}

    public static String itemStackArrayToBase64(ItemStack[] items) {
        if (items == null) return null;

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(output);

            // Write array length
            data.writeInt(items.length);

            for (ItemStack item : items) {
                if (item == null || item.getType().isAir()) {
                    data.writeInt(0); // 0 means null/empty
                    continue;
                }

                // Thread-Safety: Clone the item to ensure another thread doesn't modify it mid-serialization
                ItemStack safeCopy = item.clone();
                byte[] bytes = safeCopy.serializeAsBytes();

                data.writeInt(bytes.length);
                data.write(bytes);
            }

            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize item array", e);
        }
    }

    @SuppressWarnings("deprecation")
    public static ItemStack[] itemStackArrayFromBase64(String data) throws IOException {
        // 1. Check for null or empty
        if (data == null || data.isEmpty()) {
            return new ItemStack[0];
        }

        // 2. Decode Base64 safely. If it's legacy garbage like "deleted", just return an empty array.
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            // Database contains legacy "deleted" strings. Treat as an empty kit/page.
            return new ItemStack[0];
        }

        // 3. Try the NEW Paper format first
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int length = input.readInt();

            // Validate length to prevent OOM attacks
            if (length < 0 || length > MAX_ITEMS) {
                return new ItemStack[0];
            }

            ItemStack[] items = new ItemStack[length];
            long totalBytes = 0;

            for (int i = 0; i < length; i++) {
                int itemLength = input.readInt();

                // Java's BukkitObjectInputStream uses a header (0xACED0005) which is negative.
                // If we see a negative number, this is the OLD format. Abort and fall back.
                if (itemLength < 0) {
                    throw new IOException("Detected legacy format header");
                }

                // Validate itemLength to prevent OOM attacks
                if (itemLength > MAX_ITEM_BYTES) {
                    throw new IOException("Item size exceeds safe limit: " + itemLength);
                }

                totalBytes += itemLength;
                if (totalBytes > MAX_TOTAL_BYTES) {
                    throw new IOException("Total array size exceeds safe limit.");
                }

                if (itemLength == 0) {
                    items[i] = null;
                    continue;
                }

                byte[] itemBytes = new byte[itemLength];
                input.readFully(itemBytes);

                try {
                    items[i] = ItemStack.deserializeBytes(itemBytes);
                } catch (Exception ex) {
                    // If a single item fails to deserialize (e.g., modded NBT),
                    // set it to null instead of crashing the whole kit.
                    items[i] = null;
                }
            }
            return items;

        } catch (IOException e) {
            // Fall through to old format
        } catch (Exception ignored) {
            // Fall through to old format
        }

        // 4. Fallback to the OLD Bukkit format
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            int length = input.readInt();

            if (length < 0 || length > MAX_ITEMS) {
                return new ItemStack[0];
            }

            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                try {
                    items[i] = (ItemStack) input.readObject();
                } catch (Exception ex) {
                    items[i] = null;
                }
            }
            return items;
        } catch (Exception e) {
            // If it fails both formats, it's unreadable. Return empty.
            return new ItemStack[0];
        }
    }
}