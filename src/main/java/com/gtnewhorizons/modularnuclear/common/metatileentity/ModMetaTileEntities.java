package com.gtnewhorizons.modularnuclear.common.metatileentity;

import net.minecraft.item.ItemStack;

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
    public static final int ID_NUCLEAR_REACTOR = 32115;

    public static ItemStack reactor;
    public static ItemStack nuclearBus;
    public static ItemStack[] nuclearHatches = new ItemStack[9];
    public static ItemStack nuclearControlHatch;
    public static ItemStack nuclearControlRodHatch;
    public static ItemStack nuclearHighPressureHatch;

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

        nuclearHighPressureHatch = new com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressure(
            ID_NUCLEAR_HIGH_PRESSURE,
            "hatch.nuclearhighpressure",
            "Nuclear core high-pressure hatch",
            5).getStackForm(1L);
        NuclearStructureChannels.NUCLEAR_HATCH.registerAsIndicator(nuclearHighPressureHatch, 10);

        reactor = new MTENuclearReactor(
            ID_NUCLEAR_REACTOR,
            "multimachine.nuclearreactor",
            "Modular Pressure Tube Reactor (MPTR)").getStackForm(1L);
    }
}
