package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.HeatExchangerData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.HeatVentData;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressure;
import com.gtnewhorizons.modularnuclear.common.nuclear.INuclearTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.NeutronType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;
import com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification;

import gtnhlanth.common.hatch.MTEHatchInputBeamline;

public class NuclearGridTile implements INuclearTile {

    private final MTENuclearReactor reactor;
    private final MTEHatchNuclearBus bus;
    private final MTEHatchNuclearHatch hatch;
    private final MTEHatchNuclearHighPressure highPressureHatch;
    private MTEHatchNuclearControlRod bottomControlRod;
    private MTEHatchInputBeamline bottomBeamInput;
    private final int gx;
    private final int gy;

    // Cached property fields
    private boolean isFuel;
    private double insulationDampening;
    private double heatTransferCoeff = 0.05;
    private boolean isHeatVent;
    private boolean isHeatExchanger;
    private boolean isNeutronSource;
    private com.gtnewhorizons.modularnuclear.common.config.data.FuelData cachedFuelData;
    private NuclearFuelType cachedFuelType;
    private com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix cachedCustomFuelMix;
    private com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData cachedModeratorData;
    private HeatVentData cachedHeatVentData;
    private HeatExchangerData cachedHeatExchangerData;
    private double pAbsorptionFast = 0.01;
    private double pAbsorptionThermal = 0.01;
    private double pScatteringFast = 0.02;
    private double pScatteringThermal = 0.02;
    private double pModeration = 0.05;
    private int neutronEmissionCount = 1;
    private double controlRodAbsorptionFractionFast = 0.0;
    private double controlRodAbsorptionFractionThermal = 0.0;

    // Change-tracking state
    private long lastItemKey = Long.MIN_VALUE;
    private int lastItemDamage = -1;
    private Object lastFluid = null;
    private int lastAmount = -1;
    private int lastPipes = -1;
    private boolean lastScram = false;
    private boolean dirty = true;

    public NuclearGridTile(MTENuclearReactor reactor, MTEHatchNuclearBus bus, int gx, int gy) {
        this.reactor = reactor;
        this.bus = bus;
        this.hatch = null;
        this.highPressureHatch = null;
        this.gx = gx;
        this.gy = gy;
        refreshProperties();
    }

    public NuclearGridTile(MTENuclearReactor reactor, MTEHatchNuclearHatch hatch, int gx, int gy) {
        this.reactor = reactor;
        this.bus = null;
        this.hatch = hatch;
        this.highPressureHatch = null;
        this.gx = gx;
        this.gy = gy;
        refreshProperties();
    }

    public NuclearGridTile(MTENuclearReactor reactor, MTEHatchNuclearControlRod controlRod, int gx, int gy) {
        this.reactor = reactor;
        this.bus = null;
        this.hatch = null;
        this.bottomControlRod = controlRod;
        this.highPressureHatch = null;
        this.gx = gx;
        this.gy = gy;
        refreshProperties();
    }

    public NuclearGridTile(MTENuclearReactor reactor, MTEHatchNuclearHighPressure highPressureHatch, int gx, int gy) {
        this.reactor = reactor;
        this.bus = null;
        this.hatch = null;
        this.highPressureHatch = highPressureHatch;
        this.gx = gx;
        this.gy = gy;
        refreshProperties();
    }

    public NuclearGridTile(MTENuclearReactor reactor, MTEHatchPressurizedFluid pressurizedHatch, int gx, int gy) {
        this.reactor = reactor;
        this.bus = null;
        this.hatch = null;
        this.highPressureHatch = new MTEHatchNuclearHighPressure("core_passage_adapter", 5, new String[0], null);
        if (pressurizedHatch != null) {
            this.highPressureHatch.setBaseMetaTileEntity(pressurizedHatch.getBaseMetaTileEntity());
            this.highPressureHatch.mReactor = reactor;
            this.highPressureHatch.setOppositeHatch(pressurizedHatch.getOppositeHatchTile());
            this.highPressureHatch.setConnectedPump(pressurizedHatch.getConnectedPump());
            this.highPressureHatch.setTemperature(pressurizedHatch.getDeviceTemperatureCelsius());
            pressurizedHatch.setThermalExchangeListener((flowRate, dt, fluid, seg) -> {
                this.highPressureHatch.setConnectedPump(pressurizedHatch.getConnectedPump());
                this.highPressureHatch.processThermalExchange(flowRate, dt, fluid, seg);
                pressurizedHatch.setCurrentTemperatureCelsius(this.highPressureHatch.getTemperature());
            });
        }
        this.gx = gx;
        this.gy = gy;
        refreshProperties();
    }

