package org.pvpcore.commands.kits.impl.admin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.jetbrains.annotations.NotNull;
import org.pvpcore.util.kits.Lang;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AboutCommandListener implements Listener, CommandExecutor {

    private final Properties buildProperties = new Properties();

    public AboutCommandListener() {
        try (InputStream input = getClass().getResourceAsStream("/build.properties")) {
            if (input != null) {
                buildProperties.load(input);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void sendAboutMessage(CommandSender sender) {
        String author = "Noah Ross";
        String source = "https://github.com/rossnoah/Kits";
        String license = "AGPL-3.0";

        String buildTimestamp = buildProperties.getProperty("build.timestamp", "Unknown");
        String pluginVersion = buildProperties.getProperty("plugin.version", "Unknown");

        org.pvpcore.commands.kits.KitsCommands.lang().sendNoPrefix(sender, "about.header-start");
        org.pvpcore.commands.kits.KitsCommands.lang().sendNoPrefix(sender, "about.title");
        org.pvpcore.commands.kits.KitsCommands.lang().sendNoPrefix(sender, "about.author", "author", author);
        org.pvpcore.commands.kits.KitsCommands.lang().sendNoPrefix(sender, "about.license", "license", license);
        org.pvpcore.commands.kits.KitsCommands.lang().sendNoPrefix(sender, "about.source-code", "source", source);
        org.pvpcore.commands.kits.KitsCommands.lang().sendNoPrefix(sender, "about.version", "version", pluginVersion);
        org.pvpcore.commands.kits.KitsCommands.lang().sendNoPrefix(sender, "about.build-time", "time", buildTimestamp);
        org.pvpcore.commands.kits.KitsCommands.lang().sendNoPrefix(sender, "about.header-end");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (org.pvpcore.commands.kits.KitsCommands.disabled(sender)) {
            return true;
        }

        sendAboutMessage(sender);
        return true;
    }

    @EventHandler
    public void onPreCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message.equalsIgnoreCase("/aboutperplayerkit")) {
            event.setCancelled(true);
            sendAboutMessage(event.getPlayer());
        }
    }
}