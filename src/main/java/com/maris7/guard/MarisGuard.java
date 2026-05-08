package com.maris7.guard;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.github.retrooper.packetevents.PacketEvents;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
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
import com.maris7.guard.loadingscreenremover.Metrics;
import com.maris7.guard.loadingscreenremover.PlayerManager;
import com.maris7.guard.playertrace.NoopPlayerVisibilityPacketBridge;
import com.maris7.guard.playertrace.PlayerVisibilityPacketBridge;
import com.maris7.guard.playertrace.PlayerVisibilityRaytraceService;
import com.maris7.guard.raytraceantixray.antixray.ChunkPacketBlockControllerAntiXray;
import com.maris7.guard.raytraceantixray.commands.RayTraceAntiXrayTabExecutor;
import com.maris7.guard.raytraceantixray.data.ChunkBlocks;
import com.maris7.guard.raytraceantixray.data.ChunkPacketKey;
import com.maris7.guard.raytraceantixray.data.PlayerData;
import com.maris7.guard.raytraceantixray.data.VectorialLocation;
import com.maris7.guard.raytraceantixray.listeners.PacketListener;
import com.maris7.guard.raytraceantixray.listeners.WorldListener;
import com.maris7.guard.raytraceantixray.tasks.RayTraceCallable;
import com.maris7.guard.raytraceantixray.tasks.RayTraceTimerTask;
import com.maris7.guard.raytraceantixray.tasks.UpdateBukkitRunnable;

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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        enableLoadingScreenRemover();
        enableRayTraceAntiXray();
        enableMarisEsp();
        enablePlayerVisibilityRaytrace();
    }

    @Override
    public void onDisable() {
        disablePlayerVisibilityRaytrace();
        disableMarisEsp();
        disableRayTraceAntiXray();
    }

    private void enableLoadingScreenRemover() {
        this.debugEnabled = getConfig().getBoolean("debug", false);
        this.aggressiveSameEnvironment = getConfig().getBoolean("aggressive-same-environment", true);
        this.trackTicks = Math.max(1L, getConfig().getLong("track-ticks", 80L));
        this.deathBypassTicks = Math.max(trackTicks, getConfig().getLong("death-bypass-ticks", 200L));
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
        if (!new File(getDataFolder(), "README.txt").exists()) {
            saveResource("README.txt", false);
        }
        FileConfiguration config = getConfig();
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
        PluginCommand command = getCommand("raytraceantixray");
        if (command != null) {
            command.setExecutor(new RayTraceAntiXrayTabExecutor(this));
        }
    }

    private void enableMarisEsp() {
        saveBundledResource("checks.yml");
        saveBundledResource("message.yml");
        saveBundledResource("base.yml");
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
        revealService.start();
        esperManager.start();
    }

    private void enablePlayerVisibilityRaytrace() {
        this.playerVisibilityPacketBridge = createPlayerVisibilityPacketBridge();
        this.playerVisibilityRaytraceService = new PlayerVisibilityRaytraceService(this);
        playerVisibilityRaytraceService.start();
    }

    private void disablePlayerVisibilityRaytrace() {
        if (playerVisibilityRaytraceService != null) {
            playerVisibilityRaytraceService.stop();
            playerVisibilityRaytraceService = null;
        }
        playerVisibilityPacketBridge = null;
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

    private void disableMarisEsp() {
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
            if (throwable != null) {
                Throwables.throwIfUnchecked(throwable);
                throw new RuntimeException(throwable);
            }
        }
    }

    private void saveBundledResource(String name) {
        if (!new File(getDataFolder(), name).exists()) {
            saveResource(name, false);
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

    public boolean isEnabled(World world) {
        if (world == null) {
            return false;
        }
        FileConfiguration config = getConfig();
        if (WorldNameMatcher.contains(config.getStringList("blacklist-worlds"), world)) {
            return false;
        }
        return config.getBoolean("world-settings." + worldSettingsKey(world) + ".anti-xray.ray-trace",
                config.getBoolean("world-settings.default.anti-xray.ray-trace", true));
    }

    public String worldSettingsKey(World world) {
        FileConfiguration config = getConfig();
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
