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

    /** How a block kind resists corium, by name (see wardedKind); worked out once per block. */
    private static final int NOT_WARDED = 0, WARDED = 1, WARDED_AT_META_2 = 2;
    private static final java.util.Map<Block, Integer> WARDED_KIND = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean isImmuneToCorium(Block block, IBlockAccess world, int x, int y, int z) {
        if (block == null || block == Blocks.air) {
            return false;
        }
        // Bedrock, solid corium, and unbreakable blocks
        if (block == Blocks.bedrock || block == ModBlocks.blockCoriumSolid || isUnbreakable(block, world, x, y, z)) {
            return true;
        }

        // Warded blocks (e.g. Thaumcraft Warded Glass)
        Integer kind = WARDED_KIND.get(block);
        if (kind == null) {
            kind = wardedKind(block);
            WARDED_KIND.put(block, kind);
        }
        if (kind == WARDED) return true;
        return kind == WARDED_AT_META_2 && world != null && world.getBlockMetadata(x, y, z) == 2;
    }

    /**
     * Whether the block at the position cannot be broken. Hardness can depend on the block's tile entity (Railcraft
     * machines read it from the world), so it is asked of the real world and position; a block that cannot answer
     * counts as breakable.
     */
    private static boolean isUnbreakable(Block block, IBlockAccess world, int x, int y, int z) {
        try {
            return block.getBlockHardness(world instanceof World w ? w : null, x, y, z) < 0;
        } catch (RuntimeException e) {
            Swallowed.note(e);
            return false;
        }
    }

    /** Thaumcraft's warded blocks, by registry or unlocalized name. */
    private static int wardedKind(Block block) {
        try {
            GameRegistry.UniqueIdentifier uid = GameRegistry.findUniqueIdentifierFor(block);
            if (uid != null && uid.modId.toLowerCase(Locale.ENGLISH)
                .equals("thaumcraft")) {
                String name = uid.name.toLowerCase(Locale.ENGLISH);
                if (name.equals("blockwarded") || name.equals("blockwardedglass") || name.equals("blockwardedstone")) {
                    return WARDED;
                }
                if (name.equals("blockcosmeticopaque")) return WARDED_AT_META_2;
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
                return WARDED;
            }
            if (lower.equals("tile.blockcosmeticopaque")) return WARDED_AT_META_2;
        }
        return NOT_WARDED;
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
