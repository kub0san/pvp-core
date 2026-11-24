package org.pvpcore.commands.kits.impl.inspect;

import org.pvpcore.features.kits.KitManager;
import org.pvpcore.gui.kits.GUI;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.UUID;

public class InspectEcCommand extends AbstractInspectCommand {

    public InspectEcCommand(Plugin plugin) {
        super(plugin);
    }

    @Override
    protected String usageCommand() {
        return "inspectec";
    }

    @Override
    protected boolean hasData(UUID targetUuid, int slot) {
        return org.pvpcore.commands.kits.KitsCommands.km().hasEC(targetUuid, slot);
    }

    @Override
    protected void openInspectGui(Player inspector, UUID targetUuid, int slot) {
        GUI gui = GUI.getOrCreate(plugin, org.pvpcore.commands.kits.KitsCommands.activeKits());
        gui.InspectEc(inspector, targetUuid, slot);
    }

    @Override
    protected void loadSlotFromDB(UUID targetUuid, int slot) {
        org.pvpcore.commands.kits.KitsCommands.km().loadPlayerEnderchestFromDB(targetUuid, slot);
    }

    @Override
    protected String missingDataKey() {
        return "error.inspect-ec-missing";
    }

    @Override
    protected String loadErrorLogMessage() {
        return "Error loading enderchest data";
    }

    @Override
    protected String loadErrorUserKey() {
        return "error.inspect-ec-load-error";
    }
}
