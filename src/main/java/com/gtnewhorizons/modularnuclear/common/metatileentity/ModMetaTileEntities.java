package com.gtnewhorizons.modularnuclear.common.metatileentity;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantPipeSpec;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControl;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;

import gregtech.api.enums.GTValues;

public class ModMetaTileEntities {

    public static final int ID_NUCLEAR_BUS = 32100;
    public static final int ID_NUCLEAR_HATCH_BASE = 32101; // 32101..32109 (LV through UHV)
    public static final int ID_NUCLEAR_CONTROL = 32110;
    public static final int ID_NUCLEAR_CONTROL_ROD = 32111;
    public static final int ID_NUCLEAR_HIGH_PRESSURE = 32112;
    public static final int ID_NUCLEAR_HIGH_PRESSURE_INPUT = 32113;
    public static final int ID_NUCLEAR_HIGH_PRESSURE_OUTPUT = 32114;
    public static final int ID_NUCLEAR_REACTOR = 32115;
    public static final int ID_ACTINIDE_COMPOUNDER = 32116;
    public static final int ID_NUCLEAR_ADVANCED_CONTROL_ROD = 32117;
    /** Material-tiered high-pressure hatches, one per coolant pipe material: inputs first, then outputs. */
    public static final int ID_NUCLEAR_HIGH_PRESSURE_INPUT_BASE = 32161; // 32161..32167
    public static final int ID_NUCLEAR_HIGH_PRESSURE_OUTPUT_BASE = 32168; // 32168..32174

    public static ItemStack reactor;
    public static ItemStack actinideCompounder;
    public static ItemStack nuclearBus;
    public static ItemStack[] nuclearHatches = new ItemStack[9];
    public static ItemStack nuclearControlHatch;
    public static ItemStack nuclearControlRodHatch;
    public static ItemStack nuclearAdvancedControlRodHatch;
    public static ItemStack nuclearHighPressureHatch;
    public static ItemStack nuclearHighPressureInputHatch;
    public static ItemStack nuclearHighPressureOutputHatch;
    /** Tiered high-pressure hatches by coolant pipe material index ({@link CoolantPipeSpec#MATERIAL_NAMES}). */
    public static final ItemStack[] nuclearHighPressureInputHatches = new ItemStack[CoolantPipeSpec.MATERIAL_NAMES.length];
    public static final ItemStack[] nuclearHighPressureOutputHatches = new ItemStack[CoolantPipeSpec.MATERIAL_NAMES.length];

    public static void init() {
        NuclearStructureChannels.register();

        nuclearBus = new MTEHatchNuclearBus(ID_NUCLEAR_BUS, "hatch.nuclearbus", "Nuclear core bus", 5).getStackForm(1L);
        NuclearStructureChannels.NUCLEAR_HATCH.registerAsIndicator(nuclearBus, 0);

        for (int i = 0; i < 9; i++) {
            nuclearHatches[i] = new MTEHatchNuclearHatch(
                ID_NUCLEAR_HATCH_BASE + i,
                "hatch.nuclearhatch." + GTValues.VN[i + 1].toLowerCase(),
                "Nuclear core hatch (" + GTValues.VN[i + 1] + ")",
                i + 1).getStackForm(1L);
            NuclearStructureChannels.NUCLEAR_HATCH.registerAsIndicator(nuclearHatches[i], i + 1);
        }

        nuclearControlHatch = new MTEHatchNuclearControl(
            ID_NUCLEAR_CONTROL,
            "hatch.nuclearcontrol",
            "Nuclear control hatch",
            5).getStackForm(1L);

        nuclearControlRodHatch = new MTEHatchNuclearControlRod(
            ID_NUCLEAR_CONTROL_ROD,
            "hatch.nuclearcontrolrod",
            "Nuclear core control rod",
            5).getStackForm(1L);

        nuclearAdvancedControlRodHatch = new com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearAdvancedControlRod(
            ID_NUCLEAR_ADVANCED_CONTROL_ROD,
            "hatch.nuclearadvancedcontrolrod",
            "Advanced nuclear core control rod",
            5).getStackForm(1L);

        nuclearHighPressureHatch = new com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressure(
            ID_NUCLEAR_HIGH_PRESSURE,
            "hatch.nuclearhighpressure",
            "Nuclear core high-pressure hatch (Legacy)",
            5).getStackForm(1L);

        nuclearHighPressureInputHatch = new com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressureInput(
            ID_NUCLEAR_HIGH_PRESSURE_INPUT,
            "hatch.nuclearhighpressure.input",
            "Nuclear core high-pressure input hatch (legacy)",
            5).getStackForm(1L);

        nuclearHighPressureOutputHatch = new com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressureOutput(
            ID_NUCLEAR_HIGH_PRESSURE_OUTPUT,
            "hatch.nuclearhighpressure.output",
            "Nuclear core high-pressure output hatch (legacy)",
            5).getStackForm(1L);

        // All the inputs, then all the outputs, so each kind lists together
        for (int m = 0; m < CoolantPipeSpec.MATERIAL_NAMES.length; m++) {
            String material = CoolantPipeSpec.MATERIAL_NAMES[m];
            nuclearHighPressureInputHatches[m] = new com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressureInput(
                ID_NUCLEAR_HIGH_PRESSURE_INPUT_BASE + m,
                "hatch.nuclearhighpressure.input." + material.toLowerCase(),
                "Nuclear core high-pressure input hatch ("
                    + CoolantLoopMetaTileEntities.COOLANT_PIPE_MATERIALS[m].mDefaultLocalName
                    + ")",
                CoolantPipeSpec.VOLTAGE_TIERS[m],
                m).getStackForm(1L);
            NuclearStructureChannels.NUCLEAR_HATCH.registerAsIndicator(nuclearHighPressureInputHatches[m], 10);
        }
        for (int m = 0; m < CoolantPipeSpec.MATERIAL_NAMES.length; m++) {
            String material = CoolantPipeSpec.MATERIAL_NAMES[m];
            nuclearHighPressureOutputHatches[m] = new com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressureOutput(
                ID_NUCLEAR_HIGH_PRESSURE_OUTPUT_BASE + m,
                "hatch.nuclearhighpressure.output." + material.toLowerCase(),
                "Nuclear core high-pressure output hatch ("
                    + CoolantLoopMetaTileEntities.COOLANT_PIPE_MATERIALS[m].mDefaultLocalName
                    + ")",
                CoolantPipeSpec.VOLTAGE_TIERS[m],
                m).getStackForm(1L);
        }

        reactor = new MTENuclearReactor(
            ID_NUCLEAR_REACTOR,
            "multimachine.nuclearreactor",
            "Modular Pressure Tube Reactor (MPTR)").getStackForm(1L);

        actinideCompounder = new com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTEActinideCompounder(
            ID_ACTINIDE_COMPOUNDER,
            "multimachine.actinidecompounder",
            "Actinide Fuel Compounder").getStackForm(1L);
    }
}
