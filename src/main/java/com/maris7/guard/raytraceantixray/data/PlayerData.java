package com.maris7.guard.raytraceantixray.data;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public final class PlayerData implements Callable<Object> {
    private volatile VectorialLocation[] locations;
    private final ConcurrentMap<LongWrapper, ChunkBlocks> chunks = new ConcurrentHashMap<>();
    private final Queue<Result> results = new ConcurrentLinkedQueue<>();
    private Callable<?> callable;
    private volatile ScheduledTask updateTask;

    public PlayerData(VectorialLocation[] locations) {
        this.locations = locations;
    }

    public VectorialLocation[] getLocations() {
        return locations;
    }

    public void setLocations(VectorialLocation[] locations) {
        this.locations = locations;
    }

    public ConcurrentMap<LongWrapper, ChunkBlocks> getChunks() {
        return chunks;
    }

    public Queue<Result> getResults() {
        return results;
    }

    public Callable<?> getCallable() {
        return callable;
    }

    public void setCallable(Callable<?> callable) {
        this.callable = callable;
    }

    public ScheduledTask getUpdateTask() {
        return updateTask;
    }

    public void setUpdateTask(ScheduledTask updateTask) {
        this.updateTask = updateTask;
    }

    public void cancelUpdateTask() {
        ScheduledTask task = updateTask;
        updateTask = null;

        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public Object call() throws Exception {
        return callable.call();
    }
}

