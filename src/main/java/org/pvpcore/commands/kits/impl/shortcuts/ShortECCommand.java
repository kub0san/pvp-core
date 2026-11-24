package org.pvpcore.commands.kits.impl.shortcuts;

import org.pvpcore.features.kits.KitManager;
import org.bukkit.entity.Player;

public class ShortECCommand extends AbstractShortSlotCommand {

    public ShortECCommand() {
        super("ec", "enderchest");
    }

    @Override
    protected void executeForSlot(Player player, int slot) {
        if (!org.pvpcore.commands.kits.impl.core.ActionGuards.dataReady(player)) return;
        org.pvpcore.commands.kits.KitsCommands.km().loadEnderchest(player, slot);
    }
}
