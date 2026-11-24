package org.pvpcore.module;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Derives a deterministic module order from {@link PvPCoreModule#dependsOn()}.
 */
public final class ModuleLifecycle {

    private ModuleLifecycle() {
    }

    public static List<PvPCoreModule> enableOrder(Collection<PvPCoreModule> modules, Logger logger) {
        Map<String, PvPCoreModule> byName = new HashMap<>();
        for (PvPCoreModule module : modules) {
            byName.put(module.name(), module);
        }

        Set<String> visiting = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();
        List<PvPCoreModule> ordered = new ArrayList<>(modules.size());

        for (PvPCoreModule module : modules) {
            visit(module, byName, visiting, visited, ordered, logger, new ArrayDeque<>());
        }
        return ordered;
    }

    public static List<PvPCoreModule> disableOrder(Collection<PvPCoreModule> modules, Logger logger) {
        List<PvPCoreModule> enable = enableOrder(modules, logger);
        Deque<PvPCoreModule> reverse = new ArrayDeque<>(enable.size());
        for (PvPCoreModule module : enable) {
            reverse.addFirst(module);
        }
        return new ArrayList<>(reverse);
    }

    private static void visit(PvPCoreModule module, Map<String, PvPCoreModule> byName,
                              Set<String> visiting, Set<String> visited, List<PvPCoreModule> ordered,
                              Logger logger, Deque<String> path) {
        String name = module.name();
        if (visited.contains(name)) {
            return;
        }
        if (!visiting.add(name)) {
            logger.warning("Module dependency cycle detected involving '" + name + "' (path: " + String.join(" -> ", path) + " -> " + name + "); continuing with partial order.");
            return;
        }
        path.addLast(name);
        for (String depName : module.dependsOn()) {
            PvPCoreModule dep = byName.get(depName);
            if (dep == null) {
                logger.log(Level.FINE, "Module '" + name + "' declares unknown dependency '" + depName + "'; ignoring.");
                continue;
            }
            visit(dep, byName, visiting, visited, ordered, logger, path);
        }
        path.removeLast();
        visiting.remove(name);
        visited.add(name);
        ordered.add(module);
    }
}
