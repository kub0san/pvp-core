package org.pvpcore.commands.kits.impl.shortcuts;

import org.pvpcore.features.kits.KitManager;
import org.bukkit.entity.Player;

public class ShortKitCommand extends AbstractShortSlotCommand {

    public ShortKitCommand() {
        super("k", "kit");
    }

    @Override
    protected void executeForSlot(Player player, int slot) {
        if (!org.pvpcore.commands.kits.impl.core.ActionGuards.dataReady(player)) return;

        org.pvpcore.commands.kits.KitsCommands.km().loadKit(player, slot);
        // Auto-pot is now applied centrally by KitManager when kits.load.apply-potion-effects is enabled.
    }
}
