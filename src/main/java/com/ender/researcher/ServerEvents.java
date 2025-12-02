package com.ender.researcher;

import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class ServerEvents {
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Flush pending rewards synchronously to disk
        ResearchProgressManager.saveAllSync();
    }
}
