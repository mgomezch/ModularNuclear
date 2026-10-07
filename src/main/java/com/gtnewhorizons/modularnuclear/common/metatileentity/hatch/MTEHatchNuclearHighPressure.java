package com.gtnewhorizons.modularnuclear.common.metatileentity.hatch;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopPump;
import com.gtnewhorizons.coolantloops.engine.ICoolantPassageHatch;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.nuclear.INuclearTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.NeutronType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularui.api.math.Color;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import gregtech.api.enums.Textures;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;

/**
 * Nuclear Core High-Pressure Hatch.
 *
 * Serves as the hermetic fluid passage endpoint bridging the MPTR core to closed coolant loops.
 * Pairs strictly by column coordinates (Slice 0 Top Inlet <-> Slice 4 Bottom Outlet).
 * Holds zero Forge fluids directly; couples convective heat exchange and radiolytic gas generation.
 */
public class MTEHatchNuclearHighPressure extends MTEHatch implements ICoolantPassageHatch, INuclearTile {

    public MTENuclearReactor mReactor = null;
    public double mTemperature = NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;
    public double mHeatEU = 0.0;

    protected boolean mIsInlet = true;
    protected IGregTechTileEntity mOppositeHatch = null;
    protected ICoolantLoopPump mConnectedPump = null;
    protected int mNeutronAccumulator = 0;

    public int mFastFlux = 0;
    public int mThermalFlux = 0;
    public int mFastAbsorbed = 0;
    public int mThermalAbsorbed = 0;
    public int mLastFastFlux = 0;
    public int mLastThermalFlux = 0;
    public int mLastFastAbsorbed = 0;
    public int mLastThermalAbsorbed = 0;
    public int mLastNeutronsGenerated = 0;
    public long mDirectEUProduced = 0;
    public boolean mUsedForCooling = false;

