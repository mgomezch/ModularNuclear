package com.gtnewhorizons.modularnuclear.common.block;

import net.minecraft.block.Block;

import com.gtnewhorizons.modularnuclear.common.tileentity.TileEntityCreativeHeatReservoir;
import com.gtnewhorizons.modularnuclear.common.tileentity.TileEntityLoopInstrument;
import com.gtnewhorizons.modularnuclear.common.tileentity.TileEntityManifold;

import cpw.mods.fml.common.registry.GameRegistry;

public class CoolantLoopBlocks {

    public static Block creativeHeatReservoir;
    public static Block manifold;
    public static Block loopInstrument;

    public static void init() {
        creativeHeatReservoir = new BlockCreativeHeatReservoir();
        GameRegistry.registerBlock(creativeHeatReservoir, "creative_heat_reservoir");
        GameRegistry
            .registerTileEntity(TileEntityCreativeHeatReservoir.class, "modularnuclear.creative_heat_reservoir");

        manifold = new BlockManifold();
        GameRegistry.registerBlock(manifold, "manifold");
        GameRegistry.registerTileEntity(TileEntityManifold.class, "modularnuclear.manifold");

        loopInstrument = new BlockLoopInstrument();
        GameRegistry.registerBlock(loopInstrument, "loop_instrument");
        GameRegistry.registerTileEntity(TileEntityLoopInstrument.class, "modularnuclear.loop_instrument");
    }
}