    public MTENuclearReactor getReactor() {
        return reactor;
    }

    @Override
    public boolean hasControlRod() {
        return bottomControlRod != null;
    }

    @Override
    public double getControlRodAbsorptionFraction(NeutronType type) {
        return (type == NeutronType.FAST) ? controlRodAbsorptionFractionFast : controlRodAbsorptionFractionThermal;
    }

    @Override
    public int getControlRodMiningPipes() {
        return (bottomControlRod != null) ? bottomControlRod.getMiningPipeCount() : 5;
    }

    public MTEHatchNuclearControlRod getBottomControlRod() {
        return bottomControlRod;
    }

    public void setBottomControlRod(MTEHatchNuclearControlRod bottomControlRod) {
        this.bottomControlRod = bottomControlRod;
        this.dirty = true;
        refreshProperties();
    }

    public boolean hasBottomBeamInput() {
        return bottomBeamInput != null;
    }

    public MTEHatchInputBeamline getBottomBeamInput() {
        return bottomBeamInput;
    }

    public void setBottomBeamInput(MTEHatchInputBeamline bottomBeamInput) {
        this.bottomBeamInput = bottomBeamInput;
        this.dirty = true;
        refreshProperties();
    }

    @Override
    public boolean isNeutronSource() {
        return isNeutronSource;
    }

    public boolean isBus() {
        return bus != null;
    }

    public boolean isHatch() {
        return hatch != null;
    }

    public boolean isControlRod() {
        return bottomControlRod != null && bus == null && hatch == null && highPressureHatch == null;
    }

    public boolean isHighPressureHatch() {
        return highPressureHatch != null;
    }

    public MTEHatchNuclearBus getBus() {
        return bus;
    }

    public MTEHatchNuclearHatch getHatch() {
        return hatch;
    }

    public MTEHatchNuclearControlRod getControlRod() {
        return bottomControlRod;
    }

    public MTEHatchNuclearHighPressure getHighPressureHatch() {
        return highPressureHatch;
    }

    public double getHeatOutput() {
        if (isHatch()) return hatch.mLastHeatOutput;
        if (isHighPressureHatch()) return highPressureHatch.mLastHeatOutput;
        if (isBus()) return bus.mLastHeatOutput;
        return 0.0;
    }

    public int getGx() {
        return gx;
    }

    public int getGy() {
        return gy;
    }

    @Override
    public double getTemperature() {
        return reactor.getTileTemperature(this);
    }

    @Override
    public void setTemperature(double temp) {
        reactor.setTileTemperature(this, temp);
    }

    @Override
    public void addHeat(double heatHu) {
        reactor.addTileHeat(this, heatHu);
    }

    @Override
    public double getHeatTransferCoeff() {
        return heatTransferCoeff;
    }

    @Override
    public boolean isFuel() {
        return isFuel;
    }

    @Override
    public NuclearFuelType getFuelType() {
        return cachedFuelType;
    }

    @Override
    public com.gtnewhorizons.modularnuclear.common.config.data.FuelData getFuelData() {
        return cachedFuelData;
    }

    @Override
    public com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix getCustomFuelMix() {
        return cachedCustomFuelMix;
    }

    @Override
    public com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData getModeratorData() {
        return cachedModeratorData;
    }

    @Override
    public void addWignerEnergy(double amountHu) {
        if (isBus() && getBus() != null) {
            getBus().addWignerEnergy(amountHu);
        }
    }

