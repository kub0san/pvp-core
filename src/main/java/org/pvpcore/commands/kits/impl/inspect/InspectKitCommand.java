package org.pvpcore.commands.kits.impl.inspect;

import org.pvpcore.features.kits.KitManager;
import org.pvpcore.gui.kits.GUI;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.UUID;

public class InspectKitCommand extends AbstractInspectCommand {

    public InspectKitCommand(Plugin plugin) {
        super(plugin);
    }

    @Override
    protected String usageCommand() {
        return "inspectkit";
    }

    @Override
    protected boolean hasData(UUID targetUuid, int slot) {
        return org.pvpcore.commands.kits.KitsCommands.km().hasKit(targetUuid, slot);
    }

    @Override
    protected void openInspectGui(Player inspector, UUID targetUuid, int slot) {
        GUI gui = GUI.getOrCreate(plugin, org.pvpcore.commands.kits.KitsCommands.activeKits());
        gui.InspectKit(inspector, targetUuid, slot);
    }

    @Override
    protected void loadSlotFromDB(UUID targetUuid, int slot) {
        org.pvpcore.commands.kits.KitsCommands.km().loadPlayerKitFromDB(targetUuid, slot);
    }

    @Override
    protected String missingDataKey() {
        return "error.inspect-kit-missing";
    }

    @Override
    protected String loadErrorLogMessage() {
        return "Error loading kit data";
    }

    @Override
    protected String loadErrorUserKey() {
        return "error.inspect-kit-load-error";
    }
}
