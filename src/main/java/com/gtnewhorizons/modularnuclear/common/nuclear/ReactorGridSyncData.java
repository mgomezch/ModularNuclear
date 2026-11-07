package com.gtnewhorizons.modularnuclear.common.nuclear;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularui.common.internal.network.NetworkUtils;

import cpw.mods.fml.common.network.ByteBufUtils;

/**
 * What the reactor GUI shows: the reactor's readings and each core cell's contents and readings. The server sends it
 * in parts ({@code ReactorGridSyncHandler}): a cell's contents (items, fluids, rods) when they change, and the readings
 * (temperatures, heat, fluxes, insertion) once per reactor cycle. These are the parts' wire formats.
 */
public class ReactorGridSyncData {

    public int gridSize = 0;
    public int coreDimension = 0;
    public float coreTemp = 20.0f;
    public float avgTemp = 20.0f;
    public double efficiency = 0.0;
    public int pipeTier = 0;
    public long directPowerEUt = 0;
    public int neutronsProduced = 0;
    public int fastAbsorbed = 0;
    public int thermalAbsorbed = 0;
    public int escapedNeutrons = 0;
    public int outputCoolantRate = 0;
    public String outputCoolantName = "";
    public boolean scram = false;
    public float ambientTemp = 20.0f;
    public float reactorDamage = 0.0f;
    public long cumulativeNeutrons = 0L;
    public int ticksPerCycle = NuclearSimulationEngine.DEFAULT_TICKS_PER_CYCLE;
    public List<ReactorGridCellData> cells = new ArrayList<>();

    public static class ReactorGridCellData {

        public boolean exists = false;
        public boolean isFluid = false;
        public boolean isHighPressure = false;
        public boolean hasControlRod = false;
        public boolean isAdvancedControlRod = false;
        public int controlRodInsertion = 0;
        public int controlRodType = 0;
        public int moderatorInsertion = 0;
        /** The advanced rod's moderator, which the client names in its own language. */
        public ItemStack moderatorStack = null;
        public ItemStack itemStack = null;
        public FluidStack fluidStack = null;
        public float temperature = 20.0f;
        public float heatOutput = 0.0f;
        public int fastFlux = 0;
        public int thermalFlux = 0;
        public int fastAbsorbed = 0;
        public int thermalAbsorbed = 0;
        public long directEU = 0;
        /** 1 + the ordinal of the reason the server refuses the cell's item as fuel, or 0. */
        public byte notFuelReason = 0;

        /** Whether the cell holds the same things (items, fluid, rods) as another, exactly. */
        public boolean sameContents(ReactorGridCellData o) {
            return exists == o.exists && isFluid == o.isFluid
                && isHighPressure == o.isHighPressure
                && hasControlRod == o.hasControlRod
                && isAdvancedControlRod == o.isAdvancedControlRod
                && controlRodType == o.controlRodType
                && notFuelReason == o.notFuelReason
                && ItemStack.areItemStacksEqual(itemStack, o.itemStack)
                && ItemStack.areItemStacksEqual(moderatorStack, o.moderatorStack)
                && (fluidStack == null ? o.fluidStack == null : fluidStack.isFluidStackIdentical(o.fluidStack));
        }

        /** Takes another cell's contents, copying its stacks (it may hold the hatches' own). */
        public void copyContentsFrom(ReactorGridCellData o) {
            exists = o.exists;
            isFluid = o.isFluid;
            isHighPressure = o.isHighPressure;
            hasControlRod = o.hasControlRod;
            isAdvancedControlRod = o.isAdvancedControlRod;
            controlRodType = o.controlRodType;
            notFuelReason = o.notFuelReason;
            itemStack = ItemStack.copyItemStack(o.itemStack);
            moderatorStack = ItemStack.copyItemStack(o.moderatorStack);
            fluidStack = o.fluidStack != null ? o.fluidStack.copy() : null;
        }

        public void writeContents(PacketBuffer buf) {
            int mask = 0;
            if (exists) mask |= 1;
            if (isFluid) mask |= 2;
            if (itemStack != null) mask |= 4;
            if (fluidStack != null) mask |= 8;
            if (isHighPressure) mask |= 16;
            if (hasControlRod) mask |= 32;
            if (isAdvancedControlRod) mask |= 64;
            if (moderatorStack != null) mask |= 128;
            buf.writeByte(mask);
            if (!exists) return;
            buf.writeByte(controlRodType);
            buf.writeByte(notFuelReason);
            if (itemStack != null) NetworkUtils.writeItemStack(buf, itemStack);
            if (fluidStack != null) {
                buf.writeVarIntToBuffer(fluidStack.getFluidID());
                buf.writeVarIntToBuffer(fluidStack.amount);
            }
            if (moderatorStack != null) NetworkUtils.writeItemStack(buf, moderatorStack);
        }

        public void readContents(PacketBuffer buf) {
            int mask = buf.readByte() & 0xFF;
            exists = (mask & 1) != 0;
            isFluid = (mask & 2) != 0;
            isHighPressure = (mask & 16) != 0;
            hasControlRod = (mask & 32) != 0;
            isAdvancedControlRod = (mask & 64) != 0;
            itemStack = null;
            fluidStack = null;
            moderatorStack = null;
            if (!exists) return;
            controlRodType = buf.readByte() & 0xFF;
            notFuelReason = buf.readByte();
            if ((mask & 4) != 0) itemStack = NetworkUtils.readItemStack(buf);
            if ((mask & 8) != 0) {
                Fluid fluid = FluidRegistry.getFluid(buf.readVarIntFromBuffer());
                int amount = buf.readVarIntFromBuffer();
                if (fluid != null) fluidStack = new FluidStack(fluid, amount);
            }
            if ((mask & 128) != 0) moderatorStack = NetworkUtils.readItemStack(buf);
        }

