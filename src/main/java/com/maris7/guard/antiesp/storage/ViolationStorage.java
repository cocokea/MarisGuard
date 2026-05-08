package com.maris7.guard.antiesp.storage;

import java.util.UUID;

public interface ViolationStorage {
    void start();
    void stop();
    int loadViolations(UUID playerId);
    void saveViolationsAsync(UUID playerId, String playerName, int violations);
}

