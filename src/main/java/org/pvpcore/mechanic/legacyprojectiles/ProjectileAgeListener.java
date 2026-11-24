package org.pvpcore.mechanic.legacyprojectiles;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

/**
 * Raises the age of every accepted projectile that enters the world, so the vanilla margin
 * formula {@code clamp((tickCount - 2) / 20, 0, 0.3)} returns the full pre-1.21.6 margin of
 * 0.3 blocks from the projectile's first tick.
 *
 * <p>{@code EntityAddToWorldEvent} fires when the entity is added to the world, before its
 * first tick, and the projectile hit check runs before {@code tickCount} is incremented, so
 * the spoofed age is already in place for the very first movement. The event covers every
 * spawn source (players, dispensers, mobs, other plugins, chunk loads).</p>
 *
 * <p>This listener changes one input of the engine's own hit detection and nothing else.
 * It deliberately does not run a second, manual collision check: a hit found outside the
 * engine's collision loop cannot be attributed to the projectile correctly, so damage,
 * explosive entities and visuals would all diverge from vanilla.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>Only projectiles accepted by the mechanic's {@link
 *       LegacyProjectilesMechanic#accepts filter} are touched.</li>
 *   <li>The age is only ever raised, never lowered, so a projectile that is already older
 *       than {@link LegacyProjectilesMechanic#LEGACY_AGE_TICKS} keeps its real age.</li>
 *   <li>Every projectile that was changed is remembered by entity id, so {@link
 *       #restoreOriginalAges()} can take the added ticks back when the mechanic is disabled.
 *       Entries are dropped when the projectile leaves the world, so the map only holds
 *       projectiles that are currently in flight (or stuck in a block).</li>
 * </ul>
 *
 * <p>All callbacks run on the main thread, so the state needs no synchronization.</p>
 */
final class ProjectileAgeListener implements Listener {

    /** A projectile this listener changed, with the age it had before the change. */
    private record Spoofed(Entity entity, int original) {
    }

    private final LegacyProjectilesMechanic mechanic;
    private final TickCountAccess access;

    /** Entity id to the projectile that is currently carrying the spoofed age. */
    private final Map<Integer, Spoofed> spoofed = new HashMap<>();

    private long seen;
    private long filtered;
    private long spoofedTotal;
    private long skipped;
    private long restored;
    private long failures;
    private boolean failureLogged;

