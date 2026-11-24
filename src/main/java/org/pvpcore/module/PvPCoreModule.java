package org.pvpcore.module;

import org.pvpcore.PvPCorePlugin;

import java.util.Set;

/**
 * Lifecycle contract for PvPCore feature modules. Order is derived from {@link #dependsOn()}.
 */
public interface PvPCoreModule {

    String name();

    default Set<String> dependsOn() {
        return Set.of();
    }

    void enable(PvPCorePlugin plugin);

    void disable();

    void reload();
}