    @Override
    public double releaseWignerEnergy() {
        if (isBus() && getBus() != null) {
            return getBus().releaseWignerEnergy();
        }
        return 0.0;
    }

    @Override
    public double getStoredWignerEnergy() {
        if (isBus() && getBus() != null) {
            return getBus().getStoredWignerEnergy();
        }
        return 0.0;
    }

    @Override
    public double getThermalNeutronEmissionYield(int fastNeutronsAbsorbed) {
        return 0.0;
    }

    @Override
    public int generateNeutrons(double efficiency) {
        return (reactor != null) ? reactor.generateTileNeutrons(this, efficiency) : 0;
    }

    @Override
    public int getNeutronEmissionCount() {
        return neutronEmissionCount;
    }

    @Override
    public double getAbsorptionProbability(NeutronType type) {
        return (type == NeutronType.FAST) ? pAbsorptionFast : pAbsorptionThermal;
    }

    @Override
    public double getScatteringProbability(NeutronType type) {
        return (type == NeutronType.FAST) ? pScatteringFast : pScatteringThermal;
    }

    @Override
    public double getModerationProbability() {
        return pModeration;
    }

    @Override
    public void onNeutronAbsorbed(NeutronType type, int count) {
        if (reactor != null) {
            reactor.onTileNeutronAbsorbed(this, type, count);
        }
    }

    @Override
    public void onNeutronScattered(NeutronType type, int count) {
        if (reactor != null) {
            reactor.onTileNeutronScattered(this, type, count);
        }
    }

    @Override
    public void addNeutronFlux(NeutronType type, int count) {
        if (reactor != null) {
            reactor.addTileNeutronFlux(this, type, count);
        }
    }

    @Override
    public void nuclearTick(double efficiency) {
        if (reactor != null) {
            reactor.processTileNuclearTick(this, efficiency);
        }
    }

    @Override
    public double getInsulationDampening() {
        return insulationDampening;
    }

    @Override
    public boolean isHeatVent() {
        return isHeatVent;
    }

    @Override
    public HeatVentData getHeatVentData() {
        return cachedHeatVentData;
    }

    @Override
    public boolean isHeatExchanger() {
        return isHeatExchanger;
    }

    @Override
    public HeatExchangerData getHeatExchangerData() {
        return cachedHeatExchangerData;
    }

    @Override
    public boolean drainEnergy(long eu) {
        return reactor != null && reactor.drainEnergyUnits(eu);
    }

