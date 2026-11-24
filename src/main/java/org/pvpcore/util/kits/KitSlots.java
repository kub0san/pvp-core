/*
 * Copyright 2022-2026 Noah Ross
 *
 * This file is part of Kits.
 *
 * Kits is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Kits is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Kits. If not, see <https://www.gnu.org/licenses/>.
 */
package org.pvpcore.util.kits;

import com.google.common.primitives.Ints;
import org.bukkit.plugin.Plugin;

/**
 * Holds the configured number of kit slots each player has ({@code max-kits}).
 * The limit applies to kits and enderchest kits alike. Static (not a
 * {@code get()} singleton) so code paths under unit test keep working without
 * a Bukkit server, falling back to the default of 9.
 */
public final class KitSlots {

    public static final int MIN_LIMIT = 1; // why: public slot bound constant retained for external/legacy compatibility.
    public static final int MAX_LIMIT = 99; // why: public slot bound constant retained for external/legacy compatibility.
    public static final int SLOTS_PER_PAGE = 9; // why: public UI/pagination constant retained for external/legacy compatibility.
    public static final int DEFAULT_LIMIT = 9; // why: public default slot constant retained for external/legacy compatibility.

    private static int maxKits = DEFAULT_LIMIT; // why: static configured slot count retained for legacy slot utility compatibility.

    private KitSlots() {
    }

    /** why: static slot-config initialization retained for module enable/reload lifecycle. */
    public static void init(Plugin plugin, org.bukkit.configuration.file.FileConfiguration config) {
        int configured = config == null ? DEFAULT_LIMIT : config.getInt("kits.max-slots", DEFAULT_LIMIT);
        int clamped = Ints.constrainToRange(configured, MIN_LIMIT, MAX_LIMIT);
        if (clamped != configured && plugin != null) {
            plugin.getLogger().warning("max-kits is set to " + configured + " but must be between "
                    + MIN_LIMIT + " and " + MAX_LIMIT + ", using " + clamped);
        }
        maxKits = clamped;
    }

    /** why: legacy static slot-count accessor retained for external/legacy compatibility; no internal call sites. */
    @Deprecated
    public static int maxKits() {
        return maxKits;
    }

    /** why: legacy static page-count helper retained for external/legacy compatibility; no internal call sites. */
    @Deprecated
    public static int pageCount() {
        return (maxKits + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE;
    }

    /** why: legacy static page helper retained for external/legacy compatibility; no internal call sites. */
    @Deprecated
    public static int pageOf(int slot) {
        return (slot - 1) / SLOTS_PER_PAGE;
    }

    /**
     * Parses a slot suffix as it appears in command labels ("k12") and storage
     * IDs ("&lt;uuid&gt;12"): digits only, no leading zero, within the absolute
     * 1..{@link #MAX_LIMIT} range. Returns null if the suffix is not a valid
     * slot number. Deliberately not bounded by the configured max-kits — the
     * database may hold slots above a lowered limit; callers enforce the
     * configured bound where it applies.
     * <p>why: stateless slot-format parser utility (not a kits accessor shim).</p>
     */
    public static Integer parseSlotSuffix(String suffix) {
        if (suffix == null || suffix.isEmpty() || suffix.charAt(0) == '0') {
            return null;
        }
        Integer slot = Ints.tryParse(suffix);
        if (slot == null || slot < MIN_LIMIT || slot > MAX_LIMIT) {
            return null;
        }
        return slot;
    }


}
