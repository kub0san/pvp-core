package org.pvpcore.features.kits;

import org.pvpcore.util.kits.IDUtil;

import java.util.UUID;

/**
 * The two kinds of personal storage. Lets code say "kit or ender chest" with one value
 * instead of a boolean flag and two near-identical method families.
 */
public enum KitKind {
    KIT("Kit", KitContents.INVENTORY_SIZE, "perplayerkit.kit"),
    ENDERCHEST("Ender Chest", KitContents.ENDERCHEST_SIZE, "perplayerkit.enderchest");

    private final String label;
    private final int size;
    private final String permission;

    KitKind(String label, int size, String permission) {
        this.label = label;
        this.size = size;
        this.permission = permission;
    }

    /** Display name, e.g. "Ender Chest". */
    public String label() { return label; }

    /** Number of stored slots (41 for a kit, 27 for an ender chest). */
    public int size() { return size; }

    /** Permission needed to load or edit this kind. */
    public String permission() { return permission; }

    /** Storage / cache id of one slot. */
    public String id(UUID player, int slot) {
        return this == KIT ? IDUtil.getPlayerKitId(player, slot) : IDUtil.getECId(player, slot);
    }
}
