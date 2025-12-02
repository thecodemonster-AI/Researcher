package com.ender.blockentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.ender.registry.ModItems;
import com.ender.researcher.ResearchState;
import com.ender.registry.ModBlockEntities;
import com.ender.network.ModNetworking;
import com.ender.network.ProgressUpdatePacket;
import com.ender.network.ResearchStatePacket;
import com.ender.network.RewardDisplayPacket;
import com.ender.researcher.ResearchProgressManager;
import com.ender.researcher.ResearchManager;
import com.ender.config.ResearchEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import com.ender.researcher.ResearchSavedData;

public class ResearchTableBlockEntity extends BlockEntity {

    private static final Logger LOGGER = Logger.getLogger("ResearchTableBlockEntity");
    private static final int HOLOGRAM_FADE_TICKS = 10;
    private static final int HOLOGRAM_HOLD_TICKS = 50;
    private static final int HOLOGRAM_GAP_TICKS = 10;
    private static final int HOLOGRAM_TOTAL_TICKS = HOLOGRAM_FADE_TICKS * 2 + HOLOGRAM_HOLD_TICKS + HOLOGRAM_GAP_TICKS;

    private boolean researching = false;
    private int progress = 0;
    private int maxProgress = 200; // configurable
    private boolean finished = false;

    private ItemStack result = ItemStack.EMPTY;
    private final List<ItemStack> pendingRewards = new ArrayList<>();
    private final List<ItemStack> clientHologramItems = new ArrayList<>();
    private long clientHologramStartTick = 0L;

    private final Set<UUID> completedPlayers = new HashSet<>();

    // track last sent progress to avoid spamming clients
    private int lastSentProgress = -1;
    private boolean lastSentFinishedState = false;
    // active research index (maps to ResearchManager ORDERED list)
    private int activeResearchIndex = -1;

