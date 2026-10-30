package com.gtnewhorizons.modularnuclear;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.modularnuclear.client.renderer.CoolantBathRenderer;
import com.gtnewhorizons.modularnuclear.client.renderer.ManifoldHighlightRenderer;
import com.gtnewhorizons.modularnuclear.client.renderer.RenderBlockManifold;
import com.gtnewhorizons.modularnuclear.client.tooltip.RotorTooltipEventHandler;
import com.gtnewhorizons.modularnuclear.common.block.CoolantLoopBlocks;
import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopDataRegistry;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.gui.FloatingPanelNEIHandler;
import com.gtnewhorizons.modularnuclear.common.metatileentity.CoolantLoopMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.metatileentity.CoolantStructureChannels;
import com.gtnewhorizons.modularnuclear.common.opencomputers.CoolantLoopOpenComputers;
import com.gtnewhorizons.modularnuclear.common.projectred.CoolantLoopProjectRed;
import com.gtnewhorizons.modularnuclear.common.recipe.CoolantLoopRecipes;
import com.gtnewhorizons.modularnuclear.network.PacketEjectCoolantBathSlot;
import com.gtnewhorizons.modularnuclear.network.PacketSyncCoolantBath;

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
        CoolantLoopDataRegistry.init(event.getModConfigurationDirectory());
        CoolantFluidProperty.setDeclaredTemperatureSource(name -> {
            net.minecraftforge.fluids.Fluid f = net.minecraftforge.fluids.FluidRegistry.getFluid(name);
            return f != null ? f.getTemperature() : 0;
        });
        CoolantLoopBlocks.init();

        network = cpw.mods.fml.common.network.NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        network.registerMessage(
            PacketSyncCoolantBath.Handler.class,
            PacketSyncCoolantBath.class,
            0,
            cpw.mods.fml.relauncher.Side.CLIENT);
        network.registerMessage(
            PacketEjectCoolantBathSlot.Handler.class,
            PacketEjectCoolantBathSlot.class,
            1,
            cpw.mods.fml.relauncher.Side.SERVER);
    }

    public static void init(FMLInitializationEvent event) {
        LOG.info("Initializing Coolant Loops...");
        CoolantStructureChannels.register();
        CoolantLoopMetaTileEntities.init();
        CoolantLoopOpenComputers.register();
        CoolantLoopProjectRed.register();

        if (cpw.mods.fml.common.FMLCommonHandler.instance()
            .getSide()
            .isClient()) {
            RenderBlockManifold.init();
            ManifoldHighlightRenderer manifoldHighlight = new ManifoldHighlightRenderer();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(manifoldHighlight);
            cpw.mods.fml.common.FMLCommonHandler.instance()
                .bus()
                .register(manifoldHighlight);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new RotorTooltipEventHandler());
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new CoolantBathRenderer());
            FloatingPanelNEIHandler.register();
        }
    }

    public static void postInit(FMLPostInitializationEvent event) {
        LOG.info("Post-initializing Coolant Loops...");
        CoolantLoopRecipes.init();
    }
}
