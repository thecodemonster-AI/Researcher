package com.ender.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import com.ender.client.ResearchTableScreen;

public class StartFailedPacket {
    private final String reason;
    public StartFailedPacket(String reason) { this.reason = reason == null ? "Start failed" : reason; }
    public static void encode(StartFailedPacket pkt, FriendlyByteBuf buf) { buf.writeUtf(pkt.reason); }
    public static StartFailedPacket decode(FriendlyByteBuf buf) { return new StartFailedPacket(buf.readUtf(32767)); }
    public static void handle(StartFailedPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        // client-bound: show the authoritative failure message on the open ResearchTableScreen if present
        ctx.get().enqueueWork(() -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                Screen scr = mc.screen;
                if (scr instanceof ResearchTableScreen rts) {
                    rts.showStartFailedMessage(pkt.reason);
                } else {
                    // fallback: add as chat message if screen not open
                    mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(pkt.reason));
                }
            } catch (Exception e) {
                // ignore failures
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
