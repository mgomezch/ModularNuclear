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

    public static void getExtraInfoData(MTENuclearReactor reactor, List<String> info) {
        if (!reactor.mMachine) return;
        double maxTemp = NuclearSimulationEngine.getMaxOperatingTemperature(reactor.mPipeTier);
        info.add(String.format("Core Temp: %,.1f / %,.0f °C", reactor.mCoreTemp, maxTemp));
        if (reactor.mDirectPowerEUt > 0) {
            info.add(String.format("EU Output: +%,d EU/t", reactor.mDirectPowerEUt));
        }
        if (reactor.mOutputCoolantRate > 0 && reactor.mOutputCoolantName != null
            && !reactor.mOutputCoolantName.isEmpty()) {
            info.add(
                String.format("Coolant Output: %,d L/s %s", reactor.mOutputCoolantRate, reactor.mOutputCoolantName));
        }
        info.add(String.format("Reactivity: %.1f%%", reactor.mReactivity * 100.0));
        info.add("Flux: " + NuclearSimulationEngine.formatNeutronFlux(reactor.mNeutronsProduced));
        info.add(
            String.format(
                "Neutrons: %,d fast, %,d therm, %,d esc",
                reactor.mFastAbsorbed,
                reactor.mThermalAbsorbed,
                reactor.mEscapedNeutrons));
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
        if (!reactor.mNuclearTiles.isEmpty()) {
            info.add(
                String.format(
                    "Grid Cells: %d Fuel, %d Coolant, %d HP Loop, %d Control / %d Total",
                    fuelCount,
                    coolantHatchCount,
                    hpPassageCount,
                    controlRodCount,
                    reactor.mNuclearTiles.size()));
        }
    }
}
