package com.gtnewhorizons.modularnuclear.common.metatileentity.hatch;

import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.block.BlockNuclearCasing;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantPipeSpec;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ICoolantLoopPump;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ICoolantPassageHatch;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.gui.MTEHatchNuclearHighPressureGui;
import com.gtnewhorizons.modularnuclear.common.metatileentity.CoolantLoopMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.nuclear.CoolantPassage;
import com.gtnewhorizons.modularnuclear.common.nuclear.INuclearTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.NeutronType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularui.api.math.Color;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Textures;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
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
 * <p>
 * The MPTR takes the material-tiered input and output hatches, one per coolant pipe material: the material's voltage
 * tier ({@link CoolantPipeSpec#VOLTAGE_TIERS}) sets the passage's heat capacity and heat transfer. The untiered hatches
 * remain registered only so existing worlds keep their blocks.
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
    /** Index in {@link CoolantPipeSpec#MATERIAL_NAMES} of the hatch's material, or -1 for an untiered legacy hatch. */
    protected final int mMaterialIndex;
    protected IGregTechTileEntity mOppositeHatch = null;
    protected int[] mOppositeCoord = null;
    protected ICoolantLoopPump mConnectedPump = null;
    /** Was in a loop when saved, and its pump has not bound it again since loading. */
    protected boolean mAwaitingLoopPump = false;
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
        this(aID, aName, aNameRegional, aTier, -1);
    }

    public MTEHatchNuclearHighPressure(int aID, String aName, String aNameRegional, int aTier, int aMaterialIndex) {
        super(aID, aName, aNameRegional, aTier, 0, (String) null);
        this.mMaterialIndex = aMaterialIndex;
    }

    public MTEHatchNuclearHighPressure(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        this(aName, aTier, -1, aDescription, aTextures);
    }

    public MTEHatchNuclearHighPressure(String aName, int aTier, int aMaterialIndex, String[] aDescription,
        ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
        this.mMaterialIndex = aMaterialIndex;
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalized("gt.blockmachines.hatch.nuclearhighpressure.desc");
    }

    /** The localized description under the given key, followed by the material's passage properties if tiered. */
    protected String[] describe(String key) {
        String[] base = GTSplit.splitLocalized(key);
        if (!isTiered()) return base;
        String[] lines = java.util.Arrays.copyOf(base, base.length + 2);
        lines[base.length] = net.minecraft.util.StatCollector.translateToLocalFormatted(
            "gt.blockmachines.hatch.nuclearhighpressure.material",
            CoolantLoopMetaTileEntities.COOLANT_PIPE_MATERIALS[mMaterialIndex].getLocalizedNameForItem("%material"),
            GTValues.VN[mTier]);
        lines[base.length + 1] = net.minecraft.util.StatCollector
            .translateToLocalFormatted("gt.blockmachines.hatch.nuclearhighpressure.capacity", (long) getHeatCapacity());
        return lines;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchNuclearHighPressure(mName, mTier, mMaterialIndex, mDescriptionArray, mTextures);
    }

    /** Whether this is one of the material-tiered hatches the MPTR accepts. */
    public boolean isTiered() {
        return mMaterialIndex >= 0;
    }

    public int getMaterialIndex() {
        return mMaterialIndex;
    }

    /** GregTech name of the hatch's material, or null for an untiered legacy hatch. */
    public String getMaterialName() {
        return isTiered() ? CoolantPipeSpec.MATERIAL_NAMES[mMaterialIndex] : null;
    }

    /** Heat capacity (HU/°C) of the passage cell, set by the hatch's voltage tier. */
    @Override
    public double getHeatCapacity() {
        return CoolantPassage.heatCapacity(mTier);
    }

    @Override
    public ITexture getCasingTexture() {
        ITexture tex = super.getCasingTexture();
        if (tex != null) return tex;
        tex = Textures.BlockIcons.getCasingTextureForId(BlockNuclearCasing.CASING_TEXTURE_INDEX);
        if (tex != null) return tex;
        return Textures.BlockIcons.casingTexturePages[0][16];
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        if (mIsInlet) {
            return new ITexture[] { aBaseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_IN),
                TextureFactory.of(MTEHatchPressurizedFluid.OVERLAY_PRESSURIZED_IN) };
        } else {
            return new ITexture[] { aBaseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_OUT),
                TextureFactory.of(MTEHatchPressurizedFluid.OVERLAY_PRESSURIZED_OUT) };
        }
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return getTexturesActive(aBaseTexture);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        ITexture baseTexture = getCasingTexture();
        if (baseTexture == null) {
            baseTexture = Textures.BlockIcons.casingTexturePages[0][16];
        }
        if (side != aFacing) {
            return new ITexture[] { baseTexture };
        }
        if (mIsInlet) {
            return new ITexture[] { baseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_IN),
                TextureFactory.of(MTEHatchPressurizedFluid.OVERLAY_PRESSURIZED_IN) };
        } else {
            return new ITexture[] { baseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_OUT),
                TextureFactory.of(MTEHatchPressurizedFluid.OVERLAY_PRESSURIZED_OUT) };
        }
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
        if (aBaseMetaTileEntity != null && aBaseMetaTileEntity.isServerSide() && aTick >= 20) {
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
        if (mOppositeHatch != null && !mOppositeHatch.isDead()) {
            return mOppositeHatch;
        }
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null && te.getWorld() != null) {
            World w = te.getWorld();
            if (mOppositeCoord != null && mOppositeCoord.length >= 3) {
                TileEntity target = w.getTileEntity(mOppositeCoord[0], mOppositeCoord[1], mOppositeCoord[2]);
                if (target instanceof IGregTechTileEntity gte
                    && gte.getMetaTileEntity() instanceof ICoolantPassageHatch) {
                    mOppositeHatch = gte;
                    return mOppositeHatch;
                }
            }
            int targetY = mIsInlet ? te.getYCoord() - 4 : te.getYCoord() + 4;
            TileEntity target = w.getTileEntity(te.getXCoord(), targetY, te.getZCoord());
            if (target instanceof IGregTechTileEntity gte && gte.getMetaTileEntity() instanceof ICoolantPassageHatch) {
                mOppositeHatch = gte;
                mOppositeCoord = new int[] { te.getXCoord(), targetY, te.getZCoord() };
                return mOppositeHatch;
            }
        }
        return mOppositeHatch;
    }

    public void setOppositeHatch(IGregTechTileEntity opposite) {
        this.mOppositeHatch = opposite;
        if (opposite != null) {
            this.mOppositeCoord = new int[] { opposite.getXCoord(), opposite.getYCoord(), opposite.getZCoord() };
        }
    }

    public boolean hasOppositeHatch() {
        return getOppositeHatchTile() != null;
    }

    public boolean hasConnectedPump() {
        return getConnectedPump() != null;
    }

    @Override
    public void convertToDirectionalVariant(boolean isInput) {
        convertToDirectionalHatch(getBaseMetaTileEntity(), isInput);
    }

    public static MTEHatchNuclearHighPressure convertToDirectionalHatch(IGregTechTileEntity te, boolean isInput) {
        if (te == null) return null;
        IMetaTileEntity currentMte = te.getMetaTileEntity();
        if (isInput && currentMte instanceof MTEHatchNuclearHighPressureInput inputHatch) {
            return inputHatch;
        }
        if (!isInput && currentMte instanceof MTEHatchNuclearHighPressureOutput outputHatch) {
            return outputHatch;
        }
        if (!(currentMte instanceof MTEHatchNuclearHighPressure oldHatch)) {
            return null;
        }

        NBTTagCompound nbt = new NBTTagCompound();
        oldHatch.saveNBTData(nbt);

        int targetId;
        if (oldHatch.isTiered()) {
            targetId = (isInput ? ModMetaTileEntities.ID_NUCLEAR_HIGH_PRESSURE_INPUT_BASE
                : ModMetaTileEntities.ID_NUCLEAR_HIGH_PRESSURE_OUTPUT_BASE) + oldHatch.getMaterialIndex();
        } else {
            targetId = isInput ? ModMetaTileEntities.ID_NUCLEAR_HIGH_PRESSURE_INPUT
                : ModMetaTileEntities.ID_NUCLEAR_HIGH_PRESSURE_OUTPUT;
        }
        IMetaTileEntity newMteTemplate = GregTechAPI.METATILEENTITIES[targetId];
        if (newMteTemplate == null) return oldHatch;

        IMetaTileEntity newMte = newMteTemplate.newMetaEntity(te);
        if (!(newMte instanceof MTEHatchNuclearHighPressure newHatch)) {
            return oldHatch;
        }

        newHatch.loadNBTData(nbt);
        newHatch.setIsInlet(isInput);
        newHatch.setOppositeHatch(oldHatch.getOppositeHatchTile());
        newHatch.setConnectedPump(oldHatch.getConnectedPump());
        newHatch.mReactor = oldHatch.mReactor;

        if (oldHatch.mReactor != null) {
            int topIdx = oldHatch.mReactor.mTopHighPressureHatches.indexOf(oldHatch);
            if (topIdx >= 0) {
                oldHatch.mReactor.mTopHighPressureHatches.set(topIdx, newHatch);
            }
            int botIdx = oldHatch.mReactor.mBottomHighPressureHatches.indexOf(oldHatch);
            if (botIdx >= 0) {
                oldHatch.mReactor.mBottomHighPressureHatches.set(botIdx, newHatch);
            }
        }

        te.setMetaTileID((short) targetId);
        te.setMetaTileEntity(newHatch);
        newHatch.setBaseMetaTileEntity(te);
        if (newHatch.mReactor != null) {
            newHatch.updateTexture(MTENuclearReactor.CASING_INDEX);
            if (newHatch.mReactor.getBaseMetaTileEntity() != null) {
                newHatch.updateCraftingIcon(newHatch.mReactor.getMachineCraftingIcon());
            }
        }
        te.issueTextureUpdate();
        te.markDirty();
        return newHatch;
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
        if (pump != null) mAwaitingLoopPump = false;
        // Resolved from the saved coordinates if need be: right after a load the pump binds a passage before the
        // reactor's structure check has linked its two ends
        IGregTechTileEntity opposite = getOppositeHatchTile();
        if (opposite != null && opposite.getMetaTileEntity() instanceof MTEHatchNuclearHighPressure other) {
            if (other.mConnectedPump != pump) {
                other.mConnectedPump = pump;
            }
            if (pump != null) other.mAwaitingLoopPump = false;
        }
    }

    @Override
    public ICoolantLoopPump getConnectedPump() {
        return mConnectedPump;
    }

    @Override
    public boolean isAwaitingLoopPump() {
        return mAwaitingLoopPump;
    }

    @Override
    public void stopAwaitingLoopPump() {
        mAwaitingLoopPump = false;
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
        return CoolantPassage.minorLossK();
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
        double capacity = getHeatCapacity();
        double heatTransferredHu = CoolantPassage
            .exchange(mTemperature, capacity, mTier, volumetricFlowRateM3s, dtSeconds, fluid, segment);
        if (heatTransferredHu == 0.0) {
            return;
        }
        double coreTempChange = heatTransferredHu / capacity;

        mTemperature -= coreTempChange;
        if (mOppositeHatch != null && mOppositeHatch.getMetaTileEntity() instanceof MTEHatchNuclearHighPressure other) {
            other.mTemperature = this.mTemperature;
        }
        if (coreTempChange > 0) {
            mHeatOutputAccumulator += heatTransferredHu;
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
        this.mTemperature += heatHu / getHeatCapacity();
        if (mOppositeHatch != null && mOppositeHatch.getMetaTileEntity() instanceof MTEHatchNuclearHighPressure other) {
            other.mTemperature = this.mTemperature;
        }
    }

    @Override
    public double getHeatTransferCoeff() {
        return CoolantPassage.conduction();
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
        return CoolantPassage.absorption(type);
    }

    @Override
    public double getScatteringProbability(NeutronType type) {
        return CoolantPassage.scattering();
    }

    @Override
    public double getModerationProbability() {
        if (mConnectedPump != null && mConnectedPump.getCoolantFluidProperty() != null) {
            return CoolantPassage.moderation(
                mConnectedPump.getCoolantFluidProperty()
                    .getFluidName());
        }
        return CoolantPassage.defaultModeration();
    }

    @Override
    public void onNeutronAbsorbed(NeutronType type, int count) {
        if (type == NeutronType.FAST) {
            mFastAbsorbed += count;
        } else {
            mThermalAbsorbed += count;
        }

        // Radiolytic gas generation: every few absorbed neutrons transmute 2 L of coolant
        if (count > 0) {
            mNeutronAccumulator += count;
            if (mConnectedPump != null && mNeutronAccumulator >= CoolantPassage.neutronsPerRadiolysis()) {
                int cycles = mNeutronAccumulator / CoolantPassage.neutronsPerRadiolysis();
                mNeutronAccumulator %= CoolantPassage.neutronsPerRadiolysis();
                CoolantFluidProperty prop = mConnectedPump.getCoolantFluidProperty();
                String gas = CoolantPassage.radiolysisGas(prop != null ? prop.getFluidName() : null);
                if (gas != null) {
                    mConnectedPump.consumeCoolant(cycles * 2L);
                    mConnectedPump.addDissolvedGas(gas, cycles * 2L);
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
        aNBT.setBoolean("mInLoop", mConnectedPump != null || mAwaitingLoopPump);
        if (mOppositeHatch != null) {
            aNBT.setIntArray(
                "oppositeCoord",
                new int[] { mOppositeHatch.getXCoord(), mOppositeHatch.getYCoord(), mOppositeHatch.getZCoord() });
        } else if (mOppositeCoord != null) {
            aNBT.setIntArray("oppositeCoord", mOppositeCoord);
        }
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
        mAwaitingLoopPump = aNBT.getBoolean("mInLoop");
        if (aNBT.hasKey("oppositeCoord")) {
            mOppositeCoord = aNBT.getIntArray("oppositeCoord");
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
        return new MTEHatchNuclearHighPressureGui(this).build(data, syncManager, uiSettings);
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

    @Override
    public void collectStructureChunks(Set<Long> chunks) {
        if (mReactor != null) mReactor.collectStructureChunks(chunks);
    }
}
