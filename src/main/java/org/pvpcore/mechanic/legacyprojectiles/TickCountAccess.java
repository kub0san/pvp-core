package org.pvpcore.mechanic.legacyprojectiles;

import org.bukkit.entity.Entity;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reads and writes the NMS {@code Entity#tickCount} field.
 *
 * <p>Since 1.21.6 vanilla derives a projectile's target tolerance margin from the age of
 * the projectile itself:
 * {@code Math.max(0.0f, Math.min(0.3f, (tickCount - 2) / 20.0f))}. The margin is the only
 * input, so restoring the pre-1.21.6 margin means making the projectile look eight ticks
 * old. Before 1.21.6 the margin was the constant {@code 0.3f} for the whole flight.</p>
 *
 * <p>The field is written directly instead of through Bukkit's
 * {@code Entity#setTicksLived(int)}, because that method writes {@code totalEntityAge} as
 * well and would shift the entity despawn timer and the age reported by
 * {@code Entity#getTicksLived()}.</p>
 *
 * <p>The accessors are resolved once and cached; the read and write handles are composed so
 * that a call site performs a single exact invocation per projectile.</p>
 */
final class TickCountAccess {

    /** The field the mechanic writes, shown in the debug log. */
    static final String FIELD_DESCRIPTION = "net.minecraft.world.entity.Entity#tickCount";

    private static final String CRAFT_ENTITY = "org.bukkit.craftbukkit.entity.CraftEntity";
    private static final String NMS_ENTITY = "net.minecraft.world.entity.Entity";

    /** Reads the tick count of a Bukkit entity. */
    private final MethodHandle readTickCount;
    /** Writes the tick count of a Bukkit entity. */
    private final MethodHandle writeTickCount;

    private TickCountAccess(MethodHandle readTickCount, MethodHandle writeTickCount) {
        this.readTickCount = readTickCount;
        this.writeTickCount = writeTickCount;
    }

    /**
     * Resolves the accessors.
     *
     * @return the accessors, or {@code null} when this server build does not expose them
     */
    static TickCountAccess resolve() {
        try {
            Class<?> craftEntity = Class.forName(CRAFT_ENTITY);
            Class<?> nmsEntity = Class.forName(NMS_ENTITY);
            Method getHandle = craftEntity.getMethod("getHandle");
            Field tickCount = nmsEntity.getField("tickCount");
            tickCount.setAccessible(true);

            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandle handle = lookup.unreflect(getHandle);

            // Compose once: the call site only passes the Bukkit entity.
            MethodHandle read = MethodHandles
                    .filterArguments(lookup.unreflectGetter(tickCount), 0, handle)
                    .asType(MethodType.methodType(int.class, Entity.class));
            MethodHandle write = MethodHandles
                    .filterArguments(lookup.unreflectSetter(tickCount), 0, handle)
                    .asType(MethodType.methodType(void.class, Entity.class, int.class));
            return new TickCountAccess(read, write);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Reads the tick count of a Bukkit entity.
     *
     * @throws Throwable when the handle rejects the call
     */
    int tickCount(Entity entity) throws Throwable {
        return (int) readTickCount.invokeExact(entity);
    }

    /**
     * Writes the tick count of a Bukkit entity.
     *
     * @throws Throwable when the handle rejects the call
     */
    void setTickCount(Entity entity, int ticks) throws Throwable {
        writeTickCount.invokeExact(entity, ticks);
    }
}
