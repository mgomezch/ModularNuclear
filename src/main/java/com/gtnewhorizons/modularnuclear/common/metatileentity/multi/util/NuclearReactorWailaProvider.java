package com.gtnewhorizons.modularnuclear.common.metatileentity.multi.util;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;

import java.util.List;
import java.util.Locale;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressure;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.overlay.tooltiprenderers.TTRenderBar;

public class NuclearReactorWailaProvider {

    public static void getExtraWailaNBT(MTENuclearReactor reactor, EntityPlayerMP playerMP, TileEntity tileEntity,
        NBTTagCompound tag, World world, int x, int y, int z) {
        tag.setDouble("coreTemp", reactor.mCoreTemp);
        double maxTemp = NuclearSimulationEngine.getMaxOperatingTemperature(reactor.mPipeTier);
        tag.setDouble("maxTemp", maxTemp);
        tag.setLong("euOutput", reactor.mDirectPowerEUt);
        tag.setInteger("coolantRate", reactor.mOutputCoolantRate);
        tag.setString("coolantName", reactor.mOutputCoolantName != null ? reactor.mOutputCoolantName : "");
        tag.setFloat("reactivity", (float) reactor.mReactivity);
        tag.setInteger("neutronsProduced", reactor.mNeutronsProduced);
        tag.setInteger("fastAbsorbed", reactor.mFastAbsorbed);
        tag.setInteger("thermalAbsorbed", reactor.mThermalAbsorbed);
        tag.setInteger("escapedNeutrons", reactor.mEscapedNeutrons);
        tag.setDouble("damage", reactor.mReactorDamage);

        int fuelCount = 0;
        int coolantHatchCount = 0;
        int controlRodCount = reactor.mBottomControlRodHatches.size();
        int hpPassageCount = 0;
        for (IGregTechTileEntity te : reactor.mNuclearTiles) {
            if (te != null) {
                IMetaTileEntity mte = te.getMetaTileEntity();
                if (mte instanceof MTEHatchNuclearBus) {
                    fuelCount++;
                } else if (mte instanceof MTEHatchNuclearHatch) {
                    coolantHatchCount++;
                } else if (mte instanceof MTEHatchNuclearHighPressure) {
                    hpPassageCount++;
                }
            }
        }
        tag.setInteger("fuelCount", fuelCount);
        tag.setInteger("coolantHatchCount", coolantHatchCount);
        tag.setInteger("controlRodCount", controlRodCount);
        tag.setInteger("hpPassageCount", hpPassageCount);
        tag.setInteger("totalCells", reactor.mNuclearTiles.size());
    }

    public static void getExtraWailaBody(ItemStack itemStack, List<String> list, NBTTagCompound tag,
        IWailaDataAccessor accessor, IWailaConfigHandler config) {
        double temp = tag.getDouble("coreTemp");
        double maxTemp = tag.getDouble("maxTemp");
        if (maxTemp <= 0.0) {
            maxTemp = 2500.0;
        }
        double ratio = Math.max(0.0, Math.min(1.0, temp / maxTemp));

        int topColor;
        int bottomColor;
        if (ratio >= 0.90) {
            topColor = 0xFFFF3333;
            bottomColor = 0xFF880000;
        } else if (ratio >= 0.75) {
            topColor = 0xFFFF9900;
            bottomColor = 0xFFCC5500;
        } else if (ratio >= 0.50) {
            topColor = 0xFFFFD700;
            bottomColor = 0xFFB8860B;
        } else {
            topColor = 0xFF00E676;
            bottomColor = 0xFF007A33;
        }
        String tempText = String.format("Core Temp: %,.1f / %,.0f °C", temp, maxTemp);
        list.add(TTRenderBar.create(tempText, topColor, bottomColor, ratio));

        long euOutput = tag.getLong("euOutput");
        int coolantRate = tag.getInteger("coolantRate");
        String coolantName = tag.getString("coolantName");

        if (euOutput > 0) {
            list.add(
                EnumChatFormatting.GREEN + "EU Output: "
                    + EnumChatFormatting.WHITE
                    + String.format("+%,d EU/t", euOutput));
        }
        if (coolantRate > 0 && !coolantName.isEmpty()) {
            list.add(
                EnumChatFormatting.AQUA + "Coolant Output: "
                    + EnumChatFormatting.WHITE
                    + String.format("%,d L/s %s", coolantRate, coolantName));
        }
        if (euOutput == 0 && coolantRate == 0) {
            list.add(EnumChatFormatting.GRAY + "Output: " + EnumChatFormatting.DARK_GRAY + "0 EU/t | 0 L/s");
        }

        double dmg = tag.getDouble("damage");
        if (dmg > 0.0) {
            list.add(EnumChatFormatting.RED + String.format(Locale.US, "Reactor Damage: %.1f%%", dmg));
        }

        float reactivity = tag.getFloat("reactivity");
        int flux = tag.getInteger("neutronsProduced");
        list.add(
            EnumChatFormatting.YELLOW + "Reactivity: "
                + EnumChatFormatting.WHITE
                + String.format("%.1f%%", reactivity * 100.0f)
                + EnumChatFormatting.GRAY
                + " | "
                + EnumChatFormatting.YELLOW
                + "Flux: "
                + EnumChatFormatting.WHITE
                + NuclearSimulationEngine.formatNeutronFlux(flux));

        int fast = tag.getInteger("fastAbsorbed");
        int therm = tag.getInteger("thermalAbsorbed");
        int esc = tag.getInteger("escapedNeutrons");
        if (flux > 0 || fast > 0 || therm > 0 || esc > 0) {
            list.add(
                EnumChatFormatting.GRAY + String.format("Neutrons: %,d fast, %,d therm, %,d esc", fast, therm, esc));
        }

        int fuelCount = tag.getInteger("fuelCount");
        int coolantCount = tag.getInteger("coolantHatchCount");
        int totalCells = tag.getInteger("totalCells");
        if (totalCells > 0) {
            list.add(
                EnumChatFormatting.GRAY
                    + String.format("Grid Cells: %d Fuel, %d Coolant / %d Total", fuelCount, coolantCount, totalCells));
        }
    }

