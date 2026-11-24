package org.pvpcore.mechanic.shieldstun;

import org.pvpcore.PvPCorePlugin;
import org.pvpcore.mechanic.Mechanic;
import org.pvpcore.util.PaperSetting;

/**
 * Restores Paper's legacy shield-stun behaviour by toggling
 * {@code unsupported-settings.skip-vanilla-damage-tick-when-shield-blocked}.
 *
 * <p>In vanilla, a hit blocked by a shield consumes a 10-tick damage tick. The defender
 * gets invulnerability without the red flash or knockback, so the follow-up hit after an
 * axe disables the shield is swallowed (MC-268147). Paper used to skip that damage tick,
 * which is the "shield stun" technique PvP players use. Paper fixed it in 1.20.6 and
 * exposed the old behaviour as an unsupported global setting. This mechanic flips that
 * setting at runtime.</p>
 *
 * <p>This is not the vanilla axe shield disable: an axe still disables shields for five
 * seconds. The mechanic only removes the blocked hit's invulnerability tick.</p>
 *
 * <p><strong>Paper issue #11761.</strong> When the setting is on, Paper's
 * {@code LivingEntity#hurt} returns early and never sends the shield-block sound. The
 * auditible feedback is restored by {@code ShieldFixesMechanic}, which mutes the vanilla
 * sound packets at a blocked-hit position and plays its own {@code item.shield.block}
 * once. That mechanic must be enabled for shield hits to be audible while ShieldStun is
 * on.</p>
 */
public final class ShieldStunMechanic implements Mechanic {

    /*
     * Verified against Paper 1.21.11 and 26.3:
     *   GlobalConfiguration.unsupportedSettings
     *   UnsupportedSettings.skipVanillaDamageTickWhenShieldBlocked, boolean, default false
     *   LivingEntity#hurt returns !skipVanillaDamageTickWhenShieldBlocked after a shield block
     */
    private static final String PAPER_SECTION = "unsupportedSettings";
    private static final String PAPER_SETTING = "skipVanillaDamageTickWhenShieldBlocked";

    private PvPCorePlugin plugin;
    private volatile boolean enabled;

    /** Resolved Paper setting; its live section is re-read for every operation. */
    private PaperSetting paperSetting;

    /** Last live section this mechanic wrote to, so reloads can be detected. */
    private Object boundSection;
    /** Value from paper-global.yml to restore on disable. */
    private boolean originalValue;
    /** True only after a successful write, never merely after resolving the fields. */
    private boolean paperSettingChanged;

    @Override
    public String id() {
        return "shieldstun";
    }

    @Override
    public String displayName() {
        return "Shield Stunning";
    }

