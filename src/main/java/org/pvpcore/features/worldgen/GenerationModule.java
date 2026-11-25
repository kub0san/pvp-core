package org.pvpcore.features.worldgen;

import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.HandlerList;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.module.PvPCoreModule;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/**
 * Entry point for the optional PvP world generator.
 *
 * <p>The generator itself is pure Bukkit API and needs no external plugin: Multiverse or any
 * other world manager just needs to pass {@code "pvp-core:DESERT"} (or another
 * {@link ArenaBiome} id) as the generator when the world is created. Multiverse is only used
 * later for lifecycle events (pregen on create, reset on demand); none of that is active
 * yet.</p>
 *
 * <p>The generator is always offered. It only affects a world when that world is created with
 * {@code -g PvPCore:<BIOME_ID>}; other worlds are untouched.</p>
 */
public final class GenerationModule implements PvPCoreModule {

    private final PvPCorePlugin plugin;
    private final WorldGenSettings settings = new WorldGenSettings();

    /**
     * Loaded from {@code worldgen.expected-worlds} on enable and reload.
     *
     * <p>Kept local to this module; {@link WorldGenSettings} no longer duplicates it.</p>
     */
    private volatile Set<String> expectedWorlds = Set.of();

    private WorldSettingsListener listener;

    public GenerationModule(PvPCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "worldgen";
    }

    @Override
    public void enable(PvPCorePlugin plugin) {
        enable();
    }

    /** Called from PvPCorePlugin#onEnable. */
    public void enable() {
        settings.load(plugin, org.pvpcore.config.worldgen.WorldgenConfig.load(plugin));
        loadExpectedWorlds();
        if (listener == null) {
            listener = new WorldSettingsListener(this);
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }
        applyToLoadedWorlds();
        plugin.getLogger().info("worldgen: available generators: " + ids()
                + " (radius=" + settings.maxRadiusBlocks()
                + ", border=" + settings.worldBorderDiameter() + ").");
    }

