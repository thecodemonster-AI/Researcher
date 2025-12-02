package com.ender.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.ender.blockentity.ResearchTableBlockEntity;

public class ProgressUpdatePacket {
    private final BlockPos pos;
    private final int progress;
    private final int max;
    private final boolean researching;
    private final boolean finished;
    private final int activeIndex;

    public ProgressUpdatePacket(BlockPos pos, int progress, int max, boolean researching, boolean finished, int activeIndex) { this.pos = pos; this.progress = progress; this.max = max; this.researching = researching; this.finished = finished; this.activeIndex = activeIndex; }
    public static void encode(ProgressUpdatePacket pkt, FriendlyByteBuf buf) { buf.writeBlockPos(pkt.pos); buf.writeInt(pkt.progress); buf.writeInt(pkt.max); buf.writeBoolean(pkt.researching); buf.writeBoolean(pkt.finished); buf.writeInt(pkt.activeIndex); }
    public static ProgressUpdatePacket decode(FriendlyByteBuf buf) { return new ProgressUpdatePacket(buf.readBlockPos(), buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readBoolean(), buf.readInt()); }
    public static void handle(ProgressUpdatePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        // client-bound; apply update to client block entity
        ctx.get().enqueueWork(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return;
            BlockEntity te = level.getBlockEntity(pkt.pos);
            if (te instanceof ResearchTableBlockEntity rtb) {
                rtb.applyProgressFromServer(pkt.progress, pkt.max, pkt.researching, pkt.finished, pkt.activeIndex);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}