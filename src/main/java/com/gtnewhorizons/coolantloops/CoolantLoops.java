package com.gtnewhorizons.coolantloops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class CoolantLoops {

    public static final String MODID = "coolantloops";
    public static final String MODNAME = "Coolant Loops";
    public static final Logger LOG = LogManager.getLogger(MODID);

    public static cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper network;

    public static void preInit(FMLPreInitializationEvent event) {
        LOG.info("Pre-initializing Coolant Loops thermodynamic engine...");
        com.gtnewhorizons.coolantloops.common.config.CoolantLoopDataRegistry.init(event.getModConfigurationDirectory());
        com.gtnewhorizons.coolantloops.common.block.ModBlocks.init();

        network = cpw.mods.fml.common.network.NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        network.registerMessage(
            com.gtnewhorizons.coolantloops.network.PacketSyncCoolantBath.Handler.class,
            com.gtnewhorizons.coolantloops.network.PacketSyncCoolantBath.class,
            0,
            cpw.mods.fml.relauncher.Side.CLIENT);
        network.registerMessage(
            com.gtnewhorizons.coolantloops.network.PacketEjectCoolantBathSlot.Handler.class,
            com.gtnewhorizons.coolantloops.network.PacketEjectCoolantBathSlot.class,
            1,
            cpw.mods.fml.relauncher.Side.SERVER);
    }

    public static void init(FMLInitializationEvent event) {
        LOG.info("Initializing Coolant Loops...");
        com.gtnewhorizons.coolantloops.common.metatileentity.CoolantStructureChannels.register();
        com.gtnewhorizons.coolantloops.common.metatileentity.ModMetaTileEntities.init();
        com.gtnewhorizons.coolantloops.common.opencomputers.OpenComputersIntegration.register();
        com.gtnewhorizons.coolantloops.common.projectred.ProjectRedIntegration.register();

        if (cpw.mods.fml.common.FMLCommonHandler.instance()
            .getSide()
            .isClient()) {
            com.gtnewhorizons.coolantloops.client.renderer.RenderBlockManifold.init();
            com.gtnewhorizons.coolantloops.client.renderer.ManifoldHighlightRenderer manifoldHighlight = new com.gtnewhorizons.coolantloops.client.renderer.ManifoldHighlightRenderer();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(manifoldHighlight);
            cpw.mods.fml.common.FMLCommonHandler.instance()
                .bus()
                .register(manifoldHighlight);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS
                .register(new com.gtnewhorizons.coolantloops.client.tooltip.RotorTooltipEventHandler());
            net.minecraftforge.common.MinecraftForge.EVENT_BUS
                .register(new com.gtnewhorizons.coolantloops.client.renderer.CoolantBathRenderer());
            com.gtnewhorizons.coolantloops.common.gui.FloatingPanelNEIHandler.register();
        }
    }

    public static void postInit(FMLPostInitializationEvent event) {
        LOG.info("Post-initializing Coolant Loops...");
        com.gtnewhorizons.coolantloops.common.recipe.ModRecipes.init();
    }
}
