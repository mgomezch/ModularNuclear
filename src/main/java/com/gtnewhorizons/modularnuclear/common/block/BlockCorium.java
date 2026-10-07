package com.gtnewhorizons.modularnuclear.common.block;

import java.util.Locale;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fluids.BlockFluidClassic;
import net.minecraftforge.fluids.Fluid;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.hazards.HazardProtection;
import gregtech.api.util.GTUtility;
import ic2.core.IC2Potion;

public class BlockCorium extends BlockFluidClassic {

    public BlockCorium(Fluid fluid) {
        super(fluid, Material.lava);
        setBlockName("modularnuclear.corium");
        setLightLevel(1.0F); // Maximum luminosity (15)
        setTickRate(90); // 3 times slower than lava (lava is 30 ticks)
        setHardness(100.0F);
        setResistance(500.0F);
        setCreativeTab(CreativeTabs.tabBlock);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        IIcon still = register.registerIcon("modularnuclear:fluids/corium_still");
        IIcon flow = register.registerIcon("modularnuclear:fluids/corium_flow");
        if (this.getFluid() != null) {
            this.getFluid()
                .setIcons(still, flow);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        if (this.getFluid() == null) {
            return super.getIcon(side, meta);
        }
        return (side == 0 || side == 1) ? this.getFluid()
            .getStillIcon()
            : this.getFluid()
                .getFlowingIcon();
    }

    public static boolean isImmuneToCorium(Block block, IBlockAccess world, int x, int y, int z) {
        if (block == null || block == Blocks.air) {
            return false;
        }
        // Bedrock and unbreakable blocks
        if (block == Blocks.bedrock || block.getBlockHardness(null, 0, 0, 0) < 0) {
            return true;
        }

        // Warded blocks (e.g. Thaumcraft Warded Glass)
        try {
            GameRegistry.UniqueIdentifier uid = GameRegistry.findUniqueIdentifierFor(block);
            if (uid != null) {
                String modId = uid.modId.toLowerCase(Locale.ENGLISH);
                String name = uid.name.toLowerCase(Locale.ENGLISH);
                if (name.contains("warded")) {
                    return true;
                }
                if (modId.equals("thaumcraft") && name.equals("blockcosmeticopaque")) {
                    int meta = world != null ? world.getBlockMetadata(x, y, z) : 0;
                    if (meta == 2) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}

        String unloc = block.getUnlocalizedName();
        if (unloc != null) {
            String lower = unloc.toLowerCase(Locale.ENGLISH);
            if (lower.contains("warded")) {
                return true;
            }
            if (lower.contains("blockcosmeticopaque")) {
                int meta = world != null ? world.getBlockMetadata(x, y, z) : 0;
                if (meta == 2) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean canDisplace(IBlockAccess world, int x, int y, int z) {
        if (world.getBlock(x, y, z)
            .isAir(world, x, y, z)) {
            return true;
        }
        Block block = world.getBlock(x, y, z);
        if (block == this) {
            return false;
        }
        if (isImmuneToCorium(block, world, x, y, z)) {
            return false;
        }
        return true;
    }

    @Override
    protected boolean canFlowInto(IBlockAccess world, int x, int y, int z) {
        if (world.getBlock(x, y, z)
            .isAir(world, x, y, z)) {
            return true;
        }
        Block block = world.getBlock(x, y, z);
        if (block == this) {
            return true;
        }
        if (isImmuneToCorium(block, world, x, y, z)) {
            return false;
        }
        return true;
    }

    @Override
    public boolean displaceIfPossible(World world, int x, int y, int z) {
        if (world.getBlock(x, y, z)
            .isAir(world, x, y, z)) {
            return true;
        }
        Block block = world.getBlock(x, y, z);
        if (block == this) {
            return false;
        }
        if (isImmuneToCorium(block, world, x, y, z)) {
            return false;
        }

        world.setBlockToAir(x, y, z);
        return true;
    }

    @Override
    public void onEntityCollidedWithBlock(World world, int x, int y, int z, Entity entity) {
        super.onEntityCollidedWithBlock(world, x, y, z, entity);
        entity.setFire(30);
        entity.attackEntityFrom(DamageSource.lava, 10.0F);

        if (entity instanceof EntityLivingBase living) {
            if (!HazardProtection.isWearingFullRadioHazmat(living)) {
                GTUtility.applyRadioactivity(living, 5, 20);
                if (Loader.isModLoaded("IC2") && IC2Potion.radiation != null) {
                    living.addPotionEffect(new PotionEffect(IC2Potion.radiation.id, 240, 3));
                }
            }
        }
    }
}
