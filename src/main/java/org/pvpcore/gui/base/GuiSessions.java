package org.pvpcore.gui.base;

import org.bukkit.plugin.Plugin;

/**
 * The one shared {@link GuiSession}. The kits GUI facade registers itself here
 * the first time it is constructed (and unregisters on shutdown), so any screen
 * that wants the shared flush behaviour -- including screens constructed before
 * the GUI exists -- can reach it without importing kits.
 */
public final class GuiSessions {

    private static GuiSession current;

    private GuiSessions() {
    }

    /**
     * The shared session.
     *
     * @throws IllegalStateException if no session has been registered yet
     */
    public static synchronized GuiSession current() {
        GuiSession session = current;
        if (session == null) {
            throw new IllegalStateException(
                    "No GUI session is registered yet. Something opened a screen before the GUI was created.");
        }
        return session;
    }

    /** The shared session if registered, otherwise a minimal plugin-only session. */
    public static synchronized GuiSession currentOr(Plugin fallback) {
        return current != null ? current : GuiSession.ofPlugin(fallback);
    }

    public static synchronized void set(GuiSession session) {
        current = session;
    }

    public static synchronized void clear() {
        current = null;
    }
}
