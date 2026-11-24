package org.pvpcore.gui.base;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.pvpcore.features.kits.KitsContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the foundation needs from its owner.
 *
 * <p>The screen foundation never knows about kits or RTP; it knows a scheduler
 * ({@link #plugin()}) and one hook to flush editor state before replacing a
 * screen ({@link #flushOpenEditor(Player)}). The kits GUI facade implements
 * this with the real editor-context flushing; screens without editor state
 * get a plain plugin session from {@link #ofPlugin(Plugin)}.</p>
 */
public interface GuiSession {
    Plugin plugin();

    /** Saves whatever editor state the player has open. No-op when there is none. */
    default void flushOpenEditor(Player player) {
    }

    /** Kits context for kits-backed screens, or {@code null} for non-kits sessions. */
    default KitsContext kits() {
        return null;
    }

    /**
     * Per-player staged settings for the settings GUI. Stored on the session so it
     * persists across screen reopens within the same session and stays isolated per player.
     */
    default Map<UUID, Map<String, GuiSettingsSupport.Staged>> stagedSettingsStore() {
        return NoOpStagedStore.INSTANCE;
    }

    /** A session with only a plugin: nothing to flush. */
    static GuiSession ofPlugin(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin must not be null");
        }
        return () -> plugin;
    }

    /** No-op staged store for sessions that do not support settings staging. */
    final class NoOpStagedStore {
        static final Map<UUID, Map<String, GuiSettingsSupport.Staged>> INSTANCE = new ConcurrentHashMap<>();
        private NoOpStagedStore() {
        }
    }
}
