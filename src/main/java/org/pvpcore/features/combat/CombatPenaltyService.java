package org.pvpcore.features.combat;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

/**
 * Combat-log logout penalties, selectable via {@code combat/config.yml}'s {@code penalty.mode}.
 *
 * <ul>
 *     <li>{@code none} - no penalty; the session is simply cleared.</li>
 *     <li>{@code drop} - drop the player's inventory and XP where they stood.</li>
 *     <li>{@code kill} - kill the player on the spot (death drops as usual).</li>
 * </ul>
 *
 * <p>The penalty runs before the session is cleared (see {@link CombatListener#onQuit}), so it acts
 * on the pre-clear state. Creative and spectator players are exempt - they are not really in a
 * fight.</p>
 *
 * <p>{@code kill} sets the player's health to zero during the quit event. Some server builds skip
 * death processing for a disconnecting player, so a persistent marker is written as well: if the
 * death did not go through, the player is killed on their next join instead (the marker is cleared
 * by a normal death, so it never double-kills).</p>
 *
 * <p>Modelled on NikeyV1/CombatLog (GPL-3.0+, private use), extended with the {@code drop} mode,
 * the XP handling and the kill-on-join fallback.</p>
 */
public final class CombatPenaltyService implements Listener {

    /** Vanilla caps a single XP orb at 32767 XP, so large amounts are split. */
    private static final int MAX_ORB_XP = 32767;

    private final JavaPlugin plugin;
    private final NamespacedKey pendingDeathKey;
    private volatile CombatConfig config;
    private volatile CombatMessages messages;

    public CombatPenaltyService(JavaPlugin plugin, CombatConfig config, CombatMessages messages) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.pendingDeathKey = new NamespacedKey(plugin, "combat_pending_death");
    }

    /** Applies new config/messages after a reload. */
    public void reload(CombatConfig config, CombatMessages messages) {
        this.config = config;
        this.messages = messages;
    }

    /**
     * The quit hook: applies the configured penalty to a player who logged out while tagged.
     * Returns a no-op policy for {@code none}.
     */
    public CombatListener.LogoutPolicy policy() {
        return (event, session) -> {
            if (session.isEmpty()) {
                return; // Not tagged: nothing to punish.
            }
            Player player = event.getPlayer();
            if (isExempt(player)) {
                return;
            }
            CombatConfig current = this.config;
            switch (current.penaltyMode()) {
                case NONE -> {
                    // Nothing to do; the session is still cleared by the listener.
                }
                case DROP -> dropInventory(player);
                case KILL -> kill(player);
            }
        };
    }

    /**
     * Kills a player who was marked but whose quit-time death did not process.
     *
     * <p>The kill is deferred one tick on purpose: damage processing is suppressed during
     * {@link PlayerJoinEvent} (join protection), so {@code setHealth(0)} there would usually not
     * trigger {@code PlayerDeathEvent}. The marker is removed first, so an exception in the
     * deferred task can't leave the player getting killed on every rejoin.</p>
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.getPersistentDataContainer().has(pendingDeathKey, PersistentDataType.BYTE)) {
            return;
        }
        // Remove the marker first so an exception in the deferred task can't leave a player
        // being killed repeatedly across rejoins.
        player.getPersistentDataContainer().remove(pendingDeathKey);
        if (isExempt(player)) {
            return;
        }
        // The death never went through during the quit; finish it now, one tick later.
        // The isOnline/isDead guards cover a player who disconnected again or was already
        // killed by another plugin between the join and the tick.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && !player.isDead()) {
                player.setHealth(0.0D);
            }
        });
    }

    /** A death clears the pending marker, so a normal death never causes a second kill on join. */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getEntity().getPersistentDataContainer().remove(pendingDeathKey);
    }

    private void dropInventory(Player player) {
        broadcast(player);
        Location dropLocation = player.getLocation();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }
            player.getWorld().dropItemNaturally(dropLocation, stack.clone());
        }
        player.getInventory().clear();

        int remaining = player.getTotalExperience();
        while (remaining > 0) {
            int chunk = Math.min(remaining, MAX_ORB_XP);
            player.getWorld().spawn(dropLocation, ExperienceOrb.class, orb -> orb.setExperience(chunk));
            remaining -= chunk;
        }
        player.setLevel(0);
        player.setExp(0f);
    }

    private void kill(Player player) {
        broadcast(player);
        // Mark first: if the death does not process during the quit, the join handler finishes it.
        player.getPersistentDataContainer().set(pendingDeathKey, PersistentDataType.BYTE, (byte) 1);
        player.setHealth(0.0D);
    }

    private void broadcast(Player player) {
        Bukkit.getServer().broadcast(messages.loggedOut(player.getName()));
    }

    private static boolean isExempt(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }
}
