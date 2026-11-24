package org.pvpcore;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIPaperConfig;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Map;
import org.pvpcore.commands.PvPCoreCommands;
import org.pvpcore.config.core.CoreConfig;
import org.pvpcore.config.Messages;
import org.pvpcore.features.worldgen.GenerationModule;
import org.pvpcore.features.kits.Kits;
import org.pvpcore.mechanic.MechanicRegistry;
import org.pvpcore.mechanic.attributeswap.AttributeSwapMechanic;
import org.pvpcore.mechanic.clumps.ClumpsMechanic;
import org.pvpcore.mechanic.consistentdurability.ConsistentDurabilityMechanic;
import org.pvpcore.mechanic.consistentipc.InstantPearlCatchMechanic;
import org.pvpcore.mechanic.fastercrystals.FasterCrystalsMechanic;
import org.pvpcore.mechanic.knockbacksync.KnockbackSyncMechanic;
import org.pvpcore.mechanic.legacypearls.LegacyPearlsMechanic;
import org.pvpcore.mechanic.legacyprojectiles.LegacyProjectilesMechanic;
import org.pvpcore.mechanic.nospread.NoSpreadMechanic;
import org.pvpcore.mechanic.shieldfixes.ShieldFixesMechanic;
import org.pvpcore.mechanic.shieldstun.ShieldStunMechanic;
import org.pvpcore.mechanic.shieldsync.ShieldSyncMechanic;
import org.pvpcore.mechanic.totemfixes.AntiTotemGhostMechanic;
import org.pvpcore.mechanic.legacyknockback.LegacyKnockbackMechanic;
import org.pvpcore.mechanic.custompearlvelocity.CustomPearlVelocityMechanic;
import org.pvpcore.features.teleport.TeleportModule;
import org.pvpcore.features.combat.CombatTagFeature;
import org.pvpcore.module.ModuleLifecycle;
import org.pvpcore.module.PvPCoreModule;
import org.pvpcore.util.ConfigUpdater;
import java.util.logging.Level;

public final class PvPCorePlugin extends JavaPlugin implements Listener {

    private final GenerationModule generation = new GenerationModule(this);
    private final TeleportModule teleportation = new TeleportModule(this);
    private final Kits kits = new Kits();
    private final CombatTagFeature combat = new CombatTagFeature(this);

    private CoreConfig coreConfig;
    private Messages messages;
    private MechanicRegistry mechanics;

    @Override
    public void onLoad() {
        migrateLegacyDataFolder();
        CommandAPI.onLoad(new CommandAPIPaperConfig(this).silentLogs(true));
    }

    private void migrateLegacyDataFolder() {
        File pluginsDir = getFile().getParentFile();
        if (pluginsDir == null) return;
        File legacy = new File(pluginsDir, "PvPCore");
        File current = super.getDataFolder(); // pvp-core
        if (!legacy.exists() || legacy.equals(current) || current.exists()) {
            return;
        }
        if (legacy.renameTo(current)) {
            getLogger().info("Migrated data folder: plugins/PvPCore -> plugins/" + current.getName());
        } else {
            getLogger().warning("Found legacy data folder plugins/PvPCore but could not rename it to plugins/"
                    + current.getName() + ". Please migrate it manually to preserve existing data.");
        }
    }

