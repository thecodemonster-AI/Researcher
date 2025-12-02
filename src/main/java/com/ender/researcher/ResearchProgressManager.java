package com.ender.researcher;

// use the shared ResearchState enum from the root package

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Global server-side manager for per-player research states.
 * Stores a mapping UUID -> (categoryIndex -> ResearchState).
 * Also stores pending unclaimed reward ItemStacks per player/category so claims survive BE resets.
 *
 * Persistence: pending rewards remain in config/pending, player research states are persisted per-world
 * using SavedData (ResearchSavedData).
 */
public class ResearchProgressManager {
    // In-memory cache of states for the currently-running server worlds (populated lazily)
    private static final Map<UUID, Map<Integer, ResearchState>> STATES = new ConcurrentHashMap<>();
    // pending reward items per player -> category -> ItemStack
    private static final Map<UUID, Map<Integer, List<ItemStack>>> PENDING_REWARDS = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PENDING_DIR = EnvPathResolver.getPendingDir();

    // POJO used for JSON serialization storing full ItemStack as SNBT
    private static class PendingPojo {
        public List<String> snbt; // list of CompoundTag SNBT strings
    }

    // Debounce save executor to reduce disk I/O
    private static final ScheduledExecutorService SAVE_EXEC = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "research-pending-save");
        t.setDaemon(true);
        return t;
    });
    private static final Map<UUID, ScheduledFuture<?>> scheduledSaves = new ConcurrentHashMap<>();
    private static final long SAVE_DELAY_MS = 2000; // debounce delay

    public static Map<Integer, ResearchState> getStatesForPlayer(UUID player) {
        return STATES.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
    }

    public static ResearchState getState(UUID player, int categoryIndex) {
        Map<Integer, ResearchState> map = STATES.get(player);
        if (map == null) return ResearchState.NOT_STARTED;
        return map.getOrDefault(categoryIndex, ResearchState.NOT_STARTED);
    }

    public static void setState(UUID player, int categoryIndex, ResearchState state) {
        Map<Integer, ResearchState> map = STATES.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
        map.put(categoryIndex, state);
    }

    public static void setStates(UUID player, Map<Integer, ResearchState> states) {
        STATES.put(player, new ConcurrentHashMap<>(states));
    }

    // New: persist/load states from a ServerLevel's SavedData
    public static Map<Integer, ResearchState> loadStatesFromLevel(ServerLevel level, UUID player) {
        if (level == null) return getStatesForPlayer(player);
        ResearchSavedData sd = ResearchSavedData.get(level);
        Map<Integer, ResearchState> map = sd.getStates(player);
        if (map == null) return getStatesForPlayer(player);
        // populate in-memory cache
        STATES.put(player, new ConcurrentHashMap<>(map));
        return STATES.get(player);
    }

    public static void saveStateToLevel(ServerLevel level, UUID player) {
        if (level == null) return; // nothing to do
        ResearchSavedData sd = ResearchSavedData.get(level);
        Map<Integer, ResearchState> m = STATES.get(player);
        sd.setStates(player, m == null ? null : new HashMap<>(m));
    }

    // Pending reward helpers
    public static void setPendingReward(UUID player, int categoryIndex, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        List<ItemStack> list = PENDING_REWARDS.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(categoryIndex, k -> new ArrayList<>());
        list.clear();
        list.add(stack.copy());
        scheduleSaveForPlayer(player);
    }

    public static void addPendingReward(UUID player, int categoryIndex, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        List<ItemStack> list = PENDING_REWARDS.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(categoryIndex, k -> new ArrayList<>());
        list.add(stack.copy());
        scheduleSaveForPlayer(player);
    }

    public static List<ItemStack> takePendingRewards(UUID player, int categoryIndex) {
        Map<Integer, List<ItemStack>> map = PENDING_REWARDS.get(player);
        if (map == null) return java.util.Collections.emptyList();
        List<ItemStack> stacks = map.remove(categoryIndex);
        if (stacks == null) return java.util.Collections.emptyList();
        scheduleSaveForPlayer(player);
        return stacks;
    }

    public static boolean hasPendingReward(UUID player, int categoryIndex) {
        Map<Integer, List<ItemStack>> map = PENDING_REWARDS.get(player);
        return map != null && map.containsKey(categoryIndex);
    }

    public static Map<UUID, Map<Integer, ResearchState>> getAll() {
        return STATES;
    }

    // Schedule a debounced save for a specific player
    private static void scheduleSaveForPlayer(UUID player) {
        ScheduledFuture<?> prev = scheduledSaves.get(player);
        if (prev != null && !prev.isDone()) prev.cancel(false);
        ScheduledFuture<?> fut = SAVE_EXEC.schedule(() -> {
            savePendingForPlayerSync(player);
            scheduledSaves.remove(player);
        }, SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
        scheduledSaves.put(player, fut);
    }

    // Synchronously write a single player's pending file to disk
    private static void savePendingForPlayerSync(UUID player) {
        try {
            Map<Integer, List<ItemStack>> map = PENDING_REWARDS.get(player);
            Map<String, PendingPojo> out = new HashMap<>();
            if (map != null) {
                for (var e : map.entrySet()) {
                    List<ItemStack> list = e.getValue();
                    if (list == null || list.isEmpty()) continue;
                    PendingPojo pojo = new PendingPojo();
                    pojo.snbt = new ArrayList<>();
                    for (ItemStack stack : list) {
                        if (stack == null || stack.isEmpty()) continue;
                        try {
                            CompoundTag tag = stack.save(new CompoundTag());
                            pojo.snbt.add(tag.toString());
                        } catch (Exception ignored) {}
                    }
                    if (!pojo.snbt.isEmpty()) {
                        out.put(String.valueOf(e.getKey()), pojo);
                    }
                }
            }

            if (!Files.exists(PENDING_DIR)) Files.createDirectories(PENDING_DIR);
            Path file = PENDING_DIR.resolve(player.toString() + ".json");
            String json = GSON.toJson(out);
            try {
                PersistenceUtils.writeStringAtomic(file, json);
            } catch (IOException ex) {
                Files.write(file, json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger("ResearchProgressManager").warning("Failed to write pending for " + player + ": " + ex.getMessage());
        }
    }

    // Save all pending player files synchronously (used on shutdown)
    public static synchronized void saveAllSync() {
        try {
            if (!Files.exists(PENDING_DIR)) Files.createDirectories(PENDING_DIR);
            for (UUID player : PENDING_REWARDS.keySet()) {
                savePendingForPlayerSync(player);
            }
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger("ResearchProgressManager").warning("Failed to save all pending rewards: " + ex.getMessage());
        }
    }

    // Load pending files from disk into memory. Call at mod init.
    public static synchronized void loadPendingFromDisk() {
        try {
            if (!Files.exists(PENDING_DIR)) return;
            try (Stream<Path> files = Files.list(PENDING_DIR)) {
                files.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                    try {
                        String json = Files.readString(p, StandardCharsets.UTF_8);
                        java.lang.reflect.Type type = new TypeToken<Map<String, PendingPojo>>(){}.getType();
                        Map<String, PendingPojo> in = GSON.fromJson(json, type);
                        if (in == null) return;
                        String fileName = p.getFileName().toString();
                        String uuidStr = fileName.substring(0, fileName.length() - 5); // remove .json
                        UUID uuid = UUID.fromString(uuidStr);
                        Map<Integer, List<ItemStack>> inner = new ConcurrentHashMap<>();
                        for (var entry : in.entrySet()) {
                            try {
                                int idx = Integer.parseInt(entry.getKey());
                                PendingPojo pojo = entry.getValue();
                                if (pojo != null && pojo.snbt != null && !pojo.snbt.isEmpty()) {
                                    List<ItemStack> list = new ArrayList<>();
                                    for (String snbt : pojo.snbt) {
                                        ItemStack stack = PersistenceUtils.itemStackFromSNBT(snbt);
                                        if (stack != null && !stack.isEmpty()) list.add(stack);
                                    }
                                    if (!list.isEmpty()) inner.put(idx, list);
                                }
                            } catch (Exception ex) {
                                // skip malformed
                            }
                        }
                        PENDING_REWARDS.put(uuid, inner);
                    } catch (Exception ex) {
                        java.util.logging.Logger.getLogger("ResearchProgressManager").warning("Failed to load pending file " + p + ": " + ex.getMessage());
                    }
                });
            }
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger("ResearchProgressManager").warning("Failed to load pending rewards: " + ex.getMessage());
        }
    }

    // Public helpers for admin commands
    public static Map<Integer, List<ItemStack>> getPendingForPlayer(UUID player) {
        return PENDING_REWARDS.getOrDefault(player, new ConcurrentHashMap<>());
    }

    // Track the last open table BlockPos reported by each player (client sends RequestSync when opening)
    private static final Map<UUID, BlockPos> OPEN_TABLE_POS = new ConcurrentHashMap<>();

    public static void setOpenTablePos(UUID player, BlockPos pos) {
        if (pos == null) {
            OPEN_TABLE_POS.remove(player);
        } else {
            OPEN_TABLE_POS.put(player, pos);
        }
    }

    public static BlockPos getOpenTablePos(UUID player) {
        return OPEN_TABLE_POS.get(player);
    }

    public static void clearPendingForPlayer(UUID player) {
        PENDING_REWARDS.remove(player);
        scheduleSaveForPlayer(player);
    }

    public static void clearPendingForPlayer(UUID player, int index) {
        Map<Integer, List<ItemStack>> m = PENDING_REWARDS.get(player);
        if (m != null) {
            m.remove(index);
            scheduleSaveForPlayer(player);
        }
    }
}