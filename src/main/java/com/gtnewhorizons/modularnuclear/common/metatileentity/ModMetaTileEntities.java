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
    public static final int ID_NUCLEAR_REACTOR = 32115;

    public static ItemStack reactor;
    public static ItemStack nuclearBus;
    public static ItemStack[] nuclearHatches = new ItemStack[9];
    public static ItemStack nuclearControlHatch;
    public static ItemStack nuclearControlRodHatch;

    public static void init() {
        NuclearStructureChannels.register();

        nuclearBus = new MTEHatchNuclearBus(ID_NUCLEAR_BUS, "hatch.nuclearbus", "Nuclear Core Bus", 5).getStackForm(1L);

        for (int i = 0; i < 9; i++) {
            nuclearHatches[i] = new MTEHatchNuclearHatch(
                ID_NUCLEAR_HATCH_BASE + i,
                "hatch.nuclearhatch." + GTValues.VN[i + 1].toLowerCase(),
                "Nuclear Core Hatch (" + GTValues.VN[i + 1] + ")",
                i + 1).getStackForm(1L);
            NuclearStructureChannels.NUCLEAR_HATCH.registerAsIndicator(nuclearHatches[i], i + 1);
        }

        nuclearControlHatch = new MTEHatchNuclearControl(
            ID_NUCLEAR_CONTROL,
            "hatch.nuclearcontrol",
            "Nuclear Control Hatch",
            5).getStackForm(1L);

        nuclearControlRodHatch = new MTEHatchNuclearControlRod(
            ID_NUCLEAR_CONTROL_ROD,
            "hatch.nuclearcontrolrod",
            "Nuclear Control Rod Hatch",
            5).getStackForm(1L);

        reactor = new MTENuclearReactor(ID_NUCLEAR_REACTOR, "multimachine.nuclearreactor", "Nuclear Fission Reactor")
            .getStackForm(1L);
    }
}
