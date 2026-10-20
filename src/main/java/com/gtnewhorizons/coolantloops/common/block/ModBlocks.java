package com.gtnewhorizons.coolantloops.common.block;

import net.minecraft.block.Block;

import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityCreativeHeatReservoir;

import cpw.mods.fml.common.registry.GameRegistry;

public class ModBlocks {

    public static Block creativeHeatReservoir;
    public static Block manifold;
    public static Block loopInstrument;

    public static void init() {
        creativeHeatReservoir = new BlockCreativeHeatReservoir();
        GameRegistry.registerBlock(creativeHeatReservoir, "creative_heat_reservoir");
        GameRegistry.registerTileEntity(TileEntityCreativeHeatReservoir.class, "coolantloops.creative_heat_reservoir");
        GameRegistry
            .registerTileEntity(TileEntityCreativeHeatReservoir.class, "modularnuclear.creative_heat_reservoir");

        manifold = new BlockManifold();
        GameRegistry.registerBlock(manifold, "manifold");
        GameRegistry.registerTileEntity(
            com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold.class,
            "coolantloops.manifold");
        GameRegistry.registerTileEntity(
            com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold.class,
            "modularnuclear.manifold");

        loopInstrument = new BlockLoopInstrument();
        GameRegistry.registerBlock(loopInstrument, "loop_instrument");
        GameRegistry.registerTileEntity(
            com.gtnewhorizons.coolantloops.common.tileentity.TileEntityLoopInstrument.class,
            "coolantloops.loop_instrument");
        GameRegistry.registerTileEntity(
            com.gtnewhorizons.coolantloops.common.tileentity.TileEntityLoopInstrument.class,
            "modularnuclear.loop_instrument");

        registerAliases();
    }

    private static void registerAliases() {
        addAlias("coolantloops:creative_heat_reservoir", "modularnuclear:creative_heat_reservoir");
        addAlias("coolantloops:manifold", "modularnuclear:manifold");
        addAlias("coolantloops:loop_instrument", "modularnuclear:loop_instrument");
    }

    private static void addAlias(String from, String to) {
        addAliasToRegistry(cpw.mods.fml.common.registry.GameData.getBlockRegistry(), from, to);
        addAliasToRegistry(net.minecraft.block.Block.blockRegistry, from, to);
        addAliasToRegistry(cpw.mods.fml.common.registry.GameData.getItemRegistry(), from, to);
        addAliasToRegistry(net.minecraft.item.Item.itemRegistry, from, to);
    }

    private static void addAliasToRegistry(Object registry, String from, String to) {
        if (registry == null) return;
        Class<?> cur = registry.getClass();
        while (cur != null && cur != Object.class) {
            try {
                java.lang.reflect.Method m = cur.getDeclaredMethod("addAlias", String.class, String.class);
                m.setAccessible(true);
                m.invoke(registry, from, to);
                return;
            } catch (NoSuchMethodException e) {
                cur = cur.getSuperclass();
            } catch (Throwable t) {
                break;
            }
        }
        try {
            java.lang.reflect.Field f = cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry.class
                .getDeclaredField("aliases");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> map = (java.util.Map<String, String>) f.get(registry);
            if (map != null) {
                map.put(from, to);
            }
        } catch (Throwable ignored) {}
    }
}
