package com.gtnewhorizons.modularnuclear.common.item;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.fluid.ModFluids;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.IGT_ItemWithMaterialRenderer;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.util.GTUtil;
import gregtech.common.render.items.GeneratedMaterialRenderer;
import gregtech.common.render.items.MetaGeneratedItemRenderer;

/**
 * Cells of the heavy-water steams, made as GregTech makes cells for fluids that have no material
 * (gregtech.common.items.MetaGeneratedItem98): GregTech's own cell icon with the fluid drawn inside, so they look like
 * every other fluid's cell and the mod ships no texture of its own for them.
 */
public class ItemSteamCell extends Item implements IGT_ItemWithMaterialRenderer {

    /** The cells, by meta. A cell's meta must never change (worlds keep it); a removed cell's meta is not reused. */
    public enum Cell {

        HEAVY_WATER_STEAM(0, () -> ModFluids.fluidHeavyWaterSteam),
        SUPERHEATED_HEAVY_WATER_STEAM(1, () -> ModFluids.fluidSuperheatedHeavyWaterSteam),
        SUPERCRITICAL_HEAVY_WATER_STEAM(2, () -> ModFluids.fluidSupercriticalHeavyWaterSteam);

        public final int meta;
        private final Supplier<Fluid> fluid;

        Cell(int meta, Supplier<Fluid> fluid) {
            this.meta = meta;
            this.fluid = fluid;
        }

        public Fluid fluid() {
            return fluid.get();
        }

        static Cell byMeta(int meta) {
            for (Cell cell : values()) if (cell.meta == meta) return cell;
            return null;
        }
    }

    private static final int CAPACITY = 1000;

    /** GregTech's cell icons (Water's: every fluid cell's is the same). */
    private IIconContainer icons;

    public ItemSteamCell() {
        setUnlocalizedName("modularnuclear.steamCell");
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(64);
        setCreativeTab(CreativeTabs.tabMaterials);
        // GregTech drops its cell icons from the icon sets later in loading (see MetaGeneratedItem98.preInit)
        icons = cellIcons();
    }

    private static IIconContainer cellIcons() {
        try {
            return Materials.Water.mIconSet.mTextures[OrePrefixes.cell.getTextureIndex()];
        } catch (Throwable missing) {
            return null;
        }
    }

    private IIconContainer icons() {
        if (icons == null) icons = cellIcons();
        return icons;
    }

    public ItemStack get(Cell cell, int amount) {
        return new ItemStack(this, amount, cell.meta);
    }

    /** Each cell holds 1000 L of its fluid and empties into a plain empty cell, as GregTech's cells do. */
    public void registerContainers() {
        ItemStack empty = ItemList.Cell_Empty.get(1);
        if (empty == null) return;
        for (Cell cell : Cell.values()) {
            Fluid fluid = cell.fluid();
            if (fluid == null) continue;
            FluidContainerRegistry.registerFluidContainer(
                new FluidContainerRegistry.FluidContainerData(
                    new FluidStack(fluid, CAPACITY),
                    get(cell, 1),
                    empty.copy()));
        }
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        Cell cell = Cell.byMeta(stack.getItemDamage());
        Fluid fluid = cell != null ? cell.fluid() : null;
        if (fluid == null) return super.getItemStackDisplayName(stack);
        return StatCollector.translateToLocalFormatted(
            OrePrefixes.cell.getOreprefixKey(),
            fluid.getLocalizedName(new FluidStack(fluid, CAPACITY)));
    }

    @Override
    public boolean hasContainerItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getContainerItem(ItemStack stack) {
        return ItemList.Cell_Empty.get(1);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (Cell cell : Cell.values()) if (cell.fluid() != null) list.add(get(cell, 1));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        // GregTech registers the cell icons
    }

    @Override
    public IIcon getIconFromDamage(int meta) {
        return getIcon(meta, 0);
    }

    // GregTech's material renderer draws the cell, the fluid inside it, and the cell's overlay

    @Override
    public boolean shouldUseCustomRenderer(int meta) {
        return true;
    }

    @Override
    public GeneratedMaterialRenderer getMaterialRenderer(int meta) {
        return null;
    }

    @Override
    public boolean allowMaterialRenderer(int meta) {
        return true;
    }

    @Override
    public IIcon getIcon(int meta, int pass) {
        IIconContainer icons = icons();
        return icons != null ? icons.getIcon() : null;
    }

    @Override
    public IIcon getOverlayIcon(int meta, int pass) {
        IIconContainer icons = icons();
        return icons != null ? icons.getOverlayIcon() : null;
    }

    @Override
    public short[] getRGBa(ItemStack stack) {
        Cell cell = Cell.byMeta(stack.getItemDamage());
        Fluid fluid = cell != null ? cell.fluid() : null;
        return GTUtil.getRGBaArray(fluid != null ? fluid.getColor() : 0xFFFFFF);
    }

    @SideOnly(Side.CLIENT)
    public void registerRenderer() {
        new MetaGeneratedItemRenderer().registerItem(this);
    }
}
