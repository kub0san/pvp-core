package org.pvpcore.mechanic.fastercrystals;

import org.bukkit.entity.Entity;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

/**
 * Resolves {@code Entity#isPickable()} once. Bukkit's raytrace API does not expose it, and
 * it decides whether an entity blocks a click. If the lookup fails the resolver reports
 * everything as pickable, which can only make the raytrace stricter, never hide a crystal.
 */
final class PickableResolver {

    private static boolean resolved;
    private static MethodHandle pickable;

    private PickableResolver() {
    }

    static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Class<?> craftEntity = Class.forName("org.bukkit.craftbukkit.entity.CraftEntity");
            Class<?> nmsEntity = Class.forName("net.minecraft.world.entity.Entity");
            Method handle = craftEntity.getMethod("getHandle");
            Method isPickable = nmsEntity.getMethod("isPickable");
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandle getHandle = lookup.unreflect(handle);
            MethodHandle predicate = lookup.unreflect(isPickable);

            // Compose once so the swing path only has one exact invoke per entity.
            pickable = MethodHandles.filterArguments(predicate, 0, getHandle)
                    .asType(MethodType.methodType(boolean.class, Entity.class));
        } catch (Throwable ignored) {
            pickable = null;
        }
    }

    /** Whether the cached {@code isPickable()} handle was resolved. */
    static boolean isResolved() {
        return pickable != null;
    }

    static boolean isPickable(Entity entity) {
        MethodHandle handle = pickable;
        if (handle == null) {
            return true;
        }
        try {
            return (boolean) handle.invokeExact(entity);
        } catch (Throwable ignored) {
            return true;
        }
    }
}
