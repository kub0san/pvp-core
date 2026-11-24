package org.pvpcore.mechanic.nospread;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Works out the direction a projectile would have had without the random spread.
 *
 * <p>Vanilla keeps the aim direction for every vanilla launch path, so the jitter-free direction
 * can be reconstructed from the shooter's rotation. The one exception is a Multishot crossbow,
 * which fires its extra arrows at a fixed offset either side of the aim; each arrow is snapped to
 * the offset it was fired at instead of onto the crosshair.</p>
 *
 * <p>The reconstruction is only trusted while the vanilla shot is close to one of the candidate
 * directions. That keeps the mechanic from touching projectiles that a plugin launched with a
 * direction of its own (a gun plugin, a custom ability) just because a player is the shooter.</p>
 */
final class AimDirection {

    /**
     * How far the vanilla shot may be from a candidate direction and still be treated as a
     * spread deviation. The random add is at most about 1.7 degrees, so this is generous while
     * staying well clear of any intentional aim.
     */
    static final double SNAP_TOLERANCE_DEGREES = 4.0;

    /** Vanilla Multishot fires the extra arrows 10 degrees per level either side of the aim. */
    static final double MULTISHOT_DEGREES_PER_LEVEL = 10.0;

    private AimDirection() {
    }

    /**
     * Finds the exact direction behind a vanilla shot.
     *
     * @param shooter        the player that fired the projectile
     * @param shot           the projectile's velocity, including the random spread
     * @param multishotLevel the Multishot level of the used weapon, or {@code 0}
     * @return the closest candidate direction, with the angle it differs from the shot
     */
    static Aim nearest(Player shooter, Vector shot, int multishotLevel) {
        Vector look = shooter.getEyeLocation().getDirection().normalize();
        Aim best = measure(shot, look);
        if (multishotLevel > 0) {
            double offset = Math.toRadians(MULTISHOT_DEGREES_PER_LEVEL * multishotLevel);
            best = closer(best, measure(shot, look.clone().rotateAroundY(offset)));
            best = closer(best, measure(shot, look.clone().rotateAroundY(-offset)));
        }
        return best;
    }

    private static Aim measure(Vector shot, Vector candidate) {
        return new Aim(candidate.normalize(), Math.toDegrees(shot.angle(candidate)));
    }

    private static Aim closer(Aim current, Aim candidate) {
        return candidate.degreesOff() < current.degreesOff() ? candidate : current;
    }

    /** The yaw vanilla would store for a shot with this direction. */
    static float yaw(Vector direction) {
        return (float) Math.toDegrees(Math.atan2(direction.getX(), direction.getZ()));
    }

    /** The pitch vanilla would store for a shot with this direction. */
    static float pitch(Vector direction) {
        double horizontal = Math.sqrt(direction.getX() * direction.getX()
                + direction.getZ() * direction.getZ());
        return (float) Math.toDegrees(Math.atan2(direction.getY(), horizontal));
    }

    /** A candidate direction and how far the vanilla shot was from it. */
    record Aim(Vector direction, double degreesOff) {
    }
}
