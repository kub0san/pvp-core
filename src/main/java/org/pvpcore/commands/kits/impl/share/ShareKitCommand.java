package org.pvpcore.commands.kits.impl.share;

import org.pvpcore.features.kits.KitShareManager;

public class ShareKitCommand extends AbstractShareSlotCommand {

    public ShareKitCommand() {
        super("error.missing-kit-slot-share",
                (player, slot) -> (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().kitShareManager() : null).shareKit(player, slot),
                (player, slot, target) -> (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().kitShareManager() : null).sendKitShareRequest(player, slot, target));
    }
}
