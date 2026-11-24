package org.pvpcore.features.kits;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** Shares asynchronous lookups between startup and joins; player messages run on the server thread. */
public final class UpdateChecker implements AutoCloseable {
    private static final String UPDATE_URL = "https://hangar.papermc.io/api/v1/projects/Kits/latestrelease";
    private static final long SUCCESS_CACHE_NANOS = TimeUnit.HOURS.toNanos(1);
    private static final long FAILURE_CACHE_NANOS = TimeUnit.MINUTES.toNanos(1);
    private static final Pattern RELEASE_VERSION = Pattern.compile("\\d+(?:\\.\\d+)*");
    private static final Pattern CURRENT_VERSION = Pattern.compile("(\\d+(?:\\.\\d+)*)([-+].+)?");

    private final Plugin plugin;
    private final String currentVersion;
    private final HttpClient client;
    private final LongSupplier clock;
    private volatile boolean closed;

    private CompletableFuture<Optional<Release>> pending;
    private Release cached;
    private long checkedAt;
    private long cacheDuration;

    private record Release(String version, boolean newer) {}

    public UpdateChecker(Plugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getPluginMeta().getVersion();
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.clock = System::nanoTime;
    }

    public boolean checkForUpdate() {
        return latestRelease().getNow(Optional.empty()).map(Release::newer).orElse(false);
    }

    public CompletableFuture<Boolean> checkForUpdateAsync() {
        return latestRelease().thenApply(result -> result.map(Release::newer).orElse(false));
    }

    private synchronized CompletableFuture<Optional<Release>> latestRelease() {
        if (closed) return CompletableFuture.completedFuture(Optional.empty());
        if (pending != null) return pending;
        if (cacheDuration > 0 && clock.getAsLong() - checkedAt < cacheDuration)
            return CompletableFuture.completedFuture(Optional.ofNullable(cached));

        CompletableFuture<Optional<Release>> result = new CompletableFuture<>();
        pending = result;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(UPDATE_URL))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    Release release = null;
                    if (response.statusCode() == 200) {
                        String version = response.body().trim();
                        if (RELEASE_VERSION.matcher(version).matches()) {
                            release = new Release(version, isSemanticallyNewer(currentVersion, version));
                        }
                    }
                    finish(release);
                })
                .exceptionally(ex -> {
                    finish(null);
                    return null;
                });

        return result;
    }

    private void finish(Release release) {
        CompletableFuture<Optional<Release>> result;
        synchronized (this) {
            if (closed) return;
            result = pending;
            pending = null;
            cached = release;
            checkedAt = clock.getAsLong();
            cacheDuration = release == null ? FAILURE_CACHE_NANOS : SUCCESS_CACHE_NANOS;
        }
        result.complete(Optional.ofNullable(release));
    }

    public void printStartupStatus() {
        // Disabled because we are running PvPCore, not Kits.
        // The Hangar URL checks for "Kits" which returns a 404.
    }

    public void sendUpdateMessage(Player player) {
        // Disabled for PvPCore
    }

    @Override public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
            cached = null;
        }
        if (pending != null) {
            pending.complete(Optional.empty());
            pending = null;
        }
    }

    static boolean isSemanticallyNewer(String current, String latest) {
        var installed = CURRENT_VERSION.matcher(current.trim());
        if (!installed.matches() || !RELEASE_VERSION.matcher(latest).matches())
            throw new IllegalArgumentException("Cannot compare the installed version with the published release");
        String[] left = installed.group(1).split("\\."), right = latest.split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            java.math.BigInteger a = i < left.length ? new java.math.BigInteger(left[i]) : java.math.BigInteger.ZERO;
            java.math.BigInteger b = i < right.length ? new java.math.BigInteger(right[i]) : java.math.BigInteger.ZERO;
            int comparison = b.compareTo(a);
            if (comparison != 0) return comparison > 0;
        }
        return installed.group(2) != null && installed.group(2).startsWith("-");
    }
}