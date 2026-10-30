package com.gtnewhorizons.modularnuclear;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.modularnuclear.client.CherenkovClientHandler;
import com.gtnewhorizons.modularnuclear.client.renderer.FalloutVisualEffectRenderer;
import com.gtnewhorizons.modularnuclear.client.renderer.RenderEmpty;
import com.gtnewhorizons.modularnuclear.common.CommonProxy;
import com.gtnewhorizons.modularnuclear.common.block.CoolantLoopBlocks;
import com.gtnewhorizons.modularnuclear.common.block.ModBlocks;
import com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig;
import com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry;
import com.gtnewhorizons.modularnuclear.common.entity.EntityMeltdownFallout;
import com.gtnewhorizons.modularnuclear.common.event.CoriumPlacementHandler;
import com.gtnewhorizons.modularnuclear.common.fluid.ModFluids;
import com.gtnewhorizons.modularnuclear.common.item.ModItems;
import com.gtnewhorizons.modularnuclear.common.item.NuclearFuelTooltipHandler;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.nei.NEIConfig;
import com.gtnewhorizons.modularnuclear.common.nuclearcontrol.NuclearControlIntegration;
import com.gtnewhorizons.modularnuclear.common.opencomputers.OpenComputersIntegration;
import com.gtnewhorizons.modularnuclear.common.projectred.ProjectRedIntegration;
import com.gtnewhorizons.modularnuclear.common.quest.ModularNuclearQuestIntegration;
import com.gtnewhorizons.modularnuclear.common.recipe.CheeseRecipeRegistry;
import com.gtnewhorizons.modularnuclear.common.recipe.ModRecipes;
import com.gtnewhorizons.modularnuclear.common.textures.ModularNuclearTextures;
import com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification;
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
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModularNuclearConfig.init(event.getSuggestedConfigurationFile());
        ReactorDataRegistry.init(event.getModConfigurationDirectory());
        ModularNuclearTextures.init();
        ModFluids.init();
        ModBlocks.init();
        ModItems.init();
        CoolantLoops.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ModMetaTileEntities.init();
        ModFluids.registerContainers();
        CoolantLoops.init(event);
        NuclearControlIntegration.init();
        OpenComputersIntegration.init();
        ProjectRedIntegration.init();
        ModularNuclearQuestIntegration.init();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new NuclearFuelTooltipHandler());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new CoriumPlacementHandler());
        net.minecraftforge.common.ForgeChunkManager
            .setForcedChunkLoadingCallback(this, new EntityMeltdownFallout.MeltdownChunkLoadingCallback());

        cpw.mods.fml.common.registry.EntityRegistry.registerModEntity(
            EntityMeltdownFallout.class,
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
                    new NEIConfig().loadConfig();
                } catch (Throwable swallowed) {
                    Swallowed.note(swallowed);
                }
            }
        }
    }

    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    private void registerClientRenderers() {
        cpw.mods.fml.client.registry.RenderingRegistry
            .registerEntityRenderingHandler(EntityMeltdownFallout.class, new RenderEmpty());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new CherenkovClientHandler());
        FalloutVisualEffectRenderer.init();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        ModRecipes.init();
        CoolantLoops.postInit(event);
        CheeseRecipeRegistry.getInstance()
            .loadRecipes();
        ReactorDataRegistry.preIndexKnownItems();
        NuclearFuelClassification.preIndexKeys();
    }

    @Mod.EventHandler
    public void onMissingMappings(cpw.mods.fml.common.event.FMLMissingMappingsEvent event) {
        for (cpw.mods.fml.common.event.FMLMissingMappingsEvent.MissingMapping mapping : event.getAll()) {
            if ("coolantloops:manifold".equals(mapping.name)) {
                if (mapping.type == cpw.mods.fml.common.registry.GameRegistry.Type.BLOCK) {
                    mapping.remap(CoolantLoopBlocks.manifold);
                } else if (mapping.type == cpw.mods.fml.common.registry.GameRegistry.Type.ITEM) {
                    mapping.remap(net.minecraft.item.Item.getItemFromBlock(CoolantLoopBlocks.manifold));
                }
            } else if ("coolantloops:creative_heat_reservoir".equals(mapping.name)) {
                if (mapping.type == cpw.mods.fml.common.registry.GameRegistry.Type.BLOCK) {
                    mapping.remap(CoolantLoopBlocks.creativeHeatReservoir);
                } else if (mapping.type == cpw.mods.fml.common.registry.GameRegistry.Type.ITEM) {
                    mapping.remap(net.minecraft.item.Item.getItemFromBlock(CoolantLoopBlocks.creativeHeatReservoir));
                }
            } else if ("coolantloops:loop_instrument".equals(mapping.name)) {
                if (mapping.type == cpw.mods.fml.common.registry.GameRegistry.Type.BLOCK) {
                    mapping.remap(CoolantLoopBlocks.loopInstrument);
                } else if (mapping.type == cpw.mods.fml.common.registry.GameRegistry.Type.ITEM) {
                    mapping.remap(net.minecraft.item.Item.getItemFromBlock(CoolantLoopBlocks.loopInstrument));
                }
            }
        }
    }
}
