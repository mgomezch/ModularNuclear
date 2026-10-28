package com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure;

import java.util.function.Consumer;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.modularnuclear.common.metatileentity.CoolantStructureChannels;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTEPressurizedHeatExchanger;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTStructureUtility;

/**
 * Structure definition and helpers for the Pressurized Heat Exchanger (PHE).
 * Supports variable height (3, 5, 7, 9) and variable midsection length (3 to 9).
 * Center layer y = height / 2 contains the controller, sheetmetal, and primary/secondary hatches.
 */
public final class PressurizedHeatExchangerStructure {

    public static final int[] VALID_HEIGHTS = { 3, 5, 7, 9 };
    public static final int MIN_HEIGHT = 3;
    public static final int MAX_HEIGHT = 9;
    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 9;

    private static IStructureDefinition<MTEPressurizedHeatExchanger> STRUCTURE_DEFINITION = null;

    private PressurizedHeatExchangerStructure() {}

    public static String getPieceName(int height, int length) {
        return "exchanger_h" + height + "_l" + length;
    }

    public static int getMinCasings(int height, int length) {
        return (length + 6) * (height - 1) - 3;
    }

    public static String[] buildLayer(int y, int height, int length) {
        String[] rows = new String[length + 2];
        boolean isCenter = (y == height / 2);

        if (isCenter) {
            rows[0] = "L~H";
            for (int r = 1; r <= length; r++) {
                rows[r] = "lch";
            }
            rows[length + 1] = "LxH";
        } else {
            rows[0] = "xxx";
            for (int r = 1; r <= length; r++) {
                rows[r] = "lxh";
            }
            rows[length + 1] = "xxx";
        }

        return rows;
    }

    public static String[][] buildShape(int height, int length) {
        String[][] shape = new String[height][];
        for (int y = 0; y < height; y++) {
            shape[y] = buildLayer(y, height, length);
        }
        return shape;
    }

    public static IStructureDefinition<MTEPressurizedHeatExchanger> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            StructureDefinition.Builder<MTEPressurizedHeatExchanger> builder = StructureDefinition
                .<MTEPressurizedHeatExchanger>builder();

            for (int h : VALID_HEIGHTS) {
                for (int l = MIN_LENGTH; l <= MAX_LENGTH; l++) {
                    builder.addShape(getPieceName(h, l), StructureUtility.transpose(buildShape(h, l)));
                }
            }

            // Legacy alias for 3x3x3 midsection (height 3, length 3)
            builder.addShape("exchanger_3x5x3", StructureUtility.transpose(buildShape(3, 3)));

            builder
                .addElement(
                    'x',
                    StructureUtility.ofChain(
                        GTStructureUtility.ofHatchAdder(
                            MTEPressurizedHeatExchanger::addMaintenanceOrEnergyHatch,
                            MTEPressurizedHeatExchanger.CASING_INDEX_HEAT_PROOF,
                            1),
                        StructureUtility.onElementPass(
                            MTEPressurizedHeatExchanger::addCasingBlock,
                            StructureUtility.ofBlock(GregTechAPI.sBlockCasings1, 11))))
                .addElement('h', StructureUtility.ofBlock(GregTechAPI.sBlockCasings10, 10))
                .addElement('l', StructureUtility.ofBlock(GregTechAPI.sBlockCasings10, 9))
                .addElement('c', ofSheetMetalTiered())
                .addElement(
                    'H',
                    GTStructureUtility.ofHatchAdder(
                        MTEPressurizedHeatExchanger::addPressurizedHatch,
                        MTEPressurizedHeatExchanger.CASING_INDEX_HEAT_PROOF,
                        2))
                .addElement(
                    'L',
                    GTStructureUtility.ofHatchAdder(
                        MTEPressurizedHeatExchanger::addSecondaryHatch,
                        MTEPressurizedHeatExchanger.CASING_INDEX_HEAT_PROOF,
                        3));

