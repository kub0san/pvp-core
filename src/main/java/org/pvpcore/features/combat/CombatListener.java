package org.pvpcore.features.combat;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Turns Bukkit events into combat-tag bookkeeping.
 *
 * <p>This class only hands event facts to {@link CombatManager} and
 * {@link AnchorAttributionTracker}; the tagging and expiry rules live in the manager, and the
 * attribution rules live in the resolver. Canceled damage is never tagged.</p>
 */
public final class CombatListener implements Listener {

    private final CombatManager combatManager;
    private final DamageAttributionResolver resolver;
    private final AnchorAttributionTracker anchorTracker;
    private final LogoutPolicy logoutPolicy;

    public CombatListener(
            CombatManager combatManager,
            DamageAttributionResolver resolver,
            AnchorAttributionTracker anchorTracker
    ) {
        this(combatManager, resolver, anchorTracker, LogoutPolicy.none());
    }

    public CombatListener(
            CombatManager combatManager,
            DamageAttributionResolver resolver,
            AnchorAttributionTracker anchorTracker,
            LogoutPolicy logoutPolicy
    ) {
        this.combatManager = combatManager;
        this.resolver = resolver;
        this.anchorTracker = anchorTracker;
        this.logoutPolicy = logoutPolicy == null ? LogoutPolicy.none() : logoutPolicy;
    }

    // MONITOR is intentional here (and on onDeath): we record state only after every other plugin
    // has decided the event's outcome. Do not "fix" these to HIGHEST - late cancellation handlers
    // must be allowed to run before we mutate any state.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!combatManager.isEnabled()) {
            return; // combat tagging disabled
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return; // non-player victims can never be tagged; skip the resolver entirely
        }
        // !(x > 0) rejects zero, negative and NaN final damage.
        if (!(event.getFinalDamage() > 0.0D)) {
            return;
        }
        var context = resolver.resolve(event);
        if (context.isPresent()) {
            combatManager.recordPlayerCombat(context.get());
            return;
        }
        // Explosion trigger: tag the victim of any explosion-family damage even when no player
        // could be attributed to it (e.g. a crystal with no nearby interactor).
        if (combatManager.config().triggerExplosions()
                && BukkitDamageAttributionResolver.isExplosionDamage(event.getDamageSource())) {
            combatManager.tagPlayer(victim.getUniqueId(), CombatCause.PLAYER_EXPLOSIVE);
        }
    }

    /**
     * Clears the victim's session on death (reason {@link CombatEndReason#DEATH}). The attacker's
     * session is left in place with the victim as its recorded opponent: killing a player does not
     * hand the attacker a free pass out of combat - they stay tagged until their own timer expires.
     *
     * <p>MONITOR is intentional (see {@code onDamage}): state recording after the event is decided.
     * PlayerDeathEvent is not cancellable, so there is no late-cancellation race.</p>
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        combatManager.clear(event.getEntity().getUniqueId(), CombatEndReason.DEATH);
    }

    /**
     * Tags the shooter of an ender pearl when the pearl lands.
     *
     * <p>Modelled on NikeyV1/CombatLog (GPL-3.0+, private use): teleporting away is only useful
     * if the tag survives the hop, so the pearl counts as a combat action.</p>
     *
     * <p>This runs on the pearl's teleport at MONITOR, i.e. <em>after</em> the safe-zone check
     * (HIGHEST) has decided. A clean player pearling into a safe zone is therefore not tagged by
     * their own pearl and is not blocked by it; a tagged player is still stopped from entering a
     * safe zone, because the tag already exists before the pearl is thrown. Firing the tag on
     * {@code ProjectileHitEvent} instead would tag the shooter <em>before</em> the teleport and
     * make the safe zone block the very pearl that caused the tag.</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPearlTeleport(PlayerTeleportEvent event) {
        CombatConfig cfg = combatManager.config();
        if (!combatManager.isEnabled() || !cfg.triggerEnderPearl()) {
            return;
        }
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }
        combatManager.tagPlayer(event.getPlayer().getUniqueId(), CombatCause.PLAYER_PROJECTILE);
    }

    // Not ignoreCancelled: the deprecated isCancelled() maps to useInteractedBlock() == DENY,
    // so we check the block-use result explicitly instead of relying on it. Sneaking suppresses
    // block interaction for most blocks (though not for respawn anchors); either way, a click
    // whose block interaction is denied is not a usable attribution record.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!combatManager.isEnabled()) {
            return; // combat tagging disabled: no anchor records
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // Only the main-hand pass counts; the event fires once per hand.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.RESPAWN_ANCHOR) {
            return;
        }
        if (event.useInteractedBlock() == Event.Result.DENY) {
            // The block interaction was denied: a canceled anchor interaction leaves
            // no usable attribution record.
            return;
        }
        anchorTracker.recordInteraction(event.getPlayer().getUniqueId(), block.getLocation());
    }

    // Not ignoreCancelled-dependent either way: BlockExplodeEvent.isCancelled() is a normal,
    // non-deprecated predicate, so the annotation is safe here.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!combatManager.isEnabled()) {
            return; // combat tagging disabled: no anchor attribution records
        }
        // getBlock() is air by the time this fires; the captured state identifies the anchor.
        BlockState state = event.getExplodedBlockState();
        if (state == null || state.getType() != Material.RESPAWN_ANCHOR) {
            return;
        }
        anchorTracker.recordExplosion(state.getLocation());
    }

    // NORMAL, not MONITOR: the logout policy must be able to inspect the session before it is
    // cleared. LOWEST/LOW handlers run before this and see a live session; HIGH/HIGHEST/MONITOR
    // run after it and see the session already cleared.
    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        // The policy runs BEFORE the session is cleared so it can inspect the final state;
        // it must not be handed a cleared session. The clear runs in a finally block so a
        // throwing policy can never leave the session behind.
        try {
            logoutPolicy.onCombatLogout(event, combatManager.getSession(playerId));
        } finally {
            combatManager.clear(playerId, CombatEndReason.LOGOUT);
        }
    }

    /**
     * Logout policy hook, kept out of the core manager.
     *
     * <p>Defaults to {@link #none()}. Modeled on NikeyV1/CombatLog (GPL-3.0+, private use): the
     * policy inspects the pre-clear {@code session}; the listener then clears it with
     * {@link CombatEndReason#LOGOUT}. The concrete policies ({@code none} / {@code drop} /
     * {@code kill}) are built by {@link CombatLogoutPolicies} from {@code combat/config.yml}'s
     * {@code penalty.mode}.</p>
     */
    @FunctionalInterface
    public interface LogoutPolicy {

        /** Called synchronously from {@link #onQuit} before the session is removed. */
        void onCombatLogout(PlayerQuitEvent event, Optional<CombatSession> session);

        /** Does nothing; the session is still cleared afterwards. */
        static LogoutPolicy none() {
            return (event, session) -> { };
        }
    }
}
