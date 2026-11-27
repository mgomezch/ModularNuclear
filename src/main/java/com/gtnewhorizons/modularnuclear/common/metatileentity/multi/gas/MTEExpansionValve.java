package com.gtnewhorizons.modularnuclear.common.metatileentity.multi.gas;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasPressureStage;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasStageModels;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * Expansion Valve: the pressure stage that lets a condensing gas's liquid down from the high side to the low side,
 * where it flashes cold. Thermostatic by default (it holds a set superheat at the outlet of the evaporator it feeds);
 * a screwdriver cycles its set points and fixed openings. Needs no power. See Note [Gas loop machines]
 */
public class MTEExpansionValve extends MTEInlineLoopMachine<MTEExpansionValve> implements GasPressureStage {

    private static final int CASING_META = 1; // Frost Proof Machine Casing (sBlockCasings2)
    private static IStructureDefinition<MTEExpansionValve> DEFINITION;

    /** The screwdriver's settings: superheat set points (K), then fixed openings. */
    private static final double[] SET_POINTS = { 5.0, 3.0, 8.0, 12.0 };
    private static final double[] FIXED_OPENINGS = { 0.1, 0.25, 0.5, 1.0 };

    private int mMode = 0;
    private final GasStageModels.Thermostat mThermostat = new GasStageModels.Thermostat();
    private double mOpening = 0.2;
    private double mLastMassFlow = 0.0, mLastInBar = 0.0, mLastOutBar = 0.0, mLastSuperheat = 0.0;

    public MTEExpansionValve(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEExpansionValve(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEExpansionValve(mName);
    }

    @Override
    public int getMinLength() {
        return 3;
    }

    @Override
    public int getMaxLength() {
        return 5;
    }

    @Override
    protected String getKind() {
        return "expansion_valve";
    }

    @Override
    protected IStructureElement<MTEExpansionValve> casingElement() {
        return StructureUtility.ofBlock(GasMachineParts.casings2(), CASING_META);
    }

    @Override
    protected IStructureElement<MTEExpansionValve> ringElement() {
        return GasMachineParts
            .tiered(GasMachineParts.casings2(), new int[] { CASING_META }, (t, v) -> t.mRingTier = v, t -> t.mRingTier);
    }

    @Override
    protected IStructureElement<MTEExpansionValve> coreElement() {
        return GasMachineParts.tiered(
            GasMachineParts.casings2(),
            GasMachineParts.PIPE_CASING_METAS,
            (t, v) -> t.mCoreTier = v,
            t -> t.mCoreTier);
    }

    @Override
    protected int getCasingTextureIndex() {
        return GasMachineParts.textureIndex(GasMachineParts.casings2(), CASING_META);
    }

    @Override
    protected List<IHatchElement<? super MTEExpansionValve>> casingHatches() {
        return new ArrayList<>();
    }

    @Override
    protected int getMinCasings() {
        return 15;
    }

    @Override
    public boolean shouldCheckMaintenance() {
        return false;
    }

    @Override
    public IStructureDefinition<MTEExpansionValve> getStructureDefinition() {
        if (DEFINITION == null) DEFINITION = buildDefinition();
        return DEFINITION;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity base, ForgeDirection side, ForgeDirection facing, int color,
        boolean active, boolean redstone) {
        ITexture casing = TextureFactory.of(GasMachineParts.casings2(), CASING_META);
        if (side != facing) return new ITexture[] { casing };
        return new ITexture[] { casing,
            TextureFactory.of(
                active ? Textures.BlockIcons.OVERLAY_FRONT_VACUUM_FREEZER_ACTIVE
                    : Textures.BlockIcons.OVERLAY_FRONT_VACUUM_FREEZER) };
    }

    public double getMaxArea() {
        return getRingCount() * GasMachineParts.valveAreaPerRing(Math.max(1, mCoreTier));
    }

    public boolean isThermostatic() {
        return mMode < SET_POINTS.length;
    }

    @Override
    public void computeStage(GasPressureStage.Context c) {
        mLastInBar = c.pressureIn / 1e5;
        mLastOutBar = c.pressureOut / 1e5;
        mLastSuperheat = c.downstreamExitSuperheatK;
        if (!mMachine) {
            c.clearAnswers();
            mLastMassFlow = 0.0;
            return;
        }
        if (!isAllowedToWork()) {
            mOpening = 0.0;
        } else if (isThermostatic()) {
            mThermostat.setPointK = SET_POINTS[mMode];
            if (c.loopRunning) mOpening = mThermostat.opening(c.downstreamExitSuperheatK, c.dt);
        } else {
            mOpening = FIXED_OPENINGS[mMode - SET_POINTS.length];
        }
        new GasStageModels.Valve(getMaxArea()).compute(c, mOpening);
        mLastMassFlow = c.massFlow;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) base.setActive(c.massFlow > 0.0);
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer player, float x, float y, float z,
        ItemStack tool) {
        mMode = (mMode + 1) % (SET_POINTS.length + FIXED_OPENINGS.length);
        GTUtility.sendChatToPlayer(player, "Expansion valve: " + modeName());
    }

    private String modeName() {
        if (isThermostatic()) return String.format("thermostatic, %.0f K superheat", SET_POINTS[mMode]);
        return String.format("fixed at %.0f%% open", FIXED_OPENINGS[mMode - SET_POINTS.length] * 100.0);
    }

    @Override
    public double getLoopVolumeM3() {
        return GasMachineParts.VOLUME_PER_RING_M3 * getRingCount();
    }

    @Override
    public List<String> getReadoutLines() {
        List<String> l = new ArrayList<>();
        l.add("Mode: " + modeName());
        l.add(String.format("Opening: %.1f%% of %.4f m²", mOpening * 100.0, getMaxArea()));
        l.add(String.format("%.2f bar -> %.2f bar", mLastInBar, mLastOutBar));
        l.add(String.format("Mass flow: %.1f kg/s", mLastMassFlow));
        l.add(String.format("Evaporator outlet superheat: %.1f K", mLastSuperheat));
        l.add(gasStateLine());
        return l;
    }

    public double getOpening() {
        return mOpening;
    }

    public double getLastMassFlow() {
        return mLastMassFlow;
    }

    public void setMode(int mode) {
        mMode = Math.floorMod(mode, SET_POINTS.length + FIXED_OPENINGS.length);
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {
        super.saveNBTData(nbt);
        nbt.setInteger("mMode", mMode);
        nbt.setDouble("mOpening", mOpening);
        nbt.setDouble("mBias", mThermostat.bias);
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        super.loadNBTData(nbt);
        mMode = nbt.getInteger("mMode");
        mOpening = nbt.getDouble("mOpening");
        if (nbt.hasKey("mBias")) mThermostat.bias = nbt.getDouble("mBias");
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gt.multiblock.expansion_valve.machine_type"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.expansion_valve.desc1"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.expansion_valve.desc2"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.expansion_valve.desc3"))
            .addSeparator()
            .beginVariableStructureBlock(3, 3, 3, 3, 3, 5, false)
            .addController(StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.controller"))
            .addCasingInfoMin(
                StatCollector.translateToLocal("gt.multiblock.expansion_valve.structure.casing"),
                15,
                false)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.expansion_valve.structure.core"),
                StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.core_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.inlet"),
                StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.inlet_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.outlet"),
                StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.outlet_pos"))
            .toolTipFinisher(StatCollector.translateToLocal("gt.multiblock.modularnuclear.coolant_loop.finisher"));
        return tt;
    }
}
