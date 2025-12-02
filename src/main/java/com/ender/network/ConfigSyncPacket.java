package com.ender.network;

import com.ender.researcher.ResearchManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ConfigSyncPacket {
    private final String json;

    public ConfigSyncPacket(String json) {
        this.json = json;
    }

    public static void encode(ConfigSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.json, 262144);
    }

    public static ConfigSyncPacket decode(FriendlyByteBuf buf) {
        return new ConfigSyncPacket(buf.readUtf(262144));
    }

    @OnlyIn(Dist.CLIENT)
    private static void applyClientConfig(String json) {
        ResearchManager.reloadFromJson(json);
    }

    public static void handle(ConfigSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance() != null) {
                applyClientConfig(packet.json);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}