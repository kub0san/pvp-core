package org.pvpcore.mechanic;

/**
 * Observable status of a mechanic, including why it is in that state.
 */
public record MechanicStatus(State state, String reason) {

    public enum State {
        READY,
        DISABLED_BY_CONFIG,
        MISSING_DEPENDENCY,
        UNSUPPORTED_VERSION
    }

    public MechanicStatus {
        if (state == null) throw new IllegalArgumentException("state");
        reason = reason == null ? "" : reason;
    }

    public static MechanicStatus ready(String reason) {
        return new MechanicStatus(State.READY, reason == null || reason.isBlank() ? "Ready" : reason);
    }

    public static MechanicStatus disabledByConfig(String reason) {
        return new MechanicStatus(State.DISABLED_BY_CONFIG, reason == null || reason.isBlank() ? "Disabled by config" : reason);
    }

    public static MechanicStatus missingDependency(String reason) {
        return new MechanicStatus(State.MISSING_DEPENDENCY, reason == null || reason.isBlank() ? "Missing dependency" : reason);
    }

    public static MechanicStatus unsupportedVersion(String reason) {
        return new MechanicStatus(State.UNSUPPORTED_VERSION, reason == null || reason.isBlank() ? "Unsupported version" : reason);
    }
}
