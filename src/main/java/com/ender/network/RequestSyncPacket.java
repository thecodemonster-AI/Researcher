package com.ender.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.ender.blockentity.ResearchTableBlockEntity;
import net.minecraftforge.network.PacketDistributor;
import java.util.Map;
import java.util.Map.Entry;
import com.ender.researcher.ResearchProgressManager;
import com.ender.researcher.ResearchState;
import net.minecraft.server.level.ServerLevel;
import com.ender.researcher.ResearchManager;

public class RequestSyncPacket {
    private final BlockPos pos;
    public RequestSyncPacket(BlockPos pos) { this.pos = pos; }
    public static void encode(RequestSyncPacket pkt, FriendlyByteBuf buf) { buf.writeBlockPos(pkt.pos); }
    public static RequestSyncPacket decode(FriendlyByteBuf buf) { return new RequestSyncPacket(buf.readBlockPos()); }
    public static void handle(RequestSyncPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Level level = player.level();
            BlockEntity te = level.getBlockEntity(pkt.pos);
            if (te instanceof ResearchTableBlockEntity rtb) {
                String json = ResearchManager.getSerializedJson();
                if (json != null && !json.isBlank()) {
                    ModNetworking.sendResearchConfig(player, json);
                }
                // record that this player has opened the table at this position, so server can target packets to the open screen
                ResearchProgressManager.setOpenTablePos(player.getUUID(), pkt.pos);

                // Ensure per-world saved states are loaded into memory for this player
                if (level instanceof ServerLevel sl) {
                    ResearchProgressManager.loadStatesFromLevel(sl, player.getUUID());
                }

                // send current progress/status to the requesting player
                ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new ProgressUpdatePacket(pkt.pos, rtb.getProgress(), rtb.getMaxProgress(), rtb.isResearching(), rtb.isFinished(), rtb.getActiveResearchIndex()));

                // send per-category ResearchState for this player from the world-aware manager
                Map<Integer, ResearchState> states = ResearchProgressManager.getStatesForPlayer(player.getUUID());
                for (Entry<Integer, ResearchState> e : states.entrySet()) {
                    ModNetworking.sendResearchState(player, pkt.pos, e.getKey(), e.getValue().ordinal());
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}