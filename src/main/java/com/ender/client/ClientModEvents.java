package com.ender.client;

import com.ender.registry.ModBlockEntities;
import com.ender.registry.ModItems;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(modid = "researcher", bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    private static final Logger LOGGER = LoggerFactory.getLogger("researcher-client");

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                    ModBlockEntities.RESEARCH_TABLE.get(), ResearchTableRenderer::new);
            LOGGER.info("Research table block entity type: {}", ModBlockEntities.RESEARCH_TABLE.get());
            LOGGER.info("Research table item present: {}", ModItems.RESEARCH_TABLE_ITEM.isPresent());
        });
    }

    // Note: creative tab population varies by mapping/API. If you want the block to appear in the creative menu,
    // use the /give command while testing: /give <yourname> researchtable:research_table
}