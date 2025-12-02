package com.ender.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.ender.blockentity.ResearchTableBlockEntity;
import com.ender.researcher.ResearchState;
import com.ender.network.ModNetworking;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.server.level.ServerLevel;
import com.ender.researcher.ResearchProgressManager;
import com.ender.researcher.ResearchLogger;

public class ClaimResearchPacket {
    private final BlockPos pos;
    private final int categoryIndex;
    public ClaimResearchPacket(BlockPos pos, int categoryIndex) { this.pos = pos; this.categoryIndex = categoryIndex; }
    public static void encode(ClaimResearchPacket pkt, FriendlyByteBuf buf) { buf.writeBlockPos(pkt.pos); buf.writeInt(pkt.categoryIndex); }
    public static ClaimResearchPacket decode(FriendlyByteBuf buf) { return new ClaimResearchPacket(buf.readBlockPos(), buf.readInt()); }
    public static void handle(ClaimResearchPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Ensure logging initialized
            ResearchLogger.init();

            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Level level = player.level();
            BlockEntity te = level.getBlockEntity(pkt.pos);
            if (te instanceof ResearchTableBlockEntity rtb) {
                try {
                    boolean claimedAny = false;

                    // Prefer claiming directly from the table so the hologram can be cleared immediately.
                    if (rtb.isFinished()) {
                        var rewards = rtb.takeResults();
                        if (!rewards.isEmpty()) {
                            for (ItemStack stack : rewards) {
                                boolean added = player.getInventory().add(stack);
                                if (!added) player.drop(stack, false);
                            }
                            rtb.clearServerHologram();
                            rtb.resetProgress();
                            ResearchProgressManager.clearPendingForPlayer(player.getUUID(), pkt.categoryIndex);
                            java.util.logging.Logger.getLogger("ClaimResearchPacket").info("Player " + player.getUUID() + " claimed research idx=" + pkt.categoryIndex + " directly from table at " + pkt.pos);
                            com.ender.researcher.ResearchFileLogger.log("Player " + player.getUUID() + " claimed research idx=" + pkt.categoryIndex + " directly from table at " + pkt.pos);
                            if (player.level() instanceof ServerLevel sl) {
                                ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> sl.getChunkAt(pkt.pos)),
                                        new ProgressUpdatePacket(pkt.pos, rtb.getProgress(), rtb.getMaxProgress(), rtb.isResearching(), rtb.isFinished(), rtb.getActiveResearchIndex()));
                            }
                            claimedAny = true;
                        }
                    }

                    // If nothing was available on the table (perhaps after a restart), fall back to persisted pending rewards.
                    if (!claimedAny && ResearchProgressManager.hasPendingReward(player.getUUID(), pkt.categoryIndex)) {
                        var pendingList = ResearchProgressManager.takePendingRewards(player.getUUID(), pkt.categoryIndex);
                        if (!pendingList.isEmpty()) {
                            for (ItemStack reward : pendingList) {
                                boolean added = player.getInventory().add(reward);
                                if (!added) player.drop(reward, false);
                            }
                            java.util.logging.Logger.getLogger("ClaimResearchPacket").info("Player " + player.getUUID() + " claimed pending reward idx=" + pkt.categoryIndex);
                            com.ender.researcher.ResearchFileLogger.log("Player " + player.getUUID() + " claimed pending reward idx=" + pkt.categoryIndex);
                            claimedAny = true;
                        }
                    }

                    if (!claimedAny) return;

                    ResearchState st = ResearchProgressManager.getState(player.getUUID(), pkt.categoryIndex);
                    if (st != ResearchState.COMPLETE_UNCLAIMED && st != ResearchState.COMPLETE_CLAIMED) {
                        return;
                    }

                    ResearchProgressManager.setState(player.getUUID(), pkt.categoryIndex, ResearchState.COMPLETE_CLAIMED);
                    if (level instanceof ServerLevel sl) ResearchProgressManager.saveStateToLevel(sl, player.getUUID());
                    ModNetworking.sendResearchState(player, pkt.pos, pkt.categoryIndex, ResearchState.COMPLETE_CLAIMED.ordinal());
                    return;
                } catch (Exception ex) {
                    // ignore and fallthrough
                }
             }
         });
        ctx.get().setPacketHandled(true);
    }
 }