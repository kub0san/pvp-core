package org.pvpcore.features.worldgen;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Applies PvPCore world rules to worlds that use {@link ChunkGenerator} and warns when a
 * world listed in {@code worldgen.expected-worlds} loads without it.
 *
 * <p>With PvPCore at {@code load: STARTUP}, this listener is registered before any world
 * loads, so it also covers the worlds that {@link GenerationModule#enable()} used to
 * sweep. The only remaining hole is a world loaded by another STARTUP plugin before
 * PvPCore's own {@code onEnable}; that case is pathological and out of scope.</p>
 */
final class WorldSettingsListener implements Listener {

    private final GenerationModule module;

    WorldSettingsListener(GenerationModule module) {
        this.module = module;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();
        module.applyWorldSettings(world);
        if (!(world.getGenerator() instanceof ChunkGenerator)
                && module.isExpectedArenaWorld(world.getName())) {
            module.logMissingGenerator(world);
        }
    }
}
