package org.pvpcore.features.teleport.rtp;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Aggregated counters for search debugging: where candidate work went. Main thread only.
 */
final class RtpSearchStats {

    enum Reject {
        OUT_OF_BOUNDS,
        BAD_CHUNK,
        NO_SURFACE,
        UNSAFE_BLOCK,
        BLOCKED_BIOME,
        VERIFIER_REJECTED,
        TIMEOUT
    }

    private final EnumMap<Reject, Integer> rejects = new EnumMap<>(Reject.class);
    private int candidates;
    private int chunksLoaded;
    private int chunkLoadErrors;

    void countCandidate() {
        candidates++;
    }

    void countChunkLoaded() {
        chunksLoaded++;
    }

    void countChunkLoadError() {
        chunkLoadErrors++;
    }

    void countReject(Reject reason) {
        rejects.merge(reason, 1, Integer::sum);
    }

    int candidates() {
        return candidates;
    }

    int chunksLoaded() {
        return chunksLoaded;
    }

    int chunkLoadErrors() {
        return chunkLoadErrors;
    }

    Map<Reject, Integer> rejects() {
        return Collections.unmodifiableMap(rejects);
    }

    void reset() {
        rejects.clear();
        candidates = 0;
        chunksLoaded = 0;
        chunkLoadErrors = 0;
    }

    String summary() {
        StringBuilder out = new StringBuilder();
        out.append("candidates=").append(candidates)
                .append(", chunksLoaded=").append(chunksLoaded)
                .append(", chunkErrors=").append(chunkLoadErrors);
        if (!rejects.isEmpty()) {
            out.append(", rejects{");
            boolean first = true;
            for (Map.Entry<Reject, Integer> entry : rejects.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(entry.getKey().name().toLowerCase(java.util.Locale.ROOT))
                        .append('=').append(entry.getValue());
            }
            out.append('}');
        }
        return out.toString();
    }
}
