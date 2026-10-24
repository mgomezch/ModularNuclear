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
        public String moderatorName = "";
        public ItemStack itemStack = null;
        public FluidStack fluidStack = null;
        public float temperature = 20.0f;
        public float heatOutput = 0.0f;
        public int fastFlux = 0;
        public int thermalFlux = 0;
        public int fastAbsorbed = 0;
        public int thermalAbsorbed = 0;
        public long directEU = 0;
        public long cumulativeFluence = 0L;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ReactorGridCellData that = (ReactorGridCellData) o;
            if (exists != that.exists || isFluid != that.isFluid
                || isHighPressure != that.isHighPressure
                || hasControlRod != that.hasControlRod
                || isAdvancedControlRod != that.isAdvancedControlRod
                || controlRodInsertion != that.controlRodInsertion
                || controlRodType != that.controlRodType
                || moderatorInsertion != that.moderatorInsertion
                || !Objects.equals(moderatorName, that.moderatorName)) return false;
            if (Math.abs(temperature - that.temperature) > 0.5f) return false;
            if (Math.abs(heatOutput - that.heatOutput) > 0.5f) return false;
            if (fastFlux != that.fastFlux || thermalFlux != that.thermalFlux) return false;
            if (fastAbsorbed != that.fastAbsorbed || thermalAbsorbed != that.thermalAbsorbed) return false;
            if (directEU != that.directEU) return false;
            if (cumulativeFluence != that.cumulativeFluence) return false;
            if (!ItemStack.areItemStacksEqual(itemStack, that.itemStack)) return false;
            if (fluidStack == null ? that.fluidStack != null : !fluidStack.isFluidStackIdentical(that.fluidStack))
                return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Objects
                .hash(exists, isFluid, (int) temperature, fastFlux, thermalFlux, hasControlRod, controlRodInsertion);
        }
    }

    public static void writeToBuffer(PacketBuffer buf, ReactorGridSyncData data) {
        if (data == null) {
            buf.writeByte(0);
            return;
        }
        buf.writeByte(data.gridSize);
        buf.writeByte(data.coreDimension);
        buf.writeFloat(data.coreTemp);
        buf.writeFloat(data.avgTemp);
        buf.writeFloat((float) data.efficiency);
        buf.writeByte(data.pipeTier);
        buf.writeLong(data.directPowerEUt);
        buf.writeVarIntToBuffer(data.neutronsProduced);
        buf.writeVarIntToBuffer(data.fastAbsorbed);
        buf.writeVarIntToBuffer(data.thermalAbsorbed);
        buf.writeVarIntToBuffer(data.escapedNeutrons);
        buf.writeVarIntToBuffer(data.outputCoolantRate);
        ByteBufUtils.writeUTF8String(buf, data.outputCoolantName != null ? data.outputCoolantName : "");
        buf.writeBoolean(data.scram);
        buf.writeFloat(data.ambientTemp);
        buf.writeFloat(data.reactorDamage);
        buf.writeLong(data.cumulativeNeutrons);
        buf.writeVarIntToBuffer(data.ticksPerCycle);

        buf.writeVarIntToBuffer(data.cells.size());
        for (ReactorGridCellData cell : data.cells) {
            int mask = 0;
            if (cell.exists) mask |= 1;
            if (cell.isFluid) mask |= 2;
            if (cell.itemStack != null) mask |= 4;
            if (cell.fluidStack != null) mask |= 8;
            if (cell.isHighPressure) mask |= 16;
            if (cell.hasControlRod) mask |= 32;
            if (cell.isAdvancedControlRod) mask |= 64;
            buf.writeByte(mask);
            if (cell.exists) {
                buf.writeFloat(cell.temperature);
                buf.writeFloat(cell.heatOutput);
                buf.writeVarIntToBuffer(cell.fastFlux);
                buf.writeVarIntToBuffer(cell.thermalFlux);
                buf.writeVarIntToBuffer(cell.fastAbsorbed);
                buf.writeVarIntToBuffer(cell.thermalAbsorbed);
                buf.writeLong(cell.directEU);
                buf.writeLong(cell.cumulativeFluence);
                if (cell.hasControlRod) {
                    buf.writeByte(cell.controlRodInsertion);
                    buf.writeByte(cell.controlRodType);
                    if (cell.isAdvancedControlRod) {
                        buf.writeByte(cell.moderatorInsertion);
                        ByteBufUtils.writeUTF8String(buf, cell.moderatorName != null ? cell.moderatorName : "");
                    }
                }
                if (cell.itemStack != null) {
                    NetworkUtils.writeItemStack(buf, cell.itemStack);
                }
                if (cell.fluidStack != null) {
                    buf.writeVarIntToBuffer(cell.fluidStack.getFluidID());
                    buf.writeVarIntToBuffer(cell.fluidStack.amount);
                }
            }
        }
    }

    public static ReactorGridSyncData readFromBuffer(PacketBuffer buf) {
        ReactorGridSyncData data = new ReactorGridSyncData();
        data.gridSize = buf.readByte();
        if (data.gridSize == 0) return data;

        data.coreDimension = buf.readByte();
        data.coreTemp = buf.readFloat();
        data.avgTemp = buf.readFloat();
        data.efficiency = buf.readFloat();
        data.pipeTier = buf.readByte();
        data.directPowerEUt = buf.readLong();
        data.neutronsProduced = buf.readVarIntFromBuffer();
        data.fastAbsorbed = buf.readVarIntFromBuffer();
        data.thermalAbsorbed = buf.readVarIntFromBuffer();
        data.escapedNeutrons = buf.readVarIntFromBuffer();
        data.outputCoolantRate = buf.readVarIntFromBuffer();
        data.outputCoolantName = ByteBufUtils.readUTF8String(buf);
        data.scram = buf.readBoolean();
        data.ambientTemp = buf.readFloat();
        data.reactorDamage = buf.readFloat();
        data.cumulativeNeutrons = buf.readLong();
        data.ticksPerCycle = buf.readVarIntFromBuffer();

        int cellCount = buf.readVarIntFromBuffer();
        for (int i = 0; i < cellCount; i++) {
            ReactorGridCellData cell = new ReactorGridCellData();
            int mask = buf.readByte();
            cell.exists = (mask & 1) != 0;
            cell.isFluid = (mask & 2) != 0;
            boolean hasItem = (mask & 4) != 0;
            boolean hasFluid = (mask & 8) != 0;
            cell.isHighPressure = (mask & 16) != 0;
            cell.hasControlRod = (mask & 32) != 0;
            cell.isAdvancedControlRod = (mask & 64) != 0;
            if (cell.exists) {
                cell.temperature = buf.readFloat();
                cell.heatOutput = buf.readFloat();
                cell.fastFlux = buf.readVarIntFromBuffer();
                cell.thermalFlux = buf.readVarIntFromBuffer();
                cell.fastAbsorbed = buf.readVarIntFromBuffer();
                cell.thermalAbsorbed = buf.readVarIntFromBuffer();
                cell.directEU = buf.readLong();
                cell.cumulativeFluence = buf.readLong();
                if (cell.hasControlRod) {
                    cell.controlRodInsertion = buf.readByte() & 0xFF;
                    cell.controlRodType = buf.readByte() & 0xFF;
                    if (cell.isAdvancedControlRod) {
                        cell.moderatorInsertion = buf.readByte() & 0xFF;
                        cell.moderatorName = ByteBufUtils.readUTF8String(buf);
                    }
                }
                if (hasItem) {
                    cell.itemStack = NetworkUtils.readItemStack(buf);
                }
                if (hasFluid) {
                    int fluidId = buf.readVarIntFromBuffer();
                    int amount = buf.readVarIntFromBuffer();
                    Fluid fluid = FluidRegistry.getFluid(fluidId);
                    if (fluid != null) {
                        cell.fluidStack = new FluidStack(fluid, amount);
                    }
                }
            }
            data.cells.add(cell);
        }
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReactorGridSyncData that = (ReactorGridSyncData) o;
        if (gridSize != that.gridSize || pipeTier != that.pipeTier || directPowerEUt != that.directPowerEUt
            || ticksPerCycle != that.ticksPerCycle)
            return false;
        if (scram != that.scram || cumulativeNeutrons != that.cumulativeNeutrons) return false;
        if (Math.abs(ambientTemp - that.ambientTemp) > 0.5f) return false;
        if (Math.abs(coreTemp - that.coreTemp) > 0.5f) return false;
        if (Math.abs(avgTemp - that.avgTemp) > 0.5f) return false;
        if (Math.abs(efficiency - that.efficiency) > 0.005) return false;
        if (cells.size() != that.cells.size()) return false;
        for (int i = 0; i < cells.size(); i++) {
            if (!cells.get(i)
                .equals(that.cells.get(i))) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(gridSize, (int) coreTemp, pipeTier, cells.size());
    }
}
