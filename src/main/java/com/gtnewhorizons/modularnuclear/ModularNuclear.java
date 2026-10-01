package com.gtnewhorizons.modularnuclear;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.modularnuclear.common.block.ModBlocks;
import com.gtnewhorizons.modularnuclear.common.fluid.ModFluids;
import com.gtnewhorizons.modularnuclear.common.item.ModItems;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.recipe.ModRecipes;
import com.gtnewhorizons.modularnuclear.common.textures.ModularNuclearTextures;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = ModularNuclear.MODID,
    name = ModularNuclear.MODNAME,
    version = Tags.VERSION,
    dependencies = "required-after:gregtech")
public class ModularNuclear {

    public static final String MODID = "modularnuclear";
    public static final String MODNAME = "Modular Nuclear";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @Mod.Instance(MODID)
    public static ModularNuclear instance;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModularNuclearTextures.init();
        ModFluids.init();
        ModBlocks.init();
        ModItems.init();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ModMetaTileEntities.init();
        ModFluids.registerContainers();
        com.gtnewhorizons.modularnuclear.common.nuclearcontrol.NuclearControlIntegration.init();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS
            .register(new com.gtnewhorizons.modularnuclear.common.item.NuclearFuelTooltipHandler());
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        ModRecipes.init();
    }
}