    @Override
    public String description() {
        return "Legacy Paper shield stun: skip the blocked damage tick.";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean enable(PvPCorePlugin plugin) {
        if (enabled) {
            return true;
        }
        this.plugin = plugin;

        PaperSetting setting = PaperSetting.resolve(plugin.getLogger(),
                PAPER_SECTION, PAPER_SETTING, true);
        if (setting == null) {
            plugin.getLogger().severe("Shield stunning was not enabled: Paper's "
                    + PAPER_SECTION + "." + PAPER_SETTING + " could not be resolved on this server build. "
                    + "Nothing was changed.");
            return false;
        }

        PaperSetting.Binding binding;
        boolean original;
        try {
            binding = setting.binding();
            if (binding == null) {
                plugin.getLogger().severe("Shield stunning was not enabled: Paper's global configuration is not available.");
                return false;
            }
            original = binding.read();
        } catch (Throwable t) {
            plugin.getLogger().severe("Shield stunning was not enabled: could not read Paper's "
                    + PAPER_SETTING + ": " + t);
            return false;
        }

        if (!writeSetting(binding, true)) {
            plugin.getLogger().severe("Shield stunning was not enabled: could not write Paper's "
                    + PAPER_SETTING + ". Nothing was changed.");
            return false;
        }

        paperSetting = setting;
        boundSection = binding.holder();
        originalValue = original;
        paperSettingChanged = true;
        enabled = true;

        if (original) {
            plugin.getLogger().info("Shield stunning enabled: Paper's " + PAPER_SETTING
                    + " was already on in paper-global.yml.");
        } else {
            plugin.getLogger().info("Shield stunning enabled: Paper's " + PAPER_SETTING + " is now on.");
        }
        if (!shieldFixesEnabled(plugin)) {
            plugin.getLogger().warning("Shield stunning is on but Shield Fixes is off. Paper's "
                    + "shield-stun setting makes blocked hits silent (Paper issue #11761). "
                    + "Enable shieldfixes if you want blocked hits to make a sound.");
        }
        return true;
    }

    @Override
    public void disable(PvPCorePlugin plugin) {
        if (!enabled) {
            return;
        }
        enabled = false;

        if (paperSettingChanged) {
            if (paperSetting == null) {
                plugin.getLogger().warning("Shield stunning disabled, but Paper's " + PAPER_SETTING
                        + " could not be resolved; the setting may still be on.");
            } else {
                PaperSetting.Binding live = null;
                try {
                    live = paperSetting.binding();
                } catch (Throwable t) {
                    plugin.getLogger().warning("Shield stunning disabled, but Paper's " + PAPER_SETTING
                            + " could not be read: " + t);
                }

                if (live == null) {
                    plugin.getLogger().warning("Shield stunning disabled, but Paper's global configuration is not available; "
                            + PAPER_SETTING + " may still be on.");
                } else if (writeSetting(live, originalValue)) {
                    plugin.getLogger().info("Shield stunning disabled: restored Paper's " + PAPER_SETTING
                            + " to " + originalValue + ".");
                } else {
                    plugin.getLogger().warning("Shield stunning disabled, but Paper's " + PAPER_SETTING
                            + " could not be restored to " + originalValue + ".");
                }
            }
            paperSettingChanged = false;
        }
        paperSetting = null;
        boundSection = null;
    }

    @Override
    public void refresh(PvPCorePlugin plugin) {
        if (!enabled || !paperSettingChanged || paperSetting == null) {
            return;
        }

        PaperSetting.Binding live;
        boolean liveValue;
        try {
            live = paperSetting.binding();
            if (live == null) {
                plugin.getLogger().warning("Could not re-apply Paper's " + PAPER_SETTING + " after reload.");
                return;
            }
            liveValue = live.read();
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not read Paper's " + PAPER_SETTING + " after reload: " + t);
            return;
        }

        // A reload replaces the configuration section, so the value we read is the one from
        // paper-global.yml and must become the new "original". Without a reload the live
        // value is our own write and must not overwrite it. A live false on the same section
        // means something else reset it; treat that as the new original too.
        if (boundSection != live.holder() || !liveValue) {
            originalValue = liveValue;
        }

        boundSection = live.holder();
        if (!writeSetting(live, true)) {
            plugin.getLogger().warning("Could not re-apply Paper's " + PAPER_SETTING + " after reload.");
        }
    }

    @Override
    public String statusNote() {
        Boolean live = readLiveSetting();
        if (live == null) {
            return null;
        }
        if (enabled) {
            if (!live) {
                return "Paper setting reset, run /pvp-core reload";
            }
            if (plugin != null && !shieldFixesEnabled(plugin)) {
                return "Shield Fixes is off; blocked hits will be silent";
            }
            return null;
        }
        return live ? "paper-global.yml already on" : null;
    }

    private static boolean shieldFixesEnabled(PvPCorePlugin plugin) {
        Mechanic shieldFixes = plugin.mechanics().get("shieldfixes");
        return shieldFixes != null && shieldFixes.isEnabled();
    }

    // ------------------------------------------------------------------
    // Paper global setting
    // ------------------------------------------------------------------

    private boolean writeSetting(PaperSetting.Binding binding, boolean value) {
        try {
            binding.write(value);
            return true;
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.getLogger().warning("Could not write " + PAPER_SETTING + ": " + t);
            }
            return false;
        }
    }

    private Boolean readLiveSetting() {
        PaperSetting setting = paperSetting;
        if (setting == null) {
            // /pvp-core list also asks disabled mechanics for a status note. Resolve without
            // side effects and remember the result; enable() will re-check it anyway.
            setting = PaperSetting.resolve(plugin == null ? null : plugin.getLogger(),
                    PAPER_SECTION, PAPER_SETTING, false);
            paperSetting = setting;
        }
        if (setting == null) {
            return null;
        }
        try {
            PaperSetting.Binding binding = setting.binding();
            return binding == null ? null : binding.read();
        } catch (Throwable t) {
            return null;
        }
    }
}