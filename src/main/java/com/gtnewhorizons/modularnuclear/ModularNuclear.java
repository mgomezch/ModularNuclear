package com.gtnewhorizons.modularnuclear;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.modularnuclear.common.block.ModBlocks;
import com.gtnewhorizons.modularnuclear.common.fluid.ModFluids;
import com.gtnewhorizons.modularnuclear.common.item.ModItems;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.recipe.ModRecipes;
import com.gtnewhorizons.modularnuclear.common.textures.ModularNuclearTextures;
import com.gtnewhorizons.modularnuclear.common.util.Swallowed;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = ModularNuclear.MODID,
    name = ModularNuclear.MODNAME,
    version = Tags.VERSION,
    dependencies = "required-after:gregtech;after:Railcraft;after:OpenComputers;after:ProjRed|Transmission;after:Nuclear-Control;after:NotEnoughItems")
public class ModularNuclear {

    public static final String MODID = "modularnuclear";
    public static final String MODNAME = "Modular Nuclear";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @Mod.Instance(MODID)
    public static ModularNuclear instance;

    @cpw.mods.fml.common.SidedProxy(
        clientSide = "com.gtnewhorizons.modularnuclear.client.ClientProxy",
        serverSide = "com.gtnewhorizons.modularnuclear.common.CommonProxy")
    public static com.gtnewhorizons.modularnuclear.common.CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.init(event.getSuggestedConfigurationFile());
        com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.init(event.getModConfigurationDirectory());
        ModularNuclearTextures.init();
        ModFluids.init();
        ModBlocks.init();
        ModItems.init();
        com.gtnewhorizons.modularnuclear.CoolantLoops.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ModMetaTileEntities.init();
        ModFluids.registerContainers();
        com.gtnewhorizons.modularnuclear.CoolantLoops.init(event);
        com.gtnewhorizons.modularnuclear.common.nuclearcontrol.NuclearControlIntegration.init();
        com.gtnewhorizons.modularnuclear.common.opencomputers.OpenComputersIntegration.init();
        com.gtnewhorizons.modularnuclear.common.projectred.ProjectRedIntegration.init();
        com.gtnewhorizons.modularnuclear.common.quest.ModularNuclearQuestIntegration.init();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS
            .register(new com.gtnewhorizons.modularnuclear.common.item.NuclearFuelTooltipHandler());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS
            .register(new com.gtnewhorizons.modularnuclear.common.event.CoriumPlacementHandler());
        net.minecraftforge.common.ForgeChunkManager.setForcedChunkLoadingCallback(
            this,
            new com.gtnewhorizons.modularnuclear.common.entity.EntityMeltdownFallout.MeltdownChunkLoadingCallback());

        cpw.mods.fml.common.registry.EntityRegistry.registerModEntity(
            com.gtnewhorizons.modularnuclear.common.entity.EntityMeltdownFallout.class,
            "MeltdownFallout",
            1,
            ModularNuclear.instance,
            160,
            20,
            false);

        if (cpw.mods.fml.common.FMLCommonHandler.instance()
            .getSide()
            .isClient()) {
            registerClientRenderers();
            proxy.registerNEIHandlerInfo();
            if (cpw.mods.fml.common.Loader.isModLoaded("NotEnoughItems")) {
                try {
                    new com.gtnewhorizons.modularnuclear.common.nei.NEIConfig().loadConfig();
                } catch (Throwable swallowed) {
                    Swallowed.note(swallowed);
                }
            }
        }
    }

    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    private void registerClientRenderers() {
        cpw.mods.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(
            com.gtnewhorizons.modularnuclear.common.entity.EntityMeltdownFallout.class,
            new com.gtnewhorizons.modularnuclear.client.renderer.RenderEmpty());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS
            .register(new com.gtnewhorizons.modularnuclear.client.renderer.CherenkovWorldRenderer());
        com.gtnewhorizons.modularnuclear.client.renderer.FalloutVisualEffectRenderer.init();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        ModRecipes.init();
        com.gtnewhorizons.modularnuclear.CoolantLoops.postInit(event);
        com.gtnewhorizons.modularnuclear.common.recipe.CheeseRecipeRegistry.getInstance()
            .loadRecipes();
        com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.preIndexKnownItems();
        com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.preIndexKeys();
    }

    @Mod.EventHandler
    public void onMissingMappings(cpw.mods.fml.common.event.FMLMissingMappingsEvent event) {
        for (cpw.mods.fml.common.event.FMLMissingMappingsEvent.MissingMapping mapping : event.getAll()) {
            if ("coolantloops:manifold".equals(mapping.name)) {
                if (mapping.type == cpw.mods.fml.common.registry.GameRegistry.Type.BLOCK) {
                    mapping.remap(com.gtnewhorizons.modularnuclear.common.block.CoolantLoopBlocks.manifold);
                } else if (mapping.type == cpw.mods.fml.common.registry.GameRegistry.Type.ITEM) {
                    mapping.remap(
                        net.minecraft.item.Item.getItemFromBlock(
                            com.gtnewhorizons.modularnuclear.common.block.CoolantLoopBlocks.manifold));
                }
            } else if ("coolantloops:creative_heat_reservoir".equals(mapping.name)) {
                if (mapping.type == cpw.mods.fml.common.registry.GameRegistry.Type.BLOCK) {
                    mapping
                        .remap(com.gtnewhorizons.modularnuclear.common.block.CoolantLoopBlocks.creativeHeatReservoir);
                } else if (mapping.type == cpw.mods.fml.common.registry.GameRegistry.Type.ITEM) {
                    mapping.remap(
                        net.minecraft.item.Item.getItemFromBlock(
                            com.gtnewhorizons.modularnuclear.common.block.CoolantLoopBlocks.creativeHeatReservoir));
                }
            } else if ("coolantloops:loop_instrument".equals(mapping.name)) {
                if (mapping.type == cpw.mods.fml.common.registry.GameRegistry.Type.BLOCK) {
                    mapping.remap(com.gtnewhorizons.modularnuclear.common.block.CoolantLoopBlocks.loopInstrument);
                } else if (mapping.type == cpw.mods.fml.common.registry.GameRegistry.Type.ITEM) {
                    mapping.remap(
                        net.minecraft.item.Item.getItemFromBlock(
                            com.gtnewhorizons.modularnuclear.common.block.CoolantLoopBlocks.loopInstrument));
                }
            }
        }
    }
}
