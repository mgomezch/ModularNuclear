package com.gtnewhorizons.modularnuclear.common.block;

import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;

import com.gtnewhorizons.modularnuclear.common.textures.ModularNuclearTextures;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.Textures;
import gregtech.api.render.TextureFactory;
import gregtech.common.blocks.BlockCasingsAbstract;
import gregtech.common.blocks.ItemCasings;

public class BlockNuclearCasing extends BlockCasingsAbstract {

    public static final int CASING_PAGE = 16;
    public static final int CASING_ID = 6;
    public static final int CASING_TEXTURE_INDEX = (CASING_PAGE << 7) | (CASING_ID + 112);

    public BlockNuclearCasing() {
        super(ItemCasings.class, "modularnuclear.casing", Material.iron);
        setHardness(5.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
        setCreativeTab(CreativeTabs.tabBlock);

        ModularNuclearTextures.init();
        Textures.BlockIcons.setCasingTexture(
            (byte) CASING_PAGE,
            (byte) (CASING_ID + 112),
            TextureFactory.of(ModularNuclearTextures.MACHINE_CASING_NUCLEAR));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        if (ModularNuclearTextures.MACHINE_CASING_NUCLEAR != null) {
            IIcon icon = ModularNuclearTextures.MACHINE_CASING_NUCLEAR.getIcon();
            if (icon != null) return icon;
        }
        return Blocks.iron_block.getIcon(side, 0);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister aIconRegister) {}

    @Override
    public int getTextureIndex(int aMeta) {
        return CASING_TEXTURE_INDEX;
    }
}