    public static void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        final NBTTagCompound tag = accessor.getNBTData();
        String efficiency = EnumChatFormatting.RESET + StatCollector
            .translateToLocalFormatted("GT5U.waila.multiblock.status.efficiency", tag.getFloat("efficiency"));
        if (tag.getBoolean("hasProblems")) {
            currentTip.add(
                EnumChatFormatting.RED + StatCollector.translateToLocal("GT5U.waila.multiblock.status.has_problem")
                    + efficiency);
        } else if (!tag.getBoolean("incompleteStructure")) {
            currentTip.add(
                EnumChatFormatting.GREEN + StatCollector.translateToLocal("GT5U.waila.multiblock.status.running_fine")
                    + efficiency);
        }
        try {
            if (gregtech.GTMod.proxy != null && gregtech.GTMod.proxy.wailaAverageNS && tag.hasKey("averageNS")) {
                int tAverageTime = tag.getInteger("averageNS");
                currentTip.add(
                    StatCollector.translateToLocalFormatted(
                        "GT5U.waila.multiblock.status.cpu_load",
                        formatNumber((long) tAverageTime)));
            }
        } catch (Throwable ignored) {}
    }

    public static String[] getInfoData(MTENuclearReactor reactor) {
        if (!reactor.mMachine) {
            return new String[] { EnumChatFormatting.RED + "Structure Incomplete" + EnumChatFormatting.RESET };
        }
        reactor.calculateTelemetry();
        List<String> list = new java.util.ArrayList<>();

        // Operational State
        String stateStr;
        if (reactor.mReactorDamage >= 90.0) {
            stateStr = EnumChatFormatting.RED + EnumChatFormatting.BOLD.toString()
                + "MELTDOWN IMMINENT ("
                + String.format(Locale.US, "%.1f%% damage", reactor.mReactorDamage)
                + ")"
                + EnumChatFormatting.RESET;
        } else if (reactor.mScram) {
            stateStr = EnumChatFormatting.GOLD + "SCRAM (Emergency Shutdown Active)" + EnumChatFormatting.RESET;
        } else if (reactor.mTotalFuelItems > 0 || reactor.mNeutronsProduced > 0) {
            stateStr = EnumChatFormatting.GREEN + "Operational (Running)" + EnumChatFormatting.RESET;
        } else {
            stateStr = EnumChatFormatting.GRAY + "Standby (Offline / No Active Fuel)" + EnumChatFormatting.RESET;
        }
        list.add(EnumChatFormatting.YELLOW + "State: " + EnumChatFormatting.RESET + stateStr);

        // Core & Tile Temperatures
        double maxTemp = NuclearSimulationEngine.getMaxOperatingTemperature(reactor.mPipeTier);
        String casingName = NuclearSimulationEngine.getPipeTierName(reactor.mPipeTier);
        list.add(
            String.format(
                Locale.US,
                "%sCore Temp: %s%,.1f / %,.0f °C (%s)",
                EnumChatFormatting.YELLOW,
                EnumChatFormatting.RESET,
                reactor.mCoreTemp,
                maxTemp,
                casingName));
        list.add(
            String.format(
                Locale.US,
                "%sTile Temps: %sMin %,.1f °C | Avg %,.1f °C | Max %,.1f °C",
                EnumChatFormatting.YELLOW,
                EnumChatFormatting.RESET,
                reactor.mMinTileTemp,
                reactor.mAvgTileTemp,
                reactor.mMaxTileTemp));

        // Hull Integrity & Damage
        if (reactor.mReactorDamage > 0.0) {
            double repairThreshold = NuclearSimulationEngine.getRepairTemperatureThreshold(reactor.mPipeTier);
            list.add(
                String.format(
                    Locale.US,
                    "%sHull Damage: %s%.1f%%%s (Repair threshold: %,.0f °C)",
                    EnumChatFormatting.YELLOW,
                    EnumChatFormatting.RED,
                    reactor.mReactorDamage,
                    EnumChatFormatting.RESET,
                    repairThreshold));
        } else {
            list.add(
                EnumChatFormatting.YELLOW + "Hull Damage: "
                    + EnumChatFormatting.GREEN
                    + "0.0% (Nominal)"
                    + EnumChatFormatting.RESET);
        }

        // Reactivity & Neutron Flux
        list.add(
            String.format(
                Locale.US,
                "%sReactivity: %s%.1f%%%s | %sFlux: %s%s",
                EnumChatFormatting.YELLOW,
                EnumChatFormatting.RESET,
                reactor.mReactivity * 100.0,
                EnumChatFormatting.RESET,
                EnumChatFormatting.YELLOW,
                EnumChatFormatting.RESET,
                NuclearSimulationEngine.formatNeutronFlux(reactor.mNeutronsProduced)));
        list.add(
            String.format(
                Locale.US,
                "%sNeutrons: %s%,d fast, %,d therm, %,d esc",
                EnumChatFormatting.YELLOW,
                EnumChatFormatting.RESET,
                reactor.mFastAbsorbed,
                reactor.mThermalAbsorbed,
                reactor.mEscapedNeutrons));

        // Power & Coolant Output
        if (reactor.mDirectPowerEUt > 0) {
            list.add(
                String.format(
                    Locale.US,
                    "%sEU Output: %s+%,d EU/t%s",
                    EnumChatFormatting.YELLOW,
                    EnumChatFormatting.GREEN,
                    reactor.mDirectPowerEUt,
                    EnumChatFormatting.RESET));
        }
        if (reactor.mOutputCoolantRate > 0 && reactor.mOutputCoolantName != null
            && !reactor.mOutputCoolantName.isEmpty()) {
            list.add(
                String.format(
                    Locale.US,
                    "%sCoolant Output: %s%,d L/s %s%s",
                    EnumChatFormatting.YELLOW,
                    EnumChatFormatting.AQUA,
                    reactor.mOutputCoolantRate,
                    reactor.mOutputCoolantName,
                    EnumChatFormatting.RESET));
        }

        // Core Cells Summary
        int fuelCount = 0;
        int coolantHatchCount = 0;
        int hpPassageCount = reactor.mTopHighPressureHatches.size();
        int controlRodCount = reactor.mBottomControlRodHatches.size();
        for (IGregTechTileEntity te : reactor.mNuclearTiles) {
            if (te != null) {
                IMetaTileEntity mte = te.getMetaTileEntity();
                if (mte instanceof MTEHatchNuclearBus) {
                    fuelCount++;
                } else if (mte instanceof MTEHatchNuclearHatch) {
                    coolantHatchCount++;
                }
            }
        }
        if (!reactor.mNuclearTiles.isEmpty()) {
            list.add(
                String.format(
                    Locale.US,
                    "%sCore Cells: %s%d Fuel, %d Coolant, %d HP Loop, %d Control / %d Total",
                    EnumChatFormatting.YELLOW,
                    EnumChatFormatting.RESET,
                    fuelCount,
                    coolantHatchCount,
                    hpPassageCount,
                    controlRodCount,
                    reactor.mNuclearTiles.size()));
        }

        // Active Core Inventory Items & Fluids
        if (reactor.mTotalFuelItems > 0 || reactor.mTotalCoolantItems > 0) {
            list.add(
                String.format(
                    Locale.US,
                    "%sCore Items: %s%d fuel items (%d hatches), %d coolant items (%d hatches)",
                    EnumChatFormatting.YELLOW,
                    EnumChatFormatting.RESET,
                    reactor.mTotalFuelItems,
                    reactor.mFuelHatchCount,
                    reactor.mTotalCoolantItems,
                    reactor.mCoolantHatchCount));
        }
        if (reactor.mTotalFuelFluid > 0 || reactor.mTotalCoolantFluid > 0) {
            list.add(
                String.format(
                    Locale.US,
                    "%sCore Fluids: %s%,d / %,d L fuel | %,d / %,d L coolant",
                    EnumChatFormatting.YELLOW,
                    EnumChatFormatting.RESET,
                    reactor.mTotalFuelFluid,
                    reactor.mTotalFuelCapacity,
                    reactor.mTotalCoolantFluid,
                    reactor.mTotalCoolantCapacity));
        }

        // Maintenance Status
        if (reactor.getRepairStatus() == reactor.getIdealStatus()) {
            list.add(
                EnumChatFormatting.YELLOW + "Maintenance: "
                    + EnumChatFormatting.GREEN
                    + "Optimal"
                    + EnumChatFormatting.RESET);
        } else {
            int problems = reactor.getIdealStatus() - reactor.getRepairStatus();
            list.add(
                EnumChatFormatting.YELLOW + "Maintenance: "
                    + EnumChatFormatting.RED
                    + String.format(Locale.US, "Has Problems (%d)", problems)
                    + EnumChatFormatting.RESET);
        }

        return list.toArray(new String[0]);
    }

    public static void getExtraInfoData(MTENuclearReactor reactor, List<String> info) {
        // Main telemetry is exposed through getInfoData() so portable scanners and GT sensor cards
        // do not display duplicate entries.
    }
}
