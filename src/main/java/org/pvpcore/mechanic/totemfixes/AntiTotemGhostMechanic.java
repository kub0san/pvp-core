package org.pvpcore.mechanic.totemfixes;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.mechanic.Mechanic;
import org.pvpcore.mechanic.PacketEventsGate;

import java.util.Set;
import java.util.logging.Level;

/** Anti Totem Ghost: packet-aware inventory synchronization and a bounded death grace. */
public final class AntiTotemGhostMechanic implements Mechanic {

    private boolean enabled;
    private TotemSwapTracker tracker;
    private Object packetListener;
    private TotemInventorySync inventorySync;
    private TotemDamageListener damageListener;

    @Override
    public String id() {
        return "antitotemghost";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("totemghost", "totemfix");
    }

    @Override
    public String displayName() {
        return "Anti Totem Ghost";
    }

    @Override
    public String description() {
        return "Tracks pending totem swaps, corrects inventory sync and briefly defers qualifying lethal hits";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public boolean requiresPacketEvents() {
        return true;
    }

    @Override
    public boolean enable(PvPCorePlugin plugin) {
        if (enabled) {
            return true;
        }

        TotemSwapTracker newTracker = new TotemSwapTracker();
        TotemInventorySync newInventorySync = new TotemInventorySync(plugin, newTracker);
        TotemDamageListener newDamageListener = new TotemDamageListener(plugin, newTracker);

        boolean packetRegistered = false;
        boolean inventoryRegistered = false;
        boolean damageRegistered = false;
        Object newPacketListener = null;
        try {
            newPacketListener = TotemPacketSupport.register(newTracker);
            packetRegistered = true;

            Bukkit.getPluginManager().registerEvents(newInventorySync, plugin);
            inventoryRegistered = true;

            Bukkit.getPluginManager().registerEvents(newDamageListener, plugin);
            damageRegistered = true;

            this.tracker = newTracker;
            this.packetListener = newPacketListener;
            this.inventorySync = newInventorySync;
            this.damageListener = newDamageListener;
            this.enabled = true;

            plugin.getLogger().info("Anti Totem Ghost enabled (PacketEvents "
                    + PacketEventsGate.version() + ", 1-tick grace window).");
            return true;
        } catch (Throwable failure) {
            plugin.getLogger().log(Level.SEVERE, "Failed to enable Anti Totem Ghost", failure);

            if (damageRegistered) {
                HandlerList.unregisterAll(newDamageListener);
            }
            if (inventoryRegistered) {
                HandlerList.unregisterAll(newInventorySync);
            }
            if (packetRegistered) {
                try {
                    TotemPacketSupport.unregister(newPacketListener);
                } catch (Throwable unregisterFailure) {
                    plugin.getLogger().log(Level.SEVERE,
                            "Failed to unregister Anti Totem Ghost packet listener after startup failure",
                            unregisterFailure);
                }
            }
            newTracker.clear();
            newInventorySync.close();
            newDamageListener.flushPending();
            return false;
        }
    }

    @Override
    public void disable(PvPCorePlugin plugin) {
        if (!enabled && packetListener == null && inventorySync == null && damageListener == null) {
            return;
        }

        // Stop receiving new packets/events first. Then apply any already-cancelled hits
        // immediately so disabling this mechanic cannot grant accidental invulnerability.
        if (packetListener != null) {
            try {
                TotemPacketSupport.unregister(packetListener);
            } catch (Throwable failure) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not unregister Anti Totem Ghost packet listener", failure);
            }
        }

        if (damageListener != null) {
            HandlerList.unregisterAll(damageListener);
            damageListener.flushPending();
        }
        if (inventorySync != null) {
            HandlerList.unregisterAll(inventorySync);
            inventorySync.close();
        }

        if (tracker != null) {
            tracker.clear();
        }

        packetListener = null;
        inventorySync = null;
        damageListener = null;
        tracker = null;
        enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
