package org.pvpcore.gui.kits.editor;

import org.bukkit.entity.Player;
import org.pvpcore.features.kits.cosmetics.CustomizeSession;
import org.pvpcore.gui.kits.GUI;

/**
 * Staged cosmetic editor screen. Full interaction wiring lives in later tasks;
 * this skeleton exists so layout constants and entry points compile.
 */
public final class CustomizePage {
    private CustomizePage() {
    }

    public static void open(GUI gui, Player player, int kitSlot, CustomizeSession session) {
        // Placeholder: real rendering implemented in Tasks 6/7 integration.
    }

    public static void reopen(GUI gui, Player player, int kitSlot, CustomizeSession session) {
        open(gui, player, kitSlot, session);
    }
}