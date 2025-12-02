package com.ender.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

// Simple client-side cache of per-player research states (keyed by player UUID)
public class ClientResearchProgressManager {
    private static final Map<UUID, Map<Integer, ResearchState>> CACHE = new ConcurrentHashMap<>();

    public static void putPlayerStates(UUID player, Map<Integer, ResearchState> states) {
        CACHE.put(player, new ConcurrentHashMap<>(states));
    }

    public static Map<Integer, ResearchState> getStates(UUID player) {
        return CACHE.getOrDefault(player, new ConcurrentHashMap<>());
    }

    public static ResearchState getState(UUID player, int categoryIndex) {
        return getStates(player).getOrDefault(categoryIndex, ResearchState.NOT_STARTED);
    }

    public static void setState(UUID player, int categoryIndex, ResearchState state) {
        CACHE.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(categoryIndex, state);
    }
}
