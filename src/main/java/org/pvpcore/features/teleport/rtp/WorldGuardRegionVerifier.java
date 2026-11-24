package org.pvpcore.features.teleport.rtp;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;

/**
 * Rejects spots inside any WorldGuard region, so /rtp never drops players into an arena, a
 * spawn or a claim. This is the only class in the rtp package that touches WorldGuard types.
 * RtpModule creates it only after confirming WorldGuard is enabled, so the JVM never loads it
 * (and never needs WorldGuard) on servers without the plugin.
 */
final class WorldGuardRegionVerifier implements LocationVerifier {

    @Override
    public boolean allows(Location location) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        return query.getApplicableRegions(BukkitAdapter.adapt(location)).size() == 0;
    }
}
