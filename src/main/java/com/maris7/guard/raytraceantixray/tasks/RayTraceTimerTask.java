package com.maris7.guard.raytraceantixray.tasks;

import java.util.TimerTask;
import java.util.concurrent.RejectedExecutionException;

import com.maris7.guard.MarisGuard;

public final class RayTraceTimerTask extends TimerTask {
    private final MarisGuard plugin;

    public RayTraceTimerTask(MarisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        boolean timingsEnabled = plugin.isTimingsEnabled();
        long start = timingsEnabled ? System.currentTimeMillis() : 0L;

        try {
            plugin.getExecutorService().invokeAll(plugin.getPlayerData().values());
            plugin.getPacketChunkBlocksCache().entrySet().removeIf(entry -> entry.getValue().getChunk() == null);
        } catch (InterruptedException e) {
            plugin.getLogger().warning("Ray trace timer thread was interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (RejectedExecutionException e) {

        }

        if (timingsEnabled) {
            long stop = System.currentTimeMillis();
            plugin.getLogger().info((stop - start) + "ms per ray trace tick.");
        }
    }
}

