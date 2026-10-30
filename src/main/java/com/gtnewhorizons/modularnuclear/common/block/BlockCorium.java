package com.gtnewhorizons.modularnuclear.common.block;

import java.util.Locale;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.init.Blocks;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fluids.BlockFluidClassic;
import net.minecraftforge.fluids.Fluid;

import com.gtnewhorizons.modularnuclear.common.util.Swallowed;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.hazards.HazardProtection;
import gregtech.api.util.GTUtility;
import ic2.core.IC2Potion;

public class BlockCorium extends BlockFluidClassic {

    public static final Material materialCorium = new Material(MapColor.tntColor) {

        {
            setNoPushMobility();
            setBurning();
        }

        @Override
        public boolean isLiquid() {
            return true;
        }

        @Override
        public boolean blocksMovement() {
            return false;
        }

        @Override
        public boolean isSolid() {
            return false;
        }
    };

    public BlockCorium(Fluid fluid) {
        super(fluid, materialCorium);
        setBlockName("modularnuclear.corium");
        setLightLevel(1.0F); // Maximum luminosity (15)
        setTickRate(180); // 6 times slower than lava (lava is 30 ticks)
        setHardness(100.0F);
        setResistance(500.0F);
        setCreativeTab(CreativeTabs.tabBlock);
        setTickRandomly(true);
    }

    @Override
    public void updateTick(World world, int x, int y, int z, java.util.Random rand) {
        if (!world.isRemote && isRandomTick()) {
            solidifyCoriumBlock(world, x, y, z);
            return;
        }

        super.updateTick(world, x, y, z, rand);
    }

    public static void solidifyCoriumBlock(World world, int x, int y, int z) {
        if (world == null || world.isRemote) return;
        Block current = world.getBlock(x, y, z);
        if (current != ModBlocks.blockCorium) return;
        int meta = world.getBlockMetadata(x, y, z);
        if (meta != 0) {
            Block belowBlock = world.getBlock(x, y - 1, z);
            int belowMeta = world.getBlockMetadata(x, y - 1, z);
            if (belowBlock == ModBlocks.blockCorium && belowMeta != 0) {
                world.setBlock(x, y - 1, z, ModBlocks.blockCorium, 0, 3);
            }
        }
        world.setBlock(x, y, z, ModBlocks.blockCoriumSolid, 0, 3);
        world.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, "random.fizz", 0.5F, 2.6F);
    }

    private static boolean isRandomTick() {
        StackTraceElement[] stack = Thread.currentThread()
            .getStackTrace();
        for (int i = 2; i < Math.min(stack.length, 6); i++) {
            String m = stack[i].getMethodName();
            if (m.equals("tickBlocksAndAmbiance") || m.equals("func_147456_g")) {
                return true;
            }
        }
        return false;
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
        // Bedrock, solid corium, and unbreakable blocks
        if (block == Blocks.bedrock || block == ModBlocks.blockCoriumSolid
            || block.getBlockHardness(null, 0, 0, 0) < 0) {
            return true;
        }

        // Warded blocks (e.g. Thaumcraft Warded Glass)
        try {
            GameRegistry.UniqueIdentifier uid = GameRegistry.findUniqueIdentifierFor(block);
            if (uid != null) {
                String modId = uid.modId.toLowerCase(Locale.ENGLISH);
                String name = uid.name.toLowerCase(Locale.ENGLISH);
                if (modId.equals("thaumcraft")) {
                    if (name.equals("blockwarded") || name.equals("blockwardedglass")
                        || name.equals("blockwardedstone")) {
                        return true;
                    }
                    if (name.equals("blockcosmeticopaque")) {
                        int meta = world != null ? world.getBlockMetadata(x, y, z) : 0;
                        if (meta == 2) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable swallowed) {
            Swallowed.note(swallowed);
        }

        String unloc = block.getUnlocalizedName();
        if (unloc != null) {
            String lower = unloc.toLowerCase(Locale.ENGLISH);
            if (lower.equals("tile.blockwarded") || lower.equals("tile.blockwardedglass")
                || lower.equals("tile.wardedglass")
                || lower.equals("tile.thaumcraft:blockwarded")) {
                return true;
            }
            if (lower.equals("tile.blockcosmeticopaque")) {
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
    public boolean isReplaceable(IBlockAccess world, int x, int y, int z) {
        return false;
    }

    @Override
    public void onEntityCollidedWithBlock(World world, int x, int y, int z, Entity entity) {
        super.onEntityCollidedWithBlock(world, x, y, z, entity);
        if (entity != null) {
            if (entity instanceof EntityFallingBlock) {
                entity.setDead();
                return;
            }

            entity.setInWeb();
            entity.motionX *= 0.5D;
            entity.motionZ *= 0.5D;
            if (entity.motionY > 0) {
                entity.motionY *= 0.25D;
            }

            entity.setFire(30);
            entity.attackEntityFrom(DamageSource.lava, 10.0F);

            if (entity instanceof EntityLivingBase living) {
                if (Potion.moveSlowdown != null) {
                    living.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 40, 2, true));
                }
                if (!HazardProtection.isWearingFullRadioHazmat(living)) {
                    GTUtility.applyRadioactivity(living, 5, 20);
                    if (Loader.isModLoaded("IC2") && IC2Potion.radiation != null) {
                        living.addPotionEffect(new PotionEffect(IC2Potion.radiation.id, 240, 3));
                    }
                }
            }
        }
    }
}
