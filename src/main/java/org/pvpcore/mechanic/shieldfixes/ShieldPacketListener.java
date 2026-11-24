package org.pvpcore.mechanic.shieldfixes;

import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.sound.Sound;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntitySoundEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Packet-level half of Shield Fixes.
 *
 * <p>Paper 1.21.11 and 26.3 send both the correct shield-block sound and the buggy player-hurt
 * sound on a blocked hit (MC-105068), and when ShieldStun's Paper setting is on,
 * neither is sent (Paper issue #11761). {@code ShieldFixesMechanic} plays one
 * {@code item.shield.block} sound itself, and this listener mutes the two outgoing
 * sounds so the client hears exactly one.</p>
 *
 * <p>Hand state is the {@code LivingEntity} flags byte: {@code 0x01} for the main
 * hand, {@code 0x03} for the off hand. Every outgoing metadata packet that carries
 * index 8 is recorded per viewer, so the resync can skip viewers that already have
 * the right state.</p>
 */
final class ShieldPacketListener extends SimplePacketListenerAbstract {

    private static final String PLAYER_HURT_PREFIX = "minecraft:entity.player.hurt";
    private static final String SHIELD_BLOCK = "minecraft:item.shield.block";
    private static final String SHIELD_BREAK = "minecraft:item.shield.break";

    private final ShieldFixesMechanic mechanic;
    private final ShieldState state;

    ShieldPacketListener(ShieldFixesMechanic mechanic, ShieldState state) {
        super(PacketListenerPriority.MONITOR);
        this.mechanic = mechanic;
        this.state = state;
    }

    @Override
    public void onPacketPlaySend(PacketPlaySendEvent event) {
        if (!mechanic.isActive() || event.isCancelled()) {
            return;
        }
        try {
            if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
                recordHandState(event);
            } else if (event.getPacketType() == PacketType.Play.Server.SOUND_EFFECT) {
                mutePositionalBlockedHitSound(event);
            } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_SOUND_EFFECT) {
                muteEntityBlockedHitSound(event);
            }
        } catch (Throwable t) {
            // PacketEvents listeners must never break the pipeline.
            mechanic.debug("Shield Fixes ignored a malformed outgoing packet: " + t);
        }
    }

    @Override
    public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
        if (!mechanic.isActive() || event.isCancelled()) {
            return;
        }
        try {
            if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
                scheduleResync(event, 2L);
            } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
                WrapperPlayClientPlayerDigging packet = new WrapperPlayClientPlayerDigging(event);
                DiggingAction action = packet.getAction();
                if (action == DiggingAction.RELEASE_USE_ITEM
                        || action == DiggingAction.SWAP_ITEM_WITH_OFFHAND) {
                    scheduleResync(event, 1L);
                }
            }
        } catch (Throwable t) {
            mechanic.debug("Shield Fixes ignored a malformed incoming packet: " + t);
        }
    }

    private void recordHandState(PacketPlaySendEvent event) {
        WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
        int entityId = packet.getEntityId();
        if (!state.isTrackedPlayer(entityId)) {
            return;
        }
        Player viewer = event.getPlayer();
        if (viewer == null) {
            return;
        }
        for (EntityData<?> data : packet.getEntityMetadata()) {
            if (data.getIndex() != ShieldState.HAND_STATE_INDEX || !(data.getValue() instanceof Byte flags)) {
                continue;
            }
            state.recordSentHand(entityId, viewer.getUniqueId(), flags);
            if (mechanic.isDebug()) {
                mechanic.debug("[sniff] ENTITY_METADATA entity=" + entityId
                        + " hand=0x" + Integer.toHexString(flags & 0xFF)
                        + " viewer=" + viewer.getName());
            }
            return;
        }
    }

    /**
     * Mutes Paper's hurt sound (MC-105068) and Paper's own block sound when they are
     * sent at a blocked-hit position. ShieldFixes plays its own block sound at a
     * slightly raised position, so its packet does not match the mark and is not muted
     * by this method.
     */
    private void mutePositionalBlockedHitSound(PacketPlaySendEvent event) {
        WrapperPlayServerSoundEffect packet = new WrapperPlayServerSoundEffect(event);
        Sound sound = packet.getSound();
        debugSound(event, sound, packet.getPosition());
        if (sound == null || sound.getSoundId() == null) {
            return;
        }
        String id = sound.getSoundId().toString();
        boolean isHurt = id.startsWith(PLAYER_HURT_PREFIX);
        boolean isBlock = id.equals(SHIELD_BLOCK);
        if (!isHurt && !isBlock) {
            return;
        }
        Player viewer = event.getPlayer();
        if (viewer == null) {
            return;
        }
        Vector3d position = packet.getPosition();
        UUID worldId = viewer.getWorld().getUID();
        if (!state.isBlockedAt(worldId, position.x, position.y, position.z, System.nanoTime())) {
            return;
        }
        // Mute the sound. ShieldFixes plays its own item.shield.block separately.
        packet.setVolume(0.0F);
        event.markForReEncode(true);
        if (mechanic.isDebug()) {
            mechanic.debug("Muted blocked-hit " + (isHurt ? "hurt" : "block") + " sound for "
                    + viewer.getName() + " at "
                    + position.x + "," + position.y + "," + position.z + ".");
        }
    }

    /**
     * Same as {@link #mutePositionalBlockedHitSound} for the entity-sound packet form,
     * which a future Paper build might use instead of the positional form.
     */
    private void muteEntityBlockedHitSound(PacketPlaySendEvent event) {
        WrapperPlayServerEntitySoundEffect packet = new WrapperPlayServerEntitySoundEffect(event);
        Sound sound = packet.getSound();
        debugSound(event, sound, "entity=" + packet.getEntityId());
        if (sound == null || sound.getSoundId() == null) {
            return;
        }
        String id = sound.getSoundId().toString();
        boolean isHurt = id.startsWith(PLAYER_HURT_PREFIX);
        boolean isBlock = id.equals(SHIELD_BLOCK);
        if (!isHurt && !isBlock) {
            return;
        }
        if (!state.isBlockedEntity(packet.getEntityId(), System.nanoTime())) {
            return;
        }
        packet.setVolume(0.0F);
        event.markForReEncode(true);
        if (mechanic.isDebug()) {
            mechanic.debug("Muted blocked-hit entity " + (isHurt ? "hurt" : "block")
                    + " sound for entity " + packet.getEntityId() + ".");
        }
    }

    private void scheduleResync(PacketPlayReceiveEvent event, long delayTicks) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        if (mechanic.isDebug()) {
            mechanic.debug("[sniff] " + event.getPacketType().getName()
                    + " from " + player.getName() + ".");
        }
        mechanic.scheduleResync(player, delayTicks, false);
    }

    private void debugSound(PacketPlaySendEvent event, Sound sound, Vector3d position) {
        if (!mechanic.isDebug() || sound == null || sound.getSoundId() == null) {
            return;
        }
        String id = sound.getSoundId().toString();
        if (!id.startsWith(PLAYER_HURT_PREFIX) && !id.equals(SHIELD_BLOCK) && !id.equals(SHIELD_BREAK)) {
            return;
        }
        Player viewer = event.getPlayer();
        mechanic.debug("[sniff] " + event.getPacketType().getName() + " " + id
                + " at " + position.x + "," + position.y + "," + position.z
                + " -> " + (viewer == null ? "?" : viewer.getName()));
    }

    private void debugSound(PacketPlaySendEvent event, Sound sound, String detail) {
        if (!mechanic.isDebug() || sound == null || sound.getSoundId() == null) {
            return;
        }
        String id = sound.getSoundId().toString();
        if (!id.startsWith(PLAYER_HURT_PREFIX) && !id.equals(SHIELD_BLOCK) && !id.equals(SHIELD_BREAK)) {
            return;
        }
        Player viewer = event.getPlayer();
        mechanic.debug("[sniff] " + event.getPacketType().getName() + " " + id + " "
                + detail + " -> " + (viewer == null ? "?" : viewer.getName()));
    }
}