            STRUCTURE_DEFINITION = builder.build();
        }
        return STRUCTURE_DEFINITION;
    }

    public static IStructureElement<MTEPressurizedHeatExchanger> ofSheetMetalTiered() {
        return new IStructureElement<MTEPressurizedHeatExchanger>() {

            private IStructureElement<MTEPressurizedHeatExchanger> getElement(ItemStack trigger) {
                PheSheetmetalTier tier = PheSheetmetalTier.fromTrigger(trigger);
                ItemStack stack = tier.getItemStack();
                Block block = (stack != null && stack.getItem() != null) ? Block.getBlockFromItem(stack.getItem())
                    : null;
                if (block == null) {
                    block = Blocks.stone;
                }
                int meta = (stack != null) ? stack.getItemDamage() : 0;
                return StructureUtility.ofBlock(block, meta);
            }

            @Override
            public boolean check(MTEPressurizedHeatExchanger t, World world, int x, int y, int z) {
                Block block = world.getBlock(x, y, z);
                int meta = world.getBlockMetadata(x, y, z);
                PheSheetmetalTier tier = PheSheetmetalTier.fromBlock(block, meta);
                if (tier == null) {
                    return false;
                }
                t.recordSheetmetalBlock(tier);
                return true;
            }

            @Override
            public boolean spawnHint(MTEPressurizedHeatExchanger t, World world, int x, int y, int z,
                ItemStack trigger) {
                return getElement(trigger).spawnHint(t, world, x, y, z, trigger);
            }

            @Override
            public boolean placeBlock(MTEPressurizedHeatExchanger t, World world, int x, int y, int z,
                ItemStack trigger) {
                return getElement(trigger).placeBlock(t, world, x, y, z, trigger);
            }

            @Override
            public PlaceResult survivalPlaceBlock(MTEPressurizedHeatExchanger t, World world, int x, int y, int z,
                ItemStack trigger, IItemSource s, EntityPlayerMP actor, Consumer<IChatComponent> chatter) {
                return getElement(trigger).survivalPlaceBlock(t, world, x, y, z, trigger, s, actor, chatter);
            }

            @Override
            public PlaceResult survivalPlaceBlock(MTEPressurizedHeatExchanger t, World world, int x, int y, int z,
                ItemStack trigger, AutoPlaceEnvironment env) {
                return getElement(trigger).survivalPlaceBlock(t, world, x, y, z, trigger, env);
            }

            @Override
            public BlocksToPlace getBlocksToPlace(MTEPressurizedHeatExchanger t, World world, int x, int y, int z,
                ItemStack trigger, AutoPlaceEnvironment env) {
                return getElement(trigger).getBlocksToPlace(t, world, x, y, z, trigger, env);
            }
        };
    }

    public static int getHeightFromTrigger(ItemStack trigger) {
        if (trigger == null) return 3;
        int val = CoolantStructureChannels.PHE_HEIGHT.getValueClamped(trigger, 3, 9);
        switch (val) {
            case 3:
                return 3;
            case 5:
                return 5;
            case 7:
                return 7;
            case 9:
                return 9;
            default:
                if (val <= 3) return 3;
                if (val <= 5) return 5;
                if (val <= 7) return 7;
                return 9;
        }
    }

    public static int getLengthFromTrigger(ItemStack trigger) {
        if (trigger == null) return 3;
        return CoolantStructureChannels.PHE_LENGTH.getValueClamped(trigger, 3, 9);
    }

    public static int[] detectDimensions(IGregTechTileEntity te) {
        if (te == null) return new int[] { 3, 3 };
        World world = te.getWorld();
        if (world == null) return new int[] { 3, 3 };
        ForgeDirection front = te.getFrontFacing();
        ForgeDirection back = (front != null && front != ForgeDirection.UNKNOWN) ? front.getOpposite()
            : ForgeDirection.NORTH;
        int x = te.getXCoord();
        int y = te.getYCoord();
        int z = te.getZCoord();

        // 1. Detect length: scan line behind controller in the center layer
        int detectedLength = 0;
        for (int step = 1; step <= 10; step++) {
            int bx = x + back.offsetX * step;
            int by = y + back.offsetY * step;
            int bz = z + back.offsetZ * step;
            Block b = world.getBlock(bx, by, bz);
            int meta = world.getBlockMetadata(bx, by, bz);
            PheSheetmetalTier tier = PheSheetmetalTier.fromBlock(b, meta);
            if (tier != null) {
                detectedLength++;
            } else {
                break;
            }
        }
        if (detectedLength < MIN_LENGTH) detectedLength = MIN_LENGTH;
        if (detectedLength > MAX_LENGTH) detectedLength = MAX_LENGTH;

        // 2. Detect height: scan symmetric offset above and below controller
        int maxDy = 1;
        for (int dy = 1; dy <= 4; dy++) {
            if (!world.isAirBlock(x, y + dy, z) && !world.isAirBlock(x, y - dy, z)) {
                maxDy = dy;
            } else {
                int mx = x + back.offsetX;
                int mz = z + back.offsetZ;
                if (!world.isAirBlock(mx, y + dy, mz) && !world.isAirBlock(mx, y - dy, mz)) {
                    maxDy = dy;
                } else {
                    break;
                }
            }
        }
        int detectedHeight = 2 * maxDy + 1;
        if (detectedHeight < MIN_HEIGHT) detectedHeight = MIN_HEIGHT;
        if (detectedHeight > MAX_HEIGHT) detectedHeight = MAX_HEIGHT;

        return new int[] { detectedHeight, detectedLength };
    }
}