    ProjectileAgeListener(LegacyProjectilesMechanic mechanic, TickCountAccess access) {
        this.mechanic = mechanic;
        this.access = access;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityAddToWorld(EntityAddToWorldEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Projectile)) {
            return;
        }
        seen++;
        if (!mechanic.accepts(entity.getType())) {
            filtered++;
            if (mechanic.isDebug()) {
                mechanic.debug("Filtered " + describe(entity) + ": excluded by the current filter.");
            }
            return;
        }
        try {
            int current = access.tickCount(entity);
            if (current >= LegacyProjectilesMechanic.LEGACY_AGE_TICKS) {
                // Already old enough for the full margin: never lower a real age.
                skipped++;
                if (mechanic.isDebug()) {
                    mechanic.debug("Skipped " + describe(entity) + ": age " + current
                            + " is already >= " + LegacyProjectilesMechanic.LEGACY_AGE_TICKS + ".");
                }
                return;
            }
            access.setTickCount(entity, LegacyProjectilesMechanic.LEGACY_AGE_TICKS);
            spoofed.put(entity.getEntityId(), new Spoofed(entity, current));
            spoofedTotal++;
            if (mechanic.isDebug()) {
                mechanic.debug("Spoofed " + describe(entity) + ": age " + current + " -> "
                        + LegacyProjectilesMechanic.LEGACY_AGE_TICKS + " (margin " + margin(current)
                        + " -> " + margin(LegacyProjectilesMechanic.LEGACY_AGE_TICKS) + ").");
            }
        } catch (Throwable t) {
            fail("raise the age of " + describe(entity), t);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemoveFromWorld(EntityRemoveFromWorldEvent event) {
        if (spoofed.isEmpty()) {
            return;                                   // cheapest possible exit for every other entity
        }
        spoofed.remove(event.getEntity().getEntityId());
    }

    /**
     * Debug only, never changes behaviour. Logs what a projectile actually hit and whether the
     * point where it stopped lies inside the 0.3 block margin of a nearby entity. When the
     * answer is yes but the entity was not hit, the margin is not being applied to that
     * projectile; when it is no, the shot would not have hit in 1.21.5 either.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!mechanic.isDebug()) {
            return;
        }
        Projectile projectile = event.getEntity();
        try {
            int age = access.tickCount(projectile);
            Vector velocity = projectile.getVelocity();
            StringBuilder line = new StringBuilder("Hit ").append(describe(projectile))
                    .append(": age ").append(age).append(" (margin ").append(margin(age))
                    .append("), speed ").append(String.format("%.2f", velocity.length()))
                    .append(", burning ").append(projectile.getFireTicks() > 0);
            if (event.getHitEntity() != null) {
                line.append(", hit entity ").append(describe(event.getHitEntity()));
            }
            if (event.getHitBlock() != null) {
                line.append(", hit block ").append(event.getHitBlock().getType()).append(" at ")
                        .append(event.getHitBlock().getX()).append(',').append(event.getHitBlock().getY())
                        .append(',').append(event.getHitBlock().getZ())
                        .append(" face ").append(event.getHitBlockFace());
                appendMarginCheck(line, projectile, velocity);
            }
            mechanic.debug(line.append('.').toString());
        } catch (Throwable t) {
            fail("log the hit of " + describe(projectile), t);
        }
    }

    /** Re-runs the engine's block clip for this step and tests nearby entities against it. */
    private static void appendMarginCheck(StringBuilder line, Projectile projectile, Vector velocity) {
        double length = velocity.length();
        if (length < 1.0E-4) {
            return;
        }
        Location at = projectile.getLocation();
        RayTraceResult clip = at.getWorld().rayTraceBlocks(at, velocity.clone().normalize(), length,
                FluidCollisionMode.NEVER, false);
        if (clip == null) {
            return;
        }
        Vector point = clip.getHitPosition();
        line.append(", stopped at ").append(String.format("%.2f,%.2f,%.2f", point.getX(), point.getY(), point.getZ()));
        int listed = 0;
        for (Entity other : projectile.getNearbyEntities(length + 2.0, length + 2.0, length + 2.0)) {
            if (other instanceof Projectile || listed >= 3) {
                continue;
            }
            BoundingBox raw = other.getBoundingBox();
            boolean insideRaw = raw.contains(point);
            boolean insideMargin = raw.clone().expand(0.3).contains(point);
            if (!insideMargin && raw.getCenter().distance(point) > 2.0) {
                continue;
            }
            listed++;
            line.append("; ").append(describe(other)).append(" insideRaw ").append(insideRaw)
                    .append(" insideMargin ").append(insideMargin);
        }
    }

    /**
     * Gives the added ticks back to the projectiles that are still in the world.
     *
     * <p>The ticks are subtracted from the <em>current</em> age instead of resetting it to the
     * old value, so a projectile that flew for a few ticks after it was spoofed keeps those
     * ticks and ends up with its true age.</p>
     *
     * @return how many projectiles were restored
     */
    int restoreOriginalAges() {
        int count = 0;
        for (Spoofed entry : spoofed.values()) {
            Entity entity = entry.entity();
            if (!entity.isValid()) {
                continue;
            }
            try {
                int added = LegacyProjectilesMechanic.LEGACY_AGE_TICKS - entry.original();
                int current = access.tickCount(entity);
                int age = Math.max(0, current - added);
                access.setTickCount(entity, age);
                count++;
                restored++;
                if (mechanic.isDebug()) {
                    mechanic.debug("Restored " + describe(entity) + ": age " + current + " -> " + age
                            + " (gave back " + added + " ticks).");
                }
            } catch (Throwable t) {
                fail("restore the age of " + describe(entity), t);
            }
        }
        spoofed.clear();
        return count;
    }

    /** One line of counters for the reload and disable log. */
    String summary() {
        return "seen " + seen + ", filtered " + filtered + ", spoofed " + spoofedTotal
                + ", skipped " + skipped + ", restored " + restored
                + ", in flight " + spoofed.size() + ", failures " + failures;
    }

    /** The vanilla margin for a given age, used only for the debug log. */
    private static float margin(int tickCount) {
        return Math.max(0.0f, Math.min(0.3f, (float) (tickCount - 2) / 20.0f));
    }

    private static String describe(Entity entity) {
        return entity.getType() + "#" + entity.getEntityId();
    }

    /** Logs the first failure with a stack trace and only counts the rest. */
    private void fail(String what, Throwable t) {
        failures++;
        if (!failureLogged) {
            failureLogged = true;
            mechanic.logFailure(what, t);
        }
    }
}
