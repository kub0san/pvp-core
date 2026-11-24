package org.pvpcore.commands.kits.impl.shortcuts;

import org.pvpcore.commands.kits.KitsCommands;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.util.kits.LocationAccess;
import org.pvpcore.util.kits.LocationFeature;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public abstract class AbstractShortSlotCommand implements CommandExecutor {

    private final String shortPrefix;
    private final String longPrefix;

    protected AbstractShortSlotCommand(String shortPrefix, String longPrefix) {
        this.shortPrefix = shortPrefix;
        this.longPrefix = longPrefix;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (KitsCommands.disabled(sender)) {
            return true;
        }

        if (!(sender instanceof Player player)) {
            var lang = KitsCommands.lang();
            if (lang != null) lang.sendNoPrefix(sender, "error.players-only");
            return true;
        }

        KitsContext kits = KitsCommands.activeKits();
        LocationAccess access = kits != null ? kits.locationAccess() : null;
        if (access != null && !access.require(player, shortPrefix.equals("ec") ? LocationFeature.ENDERCHESTS : LocationFeature.KITS)) {
            return true;
        }

        Integer slot = parseSlot(label);
        if (slot == null) {
            var lang = KitsCommands.lang();
            if (lang != null) lang.send(player, "error.invalid-command-label");
            return true;
        }

        int max = kits != null ? kits.maxKits() : 9;
        if (slot > max) {
            var lang = KitsCommands.lang();
            if (lang != null) lang.send(player, "error.invalid-slot-range", "min", "1", "max", String.valueOf(max));
            return true;
        }

        executeForSlot(player, slot);
        return true;
    }

    protected abstract void executeForSlot(Player player, int slot);

    public void runForSlot(Player player, int slot) {
        executeForSlot(player, slot);
    }

    private Integer parseSlot(String label) {
        String normalizedLabel = label.substring(label.indexOf(':') + 1).toLowerCase(Locale.ROOT);
        Integer fromShort = parseSlotSuffix(normalizedLabel, shortPrefix);
        if (fromShort != null) {
            return fromShort;
        }
        return parseSlotSuffix(normalizedLabel, longPrefix);
    }

    private Integer parseSlotSuffix(String label, String prefix) {
        if (!label.startsWith(prefix)) {
            return null;
        }
        return parseSlotSuffix(label.substring(prefix.length()));
    }

    private Integer parseSlotSuffix(String suffix) {
        if (suffix == null || suffix.isEmpty() || suffix.charAt(0) == '0') {
            return null;
        }
        try {
            int slot = Integer.parseInt(suffix);
            if (slot < 1 || slot > 99) {
                return null;
            }
            return slot;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
