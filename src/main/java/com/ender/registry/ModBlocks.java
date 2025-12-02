package com.ender.registry;

import com.ender.researcher.ResearchTable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import com.ender.researcher.ResearchTableBlock;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ResearchTable.MODID);

    // Placeholder block registration; replace with your actual block registration logic
    public static final RegistryObject<Block> RESEARCH_TABLE = BLOCKS.register("researchtable",
            () -> new ResearchTableBlock(BlockBehaviour.Properties.of().strength(2.5f)));
}