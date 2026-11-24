package org.pvpcore.features.teleport.rtp;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * One /rtp destination: a world plus the area of that world players can land in. Immutable.
 *
 * <p>{@code maxRadius <= 0} means "fill the world border". {@code minY} and {@code maxY} default
 * to the world's own limits.</p>
 */
public record RtpRegion(
        String id,
        String worldName,
        Shape shape,
        boolean centerOnBorder,
        double centerX,
        double centerZ,
        int minRadius,
        int maxRadius,
        Vertical vertical,
        int minY,
        int maxY,
        String permission) {

    public enum Shape {
        CIRCLE,
        SQUARE
    }

    public enum Vertical {
        /** Use the highest solid ground in the column. Right for the overworld. */
        SURFACE,
        /** Scan down from maxY for the first spot with 2 free blocks above solid ground. */
        SCAN
    }

    static RtpRegion parse(String id, ConfigurationSection section, Logger log) {
        String world = section.getString("world", id).trim();
        Shape shape = parseEnum(Shape.class, section.getString("shape", "circle"), Shape.CIRCLE, log, id, "shape");
        Vertical vertical = parseEnum(Vertical.class, section.getString("vertical", "surface"),
                Vertical.SURFACE, log, id, "vertical");

        boolean onBorder = true;
        double cx = 0;
        double cz = 0;
        Object center = section.get("center");
        try {
            if (center instanceof List<?> list && list.size() >= 2) {
                cx = toDouble(list.get(0));
                cz = toDouble(list.get(1));
                onBorder = false;
            } else if (center instanceof String text && !text.isBlank()
                    && !text.trim().equalsIgnoreCase("border")) {
                String[] parts = text.trim().split("[,\\s]+");
                if (parts.length < 2) {
                    throw new NumberFormatException("expected two numbers");
                }
                cx = Double.parseDouble(parts[0]);
                cz = Double.parseDouble(parts[1]);
                onBorder = false;
            }
        } catch (NumberFormatException e) {
            log.warning("rtp: region '" + id + "': bad center (use 'border' or 'x, z'); using the world border centre.");
            onBorder = true;
        }

        int minRadius = Math.max(0, section.getInt("min-radius", 0));
        int maxRadius = Math.max(0, section.getInt("max-radius", 0));
        if (maxRadius > 0 && minRadius >= maxRadius) {
            log.warning("rtp: region '" + id + "': min-radius must be smaller than max-radius; using 0.");
            minRadius = 0;
        }

        return new RtpRegion(
                id, world, shape, onBorder, cx, cz, minRadius, maxRadius, vertical,
                section.getInt("min-y", Integer.MIN_VALUE),
                section.getInt("max-y", Integer.MAX_VALUE),
                section.getString("permission", "").trim());
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value).trim());
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E fallback,
                                                   Logger log, String id, String key) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("rtp: region '" + id + "': unknown " + key + " '" + raw + "'; using "
                    + fallback.name().toLowerCase(Locale.ROOT) + ".");
            return fallback;
        }
    }
}
