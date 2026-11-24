package org.pvpcore.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Resolves a boolean inside Paper's live global configuration.
 *
 * <p>The class, getter and fields are resolved once. Every {@link #binding()} then reads the
 * current {@code GlobalConfiguration}, because {@code /paper reload} replaces the section
 * object even though the classes and fields stay the same.</p>
 */
public final class PaperSetting {

    private static final String GLOBAL_CONFIG = "io.papermc.paper.configuration.GlobalConfiguration";

    private final String sectionName;
    private final String settingName;
    private final Method getGlobalConfig;
    private final Field sectionField;
    private final Field settingField;

    private PaperSetting(String sectionName, String settingName, Method getGlobalConfig,
                         Field sectionField, Field settingField) {
        this.sectionName = sectionName;
        this.settingName = settingName;
        this.getGlobalConfig = getGlobalConfig;
        this.sectionField = sectionField;
        this.settingField = settingField;
    }

    /**
     * Resolves Paper's config class and the requested boolean field once.
     *
     * @return the resolved setting, or {@code null} when this build does not expose it
     */
    public static PaperSetting resolve(Logger logger, String sectionName, String settingName,
                                       boolean warn) {
        try {
            Class<?> global = Class.forName(GLOBAL_CONFIG);
            Method get = global.getMethod("get");
            Field section = Reflect.findField(global, sectionName);
            if (section == null) {
                if (warn && logger != null) {
                    logger.warning(GLOBAL_CONFIG + " has no field '" + sectionName + "'.");
                }
                return null;
            }
            section.setAccessible(true);

            Field setting = Reflect.findField(section.getType(), settingName);
            if (setting == null || setting.getType() != boolean.class) {
                if (warn && logger != null) {
                    logger.warning(sectionName + " has no boolean field '" + settingName + "'.");
                }
                return null;
            }
            setting.setAccessible(true);
            return new PaperSetting(sectionName, settingName, get, section, setting);
        } catch (ClassNotFoundException e) {
            if (warn && logger != null) {
                logger.warning(GLOBAL_CONFIG + " was not found; is this a Paper server?");
            }
            return null;
        } catch (Throwable t) {
            if (warn && logger != null) {
                logger.warning("Could not resolve Paper's global configuration: " + t);
            }
            return null;
        }
    }

    /**
     * Returns the current section binding, or {@code null} if the live configuration is not
     * available.
     */
    public Binding binding() throws ReflectiveOperationException {
        Object root = getGlobalConfig.invoke(null);
        if (root == null) {
            return null;
        }
        Object section = sectionField.get(root);
        if (section == null) {
            return null;
        }
        return new Binding(settingField, section);
    }

    public String sectionName() {
        return sectionName;
    }

    public String settingName() {
        return settingName;
    }

    /** A resolved live section plus the boolean field that belongs to it. */
    public record Binding(Field field, Object holder) {
        public boolean read() throws IllegalAccessException {
            return field.getBoolean(holder);
        }

        public void write(boolean value) throws IllegalAccessException {
            field.setBoolean(holder, value);
        }
    }
}
