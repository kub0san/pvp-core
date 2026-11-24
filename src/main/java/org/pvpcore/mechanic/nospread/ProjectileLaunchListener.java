package org.pvpcore.mechanic.nospread;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

/**
 * Replaces the direction of every player projectile with the exact aim direction.
 */
final class ProjectileLaunchListener implements Listener {

    private static final double MIN_SPEED = 1.0E-4;

    private final NoSpreadMechanic mechanic;

    private long seen;
    private long filtered;
    private long snapped;
    private long untouched;
    private long failures;

    ProjectileLaunchListener(NoSpreadMechanic mechanic) {
        this.mechanic = mechanic;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player shooter)) {
            return;
        }

        seen++;

        // Check if the current configuration allows us to touch this projectile type.
        if (!mechanic.accepts(projectile.getType())) {
            filtered++;
            return;
        }

        Vector shot = projectile.getVelocity();
        double speed = shot.length();
        if (speed < MIN_SPEED) {
            untouched++;
            return;
        }

        try {
            AimDirection.Aim aim = AimDirection.nearest(shooter, shot, multishotLevel(shooter));
            if (aim.degreesOff() > AimDirection.SNAP_TOLERANCE_DEGREES) {
                untouched++;
                mechanic.debug("Left " + describe(projectile) + " untouched: "
                        + format(aim.degreesOff()) + " degrees off aim, over the "
                        + AimDirection.SNAP_TOLERANCE_DEGREES + " degree tolerance.");
                return;
            }

            projectile.setVelocity(aim.direction().multiply(speed));
            projectile.setRotation(AimDirection.yaw(aim.direction()),
                    AimDirection.pitch(aim.direction()));
            snapped++;
            mechanic.debug("Snapped " + describe(projectile) + ": "
                    + format(aim.degreesOff()) + " degrees off aim, speed "
                    + format(speed) + ".");
        } catch (Throwable t) {
            failures++;
            if (failures == 1) {
                mechanic.logFailure("rewrite the velocity of " + describe(projectile), t);
            }
        }
    }

    private static int multishotLevel(Player shooter) {
        PlayerInventory inventory = shooter.getInventory();
        return Math.max(level(inventory.getItemInMainHand()), level(inventory.getItemInOffHand()));
    }

    private static int level(ItemStack item) {
        return item.getType() == Material.AIR ? 0 : item.getEnchantmentLevel(Enchantment.MULTISHOT);
    }

    private static String describe(Entity entity) {
        return entity.getType() + "#" + entity.getEntityId();
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    String summary() {
        return "Player projectiles seen: " + seen + ", snapped: " + snapped
                + ", left untouched: " + untouched + ", filtered: " + filtered
                + ", failures: " + failures;
    }
}