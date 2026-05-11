package com.maris7.guard;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.github.retrooper.packetevents.PacketEvents;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.maris7.guard.antifreecam.MarisFreeCamPlugin;
import com.maris7.guard.antiseedcracker.AntiSeedCrackerPacketListener;
import com.maris7.guard.antiesp.base.BaseTemplate;
import com.maris7.guard.antiesp.base.BaseTemplateLoader;
import com.maris7.guard.antiesp.command.EsperCommand;
import com.maris7.guard.antiesp.config.CheckConfig;
import com.maris7.guard.antiesp.config.MessageConfig;
import com.maris7.guard.antiesp.esper.AbstractEsperManager;
import com.maris7.guard.antiesp.esper.bukkit.BukkitEsperManager;
import com.maris7.guard.antiesp.esper.folia.FoliaEsperManager;
import com.maris7.guard.antiesp.listener.MaskedChunkPacketListener;
import com.maris7.guard.antiesp.menu.EsperMenuListener;
import com.maris7.guard.antiesp.platform.PlatformType;
import com.maris7.guard.antiesp.platform.PlatformTypeDetector;
import com.maris7.guard.antiesp.platform.bukkit.BukkitPlayerRevealService;
import com.maris7.guard.antiesp.platform.folia.FoliaPlayerRevealService;
import com.maris7.guard.antiesp.service.AbstractPlayerRevealService;
import com.maris7.guard.antiesp.storage.HikariViolationStorage;
import com.maris7.guard.antiesp.storage.ViolationStorage;
import com.maris7.guard.command.MarisGuardCommand;
import com.maris7.guard.hideentity.HideEntityService;
import com.maris7.guard.entity.DuplicateEntityUuidGuard;
import com.maris7.guard.loadingscreenremover.Metrics;
import com.maris7.guard.loadingscreenremover.PlayerManager;
import com.maris7.guard.playertrace.NoopPlayerVisibilityPacketBridge;
import com.maris7.guard.playertrace.PlayerVisibilityPacketBridge;
import com.maris7.guard.playertrace.PlayerVisibilityRaytraceService;
import com.maris7.guard.raytraceantixray.NoopRayTracePacketBridge;
import com.maris7.guard.raytraceantixray.antixray.ChunkPacketBlockControllerAntiXray;
import com.maris7.guard.raytraceantixray.data.ChunkBlocks;
import com.maris7.guard.raytraceantixray.data.ChunkPacketKey;
import com.maris7.guard.raytraceantixray.data.PlayerData;
import com.maris7.guard.raytraceantixray.RayTracePacketBridge;
import com.maris7.guard.raytraceantixray.data.VectorialLocation;
import com.maris7.guard.raytraceantixray.listeners.PacketListener;
import com.maris7.guard.raytraceantixray.listeners.WorldListener;
import com.maris7.guard.raytraceantixray.tasks.RayTraceCallable;
import com.maris7.guard.raytraceantixray.tasks.RayTraceTimerTask;
import com.maris7.guard.raytraceantixray.tasks.UpdateBukkitRunnable;
import com.maris7.guard.update.GitHubVersionChecker;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import com.maris7.guard.util.WorldNameMatcher;
import io.papermc.paper.antixray.ChunkPacketBlockController;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Timer;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class MarisGuard extends JavaPlugin {
    private static final int MAX_SAFE_RAYTRACE_THREADS = 2;
    private static final long MIN_SAFE_RAYTRACE_MS = 75L;
    private static final long MIN_SAFE_UPDATE_TICKS = 4L;
    private PlayerManager playerManager;
    private TaskScheduler taskScheduler;
    private boolean debugEnabled;
    private boolean aggressiveSameEnvironment;
    private long trackTicks;
    private long deathBypassTicks;

    private boolean folia = false;
    private volatile boolean running = false;
    private volatile boolean timingsEnabled = false;
    private final ConcurrentMap<ChunkPacketKey, ChunkBlocks> packetChunkBlocksCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, PlayerData> playerData = new ConcurrentHashMap<>();
    private ExecutorService executorService;
    private Timer timer;
    private PacketListener packetListener;
    private long updateTicks = 1L;

    private AbstractPlayerRevealService revealService;
    private AbstractEsperManager esperManager;
    private PlatformType platformType;
    private MessageConfig messageConfig;
    private CheckConfig checkConfig;
    private ViolationStorage violationStorage;
    private BaseTemplate baseTemplate;
    private PlayerVisibilityRaytraceService playerVisibilityRaytraceService;
    private PlayerVisibilityPacketBridge playerVisibilityPacketBridge;
    private RayTracePacketBridge rayTracePacketBridge;
    private MarisFreeCamPlugin freeCamPlugin;
    private HideEntityService hideEntityService;
    private AntiSeedCrackerPacketListener antiSeedCrackerPacketListener;
    private FileConfiguration antiXrayConfig;
    private FileConfiguration antiEspConfig;
    private FileConfiguration antiFreeCamConfig;
    private FileConfiguration antiSeedCrackerConfig;
    private FileConfiguration playerRaytraceConfig;
    private FileConfiguration guiConfig;

    private static final boolean LOADING_SCREEN_DEBUG = false;
    private static final boolean LOADING_SCREEN_AGGRESSIVE_SAME_ENVIRONMENT = true;
    private static final long LOADING_SCREEN_TRACK_TICKS = 80L;
    private static final long LOADING_SCREEN_DEATH_BYPASS_TICKS = 200L;

    @Override
    public void onEnable() {
        ensureYamlDefaults();
        getServer().getPluginManager().registerEvents(new DuplicateEntityUuidGuard(this), this);
        enableLoadingScreenRemover();
        enableRayTraceAntiXray();
        enableAntiEsp();
        enableAntiFreeCam();
        enableHideEntity();
        enableAntiSeedCracker();
        enablePlayerVisibilityRaytrace();
        if (isEnabled()) {
            new GitHubVersionChecker(this).checkAsync();
        }
    }

    @Override
    public void onDisable() {
        disablePlayerVisibilityRaytrace();
        disableHideEntity();
        disableAntiFreeCam();
        disableAntiSeedCracker();
        disableAntiEsp();
        disableRayTraceAntiXray();
    }

    private void enableLoadingScreenRemover() {
        this.debugEnabled = getConfig().getBoolean("debug", false);
        this.aggressiveSameEnvironment = LOADING_SCREEN_AGGRESSIVE_SAME_ENVIRONMENT;
        this.trackTicks = Math.max(1L, LOADING_SCREEN_TRACK_TICKS);
        this.deathBypassTicks = Math.max(trackTicks, LOADING_SCREEN_DEATH_BYPASS_TICKS);
        this.playerManager = new PlayerManager();
        this.taskScheduler = UniversalScheduler.getScheduler(this);
        com.maris7.guard.loadingscreenremover.PlayerListener playerListener = new com.maris7.guard.loadingscreenremover.PlayerListener(this);
        getServer().getPluginManager().registerEvents(playerListener, this);
        PacketEvents.getAPI().getEventManager().registerListeners(playerListener);
        debug("Debug enabled. track-ticks=" + trackTicks
                + ", death-bypass-ticks=" + deathBypassTicks
                + ", aggressive-same-environment=" + aggressiveSameEnvironment);
        new Metrics(this, 20950);
    }

    private void enableRayTraceAntiXray() {
        this.rayTracePacketBridge = createRayTracePacketBridge();
        FileConfiguration config = getAntiXrayConfig();
        config.options().copyDefaults(true);
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ignored) {
        }
        running = true;
        int safeThreads = Math.max(1, Math.min(config.getInt("settings.anti-xray.ray-trace-threads"), MAX_SAFE_RAYTRACE_THREADS));
        long safeMsPerTick = Math.max(config.getLong("settings.anti-xray.ms-per-ray-trace-tick"), MIN_SAFE_RAYTRACE_MS);
        executorService = Executors.newFixedThreadPool(safeThreads, new ThreadFactoryBuilder().setThreadFactory(Executors.defaultThreadFactory()).setNameFormat("RayTraceAntiXray ray trace thread %d").setDaemon(true).build());
        timer = new Timer("RayTraceAntiXray tick thread", true);
        timer.schedule(new RayTraceTimerTask(this), 0L, safeMsPerTick);
        updateTicks = Math.max(config.getLong("settings.anti-xray.update-ticks"), MIN_SAFE_UPDATE_TICKS);
        if (!folia) {
            new UpdateBukkitRunnable(this).runTaskTimer(this, 0L, updateTicks);
        }
        PluginManager pluginManager = getServer().getPluginManager();
        WorldListener worldListener = new WorldListener(this);
        pluginManager.registerEvents(worldListener, this);
        for (World world : getServer().getWorlds()) {
            worldListener.install(world);
        }
        pluginManager.registerEvents(new com.maris7.guard.raytraceantixray.listeners.PlayerListener(this), this);
        if (folia) {
            for (Player player : getServer().getOnlinePlayers()) {
                if (validatePlayer(player) && isEnabled(player.getWorld())) {
                    PlayerData playerData = new PlayerData(getLocations(player, new VectorialLocation(player.getEyeLocation())));
                    playerData.setCallable(new RayTraceCallable(this, playerData));
                    putPlayerData(player, playerData);
                }
            }
        }
        packetListener = new PacketListener(this);
        PacketEvents.getAPI().getEventManager().registerListener(packetListener);
    }

    private void enableAntiEsp() {
        this.messageConfig = new MessageConfig(this);
        this.checkConfig = new CheckConfig(this);
        this.baseTemplate = new BaseTemplateLoader(this).load();
        this.violationStorage = new HikariViolationStorage(this);
        this.violationStorage.start();
        if (Bukkit.getPluginManager().getPlugin("packetevents") == null) {
            getLogger().severe("PacketEvents was not found. Disable MarisGuard.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        this.platformType = PlatformTypeDetector.detect();
        this.revealService = switch (platformType) {
            case FOLIA -> new FoliaPlayerRevealService(this);
            case BUKKIT -> new BukkitPlayerRevealService(this);
        };
        this.esperManager = switch (platformType) {
            case FOLIA -> new FoliaEsperManager(this);
            case BUKKIT -> new BukkitEsperManager(this);
        };
        PacketEvents.getAPI().getEventManager().registerListener(new MaskedChunkPacketListener(revealService));
        Bukkit.getPluginManager().registerEvents(revealService, this);
        Bukkit.getPluginManager().registerEvents(esperManager, this);
        Bukkit.getPluginManager().registerEvents(new EsperMenuListener(esperManager), this);
        PluginCommand esperCommand = getCommand("esper");
        if (esperCommand != null) {
            EsperCommand executor = new EsperCommand(esperManager);
            esperCommand.setExecutor(executor);
            esperCommand.setTabCompleter(executor);
        }
        PluginCommand guardCommand = getCommand("marisguard");
        if (guardCommand != null) {
            MarisGuardCommand executor = new MarisGuardCommand(this);
            guardCommand.setExecutor(executor);
            guardCommand.setTabCompleter(executor);
        }
        revealService.start();
        esperManager.start();
    }

    private void enablePlayerVisibilityRaytrace() {
        this.playerVisibilityPacketBridge = createPlayerVisibilityPacketBridge();
        this.playerVisibilityRaytraceService = new PlayerVisibilityRaytraceService(this);
        playerVisibilityRaytraceService.start();
    }

    private void enableAntiFreeCam() {
        this.freeCamPlugin = new MarisFreeCamPlugin(this);
        freeCamPlugin.start();
    }

    private void disablePlayerVisibilityRaytrace() {
        if (playerVisibilityRaytraceService != null) {
            playerVisibilityRaytraceService.stop();
            playerVisibilityRaytraceService = null;
        }
        playerVisibilityPacketBridge = null;
    }

    private void disableAntiFreeCam() {
        if (freeCamPlugin != null) {
            freeCamPlugin.stop();
            freeCamPlugin = null;
        }
    }

    private void enableAntiSeedCracker() {
        this.antiSeedCrackerPacketListener = new AntiSeedCrackerPacketListener(this);
        PacketEvents.getAPI().getEventManager().registerListener(antiSeedCrackerPacketListener);
    }

    private void disableAntiSeedCracker() {
        if (antiSeedCrackerPacketListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(antiSeedCrackerPacketListener);
            antiSeedCrackerPacketListener = null;
        }
    }

    private void enableHideEntity() {
        this.hideEntityService = new HideEntityService(this);
        hideEntityService.start();
    }

    private void disableHideEntity() {
        if (hideEntityService != null) {
            hideEntityService.stop();
            hideEntityService = null;
        }
    }

    private PlayerVisibilityPacketBridge createPlayerVisibilityPacketBridge() {
        String[] bridgeClassNames = {
                "com.maris7.guard.nms.v26_1_2.playertrace.V26_1_2PlayerVisibilityPacketBridge",
                "com.maris7.guard.nms.v1_21.playertrace.V1_21PlayerVisibilityPacketBridge",
                "com.maris7.guard.nms.v1_20.playertrace.V1_20PlayerVisibilityPacketBridge"
        };

        for (String className : bridgeClassNames) {
            try {
                Class<?> bridgeClass = Class.forName(className);
                Object instance = bridgeClass.getDeclaredConstructor().newInstance();
                if (instance instanceof PlayerVisibilityPacketBridge bridge) {
                    getLogger().info("[PlayerRaytrace] Using packet bridge " + className);
                    return bridge;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        getLogger().warning("[PlayerRaytrace] No version-specific packet bridge found. Falling back to no-op bridge.");
        return new NoopPlayerVisibilityPacketBridge();
    }

    private RayTracePacketBridge createRayTracePacketBridge() {
        String[] bridgeClassNames = {
                "com.maris7.guard.nms.v26_1_2.raytraceantixray.V26_1_2RayTracePacketBridge",
                "com.maris7.guard.nms.v1_21.raytraceantixray.V1_21RayTracePacketBridge",
                "com.maris7.guard.nms.v1_20.raytraceantixray.V1_20RayTracePacketBridge"
        };

        for (String className : bridgeClassNames) {
            try {
                Class<?> bridgeClass = Class.forName(className);
                Object instance = bridgeClass.getDeclaredConstructor().newInstance();
                if (instance instanceof RayTracePacketBridge bridge) {
                    getLogger().info("[RayTraceAntiXray] Using packet bridge " + className);
                    return bridge;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        getLogger().warning("[RayTraceAntiXray] No version-specific packet bridge found. Falling back to no-op bridge.");
        return new NoopRayTracePacketBridge();
    }

    private void disableAntiEsp() {
        if (esperManager != null) esperManager.stop();
        if (revealService != null) revealService.stop();
        if (violationStorage != null) violationStorage.stop();
    }

    private void disableRayTraceAntiXray() {
        Throwable throwable = null;
        try {
            try {
                try {
                    try {
                        try {
                        } catch (Throwable t) {
                            throwable = t;
                        } finally {
                            if (packetListener != null) {
                                PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
                                packetListener = null;
                            }
                        }
                    } catch (Throwable t) {
                        if (throwable == null) throwable = t; else throwable.addSuppressed(t);
                    } finally {
                        running = false;
                        if (timer != null) timer.cancel();
                    }
                } catch (Throwable t) {
                    if (throwable == null) throwable = t; else throwable.addSuppressed(t);
                } finally {
                    if (executorService != null) {
                        executorService.shutdownNow();
                        try {
                            executorService.awaitTermination(1000L, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                    }
                }
            } catch (Throwable t) {
                if (throwable == null) throwable = t; else throwable.addSuppressed(t);
            } finally {
                packetChunkBlocksCache.clear();
                playerData.clear();
            }
        } catch (Throwable t) {
            if (throwable == null) throwable = t; else throwable.addSuppressed(t);
        } finally {
            rayTracePacketBridge = null;
            if (throwable != null) {
                Throwables.throwIfUnchecked(throwable);
                throw new RuntimeException(throwable);
            }
        }
    }

    private void ensureYamlDefaults() {
        saveDefaultConfig();
        mergeYamlResource("config.yml");
        reloadConfig();
        mergeYamlResource("message.yml");
        mergeYamlResource("guis.yml");
        mergeYamlResource("modules/hideEntity.yml");
        mergeYamlResource("modules/antixray.yml");
        mergeYamlResource("modules/antifreecam.yml");
        mergeYamlResource("modules/antiseedcracker.yml");
        mergeYamlResource("modules/antiesp.yml");
        mergeYamlResource("modules/player-raytrace.yml");
        reloadModuleConfigs();
        migrateLegacyConfigLayout();
        reloadModuleConfigs();
        migrateLegacyEsperConfig();
        migrateRayTraceBlacklist();
        reloadModuleConfigs();
    }

    private void mergeYamlResource(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
            return;
        }

        try (InputStream resource = getResource(name)) {
            if (resource == null) {
                return;
            }

            org.bukkit.configuration.file.YamlConfiguration current = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            org.bukkit.configuration.file.YamlConfiguration defaults = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
            current.setDefaults(defaults);
            current.options().copyDefaults(true);
            current.save(file);
        } catch (Exception exception) {
            getLogger().warning("Unable to merge defaults for " + name + ": " + exception.getMessage());
        }
    }

    private void migrateLegacyEsperConfig() {
        File antiEspFile = new File(getDataFolder(), "modules/antiesp.yml");
        YamlConfiguration antiEsp = YamlConfiguration.loadConfiguration(antiEspFile);
        if (antiEsp.getConfigurationSection("esper") != null) {
            return;
        }
        ConfigurationSection legacyEsper = getConfig().getConfigurationSection("esper");
        if (legacyEsper == null) {
            File checksFile = new File(getDataFolder(), "checks.yml");
            if (!checksFile.isFile()) {
                return;
            }
            YamlConfiguration checks = YamlConfiguration.loadConfiguration(checksFile);
            legacyEsper = checks.getConfigurationSection("esper");
            if (legacyEsper == null) {
                return;
            }
        }
        antiEsp.createSection("esper", legacyEsper.getValues(true));
        try {
            antiEsp.save(antiEspFile);
        } catch (Exception exception) {
            getLogger().warning("Unable to migrate legacy esper settings into modules/antiesp.yml: " + exception.getMessage());
        }
    }

    private void migrateRayTraceBlacklist() {
        final String newPath = "settings.anti-xray.blacklist-worlds";
        if (getAntiXrayConfig().isList(newPath)) {
            return;
        }

        List<String> legacy = getConfig().getStringList("blacklist-worlds");
        if (legacy.isEmpty()) {
            return;
        }

        antiXrayConfig.set(newPath, legacy);
        saveYamlFile("modules/antixray.yml", antiXrayConfig);
    }

    private void migrateLegacyConfigLayout() {
        migrateList(getConfig(), antiEspConfig, "blacklist-worlds", "blacklist-worlds");
        migrateValue(getConfig(), antiEspConfig, "reveal-radius", "reveal-radius");
        migrateValue(getConfig(), antiEspConfig, "refresh-period-ticks", "refresh-period-ticks");
        migrateValue(getConfig(), antiEspConfig, "mask-material", "mask-material");
        migrateSection(getConfig(), antiEspConfig, "antiesp", "antiesp");

        migrateSection(getConfig(), antiFreeCamConfig, "antifreecam", "antifreecam");

        migrateSection(getConfig(), playerRaytraceConfig, "player-visibility-raytrace", "player-visibility-raytrace");

        migrateSection(getConfig(), antiXrayConfig, "settings", "settings");
        migrateSection(getConfig(), antiXrayConfig, "world-settings", "world-settings");
    }

    private void migrateValue(FileConfiguration source, FileConfiguration target, String sourcePath, String targetPath) {
        if (target.contains(targetPath) || !source.contains(sourcePath)) {
            return;
        }
        target.set(targetPath, source.get(sourcePath));
        saveModuleByPath(targetPath, target);
    }

    private void migrateList(FileConfiguration source, FileConfiguration target, String sourcePath, String targetPath) {
        if (target.isList(targetPath) || !source.isList(sourcePath)) {
            return;
        }
        target.set(targetPath, source.getStringList(sourcePath));
        saveModuleByPath(targetPath, target);
    }

    private void migrateSection(FileConfiguration source, FileConfiguration target, String sourcePath, String targetPath) {
        if (target.isConfigurationSection(targetPath) || !source.isConfigurationSection(sourcePath)) {
            return;
        }
        ConfigurationSection sourceSection = source.getConfigurationSection(sourcePath);
        if (sourceSection == null) {
            return;
        }
        target.createSection(targetPath, sourceSection.getValues(true));
        saveModuleByPath(targetPath, target);
    }

    private void saveModuleByPath(String path, FileConfiguration target) {
        if (target == antiXrayConfig) {
            saveYamlFile("modules/antixray.yml", target);
        } else if (target == antiEspConfig) {
            saveYamlFile("modules/antiesp.yml", target);
        } else if (target == antiFreeCamConfig) {
            saveYamlFile("modules/antifreecam.yml", target);
        } else if (target == playerRaytraceConfig) {
            saveYamlFile("modules/player-raytrace.yml", target);
        }
    }


    public PlayerData putPlayerData(Player player, PlayerData newPlayerData) {
        PlayerData oldPlayerData = playerData.put(player.getUniqueId(), newPlayerData);

        if (oldPlayerData != null && oldPlayerData != newPlayerData) {
            oldPlayerData.cancelUpdateTask();
        }

        ensureFoliaUpdateTask(player, newPlayerData);
        return newPlayerData;
    }

    public PlayerData removePlayerData(Player player) {
        if (player == null) {
            return null;
        }

        return removePlayerData(player.getUniqueId());
    }

    public PlayerData removePlayerData(UUID uniqueId) {
        PlayerData oldPlayerData = playerData.remove(uniqueId);

        if (oldPlayerData != null) {
            oldPlayerData.cancelUpdateTask();
        }

        return oldPlayerData;
    }

    public void ensureFoliaUpdateTask(Player player, PlayerData data) {
        if (!folia || player == null || data == null || data.getUpdateTask() != null) {
            return;
        }

        ScheduledTask task = player.getScheduler().runAtFixedRate(this, new UpdateBukkitRunnable(this, player), null, 1L, updateTicks);
        data.setUpdateTask(task);
    }

    public void debug(String message) {
        if (debugEnabled) {
            getLogger().info("[debug] " + message);
        }
    }

    public PlayerManager getPlayerManager() { return playerManager; }
    public TaskScheduler getTaskScheduler() { return taskScheduler; }
    public boolean isDebugEnabled() { return debugEnabled; }
    public boolean isAggressiveSameEnvironment() { return aggressiveSameEnvironment; }
    public long getTrackTicks() { return trackTicks; }
    public long getDeathBypassTicks() { return deathBypassTicks; }
    public boolean isFolia() { return folia; }
    public boolean isRunning() { return running; }
    public boolean isTimingsEnabled() { return timingsEnabled; }
    public void setTimingsEnabled(boolean timingsEnabled) { this.timingsEnabled = timingsEnabled; }
    public ConcurrentMap<ChunkPacketKey, ChunkBlocks> getPacketChunkBlocksCache() { return packetChunkBlocksCache; }
    public ConcurrentMap<UUID, PlayerData> getPlayerData() { return playerData; }
    public ExecutorService getExecutorService() { return executorService; }
    public long getUpdateTicks() { return updateTicks; }
    public AbstractPlayerRevealService getRevealService() { return revealService; }
    public AbstractEsperManager getEsperManager() { return esperManager; }
    public PlatformType getPlatformType() { return platformType; }
    public MessageConfig getMessageConfig() { return messageConfig; }
    public CheckConfig getCheckConfig() { return checkConfig; }
    public ViolationStorage getViolationStorage() { return violationStorage; }
    public BaseTemplate getBaseTemplate() { return baseTemplate; }
    public PlayerVisibilityPacketBridge getPlayerVisibilityPacketBridge() { return playerVisibilityPacketBridge; }
    public RayTracePacketBridge getRayTracePacketBridge() { return rayTracePacketBridge; }

    public boolean isEnabled(World world) {
        if (world == null) {
            return false;
        }
        FileConfiguration config = getAntiXrayConfig();
        if (WorldNameMatcher.contains(config.getStringList("settings.anti-xray.blacklist-worlds"), world)) {
            return false;
        }
        return config.getBoolean("world-settings." + worldSettingsKey(world) + ".anti-xray.ray-trace",
                config.getBoolean("world-settings.default.anti-xray.ray-trace", true));
    }

    public String worldSettingsKey(World world) {
        FileConfiguration config = getAntiXrayConfig();
        String namespaced = world.getKey().toString();
        String key = world.getKey().getKey();
        String name = world.getName();
        String tail = key.substring(key.lastIndexOf('/') + 1);
        if (config.isConfigurationSection("world-settings." + namespaced)) return namespaced;
        if (config.isConfigurationSection("world-settings." + key)) return key;
        if (config.isConfigurationSection("world-settings." + name)) return name;
        if (config.isConfigurationSection("world-settings." + tail)) return tail;
        return "default";
    }

    public boolean validatePlayer(Player player) {
        return !player.hasMetadata("NPC");
    }

    public boolean validatePlayerData(Player player, PlayerData playerData, String methodName) {
        if (playerData == null) {
            if (validatePlayer(player)) {
                Logger logger = getLogger();
                logger.warning("Missing player data detected for player " + player.getName() + " in method " + methodName);
                logger.warning("Please note that reloading this plugin isn't yet supported");
                logger.warning("Also make sure you are using the correct plugin version for your Minecraft version");
                logger.warning("Please restart your server");
            }
            return false;
        }
        return true;
    }

    public void reloadRuntimeFiles() {
        reloadConfig();
        mergeYamlResource("config.yml");
        reloadConfig();
        mergeYamlResource("message.yml");
        mergeYamlResource("guis.yml");
        mergeYamlResource("modules/hideEntity.yml");
        mergeYamlResource("modules/antixray.yml");
        mergeYamlResource("modules/antifreecam.yml");
        mergeYamlResource("modules/antiseedcracker.yml");
        mergeYamlResource("modules/antiesp.yml");
        mergeYamlResource("modules/player-raytrace.yml");
        reloadModuleConfigs();
        migrateLegacyConfigLayout();
        migrateLegacyEsperConfig();
        migrateRayTraceBlacklist();
        reloadModuleConfigs();
        if (messageConfig != null) {
            messageConfig.reload();
        }
        if (checkConfig != null) {
            checkConfig.reload();
        }
        if (hideEntityService != null) {
            hideEntityService.reload();
        }
        this.baseTemplate = new BaseTemplateLoader(this).load();
    }

    private void reloadModuleConfigs() {
        this.antiXrayConfig = loadYamlFile("modules/antixray.yml");
        this.antiEspConfig = loadYamlFile("modules/antiesp.yml");
        this.antiFreeCamConfig = loadYamlFile("modules/antifreecam.yml");
        this.antiSeedCrackerConfig = loadYamlFile("modules/antiseedcracker.yml");
        this.playerRaytraceConfig = loadYamlFile("modules/player-raytrace.yml");
        this.guiConfig = loadYamlFile("guis.yml");
    }

    private FileConfiguration loadYamlFile(String path) {
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), path));
    }

    private void saveYamlFile(String path, FileConfiguration config) {
        try {
            config.save(new File(getDataFolder(), path));
        } catch (Exception exception) {
            getLogger().warning("Unable to save " + path + ": " + exception.getMessage());
        }
    }

    public FileConfiguration getAntiXrayConfig() { return antiXrayConfig; }
    public FileConfiguration getAntiEspConfig() { return antiEspConfig; }
    public FileConfiguration getAntiFreeCamConfig() { return antiFreeCamConfig; }
    public FileConfiguration getAntiSeedCrackerConfig() { return antiSeedCrackerConfig; }
    public FileConfiguration getPlayerRaytraceConfig() { return playerRaytraceConfig; }
    public FileConfiguration getGuiConfig() { return guiConfig; }

    public static VectorialLocation[] getLocations(Entity entity, VectorialLocation location) {
        World world = location.getWorld();
        ChunkPacketBlockController chunkPacketBlockController = ((CraftWorld) world).getHandle().chunkPacketBlockController;
        if (chunkPacketBlockController instanceof ChunkPacketBlockControllerAntiXray && ((ChunkPacketBlockControllerAntiXray) chunkPacketBlockController).rayTraceThirdPerson) {
            VectorialLocation thirdPersonFrontLocation = new VectorialLocation(location);
            thirdPersonFrontLocation.getDirection().multiply(-1.);
            return new VectorialLocation[] { location, move(entity, new VectorialLocation(world, location.getVector().clone(), location.getDirection())), move(entity, thirdPersonFrontLocation) };
        }
        return new VectorialLocation[] { location };
    }

    private static VectorialLocation move(Entity entity, VectorialLocation location) {
        location.getVector().subtract(location.getDirection().clone().multiply(getMaxZoom(entity, location, 4.)));
        return location;
    }

    private static double getMaxZoom(Entity entity, VectorialLocation location, double maxZoom) {
        Vector vector = location.getVector();
        Vec3 position = new Vec3(vector.getX(), vector.getY(), vector.getZ());
        double positionX = position.x;
        double positionY = position.y;
        double positionZ = position.z;
        Vector direction = location.getDirection();
        double directionX = direction.getX();
        double directionY = direction.getY();
        double directionZ = direction.getZ();
        ServerLevel serverLevel = ((CraftWorld) location.getWorld()).getHandle();
        net.minecraft.world.entity.Entity handle = ((CraftEntity) entity).getHandle();
        for (int i = 0; i < 8; i++) {
            float cornerX = (float) ((i & 1) * 2 - 1);
            float cornerY = (float) ((i >> 1 & 1) * 2 - 1);
            float cornerZ = (float) ((i >> 2 & 1) * 2 - 1);
            cornerX *= 0.1f;
            cornerY *= 0.1f;
            cornerZ *= 0.1f;
            Vec3 corner = position.add(cornerX, cornerY, cornerZ);
            Vec3 cornerMoved = new Vec3(positionX - directionX * maxZoom + (double) cornerX, positionY - directionY * maxZoom + (double) cornerY, positionZ - directionZ * maxZoom + (double) cornerZ);
            BlockHitResult result = serverLevel.clip(new ClipContext(corner, cornerMoved, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, handle));
            if (result.getType() != HitResult.Type.MISS) {
                double zoom = result.getLocation().distanceTo(position);
                if (zoom < maxZoom) {
                    maxZoom = zoom;
                }
            }
        }
        return maxZoom;
    }
}
