package org.pvpcore.util.kits;
import org.pvpcore.util.WorldGuardSupport;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Evaluates global and action rules against the location at the time of use. */
public final class LocationAccess {
    private static LocationAccess instance; // why: singleton instance pointer retained for legacy static lifecycle compatibility.
    private final Plugin plugin;
    private final org.pvpcore.features.kits.KitsContext context;
    private final Map<LocationFeature, LocationRules.Rule> rules;
    private final Set<String> warnings = new HashSet<>();

    public LocationAccess(org.pvpcore.features.kits.KitsContext context) {
        this.context = java.util.Objects.requireNonNull(context, "context");
        this.plugin = context.plugin();
        this.rules = LocationRules.parse(context.config());
        instance = this;
    }

    public record Decision(boolean allowed, String rule, String reasonKey, boolean unavailable) {}

    public Decision check(Player player, LocationFeature feature) {
        RegionLookup lookup = new RegionLookup(player);
        for (LocationFeature current : feature == LocationFeature.GLOBAL
                ? List.of(LocationFeature.GLOBAL) : List.of(LocationFeature.GLOBAL, feature)) {
            LocationRules.Rule rule = rules.get(current);
            if (rule == null) continue;
            String path = "locations." + current.key();
            try {
                if (!rule.permits(player.getWorld().getName(), lookup::get))
                    return new Decision(false, path, rule.allow() ? "info.location-allow-miss" : "info.location-deny-match", false);
            } catch (RegionUnavailableException e) {
                warn(path, player, e);
                return new Decision(false, path, "info.location-rule-unavailable", true);
            }
        }
        return new Decision(true, "locations." + feature.key(), "info.location-rule-allowed", false);
    }

    public boolean require(Player player, LocationFeature feature) {
        Decision decision = check(player, feature);
        if (decision.allowed()) return true;
        String message = "error.disabled-at-location";
        LocationRules.Rule global = rules.get(LocationFeature.GLOBAL);
        if (decision.rule().equals("locations.global") && global != null
                && global.entries().stream().allMatch(entry -> entry.region() == null)) message = "error.disabled-in-world";
        if (context.lang() != null) {
            context.lang().sendNoPrefix(player, decision.unavailable() ? "error.regions-unavailable" : message);
        }
        SoundManager.playFailure(player);
        return false;
    }

    /** Only callers with a relevant region entry need WorldGuard. */
    public List<String> regions(Player player) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard"))
            throw new RegionUnavailableException("WorldGuard is not enabled");
        try { return WorldGuardSupport.getRegionIdsByPriority(player); }
        catch (RuntimeException | LinkageError e) {
            throw new RegionUnavailableException("WorldGuard regions are unavailable: " + e.getMessage());
        }
    }

    public void warn(String path, Player player, RegionUnavailableException error) {
        String message = path + " in world '" + player.getWorld().getName() + "': " + error.getMessage();
        if (warnings.add(message)) plugin.getLogger().warning(message + ". Skipping affected actions until regions are available.");
    }

    /** why: static shutdown retained for module disable/reload lifecycle cleanup of singleton-style state. */
    public static void shutdown() {
        instance = null;
    }

    public static final class RegionUnavailableException extends RuntimeException {
        public RegionUnavailableException(String message) { super(message); }
    }

    private final class RegionLookup {
        private final Player player;
        private List<String> ids;
        RegionLookup(Player player) { this.player = player; }
        List<String> get() {
            if (ids == null) ids = regions(player);
            return ids;
        }
    }
}