    public static void tick(Level level, BlockPos pos, BlockState state, ResearchTableBlockEntity be) {

        if (level.isClientSide) return;

        if (be.researching) {
            be.progress++;

            // Purple particles
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(
                        ParticleTypes.ENCHANT,
                        pos.getX() + 0.5,
                        pos.getY() + 1.2,
                        pos.getZ() + 0.5,
                        4, 0.25, 0.1, 0.25, 0.01
                );
            }

            if (be.progress >= be.maxProgress) {
                be.researching = false;
                be.finished = true;
                ResearchEntry entry = ResearchManager.getByIndex(be.activeResearchIndex);
                be.pendingRewards.clear();
                be.pendingRewards.addAll(be.resolveRewardStacks(entry));
                be.result = be.pendingRewards.isEmpty() ? new ItemStack(ModItems.RESEARCH_SCROLL.get()) : be.pendingRewards.get(0).copy();
                be.broadcastHologram(be.pendingRewards.isEmpty() ? Collections.singletonList(be.result) : be.pendingRewards);
            }

            be.setChanged();

            // send progress update to players tracking this block entity (only when changed)
            if (be.progress != be.lastSentProgress) {
                be.lastSentProgress = be.progress;
                if (level instanceof ServerLevel) {
                    ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> ((ServerLevel) level).getChunkAt(pos)),
                            new ProgressUpdatePacket(pos, be.progress, be.maxProgress, be.researching, be.finished, be.activeResearchIndex));
                }
            }

            // send final update on finish and update global per-player states
            if (be.finished) {
                if (level instanceof ServerLevel) {
                    ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> ((ServerLevel) level).getChunkAt(pos)),
                            new ProgressUpdatePacket(pos, be.progress, be.maxProgress, be.researching, be.finished, be.activeResearchIndex));

                    // Update per-player research states from IN_PROGRESS -> COMPLETE and notify players once
                    if (!be.lastSentFinishedState) {
                        be.lastSentFinishedState = true;
                        // iterate global manager and update players who had this active research IN_PROGRESS
                        int finishedCategory = be.activeResearchIndex;
                        for (Map.Entry<UUID, Map<Integer, ResearchState>> playerEntry : ResearchProgressManager.getAll().entrySet()) {
                            UUID uuid = playerEntry.getKey();
                            ResearchState st = ResearchProgressManager.getState(uuid, finishedCategory);
                            if (st == ResearchState.IN_PROGRESS) {
                                // mark COMPLETE_UNCLAIMED globally for the finished category
                                ResearchProgressManager.setState(uuid, finishedCategory, ResearchState.COMPLETE_UNCLAIMED);
                                // persist to world saved data
                                try {
                                    if (level instanceof ServerLevel slForSave) {
                                        ResearchProgressManager.saveStateToLevel(slForSave, uuid);
                                    }
                                } catch (Exception ignored) {}
                                // add a pending reward copy for this player so they can claim later even if the BE resets
                                try {
                                    List<ItemStack> stacks = be.pendingRewards.isEmpty() ? Collections.singletonList(be.result) : be.pendingRewards;
                                    for (ItemStack stack : stacks) {
                                        if (stack == null || stack.isEmpty()) continue;
                                        ResearchProgressManager.addPendingReward(uuid, finishedCategory, stack.copy());
                                    }
                                 } catch (Exception ignored) {}
                                // send packet to that player if online and play XP ding
                                net.minecraft.world.entity.player.Player maybe = ((ServerLevel) level).getPlayerByUUID(uuid);
                                if (maybe instanceof ServerPlayer target) {
                                    ModNetworking.sendResearchState(target, pos, finishedCategory, ResearchState.COMPLETE_UNCLAIMED.ordinal());
                                     // play XP ding for the player (use public accessor)
                                     target.level().playSound(null, target.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.0F);
                                  }
                             }
                         }
                    }
                }
            }
        }
    }

    public boolean isFinished() {
        return finished;
    }

    public void markCompleted(Player p) {
        completedPlayers.add(p.getUUID());
    }

    public boolean hasCompleted(Player p) {
        return completedPlayers.contains(p.getUUID());
    }

    public boolean tryStartResearch(Player p) {
        // Deprecated: maintain compatibility by scanning player's states and delegating to new method
        int foundIndex = -1;
        Map<Integer, ResearchState> states = ResearchProgressManager.getStatesForPlayer(p.getUUID());
        for (Map.Entry<Integer, ResearchState> e : states.entrySet()) {
            if (e.getValue() == ResearchState.IN_PROGRESS) { foundIndex = e.getKey(); break; }
        }
        if (foundIndex < 0) return false;
        return tryStartResearch(p, foundIndex);
     }

    // New: try to start research for the specified category index (called by StartResearchPacket)
    public boolean tryStartResearch(Player p, int selectedIndex) {
        // Ensure logging is initialized (in case mod constructor wasn't executed in this environment)
        com.ender.researcher.ResearchLogger.init();

        if (researching || finished) {
            LOGGER.info("tryStartResearch rejected: BE busy researching=" + researching + " finished=" + finished + " for player=" + p.getUUID() + " idx=" + selectedIndex);
            com.ender.researcher.ResearchFileLogger.log("tryStartResearch rejected: BE busy researching=" + researching + " finished=" + finished + " for player=" + p.getUUID() + " idx=" + selectedIndex);
            return false;
        }
        if (selectedIndex < 0) {
            LOGGER.warning("tryStartResearch rejected: invalid selectedIndex " + selectedIndex + " for player=" + p.getUUID());
            com.ender.researcher.ResearchFileLogger.log("tryStartResearch rejected: invalid selectedIndex " + selectedIndex + " for player=" + p.getUUID());
            return false;
        }

        // confirm player has IN_PROGRESS for this index
        ResearchState playerState = ResearchProgressManager.getState(p.getUUID(), selectedIndex);
        if (playerState != ResearchState.IN_PROGRESS) {
            LOGGER.info("tryStartResearch rejected: player state is " + playerState + " (expected IN_PROGRESS) for player=" + p.getUUID() + " idx=" + selectedIndex);
            com.ender.researcher.ResearchFileLogger.log("tryStartResearch rejected: player state is " + playerState + " (expected IN_PROGRESS) for player=" + p.getUUID() + " idx=" + selectedIndex);
            return false;
        }

        ResearchEntry entry = ResearchManager.getByIndex(selectedIndex);
        if (entry == null) {
            LOGGER.warning("tryStartResearch rejected: no entry for idx=" + selectedIndex + " for player=" + p.getUUID());
            com.ender.researcher.ResearchFileLogger.log("tryStartResearch rejected: no entry for idx=" + selectedIndex + " for player=" + p.getUUID());
            return false;
        }

        // Check item requirements (only type == "item" supported now)
        if (entry.requirements != null) {
            for (var req : entry.requirements) {
                if (req == null) continue;
                if ("item".equalsIgnoreCase(req.type)) {
                    int remaining = req.count;
                    var inv = p.getInventory();
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        if (remaining <= 0) break;
                        var stack = inv.getItem(i);
                        if (stack != null && !stack.isEmpty()) {
                            var item = stack.getItem();
                            var key = ForgeRegistries.ITEMS.getKey(item);
                            if (key != null) {
                                String id = key.toString();
                                if (id.equals(req.item)) {
                                    remaining -= stack.getCount();
                                }
                            }
                        }
                    }
                    if (remaining > 0) {
                        LOGGER.info("tryStartResearch rejected: player lacks item requirement " + req.item + " x" + req.count + " (remaining=" + remaining + ") for player=" + p.getUUID() + " idx=" + selectedIndex);
                        com.ender.researcher.ResearchFileLogger.log("tryStartResearch rejected: player lacks item requirement " + req.item + " x" + req.count + " (remaining=" + remaining + ") for player=" + p.getUUID() + " idx=" + selectedIndex);
                        return false; // lacks items
                    }
                } else if ("research".equalsIgnoreCase(req.type)) {
                    int otherIndex = ResearchManager.getIndexForId(req.research_id);
                    if (otherIndex < 0) {
                        LOGGER.warning("tryStartResearch rejected: referenced research_id not found " + req.research_id + " for player=" + p.getUUID() + " idx=" + selectedIndex);
                        com.ender.researcher.ResearchFileLogger.log("tryStartResearch rejected: referenced research_id not found " + req.research_id + " for player=" + p.getUUID() + " idx=" + selectedIndex);
                        return false;
                    }
                    ResearchState st = ResearchProgressManager.getState(p.getUUID(), otherIndex);
                    if (st != ResearchState.COMPLETE_UNCLAIMED && st != ResearchState.COMPLETE_CLAIMED) {
                        LOGGER.info("tryStartResearch rejected: player missing research prereq " + req.research_id + " state=" + st + " for player=" + p.getUUID() + " idx=" + selectedIndex);
                        com.ender.researcher.ResearchFileLogger.log("tryStartResearch rejected: player missing research prereq " + req.research_id + " state=" + st + " for player=" + p.getUUID() + " idx=" + selectedIndex);
                        return false;
                    }
                }
            }
        }

        // consume item requirements
        if (entry.requirements != null) {
            for (var req : entry.requirements) {
                if (req == null) continue;
                if ("item".equalsIgnoreCase(req.type)) {
                    int remaining = req.count;
                    var inv = p.getInventory();
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        if (remaining <= 0) break;
                        var stack = inv.getItem(i);
                        if (stack != null && !stack.isEmpty()) {
                            var item = stack.getItem();
                            var key = ForgeRegistries.ITEMS.getKey(item);
                            if (key != null) {
                                String id = key.toString();
                                if (id.equals(req.item)) {
                                    int take = Math.min(stack.getCount(), remaining);
                                    stack.shrink(take);
                                    remaining -= take;
                                }
                            }
                        }
                    }
                }
            }
        }

        // set active research and duration from config
        this.activeResearchIndex = selectedIndex;
        this.maxProgress = Math.max(1, entry.time_seconds * 20); // convert seconds to ticks from research_default
        LOGGER.info("Starting research index=" + selectedIndex + " time_seconds=" + entry.time_seconds + " maxProgress=" + this.maxProgress + ", for player=" + p.getUUID());
        com.ender.researcher.ResearchFileLogger.log("Starting research index=" + selectedIndex + " time_seconds=" + entry.time_seconds + " maxProgress=" + this.maxProgress + ", for player=" + p.getUUID());
        this.researching = true;
        this.progress = 0;

        // Send immediate update to the player and nearby clients
        if (this.level instanceof ServerLevel) {
            if (p instanceof ServerPlayer sp) {
                ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                        new ProgressUpdatePacket(this.worldPosition, this.progress, this.maxProgress, this.researching, this.finished, this.activeResearchIndex));
            }
            ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> ((ServerLevel) this.level).getChunkAt(this.worldPosition)),
                    new ProgressUpdatePacket(this.worldPosition, this.progress, this.maxProgress, this.researching, this.finished, this.activeResearchIndex));
        }

        return true;
    }

    // New: try to start research and return a failure reason (null on success)
    public String tryStartResearchWithReason(Player p, int selectedIndex) {
        // Ensure logging is initialized (in case mod constructor wasn't executed in this environment)
        com.ender.researcher.ResearchLogger.init();

        if (researching || finished) {
            String msg = "Already Researching Something";
            LOGGER.info("tryStartResearch rejected: " + msg + " for player=" + p.getUUID() + " idx=" + selectedIndex);
            com.ender.researcher.ResearchFileLogger.log("tryStartResearch rejected: " + msg + " for player=" + p.getUUID() + " idx=" + selectedIndex);
            return msg;
        }
        if (selectedIndex < 0) {
            String msg = "Invalid research index";
            LOGGER.warning("tryStartResearch rejected: " + msg + " for player=" + p.getUUID());
            com.ender.researcher.ResearchFileLogger.log("tryStartResearch rejected: " + msg + " for player=" + p.getUUID());
            return msg;
        }

        // confirm player has IN_PROGRESS for this index
        ResearchState playerState = ResearchProgressManager.getState(p.getUUID(), selectedIndex);
        if (playerState != ResearchState.IN_PROGRESS) {
            String msg = "Player state is not IN_PROGRESS";
            LOGGER.info("tryStartResearch rejected: " + msg + " (state=" + playerState + ") for player=" + p.getUUID() + " idx=" + selectedIndex);
            com.ender.researcher.ResearchFileLogger.log("tryStartResearch rejected: " + msg + " (state=" + playerState + ") for player=" + p.getUUID() + " idx=" + selectedIndex);
            return "Research not started client-side";
        }

        ResearchEntry entry = ResearchManager.getByIndex(selectedIndex);
        if (entry == null) {
            String msg = "No entry for selected index";
            LOGGER.warning("tryStartResearch rejected: " + msg + " idx=" + selectedIndex + " for player=" + p.getUUID());
            com.ender.researcher.ResearchFileLogger.log("tryStartResearch rejected: " + msg + " idx=" + selectedIndex + " for player=" + p.getUUID());
            return msg;
        }

        // Check item requirements (only type == "item" supported now)
        if (entry.requirements != null) {
            for (var req : entry.requirements) {
                if (req == null) continue;
                if ("item".equalsIgnoreCase(req.type)) {
                    int remaining = req.count;
                    var inv = p.getInventory();
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        if (remaining <= 0) break;
                        var stack = inv.getItem(i);
                        if (stack != null && !stack.isEmpty()) {
                            var item = stack.getItem();
                            var key = ForgeRegistries.ITEMS.getKey(item);
                            if (key != null) {
                                String id = key.toString();
                                if (id.equals(req.item)) {
                                    remaining -= stack.getCount();
                                }
                            }
                        }
                    }
                    if (remaining > 0) {
                        String friendly = req.item;
                        try {
                            Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(req.item));
                            if (it != null) friendly = it.getDefaultInstance().getHoverName().getString();
                        } catch (Exception ignored) {}
                        String msg = "Missing item: " + remaining + "x " + friendly;
                        LOGGER.info("tryStartResearch rejected: " + msg + " for player=" + p.getUUID() + " idx=" + selectedIndex);
                        com.ender.researcher.ResearchFileLogger.log("tryStartResearch rejected: " + msg + " for player=" + p.getUUID() + " idx=" + selectedIndex);
                        return msg;
                    }
                } else if ("research".equalsIgnoreCase(req.type)) {
                    int otherIndex = ResearchManager.getIndexForId(req.research_id);
                    if (otherIndex < 0) {
                        String msg = "Referenced research not found: " + req.research_id;
                        LOGGER.warning("tryStartResearch rejected: " + msg + " for player=" + p.getUUID() + " idx=" + selectedIndex);
                        com.ender.researcher.ResearchFileLogger.log("tryStartResearch rejected: " + msg + " for player=" + p.getUUID() + " idx=" + selectedIndex);
                        return msg;
                    }
                    ResearchState st = ResearchProgressManager.getState(p.getUUID(), otherIndex);
                    if (st != ResearchState.COMPLETE_UNCLAIMED && st != ResearchState.COMPLETE_CLAIMED) {
                        ResearchEntry other = ResearchManager.getByIndex(otherIndex);
                        String rtitle = other != null && other.title != null ? other.title : req.research_id;
                        String msg = "Requires research: " + rtitle;
                        LOGGER.info("tryStartResearch rejected: " + msg + " for player=" + p.getUUID() + " idx=" + selectedIndex);
                        com.ender.researcher.ResearchFileLogger.log("tryStartResearch rejected: " + msg + " for player=" + p.getUUID() + " idx=" + selectedIndex);
                        return msg;
                    }
                }
            }
        }

        // consume item requirements
        if (entry.requirements != null) {
            for (var req : entry.requirements) {
                if (req == null) continue;
                if ("item".equalsIgnoreCase(req.type)) {
                    int remaining = req.count;
                    var inv = p.getInventory();
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        if (remaining <= 0) break;
                        var stack = inv.getItem(i);
                        if (stack != null && !stack.isEmpty()) {
                            var item = stack.getItem();
                            var key = ForgeRegistries.ITEMS.getKey(item);
                            if (key != null) {
                                String id = key.toString();
                                if (id.equals(req.item)) {
                                    int take = Math.min(stack.getCount(), remaining);
                                    stack.shrink(take);
                                    remaining -= take;
                                }
                            }
                        }
                    }
                }
            }
        }

        // set active research and duration from config
        this.activeResearchIndex = selectedIndex;
        this.maxProgress = Math.max(1, entry.time_seconds * 20); // convert seconds to ticks from research_default
        LOGGER.info("Starting research index=" + selectedIndex + " time_seconds=" + entry.time_seconds + " maxProgress=" + this.maxProgress + ", for player=" + p.getUUID());
        com.ender.researcher.ResearchFileLogger.log("Starting research index=" + selectedIndex + " time_seconds=" + entry.time_seconds + " maxProgress=" + this.maxProgress + ", for player=" + p.getUUID());
        this.researching = true;
        this.progress = 0;

        // Send immediate update to the player and nearby clients
        if (this.level instanceof ServerLevel) {
            if (p instanceof ServerPlayer sp) {
                ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                        new ProgressUpdatePacket(this.worldPosition, this.progress, this.maxProgress, this.researching, this.finished, this.activeResearchIndex));
            }
            ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> ((ServerLevel) this.level).getChunkAt(this.worldPosition)),
                    new ProgressUpdatePacket(this.worldPosition, this.progress, this.maxProgress, this.researching, this.finished, this.activeResearchIndex));
        }

        return null; // success
    }

    public List<ItemStack> takeResults() {
        List<ItemStack> out = new ArrayList<>();
        if (!pendingRewards.isEmpty()) {
            for (ItemStack stack : pendingRewards) {
                if (stack == null || stack.isEmpty()) continue;
                out.add(stack.copy());
            }
        } else if (!result.isEmpty()) {
            out.add(result.copy());
        }
        result = ItemStack.EMPTY;
        pendingRewards.clear();
        return out;
    }

    public void resetProgress() {
        researching = false;
        finished = false;
        progress = 0;
        result = ItemStack.EMPTY;
        pendingRewards.clear();
        // clear active index and last-sent tracking so the BE is ready for new research
        this.activeResearchIndex = -1;
        this.lastSentProgress = -1;
        this.lastSentFinishedState = false;
        if (this.level instanceof ServerLevel) {
            broadcastHologram(Collections.emptyList());
        } else {
            clearClientHologram();
        }
        setChanged();
    }

    // Note: per-player states are now stored in ResearchProgressManager (global)

    public ResearchTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESEARCH_TABLE.get(), pos, state);
    }

    // Allow configuring max progress externally
    public void setMaxProgress(int max) { this.maxProgress = max; }

    // Helper: get research entry id/title by category index
    public String getResearchIdByIndex(int index) {
        var list = com.ender.researcher.ResearchManager.getAll();
        if (index < 0 || index >= list.size()) return null;
        return list.get(index).id;
    }

    public String getResearchTitleByIndex(int index) {
        var list = com.ender.researcher.ResearchManager.getAll();
        if (index < 0 || index >= list.size()) return "";
        return list.get(index).title != null ? list.get(index).title : "";
    }

    public ResearchState getResearchState(int selectedIndex, Player player) {
        return ResearchProgressManager.getState(player.getUUID(), selectedIndex);
    }

    public void setResearchState(int categoryIndex, Player player, ResearchState state) {
        ResearchProgressManager.setState(player.getUUID(), categoryIndex, state);
        // Persist to world saved data if on server side and we have a ServerLevel
        try {
            if (this.level instanceof ServerLevel sl) {
                ResearchProgressManager.saveStateToLevel(sl, player.getUUID());
            }
        } catch (Exception ignored) {}
        setChanged();
    }

    // Client-side: apply progress updates received from server
    public void applyProgressFromServer(int progress, int max, boolean researching, boolean finished, int activeIndex) {
        this.progress = progress;
        this.maxProgress = max;
        this.researching = researching;
        this.finished = finished;
        this.activeResearchIndex = activeIndex;
        // Mark changed so client updates UI if needed
        setChanged();
    }
    
    public void applyRewardDisplayFromServer(List<ItemStack> stacks) {
        this.clientHologramItems.clear();
        if (stacks != null) {
            for (ItemStack stack : stacks) {
                if (stack == null || stack.isEmpty()) continue;
                this.clientHologramItems.add(stack.copy());
            }
        }
        if (this.level != null && this.level.isClientSide && !this.clientHologramItems.isEmpty()) {
            this.clientHologramStartTick = this.level.getGameTime();
        } else {
            this.clientHologramStartTick = 0L;
        }
        setChanged();
    }
    
    public void restoreResearchState(CompoundTag tag) {
        if (tag == null) return;
        if (tag.contains("activeResearchIndex")) {
            this.activeResearchIndex = tag.getInt("activeResearchIndex");
        }
        if (tag.contains("progress")) {
            this.progress = Math.max(0, tag.getInt("progress"));
        }
        if (tag.contains("maxProgress")) {
            this.maxProgress = Math.max(1, tag.getInt("maxProgress"));
        }
        if (tag.contains("researching")) {
            this.researching = tag.getBoolean("researching");
        }
        if (tag.contains("finished")) {
            this.finished = tag.getBoolean("finished");
        }
        this.lastSentProgress = -1;
        this.lastSentFinishedState = false;
        setChanged();
    }

    public void syncProgressToClients() {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> serverLevel.getChunkAt(this.worldPosition)),
                new ProgressUpdatePacket(this.worldPosition, this.progress, this.maxProgress, this.researching, this.finished, this.activeResearchIndex));
    }

    public HologramRenderData getHologramRenderData(float partialTicks) {
        if (this.level == null || !this.level.isClientSide) return null;
        if (this.clientHologramItems.isEmpty()) return null;
        long elapsedTicksRaw = (this.level.getGameTime() - this.clientHologramStartTick);
        if (elapsedTicksRaw < 0L) elapsedTicksRaw = 0L;
        float elapsed = elapsedTicksRaw + partialTicks;
        int itemCount = this.clientHologramItems.size();
        int cycleLength = Math.max(HOLOGRAM_TOTAL_TICKS * itemCount, 1);
        float cycleProgress = elapsed % cycleLength;
        int currentIndex = Math.min((int)(cycleProgress / HOLOGRAM_TOTAL_TICKS), itemCount - 1);
        int stageTicks = (int)(cycleProgress % HOLOGRAM_TOTAL_TICKS);
        float alpha;
        if (stageTicks < HOLOGRAM_FADE_TICKS) {
            alpha = stageTicks / (float) HOLOGRAM_FADE_TICKS;
        } else if (stageTicks < HOLOGRAM_FADE_TICKS + HOLOGRAM_HOLD_TICKS) {
            alpha = 1.0F;
        } else if (stageTicks < HOLOGRAM_FADE_TICKS + HOLOGRAM_HOLD_TICKS + HOLOGRAM_FADE_TICKS) {
            alpha = 1.0F - (stageTicks - (HOLOGRAM_FADE_TICKS + HOLOGRAM_HOLD_TICKS)) / (float) HOLOGRAM_FADE_TICKS;
        } else {
            alpha = 0.0F;
        }
        if (alpha <= 0.0F) return null;
        return new HologramRenderData(this.clientHologramItems.get(currentIndex), alpha);
    }

    private void clearClientHologram() {
        this.clientHologramItems.clear();
        this.clientHologramStartTick = 0L;
    }

    private void broadcastHologram(List<ItemStack> stacks) {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        List<ItemStack> payload = new ArrayList<>();
        if (stacks != null) {
            for (ItemStack stack : stacks) {
                if (stack == null || stack.isEmpty()) continue;
                payload.add(stack.copy());
            }
        }
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> serverLevel.getChunkAt(this.worldPosition)),
                new RewardDisplayPacket(this.worldPosition, payload));
    }

    public void clearServerHologram() {
        broadcastHologram(Collections.emptyList());
    }

    private List<ItemStack> resolveRewardStacks(ResearchEntry entry) {
        List<ItemStack> stacks = new ArrayList<>();
        if (entry == null) {
            stacks.add(new ItemStack(ModItems.RESEARCH_SCROLL.get()));
            return stacks;
        }
        if (entry.rewards != null && !entry.rewards.isEmpty()) {
            for (ResearchEntry.Reward reward : entry.rewards) {
                ItemStack stack = rewardToStack(reward);
                if (!stack.isEmpty()) stacks.add(stack);
            }
        } else if (entry.reward != null) {
            ItemStack stack = rewardToStack(entry.reward);
            if (!stack.isEmpty()) stacks.add(stack);
        }
        if (stacks.isEmpty()) stacks.add(new ItemStack(ModItems.RESEARCH_SCROLL.get()));
        return stacks;
    }

    private ItemStack rewardToStack(ResearchEntry.Reward reward) {
        if (reward == null || reward.item == null) return ItemStack.EMPTY;
        try {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(reward.item));
            if (item == null) return new ItemStack(ModItems.RESEARCH_SCROLL.get());
            ItemStack stack = new ItemStack(item, Math.max(1, reward.count));
            if (reward.nbt != null && !reward.nbt.isBlank()) {
                try {
                    CompoundTag tag = TagParser.parseTag(reward.nbt);
                    stack.getOrCreateTag().merge(tag);
                } catch (Exception e) {
                    java.util.logging.Logger.getLogger("ResearchTable").warning("Invalid reward NBT for " + reward.item + ": " + e.getMessage());
                }
            }
            return stack;
        } catch (Exception e) {
            return new ItemStack(ModItems.RESEARCH_SCROLL.get());
        }
    }

    public int getProgress() { return progress; }
    public int getMaxProgress() { return maxProgress; }
    public boolean isResearching() { return researching; }
    public int getActiveResearchIndex() { return activeResearchIndex; }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // Load saved progress here (optional, if you serialize UUIDs and states)
        if (tag.contains("activeResearchIndex")) this.activeResearchIndex = tag.getInt("activeResearchIndex");
        if (tag.contains("progress")) this.progress = tag.getInt("progress");
        if (tag.contains("maxProgress")) this.maxProgress = tag.getInt("maxProgress");
        if (tag.contains("researching")) this.researching = tag.getBoolean("researching");
        if (tag.contains("finished")) this.finished = tag.getBoolean("finished");
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        // Save researchProgress to NBT (optional)
        tag.putInt("activeResearchIndex", this.activeResearchIndex);
        tag.putInt("progress", this.progress);
        tag.putInt("maxProgress", this.maxProgress);
        tag.putBoolean("researching", this.researching);
        tag.putBoolean("finished", this.finished);
    }

    // Return shared ResearchState values for a player; client can map to client-side enum where needed
    public Map<Integer, ResearchState> getPlayerResearchStates(Player p) {
        Map<Integer, ResearchState> out = new HashMap<>();
        Map<Integer, ResearchState> serverStates = ResearchProgressManager.getStatesForPlayer(p.getUUID());
        if (serverStates != null && !serverStates.isEmpty()) {
            out.putAll(serverStates);
        }
        return out;
    }

    private CompoundTag createResearchSnapshot() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("activeResearchIndex", this.activeResearchIndex);
        tag.putInt("progress", this.progress);
        tag.putInt("maxProgress", this.maxProgress);
        tag.putBoolean("researching", this.researching);
        tag.putBoolean("finished", this.finished);
        return tag;
    }

    public void persistResearchGlobally() {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        ResearchSavedData data = ResearchSavedData.get(serverLevel);
        CompoundTag snapshot = createResearchSnapshot();
        data.setSavedResearch(snapshot);
    }

    public void loadPersistedResearch() {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        ResearchSavedData data = ResearchSavedData.get(serverLevel);
        CompoundTag snapshot = data.getSavedResearch();
        if (snapshot != null) {
            restoreResearchState(snapshot);
            data.clearSavedResearch();
            syncProgressToClients();
        }
    }

    public static final class HologramRenderData {
        private final ItemStack stack;
        private final float alpha;

        public HologramRenderData(ItemStack stack, float alpha) {
            this.stack = stack;
            this.alpha = alpha;
        }

        public ItemStack stack() { return stack; }
        public float alpha() { return alpha; }
    }
 }
