package com.ender.registry;

import com.ender.researcher.ResearchTable;
import com.ender.blockentity.ResearchTableBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Central place to register BlockEntityTypes.
 */
public class ModBlockEntities {
    // DeferredRegister for block entity types using your mod id
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            // use literal mod id string because a main mod class with MODID constant wasn't found
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ResearchTable.MODID);

    // Register a BlockEntityType for the research table.
    // The builder takes a factory (constructor reference) and the blocks that use this block entity.
    public static final RegistryObject<BlockEntityType<ResearchTableBlockEntity>> RESEARCH_TABLE =
            BLOCK_ENTITIES.register("researchtable",
                    () -> BlockEntityType.Builder.of(ResearchTableBlockEntity::new,
                            ModBlocks.RESEARCH_TABLE.get() // reference to your registered block
                    ).build(null));
}