    /** Called from PvPCorePlugin#onDisable. */
    @Override
    public void disable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
    }

    /** Called from /pvp-core reload. */
    @Override
    public void reload() {
        settings.load(plugin, org.pvpcore.config.worldgen.WorldgenConfig.load(plugin));
        loadExpectedWorlds();
        applyToLoadedWorlds();
        plugin.getLogger().info("worldgen: settings reloaded (radius=" + settings.maxRadiusBlocks()
                + ", border=" + settings.worldBorderDiameter() + "). "
                + "Existing chunks keep their blocks; only new chunks use the new settings.");
    }

    /**
     * Bukkit entry point. Returns a generator for every known {@link ArenaBiome} id, or
     * {@code null} for an unknown id (including {@code null} and the empty string).
     *
     * <p>Returning {@code null} for an unknown id is deliberate: a typo such as
     * {@code PvPCore:DESRT} fails loudly at world creation instead of silently producing
     * vanilla terrain. Known ids are matched case-insensitively.</p>
     *
     * <p>Bukkit re-asks {@code getDefaultWorldGenerator} on every world load with the id the
     * world was created with. Because the id is always a valid {@link ArenaBiome} name, a
     * world created with {@code PvPCore:DESERT} keeps working across reloads.</p>
     */
    public org.bukkit.generator.ChunkGenerator createGenerator(String worldName, String id) {
        ArenaBiome biome = ArenaBiome.byId(id);
        if (biome == null) {
            return null;
        }
        return new ChunkGenerator(settings, biome);
    }

    /** True when the world is listed in {@code worldgen.expected-worlds}. */
    public boolean isExpectedArenaWorld(String name) {
        return expectedWorlds.contains(name);
    }

    /** Logs the "world loaded without the pvp-core generator" warning. */
    public void logMissingGenerator(World world) {
        plugin.getLogger().warning("worldgen: world '" + world.getName()
                + "' loaded WITHOUT the pvp-core generator; new chunks will use vanilla "
                + "generation. Common causes: the plugin was not enabled when the world "
                + "loaded (check load order), the world was created with a different "
                + "generator id, or the world manager did not pass the id through. "
                + "Recreate the world with '-g pvp-core:<BIOME_ID>' (" + biomeIds()
                + ") to regenerate.");
    }

    /**
     * Applies the arena rules to a world created with {@link ChunkGenerator}: a border
     * centered on 0,0, the configured mob-spawning flag, and the weather-cycle rule.
     * Vanilla worlds and worlds from other generators are left alone.
     *
     * <p>{@link GameRule#DO_WEATHER_CYCLE} is deprecated for removal but still has no
     * {@link GameRules} replacement on this Paper build, so the singular constant is used
     * and the warning is suppressed locally. Remove the suppression and switch to
     * {@code GameRules.DO_WEATHER_CYCLE} once Paper exposes it.</p>
     */
    @SuppressWarnings("removal")
    void applyWorldSettings(World world) {
        if (!(world.getGenerator() instanceof ChunkGenerator)) {
            return;
        }
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0.0, 0.0);
        border.setSize(settings.worldBorderDiameter());

        boolean allowSpawning = settings.allowMobSpawning();
        boolean allowWeather = settings.allowWeatherCycle();
        boolean allowDayCycle = settings.allowDayCycle();
        long dayTime = settings.dayTime();

        setAllowMonsterSpawning(world, allowSpawning);
        world.setGameRule(GameRules.SPAWN_MOBS, allowSpawning);

        if (!allowWeather) {
            world.setStorm(false);
            world.setThundering(false);
        }
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, allowWeather);

        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, allowDayCycle);
        if (!allowDayCycle) {
            world.setTime(dayTime);
        }

        plugin.getLogger().info("worldgen: applied to '" + world.getName()
                + "' (border=" + settings.worldBorderDiameter()
                + ", mob-spawning=" + allowSpawning
                + ", weather-cycle=" + allowWeather
                + ", day-cycle=" + allowDayCycle + ").");
    }

    private void applyToLoadedWorlds() {
        for (World world : plugin.getServer().getWorlds()) {
            applyWorldSettings(world);
        }
    }

    private void loadExpectedWorlds() {
        org.bukkit.configuration.file.FileConfiguration worldgenConfig = org.pvpcore.config.worldgen.WorldgenConfig.load(plugin);
        ConfigurationSection section = worldgenConfig;
        if (section == null) {
            expectedWorlds = Set.of();
            return;
        }
        List<String> list = section.getStringList("expected-worlds");
        java.util.Set<String> tmp = new java.util.HashSet<>();
        for (String w : list) {
            if (w != null && !w.isEmpty()) {
                tmp.add(w);
            }
        }
        expectedWorlds = java.util.Set.copyOf(tmp);
    }

    /**
     * Calls {@code World#setAllowMonsterSpawning(boolean)} when the server exposes it.
     *
     * <p>On Paper builds that expose the method, it sets the world-level monster flag in
     * addition to the {@code SPAWN_MOBS} game rule. On builds that do not, {@code SPAWN_MOBS}
     * alone already prevents natural spawning, so a missing method is a normal, silent
     * fallback.</p>
     */
    private void setAllowMonsterSpawning(World world, boolean value) {
        try {
            Method method = World.class.getMethod("setAllowMonsterSpawning", boolean.class);
            method.invoke(world, value);
        } catch (NoSuchMethodException ignored) {
            // Build does not expose the method; SPAWN_MOBS covers it.
        } catch (Throwable t) {
            plugin.getLogger().warning("worldgen: could not update the world monster-spawn flag on '"
                    + world.getName() + "': " + t);
        }
    }

    /** Comma-separated full ids, e.g. {@code pvp-core:DESERT, pvp-core:PLAINS}. */
    private static String ids() {
        StringBuilder sb = new StringBuilder();
        for (ArenaBiome biome : ArenaBiome.values()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("pvp-core:").append(biome.id());
        }
        return sb.toString();
    }

    /** Comma-separated bare ids, e.g. {@code DESERT, PLAINS}. */
    private static String biomeIds() {
        StringBuilder sb = new StringBuilder();
        for (ArenaBiome biome : ArenaBiome.values()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(biome.id());
        }
        return sb.toString();
    }
}