package org.pvpcore.mechanic.fastercrystals;

import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jspecify.annotations.NonNull;
import org.pvpcore.PvPCorePlugin;

import java.util.Map;
import java.util.UUID;

/**
 * Turns an arm swing into an immediate attack on the crystal under the crosshair instead of
 * waiting for the client's action packet.
 *
 * <p>Older clients send the serverbound {@code ANIMATION} (swing) packet. Minecraft 26.3
 * replaced it with {@code PUNCH}, so both are handled. The packet data is captured on the
 * network thread and all Bukkit access happens in a task on the primary thread, scheduled
 * through the Bukkit next-tick scheduler.</p>
 */
final class CrystalAttackListener extends SimplePacketListenerAbstract {

    private final PvPCorePlugin plugin;
    private final FasterCrystalsMechanic mechanic;
    private final CrystalTracker tracker;
    private final Map<UUID, PlayerPacketState> states;

    CrystalAttackListener(PvPCorePlugin plugin, FasterCrystalsMechanic mechanic,
                          CrystalTracker tracker, Map<UUID, PlayerPacketState> states) {
        // Must run before LastPacketListener (MONITOR); see the class comment there.
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
        this.mechanic = mechanic;
        this.tracker = tracker;
        this.states = states;
    }

    @Override
    public void onPacketPlayReceive(@NonNull PacketPlayReceiveEvent event) {
        try {
            onSwing(event);
        } catch (Throwable t) {
            // A malformed packet must never reach the server.
            mechanic.reportException("attack packet handling", t);
        }
    }

    private void onSwing(PacketPlayReceiveEvent event) {
        if (!mechanic.isActive()) {
            return;
        }
        boolean punch = event.getPacketType() == PacketType.Play.Client.PUNCH;
        if (!punch && event.getPacketType() != PacketType.Play.Client.ANIMATION) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Map<UUID, PlayerPacketState> currentStates = states;
        if (currentStates == null) {
            return;
        }
        // Robust against a missed join event (AuthMe can delay/cancel it): a swing must
        // never be dropped just because the player was not present when the mechanic started.
        PlayerPacketState state = currentStates.computeIfAbsent(uuid, key -> new PlayerPacketState());

        if (mechanic.isDebug()) {
            mechanic.debug("swing packet=" + (punch ? "PUNCH" : "ANIMATION")
                    + " player=" + player.getName());
        }

        // Cheap no-crystal guard before scheduling any main-thread work.
        if (tracker.size() == 0) {
            if (mechanic.isDebug()) {
                mechanic.debug("swing skipped: no crystals tracked.");
            }
            return;
        }

        // Each click gets its own swing object. LastPacketListener marks that object if the
        // packet following this swing is an inventory action.
        PlayerPacketState.Swing swing = state.beginSwing();
        Bukkit.getScheduler().runTask(plugin, () -> handleSwing(player, swing));
    }

    private void handleSwing(Player player, PlayerPacketState.Swing swing) {
        try {
            handleSwingUnsafe(player, swing);
        } catch (Throwable t) {
            // Never let a scheduled task break the mechanic.
            mechanic.reportException("attack task", t);
        }
    }

