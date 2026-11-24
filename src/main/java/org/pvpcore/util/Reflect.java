package org.pvpcore.util;

import java.lang.reflect.Field;

/** Small helpers for one-time reflection lookups. */
public final class Reflect {

    private Reflect() {
    }

    /**
     * Finds a declared field by exact name, walking up the class hierarchy.
     *
     * @return the field (not yet accessible), or {@code null}
     */
    public static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // continue with the superclass
            }
        }
        return null;
    }
}
