package com.ender.researcher;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-world SavedData storing a mapping of player UUID -> (categoryIndex -> ResearchState.ordinal()).
 */
public class ResearchSavedData extends SavedData {
    private static final String PLAYERS_KEY = "players";
    private static final String ACTIVE_RESEARCH_KEY = "activeResearch";

    private final Map<UUID, Map<Integer, ResearchState>> states = new HashMap<>();
    private CompoundTag savedResearch;

    public ResearchSavedData() {}

    // Load from NBT
    public static ResearchSavedData load(CompoundTag nbt) {
        ResearchSavedData data = new ResearchSavedData();
        if (nbt == null) return data;
        CompoundTag players = nbt.getCompound(PLAYERS_KEY);
        for (String uuidStr : players.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                CompoundTag playerTag = players.getCompound(uuidStr);
                Map<Integer, ResearchState> inner = new HashMap<>();
                for (String key : playerTag.getAllKeys()) {
                    try {
                        int idx = Integer.parseInt(key);
                        int ord = playerTag.getInt(key);
                        int clamped = Math.max(0, Math.min(ord, ResearchState.values().length - 1));
                        inner.put(idx, ResearchState.values()[clamped]);
                    } catch (Exception ex) {
                        // skip malformed
                    }
                }
                data.states.put(uuid, inner);
            } catch (Exception ex) {
                // skip malformed uuid
            }
        }
        if (nbt.contains(ACTIVE_RESEARCH_KEY, Tag.TAG_COMPOUND)) {
            data.savedResearch = nbt.getCompound(ACTIVE_RESEARCH_KEY).copy();
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, Map<Integer, ResearchState>> e : states.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            for (Map.Entry<Integer, ResearchState> inner : e.getValue().entrySet()) {
                playerTag.putInt(String.valueOf(inner.getKey()), inner.getValue().ordinal());
            }
            players.put(e.getKey().toString(), playerTag);
        }
        nbt.put(PLAYERS_KEY, players);
        if (savedResearch != null) {
            nbt.put(ACTIVE_RESEARCH_KEY, savedResearch.copy());
        }
        return nbt;
    }

    // Retrieve states for a player (may return null if none)
    public Map<Integer, ResearchState> getStates(UUID player) {
        return states.get(player);
    }

    public void setStates(UUID player, Map<Integer, ResearchState> map) {
        if (map == null) {
            states.remove(player);
        } else {
            states.put(player, new HashMap<>(map));
        }
        setDirty();
    }

    public CompoundTag getSavedResearch() {
        return savedResearch == null ? null : savedResearch.copy();
    }

    public void setSavedResearch(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            this.savedResearch = null;
        } else {
            this.savedResearch = tag.copy();
        }
        setDirty();
    }

    public void clearSavedResearch() {
        if (this.savedResearch != null) {
            this.savedResearch = null;
            setDirty();
        }
    }

    public void setState(UUID player, int index, ResearchState state) {
        Map<Integer, ResearchState> map = states.get(player);
        if (map == null) {
            map = new HashMap<>();
            states.put(player, map);
        }
        map.put(index, state);
        setDirty();
    }

    // Helper to obtain the SavedData instance for a server-level
    public static ResearchSavedData get(ServerLevel level) {
        if (level == null) return new ResearchSavedData();
        return level.getDataStorage().computeIfAbsent(ResearchSavedData::load, ResearchSavedData::new, "researchtable_player_data");
    }
}