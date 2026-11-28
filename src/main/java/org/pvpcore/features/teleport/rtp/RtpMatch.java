package org.pvpcore.features.teleport.rtp;

import java.util.UUID;

/** Two players paired in one region's FIFO; both teleport to one shared acquire() spot. */
public record RtpMatch(String regionId, UUID first, UUID second) {}