    @Override
    public void voidComponent() {
        if (isBus() && getBus() != null) {
            getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
            if (reactor != null) {
                reactor.mCycleZeroedCoolantItems++;
            }
            getBus().markTileDirty();
            this.dirty = true;
            refreshProperties();
        }
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void refreshTileProperties() {
        refreshProperties();
    }

    public void refreshProperties() {
        ItemStack stack = (bus != null) ? bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] : null;
        long itemKey = (stack != null) ? ReactorDataRegistry.getItemKey(stack) : 0L;
        int damage = (stack != null) ? stack.getItemDamage() : 0;
        boolean damageable = (stack != null) && stack.isItemStackDamageable();

        net.minecraftforge.fluids.FluidStack fluid = (hatch != null) ? hatch.mInputFluid : null;
        Object currentFluid = (fluid != null) ? fluid.getFluid() : null;
        int currentAmount = (fluid != null) ? fluid.amount : 0;

        int pipes = (bottomControlRod != null) ? bottomControlRod.getMiningPipeCount() : -1;
        boolean scram = (bottomControlRod != null) && bottomControlRod.mScram;

        if (!dirty && itemKey == lastItemKey
            && (damageable || damage == lastItemDamage)
            && currentFluid == lastFluid
            && (currentAmount > 0) == (lastAmount > 0)
            && pipes == lastPipes
            && scram == lastScram) {
            return;
        }

        lastItemKey = itemKey;
        lastItemDamage = damage;
        lastFluid = currentFluid;
        lastAmount = currentAmount;
        lastPipes = pipes;
        lastScram = scram;
        dirty = false;

        if (reactor != null) {
            this.isFuel = NuclearReactorPhysics.isTileFuel(reactor, this);
            this.heatTransferCoeff = NuclearReactorPhysics.getTileHeatTransferCoeff(reactor, this);
            this.insulationDampening = reactor.getTileInsulationDampening(this);
            this.pAbsorptionFast = NuclearReactorPhysics.getTileAbsorptionProbability(reactor, this, NeutronType.FAST);
            this.pAbsorptionThermal = NuclearReactorPhysics
                .getTileAbsorptionProbability(reactor, this, NeutronType.THERMAL);
            this.pScatteringFast = NuclearReactorPhysics.getTileScatteringProbability(reactor, this, NeutronType.FAST);
            this.pScatteringThermal = NuclearReactorPhysics
                .getTileScatteringProbability(reactor, this, NeutronType.THERMAL);
        } else {
            if (isBus() && bus != null) {
                this.isFuel = NuclearFuelClassification.isItemFuel(stack);
                this.insulationDampening = NuclearFuelClassification.getInsulationDampening(stack);
            } else if (isHatch() && hatch != null) {
                this.isFuel = NuclearFuelClassification.isFluidFuel(fluid);
                this.insulationDampening = 0.0;
            } else {
                this.isFuel = false;
                this.insulationDampening = 0.0;
            }
            this.heatTransferCoeff = 0.05;
            this.pAbsorptionFast = 0.01;
            this.pAbsorptionThermal = 0.01;
            this.pScatteringFast = 0.02;
            this.pScatteringThermal = 0.02;
        }

        this.cachedFuelType = NuclearReactorPhysics.getTileFuelType(this);
        this.cachedCustomFuelMix = NuclearReactorPhysics.getTileCustomFuelMix(this);
        this.cachedFuelData = NuclearReactorPhysics.getTileFuelData(this);
        this.cachedModeratorData = NuclearReactorPhysics.getTileModeratorData(this);
        this.pModeration = NuclearReactorPhysics.getTileModerationProbability(this);
        this.neutronEmissionCount = NuclearReactorPhysics.getTileNeutronEmissionCount(this);

        if (isBus() && bus != null && stack != null) {
            this.isNeutronSource = NuclearFuelClassification.isItemNeutronSource(stack);
            this.isHeatVent = NuclearFuelClassification.isItemHeatVent(stack);
            this.cachedHeatVentData = this.isHeatVent ? ReactorDataRegistry.findHeatVent(stack) : null;
            this.isHeatExchanger = NuclearFuelClassification.isItemHeatExchanger(stack);
            this.cachedHeatExchangerData = this.isHeatExchanger ? ReactorDataRegistry.findHeatExchanger(stack) : null;
        } else {
            this.isNeutronSource = false;
            this.isHeatVent = false;
            this.cachedHeatVentData = null;
            this.isHeatExchanger = false;
            this.cachedHeatExchangerData = null;
        }

        if (bottomControlRod == null) {
            this.controlRodAbsorptionFractionFast = 0.0;
            this.controlRodAbsorptionFractionThermal = 0.0;
        } else {
            double pRodFast = bottomControlRod.getAbsorptionProbability(NeutronType.FAST);
            double pBaseFast = (reactor != null)
                ? NuclearReactorPhysics.getBaseAbsorptionProbability(reactor, this, NeutronType.FAST)
                : 0.01;
            double sumFast = pBaseFast + pRodFast;
            this.controlRodAbsorptionFractionFast = (sumFast > 0.0) ? (pRodFast / sumFast) : 0.0;

            double pRodTherm = bottomControlRod.getAbsorptionProbability(NeutronType.THERMAL);
            double pBaseTherm = (reactor != null)
                ? NuclearReactorPhysics.getBaseAbsorptionProbability(reactor, this, NeutronType.THERMAL)
                : 0.01;
            double sumTherm = pBaseTherm + pRodTherm;
            this.controlRodAbsorptionFractionThermal = (sumTherm > 0.0) ? (pRodTherm / sumTherm) : 0.0;
        }
    }
}
