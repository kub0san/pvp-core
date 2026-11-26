package org.pvpcore.gui.base;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiChatPromptTest {
    private static final String SENTINEL = "QA_RENAME_SENTINEL_7F3A";
    private static final String INSTRUCTION =
            "Enter a new item name using MiniMessage. Type cancel to go back.";
    private static final String GENERIC_INSTRUCTION = "Type your answer";

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        GuiChatPrompt.clear();
        MockBukkit.unmock();
    }

    /**
     * Fake gateway: both chat pipelines a server may deliver to the fallback handlers.
     * One send is dispatched through Paper's AsyncChatEvent and the legacy event, as a
     * chat plugin rebroadcast would do.
     */
    private static final class ChatGateway {
        final AsyncChatEvent paper;
        final AsyncPlayerChatEvent legacy;

        ChatGateway(Player sender, String text, Set<Player> viewers) {
            paper = new AsyncChatEvent(false, sender, new HashSet<>(viewers),
                    ChatRenderer.defaultRenderer(), Component.text(text), sender.name(), null);
            legacy = new AsyncPlayerChatEvent(false, sender, text, new HashSet<>(viewers));
        }

        void dispatch() {
            Bukkit.getPluginManager().callEvent(paper);
            Bukkit.getPluginManager().callEvent(legacy);
        }
    }

    private static String drain(PlayerMock player) {
        StringBuilder text = new StringBuilder();
        Component next;
        while ((next = player.nextComponentMessage()) != null) {
            text.append(PlainTextComponentSerializer.plainText().serialize(next)).append('\n');
        }
        return text.toString();
    }

    private void ask(Plugin plugin, Player player, AtomicReference<String> answer, AtomicInteger cancels) {
        GuiChatPrompt.ask(plugin, player, GENERIC_INSTRUCTION,
                answer::set, cancels::incrementAndGet);
    }

    /** Regression for L-01: prompts must work again after a reload/disable cycle. */
    @Test
    void promptStillWorksAfterReload() {
        PluginMock plugin = MockBukkit.createMockPlugin();
        Player player = server.addPlayer();
        AtomicInteger answers = new AtomicInteger();

        GuiChatPrompt.ask(plugin, player, "first", text -> answers.incrementAndGet(), () -> {});
        GuiChatPrompt.clear();
        Bukkit.getPluginManager().disablePlugin(plugin);
        Bukkit.getPluginManager().enablePlugin(plugin);

        GuiChatPrompt.ask(plugin, player, "second", text -> answers.incrementAndGet(), () -> {});
        Bukkit.getPluginManager().callEvent(new AsyncChatEvent(false, player, new HashSet<>(),
                ChatRenderer.defaultRenderer(), Component.text("answer"), player.name(), null));
        server.getScheduler().performTicks(2);

        assertEquals(1, answers.get(), "a prompt opened after reload must still receive chat answers");
    }

    private static void askRename(Plugin plugin, Player player,
                                  Consumer<String> onInput, Runnable onCancel) throws Exception {
        Method method = GuiChatPrompt.class.getDeclaredMethod("askRename", Plugin.class, Player.class,
                Consumer.class, Runnable.class);
        method.invoke(null, plugin, player, onInput, onCancel);
    }

    @Test
    void askRenameSendsExactlyThePrivateInstruction() throws Exception {
        PluginMock plugin = MockBukkit.createMockPlugin();
        PlayerMock player = server.addPlayer();
        PlayerMock viewer = server.addPlayer();

        askRename(plugin, player, text -> {}, () -> {});

        assertEquals(INSTRUCTION + "\n", drain(player),
                "the dedicated rename entry point must send exactly one private instruction line");
        assertNull(viewer.nextComponentMessage(), "the instruction must not be broadcast");
        assertTrue(GuiChatPrompt.isWaiting(player.getUniqueId()));
    }

    @Test
    void fallbackSuppressesBothEventsWithNoEchoOrBroadcast() {
        PluginMock plugin = MockBukkit.createMockPlugin();
        PlayerMock owner = server.addPlayer();
        PlayerMock viewer = server.addPlayer();
        AtomicReference<String> answer = new AtomicReference<>();
        AtomicInteger cancels = new AtomicInteger();
        ask(plugin, owner, answer, cancels);
        drain(owner);
        drain(viewer);

        ChatGateway chat = new ChatGateway(owner, SENTINEL, Set.of(viewer, owner));
        chat.dispatch();

        assertTrue(chat.paper.isCancelled(), "Paper chat must be cancelled for a pending player");
        assertTrue(chat.paper.viewers().isEmpty(), "Paper viewers must be cleared");
        assertTrue(chat.legacy.isCancelled(), "legacy chat must be cancelled for a pending player");
        assertTrue(chat.legacy.getRecipients().isEmpty(), "legacy recipients must be cleared");
        assertNull(owner.nextComponentMessage(), "the answer must not be echoed to its sender");
        assertNull(viewer.nextComponentMessage(), "the answer must not reach another player");

        server.getScheduler().performTicks(2);
        assertEquals(SENTINEL, answer.get());
        assertEquals(0, cancels.get());
        assertNull(viewer.nextComponentMessage(), "no chat may appear after the callback either");
    }

    @Test
    void bothEventsDeliverExactlyOneCallbackAndLateInputIsIgnored() {
        PluginMock plugin = MockBukkit.createMockPlugin();
        PlayerMock player = server.addPlayer();
        AtomicReference<String> answer = new AtomicReference<>();
        AtomicInteger cancels = new AtomicInteger();
        ask(plugin, player, answer, cancels);
        drain(player);

        new ChatGateway(player, SENTINEL, Set.of()).dispatch();
        server.getScheduler().performTicks(2);
        assertEquals(SENTINEL, answer.get());

        ChatGateway late = new ChatGateway(player, "LATE_INPUT", Set.of());
        late.dispatch();
        server.getScheduler().performTicks(2);

        assertEquals(SENTINEL, answer.get(), "late input must never be staged");
        assertEquals(0, cancels.get());
        assertFalse(late.legacy.isCancelled(), "ordinary chat after the prompt is complete is unaffected");
    }

    @Test
    void callbackRunsOnPrimaryThreadOnly() {
        PluginMock plugin = MockBukkit.createMockPlugin();
        PlayerMock player = server.addPlayer();
        AtomicReference<String> answer = new AtomicReference<>();
        ask(plugin, player, answer, new AtomicInteger());
        drain(player);

        new ChatGateway(player, SENTINEL, Set.of()).dispatch();
        assertNull(answer.get(), "chat callbacks must be scheduled, not run on the chat thread");

        server.getScheduler().performTicks(1);
        assertEquals(SENTINEL, answer.get());
    }

    @Test
    void timeoutCancelsOnceWithPrivateFeedbackOnly() {
        PluginMock plugin = MockBukkit.createMockPlugin();
        PlayerMock owner = server.addPlayer();
        PlayerMock viewer = server.addPlayer();
        AtomicReference<String> answer = new AtomicReference<>();
        AtomicInteger cancels = new AtomicInteger();
        ask(plugin, owner, answer, cancels);
        drain(owner);

        server.getScheduler().performTicks(20L * 60 - 1);
        assertEquals(0, cancels.get(), "timeout must not fire early");

        server.getScheduler().performTicks(3);
        assertEquals(1, cancels.get(), "timeout must call onCancel exactly once");
        assertNull(answer.get(), "timeout must never stage a value");
        assertTrue(drain(owner).contains("Timed out, nothing was changed."),
                "timeout feedback must be private to the owner");
        assertNull(viewer.nextComponentMessage(), "timeout must not be broadcast");

        server.getScheduler().performTicks(20L * 60);
        assertEquals(1, cancels.get(), "a fired timeout must not fire again");
    }

    @Test
    void clearUnregistersListenersAndInvalidatesTheOldOwner() {
        PluginMock plugin = MockBukkit.createMockPlugin();
        PlayerMock player = server.addPlayer();
        AtomicInteger answers = new AtomicInteger();
        ask(plugin, player, new AtomicReference<>(), answers);
        drain(player);

        GuiChatPrompt.clear();
        Bukkit.getPluginManager().disablePlugin(plugin);
        Bukkit.getPluginManager().enablePlugin(plugin);
        new ChatGateway(player, SENTINEL, Set.of()).dispatch();
        server.getScheduler().performTicks(2);

        assertEquals(0, answers.get(), "cleared prompts must not accept late input");
        assertFalse(GuiChatPrompt.isWaiting(player.getUniqueId()));
        for (var registered : HandlerList.getRegisteredListeners(plugin)) {
            assertFalse(registered.getListener() instanceof GuiChatPrompt,
                    "clear() must unregister Bukkit listeners");
        }
    }

    @Test
    void disconnectDropsPendingWithoutGuiCallback() {
        PluginMock plugin = MockBukkit.createMockPlugin();
        PlayerMock player = server.addPlayer();
        AtomicInteger answers = new AtomicInteger();
        AtomicInteger cancels = new AtomicInteger();
        ask(plugin, player, new AtomicReference<>(), answers);
        drain(player);

        Bukkit.getPluginManager().callEvent(new PlayerQuitEvent(player, Component.empty()));
        new ChatGateway(player, SENTINEL, Set.of()).dispatch();
        server.getScheduler().performTicks(20L * 60 + 2);

        assertEquals(0, answers.get(), "disconnect must drop the prompt without staging a value");
        assertEquals(0, cancels.get(), "disconnect must cancel the timeout as well");
        assertFalse(GuiChatPrompt.isWaiting(player.getUniqueId()));
    }

    @Test
    void secondAskInvalidatesTheFirst() {
        PluginMock plugin = MockBukkit.createMockPlugin();
        PlayerMock player = server.addPlayer();
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        GuiChatPrompt.ask(plugin, player, GENERIC_INSTRUCTION,
                text -> first.incrementAndGet(), first::incrementAndGet);
        drain(player);
        GuiChatPrompt.ask(plugin, player, GENERIC_INSTRUCTION,
                text -> second.incrementAndGet(), second::incrementAndGet);
        drain(player);
        assertTrue(GuiChatPrompt.isWaiting(player.getUniqueId()), "second prompt must be pending");

        new ChatGateway(player, SENTINEL, Set.of()).dispatch();
        server.getScheduler().performTicks(2);

        assertEquals(0, first.get(), "a replaced prompt must never call back");
        assertEquals(1, second.get());
    }

    @Test
    void slashCommandsAreNotSwallowedAsPromptInput() {
        PluginMock plugin = MockBukkit.createMockPlugin();
        PlayerMock player = server.addPlayer();
        AtomicReference<String> answer = new AtomicReference<>();
        ask(plugin, player, answer, new AtomicInteger());
        drain(player);

        ChatGateway chat = new ChatGateway(player, "/pvpcore reload", Set.of());
        chat.dispatch();
        server.getScheduler().performTicks(2);

        assertFalse(chat.paper.isCancelled(), "commands must bypass prompt interception");
        assertFalse(chat.legacy.isCancelled(), "commands must bypass prompt interception");
        assertNull(answer.get());
        assertTrue(GuiChatPrompt.isWaiting(player.getUniqueId()),
                "a command must not consume the pending prompt");
    }

    @Test
    void ordinaryChatIsUntouchedWithoutAPendingPrompt() {
        PluginMock plugin = MockBukkit.createMockPlugin();
        PlayerMock player = server.addPlayer();
        PlayerMock viewer = server.addPlayer();
        GuiChatPrompt.ask(plugin, player, GENERIC_INSTRUCTION, text -> {}, () -> {});
        drain(player);
        GuiChatPrompt.clear();

        ChatGateway chat = new ChatGateway(player, SENTINEL, Set.of(viewer));
        chat.dispatch();

        assertFalse(chat.paper.isCancelled());
        assertTrue(chat.paper.viewers().contains(viewer));
        assertFalse(chat.legacy.isCancelled());
        assertTrue(chat.legacy.getRecipients().contains(viewer));
        assertNull(answerText(viewer), "ordinary chat is delivered normally");
    }

    private static String answerText(PlayerMock player) {
        Component message = player.nextComponentMessage();
        return message == null ? null : PlainTextComponentSerializer.plainText().serialize(message);
    }

    @Test
    void disabledOwnerReceivesNoCallback() {
        PluginMock plugin = MockBukkit.createMockPlugin();
        PlayerMock player = server.addPlayer();
        AtomicInteger answers = new AtomicInteger();
        ask(plugin, player, new AtomicReference<>(), answers);
        drain(player);

        Bukkit.getPluginManager().disablePlugin(plugin);
        new ChatGateway(player, SENTINEL, Set.of()).dispatch();
        server.getScheduler().performTicks(2);

        assertEquals(0, answers.get(), "a disabled plugin must never receive a chat callback");
    }

    @Test
    void fallbackHandlersOnlyUseLowestPriority() {
        PluginMock plugin = MockBukkit.createMockPlugin();
        Player player = server.addPlayer();
        GuiChatPrompt.ask(plugin, player, GENERIC_INSTRUCTION, text -> {}, () -> {});

        int lowest = 0;
        for (var registered : HandlerList.getRegisteredListeners(plugin)) {
            Listener candidate = registered.getListener();
            if (candidate instanceof GuiChatPrompt) {
                assertTrue(registered.getPriority().getSlot() < EventPriority.MONITOR.getSlot(),
                        "chat interception must run before MONITOR handlers");
                if (registered.getPriority() == EventPriority.LOWEST) {
                    lowest++;
                }
            }
        }
        assertTrue(lowest >= 2, "both Paper and legacy fallback handlers must be LOWEST");
    }

    @Test
    void packetPathFallbackHandlersAreNoOpsAndQuitDropsPending() throws Exception {
        PluginMock plugin = MockBukkit.createMockPlugin();
        PlayerMock player = server.addPlayer();
        PlayerMock viewer = server.addPlayer();
        AtomicReference<String> answer = new AtomicReference<>();
        AtomicInteger cancels = new AtomicInteger();
        ask(plugin, player, answer, cancels);
        drain(player);

        GuiChatPrompt.packetPathForTest(true);
        assertTrue(GuiChatPrompt.submitFromPacket(player.getUniqueId(), "FIRST"),
                "the packet entry point must still consume a prompt");
        server.getScheduler().performTicks(2);
        assertEquals("FIRST", answer.get());
        answer.set(null);

        ask(plugin, player, answer, cancels);
        drain(player);
        ChatGateway chat = new ChatGateway(player, SENTINEL, Set.of(viewer));
        chat.dispatch();
        assertFalse(chat.paper.isCancelled(), "fallback chat handler must be a no-op on the PE path");
        assertFalse(chat.legacy.isCancelled(), "fallback legacy handler must be a no-op on the PE path");
        assertNull(answer.get(), "no fallback callback while PE is active");

        Bukkit.getPluginManager().callEvent(new PlayerQuitEvent(player, Component.empty()));
        assertFalse(GuiChatPrompt.isWaiting(player.getUniqueId()), "quit must drop a PE-path prompt");
        assertFalse(GuiChatPrompt.submitFromPacket(player.getUniqueId(), SENTINEL),
                "late packet input must be dropped");
        server.getScheduler().performTicks(GuiChatPrompt.TIMEOUT_TICKS + 2);
        assertNull(answer.get(), "quit must never stage a value");
        assertEquals(0, cancels.get(), "quit must cancel the timeout");
        GuiChatPrompt.packetPathForTest(false);
    }

    @Test
    void firstPromptUsesItsOwnOwnerAfterALaterAskChangesOwner() {
        PluginMock firstOwner = MockBukkit.createMockPlugin();
        PluginMock secondOwner = MockBukkit.createMockPlugin();
        PlayerMock firstPlayer = server.addPlayer();
        PlayerMock secondPlayer = server.addPlayer();
        PlayerMock viewer = server.addPlayer();
        AtomicReference<String> firstAnswer = new AtomicReference<>();
        AtomicReference<String> secondAnswer = new AtomicReference<>();

        GuiChatPrompt.ask(firstOwner, firstPlayer, "first", firstAnswer::set, () -> {});
        GuiChatPrompt.ask(secondOwner, secondPlayer, "second", secondAnswer::set, () -> {});
        drain(firstPlayer);
        drain(secondPlayer);

        ChatGateway chat = new ChatGateway(firstPlayer, SENTINEL, Set.of(viewer));
        chat.dispatch();

        assertTrue(chat.paper.isCancelled(), "the first prompt's text must be suppressed");
        assertTrue(chat.legacy.isCancelled(), "the first prompt's text must be suppressed on both events");
        assertNull(viewer.nextComponentMessage(), "the first prompt's text must not broadcast");
        assertNull(firstPlayer.nextComponentMessage(), "the first prompt's text must not echo");
        server.getScheduler().performTicks(2);
        assertEquals(SENTINEL, firstAnswer.get(), "the callback must be scheduled with pending.owner()");
        assertNull(secondAnswer.get(), "the second prompt must not receive the first answer");
    }

    @Test
    void clearInvalidatesAnAlreadyScheduledCallback() {
        PluginMock plugin = MockBukkit.createMockPlugin();
        PlayerMock player = server.addPlayer();
        AtomicInteger answers = new AtomicInteger();
        AtomicInteger cancels = new AtomicInteger();
        GuiChatPrompt.ask(plugin, player, GENERIC_INSTRUCTION,
                text -> answers.incrementAndGet(), cancels::incrementAndGet);
        drain(player);

        new ChatGateway(player, SENTINEL, Set.of()).dispatch();
        GuiChatPrompt.clear();
        server.getScheduler().performTicks(2);

        assertEquals(0, answers.get(), "clear() must invalidate the scheduled callback by pending identity");
        assertEquals(0, cancels.get());
        assertFalse(GuiChatPrompt.isWaiting(player.getUniqueId()));
    }

    @Test
    void duplicateInputBeforeSchedulerTicksIsSuppressedOnce() {
        PluginMock plugin = MockBukkit.createMockPlugin();
        PlayerMock player = server.addPlayer();
        PlayerMock viewer = server.addPlayer();
        AtomicReference<String> answer = new AtomicReference<>();
        AtomicInteger cancels = new AtomicInteger();
        ask(plugin, player, answer, cancels);
        drain(player);

        ChatGateway first = new ChatGateway(player, SENTINEL, Set.of(viewer));
        first.dispatch();
        ChatGateway duplicate = new ChatGateway(player, "DUPLICATE_INPUT", Set.of(viewer));
        duplicate.dispatch();

        assertTrue(first.paper.isCancelled());
        assertTrue(duplicate.paper.isCancelled(), "a consumed prompt suppresses duplicates until the task runs");
        assertTrue(duplicate.legacy.isCancelled());
        server.getScheduler().performTicks(2);
        assertEquals(SENTINEL, answer.get(), "exactly one callback must stage the first answer");
        assertNull(viewer.nextComponentMessage(), "no duplicate may be broadcast");
        assertEquals(0, cancels.get());
    }
}
