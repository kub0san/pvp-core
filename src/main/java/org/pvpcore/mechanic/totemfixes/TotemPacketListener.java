package org.pvpcore.mechanic.totemfixes;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;

import java.util.UUID;

/** Observes client inventory actions before their Bukkit events are processed. */
final class TotemPacketListener extends PacketListenerAbstract {

    private final TotemSwapTracker tracker;

    TotemPacketListener(TotemSwapTracker tracker) {
        super(PacketListenerPriority.NORMAL);
        this.tracker = tracker;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getUser() == null) {
            return;
        }

        UUID playerId = event.getUser().getUUID();
        if (playerId == null) {
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            try {
                WrapperPlayClientClickWindow packet = new WrapperPlayClientClickWindow(event);
                // A quick-craft drag is a sequence of start/add/end packets but produces
                // one InventoryDragEvent. Track only the final stage so the packet and
                // Bukkit event counters remain paired.
                if (packet.getWindowClickType() != WrapperPlayClientClickWindow.WindowClickType.QUICK_CRAFT
                        || QuickCraftButtons.isEnd(packet.getButton())) {
                    tracker.packetReceived(playerId, TotemSwapTracker.Action.INVENTORY_CLICK);
                }
            } catch (RuntimeException malformedPacket) {
                // A malformed or version-specific packet must not break packet processing.
            }
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            tracker.packetReceived(playerId, TotemSwapTracker.Action.HOTBAR_CHANGE);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.CREATIVE_INVENTORY_ACTION) {
            tracker.packetReceived(playerId, TotemSwapTracker.Action.CREATIVE_ACTION);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            try {
                WrapperPlayClientPlayerDigging packet = new WrapperPlayClientPlayerDigging(event);
                if (packet.getAction() == DiggingAction.SWAP_ITEM_WITH_OFFHAND) {
                    tracker.packetReceived(playerId, TotemSwapTracker.Action.HAND_SWAP);
                }
            } catch (RuntimeException malformedPacket) {
                // A malformed or version-specific packet must not break packet processing.
            }
        }
    }

    @Override
    public void onUserDisconnect(UserDisconnectEvent event) {
        if (event.getUser() != null) {
            tracker.remove(event.getUser().getUUID());
        }
    }
}
