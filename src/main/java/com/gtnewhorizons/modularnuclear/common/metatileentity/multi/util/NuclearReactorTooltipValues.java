package com.gtnewhorizons.modularnuclear.common.metatileentity.multi.util;

import java.util.Locale;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.modularnuclear.common.metatileentity.NuclearStructureChannels;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.misc.GTStructureChannels;

public class NuclearReactorTooltipValues {

    public static MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        String t0 = String.format(Locale.US, "%,.0f °C", NuclearSimulationEngine.getMaxOperatingTemperature(0));
        String t1 = String.format(Locale.US, "%,.0f °C", NuclearSimulationEngine.getMaxOperatingTemperature(1));
        String t2 = String.format(Locale.US, "%,.0f °C", NuclearSimulationEngine.getMaxOperatingTemperature(2));
        String t3 = String.format(Locale.US, "%,.0f °C", NuclearSimulationEngine.getMaxOperatingTemperature(3));
        String t4 = String.format(Locale.US, "%,.0f °C", NuclearSimulationEngine.getMaxOperatingTemperature(4));
        String t5 = String.format(Locale.US, "%,.0f °C", NuclearSimulationEngine.getMaxOperatingTemperature(5));

        tt.addMachineType(StatCollector.translateToLocal("gt.multiblock.nuclearreactor.machine_type"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.nuclearreactor.desc1"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.nuclearreactor.desc2"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.nuclearreactor.desc3"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.nuclearreactor.desc4"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.nuclearreactor.desc5"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.nuclearreactor.desc6"))
            .addInfo(StatCollector.translateToLocalFormatted("gt.multiblock.nuclearreactor.desc7", t0))
            .addInfo(StatCollector.translateToLocalFormatted("gt.multiblock.nuclearreactor.desc8", t1))
            .addInfo(StatCollector.translateToLocalFormatted("gt.multiblock.nuclearreactor.desc9", t2))
            .addInfo(StatCollector.translateToLocalFormatted("gt.multiblock.nuclearreactor.desc10", t3))
            .addInfo(StatCollector.translateToLocalFormatted("gt.multiblock.nuclearreactor.desc11", t4))
            .addInfo(StatCollector.translateToLocalFormatted("gt.multiblock.nuclearreactor.desc12", t5))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.nuclearreactor.desc13"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.nuclearreactor.desc14"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.nuclearreactor.desc15"))
            .addInfo(EnumChatFormatting.RED + StatCollector.translateToLocal("gt.multiblock.nuclearreactor.desc16"))
            .addInfo(EnumChatFormatting.RED + StatCollector.translateToLocal("gt.multiblock.nuclearreactor.desc17"))
            .addInfo(EnumChatFormatting.RED + StatCollector.translateToLocal("gt.multiblock.nuclearreactor.desc18"))
            .addInfo(EnumChatFormatting.RED + StatCollector.translateToLocal("gt.multiblock.nuclearreactor.desc19"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.nuclearreactor.desc20"))
            .beginVariableStructureBlock(7, 15, 5, 5, 7, 15, true)
            .addStructureInfo(
                EnumChatFormatting.RED
                    + StatCollector.translateToLocal("gt.multiblock.nuclearreactor.structure.wallshare"))
            .addStructureInfo(StatCollector.translateToLocal("gt.multiblock.nuclearreactor.structure.facing"))
            .addController(StatCollector.translateToLocal("gt.mbtt.structure.front_center_2nd_layer"))
            .addCasing("50+", StatCollector.translateToLocal("gt.multiblock.nuclearreactor.structure.casings"), false)
            .addCasing(
                "21+",
                StatCollector.translateToLocal("gt.multiblock.nuclearreactor.structure.pipe_casings"),
                false)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.nuclearreactor.structure.hatches_top"),
                StatCollector.translateToLocal("gt.multiblock.nuclearreactor.structure.hatches_top_pos"),
                1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.nuclearreactor.structure.hatches_bot"),
                StatCollector.translateToLocal("gt.multiblock.nuclearreactor.structure.hatches_bot_pos"),
                1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.nuclearreactor.structure.control_hatch"),
                StatCollector.translateToLocal("gt.mbtt.structure.any_casing"),
                2)
            .addMaintenanceHatch(
                StatCollector.translateToLocal("gt.multiblock.nuclearreactor.structure.maintenance"),
                1)
            .addDynamoHatch(StatCollector.translateToLocal("gt.multiblock.nuclearreactor.structure.dynamo"), 1)
            .addOutputBus(StatCollector.translateToLocal("gt.multiblock.nuclearreactor.structure.optional_outer"), 1)
            .addOutputHatch(StatCollector.translateToLocal("gt.multiblock.nuclearreactor.structure.optional_outer"), 1)
            .addAir(StatCollector.translateToLocal("gt.mbtt.structure.interior"))
            .addSubChannel(GTStructureChannels.ITEM_PIPE_CASING)
            .addSubChannel(NuclearStructureChannels.NUCLEAR_HATCH)
            .toolTipFinisher(
                EnumChatFormatting.AQUA + StatCollector.translateToLocal("gt.multiblock.nuclearreactor.finisher"));
        return tt;
    }
}
