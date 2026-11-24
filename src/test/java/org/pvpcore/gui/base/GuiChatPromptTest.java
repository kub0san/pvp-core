package org.pvpcore.gui.base;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuiChatPromptTest {
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
        Bukkit.getPluginManager().callEvent(new AsyncChatEvent(false, player, Set.of(),
                ChatRenderer.defaultRenderer(), Component.text("answer"), player.name(), null));
        server.getScheduler().performTicks(2);

        assertEquals(1, answers.get(), "a prompt opened after reload must still receive chat answers");
    }
}
