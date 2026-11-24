package org.pvpcore.mechanic.legacypearls;

import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;
import org.bukkit.Bukkit;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the player's momentum through a pearl teleport.
 *
 * <ol>
 *   <li><b>Rewrite:</b> the captured momentum is written into the outgoing position packet
 *   as an absolute value.</li>
 *   <li><b>Follow-up:</b> a velocity packet with the same momentum is sent right after the
 *   position packet, in the same tick.</li>
 *   <li><b>Cancel the reset:</b> since 26.2 the server resets the player's velocity on a
 *   pearl teleport, and one tick later it tells the client about that with a velocity
 *   packet that is zero sideways. That packet is what stopped the player. The first such
 *   packet in the next few ticks is cancelled, so the client keeps the momentum it has.
 *   A packet that is faster than the captured momentum (for example real knockback)
 *   is never cancelled.</li>
 * </ol>
 *
 * <p>While debug is on, every position and self-velocity packet sent to the player for two
 * seconds after a rewrite is logged with its server tick.</p>
 */
final class LegacyPearlPositionListener extends SimplePacketListenerAbstract {

    /** How long after a rewrite the follow-up packets for that player are watched. */
    private static final long WATCH_NANOS = 2_000_000_000L;

    /** Server ticks after the rewrite in which the server's reset packet can be cancelled. */
    private static final int RESET_WINDOW_TICKS = 2;

    private final LegacyPearlsMechanic mechanic;
    private final Map<UUID, Watch> watched = new ConcurrentHashMap<>();

    /** State kept for one player after a pearl teleport was rewritten. */
    private static final class Watch {
        final long createdNanos = System.nanoTime();
        final int tick;
        final double speedSq;
        volatile boolean ownPending;
        volatile boolean resetCancelled;

        Watch(int tick, double speedSq) {
            this.tick = tick;
            this.speedSq = speedSq;
        }
    }

    LegacyPearlPositionListener(LegacyPearlsMechanic mechanic) {
        super(PacketListenerPriority.HIGHEST);
        this.mechanic = mechanic;
    }

    @Override
    public void onPacketPlaySend(@NonNull PacketPlaySendEvent event) {
        try {
            handle(event);
        } catch (Throwable t) {
            // Never let a malformed packet escape into the network pipeline.
            mechanic.reportException("position packet rewrite", t);
        }
    }

    private void handle(PacketPlaySendEvent event) {
        var type = event.getPacketType();
        boolean isPosition = type == PacketType.Play.Server.PLAYER_POSITION_AND_LOOK;
        boolean isVelocity = type == PacketType.Play.Server.ENTITY_VELOCITY;
        if (!isPosition && !isVelocity) {
            return;
        }

        User user = event.getUser();
        if (user == null || user.getUUID() == null) {
            return;
        }
        UUID id = user.getUUID();

        if (isVelocity) {
            handleVelocity(event, user, id);
            return;
        }

        LegacyPearlsMechanic.Momentum momentum = mechanic.take(id);
        if (momentum == null) {
            if (activeWatch(id) != null) {
                mechanic.debug("[wire] " + user.getName()
                        + " extra position packet after pearl teleport, tick="
                        + Bukkit.getCurrentTick());
            }
            return;
        }

        WrapperPlayServerPlayerPositionAndLook wrapper =
                new WrapperPlayServerPlayerPositionAndLook(event);

        if (wrapper.getValues() == null) {
            mechanic.debug("Consumed pearl momentum, but the position packet had null values. "
                    + "Rewrite skipped.");
            return;
        }

        // Make sure the momentum is applied as an absolute value, not added to the
        // client's current motion.
        if (wrapper.isRelativeFlag(RelativeFlag.DELTA_X)) {
            wrapper.setRelative(RelativeFlag.DELTA_X, false);
        }
        if (wrapper.isRelativeFlag(RelativeFlag.DELTA_Y)) {
            wrapper.setRelative(RelativeFlag.DELTA_Y, false);
        }
        if (wrapper.isRelativeFlag(RelativeFlag.DELTA_Z)) {
            wrapper.setRelative(RelativeFlag.DELTA_Z, false);
        }

        Vector3d velocity = new Vector3d(momentum.x(), momentum.y(), momentum.z());
        wrapper.setDeltaMovement(velocity);
        event.markForReEncode(true);

        double speedSq = momentum.x() * momentum.x()
                + momentum.y() * momentum.y()
                + momentum.z() * momentum.z();
        Watch watch = new Watch(Bukkit.getCurrentTick(), speedSq);
        watched.put(id, watch);

        // Follow-up velocity packet, sent right after the position packet.
        final int entityId = user.getEntityId();
        event.getTasksAfterSend().add(() -> {
            try {
                watch.ownPending = true;
                user.sendPacket(new WrapperPlayServerEntityVelocity(entityId, velocity));
            } catch (Throwable t) {
                watch.ownPending = false;
                mechanic.reportException("follow-up velocity packet", t);
            }
        });

        mechanic.debug("[wire] " + user.getName() + " position packet (teleport ID "
                + wrapper.getTeleportId() + ") rewritten to "
                + String.format("%.4f/%.4f/%.4f", momentum.x(), momentum.y(), momentum.z())
                + ", follow-up velocity packet queued, tick=" + Bukkit.getCurrentTick());
    }

    private void handleVelocity(PacketPlaySendEvent event, User user, UUID id) {
        Watch watch = activeWatch(id);
        if (watch == null) {
            return;
        }

        WrapperPlayServerEntityVelocity wrapper = new WrapperPlayServerEntityVelocity(event);
        if (wrapper.getEntityId() != user.getEntityId()) {
            return;
        }
        Vector3d v = wrapper.getVelocity();
        int tick = Bukkit.getCurrentTick();

        // The follow-up packet sent by this class.
        if (watch.ownPending) {
            watch.ownPending = false;
            mechanic.debug("[wire] " + user.getName() + " follow-up velocity packet " + v
                    + " sent, tick=" + tick);
            return;
        }

        // The server's own velocity packet right after the teleport: slower than the
        // captured momentum means it is the reset, so it is dropped.
        double speedSq = v.getX() * v.getX() + v.getY() * v.getY() + v.getZ() * v.getZ();
        if (!watch.resetCancelled
                && tick - watch.tick <= RESET_WINDOW_TICKS
                && speedSq < watch.speedSq) {
            watch.resetCancelled = true;
            event.setCancelled(true);
            mechanic.debug("[wire] " + user.getName() + " server velocity reset " + v
                    + " CANCELLED, tick=" + tick);
            return;
        }

        mechanic.debug("[wire] " + user.getName() + " velocity packet " + v
                + " passed through, tick=" + tick);
    }

    private Watch activeWatch(UUID id) {
        Watch watch = watched.get(id);
        if (watch == null) {
            return null;
        }
        if (System.nanoTime() - watch.createdNanos > WATCH_NANOS) {
            watched.remove(id, watch);
            return null;
        }
        return watch;
    }
}