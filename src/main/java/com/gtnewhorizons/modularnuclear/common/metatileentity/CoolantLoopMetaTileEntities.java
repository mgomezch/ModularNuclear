package com.gtnewhorizons.modularnuclear.common.metatileentity;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantPipeSpec;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedInput;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedOutput;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTECoolantPump;
import com.gtnewhorizons.modularnuclear.common.metatileentity.pipe.MTECoolantPipe;

import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

public class CoolantLoopMetaTileEntities {

    public static MTECoolantPump coolantPump;
    public static MTEHatchPressurizedInput pressurizedInputHatch;
    public static MTEHatchPressurizedOutput pressurizedOutputHatch;
    public static MTEHatchPressurizedFluid pressurizedFluidHatch;
    public static com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTEPressurizedHeatExchanger pressurizedHeatExchanger;
    public static com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTEPressurizedDegasser pressurizedDegasser;
    public static com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTECoolantBath coolantBath;
    public static com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTEPressurizedFluidHeater pressurizedFluidHeater;

    public static final int PUMP_ID = 32120;
    public static final int PRESSURIZED_HATCH_INPUT_ID = 32121;
    public static final int PRESSURIZED_HATCH_ID = 32121;
    public static final int HEAT_EXCHANGER_ID = 32122;
    public static final int DEGASSER_ID = 32123;
    public static final int COOLANT_BATH_ID = 32124;
    public static final int COOLANT_PIPE_START_ID = 32125;
    public static final int PIPE_TINY_ID = 32125;
    public static final int PIPE_SMALL_ID = 32132;
    public static final int PIPE_MEDIUM_ID = 32139;
    public static final int PIPE_LARGE_ID = 32146;
    public static final int PIPE_HUGE_ID = 32153;
    public static final int PRESSURIZED_HATCH_OUTPUT_ID = 32160;
    public static final int PRESSURIZED_FLUID_HEATER_ID = 32175;

    public static final Materials[] COOLANT_PIPE_MATERIALS = new Materials[] { Materials.CastIron, Materials.Steel,
        Materials.StainlessSteel, Materials.Titanium, Materials.TungstenSteel, Materials.Osmium, Materials.Neutronium };

    public static final float[] PIPE_THICKNESSES = CoolantPipeSpec.THICKNESSES;

    public static final String[] SIZE_NAMES = CoolantPipeSpec.SIZE_NAMES;

    public static final MTECoolantPipe[][] coolantPipes = new MTECoolantPipe[COOLANT_PIPE_MATERIALS.length][PIPE_THICKNESSES.length];
    public static final ItemStack[][] coolantPipeItems = new ItemStack[COOLANT_PIPE_MATERIALS.length][PIPE_THICKNESSES.length];

    public static void init() {
        coolantPump = new MTECoolantPump(PUMP_ID, "multimachine.coolantpump", "Coolant loop pump");
        pressurizedInputHatch = new com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedInput(
            PRESSURIZED_HATCH_INPUT_ID,
            "hatch.pressurizedfluid.input",
            "High-pressure input hatch",
            4 // EV Tier
        );
        pressurizedOutputHatch = new com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedOutput(
            PRESSURIZED_HATCH_OUTPUT_ID,
            "hatch.pressurizedfluid.output",
            "High-pressure output hatch",
            4 // EV Tier
        );
        pressurizedFluidHatch = pressurizedInputHatch;
        pressurizedHeatExchanger = new com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTEPressurizedHeatExchanger(
            HEAT_EXCHANGER_ID,
            "multimachine.pressurizedheatexchanger",
            "High-pressure heat exchanger");
        pressurizedDegasser = new com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTEPressurizedDegasser(
            DEGASSER_ID,
            "multimachine.pressurizeddegasser",
            "High-pressure degasser");
        coolantBath = new com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTECoolantBath(
            COOLANT_BATH_ID,
            "multimachine.coolantbath",
            "Coolant Bath");
        pressurizedFluidHeater = new com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTEPressurizedFluidHeater(
            PRESSURIZED_FLUID_HEATER_ID,
            "multimachine.pressurizedfluidheater",
            "Pressurized fluid heater");

        for (int s = 0; s < PIPE_THICKNESSES.length; s++) {
            float thick = PIPE_THICKNESSES[s];
            String sizeName = SIZE_NAMES[s];
            int sizeBaseId = COOLANT_PIPE_START_ID + s * COOLANT_PIPE_MATERIALS.length;
            for (int m = 0; m < COOLANT_PIPE_MATERIALS.length; m++) {
                Materials mat = COOLANT_PIPE_MATERIALS[m];
                int heatRes = CoolantPipeSpec.HEAT_RESISTANCES[m];
                int cap = CoolantPipeSpec.capacityPerTick(m, s);

                int id = sizeBaseId + m;
                String internalName = "coolantpipe." + sizeName.toLowerCase() + "." + mat.mName.toLowerCase();
                MTECoolantPipe pipe = new MTECoolantPipe(
                    id,
                    internalName,
                    "gt.oreprefix.material_fluid_pipe",
                    thick,
                    mat,
                    cap,
                    heatRes,
                    true);
                coolantPipes[m][s] = pipe;
                coolantPipeItems[m][s] = pipe.getStackForm(1L);
            }
        }
    }

    public static int getPipeSizeBaseId(int sizeIndex) {
        if (sizeIndex >= 0 && sizeIndex < PIPE_THICKNESSES.length) {
            return COOLANT_PIPE_START_ID + sizeIndex * COOLANT_PIPE_MATERIALS.length;
        }
        return -1;
    }

    public static int getMaterialIndex(Materials mat) {
        if (mat == null) return -1;
        for (int i = 0; i < COOLANT_PIPE_MATERIALS.length; i++) {
            if (COOLANT_PIPE_MATERIALS[i] == mat || COOLANT_PIPE_MATERIALS[i].mName.equalsIgnoreCase(mat.mName)) {
                return i;
            }
        }
        if (mat == Materials.Iron || mat == Materials.WroughtIron) {
            return 0; // CastIron/Iron group
        }
        return -1;
    }

    public static ItemStack getCoolantPipe(Materials mat, int sizeIndex) {
        int mIdx = getMaterialIndex(mat);
        if (mIdx >= 0 && sizeIndex >= 0 && sizeIndex < PIPE_THICKNESSES.length) {
            return coolantPipeItems[mIdx][sizeIndex];
        }
        return null;
    }

    public static boolean isCoolantPipe(IMetaTileEntity mte) {
        return mte instanceof MTECoolantPipe;
    }
}
