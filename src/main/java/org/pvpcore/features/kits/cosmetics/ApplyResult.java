package org.pvpcore.features.kits.cosmetics;

/** Outcome of a cosmetic Apply. QUEUED means the write is pending, not saved. */
public enum ApplyResult {
    PERMISSION_DENIED,
    KIT_CHANGED,
    INVALID_CONTENT,
    STORAGE_FAILED,
    QUEUED
}
