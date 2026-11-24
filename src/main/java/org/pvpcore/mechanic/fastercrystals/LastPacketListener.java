package org.pvpcore.mechanic.fastercrystals;

import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.UUID;

/**
 * Records the packet that came before an arm swing, so the attack listener can tell a real
 * attack from a hotbar drop, an item use or an inventory click.
 *
 * <p>Runs at MONITOR so it observes the final state of the packet after other plugins and
 * after {@link CrystalAttackListener}, which must run at NORMAL. The drop mark belongs to the
 * current {@link PlayerPacketState.Swing}; it is overwritten by the packet that follows each
 * animation and re-checked by the attack task immediately before the hit.</p>
 */
final class LastPacketListener extends SimplePacketListenerAbstract {

    private final FasterCrystalsMechanic mechanic;
    private final Map<UUID, PlayerPacketState> states;

    LastPacketListener(FasterCrystalsMechanic mechanic, Map<UUID, PlayerPacketState> states) {
        super(PacketListenerPriority.MONITOR);
        this.mechanic = mechanic;
        this.states = states;
    }

    @Override
    public void onPacketPlayReceive(@NonNull PacketPlayReceiveEvent event) {
        try {
            handle(event);
        } catch (Throwable t) {
            // A malformed packet must never reach the server.
            mechanic.reportException("last-packet classification", t);
        }
    }

    private void handle(PacketPlayReceiveEvent event) {
        if (!mechanic.isActive()) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        Map<UUID, PlayerPacketState> currentStates = states;
        if (currentStates == null) {
            return;
        }
        PlayerPacketState state = currentStates.computeIfAbsent(player.getUniqueId(),
                key -> new PlayerPacketState());

        AnimPacket next = classify(event);

        // Dropping sends ANIMATION/PUNCH -> WINDOW_CLICK. Mark the swing object that the
        // attack listener captured for this click, then advance the last-packet state.
        AnimPacket previous = state.lastPacket();
        if (previous == AnimPacket.ANIMATION || previous == AnimPacket.PUNCH) {
            PlayerPacketState.Swing swing = state.swing();
            if (swing != null) {
                boolean ignore = next == AnimPacket.INV_DROP
                        || next == AnimPacket.CREATIVE_INV_ACTION;
                swing.ignore(ignore);
                if (ignore && mechanic.isDebug()) {
                    mechanic.debug("swing marked as an inventory action (" + next
                            + "); not attacking.");
                }
            }
        }
        state.lastPacket(next);
    }

    private static AnimPacket classify(PacketPlayReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PUNCH) {
            return AnimPacket.PUNCH;
        }
        if (event.getPacketType() == PacketType.Play.Client.ANIMATION) {
            return AnimPacket.ANIMATION;
        }
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);
            DiggingAction action = wrapper.getAction();
            if (action == DiggingAction.DROP_ITEM || action == DiggingAction.DROP_ITEM_STACK) {
                return AnimPacket.IGNORE;
            }
            if (action == DiggingAction.START_DIGGING) {
                return AnimPacket.START_DIGGING;
            }
        } else if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            WrapperPlayClientClickWindow wrapper = new WrapperPlayClientClickWindow(event);
            if (wrapper.getWindowClickType() == WrapperPlayClientClickWindow.WindowClickType.THROW
                    || (wrapper.getWindowClickType() == WrapperPlayClientClickWindow.WindowClickType.PICKUP
                    && wrapper.getSlot() == -999)) {
                return AnimPacket.INV_DROP;
            }
        } else if (event.getPacketType() == PacketType.Play.Client.CREATIVE_INVENTORY_ACTION) {
            return AnimPacket.CREATIVE_INV_ACTION;
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT
                || event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            return AnimPacket.IGNORE;
        } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
            if (wrapper.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                return AnimPacket.ATTACK;
            }
        }
        return AnimPacket.MISC;
    }
}
