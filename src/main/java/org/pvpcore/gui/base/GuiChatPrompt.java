package org.pvpcore.gui.base;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.pvpcore.mechanic.PacketEventsGate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Asks a player to type something in chat. The menu closes, the player types an answer
 * (or "cancel"), and the answer comes back on the main thread. Nothing the player types
 * is shown to other players.
 *
 * <p>Primary interception is packet-level: when PacketEvents is installed and ready, an
 * isolated {@code GuiChatPromptPacketListener} drops the chat packet before Bukkit events
 * exist. Without a registered packet listener, LOWEST handlers on both the Paper and the
 * legacy chat event suppress delivery (including a renderer/view rebroadcast). The
 * PacketEvents class is only touched after {@link PacketEventsGate#ready()}, so this class
 * loads on servers where the optional dependency is absent. Bukkit handlers are registered
 * unconditionally (quit cleanup is needed on both paths); the fallback chat handlers are
 * explicit no-ops while the packet path is active.</p>
 *
 * <p><strong>Do not call this from a screen that saves state on close</strong> -- an
 * editor, whose close handler consumes the editor context via
 * {@code GUI.getAndRemoveEditorContext} and saves the inventory. Calling ask() closes
 * the inventory and fires that close handler, so the context is gone before the prompt
 * is answered. If a prompt ever is added to an editor, its onInput/onCancel must
 * re-establish the context (or make the editor save before ask() is called).</p>
 */
public final class GuiChatPrompt implements Listener {
    static final long TIMEOUT_TICKS = 20L * 60;

    /** Exact private instruction emitted by {@link #askRename}. */
    static final String RENAME_INSTRUCTION =
            "Enter a new item name using MiniMessage. Type cancel to go back.";

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static volatile GuiChatPrompt listener;

    /*
     * Engine state belongs to the active facade listener instance, not to the class, so a
     * reload can drop an old engine (and its PacketEvents registration) atomically.
     */
    private boolean packetAttempted;
    private volatile boolean packetInstalled;
    private GuiChatPromptPacketListener packetListener;

    /**
     * One prompt. owner and operationId identify the operation; consumed guards
     * exactly-once delivery. A scheduled callback requires this exact instance to still
     * be the map entry, so clear() and a later ask() invalidate it independently of the
     * owner plugin's enabled state.
     */
    private static final class Pending {
        final GuiChatPrompt engine;
        final Plugin owner;
        final UUID operationId;
        final AtomicBoolean consumed = new AtomicBoolean();
        final Consumer<String> onInput;
        final Runnable onCancel;
        volatile BukkitTask timeout;

        Pending(GuiChatPrompt engine, Plugin owner, UUID operationId,
                Consumer<String> onInput, Runnable onCancel) {
            this.engine = engine;
            this.owner = owner;
            this.operationId = operationId;
            this.onInput = onInput;
            this.onCancel = onCancel;
        }
    }

    private GuiChatPrompt() {
    }

    /**
     * @param instruction what to type, shown in chat
     * @param onInput     called with the typed text
     * @param onCancel    called when the player types "cancel" or takes too long
     */
    public static void ask(Plugin owner, Player player, String instruction,
                           Consumer<String> onInput, Runnable onCancel) {
        askInternal(owner, player, instruction, true, onInput, onCancel);
    }

    /**
     * Item-name rename prompt. Emits exactly one private instruction line; callers should
     * use this instead of passing instruction strings that could drift.
     */
    public static void askRename(Plugin owner, Player player,
                                 Consumer<String> onInput, Runnable onCancel) {
        askInternal(owner, player, RENAME_INSTRUCTION, false, onInput, onCancel);
    }

    private static void askInternal(Plugin owner, Player player, String instruction,
                                    boolean showCancelHint,
                                    Consumer<String> onInput, Runnable onCancel) {
        if (owner == null || player == null || onInput == null || onCancel == null) {
            return;
        }
        UUID id = player.getUniqueId();
        discard(id);
        GuiChatPrompt engine = ensureListeners(owner);
        player.closeInventory();

        if (showCancelHint) {
            say(player, GuiTheme.ACCENT + "<bold>\u270E " + GuiTheme.TEXT + instruction);
            say(player, GuiTheme.MUTED + "Type your answer in chat, or type " + GuiTheme.RED + "cancel"
                    + GuiTheme.MUTED + " to go back. " + GuiTheme.FAINT + "(1 minute)");
        } else {
            say(player, instruction);
        }

        Pending pending = new Pending(engine, owner, UUID.randomUUID(), onInput, onCancel);
        pending.timeout = Bukkit.getScheduler().runTaskLater(owner, () -> {
            if (PENDING.get(id) != pending || !pending.consumed.compareAndSet(false, true)) {
                return;
            }
            PENDING.remove(id, pending);
            Plugin promptOwner = pending.owner;
            if (promptOwner == null || !promptOwner.isEnabled()) {
                return;
            }
            say(player, GuiTheme.warn("Timed out, nothing was changed."));
            pending.onCancel.run();
        }, TIMEOUT_TICKS);
        PENDING.put(id, pending);
    }

    public static void say(Player player, String miniMessage) {
        player.sendMessage(MiniMessage.miniMessage().deserialize(miniMessage));
    }

    /** Whether a player is currently waiting to answer a prompt. */
    public static boolean isWaiting(UUID id) {
        return PENDING.containsKey(id);
    }

    /** Drops every waiting prompt and forgets the registration so a reload can re-register. */
    public static void clear() {
        PENDING.values().forEach(pending -> {
            if (pending.timeout != null) {
                pending.timeout.cancel();
            }
        });
        PENDING.clear();
        GuiChatPrompt engine = listener;
        if (engine != null) {
            HandlerList.unregisterAll(engine);
            engine.stopPacketListener();
            listener = null;
        }
    }

    private static void discard(UUID id) {
        Pending old = PENDING.remove(id);
        if (old != null && old.timeout != null) {
            old.timeout.cancel();
        }
    }

    private static GuiChatPrompt ensureListeners(Plugin owner) {
        GuiChatPrompt engine = listener;
        if (engine == null) {
            engine = new GuiChatPrompt();
            // Always registered: PlayerQuitEvent cleanup is required on the PE path too.
            Bukkit.getPluginManager().registerEvents(engine, owner);
            listener = engine;
        }
        if (!engine.packetAttempted) {
            engine.packetAttempted = true;
            if (PacketEventsGate.ready()) {
                // Only this branch resolves the PacketEvents listener class.
                GuiChatPromptPacketListener candidate =
                        new GuiChatPromptPacketListener(engine::submit);
                if (candidate.install()) {
                    engine.packetListener = candidate;
                    engine.packetInstalled = true;
                }
            }
        }
        return engine;
    }

    private void stopPacketListener() {
        if (packetListener != null) {
            packetListener.stop();
            packetListener = null;
        }
        packetInstalled = false;
        packetAttempted = false;
    }

    /**
     * Shared entry point for both interception paths. Returns {@code true} when the
     * incoming chat must be suppressed; the callback runs at most once, on the main
     * thread. A consumed prompt stays in the map until that task runs so a duplicate
     * event from a rebroadcast chat plugin is dropped silently instead of being staged.
     */
    boolean submit(UUID id, String text) {
        if (id == null || text == null || text.startsWith("/")) {
            return false;
        }
        Pending pending = PENDING.get(id);
        if (pending == null || pending.engine != this) {
            return false;
        }
        // The prompt owns its scheduler: a later ask() must not change this owner.
        Plugin owner = pending.owner;
        if (owner == null || !owner.isEnabled()) {
            return false;
        }
        String answer = text.trim();
        if (pending.consumed.compareAndSet(false, true)) {
            if (pending.timeout != null) {
                pending.timeout.cancel();
            }
            try {
                Bukkit.getScheduler().runTask(owner, () -> {
                    // Identity is the generation/operation check: clear(), reload or a
                    // replacement prompt invalidates this callback even while owner is enabled.
                    Pending current = PENDING.get(id);
                    if (current != pending
                            || !pending.operationId.equals(current.operationId)) {
                        return;
                    }
                    PENDING.remove(id, pending);
                    Plugin promptOwner = pending.owner;
                    if (promptOwner == null || !promptOwner.isEnabled()) {
                        return;
                    }
                    if ("cancel".equalsIgnoreCase(answer)) {
                        pending.onCancel.run();
                    } else {
                        pending.onInput.accept(answer);
                    }
                });
            } catch (IllegalStateException notSchedulable) {
                // Owner was disabled between the check and scheduling; drop silently.
                PENDING.remove(id, pending);
            }
        }
        return true;
    }

    /**
     * Package-private seams for unit tests: simulate the packet path and feed it chat
     * without loading any PacketEvents class in MockBukkit.
     */
    static void packetPathForTest(boolean active) {
        GuiChatPrompt engine = listener;
        if (engine == null) {
            throw new IllegalStateException("ask() must register an engine first");
        }
        engine.packetInstalled = active;
    }

    static boolean submitFromPacket(UUID id, String text) {
        GuiChatPrompt engine = listener;
        return engine != null && engine.submit(id, text);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (packetInstalled) {
            // PacketEvents already cancelled the packet before Bukkit events; stay dormant.
            return;
        }
        UUID id = event.getPlayer().getUniqueId();
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (!submit(id, text)) {
            return;
        }
        event.setCancelled(true);
        event.viewers().clear();
        event.renderer((source, sourceDisplayName, message, viewer) -> Component.empty());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        if (packetInstalled) {
            // No fallback rebroadcast suppression while the packet path is active.
            return;
        }
        if (!submit(event.getPlayer().getUniqueId(), event.getMessage())) {
            return;
        }
        event.setCancelled(true);
        event.getRecipients().clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Registered on both paths: a prompt must never call back after disconnect.
        discard(event.getPlayer().getUniqueId());
    }
}
