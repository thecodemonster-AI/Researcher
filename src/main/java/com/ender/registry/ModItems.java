package com.ender.registry;

import com.ender.researcher.ResearchTable;
import com.ender.item.ResearchScrollItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import com.ender.registry.ModBlocks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ResearchTable.MODID);

    // Standard research scroll (kept for existing code compatibility)
    public static final RegistryObject<Item> RESEARCH_SCROLL = ITEMS.register("research_scroll",
            () -> new ResearchScrollItem(new Item.Properties()));

    // Register a BlockItem for the research table so it appears in the creative inventory
    public static final RegistryObject<Item> RESEARCH_TABLE_ITEM = ITEMS.register("research_table",
            () -> new BlockItem(ModBlocks.RESEARCH_TABLE.get(), new Item.Properties()));
    
    // Register the new parchment item
    public static final RegistryObject<Item> PARCHMENT = ITEMS.register("parchment",
            () -> new Item(new Item.Properties()));
}