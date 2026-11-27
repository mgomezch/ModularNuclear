package com.gtnewhorizons.modularnuclear.common.metatileentity.multi.gas;

import static gregtech.api.util.GTUtility.validMTEList;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ICoolantLoopPump;
import com.gtnewhorizons.modularnuclear.common.coolantloop.IInlineLoopDevice;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasLoopEngine;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasProperty;
import com.gtnewhorizons.modularnuclear.common.gui.CoolantLoopMachineGui;
import com.gtnewhorizons.modularnuclear.common.gui.IReadoutLines;
import com.gtnewhorizons.modularnuclear.common.metatileentity.CoolantLoopMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedInput;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedOutput;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTECoolantPump;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure.CountedHatchElement;
import com.gtnewhorizons.modularnuclear.common.util.MultiblockStartup;
import com.gtnewhorizons.modularnuclear.common.util.StructureChunks;
import com.gtnewhorizons.modularui.api.widget.IWidgetBuilder;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;

import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTStructureUtility;
import gregtech.api.util.HatchElementBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/*-
Note [Gas loop machines] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
The gas loop machines (MTEGasCompressor, MTEExpansionValve, MTETurboExpander, MTERadiator) share one shape and
one way into a loop, in MTEInlineLoopMachine:

    front (z = 0)      xxx      the controller at the bottom centre (~), the loop's high-pressure input
                       xIx      hatch at the centre (I)
                       x~x
    rings (1..L-2)     rrr      a ring of the machine's tiered block (r) around a core block (c)
                       rcr
                       rrr
    back (z = L-1)     xxx      the high-pressure output hatch at the centre (O)
                       xOx
                       xxx

x is the machine's casing, which its maintenance, energy and dynamo hatches may replace.  The length L runs
from getMinLength to getMaxLength; checkMachine tries the longest first.  The rings must all be one tier
(StructureUtility.ofBlocksTiered), and that tier and the number of rings size the machine.

Each machine is an IInlineLoopDevice, so the crawler takes the loop through it as a run of the pipe that feeds
its inlet (LoopGraphCrawler.visitInlineLoopDevice): the pipe's bore and pressure rating, the machine's length.
The pressure stages (compressor, valve, expander) are also GasPressureStages, which the pump binds to its
GasLoopEngine after each crawl (CoolantPumpGasLoop.bindStages); on a liquid loop they are only pipe.  The radiator
exchanges heat through processThermalExchange like any loop device, on liquid and gas loops alike.
*/
/** Base of the gas loop machines. See Note [Gas loop machines] */
public abstract class MTEInlineLoopMachine<T extends MTEInlineLoopMachine<T>> extends MTEEnhancedMultiBlockBase<T>
    implements IInlineLoopDevice, ISurvivalConstructable, IReadoutLines {

    protected MTEHatchPressurizedFluid mInlet = null;
    protected MTEHatchPressurizedFluid mOutlet = null;
    protected int mCasing = 0;
    /** Tier of the ring blocks (from 1), or -1 before any is found. */
    protected int mRingTier = -1;
    /** Tier of the core blocks (from 1), or -1. */
    protected int mCoreTier = -1;
    protected int mLength = 0;
    protected ICoolantLoopPump mPump = null;
    protected LoopSegment mBoundSegment = null;
    protected CoolantFluidProperty mLoopFluid = null;
    protected double mLastFlowM3s = 0.0;

    protected MTEInlineLoopMachine(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    protected MTEInlineLoopMachine(String aName) {
        super(aName);
    }

    // --- What each machine sets ---

    public abstract int getMinLength();

    public abstract int getMaxLength();

    /** Prefix of the machine's structure pieces and device id. */
    protected abstract String getKind();

    /** The casing element (x), without hatches. */
    protected abstract IStructureElement<T> casingElement();

    /** The ring element (r): tiered blocks setting mRingTier. */
    protected abstract IStructureElement<T> ringElement();

    /** The core element (c). */
    protected abstract IStructureElement<T> coreElement();

    /** GregTech texture index of the casing, for the hatches. */
    protected abstract int getCasingTextureIndex();

    /** The hatches the casings may be replaced by (maintenance, energy, dynamo). */
    protected abstract List<IHatchElement<? super T>> casingHatches();

    /** Fewest casings the structure needs. */
    protected int getMinCasings() {
        return 10;
    }

    /** Checks the machine's own hatch counts, adding errors. */
    protected void checkHatches(List<StructureError> errors) {}

    // --- Shape ---

    public String pieceName(int length) {
        return getKind() + "_l" + length;
    }

    public int getRingCount() {
        return Math.max(0, mLength - 2);
    }

    public int getStructureLength() {
        return mLength;
    }

    public static String[][] makeShape(int length) {
        String[][] slices = new String[length][];
        slices[0] = new String[] { "xxx", "xIx", "x~x" };
        for (int z = 1; z < length - 1; z++) slices[z] = new String[] { "rrr", "rcr", "rrr" };
        slices[length - 1] = new String[] { "xxx", "xOx", "xxx" };
        return slices;
    }

    @SuppressWarnings("unchecked")
    protected IStructureDefinition<T> buildDefinition() {
        StructureDefinition.Builder<T> b = StructureDefinition.builder();
        for (int l = getMinLength(); l <= getMaxLength(); l++) b.addShape(pieceName(l), makeShape(l));
        int tex = getCasingTextureIndex();
        IStructureElement<T> casing = com.gtnewhorizon.structurelib.structure.StructureUtility
            .onElementPass(t -> t.mCasing++, casingElement());
        List<IHatchElement<? super T>> hatches = casingHatches();
        IStructureElement<T> x = hatches.isEmpty() ? casing
            : HatchElementBuilder.<T>builder()
                .atLeast(hatches.toArray(new IHatchElement[0]))
                .casingIndex(tex)
                .hint(1)
                .buildAndChain(casing);
        IStructureElement<T> in = HatchElementBuilder.<T>builder()
            .atLeast(inletElement())
            .adder(MTEInlineLoopMachine::addLoopHatch)
            .hatchIds(CoolantLoopMetaTileEntities.PRESSURIZED_HATCH_INPUT_ID)
            .shouldReject(t -> t.mInlet != null)
            .casingIndex(tex)
            .hint(2)
            .buildAndChain(GTStructureUtility.ofHatchAdder(MTEInlineLoopMachine::addLoopHatch, tex, 2));
        IStructureElement<T> out = HatchElementBuilder.<T>builder()
            .atLeast(outletElement())
            .adder(MTEInlineLoopMachine::addLoopHatch)
            .hatchIds(CoolantLoopMetaTileEntities.PRESSURIZED_HATCH_OUTPUT_ID)
            .shouldReject(t -> t.mOutlet != null)
            .casingIndex(tex)
            .hint(3)
            .buildAndChain(GTStructureUtility.ofHatchAdder(MTEInlineLoopMachine::addLoopHatch, tex, 3));
        return b.addElement('x', x)
            .addElement('I', in)
            .addElement('O', out)
            .addElement('r', ringElement())
            .addElement('c', coreElement())
            .build();
    }

    private IHatchElement<T> inletElement() {
        return new CountedHatchElement<>(
            "PressurizedInput",
            MTEInlineLoopMachine::addLoopHatch,
            t -> t.mInlet != null ? 1 : 0,
            MTEHatchPressurizedInput.class);
    }

    private IHatchElement<T> outletElement() {
        return new CountedHatchElement<>(
            "PressurizedOutput",
            MTEInlineLoopMachine::addLoopHatch,
            t -> t.mOutlet != null ? 1 : 0,
            MTEHatchPressurizedOutput.class);
    }

    /** Takes a high-pressure hatch: an input as the loop's inlet, an output as its outlet. */
    public boolean addLoopHatch(IGregTechTileEntity te, int casingIndex) {
        if (te == null) return false;
        IMetaTileEntity mte = te.getMetaTileEntity();
        if (!(mte instanceof MTEHatchPressurizedFluid ph)) return false;
        ph.updateTexture(casingIndex);
        if (ph.isInput()) {
            if (mInlet != null) return false;
            mInlet = ph;
        } else if (ph.isOutput()) {
            if (mOutlet != null) return false;
            mOutlet = ph;
        } else {
            return false;
        }
        ph.setLoopOwner(this);
        return true;
    }

    protected void resetParts() {
        mInlet = null;
        mOutlet = null;
        mCasing = 0;
        mRingTier = -1;
        mCoreTier = -1;
        mEnergyHatches.clear();
        mDynamoHatches.clear();
        mMaintenanceHatches.clear();
    }

    @Override
    public void checkMachine(IGregTechTileEntity base, ItemStack stack, List<StructureError> errors) {
        boolean matched = false;
        List<StructureError> trial = new ArrayList<>();
        for (int l = getMaxLength(); l >= getMinLength(); l--) {
            resetParts();
            trial.clear();
            if (checkPiece(pieceName(l), 1, 2, 0, trial)) {
                mLength = l;
                matched = true;
                break;
            }
        }
        if (!matched) {
            resetParts();
            mLength = 0;
            checkPiece(pieceName(getMinLength()), 1, 2, 0, errors);
            return;
        }
        checkCasingMin(errors, mCasing, getMinCasings());
        if (mInlet == null) {
            errors.add(StructureErrors.of("modularnuclear.coolant_loop.structure.error.missing_primary_inlet"));
        }
        if (mOutlet == null) {
            errors.add(StructureErrors.of("modularnuclear.coolant_loop.structure.error.missing_primary_outlet"));
        }
        checkHatches(errors);
        if (!errors.isEmpty()) return;
        mInlet.setMode(MTEHatchPressurizedFluid.HatchMode.SUCTION);
        mOutlet.setMode(MTEHatchPressurizedFluid.HatchMode.DISCHARGE);
        int tex = getCasingTextureIndex();
        ItemStack icon = getBaseMetaTileEntity() != null ? getMachineCraftingIcon() : null;
        for (MTEHatchPressurizedFluid h : new MTEHatchPressurizedFluid[] { mInlet, mOutlet }) {
            h.updateTexture(tex);
            if (icon != null) h.updateCraftingIcon(icon);
        }
        for (MTEHatchMaintenance h : mMaintenanceHatches) h.updateTexture(tex);
        for (MTEHatchEnergy h : mEnergyHatches) h.updateTexture(tex);
        for (MTEHatchDynamo h : mDynamoHatches) h.updateTexture(tex);
        checkMaintenance();
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity base, ItemStack stack) {
        return checkMachine_TT(base);
    }

    /** The length the trigger builds: its stack size, from the shortest up. */
    protected int lengthFromTrigger(ItemStack trigger) {
        int n = trigger != null ? trigger.stackSize : 1;
        return Math.max(getMinLength(), Math.min(getMaxLength(), getMinLength() + n - 1));
    }

    @Override
    public void construct(ItemStack trigger, boolean hintsOnly) {
        buildPiece(pieceName(lengthFromTrigger(trigger)), trigger, hintsOnly, 1, 2, 0);
    }

    @Override
    public int survivalConstruct(ItemStack trigger, int budget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        String piece = pieceName(lengthFromTrigger(trigger));
        // See Note [Survival builds count the hatches of the tier they build]
        resetParts();
        checkPiece(piece, 1, 2, 0, new ArrayList<>());
        return survivalBuildPiece(piece, trigger, 1, 2, 0, budget, env, false, true);
    }

    @Override
    public void onFirstTick(IGregTechTileEntity base) {
        super.onFirstTick(base);
        MultiblockStartup.formNow(this);
    }

    @Override
    public void collectStructureChunks(Set<Long> chunks) {
        if (mLength > 0) StructureChunks.collect(self(), pieceName(mLength), 1, 2, 0, chunks);
    }

    @SuppressWarnings("unchecked")
    private T self() {
        return (T) this;
    }

    // --- Loop device ---

    private String cachedDeviceId;

    @Override
    public String getDeviceId() {
        if (cachedDeviceId != null) return cachedDeviceId;
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te == null) return getKind() + "_unbound";
        cachedDeviceId = getKind() + "_" + te.getXCoord() + "_" + te.getYCoord() + "_" + te.getZCoord();
        return cachedDeviceId;
    }

    @Override
    public boolean isLoopReady() {
        return mMachine && mInlet != null && mOutlet != null;
    }

    @Override
    public double getLoopLengthMeters() {
        return Math.max(1, mLength);
    }

    @Override
    public double getMinorLossK() {
        return 1.0;
    }

    @Override
    public double getDeviceTemperatureCelsius() {
        return mBoundSegment != null ? mBoundSegment.getCurrentTemperatureCelsius() : 20.0;
    }

    @Override
    public long getFillVolumeLiters() {
        return Math.max(1L, Math.round(getLoopVolumeM3() * 1000.0));
    }

    @Override
    public void setLoopPump(ICoolantLoopPump pump) {
        mPump = pump;
    }

    public ICoolantLoopPump getLoopPump() {
        return mPump;
    }

    @Override
    public MTEHatchPressurizedFluid getLoopInlet() {
        return mInlet;
    }

    @Override
    public MTEHatchPressurizedFluid getLoopOutlet() {
        return mOutlet;
    }

    @Override
    public void processThermalExchange(double flowM3s, double dt, CoolantFluidProperty fluid, LoopSegment segment) {
        mBoundSegment = segment;
        mLoopFluid = fluid;
        mLastFlowM3s = flowM3s;
        onLoopTick(flowM3s, dt, fluid, segment);
        if (mInlet != null && segment != null)
            mInlet.setCurrentTemperatureCelsius(segment.getIncomingTemperatureCelsius());
        if (mOutlet != null && segment != null)
            mOutlet.setCurrentTemperatureCelsius(segment.getCurrentTemperatureCelsius());
    }

    /** One tick of the machine on its loop, after the loop's step. */
    protected void onLoopTick(double flowM3s, double dt, CoolantFluidProperty fluid, LoopSegment segment) {}

    /** The gas loop the machine is on, or null if it is on none or on a liquid loop. */
    protected GasLoopEngine gasEngine() {
        if (mPump instanceof MTECoolantPump p && p.getEngine() instanceof GasLoopEngine g) return g;
        return null;
    }

    protected GasProperty gas() {
        GasLoopEngine g = gasEngine();
        return g != null ? g.getGas() : null;
    }

    /** Ambient temperature in K: the loop's pump's, or the machine's own biome's if it is on no loop. */
    protected double ambientK() {
        if (mPump instanceof MTECoolantPump p) return p.getBiomeTemperatureCelsius() + 273.15;
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null && te.getWorld() != null) {
            return MTECoolantPump
                .calculateAmbientTemperature(te.getWorld(), te.getXCoord(), te.getYCoord(), te.getZCoord()) + 273.15;
        }
        return 293.15;
    }

    // --- Power ---

    @Override
    public boolean shouldCheckMaintenance() {
        return true;
    }

    public double getEfficiencyFactor() {
        int eff = getCurrentEfficiency(null);
        return Math.max(0, Math.min(10000, eff)) / 10000.0;
    }

    /** EU the energy hatches hold now. */
    protected long storedEnergy() {
        long total = 0L;
        for (MTEHatchEnergy h : validMTEList(mEnergyHatches)) total += h.getBaseMetaTileEntity()
            .getStoredEU();
        return total;
    }

    /** EU per tick the energy hatches take in at most (voltage times amperes). */
    protected long maxInputPerTick() {
        return getMaxInputPower();
    }

    // --- GUI ---

    @Override
    public boolean checkRecipe(ItemStack aStack) {
        return true;
    }

    @Override
    protected MTEMultiBlockBaseGui<?> getGui() {
        return new CoolantLoopMachineGui<>(this);
    }

    @Override
    public boolean supportsSingleRecipeLocking() {
        return false;
    }

    @Override
    public ButtonWidget createLockToSingleRecipeButton(IWidgetBuilder<?> builder) {
        return (ButtonWidget) new ButtonWidget().setSize(0, 0)
            .setEnabled(false);
    }

    /** Our GUIs show no GregTech logo. */
    @Override
    public boolean supportsLogo() {
        return false;
    }

    @Override
    public String[] getInfoData() {
        List<String> lines = new ArrayList<>(getReadoutLines());
        return lines.toArray(new String[0]);
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {
        super.saveNBTData(nbt);
        nbt.setInteger("mLength", mLength);
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        super.loadNBTData(nbt);
        mLength = nbt.getInteger("mLength");
    }

    /** The bound segment's gas state as a short line, for readouts. */
    protected String gasStateLine() {
        GasProperty g = gas();
        LoopSegment s = mBoundSegment;
        if (g == null || s == null || !s.holdsGas()) return "Not on a gas loop";
        double x = s.getGasQuality();
        String phase = x >= 1.0 ? "vapour" : x <= 0.0 ? "liquid" : String.format("%.0f%% vapour", x * 100.0);
        return String.format(
            "%s: %.2f bar, %.1f °C, %s",
            g.getFluidName(),
            s.getInletPressureBar(),
            s.getCurrentTemperatureCelsius(),
            phase);
    }
}
