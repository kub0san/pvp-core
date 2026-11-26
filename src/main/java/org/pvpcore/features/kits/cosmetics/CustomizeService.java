package org.pvpcore.features.kits.cosmetics;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.pvpcore.commands.kits.core.ActionGuards;
import org.pvpcore.config.kits.CustomizeRules;
import org.pvpcore.features.kits.KitManager;
import org.pvpcore.features.kits.KitSaveResult;
import org.pvpcore.features.kits.Kits;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.util.kits.LocationFeature;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the Apply decision. Every external effect goes through a seam so the ordering (guard,
 * reload/stale check, snapshot check, content validation, save) can be tested without Bukkit
 * scheduling or storage. Production seams re-resolve the live KitsContext on every call; a
 * screen never hands this service a cached KitManager.
 */
public final class CustomizeService {

    /** Queues the full working kit through the explicit async save helper. */
    @FunctionalInterface
    public interface KitSaver {
        KitSaveResult save(Player player, int slot, ItemStack[] kit);
    }

    /** Rechecks permission and location at Apply time, not when the screen opened. */
    @FunctionalInterface
    public interface GuardChecker {
        boolean allowed(Player player);
    }

    /** Reads a fresh owned kit array for stale detection. */
    @FunctionalInterface
    public interface CurrentKitReader {
        ItemStack[] read(Player player, int slot);
    }

    /** True when the viewer's screen was invalidated by a reload. */
    @FunctionalInterface
    public interface StaleChecker {
        boolean isStale(Player player);
    }

    private final CustomizeRules rules;
    private final KitSaver saver;
    private final GuardChecker guard;
    private final CurrentKitReader reader;
    private final StaleChecker stale;
    private final Logger logger = Logger.getLogger(CustomizeService.class.getName());

    /** Test seam constructor: no reload invalidation unless a stale checker is supplied. */
    CustomizeService(CustomizeRules rules, KitSaver saver, GuardChecker guard, CurrentKitReader reader) {
        this(rules, saver, guard, reader, player -> false);
    }

    CustomizeService(CustomizeRules rules, KitSaver saver, GuardChecker guard, CurrentKitReader reader,
                     StaleChecker stale) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.saver = Objects.requireNonNull(saver, "saver");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.reader = Objects.requireNonNull(reader, "reader");
        this.stale = Objects.requireNonNull(stale, "stale");
    }

    /** Production wiring; call at Apply time so every dependency reflects the live context. */
    public static CustomizeService create(GUI gui) {
        return new CustomizeService(
                liveRules(),
                (player, slot, kit) -> {
                    KitManager manager = liveManager();
                    UUID viewer = player == null ? null : player.getUniqueId();
                    if (manager == null || viewer == null) return KitSaveResult.rejected();
                    return manager.savePersonalKitAsync(viewer, slot, kit);
                },
                player -> player != null && ActionGuards.allowed(player, "perplayerkit.kit", LocationFeature.KITS),
                (player, slot) -> {
                    KitManager manager = liveManager();
                    if (manager == null || player == null) return null;
                    return manager.getPlayerKit(player.getUniqueId(), slot);
                },
                player -> gui != null && player != null && gui.isCustomizeStale(player.getUniqueId()));
    }

    /** Applies a staged session, returning QUEUED only after the write has been accepted. */
    public ApplyResult commit(Player player, int slot, CustomizeSession session) {
        return commit(player, slot, session, outcome -> {});
    }

    /**
     * Same as {@link #commit(Player, int, CustomizeSession)} plus an outcome callback invoked once
     * the queued write resolves. Callers can use it to consume a pending session on success or
     * reopen the screen on failure; it never cancels or otherwise touches the write.
     */
    public ApplyResult commit(Player player, int slot, CustomizeSession session, Consumer<Boolean> outcome) {
        if (session == null) return ApplyResult.INVALID_CONTENT;
        if (!guard.allowed(player)) return ApplyResult.PERMISSION_DENIED;
        if (stale.isStale(player)) return ApplyResult.KIT_CHANGED;
        if (!session.matchesCurrent(reader.read(player, slot))) return ApplyResult.KIT_CHANGED;

        ItemStack[] working = session.working();
        if (!validContent(working)) return ApplyResult.INVALID_CONTENT;

        KitSaveResult saved = saver.save(player, slot, working);
        if (saved == null || !saved.accepted()) return ApplyResult.STORAGE_FAILED;

        observeWrite(player, saved.write(), outcome);
        return ApplyResult.QUEUED;
    }

    private boolean validContent(ItemStack[] kit) {
        // Kit contents are opaque here: no cosmetic validation may rewrite a saved item's
        // metadata (in particular, shield banner layers must survive a save untouched).
        return rules.enabled() && kit != null && kit.length > 0;
    }

    private void observeWrite(Player player, CompletableFuture<Void> write, Consumer<Boolean> outcome) {
        if (write == null) return;
        UUID viewer = player == null ? null : player.getUniqueId();
        write.whenComplete((unused, error) -> {
            if (error == null) {
                logger.info(() -> "Kit cosmetics saved for " + viewer);
            } else {
                logger.log(Level.WARNING, "Kit cosmetics save failed for " + viewer, error);
            }
            if (outcome != null) {
                try {
                    outcome.accept(error == null);
                } catch (RuntimeException e) {
                    logger.log(Level.WARNING, "Kit cosmetics completion callback failed for " + viewer, e);
                }
            }
        });
    }

    // ------------------------------------------------------------------ live seam helpers

    /** Resolves the manager held by the current context; reload replaces it, this never caches it. */
    private static KitManager liveManager() {
        KitsContext context = Kits.context();
        return context == null ? null : context.kitManager();
    }

    private static CustomizeRules liveRules() {
        KitsContext context = Kits.context();
        return CustomizeRules.parse(context == null || context.config() == null
                ? null : context.config().getConfigurationSection("customize"));
    }
}
