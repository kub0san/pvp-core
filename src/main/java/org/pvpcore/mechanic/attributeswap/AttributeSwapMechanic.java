package org.pvpcore.mechanic.attributeswap;

import org.pvpcore.PvPCorePlugin;
import org.pvpcore.mechanic.Mechanic;
import org.pvpcore.util.PaperSetting;

/**
 * Restores vanilla attribute swapping (MC-28289) on Paper.
 *
 * <p>Paper closes the swap window in {@code LivingEntity#detectEquipmentUpdates()}: every
 * call site in {@code ServerGamePacketListenerImpl} is gated on
 * {@code unsupported-settings.update-equipment-on-player-actions}, and the player
 * override additionally resets the attack strength ticker. Switching that field off
 * returns both the attribute refresh and the ticker reset to their vanilla places
 * (attribute refresh in {@code LivingEntity#collectEquipmentChanges}, ticker reset in
 * {@code Player#tick}). No listener, task or per-player state is needed.</p>
 *
 * <p>This mechanic fails closed. If the setting cannot be resolved or written, it refuses
 * to enable and logs the reason; the server keeps stock Paper behaviour. There is no
 * approximate mode, because an approximate mode cannot reproduce a same-tick swap: any
 * emulation applied from the next tick lands after the damage was calculated.</p>
 *
 * <p>The Paper configuration object is re-resolved for every read and write, because
 * {@code /paper reload} builds a new one. {@code /pvp-core list} reports when a reload has
 * silently turned Paper's setting back on, and {@link #refresh(PvPCorePlugin)} re-applies
 * it on {@code /pvp-core reload}.</p>
 */
public final class AttributeSwapMechanic implements Mechanic {

    /*
     * Verified against Paper 1.21.11 and 26.3 sources:
     *   GlobalConfiguration.unsupportedSettings (line 168)
     *   UnsupportedSettings.updateEquipmentOnPlayerActions, boolean, default true (line 185)
     *   all detectEquipmentUpdates() call sites are gated on it
     */
    private static final String PAPER_SECTION = "unsupportedSettings";
    private static final String PAPER_SETTING = "updateEquipmentOnPlayerActions";

    private PvPCorePlugin plugin;
    private boolean enabled;

    /** Resolved Paper setting; its live section is re-read for every operation. */
    private PaperSetting paperSetting;
    /** Last live section this mechanic wrote to, so reloads can be detected. */
    private PaperSetting.Binding paperBinding;
    /** Value from paper-global.yml to restore on disable. */
    private boolean originalEquipmentValue;
    /** True only after a successful write, never merely after resolving the field. */
    private boolean paperSettingChanged;

    @Override
    public String id() {
        return "attributeswap";
    }

    @Override
    public String displayName() {
        return "Attribute Swapping";
    }

    @Override
    public String description() {
        return "Vanilla weapon attribute swapping (MC-28289).";
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
            plugin.getLogger().severe("Attribute swapping was not enabled: Paper's "
                    + PAPER_SECTION + "." + PAPER_SETTING + " could not be resolved on this server build. "
                    + "Nothing was changed.");
            return false;
        }

        PaperSetting.Binding binding;
        boolean original;
        try {
            binding = setting.binding();
            if (binding == null) {
                plugin.getLogger().severe("Attribute swapping was not enabled: Paper's global "
                        + "configuration is not available. Nothing was changed.");
                return false;
            }
            original = binding.read();
        } catch (Throwable t) {
            plugin.getLogger().severe("Attribute swapping was not enabled: could not read Paper's "
                    + PAPER_SETTING + ": " + t);
            return false;
        }

        if (!writeSetting(binding, false)) {
            plugin.getLogger().severe("Attribute swapping was not enabled: could not write Paper's "
                    + PAPER_SETTING + ". Nothing was changed.");
            return false;
        }

        paperSetting = setting;
        paperBinding = binding;
        originalEquipmentValue = original;
        paperSettingChanged = true;
        enabled = true;

        if (original) {
            plugin.getLogger().info("Attribute swapping enabled: Paper's " + PAPER_SETTING + " is now off.");
        } else {
            plugin.getLogger().info("Attribute swapping enabled: Paper's " + PAPER_SETTING
                    + " was already off in paper-global.yml; vanilla swapping was already active.");
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
            // Re-read the live section: /paper reload may have replaced the object.
            if (paperSetting != null) {
                try {
                    PaperSetting.Binding live = paperSetting.binding();
                    if (live != null) {
                        paperBinding = live;
                    }
                } catch (Throwable ignored) {
                    // Fall through to the last binding and report if the write fails.
                }
            }
            if (paperBinding != null && writeSetting(paperBinding, originalEquipmentValue)) {
                plugin.getLogger().info("Attribute swapping disabled: restored Paper's " + PAPER_SETTING
                        + " to " + originalEquipmentValue + ".");
            } else {
                plugin.getLogger().warning("Attribute swapping disabled, but Paper's " + PAPER_SETTING
                        + " could not be restored to " + originalEquipmentValue + ".");
            }
            paperSettingChanged = false;
        }
        paperSetting = null;
        paperBinding = null;
    }

    /**
     * Re-applies the setting after a config reload. Also refreshes the value that will be
     * restored on disable, because a reload may have changed it in paper-global.yml.
     */
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

        // A reload replaces the configuration object, so the value we read is the one from
        // paper-global.yml and must become the new "original". Without a reload the live
        // value is our own write and must not overwrite it.
        if (paperBinding == null || live.holder() != paperBinding.holder() || liveValue) {
            originalEquipmentValue = liveValue;
        }

        paperBinding = live;
        if (!writeSetting(live, false)) {
            plugin.getLogger().warning("Could not re-apply Paper's " + PAPER_SETTING + " after reload.");
        }
    }

    @Override
    public String statusNote() {
        Boolean live = readLiveSetting();
        if (live == null) {
            return null;
        }
        if (live) {
            return enabled && paperSettingChanged ? "Paper setting re-enabled, run /pvp-core reload" : null;
        }
        if (paperSettingChanged && originalEquipmentValue) {
            return null;
        }
        return "paper-global.yml already off";
    }

    // ------------------------------------------------------------------
    // Paper global setting
    // ------------------------------------------------------------------

    private boolean writeSetting(PaperSetting.Binding binding, boolean value) {
        try {
            binding.write(value);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not write " + PAPER_SETTING + ": " + t);
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
