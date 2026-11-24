package org.pvpcore.features.combat;

/** Why a combat session was removed. */
public enum CombatEndReason {
    EXPIRED,
    DEATH,
    LOGOUT,
    CLEARED,
    PLUGIN_DISABLE
}
