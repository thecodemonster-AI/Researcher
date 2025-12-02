package com.ender.researcher;

import com.ender.registry.ModBlocks;
import com.ender.registry.ModBlockEntities;
import com.ender.registry.ModItems;
import com.ender.network.ModNetworking;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("researcher")
public class ResearchTable {
    public static final String MODID = "researcher";

    public ResearchTable() {
        try {
            java.nio.file.Path configDir = EnvPathResolver.getConfigDir();
            java.nio.file.Files.createDirectories(configDir);
            java.nio.file.Path logsDir = EnvPathResolver.getLogsDir();
            java.nio.file.Files.createDirectories(logsDir);
        } catch (Exception ignored) {}
        // Ensure default config exists so servers inherit consistent research data
        ResearchManager.loadDefaults();
        // Initialize research-specific file logging
        ResearchLogger.init();
        // Direct fallback write so the research.log file exists when the mod loads
        com.ender.researcher.ResearchFileLogger.log("Research mod loaded");

        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register common deferred registers
        ModBlocks.BLOCKS.register(bus);
        ModItems.ITEMS.register(bus);
        ModBlockEntities.BLOCK_ENTITIES.register(bus);

        // Register networking
        ModNetworking.register();

        // Load persisted pending rewards so they survive server restarts
        ResearchProgressManager.loadPendingFromDisk();

        // Client events are handled in com.ender.researchtable.client.ClientModEvents via @EventBusSubscriber(Dist.CLIENT)
    }
}