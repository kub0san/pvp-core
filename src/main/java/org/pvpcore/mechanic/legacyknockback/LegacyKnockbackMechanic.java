package org.pvpcore.mechanic.legacyknockback;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.config.core.CoreConfig;
import org.pvpcore.mechanic.Mechanic;

import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/**
 * Restores the pre-1.19.4 explosion knockback behaviour (MC-198809), including the
 * integer-rounding bug that made Blast Protection useless for typical explosions.
 *
 * <p>Before snapshot 23w04a (Minecraft 1.19.4), the knockback reduction from Blast
 * Protection was computed as an integer:
 * {@code reduction = (int) (originalKnockback * level * 0.15)}, then subtracted from
 * the original. Because explosion knockback is usually well below 1.0, the product
 * rounded down to zero, so the reduction had no effect at normal enchantment levels
 * and normal explosion distances. From 1.19.4 onwards the reduction became a straight
 * multiplier, {@code originalKnockback * (1.0 - 0.15 * level)}, so Blast Protection
 * I-IV reduces explosion knockback by 15-60%.</p>
 *
 * <p>This mechanic restores the old behaviour <em>exactly</em>, including the rounding
 * bug. Paper's {@link EntityKnockbackEvent} fires after vanilla applied the new
 * dampener, so the mechanic first recovers the original magnitude
 * ({@code current / (1.0 - 0.15 * level)}), then applies the 1.19.3 formula
 * ({@code original - (int)(original * level * 0.15)}) and writes the result back.
 * For typical explosion knockback this gives the full pre-1.19.4 launch (the bug);
 * for very large knockback from extremely close high-power explosions it matches
 * 1.19.3's integer subtraction exactly.</p>
 *
 * <p>Level 7 and above is skipped: the 1.19.4+ dampener is zero at that point, so the
 * original magnitude is unrecoverable from the event. In practice 1.19.3 also reduces
 * those to zero for all standard knockback values, so the difference is negligible.</p>
 */
public final class LegacyKnockbackMechanic implements Mechanic, Listener {

    /** Vanilla 1.19.4+ reduction per Blast Protection level: 15%. */
    private static final double REDUCTION_PER_LEVEL = 0.15;

    /** Values below this are treated as zero to avoid division-by-zero and NaN vectors. */
    private static final double MIN_MAGNITUDE = 1.0E-9;

    private PvPCorePlugin plugin;
    private volatile boolean active;
    private volatile boolean debug;

    @Override
    public String id() {
        return "legacyknockback";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("oldblastprotection", "pre1194blastprotection", "blastprot1194");
    }

    @Override
    public String displayName() {
        return "Legacy Explosion Knockback";
    }

    @Override
    public String description() {
        return "Pre-1.19.4 explosion knockback: Blast Protection rounds its reduction away (MC-198809), so it rarely reduces knockback.";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public boolean enable(PvPCorePlugin plugin) {
        if (active) {
            return true;
        }
        this.plugin = plugin;
        this.debug = CoreConfig.isDebug(plugin);

        try {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            active = true;
            plugin.getLogger().info("Legacy Explosion Knockback enabled: Blast Protection uses "
                    + "the pre-1.19.4 integer-rounded reduction (so it usually has no effect).");
            if (debug) {
                plugin.getLogger().info("[debug] Formula restored: original - (int)(original * level * 0.15).");
            }
            return true;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE,
                    "Legacy Explosion Knockback could not register its listener", t);
            return false;
        }
    }

    @Override
    public void disable(PvPCorePlugin plugin) {
        if (!active) {
            return;
        }
        active = false;
        HandlerList.unregisterAll(this);
        plugin.getLogger().info("Legacy Explosion Knockback disabled.");
    }

    @Override
    public String statusNote() {
        return active ? "1.19.3 rounding: Blast Protection usually has no effect" : null;
    }

    @Override
    public void refresh(PvPCorePlugin plugin) {
        boolean wasDebug = debug;
        debug = CoreConfig.isDebug(plugin);
        if (wasDebug != debug) {
            plugin.getLogger().info("Legacy Explosion Knockback debug logging "
                    + (debug ? "enabled" : "disabled") + ".");
        }
    }

    // ------------------------------------------------------------------
    // Knockback restoration
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent event) {
        if (!active) {
            return;
        }
        if (event.getCause() != EntityKnockbackEvent.Cause.EXPLOSION) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        int level = highestBlastProtectionLevel(victim);
        if (level <= 0) {
            return;
        }

        Vector current = event.getKnockback();
        double currentMagnitude = current.length();
        if (currentMagnitude < MIN_MAGNITUDE) {
            // The dampener zeroed the knockback (level 7+), so the original is
            // unrecoverable from the event. 1.19.3 also reduces this to ~0.
            return;
        }

        double dampener = 1.0 - REDUCTION_PER_LEVEL * level;
        if (dampener <= 0.0) {
            // Defensive: for level 7+ the event should already be zero.
            if (debug) {
                debug("Skipped " + victim.getName() + ": Blast Protection " + level
                        + " dampens to zero; original unrecoverable.");
            }
            return;
        }

        // Step 1: undo the 1.19.4+ multiplier to recover the original magnitude.
        double originalMagnitude = currentMagnitude / dampener;

        // Step 2: apply the 1.19.3 formula exactly, including its integer rounding.
        int reduction = (int) (originalMagnitude * level * REDUCTION_PER_LEVEL);
        double legacyMagnitude = Math.max(0.0, originalMagnitude - reduction);

        // Step 3: preserve the direction and write the restored magnitude back.
        Vector legacy = current.clone().multiply(legacyMagnitude / currentMagnitude);
        event.setKnockback(legacy);

        if (debug) {
            debug("Restored " + victim.getName() + ": Blast Prot " + level
                    + ", event " + format(currentMagnitude)
                    + " -> original " + format(originalMagnitude)
                    + " -> legacy " + format(legacyMagnitude)
                    + " (subtracted " + reduction + ").");
        }
    }

    /**
     * The highest Blast Protection level across all armour pieces. Vanilla only uses the
     * maximum, not the sum, so four Blast Protection I pieces are the same as one
     * Blast Protection I piece.
     */
    private static int highestBlastProtectionLevel(LivingEntity entity) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return 0;
        }
        int highest = 0;
        for (ItemStack item : equipment.getArmorContents()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            highest = Math.max(highest, item.getEnchantmentLevel(Enchantment.BLAST_PROTECTION));
        }
        return highest;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private void debug(String message) {
        if (debug && plugin != null) {
            plugin.getLogger().info("[debug] " + message);
        }
    }
}