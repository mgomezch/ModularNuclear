package com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure;

import net.minecraft.item.ItemStack;

import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.modularnuclear.common.metatileentity.CoolantStructureChannels;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTEPressurizedFluidHeater;
import com.gtnewhorizons.modularnuclear.common.util.Swallowed;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.util.GTStructureUtility;
import gregtech.api.util.HatchElementBuilder;
import gregtech.common.misc.GTStructureChannels;

/**
 * Structure definition for the Pressurized Fluid Heater multiblock.
 *
 * <ul>
 * <li>Width: 3, Height: 3, Overall Length: 3 to 11 (1 to 9 middle ring slices).</li>
 * <li>Front layer (Z=0): 3x3 Heat-Proof Machine Casings with Controller at front-bottom center (1, 2, 0) and
 * High-Pressure Input Hatch at front center (1, 1, 0).</li>
 * <li>Middle layers (Z=1..L-2): 3x3 ring of EBF Heating Coils around a center Item Pipe Casing (1, 1, Z).</li>
 * <li>Back layer (Z=L-1): 3x3 Heat-Proof Machine Casings with High-Pressure Output Hatch at back center (1, 1,
 * L-1).</li>
 * <li>Front and back Heat-Proof Machine Casings can be replaced by a Maintenance Hatch (exactly 1 required, any type)
 * and an Energy Hatch (single-amp or multi-amp, no laser allowed, exactly 1 required).</li>
 * </ul>
 */
public final class PressurizedFluidHeaterStructure {

    private PressurizedFluidHeaterStructure() {}

    public static final int WIDTH = 3;
    public static final int HEIGHT = 3;
    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 11;
    public static final int MIN_RINGS = 1;
    public static final int MAX_RINGS = 9;
    public static final int CASING_INDEX = 11; // Heat-Proof Machine Casing (sBlockCasings1 meta 11)

    private static IStructureDefinition<MTEPressurizedFluidHeater> STRUCTURE_DEFINITION = null;

    public static String getShapeName(int length) {
        return "heater_l" + clampLength(length);
    }

    public static int clampLength(int length) {
        return Math.max(MIN_LENGTH, Math.min(MAX_LENGTH, length));
    }

    public static int getRingCount(int length) {
        return clampLength(length) - 2;
    }

    /**
     * Minimum required Heat-Proof Machine Casings across the front and back 3x3 layers.
     * Total front+back positions = 18. Subtract 3 fixed positions (controller, HP input, HP output) = 15.
     * Subtract 2 required hatches (1 maintenance hatch + 1 energy hatch) = 13 casings.
     */
    public static int getMinCasings() {
        return 13;
    }

    public static int getLengthFromTrigger(ItemStack trigger) {
        if (trigger == null) return MIN_LENGTH;
        try {
            if (ChannelDataAccessor.hasSubChannel(trigger, CoolantStructureChannels.HEATER_LENGTH.get())) {
                int val = CoolantStructureChannels.HEATER_LENGTH.getValueClamped(trigger, MIN_LENGTH, MAX_LENGTH);
                return clampLength(val);
            }
        } catch (Throwable swallowed) {
            Swallowed.note(swallowed);
        }
        int size = trigger.stackSize;
        if (size >= 1 && size <= 9) {
            return clampLength(size + 2);
        }
        return MIN_LENGTH;
    }

    /**
     * Generates the 3x3xL structure shape array for a Pressurized Fluid Heater of overall length L (3 to 11).
     * Each element of the outer array is a Z slice from front (0) to back (L-1).
     * Each element of the inner array is a Y row from top (0) to bottom (2).
     */
    public static String[][] makeShape(int length) {
        int l = clampLength(length);
        String[][] slices = new String[l][HEIGHT];

        // Front slice (Z = 0): top "xxx", middle "xIx" (HP input), bottom "x~x" (controller)
        slices[0] = new String[] { "xxx", "xIx", "x~x" };

        // Middle slices (Z = 1 .. l - 2): outer ring of heating coils 'c', center item pipe casing 'p'
        for (int z = 1; z < l - 1; z++) {
            slices[z] = new String[] { "ccc", "cpc", "ccc" };
        }

        // Back slice (Z = l - 1): top "xxx", middle "xOx" (HP output), bottom "xxx"
        slices[l - 1] = new String[] { "xxx", "xOx", "xxx" };

        return slices;
    }

