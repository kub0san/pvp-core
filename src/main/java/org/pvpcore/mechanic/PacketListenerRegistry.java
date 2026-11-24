package org.pvpcore.mechanic;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Shared PacketEvents listener lifecycle for mechanics that manage a fixed set of listeners.
 * Register/unregister is best-effort and independent per listener so shutdown races do not
 * leak or duplicate handlers.
 */
public final class PacketListenerRegistry {

    private final List<PacketListenerCommon> listeners = new ArrayList<>();

    public synchronized int registerAll(Collection<? extends PacketListenerCommon> created) {
        stop();
        try {
            for (PacketListenerCommon listener : created) {
                PacketEvents.getAPI().getEventManager().registerListener(listener);
                listeners.add(listener);
            }
        } catch (Throwable t) {
            stop();
            throw t;
        }
        return listeners.size();
    }

    public synchronized int register(PacketListenerCommon listener) {
        return registerAll(List.of(listener));
    }

    public synchronized int stop() {
        if (listeners.isEmpty()) {
            return 0;
        }
        List<PacketListenerCommon> current = new ArrayList<>(listeners);
        listeners.clear();
        int removed = 0;
        for (PacketListenerCommon listener : current) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(listener);
                removed++;
            } catch (Throwable ignored) {
                // PacketEvents may already be shutting down; keep cleaning up the rest.
            }
        }
        return removed;
    }

    public synchronized boolean isEmpty() {
        return listeners.isEmpty();
    }
}