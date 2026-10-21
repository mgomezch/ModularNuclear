package com.gtnewhorizons.modularnuclear.common.metatileentity.hatch;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid;
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
import gregtech.api.interfaces.modularui.IAddGregtechLogo;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTSplit;

/**
 * Nuclear Core High-Pressure Hatch.
 *
 * Serves as the hermetic fluid passage endpoint bridging the MPTR core to closed coolant loops.
 * Pairs strictly by column coordinates (Slice 0 Top Inlet <-> Slice 4 Bottom Outlet).
 * Holds zero Forge fluids directly; couples convective heat exchange and radiolytic gas generation.
 */
public class MTEHatchNuclearHighPressure extends MTEHatch
    implements ICoolantPassageHatch, INuclearTile, IAddGregtechLogo {

    public MTENuclearReactor mReactor = null;
    public double mTemperature = NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;
    public double mHeatHu = 0.0;
    public double mLastHeatOutput = 0.0;
    public double mHeatOutputAccumulator = 0.0;
    public long mCumulativeFluence = 0L;

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
    public double mIncomingCoolantTemp = NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;

    public MTEHatchNuclearHighPressure(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier, 0, (String) null);
    }

    public MTEHatchNuclearHighPressure(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalized("gt.blockmachines.hatch.nuclearhighpressure.desc");
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchNuclearHighPressure(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        ITexture casing = Textures.BlockIcons.casingTexturePages[0][16];
        return new ITexture[] { casing, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_OUT),
            TextureFactory.of(MTEHatchPressurizedFluid.OVERLAY_PRESSURIZED_OUT) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        ITexture casing = Textures.BlockIcons.casingTexturePages[0][16];
        return new ITexture[] { casing, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_IN),
            TextureFactory.of(MTEHatchPressurizedFluid.OVERLAY_PRESSURIZED_IN) };
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        ITexture casing = Textures.BlockIcons.casingTexturePages[0][16];
        if (side == aFacing) {
            return new ITexture[] { casing,
                TextureFactory
                    .of(mIsInlet ? Textures.BlockIcons.OVERLAY_PIPE_IN : Textures.BlockIcons.OVERLAY_PIPE_OUT),
                TextureFactory.of(
                    mIsInlet ? MTEHatchPressurizedFluid.OVERLAY_PRESSURIZED_IN
                        : MTEHatchPressurizedFluid.OVERLAY_PRESSURIZED_OUT) };
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
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity != null && aBaseMetaTileEntity.isServerSide()) {
            aBaseMetaTileEntity.tryDisableTicking();
        }
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

    private String cachedDeviceId;

    @Override
    public String getDeviceId() {
        if (cachedDeviceId != null) return cachedDeviceId;
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null) {
            cachedDeviceId = "core_passage_" + te.getXCoord() + "_" + te.getYCoord() + "_" + te.getZCoord();
            return cachedDeviceId;
        }
        return "core_passage_unbound";
    }

    @Override
    public double getMinorLossK() {
        return 2.5; // Pressure tube core passage with fuel bundle spacers
    }

    @Override
    public double getDeviceTemperatureCelsius() {
        return getTemperature();
    }

    @Override
    public void processThermalExchange(double volumetricFlowRateM3s, double dtSeconds, CoolantFluidProperty fluid,
        LoopSegment segment) {
        if (volumetricFlowRateM3s <= 1e-6 || fluid == null || segment == null) {
            return;
        }
        double coolantTemp = segment.getCurrentTemperatureCelsius();
        this.mIncomingCoolantTemp = coolantTemp;
        if (mOppositeHatch != null && mOppositeHatch.getMetaTileEntity() instanceof MTEHatchNuclearHighPressure other) {
            other.mIncomingCoolantTemp = coolantTemp;
        }
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

        double coreHeatCapacityHuPerC = NuclearSimulationEngine.HU_PER_DEGREE;
        double coreHeatCapacityJoulesPerC = coreHeatCapacityHuPerC * 128.0;

        double segmentMassKg = segment.getVolume() * rho;
        double segmentHeatCapacity = segmentMassKg * cp;

        // Limit heat transfer to equilibrium so temperatures cannot cross over in a single tick
        if (segmentHeatCapacity > 1e-4) {
            double maxEquilibriumHeatJoules = (coreHeatCapacityJoulesPerC * segmentHeatCapacity)
                / (coreHeatCapacityJoulesPerC + segmentHeatCapacity)
                * maxDeltaT;
            if (Math.abs(heatTransferredJoules) > maxEquilibriumHeatJoules) {
                heatTransferredJoules = Math.signum(heatTransferredJoules) * maxEquilibriumHeatJoules;
            }
        } else {
            if (Math.abs(heatTransferredJoules) > coreHeatCapacityJoulesPerC * maxDeltaT) {
                heatTransferredJoules = Math.signum(heatTransferredJoules) * coreHeatCapacityJoulesPerC * maxDeltaT;
            }
        }

        double heatTransferredHu = heatTransferredJoules / 128.0;
        double coreTempChange = heatTransferredHu / coreHeatCapacityHuPerC;

        mTemperature -= coreTempChange;
        if (mOppositeHatch != null && mOppositeHatch.getMetaTileEntity() instanceof MTEHatchNuclearHighPressure other) {
            other.mTemperature = this.mTemperature;
        }
        if (coreTempChange > 0) {
            mHeatOutputAccumulator += heatTransferredHu;
        }

        // Coolant temperature rise: strict finite-volume energy conservation Q = m_seg * cp * deltaT
        if (segmentHeatCapacity > 1e-4) {
            double coolantTempRise = heatTransferredJoules / segmentHeatCapacity;
            segment.setCurrentTemperatureCelsius(coolantTemp + coolantTempRise);
        }

        mUsedForCooling = true;
    }

    // --- INuclearTile Implementation ---

    @Override
    public double getTemperature() {
        if (!mIsInlet && mOppositeHatch != null
            && mOppositeHatch.getMetaTileEntity() instanceof MTEHatchNuclearHighPressure other) {
            return other.mTemperature;
        }
        return mTemperature;
    }

    @Override
    public void setTemperature(double temperature) {
        this.mTemperature = temperature;
        if (mOppositeHatch != null && mOppositeHatch.getMetaTileEntity() instanceof MTEHatchNuclearHighPressure other) {
            other.mTemperature = temperature;
        }
    }

    @Override
    public void addHeat(double heatHu) {
        this.mTemperature += heatHu / NuclearSimulationEngine.HU_PER_DEGREE;
        if (mOppositeHatch != null && mOppositeHatch.getMetaTileEntity() instanceof MTEHatchNuclearHighPressure other) {
            other.mTemperature = this.mTemperature;
        }
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
        if (mConnectedPump != null && mConnectedPump.getCoolantFluidProperty() != null) {
            String name = mConnectedPump.getCoolantFluidProperty()
                .getFluidName();
            com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantFluidData cfd = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
                .findCoolantFluid(name);
            if (cfd != null) {
                return cfd.moderation;
            }
            return 0.05;
        }
        return 0.05;
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
                String name = prop != null ? prop.getFluidName() : "ic2distilledwater";
                com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantFluidData cfd = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
                    .findCoolantFluid(name);
                if (cfd != null && cfd.transmutationProduct != null) {
                    if ("tritium".equalsIgnoreCase(cfd.transmutationProduct)) {
                        // 2 D2O -> 2 T2 + 1 O2
                        mConnectedPump.consumeCoolant(cycles * 2L);
                        mConnectedPump.addDissolvedGas("Tritium", cycles * 2L);
                        mConnectedPump.addDissolvedGas("Oxygen", cycles * 1L);
                    } else if ("deuterium".equalsIgnoreCase(cfd.transmutationProduct)) {
                        // 2 H2O -> 2 D2 + 1 O2
                        mConnectedPump.consumeCoolant(cycles * 2L);
                        mConnectedPump.addDissolvedGas("Deuterium", cycles * 2L);
                        mConnectedPump.addDissolvedGas("Oxygen", cycles * 1L);
                    }
                }
                // Radiolysis is strictly disallowed for any fluid other than fluids configured with deuterium/tritium
                // transmutationProduct
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
        mCumulativeFluence += (mFastFlux + mThermalFlux);
        mLastFastFlux = mFastFlux;
        mLastThermalFlux = mThermalFlux;
        mLastFastAbsorbed = mFastAbsorbed;
        mLastThermalAbsorbed = mThermalAbsorbed;
        mLastHeatOutput = mHeatOutputAccumulator;
        mHeatOutputAccumulator = 0.0;
        mFastFlux = 0;
        mThermalFlux = 0;
        mFastAbsorbed = 0;
        mThermalAbsorbed = 0;
        if (mOppositeHatch != null && mOppositeHatch.getMetaTileEntity() instanceof MTEHatchNuclearHighPressure other) {
            other.mTemperature = this.mTemperature;
            other.mLastFastFlux = this.mLastFastFlux;
            other.mLastThermalFlux = this.mLastThermalFlux;
            other.mLastFastAbsorbed = this.mLastFastAbsorbed;
            other.mLastThermalAbsorbed = this.mLastThermalAbsorbed;
            other.mLastHeatOutput = this.mLastHeatOutput;
            other.mCumulativeFluence = this.mCumulativeFluence;
            other.mIncomingCoolantTemp = this.mIncomingCoolantTemp;
        }
    }

    // --- NBT Persistence ---

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setDouble("mTemperature", mTemperature);
        aNBT.setDouble("mHeatHu", mHeatHu);
        aNBT.setLong("mCumulativeFluence", mCumulativeFluence);
        aNBT.setBoolean("mIsInlet", mIsInlet);
        aNBT.setDouble("mIncomingCoolantTemp", mIncomingCoolantTemp);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("mTemperature")) {
            mTemperature = aNBT.getDouble("mTemperature");
        }
        if (aNBT.hasKey("mHeatHu")) {
            mHeatHu = aNBT.getDouble("mHeatHu");
        }
        if (aNBT.hasKey("mCumulativeFluence")) {
            mCumulativeFluence = aNBT.getLong("mCumulativeFluence");
        }
        if (aNBT.hasKey("mIsInlet")) {
            mIsInlet = aNBT.getBoolean("mIsInlet");
        }
        if (aNBT.hasKey("mIncomingCoolantTemp")) {
            mIncomingCoolantTemp = aNBT.getDouble("mIncomingCoolantTemp");
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
    public int getGUIHeight() {
        return 192;
    }

    @Override
    public void addGregTechLogo(ModularWindow.Builder builder) {
        // No-op: the diagnostic terminal screen occupies the upper panel
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        builder.widget(
            new DrawableWidget().setDrawable(GTUITextures.PICTURE_SCREEN_BLACK)
                .setPos(7, 16)
                .setSize(160, 82))
            .widget(
                new TextWidget("High-Pressure Passage").setDefaultColor(Color.rgb(255, 128, 0))
                    .setPos(10, 21))
            .widget(
                new TextWidget()
                    .setStringSupplier(
                        () -> String.format("Role: %s", mIsInlet ? "Core inlet (slice 0)" : "Core outlet (slice 4)"))
                    .setDefaultColor(Color.rgb(200, 200, 200))
                    .setPos(10, 33))
            .widget(
                new TextWidget().setStringSupplier(() -> String.format("Core temp: %.1f °C", getTemperature()))
                    .setDefaultColor(Color.rgb(255, 200, 0))
                    .setPos(10, 45))
            .widget(
                new TextWidget()
                    .setStringSupplier(
                        () -> String.format("Fast: %d n/t  Thrm: %d n/t", mLastFastFlux, mLastThermalFlux))
                    .setDefaultColor(Color.rgb(100, 200, 255))
                    .setPos(10, 57))
            .widget(
                new TextWidget()
                    .setStringSupplier(
                        () -> String.format("Paired: %s", mOppositeHatch != null ? "Linked" : "Unpaired"))
                    .setDefaultColor(Color.rgb(150, 255, 150))
                    .setPos(10, 69))
            .widget(
                new TextWidget()
                    .setStringSupplier(
                        () -> String.format("Pump: %s", mConnectedPump != null ? "Connected" : "Disconnected"))
                    .setDefaultColor(Color.rgb(150, 180, 255))
                    .setPos(10, 81));
    }
}
