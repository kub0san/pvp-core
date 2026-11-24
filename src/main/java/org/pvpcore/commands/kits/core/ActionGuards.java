package org.pvpcore.commands.kits.core;

import org.bukkit.entity.Player;
import org.pvpcore.util.kits.LocationFeature;

/**
 * Compatibility shim for kits.gui (not modified during command refactor).
 * Delegates to the relocated implementation in {@code org.pvpcore.commands.kits.impl.core}.
 */
public final class ActionGuards {
    private ActionGuards() {}

    public static boolean allowed(Player player, String permission) {
        return org.pvpcore.commands.kits.impl.core.ActionGuards.allowed(player, permission);
    }

    public static boolean allowed(Player player, String permission, LocationFeature feature) {
        return org.pvpcore.commands.kits.impl.core.ActionGuards.allowed(player, permission, feature);
    }

    public static boolean dataReady(Player player) {
        return org.pvpcore.commands.kits.impl.core.ActionGuards.dataReady(player);
    }
}
