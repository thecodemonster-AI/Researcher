package com.ender.network;

import com.ender.researcher.ResearchTable;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;

public class ModNetworking {
    private static final String PROTOCOL_VERSION = "1";
    public static SimpleChannel CHANNEL;
    private static int id = 0;

    public static void register() {
        ResourceLocation name = new ResourceLocation(ResearchTable.MODID, "main");
        CHANNEL = NetworkRegistry.newSimpleChannel(name, () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

        // Register packets
        CHANNEL.messageBuilder(StartResearchPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(StartResearchPacket::encode)
                .decoder(StartResearchPacket::decode)
                .consumerNetworkThread(StartResearchPacket::handle)
                .add();

        CHANNEL.messageBuilder(RequestSyncPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestSyncPacket::encode)
                .decoder(RequestSyncPacket::decode)
                .consumerNetworkThread(RequestSyncPacket::handle)
                .add();

        CHANNEL.messageBuilder(ProgressUpdatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ProgressUpdatePacket::encode)
                .decoder(ProgressUpdatePacket::decode)
                .consumerNetworkThread(ProgressUpdatePacket::handle)
                .add();

        CHANNEL.messageBuilder(ResearchStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ResearchStatePacket::encode)
                .decoder(ResearchStatePacket::decode)
                .consumerNetworkThread(ResearchStatePacket::handle)
                .add();

        CHANNEL.messageBuilder(RewardDisplayPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RewardDisplayPacket::encode)
                .decoder(RewardDisplayPacket::decode)
                .consumerNetworkThread(RewardDisplayPacket::handle)
                .add();

        CHANNEL.messageBuilder(StartFailedPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StartFailedPacket::encode)
                .decoder(StartFailedPacket::decode)
                .consumerNetworkThread(StartFailedPacket::handle)
                .add();

        CHANNEL.messageBuilder(ClaimResearchPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClaimResearchPacket::encode)
                .decoder(ClaimResearchPacket::decode)
                .consumerNetworkThread(ClaimResearchPacket::handle)
                .add();

        CHANNEL.messageBuilder(ConfigSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ConfigSyncPacket::encode)
                .decoder(ConfigSyncPacket::decode)
                .consumerNetworkThread(ConfigSyncPacket::handle)
                .add();
    }

    public static void sendResearchState(ServerPlayer player, BlockPos pos, int categoryIndex, int stateOrdinal) {
        if (CHANNEL == null || player == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ResearchStatePacket(pos, categoryIndex, stateOrdinal));
    }

    public static void sendResearchConfig(ServerPlayer player, String json) {
        if (CHANNEL == null || player == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ConfigSyncPacket(json));
    }

    public static void broadcastResearchConfig(Iterable<ServerPlayer> players, String json) {
        if (CHANNEL == null || players == null) return;
        ConfigSyncPacket packet = new ConfigSyncPacket(json);
        for (ServerPlayer player : players) {
            if (player == null) continue;
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }
}