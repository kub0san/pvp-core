package org.pvpcore.commands.kits.impl.share;

import org.pvpcore.features.kits.KitShareManager;

public class ShareECKitCommand extends AbstractShareSlotCommand {

    public ShareECKitCommand() {
        super("error.missing-ec-slot-share",
                (player, slot) -> (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().kitShareManager() : null).shareEC(player, slot),
                (player, slot, target) -> (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().kitShareManager() : null).sendECShareRequest(player, slot, target));
    }
}
