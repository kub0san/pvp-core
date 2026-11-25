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

        // 3. Try the NEW Paper format first. Only a negative per-item length (the legacy
        // Java serialization header) falls through to the old format. Any other framing
        // error returns the items decoded so far: falling through would reinterpret new
        // bytes as legacy objects and discard the entire kit/page.
        boolean legacyFormat = false;
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int length = input.readInt();

            // Validate length to prevent OOM attacks
            if (length < 0 || length > MAX_ITEMS) {
                return new ItemStack[0];
            }

            ItemStack[] items = new ItemStack[length];
            long totalBytes = 0;

            for (int i = 0; i < length; i++) {
                int itemLength;
                try {
                    itemLength = input.readInt();
                } catch (IOException truncated) {
                    return items;
                }

                // Java's BukkitObjectInputStream uses a header (0xACED0005) which is negative.
                // If we see a negative number, this is the OLD format. Abort and fall back.
                if (itemLength < 0) {
                    legacyFormat = true;
                    break;
                }

                // Invalid lengths or truncated payloads mean no further item can be located;
                // keep what was already decoded instead of losing the whole array.
                if (itemLength > MAX_ITEM_BYTES || totalBytes + itemLength > MAX_TOTAL_BYTES) {
                    return items;
                }

                byte[] itemBytes = new byte[itemLength];
                try {
                    input.readFully(itemBytes);
                } catch (IOException truncated) {
                    return items;
                }
                totalBytes += itemLength;

                try {
                    items[i] = sanitize(ItemStack.deserializeBytes(itemBytes));
                } catch (Exception ex) {
                    // If a single item fails to deserialize (e.g., modded NBT),
                    // set it to null instead of crashing the whole kit.
                    items[i] = null;
                }
            }
            if (!legacyFormat) {
                return items;
            }

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
                    items[i] = sanitize((ItemStack) input.readObject());
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

    /**
     * Clamps a deserialized stack's amount to its type's max stack size and drops
     * stacks with a non-positive amount. Guards against oversized/corrupt blobs
     * bypassing the (default-off) item filter.
     */
    private static ItemStack sanitize(ItemStack item) {
        if (item == null) return null;
        if (item.getAmount() <= 0) return null;
        int max = item.getMaxStackSize();
        if (max > 0 && item.getAmount() > max) {
            item.setAmount(max);
        }
        return item;
    }
}