    @SuppressWarnings("unchecked")
    public static IStructureDefinition<MTEPressurizedFluidHeater> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            IStructureElement<MTEPressurizedFluidHeater> casing = StructureUtility
                .ofBlock(GregTechAPI.sBlockCasings1, CASING_INDEX);
            IStructureElement<MTEPressurizedFluidHeater> countedCasing = StructureUtility
                .onElementPass(t -> t.mCasingAmount++, casing);

            IStructureElement<MTEPressurizedFluidHeater> frontAndBackHatches = HatchElementBuilder
                .<MTEPressurizedFluidHeater>builder()
                .atLeast(
                    MTEPressurizedFluidHeater.HeaterHatchElement.Maintenance,
                    MTEPressurizedFluidHeater.HeaterHatchElement.Energy)
                .casingIndex(CASING_INDEX)
                .hint(1)
                .build();

            IStructureElement<MTEPressurizedFluidHeater> xElement = StructureUtility
                .ofChain(countedCasing, frontAndBackHatches);

            IStructureElement<MTEPressurizedFluidHeater> hpInputElement = HatchElementBuilder
                .<MTEPressurizedFluidHeater>builder()
                .atLeast(MTEPressurizedFluidHeater.HeaterHatchElement.PressurizedInput)
                .adder(MTEPressurizedFluidHeater::addPressurizedInputToMachineList)
                .hatchIds(
                    com.gtnewhorizons.modularnuclear.common.metatileentity.CoolantLoopMetaTileEntities.PRESSURIZED_HATCH_INPUT_ID)
                .shouldReject(t -> !t.mPressurizedInputs.isEmpty())
                .casingIndex(CASING_INDEX)
                .hint(2)
                .buildAndChain(
                    GTStructureUtility
                        .ofHatchAdder(MTEPressurizedFluidHeater::addPressurizedInputToMachineList, CASING_INDEX, 2));

            IStructureElement<MTEPressurizedFluidHeater> hpOutputElement = HatchElementBuilder
                .<MTEPressurizedFluidHeater>builder()
                .atLeast(MTEPressurizedFluidHeater.HeaterHatchElement.PressurizedOutput)
                .adder(MTEPressurizedFluidHeater::addPressurizedOutputToMachineList)
                .hatchIds(
                    com.gtnewhorizons.modularnuclear.common.metatileentity.CoolantLoopMetaTileEntities.PRESSURIZED_HATCH_OUTPUT_ID)
                .shouldReject(t -> !t.mPressurizedOutputs.isEmpty())
                .casingIndex(CASING_INDEX)
                .hint(3)
                .buildAndChain(
                    GTStructureUtility
                        .ofHatchAdder(MTEPressurizedFluidHeater::addPressurizedOutputToMachineList, CASING_INDEX, 3));

            IStructureElement<MTEPressurizedFluidHeater> coilElement = GTStructureChannels.HEATING_COIL.use(
                GTStructureUtility
                    .ofCoil(MTEPressurizedFluidHeater::setCoilLevel, MTEPressurizedFluidHeater::getCoilLevel));

            IStructureElement<MTEPressurizedFluidHeater> pipeCasingElement = GTStructureChannels.ITEM_PIPE_CASING.use(
                GTStructureUtility.chainItemPipeCasings(
                    -1,
                    MTEPressurizedFluidHeater::setPipeCasingTier,
                    MTEPressurizedFluidHeater::getPipeCasingTier));

            StructureDefinition.Builder<MTEPressurizedFluidHeater> builder = StructureDefinition
                .<MTEPressurizedFluidHeater>builder();

