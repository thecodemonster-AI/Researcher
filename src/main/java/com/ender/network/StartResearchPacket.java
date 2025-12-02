package com.ender.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerLevel;
import com.ender.blockentity.ResearchTableBlockEntity;
import com.ender.researcher.ResearchState;
import com.ender.network.ModNetworking;
import net.minecraftforge.network.PacketDistributor;
import com.ender.researcher.ResearchManager;
import com.ender.researcher.ResearchProgressManager;
import com.ender.researcher.ResearchLogger;

public class StartResearchPacket {
    private final BlockPos pos;
    private final int categoryIndex;
    public StartResearchPacket(BlockPos pos, int categoryIndex) { this.pos = pos; this.categoryIndex = categoryIndex; }
    public static void encode(StartResearchPacket pkt, FriendlyByteBuf buf) { buf.writeBlockPos(pkt.pos); buf.writeInt(pkt.categoryIndex); }
    public static StartResearchPacket decode(FriendlyByteBuf buf) { return new StartResearchPacket(buf.readBlockPos(), buf.readInt()); }
    public static void handle(StartResearchPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Ensure logging is initialized
            ResearchLogger.init();

            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Level level = player.level();
            BlockEntity te = level.getBlockEntity(pkt.pos);
            // Log request arrival
            try {
                java.util.logging.Logger.getLogger("StartResearchPacket").info("StartResearch request from " + player.getUUID() + " idx=" + pkt.categoryIndex + " at pos=" + pkt.pos + "");
            } catch (Exception e) { /* ignore logging failures */ }
            // Fallback write
            com.ender.researcher.ResearchFileLogger.log("StartResearch request from " + player.getUUID() + " idx=" + pkt.categoryIndex + " at pos=" + pkt.pos);
            if (te instanceof ResearchTableBlockEntity rtb) {
                // Validate requested index against ResearchManager entries
                var entries = ResearchManager.getAll();
                if (pkt.categoryIndex < 0 || pkt.categoryIndex >= entries.size()) {
                    // invalid index - ignore
                    java.util.logging.Logger.getLogger("StartResearchPacket").warning("Invalid category index " + pkt.categoryIndex + " from " + player.getUUID());
                    // send authoritative failure reason
                    ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            new StartFailedPacket("Invalid research index"));
                    return;
                }
                // Enforce single active research per player: do not allow starting if any other category is IN_PROGRESS
                var states = ResearchProgressManager.getStatesForPlayer(player.getUUID());
                boolean foundInProgress = false;
                int foundIndex = -1;
                if (states != null) {
                    for (var entry : states.entrySet()) {
                        if (entry.getValue() == ResearchState.IN_PROGRESS) {
                            foundInProgress = true;
                            foundIndex = entry.getKey();
                            break;
                        }
                    }
                }
                if (foundInProgress) {
                    // If the BE at this position is actively researching the same index, block start.
                    if (te instanceof ResearchTableBlockEntity curRtb && curRtb.isResearching() && curRtb.getActiveResearchIndex() == foundIndex) {
                        // Player already has an active research; ignore this start request
                        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                                new ResearchStatePacket(pkt.pos, pkt.categoryIndex, ResearchState.NOT_STARTED.ordinal()));
                        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                                new StartFailedPacket("Another Research In Progress"));
                        java.util.logging.Logger.getLogger("StartResearchPacket").info("Player " + player.getUUID() + " already has IN_PROGRESS, rejecting start idx=" + pkt.categoryIndex);
                        com.ender.researcher.ResearchFileLogger.log("Player " + player.getUUID() + " already has IN_PROGRESS, rejecting start idx=" + pkt.categoryIndex);
                        return;
                    } else {
                        // No active BE at this pos matches the recorded IN_PROGRESS -> treat as stale state and clear it
                        java.util.logging.Logger.getLogger("StartResearchPacket").info("Clearing stale IN_PROGRESS state for player " + player.getUUID() + " idx=" + foundIndex);
                        com.ender.researcher.ResearchFileLogger.log("Clearing stale IN_PROGRESS state for player " + player.getUUID() + " idx=" + foundIndex);
                        ResearchProgressManager.setState(player.getUUID(), foundIndex, ResearchState.NOT_STARTED);
                        // persist change to world
                        if (level instanceof ServerLevel sl) {
                            ResearchProgressManager.saveStateToLevel(sl, player.getUUID());
                        }
                        // Notify the player client so their cached state is updated immediately
                        ModNetworking.sendResearchState(player, pkt.pos, foundIndex, ResearchState.NOT_STARTED.ordinal());
                        // continue processing start attempt
                    }
                }

                // If the block entity has a finished unclaimed result, resolve it before starting a new research.
                if (rtb.isFinished()) {
                    int finishedCategory = rtb.getActiveResearchIndex();
                    try {
                        var claimedList = rtb.takeResults();
                        if (claimedList.isEmpty()) {
                            claimedList = java.util.List.of(com.ender.registry.ModItems.RESEARCH_SCROLL.get().getDefaultInstance());
                        }
                        for (var e : ResearchProgressManager.getAll().entrySet()) {
                            var uuid = e.getKey();
                            if (ResearchProgressManager.getState(uuid, finishedCategory) == ResearchState.COMPLETE_UNCLAIMED) {
                                for (var stack : claimedList) {
                                    if (stack == null || stack.isEmpty()) continue;
                                    ResearchProgressManager.addPendingReward(uuid, finishedCategory, stack);
                                }
                                if (uuid.equals(player.getUUID())) {
                                    ModNetworking.sendResearchState(player, pkt.pos, finishedCategory, ResearchState.COMPLETE_UNCLAIMED.ordinal());
                                }
                            }
                        }

                        rtb.resetProgress();
                        if (player.level() instanceof ServerLevel sl) {
                            ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> sl.getChunkAt(pkt.pos)),
                                    new ProgressUpdatePacket(pkt.pos, rtb.getProgress(), rtb.getMaxProgress(), rtb.isResearching(), rtb.isFinished(), rtb.getActiveResearchIndex()));
                        }
                    } catch (Exception ex) {
                        java.util.logging.Logger.getLogger("StartResearchPacket").warning("Failed to auto-claim finished result: " + ex.getMessage());
                    }
                }

                // Mark the player's per-category state as IN_PROGRESS before attempting to start the BE.
                ResearchProgressManager.setState(player.getUUID(), pkt.categoryIndex, ResearchState.IN_PROGRESS);
                if (level instanceof ServerLevel sl2) {
                    ResearchProgressManager.saveStateToLevel(sl2, player.getUUID());
                }
                java.util.logging.Logger.getLogger("StartResearchPacket").info("Set player state to IN_PROGRESS for " + player.getUUID() + " idx=" + pkt.categoryIndex);
                com.ender.researcher.ResearchFileLogger.log("Set player state to IN_PROGRESS for " + player.getUUID() + " idx=" + pkt.categoryIndex);

                // Attempt to start the requested index on the BE.
                String reason = rtb.tryStartResearchWithReason(player, pkt.categoryIndex);
                boolean started = reason == null;
                if (started) {
                    // Send a per-player research-state packet back to the initiating player so their client UI updates
                    ModNetworking.sendResearchState(player, pkt.pos, pkt.categoryIndex, ResearchState.IN_PROGRESS.ordinal());
                    java.util.logging.Logger.getLogger("StartResearchPacket").info("Research actually started for " + player.getUUID() + " idx=" + pkt.categoryIndex);
                    com.ender.researcher.ResearchFileLogger.log("Research actually started for " + player.getUUID() + " idx=" + pkt.categoryIndex);
                } else {
                    // Revert the player's state to NOT_STARTED and ensure the client does not remain in an optimistic IN_PROGRESS state
                    ResearchProgressManager.setState(player.getUUID(), pkt.categoryIndex, ResearchState.NOT_STARTED);
                    if (level instanceof ServerLevel sl3) {
                        ResearchProgressManager.saveStateToLevel(sl3, player.getUUID());
                    }
                    ModNetworking.sendResearchState(player, pkt.pos, pkt.categoryIndex, ResearchState.NOT_STARTED.ordinal());
                    // Send authoritative failure reason to the client from the BE
                    String failMsg = reason != null ? reason : "Failed to start research";
                    ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            new StartFailedPacket(failMsg));
                    java.util.logging.Logger.getLogger("StartResearchPacket").warning("Failed to start research for " + player.getUUID() + " idx=" + pkt.categoryIndex + ", reverted state");
                    com.ender.researcher.ResearchFileLogger.log("Failed to start research for " + player.getUUID() + " idx=" + pkt.categoryIndex + ", reverted state");
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}