    private void handleSwingUnsafe(Player player, PlayerPacketState.Swing swing) {
        if (!mechanic.isActive() || !player.isOnline()) {
            return;
        }
        if (tracker.size() == 0) {
            return;
        }

        AnimPacket previous = swing.previous();
        if (mechanic.isDebug()) {
            mechanic.debug("swing: player=" + player.getName() + " previous=" + previous
                    + " ignored=" + swing.ignore() + " gamemode=" + player.getGameMode());
        }
        if (swing.ignore()) {
            return;
        }
        if (previous == AnimPacket.IGNORE) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (isAttackDamageReduced(player)) {
            if (mechanic.isDebug()) {
                mechanic.debug("swing skipped: damage is reduced by weakness.");
            }
            return;
        }

        // PvPCore is Paper-only and raytraces on the primary thread, so upstream's Folia
        // region-movement guard is unnecessary here.
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        AttributeInstance range = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (range == null) {
            return;
        }

        RayTraceResult hit = eye.getWorld().rayTraceEntities(eye, direction, range.getValue(), 0.0,
                entity -> isTargetable(player, entity));
        if (hit == null) {
            if (mechanic.isDebug()) {
                mechanic.debug("swing: no pickable entity in range.");
            }
            return;
        }

        Entity entity = hit.getHitEntity();
        if (entity == null || entity.getType() != EntityType.END_CRYSTAL) {
            if (mechanic.isDebug()) {
                mechanic.debug("swing: hit "
                        + (entity == null ? "nothing" : entity.getType()) + ", not a crystal.");
            }
            return;
        }

        // A crystal spawned this tick is the client's own prediction; attacking it would
        // pop it twice.
        if (entity.getTicksLived() == 0) {
            if (mechanic.isDebug()) {
                mechanic.debug("swing skipped: crystal id=" + entity.getEntityId()
                        + " spawned this tick.");
            }
            return;
        }

        // A block in front of the crystal blocks the swing. The exception is when the eye is
        // inside the crystal's bounding box: the raytrace reports the far side of the box.
        if (!entity.getBoundingBox().contains(eye.toVector())) {
            AttributeInstance blockRange = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
            if (blockRange == null) {
                return;
            }
            RayTraceResult blockHit = eye.getWorld().rayTraceBlocks(
                    eye, direction, blockRange.getValue());
            if (blockHit != null && blockHit.getHitBlock() != null) {
                Vector eyeVector = eye.toVector();
                if (eyeVector.distanceSquared(blockHit.getHitPosition())
                        <= eyeVector.distanceSquared(hit.getHitPosition())) {
                    if (mechanic.isDebug()) {
                        mechanic.debug("swing blocked by " + blockHit.getHitBlock().getType()
                                + " at " + blockHit.getHitBlock().getLocation().toVector() + ".");
                    }
                    return;
                }
                // Mid-break swings should not attack; only the start of a click may.
                if (previous != AnimPacket.START_DIGGING && previous != AnimPacket.ATTACK) {
                    if (mechanic.isDebug()) {
                        mechanic.debug("swing skipped: block hit but previous packet was "
                                + previous + ".");
                    }
                    return;
                }
            }
        }

        // Final check: the inventory-drop packet may have arrived while the raytrace ran.
        if (swing.ignore()) {
            if (mechanic.isDebug()) {
                mechanic.debug("swing skipped: inventory action arrived before the hit.");
            }
            return;
        }

        if (mechanic.isDebug()) {
            mechanic.debug("attacking crystal id=" + entity.getEntityId()
                    + " at " + entity.getLocation().toVector() + ".");
        }
        // Full vanilla attack: fires normal Bukkit attack events and resets the attack-strength
        // ticker. The crystal is normally removed by this call, so the follow-up action packet
        // has no live target and does not double-hit.
        try {
            player.attack(entity);
        } catch (Throwable t) {
            mechanic.reportException("attack", t);
            if (mechanic.isDebug()) {
                mechanic.debug("attack failed for crystal id=" + entity.getEntityId() + ": " + t);
            }
        }
    }

    private static boolean isTargetable(Player player, Entity entity) {
        if (!PickableResolver.isPickable(entity)) {
            return false;
        }
        if (entity.getType() != EntityType.PLAYER) {
            return true;
        }
        Player other = (Player) entity;
        if (other.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        return !player.getUniqueId().equals(other.getUniqueId()) && player.canSee(other);
    }

    private static boolean isAttackDamageReduced(Player player) {
        PotionEffect weakness = player.getPotionEffect(PotionEffectType.WEAKNESS);
        int weaknessLevel = weakness != null ? weakness.getAmplifier() + 1 : 0;

        PotionEffect strength = player.getPotionEffect(PotionEffectType.STRENGTH);
        int strengthLevel = strength != null ? strength.getAmplifier() + 1 : 0;

        return strengthLevel * 3 - weaknessLevel * 4 < 0;
    }
}