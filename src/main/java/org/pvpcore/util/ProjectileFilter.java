package org.pvpcore.util;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Projectile;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Decides which projectiles a mechanic touches.
 *
 * <p>Two modes:</p>
 * <ul>
 *   <li>{@code blacklist} (default): every projectile is affected except the listed ones.
 *       An empty list therefore means "affect every projectile".</li>
 *   <li>{@code whitelist}: only the listed projectiles are affected. An empty list
 *       therefore means "affect nothing".</li>
 * </ul>
 */
public final class ProjectileFilter {

    /** The default: affect every projectile. */
    public static final ProjectileFilter ALL = new ProjectileFilter(false, EnumSet.noneOf(EntityType.class));

    private final boolean whitelist;
    private final Set<EntityType> types;

    public ProjectileFilter(boolean whitelist, Set<EntityType> types) {
        this.whitelist = whitelist;
        this.types = types.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(types));
    }

    public boolean isWhitelist() {
        return whitelist;
    }

    public int size() {
        return types.size();
    }

    /** True when this entity type should be affected. */
    public boolean accepts(EntityType type) {
        if (types.isEmpty()) {
            return !whitelist;
        }
        return whitelist == types.contains(type);
    }

    /** True when {@code other} has the same mode and the same set of types. */
    public boolean sameAs(ProjectileFilter other) {
        return other != null
                && whitelist == other.whitelist
                && types.equals(other.types);
    }

    /** True when this looks like a filter the operator accidentally set wrong. */
    public boolean isEmptyWhitelist() {
        return whitelist && types.isEmpty();
    }

    /** True when an entity type exists in the filter but can never match a projectile. */
    public boolean containsNonProjectile(EntityType type) {
        Class<?> clazz = type.getEntityClass();
        return clazz != null && !Projectile.class.isAssignableFrom(clazz);
    }

    /** Human-readable description for the enable and reload log. */
    public String describe() {
        String mode = whitelist ? "whitelist" : "blacklist";
        if (types.isEmpty()) {
            return whitelist
                    ? mode + " (empty; no projectiles affected)"
                    : mode + " (empty; every projectile affected)";
        }
        return mode + " " + types;
    }
}