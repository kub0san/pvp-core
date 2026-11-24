package org.pvpcore.mechanic.totemfixes;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.concurrent.TimeUnit;

/**
 * Gives a just-received inventory action one server tick to reach Bukkit before a
 * potentially lethal hit is applied. Damage is re-submitted with its original Paper
 * DamageSource so vanilla still performs armor, absorption and totem handling.
 *
 * <p>This is deliberately conservative: it only activates for lethal damage, only when
 * the server has a real totem somewhere in the player's accessible inventory, and only
 * while a matching inventory packet is still awaiting its Bukkit event.</p>
 */
final class TotemDamageListener implements Listener {

    private static final long PENDING_PACKET_WINDOW_NANOS = TimeUnit.MILLISECONDS.toNanos(250L);
    private static final long GRACE_TICKS = 1L;
    private static final int MAX_QUEUED_HITS = 16;

    private record DamageSnapshot(double amount, DamageSource source) {
    }

    private static final class PendingDamage {
        private final List<DamageSnapshot> hits = new ArrayList<>();
        private BukkitTask task;
    }

    private final Plugin plugin;
    private final TotemSwapTracker tracker;
    private final Map<UUID, PendingDamage> pending = new HashMap<>();
    private final Set<UUID> replaying = new HashSet<>();

    TotemDamageListener(Plugin plugin, TotemSwapTracker tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        if (replaying.contains(playerId)) {
            return;
        }

        DamageSnapshot snapshot = new DamageSnapshot(event.getDamage(), event.getDamageSource());

        // Once a grace window has started, hold every additional hit for that same
        // single tick. Otherwise a second hit could kill the player before the pending
        // inventory packet gets processed.
        PendingDamage existing = pending.get(playerId);
        if (existing != null) {
            if (existing.hits.size() >= MAX_QUEUED_HITS) {
                // Fail open under pathological damage spam. Never create an unbounded queue.
                return;
            }
            event.setCancelled(true);
            existing.hits.add(snapshot);
            return;
        }

        if (!isLethal(player, event)) {
            return;
        }

        long now = System.nanoTime();
        if (!tracker.hasRecentUnprocessedAction(playerId, now, PENDING_PACKET_WINDOW_NANOS)) {
            return;
        }

        // If the server already sees a totem in either hand, leave vanilla completely alone.
        if (hasTotemInEitherHand(player) || !hasAccessibleTotem(player)) {
            return;
        }

        event.setCancelled(true);

        PendingDamage delayed = new PendingDamage();
        delayed.hits.add(snapshot);
        pending.put(playerId, delayed);
        delayed.task = Bukkit.getScheduler().runTaskLater(plugin, () -> replay(player), GRACE_TICKS);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        PendingDamage delayed = pending.remove(playerId);
        if (delayed != null && delayed.task != null) {
            delayed.task.cancel();
        }
        replaying.remove(playerId);
        tracker.remove(playerId);
    }

    private void replay(Player player) {
        UUID playerId = player.getUniqueId();
        PendingDamage delayed = pending.remove(playerId);
        if (delayed == null || !player.isOnline() || player.isDead()) {
            return;
        }

        replaying.add(playerId);
        try {
            for (DamageSnapshot hit : delayed.hits) {
                if (!player.isOnline() || player.isDead()) {
                    break;
                }
                // Paper's Damageable API keeps the original damage source attribution.
                // The amount is the raw event damage captured before other event listeners,
                // allowing the ordinary damage pipeline to calculate mitigation once.
                player.damage(hit.amount(), hit.source());
            }
        } catch (Throwable failure) {
            plugin.getLogger().log(Level.SEVERE,
                    "Anti Totem Ghost could not replay delayed damage for " + player.getName(), failure);
        } finally {
            replaying.remove(playerId);
        }
    }

    private boolean isLethal(Player player, EntityDamageEvent event) {
        double finalDamage = event.getFinalDamage();
        return finalDamage > 0.0
                && player.getHealth() + player.getAbsorptionAmount() - finalDamage <= 0.0;
    }

    private boolean hasTotemInEitherHand(Player player) {
        return isTotem(player.getInventory().getItemInMainHand())
                || isTotem(player.getInventory().getItemInOffHand());
    }

    private boolean hasAccessibleTotem(Player player) {
        if (player.getInventory().contains(Material.TOTEM_OF_UNDYING)) {
            return true;
        }

        if (isTotem(player.getItemOnCursor())) {
            return true;
        }

        Inventory top = player.getOpenInventory().getTopInventory();
        return top != null && top.contains(Material.TOTEM_OF_UNDYING);
    }

    private static boolean isTotem(ItemStack item) {
        return item != null && item.getType() == Material.TOTEM_OF_UNDYING && item.getAmount() > 0;
    }

    /**
     * Flushes held hits when the mechanic is toggled off. This prevents cancelled damage
     * from being silently lost if the mechanic is disabled during its one-tick window.
     */
    void flushPending() {
        if (pending.isEmpty()) {
            return;
        }

        List<Map.Entry<UUID, PendingDamage>> entries = new ArrayList<>(pending.entrySet());
        pending.clear();

        for (Map.Entry<UUID, PendingDamage> entry : entries) {
            PendingDamage delayed = entry.getValue();
            if (delayed.task != null) {
                delayed.task.cancel();
            }

            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || player.isDead()) {
                continue;
            }

            replaying.add(entry.getKey());
            try {
                for (DamageSnapshot hit : delayed.hits) {
                    if (!player.isOnline() || player.isDead()) {
                        break;
                    }
                    player.damage(hit.amount(), hit.source());
                }
            } catch (Throwable failure) {
                plugin.getLogger().log(Level.SEVERE,
                        "Anti Totem Ghost could not flush delayed damage for " + player.getName(), failure);
            } finally {
                replaying.remove(entry.getKey());
            }
        }
    }
}
