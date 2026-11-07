package com.gtnewhorizons.modularnuclear.common.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.modularnuclear.common.metatileentity.pipe.MTECoolantPipe;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;

/**
 * The housing a control rod withdraws into: up to five extend in a line from a control rod hatch's front face, each
 * letting the rod come 20 % further out of the core. A plain block, with no tile entity and no ticking. Its metadata is
 * the axis it runs along, and it looks like a small steel coolant pipe running straight through the block along it.
 * <p>
 * It is a GregTech machine block: placing, breaking or rotating one starts GregTech's machine update, which reaches the
 * reactor through the housings, the hatch and the casings, and makes the reactor check its structure again. That
 * check is when the reactor counts the housings.
 */
public class BlockControlRodHousing extends Block {

    /** Metadata: the axis the housing runs along. */
    public static final int AXIS_Y = 0, AXIS_X = 1, AXIS_Z = 2;

    /** Its look: a small steel coolant pipe. */
    public static final Materials MATERIAL = Materials.Steel;
    public static final float THICKNESS = 0.375F;

    /** Set by the client's renderer when it registers. */
    public static int renderID = 0;

    public BlockControlRodHousing() {
        super(Material.iron);
        setBlockName("modularnuclear.control_rod_housing");
        setHardness(5.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("wrench", 1);
        setCreativeTab(CreativeTabs.tabBlock);
    }

    public static int axisOf(ForgeDirection direction) {
        if (direction.offsetX != 0) return AXIS_X;
        if (direction.offsetZ != 0) return AXIS_Z;
        return AXIS_Y;
    }

    /** True when a housing with this metadata runs along the direction. */
    public static boolean runsAlong(int meta, ForgeDirection direction) {
        return meta == axisOf(direction);
    }

    @Override
    public int onBlockPlaced(World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ, int meta) {
        return axisOf(ForgeDirection.getOrientation(side));
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        GregTechAPI.causeMachineUpdate(world, x, y, z);
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        super.breakBlock(world, x, y, z, block, meta);
        GregTechAPI.causeMachineUpdate(world, x, y, z);
    }

    @Override
    public ForgeDirection[] getValidRotations(World world, int x, int y, int z) {
        return ForgeDirection.VALID_DIRECTIONS;
    }

    /** Turns the housing to run along the axis of the wrenched direction. */
    @Override
    public boolean rotateBlock(World world, int x, int y, int z, ForgeDirection axis) {
        int meta = axisOf(axis);
        if (world.getBlockMetadata(x, y, z) == meta) return false;
        world.setBlockMetadataWithNotify(x, y, z, meta, 3);
        GregTechAPI.causeMachineUpdate(world, x, y, z);
        return true;
    }

    @Override
    public int damageDropped(int meta) {
        return 0;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean isNormalCube(IBlockAccess world, int x, int y, int z) {
        return false;
    }

    @Override
    public boolean isSideSolid(IBlockAccess world, int x, int y, int z, ForgeDirection side) {
        return false;
    }

    @Override
    public int getRenderType() {
        return renderID;
    }

    /** The pipe's cross-section, running the full length of the block along the axis. */
    public static float[] bounds(int meta) {
        float min = (1.0F - THICKNESS) / 2.0F, max = 1.0F - min;
        return switch (meta) {
            case AXIS_X -> new float[] { 0.0F, min, min, 1.0F, max, max };
            case AXIS_Z -> new float[] { min, min, 0.0F, max, max, 1.0F };
            default -> new float[] { min, 0.0F, min, max, 1.0F, max };
        };
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        float[] b = bounds(world.getBlockMetadata(x, y, z));
        setBlockBounds(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    @Override
    public void setBlockBoundsForItemRender() {
        float[] b = bounds(AXIS_Y);
        setBlockBounds(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        setBlockBoundsBasedOnState(world, x, y, z);
        return super.getCollisionBoundingBoxFromPool(world, x, y, z);
    }

    @Override
    public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) {
        setBlockBoundsBasedOnState(world, x, y, z);
        return super.getSelectedBoundingBoxFromPool(world, x, y, z);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB mask, List list,
        Entity entity) {
        setBlockBoundsBasedOnState(world, x, y, z);
        super.addCollisionBoxesToList(world, x, y, z, mask, list, entity);
    }

    /** The bezel cut from the pipe's four long edges, as on coolant pipes of the same size. */
    public static float bezel() {
        return MTECoolantPipe.getBezelSize(THICKNESS);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {}

    /** The pipe's side or end: GregTech's steel pipe icons, tinted by {@link #getRenderColor}. */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        boolean end = runsAlong(meta, ForgeDirection.getOrientation(side));
        OrePrefixes prefix = end ? OrePrefixes.pipeSmall : OrePrefixes.pipe;
        return MATERIAL.mIconSet.mTextures[prefix.getTextureIndex()].getIcon();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderColor(int meta) {
        short[] rgba = MATERIAL.mRGBa;
        return ((rgba[0] & 0xFF) << 16) | ((rgba[1] & 0xFF) << 8) | (rgba[2] & 0xFF);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int colorMultiplier(IBlockAccess world, int x, int y, int z) {
        return getRenderColor(0);
    }
}
