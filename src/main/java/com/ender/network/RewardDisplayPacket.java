package com.ender.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import com.ender.blockentity.ResearchTableBlockEntity;

public class RewardDisplayPacket {
    private final BlockPos pos;
    private final List<ItemStack> items;

    public RewardDisplayPacket(BlockPos pos, List<ItemStack> items) {
        this.pos = pos;
        this.items = items;
    }

    public static void encode(RewardDisplayPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeVarInt(packet.items.size());
        for (ItemStack stack : packet.items) {
            buf.writeItem(stack);
        }
    }

    public static RewardDisplayPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int count = buf.readVarInt();
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(buf.readItem());
        }
        return new RewardDisplayPacket(pos, items);
    }

    public static void handle(RewardDisplayPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            BlockEntity be = mc.level.getBlockEntity(packet.pos);
            if (be instanceof ResearchTableBlockEntity table) {
                table.applyRewardDisplayFromServer(packet.items);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
