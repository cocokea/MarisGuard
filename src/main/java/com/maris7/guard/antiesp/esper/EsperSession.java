package com.maris7.guard.antiesp.esper;

import java.util.List;
import java.util.UUID;

public record EsperSession(UUID worldId, long expiresAtMillis, List<BaitBlock> baitBlocks) {
}

