package com.gtnewhorizons.modularnuclear;

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
        com.gtnewhorizons.modularnuclear.common.config.CoolantLoopDataRegistry
            .init(event.getModConfigurationDirectory());
        com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty.setDeclaredTemperatureSource(name -> {
            net.minecraftforge.fluids.Fluid f = net.minecraftforge.fluids.FluidRegistry.getFluid(name);
            return f != null ? f.getTemperature() : 0;
        });
        com.gtnewhorizons.modularnuclear.common.block.CoolantLoopBlocks.init();

        network = cpw.mods.fml.common.network.NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        network.registerMessage(
            com.gtnewhorizons.modularnuclear.network.PacketSyncCoolantBath.Handler.class,
            com.gtnewhorizons.modularnuclear.network.PacketSyncCoolantBath.class,
            0,
            cpw.mods.fml.relauncher.Side.CLIENT);
        network.registerMessage(
            com.gtnewhorizons.modularnuclear.network.PacketEjectCoolantBathSlot.Handler.class,
            com.gtnewhorizons.modularnuclear.network.PacketEjectCoolantBathSlot.class,
            1,
            cpw.mods.fml.relauncher.Side.SERVER);
    }

    public static void init(FMLInitializationEvent event) {
        LOG.info("Initializing Coolant Loops...");
        com.gtnewhorizons.modularnuclear.common.metatileentity.CoolantStructureChannels.register();
        com.gtnewhorizons.modularnuclear.common.metatileentity.CoolantLoopMetaTileEntities.init();
        com.gtnewhorizons.modularnuclear.common.opencomputers.CoolantLoopOpenComputers.register();
        com.gtnewhorizons.modularnuclear.common.projectred.CoolantLoopProjectRed.register();

        if (cpw.mods.fml.common.FMLCommonHandler.instance()
            .getSide()
            .isClient()) {
            com.gtnewhorizons.modularnuclear.client.renderer.RenderBlockManifold.init();
            com.gtnewhorizons.modularnuclear.client.renderer.ManifoldHighlightRenderer manifoldHighlight = new com.gtnewhorizons.modularnuclear.client.renderer.ManifoldHighlightRenderer();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(manifoldHighlight);
            cpw.mods.fml.common.FMLCommonHandler.instance()
                .bus()
                .register(manifoldHighlight);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS
                .register(new com.gtnewhorizons.modularnuclear.client.tooltip.RotorTooltipEventHandler());
            net.minecraftforge.common.MinecraftForge.EVENT_BUS
                .register(new com.gtnewhorizons.modularnuclear.client.renderer.CoolantBathRenderer());
            com.gtnewhorizons.modularnuclear.common.gui.FloatingPanelNEIHandler.register();
        }
    }

    public static void postInit(FMLPostInitializationEvent event) {
        LOG.info("Post-initializing Coolant Loops...");
        com.gtnewhorizons.modularnuclear.common.recipe.CoolantLoopRecipes.init();
    }
}
