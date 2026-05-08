package com.maris7.guard.antiesp.esper;

import com.maris7.guard.MarisGuard;
import com.maris7.guard.util.WorldNameMatcher;
import com.maris7.guard.antiesp.base.BaseTemplate;
import com.maris7.guard.antiesp.base.BaseTemplateBlock;
import com.maris7.guard.antiesp.config.CheckConfig;
import com.maris7.guard.antiesp.config.MessageConfig;
import com.maris7.guard.antiesp.storage.ViolationStorage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractEsperManager implements Listener {

    protected final MarisGuard plugin;
    protected final CheckConfig checkConfig;
    protected final MessageConfig messageConfig;
    protected final ViolationStorage violationStorage;
    protected final BaseTemplate baseTemplate;

    protected final Map<UUID, EsperSession> activeSessions = new ConcurrentHashMap<>();
    protected final Map<UUID, Integer> violations = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> alertsEnabled = new ConcurrentHashMap<>();
    private final Map<UUID, Long> autoCheckCooldownUntil = new ConcurrentHashMap<>();
    private boolean consoleAlerts = true;

    protected AbstractEsperManager(MarisGuard plugin) {
        this.plugin = plugin;
        this.checkConfig = plugin.getCheckConfig();
        this.messageConfig = plugin.getMessageConfig();
        this.violationStorage = plugin.getViolationStorage();
        this.baseTemplate = plugin.getBaseTemplate();
    }

    public abstract void start();
    public abstract void stop();
    protected abstract void runForPlayer(Player player, Runnable task);
    protected abstract void runForPlayerLater(Player player, long delayTicks, Runnable task);
    protected abstract void runGlobalRepeating(long delayTicks, long periodTicks, Runnable task);
    protected abstract void runGlobalLater(long delayTicks, Runnable task);
    protected abstract void cancelAutoCheckTask();
    protected abstract void scheduleSessionTicker(Player player);
    protected abstract void cancelSessionTicker(UUID playerId);

    public final MarisGuard plugin() {
        return plugin;
    }

    public final String message(String path, String... replacements) {
        return messageConfig.get(path, replacements);
    }

    public final void handleCheckCommand(CommandSender sender, Player target) {
        if (!checkConfig.isEsperEnabled()) {
            sender.sendMessage(message("esper.disabled"));
            return;
        }
        runForPlayer(target, () -> beginCheck(sender, target, false));
    }

    public final void handleResetCommand(CommandSender sender, String targetName) {
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            resetViolations(online.getUniqueId(), online.getName());
            sender.sendMessage(message("command.reset-success", "%player%", online.getName()));
            return;
        }

        var offline = Bukkit.getOfflinePlayerIfCached(targetName);
        if (offline == null || (!offline.hasPlayedBefore() && !offline.isOnline())) {
            sender.sendMessage(message("command.player-not-found"));
            return;
        }
        resetViolations(offline.getUniqueId(), offline.getName() == null ? targetName : offline.getName());
        sender.sendMessage(message("command.reset-success", "%player%", offline.getName() == null ? targetName : offline.getName()));
    }

    public final int getFlaggedPlayerCount() {
        int count = 0;
        for (int vl : violations.values()) {
            if (vl > 0) {
                count++;
            }
        }
        return count;
    }

    public final List<FlaggedPlayerEntry> getFlaggedPlayers() {
        List<FlaggedPlayerEntry> entries = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : violations.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            entries.add(new FlaggedPlayerEntry(entry.getKey(), name == null ? entry.getKey().toString() : name, entry.getValue()));
        }
        entries.sort(Comparator.comparingInt(FlaggedPlayerEntry::violations).reversed().thenComparing(FlaggedPlayerEntry::name, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    public final int getViolations(UUID playerId) {
        return violations.getOrDefault(playerId, 0);
    }

    public final void resetViolations(UUID playerId, String playerName) {
        violations.put(playerId, 0);
        autoCheckCooldownUntil.remove(playerId);
        violationStorage.saveViolationsAsync(playerId, playerName, 0);
    }

    protected final void startAutoCheckScheduler() {
        if (!checkConfig.isEsperEnabled() || !checkConfig.isEsperAutoCheckEnabled()) {
            return;
        }
        runGlobalRepeating(checkConfig.getEsperAutoCheckIntervalTicks(), checkConfig.getEsperAutoCheckIntervalTicks(), this::runAutoCheckCycle);
    }

    protected final void runAutoCheckCycle() {
        if (!checkConfig.isEsperEnabled()) {
            return;
        }

        int remainingCapacity = checkConfig.getEsperAutoCheckMaxActiveSessions() - activeSessions.size();
        if (remainingCapacity <= 0) {
            return;
        }

        List<Player> candidates = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (candidates.isEmpty()) {
            return;
        }

        java.util.Collections.shuffle(candidates);
        int limit = Math.min(Math.min(checkConfig.getEsperAutoCheckMaxPlayersPerCycle(), candidates.size()), remainingCapacity);
        AtomicInteger selected = new AtomicInteger();
        for (Player target : candidates) {
            runForPlayer(target, () -> {
                if (!isEligibleForAutoCheck(target)) {
                    return;
                }
                while (true) {
                    int current = selected.get();
                    if (current >= limit) {
                        return;
                    }
                    if (selected.compareAndSet(current, current + 1)) {
                        break;
                    }
                }
                synchronized (activeSessions) {
                    if (activeSessions.size() >= checkConfig.getEsperAutoCheckMaxActiveSessions()
                            || activeSessions.containsKey(target.getUniqueId())) {
                        return;
                    }
                    beginCheck(null, target, true);
                }
            });
        }
    }

    private boolean isEligibleForAutoCheck(Player player) {
        if (player == null || !player.isOnline() || !player.isValid() || player.isDead()) {
            return false;
        }
        if (activeSessions.containsKey(player.getUniqueId())) {
            return false;
        }
        if (isBlacklistedWorld(player.getWorld())) {
            return false;
        }
        long cooldownUntil = autoCheckCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
        if (cooldownUntil > System.currentTimeMillis()) {
            return false;
        }
        if (checkConfig.isEsperRequireSurvival()) {
            GameMode mode = player.getGameMode();
            if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
                return false;
            }
        }
        if (checkConfig.hasEsperMaxY() && player.getLocation().getY() > checkConfig.getEsperMaxY()) {
            return false;
        }
        return true;
    }

    private boolean isBlacklistedWorld(World world) {
        if (world == null) {
            return true;
        }
        List<String> blacklist = plugin.getConfig().getStringList("blacklist-worlds");
        return WorldNameMatcher.contains(blacklist, world);
    }

    private void beginCheck(CommandSender sender, Player target, boolean automatic) {
        if (!target.isOnline() || !target.isValid()) {
            if (sender != null) {
                sender.sendMessage(message("command.target-offline"));
            }
            return;
        }
        if (isBlacklistedWorld(target.getWorld())) {
            if (sender != null) {
                sender.sendMessage(message("esper.blacklisted-world", "%player%", target.getName()));
            }
            return;
        }

        EsperSession previous = activeSessions.remove(target.getUniqueId());
        if (previous != null) {
            cancelSessionTicker(target.getUniqueId());
            restoreBlocks(target, previous);
        }

        EsperSession session = createSession(target);
        if (session == null || session.baitBlocks().isEmpty()) {
            if (sender != null) {
                sender.sendMessage(message("esper.create-failed", "%player%", target.getName()));
            }
            return;
        }

        activeSessions.put(target.getUniqueId(), session);
        sendFakeBlocks(target, session);
        runForPlayerLater(target, 2L, () -> {
            EsperSession current = activeSessions.get(target.getUniqueId());
            if (current != null && current == session && target.isOnline()) {
                sendFakeBlocks(target, session);
            }
        });
        scheduleSessionTicker(target);
        autoCheckCooldownUntil.put(target.getUniqueId(), System.currentTimeMillis() + (checkConfig.getEsperCooldownSeconds() * 1000L));
        if (sender != null && !automatic) {
            sender.sendMessage(message("esper.started", "%player%", target.getName()));
        }
    }

    private EsperSession createSession(Player target) {
        Location origin = target.getLocation();
        World world = origin.getWorld();
        if (world == null) {
            return null;
        }

        Vector forward = origin.getDirection().setY(0.0D);
        if (forward.lengthSquared() < 0.0001D) {
            forward = new Vector(1.0D, 0.0D, 0.0D);
        } else {
            forward.normalize();
        }

        if (Math.abs(forward.getX()) >= Math.abs(forward.getZ())) {
            forward = new Vector(Math.signum(forward.getX()) == 0.0D ? 1.0D : Math.signum(forward.getX()), 0.0D, 0.0D);
        } else {
            forward = new Vector(0.0D, 0.0D, Math.signum(forward.getZ()) == 0.0D ? 1.0D : Math.signum(forward.getZ()));
        }

        Vector right = new Vector(-forward.getZ(), 0.0D, forward.getX());
        int side = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        double spawnDistance = checkConfig.getEsperSpawnDistance();
        double forwardOffset = checkConfig.getEsperForwardOffset();
        Vector spawnOrigin = origin.toVector()
                .add(right.clone().multiply(spawnDistance * side))
                .add(forward.clone().multiply(forwardOffset));

        List<BaitBlock> baitBlocks = new ArrayList<>(baseTemplate.blocks().size());
        Set<Long> usedPositions = new HashSet<>();
        for (BaseTemplateBlock templateBlock : baseTemplate.blocks()) {
            int localRight = templateBlock.x() - baseTemplate.anchorX();
            int localUp = templateBlock.y() - baseTemplate.anchorY();
            int localForward = templateBlock.z() - baseTemplate.anchorZ();

            Vector transformed = spawnOrigin.clone()
                    .add(right.clone().multiply(localRight * side))
                    .add(new Vector(0.0D, localUp, 0.0D))
                    .add(forward.clone().multiply(localForward));

            int x = (int) Math.floor(transformed.getX());
            int y = (int) Math.floor(transformed.getY());
            int z = (int) Math.floor(transformed.getZ());
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                continue;
            }

            long packed = BaitBlock.packKey(x, y, z);
            if (!usedPositions.add(packed)) {
                continue;
            }

            baitBlocks.add(BaitBlock.create(
                    x,
                    y,
                    z,
                    world.getBlockAt(x, y, z).getBlockData(),
                    templateBlock.blockData().clone()
            ));
        }

        return new EsperSession(
                world.getUID(),
                System.currentTimeMillis() + (checkConfig.getEsperDurationSeconds() * 1000L),
                List.copyOf(baitBlocks)
        );
    }

    protected final void tickSession(Player player) {
        EsperSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            cancelSessionTicker(player.getUniqueId());
            return;
        }

        if (!player.isOnline() || !player.isValid()) {
            clearSession(player.getUniqueId());
            return;
        }

        World world = player.getWorld();
        if (world == null || !world.getUID().equals(session.worldId())) {
            clearSession(player.getUniqueId());
            return;
        }

        if (checkConfig.hasEsperMaxY() && player.getLocation().getY() >= checkConfig.getEsperMaxY() + 1.0D) {
            restoreBlocks(player, session);
            clearSession(player.getUniqueId());
            return;
        }

        if (System.currentTimeMillis() >= session.expiresAtMillis()) {
            restoreBlocks(player, session);
            clearSession(player.getUniqueId());
            return;
        }

        Location body = player.getLocation();
        double triggerSquared = checkConfig.getEsperTriggerDistance() * checkConfig.getEsperTriggerDistance();
        for (BaitBlock baitBlock : session.baitBlocks()) {
            if (isWithinBlockProximity(body, baitBlock, triggerSquared)) {
                registerFailure(player, session);
                return;
            }
        }
    }

    private boolean isWithinBlockProximity(Location location, BaitBlock baitBlock, double thresholdSquared) {
        double minX = baitBlock.x();
        double minY = baitBlock.y();
        double minZ = baitBlock.z();
        double maxX = minX + 1.0D;
        double maxY = minY + 1.0D;
        double maxZ = minZ + 1.0D;

        double clampedX = Math.max(minX, Math.min(location.getX(), maxX));
        double clampedY = Math.max(minY, Math.min(location.getY(), maxY));
        double clampedZ = Math.max(minZ, Math.min(location.getZ(), maxZ));

        double dx = location.getX() - clampedX;
        double dy = location.getY() - clampedY;
        double dz = location.getZ() - clampedZ;
        return (dx * dx) + (dy * dy) + (dz * dz) <= thresholdSquared;
    }

    private void registerFailure(Player player, EsperSession session) {
        restoreBlocks(player, session);
        clearSession(player.getUniqueId());

        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        int current = violations.merge(playerId, 1, Integer::sum);
        violationStorage.saveViolationsAsync(playerId, playerName, current);
        notifyAlerts(message("esper.failed-alert",
                "%player%", playerName,
                "%current_vl%", String.valueOf(current),
                "%max_vl%", String.valueOf(checkConfig.getEsperMaxViolations())));

        if (checkConfig.isEsperPunishable() && current >= checkConfig.getEsperMaxViolations()) {
            long delayTicks = Math.max(0L, checkConfig.getEsperPunishmentDelaySeconds() * 20L);
            Runnable punishTask = () -> {
                for (String command : checkConfig.getEsperPunishmentCommands()) {
                    String finalCommand = command.replace("%player%", playerName);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                }
                resetViolations(playerId, playerName);
            };
            runGlobalLater(delayTicks, punishTask);
        }
    }

    protected final void sendFakeBlocks(Player player, EsperSession session) {
        World world = player.getWorld();
        if (world == null || !world.getUID().equals(session.worldId())) {
            return;
        }
        for (BaitBlock baitBlock : session.baitBlocks()) {
            player.sendBlockChange(new Location(world, baitBlock.x(), baitBlock.y(), baitBlock.z()), baitBlock.fakeBlockData());
        }
    }

    protected final void restoreBlocks(Player player, EsperSession session) {
        World world = player.getWorld();
        if (world == null || !world.getUID().equals(session.worldId())) {
            return;
        }
        for (BaitBlock baitBlock : session.baitBlocks()) {
            player.sendBlockChange(new Location(world, baitBlock.x(), baitBlock.y(), baitBlock.z()), baitBlock.realBlockData());
        }
    }

    protected final void clearSession(UUID playerId) {
        cancelSessionTicker(playerId);
        activeSessions.remove(playerId);
    }

    protected void notifyAlerts(String message) {
        if (consoleAlerts) {
            runGlobalLater(0L, () -> Bukkit.getConsoleSender().sendMessage(message));
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            runForPlayer(player, () -> {
                if (!player.hasPermission("esper.alerts")) {
                    return;
                }
                if (!alertsEnabled.getOrDefault(player.getUniqueId(), true)) {
                    return;
                }
                player.sendMessage(message);
            });
        }
    }

    public void toggleAlerts(CommandSender sender) {
        if (sender instanceof Player player) {
            if (!player.hasPermission("esper.alerts")) {
                player.sendMessage(message("alerts.no-permission"));
                return;
            }
            boolean newState = !alertsEnabled.getOrDefault(player.getUniqueId(), true);
            alertsEnabled.put(player.getUniqueId(), newState);
            player.sendMessage(message(newState ? "alerts.enabled" : "alerts.disabled"));
            return;
        }

        consoleAlerts = !consoleAlerts;
        sender.sendMessage(message(consoleAlerts ? "alerts.console-enabled" : "alerts.console-disabled"));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        CompletableFuture.supplyAsync(() -> violationStorage.loadViolations(player.getUniqueId()))
                .thenAccept(vl -> runForPlayer(player, () -> {
                    if (player.isOnline()) {
                        violations.put(player.getUniqueId(), vl);
                    }
                }));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreakNearBait(BlockBreakEvent event) {
        Player player = event.getPlayer();
        EsperSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        double triggerSquared = checkConfig.getEsperTriggerDistance() * checkConfig.getEsperTriggerDistance();
        Location brokenLocation = event.getBlock().getLocation().add(0.5D, 0.5D, 0.5D);
        for (BaitBlock baitBlock : session.baitBlocks()) {
            if (isWithinBlockProximity(brokenLocation, baitBlock, triggerSquared)) {
                registerFailure(player, session);
                return;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        clearSession(playerId);
        violationStorage.saveViolationsAsync(playerId, event.getPlayer().getName(), violations.getOrDefault(playerId, 0));
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        clearSession(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        clearSession(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }
        World fromWorld = event.getFrom().getWorld();
        World toWorld = event.getTo().getWorld();
        if (fromWorld != null && toWorld != null && !fromWorld.getUID().equals(toWorld.getUID())) {
            clearSession(event.getPlayer().getUniqueId());
        }
    }

    public record FlaggedPlayerEntry(UUID uniqueId, String name, int violations) {
    }
}