    @Override
    public void onEnable() {
        // The GUI layout constants are checked before anything else: a broken
        // layout must fail the startup, not produce a wrong screen later.
        try {
            org.pvpcore.gui.base.GuiLayoutChecks.verify();
        } catch (IllegalStateException badLayout) {
            getLogger().severe("GUI layout check failed, refusing to start: " + badLayout.getMessage());
            throw badLayout;
        }

        CommandAPI.onEnable();
        getLogger().warning("It is recommended to fully restart the server when updating pvp-core. "
                + "Hot-reloading while players are in PvPCore worlds may cause chunk generation errors.");

        // Auto-update all major configs and message files (including messages.yml) to remove obsolete keys and add new ones.
        ConfigUpdater.updateAll(this);

        this.coreConfig = new CoreConfig(this);

        // Load the messages file correctly using your static factory method
        this.messages = Messages.load(this, new File(getDataFolder(), "messages.yml"), "messages.yml");

        this.mechanics = new MechanicRegistry(this, coreConfig);

        registerSafe("AttributeSwap", () -> new AttributeSwapMechanic());
        registerSafe("FasterCrystals", () -> new FasterCrystalsMechanic());
        registerSafe("ShieldFixes", () -> new ShieldFixesMechanic());
        registerSafe("ShieldStun", () -> new ShieldStunMechanic());
        registerSafe("ShieldSync", () -> new ShieldSyncMechanic());
        registerSafe("AntiTotemGhost", () -> new AntiTotemGhostMechanic());
        registerSafe("LegacyProjectiles", () -> new LegacyProjectilesMechanic());
        registerSafe("LegacyKnockback", () -> new LegacyKnockbackMechanic());
        registerSafe("CustomPearlVelocity", () -> new CustomPearlVelocityMechanic());
        registerSafe("NoSpread", () -> new NoSpreadMechanic());
        registerSafe("KnockbackSync", () -> new KnockbackSyncMechanic());
        registerSafe("ConsistentDurability", () -> new ConsistentDurabilityMechanic());
        registerSafe("Clumps", () -> new ClumpsMechanic());
        registerSafe("LegacyPearls", () -> new LegacyPearlsMechanic());
        registerSafe("InstantPearlCatch", () -> new InstantPearlCatchMechanic());

        // Try to enable what we can right now (mechanics without PacketEvents will succeed).
        try {
            mechanics.enableConfigured();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Some mechanics could not be enabled at startup", t);
        }

        for (PvPCoreModule module : featureModulesInEnableOrder()) {
            try {
                module.enable(this);
            } catch (Throwable t) {
                getLogger().log(Level.SEVERE, enableFailureMessage(module), t);
            }
        }

        try {
            PvPCoreCommands commands = new PvPCoreCommands(this, mechanics, generation, teleportation, combat);
            commands.register();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Command registration failed; /pvp-core will not work until restart", t);
        }

        // /pvp-core is registered via CommandAPI (delegating to the router). Legacy
        // top-level commands continue to work through the bridge and existing module
        // Bukkit registrations.
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    /**
     * Registers a mechanic without letting a broken optional dependency kill the plugin.
     * Soft dependencies (PacketEvents, Multiverse-Core) may be absent at runtime; a
     * mechanic that cannot be instantiated for that reason is reported and skipped
     * instead of preventing the command and bridge registration.
     */
    private void registerSafe(String name, java.util.function.Supplier<org.pvpcore.mechanic.Mechanic> factory) {
        try {
            mechanics.register(factory.get());
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Mechanic '" + name + "' could not be registered; it will be unavailable.", t);
        }
    }

    @Override
    public void onDisable() {
        // Each step is isolated: one failing module must not skip the rest of the
        // shutdown. In particular, mechanics.disableAll() unregisters our PacketEvents
        // listeners - if it is skipped, PacketEvents keeps calling into a dead
        // classloader after unload (IllegalStateException: zip file closed).
        runDisableStep("CommandAPI shutdown", CommandAPI::onDisable);
        runDisableStep("Cancel scheduled tasks", () -> {
            getServer().getScheduler().cancelTasks(this);
            getServer().getAsyncScheduler().cancelTasks(this);
        });

        // Feature modules: derived reverse dependency order, each step isolated.
        for (PvPCoreModule module : featureModulesInDisableOrder()) {
            runDisableStep(disableStepName(module), () -> {
                if (module != null) module.disable();
            });
        }
        runDisableStep("Mechanics", () -> {
            if (mechanics != null) mechanics.disableAll();
        });

        // 3. Clear static references to prevent memory leaks across reloads
        this.coreConfig = null;
        this.messages = null;
        this.mechanics = null;
    }

    private void runDisableStep(String step, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Disable step failed: " + step, t);
        }
    }

    private List<PvPCoreModule> featureModules() {
        return List.of(generation, teleportation, kits, combat);
    }

    private List<PvPCoreModule> featureModulesInEnableOrder() {
        return ModuleLifecycle.enableOrder(featureModules(), getLogger());
    }

    private List<PvPCoreModule> featureModulesInDisableOrder() {
        return ModuleLifecycle.disableOrder(featureModules(), getLogger());
    }

    private String enableFailureMessage(PvPCoreModule module) {
        return switch (module.name()) {
            case "worldgen" -> "World generation module failed to enable";
            case "teleport" -> "Teleportation module failed to enable";
            case "kits" -> "Kits module failed to enable";
            case "combat" -> "Combat tag feature failed to enable";
            default -> module.name() + " module failed to enable";
        };
    }

    private String disableStepName(PvPCoreModule module) {
        return switch (module.name()) {
            case "worldgen" -> "World generation module";
            case "teleport" -> "Teleportation module";
            case "kits" -> "Kits module";
            case "combat" -> "Combat tag feature";
            default -> module.name() + " module";
        };
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        getLogger().info("Server fully loaded. Retrying startup for mechanics that depend on late-loading plugins.");
        mechanics.enableConfigured();
    }

    public CoreConfig coreConfig() {
        return coreConfig;
    }

    public Messages messages() {
        return messages;
    }

    // Add this method right below messages()
    public void reloadMessages() {
        this.messages = Messages.load(this, new File(getDataFolder(), "messages.yml"), "messages.yml");
    }

    public MechanicRegistry mechanics() {
        return mechanics;
    }

    public GenerationModule generation() {
        return generation;
    }

    public TeleportModule teleportation() {
        return teleportation;
    }

    /**
     * Combat Tag entry point. Combat state itself lives behind {@link CombatTagFeature#getCombatManager()},
     * which is {@code null} when the feature is disabled or failed to enable - tolerate that before use.
     */
    public CombatTagFeature combat() {
        return combat;
    }

    public Kits kits() {
        return kits;
    }

    public org.pvpcore.features.kits.KitsContext kitsContext() {
        return kits != null ? kits.kitsContext() : null;
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, String id) {
        return generation.createGenerator(worldName, id);
    }
}