            for (int l = MIN_LENGTH; l <= MAX_LENGTH; l++) {
                builder.addShape(getShapeName(l), makeShape(l));
            }

            STRUCTURE_DEFINITION = builder.addElement('x', xElement)
                .addElement('I', hpInputElement)
                .addElement('O', hpOutputElement)
                .addElement('c', coilElement)
                .addElement('p', pipeCasingElement)
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    public static int getCoilTierIndex(HeatingCoilLevel coilLevel) {
        if (coilLevel == null || coilLevel == HeatingCoilLevel.None || coilLevel == HeatingCoilLevel.ULV) {
            return 0;
        }
        return Math.max(0, coilLevel.getTier());
    }

    /**
     * Maximum coil tier index (0-based from Cupronickel = 0 up to Eternal/MAX = 13).
     */
    public static int getMaxCoilTierIndex() {
        return Math.max(1, HeatingCoilLevel.getMaxTier());
    }

    /**
     * Heating efficiency multiplier based on heating coil tier:
     * starts at 25% (0.25) for Cupronickel (HeatingCoilLevel.LV, coilTierIndex = 0) and increases
     * linearly to 100% (1.0) at the maximum heating coil tier (HeatingCoilLevel.MAX).
     */
    public static double getCoilHeatingEfficiency(HeatingCoilLevel coilLevel) {
        int maxTierIdx = getMaxCoilTierIndex();
        int tierIdx = Math.max(0, Math.min(maxTierIdx, getCoilTierIndex(coilLevel)));
        return 0.25 + 0.75 * ((double) tierIdx / (double) maxTierIdx);
    }

    /**
     * Maximum allowed energy hatch voltage tier for the given heating coil level.
     * Cupronickel (HeatingCoilLevel.LV, tier 1) -> LV (tier 1), Kanthal -> MV (tier 2), etc.
     */
    public static int getMaxAllowedEnergyHatchTier(HeatingCoilLevel coilLevel) {
        return getCoilTierIndex(coilLevel) + 1;
    }

    /**
     * Maximum EU consumed per tick for the given number of middle ring layers (1..9) and heating coil level.
     * Maxes out at 32 EU per ring layer at Cupronickel (coilTierIndex = 0), scaling by 4^coilTierIndex.
     */
    public static long getMaxEuPerTick(int ringCount, HeatingCoilLevel coilLevel) {
        int rings = Math.max(MIN_RINGS, Math.min(MAX_RINGS, ringCount));
        int coilTierIdx = getCoilTierIndex(coilLevel);
        long multiplier = 1L << (2 * coilTierIdx); // 4^coilTierIdx
        return 32L * rings * multiplier;
    }

    /**
     * Detects the structure length in-world by scanning item pipe casings behind the front center hatch (y + 1).
     */
    public static int detectLength(gregtech.api.interfaces.tileentity.IGregTechTileEntity te) {
        if (te == null) return MIN_LENGTH;
        net.minecraft.world.World world = te.getWorld();
        if (world == null) return MIN_LENGTH;
        net.minecraftforge.common.util.ForgeDirection front = te.getFrontFacing();
        net.minecraftforge.common.util.ForgeDirection back = (front != null
            && front != net.minecraftforge.common.util.ForgeDirection.UNKNOWN) ? front.getOpposite()
                : net.minecraftforge.common.util.ForgeDirection.NORTH;
        int x = te.getXCoord();
        int y = te.getYCoord() + 1;
        int z = te.getZCoord();

        int rings = 0;
        for (int step = 1; step <= MAX_RINGS; step++) {
            int bx = x + back.offsetX * step;
            int by = y + back.offsetY * step;
            int bz = z + back.offsetZ * step;
            net.minecraft.block.Block b = world.getBlock(bx, by, bz);
            if (b != null && b == GregTechAPI.sBlockCasings11) {
                rings++;
            } else {
                break;
            }
        }
        if (rings >= MIN_RINGS && rings <= MAX_RINGS) {
            return rings + 2;
        }
        return MIN_LENGTH;
    }
}
