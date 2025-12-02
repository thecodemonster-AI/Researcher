package com.ender.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.ender.blockentity.ResearchTableBlockEntity;
import com.ender.researcher.ResearchState;
import com.ender.client.ClientResearchProgressManager;

public class ResearchStatePacket {
    private final BlockPos pos;
    private final int categoryIndex;
    private final int stateOrdinal;

    public ResearchStatePacket(BlockPos pos, int categoryIndex, int stateOrdinal) { this.pos = pos; this.categoryIndex = categoryIndex; this.stateOrdinal = stateOrdinal; }
    public static void encode(ResearchStatePacket pkt, FriendlyByteBuf buf) { buf.writeBlockPos(pkt.pos); buf.writeInt(pkt.categoryIndex); buf.writeInt(pkt.stateOrdinal); }
    public static ResearchStatePacket decode(FriendlyByteBuf buf) { return new ResearchStatePacket(buf.readBlockPos(), buf.readInt(), buf.readInt()); }
    public static void handle(ResearchStatePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return;
            // Convert ordinal to enum safely
            int clamped = Math.max(0, Math.min(pkt.stateOrdinal, ResearchState.values().length - 1));
            // Convert to client-side enum and update client manager
            com.ender.client.ResearchState clientState = com.ender.client.ResearchState.values()[clamped];
            if (Minecraft.getInstance().player != null) {
                ClientResearchProgressManager.setState(Minecraft.getInstance().player.getUUID(), pkt.categoryIndex, clientState);
            }
            // Also update BE if present so any open screens reading BE are refreshed
            BlockEntity te = level.getBlockEntity(pkt.pos);
            if (te instanceof ResearchTableBlockEntity rtb) {
                // mark changed so UI polling updates
                rtb.setChanged();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}