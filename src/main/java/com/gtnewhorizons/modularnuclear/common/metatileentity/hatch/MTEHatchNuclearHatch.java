package com.gtnewhorizons.modularnuclear.common.metatileentity.hatch;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;
import static gregtech.api.enums.Textures.BlockIcons.FLUID_IN_SIGN;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_COLORS;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_IN;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import com.cleanroommc.modularui.utils.fluid.FluidStackTank;
import com.gtnewhorizons.modularnuclear.common.block.BlockNuclearCasing;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData;
import com.gtnewhorizons.modularnuclear.common.gui.ColorUtils;
import com.gtnewhorizons.modularnuclear.common.gui.MTEHatchNuclearHatchGui;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.util.FluidRoles;
import com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification;
import com.gtnewhorizons.modularnuclear.common.util.Swallowed;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.FluidSlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.Textures;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTSplit;

/**
 * Dumb fluid container hatch for the modular nuclear reactor.
 * Holds input coolant or liquid nuclear fuel.
 * All nuclear physics, boiling, and transmutation logic is processed by MTENuclearReactor.
 */
public class MTEHatchNuclearHatch extends MTEHatch implements IReadyBeforeFirstTick {

    public FluidStack mInputFluid;
    public final int mCapacity;
    public MTENuclearReactor mReactor;

    public double mTemperature = NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;
    public double mHeatHu = 0.0;
    public long mCumulativeFluence = 0L;
    public int mFastFlux = 0;
    public int mThermalFlux = 0;
    public int mFastAbsorbed = 0;
    public int mThermalAbsorbed = 0;
    public int mLastFastFlux = 0;
    public int mLastThermalFlux = 0;
    public int mLastFastAbsorbed = 0;
    public int mLastThermalAbsorbed = 0;
    public int mLastNeutronsGenerated = 0;
    /** Chain-reaction emission rate in neutrons per second (see TileProperties.chainRate); not persisted. */
    public double mChainRate = 0.0;
    public int mLastProducedAmount = 0;
    public String mLastProducedFluidName = "";
    public double mLastHeatOutput = 0.0;
    /** Heat moved into the coolant during this cycle's conduction sub-steps, consumed by the nuclear tick. */
    public double mPendingCoolantHeat = 0.0;
    public int mReactorPipeTier = -1;
    public boolean mWasDry = false;
    public boolean mUsedForCooling = false;

    public int getReactorPipeTier() {
        return mReactorPipeTier;
    }

    public int getTicksPerCycle() {
        return mReactor != null ? mReactor.getTicksPerCycle() : NuclearSimulationEngine.params().ticksPerCycle;
    }

    public void setReactorPipeTier(int aTier) {
        this.mReactorPipeTier = aTier;
    }

    public static int getRequiredFluidTier(String fluidName) {
        return NuclearSimulationEngine.getRequiredFluidTier(fluidName);
    }