    public MTEHatchNuclearHighPressure(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier, 0, "Hermetic core passage endpoint for closed coolant loops");
    }

    public MTEHatchNuclearHighPressure(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchNuclearHighPressure(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        ITexture casing = Textures.BlockIcons.casingTexturePages[0][16];
        return new ITexture[] { casing, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_OUT) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        ITexture casing = Textures.BlockIcons.casingTexturePages[0][16];
        return new ITexture[] { casing, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_IN) };
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        ITexture casing = Textures.BlockIcons.casingTexturePages[0][16];
        if (side == aFacing) {
            return new ITexture[] { casing, TextureFactory
                .of(mIsInlet ? Textures.BlockIcons.OVERLAY_PIPE_IN : Textures.BlockIcons.OVERLAY_PIPE_OUT) };
        }
        return new ITexture[] { casing };
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return true;
    }

    @Override
    public boolean isAccessAllowed(EntityPlayer aPlayer) {
        return true;
    }

    // --- Zero Forge Fluid Invariants ---

    @Override
    public boolean canTankBeFilled() {
        return false;
    }

    @Override
    public boolean canTankBeEmptied() {
        return false;
    }

    @Override
    public int fill(ForgeDirection side, FluidStack resource, boolean doFill) {
        return 0;
    }

    @Override
    public FluidStack drain(ForgeDirection side, int maxDrain, boolean doDrain) {
        return null;
    }

    @Override
    public FluidStack drain(ForgeDirection side, FluidStack resource, boolean doDrain) {
        return null;
    }

    @Override
    public boolean canFill(ForgeDirection side, Fluid fluid) {
        return false;
    }

    @Override
    public boolean canDrain(ForgeDirection side, Fluid fluid) {
        return false;
    }

    @Override
    public boolean isFluidInputAllowed(FluidStack aFluid) {
        return false;
    }

    // --- ICoolantPassageHatch Implementation ---

    @Override
    public IGregTechTileEntity getOppositeHatchTile() {
        return mOppositeHatch;
    }

    public void setOppositeHatch(IGregTechTileEntity opposite) {
        this.mOppositeHatch = opposite;
    }

    @Override
    public boolean isPassageInlet() {
        return mIsInlet;
    }

    public void setIsInlet(boolean isInlet) {
        this.mIsInlet = isInlet;
    }

    @Override
    public void setConnectedPump(ICoolantLoopPump pump) {
        this.mConnectedPump = pump;
        if (mOppositeHatch != null && mOppositeHatch.getMetaTileEntity() instanceof MTEHatchNuclearHighPressure other) {
            if (other.mConnectedPump != pump) {
                other.mConnectedPump = pump;
            }
        }
    }

    @Override
    public ICoolantLoopPump getConnectedPump() {
        return mConnectedPump;
    }

    // --- ICoolantLoopDevice Implementation ---

    @Override
    public String getDeviceId() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null) {
            return String.format("core_passage_%d_%d_%d", te.getXCoord(), te.getYCoord(), te.getZCoord());
        }
        return "core_passage_unbound";
    }

    @Override
    public double getMinorLossK() {
        return 2.5; // Pressure tube core passage with fuel bundle spacers
    }

    @Override
    public double getDeviceTemperatureCelsius() {
        return mTemperature;
    }

    @Override
    public void processThermalExchange(double volumetricFlowRateM3s, double dtSeconds, CoolantFluidProperty fluid,
        LoopSegment segment) {
        if (volumetricFlowRateM3s <= 1e-6 || fluid == null || segment == null) {
            return;
        }
        double coolantTemp = segment.getCurrentTemperatureCelsius();
        double deltaT = mTemperature - coolantTemp;
        if (Math.abs(deltaT) < 1e-4) {
            return;
        }

        // Passage geometry: 10cm internal diameter, 5.0m height
        double diameter = 0.10;
        double length = 5.0;
        double area = Math.PI * diameter * length; // ~1.571 m^2
        double flowArea = Math.PI * Math.pow(diameter / 2.0, 2);
        double velocity = volumetricFlowRateM3s / flowArea;

        // Convective heat transfer coefficient h(v)
        double rho = fluid.getDensity();
        double mu = fluid.getDynamicViscosity();
        double kThermal = fluid.getThermalConductivity();
        double cp = fluid.getSpecificHeat();

        double re = (rho * velocity * diameter) / Math.max(1e-6, mu);
        double pr = (mu * cp) / Math.max(1e-4, kThermal);

        double nu;
        if (re < 2300.0) {
            nu = 4.36; // Fully developed laminar flow
        } else {
            nu = 0.023 * Math.pow(re, 0.8) * Math.pow(Math.max(0.6, pr), 0.4); // Turbulent Dittus-Boelter
        }

        double h = (nu * kThermal) / diameter;
        double qDotWatts = h * area * deltaT;

        // Limit heat transfer to prevent temperature overshooting
        double maxDeltaT = Math.abs(deltaT);
        double heatTransferredJoules = qDotWatts * dtSeconds;
        double heatTransferredEU = heatTransferredJoules / 128.0;

        double coreHeatCapacityEUPerC = NuclearSimulationEngine.EU_PER_DEGREE;
        double coreTempChange = heatTransferredEU / coreHeatCapacityEUPerC;

        if (Math.abs(coreTempChange) > maxDeltaT) {
            coreTempChange = Math.signum(coreTempChange) * maxDeltaT;
            heatTransferredEU = coreTempChange * coreHeatCapacityEUPerC;
            heatTransferredJoules = heatTransferredEU * 128.0;
        }

        mTemperature -= coreTempChange;

        // Coolant temperature rise
        double massFlowKgS = volumetricFlowRateM3s * rho;
        double coolantHeatCapacityRate = massFlowKgS * cp;
        if (coolantHeatCapacityRate > 1e-4) {
            double coolantTempRise = (heatTransferredJoules / dtSeconds) / coolantHeatCapacityRate;
            segment.setCurrentTemperatureCelsius(coolantTemp + coolantTempRise);
        }

        mUsedForCooling = true;
    }

    // --- INuclearTile Implementation ---

    @Override
    public double getTemperature() {
        return mTemperature;
    }

    @Override
    public void setTemperature(double temperature) {
        this.mTemperature = temperature;
    }

    @Override
    public void addHeat(double heatEU) {
        this.mTemperature += heatEU / NuclearSimulationEngine.EU_PER_DEGREE;
    }

    @Override
    public double getHeatTransferCoeff() {
        return 0.90; // High thermal conductance in metal pressure tube
    }

    @Override
    public boolean isFuel() {
        return false;
    }

    @Override
    public int generateNeutrons(double efficiency) {
        return 0;
    }

    @Override
    public double getAbsorptionProbability(NeutronType type) {
        return type == NeutronType.THERMAL ? 0.05 : 0.02;
    }

    @Override
    public double getScatteringProbability(NeutronType type) {
        return 0.80;
    }

    @Override
    public double getModerationProbability() {
        return 0.85; // Coolant passage acts as a moderator
    }

    @Override
    public void onNeutronAbsorbed(NeutronType type, int count) {
        if (type == NeutronType.FAST) {
            mFastAbsorbed += count;
        } else {
            mThermalAbsorbed += count;
        }

        // Radiolytic gas generation: every 4 absorbed neutrons transmutes 2L coolant
        if (count > 0) {
            mNeutronAccumulator += count;
            if (mConnectedPump != null && mNeutronAccumulator >= 4) {
                int cycles = mNeutronAccumulator / 4;
                mNeutronAccumulator %= 4;
                CoolantFluidProperty prop = mConnectedPump.getCoolantFluidProperty();
                String name = prop != null ? prop.getFluidName()
                    .toLowerCase() : "water";
                mConnectedPump.consumeCoolant(cycles * 2L);
                if (name.contains("heavy")) {
                    // 2 D2O -> 2 T2 + 1 O2
                    mConnectedPump.addDissolvedGas("Tritium", cycles * 2L);
                    mConnectedPump.addDissolvedGas("Oxygen", cycles * 1L);
                } else {
                    // 2 H2O -> 2 D2 + 1 O2
                    mConnectedPump.addDissolvedGas("Deuterium", cycles * 2L);
                    mConnectedPump.addDissolvedGas("Oxygen", cycles * 1L);
                }
            }
        }
    }

    @Override
    public void onNeutronScattered(NeutronType type, int count) {}

    @Override
    public void addNeutronFlux(NeutronType type, int count) {
        if (type == NeutronType.FAST) {
            mFastFlux += count;
        } else {
            mThermalFlux += count;
        }
    }

    @Override
    public void nuclearTick(double efficiency) {
        mLastFastFlux = mFastFlux;
        mLastThermalFlux = mThermalFlux;
        mLastFastAbsorbed = mFastAbsorbed;
        mLastThermalAbsorbed = mThermalAbsorbed;
        mFastFlux = 0;
        mThermalFlux = 0;
        mFastAbsorbed = 0;
        mThermalAbsorbed = 0;
    }

    // --- NBT Persistence ---

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setDouble("mTemperature", mTemperature);
        aNBT.setBoolean("mIsInlet", mIsInlet);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("mTemperature")) {
            mTemperature = aNBT.getDouble("mTemperature");
        }
        if (aNBT.hasKey("mIsInlet")) {
            mIsInlet = aNBT.getBoolean("mIsInlet");
        }
    }

    // --- ModularUI GUI ---

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (aPlayer != null && aPlayer.getHeldItem() != null && mReactor != null) {
            if (mReactor.handleSensorCardLinking(aPlayer.getHeldItem(), aPlayer, mReactor.getBaseMetaTileEntity())) {
                return true;
            }
        }
        openGui(aPlayer);
        return true;
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        builder.widget(
            new DrawableWidget().setDrawable(GTUITextures.PICTURE_SCREEN_BLACK)
                .setPos(7, 16)
                .setSize(160, 80))
            .widget(
                new TextWidget("High-Pressure Passage").setDefaultColor(Color.rgb(255, 128, 0))
                    .setPos(10, 20))
            .widget(
                new TextWidget()
                    .setStringSupplier(
                        () -> String.format("Role: %s", mIsInlet ? "Core Inlet (Slice 0)" : "Core Outlet (Slice 4)"))
                    .setDefaultColor(Color.rgb(200, 200, 200))
                    .setPos(10, 32))
            .widget(
                new TextWidget().setStringSupplier(() -> String.format("Core Temp: %.1f °C", mTemperature))
                    .setDefaultColor(Color.rgb(255, 200, 0))
                    .setPos(10, 44))
            .widget(
                new TextWidget()
                    .setStringSupplier(
                        () -> String.format("Fast: %d n/t  Thrm: %d n/t", mLastFastFlux, mLastThermalFlux))
                    .setDefaultColor(Color.rgb(100, 200, 255))
                    .setPos(10, 56))
            .widget(
                new TextWidget()
                    .setStringSupplier(
                        () -> String.format("Paired: %s", mOppositeHatch != null ? "Linked" : "Unpaired"))
                    .setDefaultColor(Color.rgb(150, 255, 150))
                    .setPos(10, 68))
            .widget(
                new TextWidget()
                    .setStringSupplier(
                        () -> String.format("Pump: %s", mConnectedPump != null ? "Connected" : "Disconnected"))
                    .setDefaultColor(Color.rgb(150, 180, 255))
                    .setPos(10, 80));
    }
}
