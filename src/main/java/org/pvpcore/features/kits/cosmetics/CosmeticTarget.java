package org.pvpcore.features.kits.cosmetics;

public enum CosmeticTarget {
    BOOTS(36),
    LEGGINGS(37),
    CHESTPLATE(38),
    HELMET(39),
    SHIELD(40);

    private final int index;

    CosmeticTarget(int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }
}