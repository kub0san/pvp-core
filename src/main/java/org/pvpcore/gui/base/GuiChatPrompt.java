package org.pvpcore.gui.base;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Asks a player to type something in chat. The menu closes, the player types an answer
 * (or "cancel"), and the answer comes back on the main thread. Nothing the player types
 * is shown to other players.
 *
 * <p>Registers its own listener the first time it is used.</p>
 *
 * <p><strong>Do not call this from a screen that saves state on close</strong> -- an
 * editor, whose close handler consumes the editor context via
 * {@code GUI.getAndRemoveEditorContext} and saves the inventory. Calling ask() closes
 * the inventory and fires that close handler, so the context is gone before the prompt
 * is answered. If a prompt ever is added to an editor, its onInput/onCancel must
 * re-establish the context (or make the editor save before ask() is called).</p>
 */
public final class GuiChatPrompt implements Listener {
    private static final long TIMEOUT_TICKS = 20L * 60;
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static Plugin plugin;
    private static boolean registered;

    private record Pending(Consumer<String> onInput, Runnable onCancel, BukkitTask timeout) {
    }

    private GuiChatPrompt() {
    }

    /**
     * @param instruction what to type, shown in chat
     * @param onInput     called with the typed text
     * @param onCancel    called when the player types "cancel" or takes too long
     */
    public static void ask(Plugin plugin, Player player, String instruction, Consumer<String> onInput, Runnable onCancel) {
        GuiChatPrompt.plugin = plugin;
        if (!registered) {
            Bukkit.getPluginManager().registerEvents(new GuiChatPrompt(), plugin);
            registered = true;
        }
        UUID id = player.getUniqueId();
        discard(id);
        player.closeInventory();

        say(player, GuiTheme.ACCENT + "<bold>\u270E " + GuiTheme.TEXT + instruction);
        say(player, GuiTheme.MUTED + "Type your answer in chat, or type " + GuiTheme.RED + "cancel" + GuiTheme.MUTED
                + " to go back. " + GuiTheme.FAINT + "(1 minute)");

        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Pending expired = PENDING.remove(id);
            if (expired != null) {
                say(player, GuiTheme.warn("Timed out, nothing was changed."));
                expired.onCancel().run();
            }
        }, TIMEOUT_TICKS);
        PENDING.put(id, new Pending(onInput, onCancel, timeout));
    }

    public static void say(Player player, String miniMessage) {
        player.sendMessage(MiniMessage.miniMessage().deserialize(miniMessage));
    }

    /** Whether a player is currently waiting to answer a prompt. */
    public static boolean isWaiting(UUID id) {
        return PENDING.containsKey(id);
    }

    private static void discard(UUID id) {
        Pending old = PENDING.remove(id);
        if (old != null) {
            old.timeout().cancel();
        }
    }

    /** Drops every waiting prompt. Called when the plugin shuts down. */
    public static void clear() {
        PENDING.values().forEach(p -> p.timeout().cancel());
        PENDING.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Pending pending = PENDING.remove(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }
        event.setCancelled(true);
        pending.timeout().cancel();
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase("cancel")) {
                pending.onCancel().run();
            } else {
                pending.onInput().accept(text);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        discard(event.getPlayer().getUniqueId());
    }
}
