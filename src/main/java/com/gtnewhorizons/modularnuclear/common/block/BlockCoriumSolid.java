package com.gtnewhorizons.modularnuclear.common.block;

import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.util.GTUtility;

public class BlockCoriumSolid extends Block {

    public static final float CORIUM_HARDNESS = 2500.0F; // 50x obsidian (50.0F)
    public static final float CORIUM_RESISTANCE = 6000.0F;
    public static final int RADIATION_RADIUS = 5;

    public BlockCoriumSolid() {
        super(Material.rock);
        setBlockName("modularnuclear.corium_solid");
        setBlockTextureName("modularnuclear:corium_solid");
        setHardness(CORIUM_HARDNESS);
        setResistance(CORIUM_RESISTANCE);
        setHarvestLevel("pickaxe", 3);
        setStepSound(soundTypeStone);
        setCreativeTab(CreativeTabs.tabBlock);
        setTickRandomly(true);
    }

    @Override
    public int tickRate(World world) {
        return 20;
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        world.scheduleBlockUpdate(x, y, z, this, tickRate(world));
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        world.scheduleBlockUpdate(x, y, z, this, tickRate(world));
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        super.onNeighborBlockChange(world, x, y, z, neighbor);
        world.scheduleBlockUpdate(x, y, z, this, tickRate(world));
    }

    @Override
    public void updateTick(World world, int x, int y, int z, Random rand) {
        if (!world.isRemote) {
            AxisAlignedBB aabb = AxisAlignedBB.getBoundingBox(
                x - RADIATION_RADIUS,
                y - RADIATION_RADIUS,
                z - RADIATION_RADIUS,
                x + RADIATION_RADIUS + 1,
                y + RADIATION_RADIUS + 1,
                z + RADIATION_RADIUS + 1);
            @SuppressWarnings("unchecked")
            List<EntityLivingBase> entities = world.getEntitiesWithinAABB(EntityLivingBase.class, aabb);
            for (EntityLivingBase living : entities) {
                GTUtility.applyRadioactivity(living, 4, 1);
            }
            world.scheduleBlockUpdate(x, y, z, this, tickRate(world));
        }
    }

    @Override
    public void onEntityCollidedWithBlock(World world, int x, int y, int z, Entity entity) {
        if (!world.isRemote && entity instanceof EntityLivingBase living) {
            GTUtility.applyRadioactivity(living, 4, 1);
        }
    }

    @Override
    public Item getItemDropped(int meta, Random rand, int fortune) {
        return Item.getItemFromBlock(this);
    }

    @Override
    public int quantityDropped(Random rand) {
        return 1;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World world, int x, int y, int z, Random rand) {
        if (rand.nextInt(3) == 0) {
            world.spawnParticle("smoke", x + rand.nextFloat(), y + 1.05F, z + rand.nextFloat(), 0.0, 0.02, 0.0);
        }
        if (rand.nextInt(4) == 0) {
            world.spawnParticle(
                "townaura",
                x + rand.nextFloat(),
                y + rand.nextFloat(),
                z + rand.nextFloat(),
                0.0,
                0.04,
                0.0);
        }
    }
}
