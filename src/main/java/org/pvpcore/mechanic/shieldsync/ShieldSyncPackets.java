package org.pvpcore.mechanic.shieldsync;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.pvpcore.PvPCorePlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Optional PacketEvents ping probes for Shield Sync.
 *
 * <p>This is the only Shield Sync class that references PacketEvents. It is loaded only
 * after {@link org.pvpcore.mechanic.PacketEventsGate#ready()} succeeds; the mechanic
 * remains usable with Bukkit's {@code Player#getPing()} when PacketEvents is absent or
 * probe setup fails.</p>
 *
 * <p>The probes use the protocol PING/PONG pair, not keep-alives. The server's pong
 * handler ignores unknown ids, so no packet is cancelled or rewritten. PacketEvents'
 * lifecycle is never touched: this class only uses the API of the already-installed
 * PacketEvents plugin.</p>
 *
 * <p><strong>Probe send path.</strong> Each probe is written on the player's Netty event
 * loop via {@link ChannelHelper#runInEventLoop}, with {@code System.nanoTime()} sampled
 * inside that runnable immediately before {@code sendPacket}. This is the same approach
 * KnockbackSync uses in {@code PlayerData#sendPing(boolean)}: running on the event loop
 * avoids a thread switch between the timestamp and the write, which tightens the RTT
 * measurement. The distinct {@code 0x5353xxxx} id range already prevents collisions
 * with vanilla keep-alives, so no strategy switch is needed.</p>
 */
final class ShieldSyncPackets {

    /** Distinctive id range; the low 16 bits are a wrapping counter. */
    private static final int PROBE_BASE = 0x53530000;
    private static final int PROBE_MASK = 0xFFFF0000;
    private static final long PROBE_INTERVAL_TICKS = 20L;

    private static final Map<UUID, User> USERS = new ConcurrentHashMap<>();
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final AtomicBoolean ERROR_LOGGED = new AtomicBoolean();

    private static PvPCorePlugin plugin;
    private static ShieldSyncMechanic mechanic;
    private static PacketListenerAbstract listener;
    private static BukkitTask sender;

    private ShieldSyncPackets() {
    }

    static boolean start(PvPCorePlugin plugin, ShieldSyncMechanic mechanic) {
        // Do NOT check PacketEvents.getAPI().isInitialized() here. PacketEvents' init is
        // async; at PvPCore STARTUP the plugin is enabled but isInitialized() can still
        // return false, and registerListener() safely queues before init finishes.
        stop();
        ERROR_LOGGED.set(false);
        ShieldSyncPackets.plugin = plugin;
        ShieldSyncPackets.mechanic = mechanic;

        try {
            PacketListenerAbstract newListener = new PacketListenerAbstract(PacketListenerPriority.MONITOR) {
                @Override
                public void onPacketReceive(PacketReceiveEvent event) {
                    handlePong(event);
                }
            };
            listener = newListener;
            PacketEvents.getAPI().getEventManager().registerListener(newListener);

            sender = Bukkit.getScheduler().runTaskTimerAsynchronously(
                    plugin, ShieldSyncPackets::sendProbes,
                    PROBE_INTERVAL_TICKS, PROBE_INTERVAL_TICKS);
            return true;
        } catch (Throwable t) {
            logFirst("Shield Sync: PacketEvents ping probes could not start; using Bukkit ping.", t);
            stop();
            return false;
        }
    }

    static void stop() {
        PacketListenerAbstract oldListener = listener;
        listener = null;
        if (oldListener != null) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(oldListener);
            } catch (Throwable t) {
                logFirst("Shield Sync: could not unregister the PacketEvents pong listener.", t);
            }
        }

        BukkitTask oldSender = sender;
        sender = null;
        if (oldSender != null) {
            oldSender.cancel();
        }

        USERS.clear();
        mechanic = null;
        plugin = null;
    }

    static boolean track(Player player) {
        if (player == null) {
            return false;
        }
        try {
            User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (user != null) {
                USERS.put(player.getUniqueId(), user);
                return true;
            }
        } catch (Throwable t) {
            logFirst("Shield Sync: could not resolve a PacketEvents user; using Bukkit ping for that player.", t);
        }
        return false;
    }

    static void untrack(UUID playerId) {
        if (playerId != null) {
            USERS.remove(playerId);
        }
    }

    private static void sendProbes() {
        ShieldSyncMechanic currentMechanic = mechanic;
        if (currentMechanic == null || USERS.isEmpty()) {
            return;
        }

        int id = PROBE_BASE | (COUNTER.getAndIncrement() & 0xFFFF);
        for (Map.Entry<UUID, User> entry : USERS.entrySet()) {
            ShieldSyncState state = currentMechanic.state(entry.getKey());
            if (state == null) {
                continue;
            }

            User user = entry.getValue();
            // Sample the timestamp and write the packet on the player's Netty event
            // loop. This is the same placement KnockbackSync uses: the Runnable is
            // queued behind any in-flight channel work, so nanoTime() is as close to
            // the actual write as the channel allows, and no scheduler thread switch
            // sits between the two.
            ChannelHelper.runInEventLoop(user.getChannel(), () -> {
                state.probeId = id;
                state.probeSentNanos = System.nanoTime();
                try {
                    user.sendPacket(new WrapperPlayServerPing(id));
                } catch (Throwable t) {
                    state.probeId = -1;
                    state.probeSentNanos = 0L;
                    logFirst("Shield Sync: could not send a PacketEvents ping probe; using Bukkit ping for affected players.", t);
                }
            });
        }
    }

    private static void handlePong(PacketReceiveEvent event) {
        ShieldSyncMechanic currentMechanic = mechanic;
        if (currentMechanic == null) {
            return;
        }

        try {
            if (event.getPacketType() != PacketType.Play.Client.PONG) {
                return;
            }
            User user = event.getUser();
            if (user == null || user.getUUID() == null) {
                return;
            }

            int id = new WrapperPlayClientPong(event).getId();
            if ((id & PROBE_MASK) != PROBE_BASE) {
                return;
            }

            ShieldSyncState state = currentMechanic.state(user.getUUID());
            if (state == null || state.probeId != id) {
                return;
            }

            long sentNanos = state.probeSentNanos;
            if (sentNanos == 0L) {
                return;
            }

            state.probeId = -1;
            long rttMs = (System.nanoTime() - sentNanos) / 1_000_000L;
            if (rttMs < 0L) {
                return;
            }

            currentMechanic.onMedian(user.getUUID(), state.window.add(rttMs));
        } catch (Throwable t) {
            logFirst("Shield Sync: could not read a PacketEvents pong reply; ignoring it.", t);
        }
    }

    private static void logFirst(String message, Throwable throwable) {
        if (ERROR_LOGGED.compareAndSet(false, true)) {
            PvPCorePlugin currentPlugin = plugin;
            if (currentPlugin != null) {
                currentPlugin.getLogger().log(Level.WARNING, message, throwable);
            }
        }
    }
}