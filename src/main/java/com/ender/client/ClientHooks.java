package com.ender.client;

import com.ender.blockentity.ResearchTableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Client-only entry points invoked via DistExecutor to avoid loading client classes on servers.
 */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openResearchTableScreen(ResearchTableBlockEntity be, Player player) {
        if (Minecraft.getInstance().screen instanceof ResearchTableScreen current && current.getBlockEntity() == be) {
            return;
        }
        Minecraft.getInstance().setScreen(new ResearchTableScreen(be, player));
    }
}