    public MTEHatchNuclearHatch(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier, 0, (String) null);
        this.mCapacity = 8000 * (1 << aTier);
    }

    public MTEHatchNuclearHatch(String aName, int aTier, int aCapacity, String[] aDescription,
        ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
        this.mCapacity = aCapacity;
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalizedFormatted(
            "gt.blockmachines.hatch.nuclearhatch.desc",
            GTValues.VN[mTier],
            formatNumber(mCapacity),
            NuclearSimulationEngine.getHatchConductance(mTier));
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchNuclearHatch(mName, mTier, mCapacity, mDescriptionArray, mTextures);
    }

    @Override
    public int getCapacity() {
        return mCapacity;
    }

    @Override
    public int getRealCapacity() {
        return mCapacity;
    }

    @Override
    public boolean doesFillContainers() {
        return false;
    }

    @Override
    public boolean doesEmptyContainers() {
        return false;
    }

    @Override
    public boolean canTankBeFilled() {
        return true;
    }

    @Override
    public boolean canTankBeEmptied() {
        return false;
    }

    // See Note [Hatches that work without ticking]
    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity != null && aBaseMetaTileEntity.isServerSide() && aTick >= 20) {
            aBaseMetaTileEntity.tryDisableTicking();
        }
    }

    // --- Input-Only Fluid Hatch ---

    @Override
    public int fill(ForgeDirection side, FluidStack resource, boolean doFill) {
        if (resource == null || resource.amount <= 0) return 0;
        Fluid fluid = null;
        try {
            fluid = resource.getFluid();
        } catch (Exception swallowed) {
            Swallowed.note(swallowed);
        }
        if (fluid == null) return 0;
        if (!FluidRoles.of(fluid)
            .allowedAtTier(mReactorPipeTier)) {
            return 0;
        }

        if (mInputFluid == null) {
            int toFill = Math.min(resource.amount, mCapacity);
            if (doFill) {
                mInputFluid = resource.copy();
                mInputFluid.amount = toFill;
                markTileDirty();
            }
            return toFill;
        } else if (mInputFluid.isFluidEqual(resource)) {
            int space = mCapacity - mInputFluid.amount;
            int toFill = Math.min(resource.amount, space);
            if (doFill && toFill > 0) {
                mInputFluid.amount += toFill;
                markTileDirty();
            }
            return toFill;
        }
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
        if (fluid == null) return false;
        if (!FluidRoles.of(fluid)
            .allowedAtTier(mReactorPipeTier)) {
            return false;
        }
        return mInputFluid == null || (mInputFluid.getFluid() == fluid && mInputFluid.amount < mCapacity);
    }

    @Override
    public boolean canDrain(ForgeDirection side, Fluid fluid) {
        return false;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection side) {
        return new FluidTankInfo[] { new FluidTankInfo(mInputFluid, mCapacity) };
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return true;
    }

    @Override
    public ITexture getCasingTexture() {
        ITexture tex = super.getCasingTexture();
        if (tex != null) return tex;
        tex = Textures.BlockIcons.getCasingTextureForId(BlockNuclearCasing.CASING_TEXTURE_INDEX);
        if (tex != null) return tex;
        return Textures.BlockIcons.MACHINE_CASINGS[mTier][0];
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        byte color = getBaseMetaTileEntity() == null ? -1 : getBaseMetaTileEntity().getColorization();
        ITexture coloredOverlay = TextureFactory.of(OVERLAY_PIPE_COLORS[color + 1]);
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_PIPE_IN), coloredOverlay,
            TextureFactory.of(FLUID_IN_SIGN) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        byte color = getBaseMetaTileEntity() == null ? -1 : getBaseMetaTileEntity().getColorization();
        ITexture coloredOverlay = TextureFactory.of(OVERLAY_PIPE_COLORS[color + 1]);
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_PIPE_IN), coloredOverlay,
            TextureFactory.of(FLUID_IN_SIGN) };
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setDouble("mTemperature", mTemperature);
        aNBT.setDouble("mHeatHu", mHeatHu);
        aNBT.setLong("mCumulativeFluence", mCumulativeFluence);
        aNBT.setInteger("mReactorPipeTier", mReactorPipeTier);
        aNBT.setBoolean("mWasDry", mWasDry);
        aNBT.setBoolean("mUsedForCooling", mUsedForCooling);
        if (mInputFluid != null) aNBT.setTag("mInputFluid", mInputFluid.writeToNBT(new NBTTagCompound()));
    }

    public boolean hasWaterCoolant() {
        if (mInputFluid != null && mInputFluid.getFluid() != null) {
            ComponentData.CoolantFluidData cfd = FluidRoles.of(mInputFluid)
                .coolantData();
            return cfd != null && cfd.freezingTemperature >= 0.0;
        }
        return false;
    }

    public double getAmbientTemperature() {
        double ambient = NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().getWorld() != null) {
            ambient = MTENuclearReactor.calculateAmbientTemperature(
                getBaseMetaTileEntity().getWorld(),
                getBaseMetaTileEntity().getXCoord(),
                getBaseMetaTileEntity().getYCoord(),
                getBaseMetaTileEntity().getZCoord());
        }
        if (hasWaterCoolant()) {
            return Math.max(0.0, ambient);
        }
        return ambient;
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("mTemperature")) {
            mTemperature = aNBT.getDouble("mTemperature");
        } else {
            mTemperature = getAmbientTemperature();
        }
        if (aNBT.hasKey("mHeatHu")) {
            mHeatHu = aNBT.getDouble("mHeatHu");
        }
        if (aNBT.hasKey("mCumulativeFluence")) {
            mCumulativeFluence = aNBT.getLong("mCumulativeFluence");
        }
        if (aNBT.hasKey("mReactorPipeTier")) {
            mReactorPipeTier = aNBT.getInteger("mReactorPipeTier");
        }
        if (aNBT.hasKey("mWasDry")) {
            mWasDry = aNBT.getBoolean("mWasDry");
        }
        if (aNBT.hasKey("mUsedForCooling")) {
            mUsedForCooling = aNBT.getBoolean("mUsedForCooling");
        }
        if (aNBT.hasKey("mInputFluid")) {
            mInputFluid = FluidStack.loadFluidStackFromNBT(aNBT.getCompoundTag("mInputFluid"));
        }
    }

    public void markTileDirty() {
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().markDirty();
        }
    }

    /** Takes up to {@code amount} of the hatch's fluid (fuel burnt, coolant transmuted); it is empty at 0. */
    public void drainFluid(int amount) {
        if (mInputFluid == null) return;
        mInputFluid.amount -= amount;
        if (mInputFluid.amount <= 0) mInputFluid = null;
        markTileDirty();
    }

    /** Takes coolant that boiled off; a hatch boiled dry remembers it ran dry. */
    public void drainCoolant(int amount) {
        drainFluid(amount);
        if (mInputFluid == null) mWasDry = true;
    }

    /** Loses all its fluid at once (a meltdown, a thermal shock flashing it to steam): the hatch ran dry. */
    public void voidFluid() {
        mInputFluid = null;
        mWasDry = true;
        markTileDirty();
    }

    /** Loses its fluid if it is a liquid fuel. */
    public void voidFuel() {
        if (NuclearFuelClassification.isFluidFuel(mInputFluid)) {
            mInputFluid = null;
            markTileDirty();
        }
    }

    /** Loses its fluid unless it is a liquid fuel (an overheated column boils off its other contents). */
    public void voidNonFuel() {
        if (mInputFluid != null && !NuclearFuelClassification.isFluidFuel(mInputFluid)) {
            mInputFluid = null;
            markTileDirty();
        }
    }

    public boolean isFluidAllowed(String fluidName) {
        return NuclearSimulationEngine.isFluidAllowed(fluidName, mReactorPipeTier);
    }

    public boolean isFluidTierValid() {
        if (mInputFluid == null || mInputFluid.getFluid() == null) return true;
        return FluidRoles.of(mInputFluid)
            .allowedAtTier(mReactorPipeTier);
    }

    public boolean isFluidInputAllowed(FluidStack aFluid) {
        if (aFluid == null || aFluid.getFluid() == null) return false;
        return FluidRoles.of(aFluid)
            .allowedAtTier(mReactorPipeTier);
    }

    public com.cleanroommc.modularui.utils.fluid.FluidStackTank getInputTank() {
        return new FluidStackTank(() -> mInputFluid, f -> {
            mInputFluid = f;
            markTileDirty();
        }, () -> mCapacity);
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity,
        net.minecraft.entity.player.EntityPlayer aPlayer) {
        if (aPlayer != null && aPlayer.getHeldItem() != null && mReactor != null) {
            if (mReactor.handleSensorCardLinking(aPlayer.getHeldItem(), aPlayer, mReactor.getBaseMetaTileEntity())) {
                return true;
            }
        }
        openGui(aPlayer);
        return true;
    }

    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    protected boolean forceUseMui2() {
        return true;
    }

    @Override
    public com.cleanroommc.modularui.screen.ModularPanel buildUI(com.cleanroommc.modularui.factory.PosGuiData data,
        com.cleanroommc.modularui.value.sync.PanelSyncManager syncManager,
        com.cleanroommc.modularui.screen.UISettings uiSettings) {
        return new MTEHatchNuclearHatchGui(this).build(data, syncManager, uiSettings);
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        builder.widget(
            new DrawableWidget().setDrawable(GTUITextures.PICTURE_SCREEN_BLACK)
                .setPos(7, 16)
                .setSize(96, 56))
            .widget(
                new TextWidget("Stats").setDefaultColor(ColorUtils.hatchStatsTitle.getColor())
                    .setPos(10, 20))
            .widget(new TextWidget().setStringSupplier(() -> {
                if (!isFluidTierValid()) {
                    return "ERR: TIER";
                }
                return String.format("%.0f °C", mTemperature);
            })
                .setDefaultColor(ColorUtils.hatchTemperature.getColor())
                .setPos(10, 31))
            .widget(
                new TextWidget().setStringSupplier(() -> String.format("F: %d", mLastFastFlux))
                    .setDefaultColor(ColorUtils.hatchFastFlux.getColor())
                    .setPos(10, 42))
            .widget(
                new TextWidget().setStringSupplier(() -> String.format("T: %d", mLastThermalFlux))
                    .setDefaultColor(ColorUtils.hatchThermalFlux.getColor())
                    .setPos(10, 53))
            // Coolant In Tank
            .widget(
                new DrawableWidget().setDrawable(GTUITextures.PICTURE_SCREEN_BLACK)
                    .setPos(107, 16)
                    .setSize(48, 56))
            .widget(
                new TextWidget("In").setDefaultColor(ColorUtils.hatchSlotLabel.getColor())
                    .setPos(109, 19))
            .widget(
                new TextWidget().setStringSupplier(() -> (mInputFluid != null ? mInputFluid.amount : 0) + "L")
                    .setDefaultColor(ColorUtils.hatchFluidAmount.getColor())
                    .setPos(109, 30))
            .widget(new FluidSlotWidget(getInputTank()).setPos(121, 42));
    }
}
