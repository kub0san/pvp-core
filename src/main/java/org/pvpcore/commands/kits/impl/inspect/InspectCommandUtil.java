package org.pvpcore.commands.kits.impl.inspect;

import org.pvpcore.util.kits.BroadcastManager;
import org.pvpcore.util.kits.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class InspectCommandUtil {
    public static final MiniMessage mm = MiniMessage.miniMessage();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private InspectCommandUtil() {
    }

    public static Component errorPrefix() {
        return org.pvpcore.commands.kits.KitsCommands.lang().component("error.prefix-tag");
    }

    public static CompletableFuture<@Nullable UUID> resolvePlayerIdentifierAsync(String identifier) {
        try {
            UUID uuid = UUID.fromString(identifier);
            return CompletableFuture.completedFuture(uuid);
        } catch (IllegalArgumentException ignored) {
        }

        Player onlinePlayer = Bukkit.getPlayerExact(identifier);
        if (onlinePlayer != null) {
            return CompletableFuture.completedFuture(onlinePlayer.getUniqueId());
        }

        return CompletableFuture.supplyAsync(() -> {
            UUID cachedOfflinePlayer = findCachedOfflinePlayerUuid(identifier);
            return selectResolvedUuid(identifier, cachedOfflinePlayer,
                    () -> lookupPlayerUuidFromMojang(identifier));
        });
    }

    public static void showUsage(@NotNull Player player, @NotNull String commandName) {
        (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().broadcastManager() : null).sendComponentMessage(player,
                errorPrefix().append(org.pvpcore.commands.kits.KitsCommands.lang().component("command.inspect-usage", "command", commandName)));
    }

    private static @Nullable UUID findCachedOfflinePlayerUuid(@NotNull String identifier) {
        OfflinePlayer cachedOfflinePlayer = getOfflinePlayerIfCached(identifier);
        if (cachedOfflinePlayer == null) {
            return null;
        }

        String cachedName = cachedOfflinePlayer.getName();
        if (cachedName == null || !cachedName.equalsIgnoreCase(identifier)) {
            return null;
        }

        return cachedOfflinePlayer.getUniqueId();
    }

    private static @Nullable OfflinePlayer getOfflinePlayerIfCached(@NotNull String identifier) {
        Object server = Bukkit.getServer();
        if (server == null) {
            return null;
        }

        try {
            Method method = server.getClass().getMethod("getOfflinePlayerIfCached", String.class);
            Object offlinePlayer = method.invoke(server, identifier);
            return offlinePlayer instanceof OfflinePlayer ? (OfflinePlayer) offlinePlayer : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static @Nullable UUID lookupPlayerUuidFromMojang(@NotNull String identifier) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + identifier))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2 || response.body() == null) {
                return null;
            }

            String body = response.body();
            int idStart = body.indexOf("\"id\":\"");
            if (idStart < 0) {
                return null;
            }

            idStart += 6;
            int idEnd = body.indexOf("\"", idStart);
            if (idEnd <= idStart) {
                return null;
            }

            String raw = body.substring(idStart, idEnd);
            if (raw.length() != 32) return null;
            String formatted = raw.substring(0, 8) + "-"
                    + raw.substring(8, 12) + "-"
                    + raw.substring(12, 16) + "-"
                    + raw.substring(16, 20) + "-"
                    + raw.substring(20);
            return UUID.fromString(formatted);
        } catch (Exception ignored) {
            return null;
        }
    }

    static @Nullable UUID selectResolvedUuid(@NotNull String identifier, @Nullable UUID cachedOfflinePlayer,
                                             @NotNull Supplier<UUID> mojangLookup) {
        if (cachedOfflinePlayer != null) {
            return cachedOfflinePlayer;
        }

        UUID mojangUuid = mojangLookup.get();
        if (mojangUuid != null) {
            return mojangUuid;
        }

        return null;
    }
}
