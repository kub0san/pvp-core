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
 * loads on servers where the optional dependency is absent.</p>
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

    /** Private instruction wording for the item-name rename prompt. */
    static final String RENAME_INSTRUCTION =
            "Enter a new item name using MiniMessage. Type cancel to go back.";
    private static final String LEGACY_RENAME_INSTRUCTION = "Type a new name (MiniMessage), or 'cancel'";

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static Plugin plugin;
    private static boolean registered;
    private static Listener listener;

    /*
     * The fields below belong to the active facade listener instance, not to the class,
     * so a reload can drop an old engine (and its PacketEvents registration) atomically.
     */
    private boolean packetAttempted;
    private boolean packetInstalled;

    /**
     * owner + operationId identify one prompt; consumed guards exactly-once delivery.
     * The engine identity prevents a listener orphaned by clear() from handling a prompt
     * created by its replacement.
     */
    private record Pending(GuiChatPrompt engine, Plugin owner, UUID operationId,
                           AtomicBoolean consumed, Consumer<String> onInput,
                           Runnable onCancel, BukkitTask timeout) {
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
        if (owner == null || player == null || onInput == null || onCancel == null) {
            return;
        }
        plugin = owner;
        UUID id = player.getUniqueId();
        discard(id);
        GuiChatPrompt engine = ensureListeners(owner);
        player.closeInventory();

        if (LEGACY_RENAME_INSTRUCTION.equals(instruction)) {
            // Keep the caller's historical ask(...) shape while showing the agreed wording.
            say(player, RENAME_INSTRUCTION);
        } else {
            say(player, GuiTheme.ACCENT + "<bold>\u270E " + GuiTheme.TEXT + instruction);
            say(player, GuiTheme.MUTED + "Type your answer in chat, or type " + GuiTheme.RED + "cancel"
                    + GuiTheme.MUTED + " to go back. " + GuiTheme.FAINT + "(1 minute)");
        }

        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(owner, () -> {
            Pending expired = PENDING.get(id);
            if (expired == null || !expired.consumed().compareAndSet(false, true)) {
                return;
            }
            PENDING.remove(id, expired);
            Plugin expiredOwner = expired.owner();
            if (expiredOwner == null || !expiredOwner.isEnabled()) {
                return;
            }
            say(player, GuiTheme.warn("Timed out, nothing was changed."));
            expired.onCancel().run();
        }, TIMEOUT_TICKS);
        PENDING.put(id, new Pending(engine, owner, UUID.randomUUID(), new AtomicBoolean(),
                onInput, onCancel, timeout));
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
        PENDING.values().forEach(pending -> pending.timeout().cancel());
        PENDING.clear();
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            if (listener instanceof GuiChatPrompt engine) {
                engine.stopPacketListener();
            }
            listener = null;
        }
        registered = false;
        plugin = null;
    }

    private static void discard(UUID id) {
        Pending old = PENDING.remove(id);
        if (old != null) {
            old.timeout().cancel();
        }
    }

    private static GuiChatPrompt ensureListeners(Plugin owner) {
        if (listener == null) {
            listener = new GuiChatPrompt();
        }
        GuiChatPrompt engine = (GuiChatPrompt) listener;
        if (!engine.packetAttempted) {
            engine.packetAttempted = true;
            if (PacketEventsGate.ready()) {
                // Only this branch resolves the PacketEvents listener class.
                engine.packetInstalled = GuiChatPromptPacketListener.install(engine::submit);
            }
        }
        if (!engine.packetInstalled && !registered) {
            Bukkit.getPluginManager().registerEvents(engine, owner);
            registered = true;
        }
        return engine;
    }

    private void stopPacketListener() {
        if (packetInstalled) {
            GuiChatPromptPacketListener.stop();
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
        Plugin owner = plugin;
        Pending pending = PENDING.get(id);
        if (owner == null || !owner.isEnabled() || pending == null
                || pending.engine() != this || pending.owner() != owner) {
            return false;
        }
        String answer = text.trim();
        if (pending.consumed().compareAndSet(false, true)) {
            pending.timeout().cancel();
            try {
                Bukkit.getScheduler().runTask(owner, () -> {
                    PENDING.remove(id, pending);
                    if (!owner.isEnabled()) {
                        return;
                    }
                    if ("cancel".equalsIgnoreCase(answer)) {
                        pending.onCancel().run();
                    } else {
                        pending.onInput().accept(answer);
                    }
                });
            } catch (IllegalStateException notSchedulable) {
                // Owner was disabled between the check and scheduling; drop silently.
                PENDING.remove(id, pending);
            }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
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
        if (!submit(event.getPlayer().getUniqueId(), event.getMessage())) {
            return;
        }
        event.setCancelled(true);
        event.getRecipients().clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        discard(event.getPlayer().getUniqueId());
    }
}