        public void writeReadings(PacketBuffer buf) {
            buf.writeFloat(temperature);
            buf.writeFloat(heatOutput);
            buf.writeVarIntToBuffer(fastFlux);
            buf.writeVarIntToBuffer(thermalFlux);
            buf.writeVarIntToBuffer(fastAbsorbed);
            buf.writeVarIntToBuffer(thermalAbsorbed);
            buf.writeLong(directEU);
            buf.writeByte(controlRodInsertion);
            buf.writeByte(moderatorInsertion);
        }

        public void readReadings(PacketBuffer buf) {
            temperature = buf.readFloat();
            heatOutput = buf.readFloat();
            fastFlux = buf.readVarIntFromBuffer();
            thermalFlux = buf.readVarIntFromBuffer();
            fastAbsorbed = buf.readVarIntFromBuffer();
            thermalAbsorbed = buf.readVarIntFromBuffer();
            directEU = buf.readLong();
            controlRodInsertion = buf.readByte() & 0xFF;
            moderatorInsertion = buf.readByte() & 0xFF;
        }
    }

    /** Whether the reactor-wide readings are the same as another's, exactly. */
    public boolean sameHeader(ReactorGridSyncData o) {
        return gridSize == o.gridSize && coreDimension == o.coreDimension
            && coreTemp == o.coreTemp
            && avgTemp == o.avgTemp
            && efficiency == o.efficiency
            && pipeTier == o.pipeTier
            && directPowerEUt == o.directPowerEUt
            && neutronsProduced == o.neutronsProduced
            && fastAbsorbed == o.fastAbsorbed
            && thermalAbsorbed == o.thermalAbsorbed
            && escapedNeutrons == o.escapedNeutrons
            && outputCoolantRate == o.outputCoolantRate
            && Objects.equals(outputCoolantName, o.outputCoolantName)
            && scram == o.scram
            && ambientTemp == o.ambientTemp
            && reactorDamage == o.reactorDamage
            && cumulativeNeutrons == o.cumulativeNeutrons
            && ticksPerCycle == o.ticksPerCycle;
    }

    public void copyHeaderFrom(ReactorGridSyncData o) {
        gridSize = o.gridSize;
        coreDimension = o.coreDimension;
        coreTemp = o.coreTemp;
        avgTemp = o.avgTemp;
        efficiency = o.efficiency;
        pipeTier = o.pipeTier;
        directPowerEUt = o.directPowerEUt;
        neutronsProduced = o.neutronsProduced;
        fastAbsorbed = o.fastAbsorbed;
        thermalAbsorbed = o.thermalAbsorbed;
        escapedNeutrons = o.escapedNeutrons;
        outputCoolantRate = o.outputCoolantRate;
        outputCoolantName = o.outputCoolantName;
        scram = o.scram;
        ambientTemp = o.ambientTemp;
        reactorDamage = o.reactorDamage;
        cumulativeNeutrons = o.cumulativeNeutrons;
        ticksPerCycle = o.ticksPerCycle;
    }

    public void writeHeader(PacketBuffer buf) {
        buf.writeByte(gridSize);
        buf.writeByte(coreDimension);
        buf.writeFloat(coreTemp);
        buf.writeFloat(avgTemp);
        buf.writeDouble(efficiency);
        buf.writeByte(pipeTier);
        buf.writeLong(directPowerEUt);
        buf.writeVarIntToBuffer(neutronsProduced);
        buf.writeVarIntToBuffer(fastAbsorbed);
        buf.writeVarIntToBuffer(thermalAbsorbed);
        buf.writeVarIntToBuffer(escapedNeutrons);
        buf.writeVarIntToBuffer(outputCoolantRate);
        ByteBufUtils.writeUTF8String(buf, outputCoolantName != null ? outputCoolantName : "");
        buf.writeBoolean(scram);
        buf.writeFloat(ambientTemp);
        buf.writeFloat(reactorDamage);
        buf.writeLong(cumulativeNeutrons);
        buf.writeVarIntToBuffer(ticksPerCycle);
    }

    public void readHeader(PacketBuffer buf) {
        gridSize = buf.readByte();
        coreDimension = buf.readByte();
        coreTemp = buf.readFloat();
        avgTemp = buf.readFloat();
        efficiency = buf.readDouble();
        pipeTier = buf.readByte();
        directPowerEUt = buf.readLong();
        neutronsProduced = buf.readVarIntFromBuffer();
        fastAbsorbed = buf.readVarIntFromBuffer();
        thermalAbsorbed = buf.readVarIntFromBuffer();
        escapedNeutrons = buf.readVarIntFromBuffer();
        outputCoolantRate = buf.readVarIntFromBuffer();
        outputCoolantName = ByteBufUtils.readUTF8String(buf);
        scram = buf.readBoolean();
        ambientTemp = buf.readFloat();
        reactorDamage = buf.readFloat();
        cumulativeNeutrons = buf.readLong();
        ticksPerCycle = buf.readVarIntFromBuffer();
    }
}
