package com.gtnewhorizons.modularnuclear.common.metatileentity.compact;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ICompactLoopDevice;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ICoolantLoopPump;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasLoopEngine;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.pump.ICoolantPumpHost;
import com.gtnewhorizons.modularnuclear.common.gui.CompactInlineGui;
import com.gtnewhorizons.modularnuclear.common.gui.IReadoutLines;
import com.gtnewhorizons.modularnuclear.common.util.CoolantFluidHelper;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/*-
Note [Compact loop machines] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
The compact loop machines are one-block versions of the gas loop's multiblocks, in tiers from MV to LuV: the
Compact Loop Pump (MTECompactLoopPump), Gas Compressor, Expansion Valve, Turbo-Expander, Radiator, Coolant Bath
and Heat Exchanger.  They make a small refrigeration loop possible two tiers before the multiblock pump's
high-pressure hatches, and stay much weaker than the multiblocks at every tier.

Shape.  A compact machine has a front, which faces the player who places it, and the loop runs straight through
it across the front: in through its inlet side and out through the opposite side (ICompactLoopDevice), by default
from the left of the front to the right.  A screwdriver turns that through the four directions the front leaves
free (cycleFlow); coolant pipes connect only to those two sides (ILoopPortSides, MTECoolantPipe.canConnect).
Power goes in through any other side but the front.  There are no hatches, no maintenance and no structure.

In a loop.  LoopGraphCrawler.visitCompactDevice takes the loop through a compact device as a run of the pipe
that feeds it, as it does a multiblock gas machine, with the lower of the pipe's and the machine's pressure
ratings.  The pressure stages reuse the multiblocks' physics (GasStageModels), each with its tier's numbers from
CompactTier.  Compact and multiblock machines mix freely on one loop, under either kind of pump.

Tiers.  CompactTier holds every number that changes with the tier: the EU a machine works with (one ampere of
its tier), its bursting pressure, the gas it holds, and what each kind does with them.  Three things keep a
compact loop far below a multiblock one:

  - A compact compressor's drive gives its shaft only a small share of its EU (CompactTier.driveEfficiency, 9%
    at MV to 23% at LuV); a multiblock compressor's shaft gets it all.
  - It has a built-in pressure ratio (GasStageModels.Compressor.builtInRatio): it compresses at least that far
    whatever the loop's pressures, which bounds the loop's coefficient of performance however warm its
    evaporator runs.  Past that ratio it slows, and stops at one and a half times it (unloading), so a lightly
    loaded loop idles rather than pumping its evaporator down to a vacuum.
  - Each machine holds little gas, and a loop's flow is bounded by the gas on its low side (wrinkle (W2) of Note
    [Gas loop pressure zones]), so a machine's volume grows with its tier's flow (CompactTier.volumeM3) and with
    it the charge its pump's tanks must hold.

An ingot holds the same heat in a compact bath as in a multiblock one, so the machines mix without exploits.

Balance.  CompactLoopBalanceTest runs a loop of each tier (compressor, radiator in air at 20 °C, valve, bath kept
full) and compares what it spends per ingot with the Vacuum Freezer's recipe for that ingot:

    tier  ingot          EU efficiency   pace
    MV    Silicon SG     1.0 x           1.0 x
    HV    Titanium       1.7 x           1.6 x
    EV    Tungstensteel  2.3 x           1.8 x
    IV    Naquadah       2.8 x           2.2 x
    LuV   Naquadah       2.8 x           3.5 x

At MV there is no freezer: the alternative is the Chemical Bath, at twice the freezer's EU and time for Silicon SG
and five to six times for Kanthal, Silicon and Tantalum, so an MV loop is two to six times as good as that.  A
loop on ingots far below its tier is quick but wasteful: a LuV loop cools Kanthal at five times the freezer's pace
for 0.8 times its efficiency.  The figures are for a pump's default charge (CompactTier.LIQUID_FRACTIONS); with
twice the charge a loop does about a tenth better.

  (W1) Gas only.  A compact pump has no impeller (ICoolantPumpHost.getImpeller is null), so it charges and owns
       gas loops only; the liquid-only multiblocks (fluid heater, degasser) have no compact version.
  (W2) Few controls.  Compact machines have no redstone modes or computer API beyond GregTech's own (soft
       mallet, machine controller cover).  They report through the scanner and Waila, from the same lines
       (getReadoutLines), and through the tooltips of their GUI (Note [Compact machine GUIs]).
*/
/** Base of the compact loop devices. See Note [Compact loop machines] */
public abstract class MTECompactLoopMachine extends MTETieredMachineBlock implements ICompactLoopDevice, IReadoutLines {

    /** The side the loop enters through; it leaves through the opposite one. UNKNOWN until first asked. */
    protected ForgeDirection mInletSide = ForgeDirection.UNKNOWN;
    protected ICoolantLoopPump mPump = null;
    protected LoopSegment mBoundSegment = null;
    protected CoolantFluidProperty mLoopFluid = null;
    protected double mLastFlowM3s = 0.0;
    protected String mStatus = "Idle";
    private String cachedDeviceId;

    protected MTECompactLoopMachine(int aID, String aName, String aNameRegional, int aTier, int aInvSlotCount) {
        super(aID, aName, aNameRegional, aTier, aInvSlotCount, new String[0]);
    }

    protected MTECompactLoopMachine(String aName, int aTier, int aInvSlotCount, String[] aDescription,
        ITexture[][][] aTextures) {
        super(aName, aTier, aInvSlotCount, aDescription, aTextures);
    }

    // --- What each machine sets ---

    /** Prefix of the machine's device id and of its tooltip keys. */
    protected abstract String getKind();

    /** The overlay on the machine's front. */
    protected abstract ITexture frontOverlay(boolean active);

    /** The machine's own lines for the scanner, Waila and a right click. */
    protected abstract void addReadoutLines(List<String> lines);

    /** One tick of the machine on its loop, after the loop's step. */
    protected void onLoopTick(double flowM3s, double dt, CoolantFluidProperty fluid, LoopSegment segment) {}

    /** EU per tick the machine takes in at most: one ampere of its tier, or none for a machine that needs no power. */
    protected boolean usesPower() {
        return true;
    }

    public CompactTier tier() {
        return CompactTier.of(mTier);
    }

    // --- Shape ---

    /** The sides the loop may enter through, in the order a screwdriver cycles them: across the front. */
    public static ForgeDirection[] flowChoices(ForgeDirection front) {
        if (front == null || front == ForgeDirection.UNKNOWN || front.offsetY != 0) front = ForgeDirection.NORTH;
        ForgeDirection left = front.getRotation(ForgeDirection.UP);
        return new ForgeDirection[] { left, left.getOpposite(), ForgeDirection.DOWN, ForgeDirection.UP };
    }

    @Override
    public ForgeDirection getLoopInletSide() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        ForgeDirection front = base != null ? base.getFrontFacing() : ForgeDirection.NORTH;
        ForgeDirection[] choices = flowChoices(front);
        for (ForgeDirection d : choices) if (d == mInletSide) return d;
        mInletSide = choices[0];
        return mInletSide;
    }

    /** A screwdriver: the loop's next direction through the machine. */
    protected void cycleFlow(EntityPlayer player) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        ForgeDirection[] choices = flowChoices(base != null ? base.getFrontFacing() : ForgeDirection.NORTH);
        ForgeDirection now = getLoopInletSide();
        int i = 0;
        while (i < choices.length && choices[i] != now) i++;
        mInletSide = choices[(i + 1) % choices.length];
        if (base != null) {
            base.issueTextureUpdate();
            base.issueBlockUpdate();
        }
        GTUtility.sendChatToPlayer(
            player,
            StatCollector.translateToLocalFormatted(
                "modularnuclear.compact.flow",
                mInletSide.name()
                    .toLowerCase(),
                mInletSide.getOpposite()
                    .name()
                    .toLowerCase()));
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        cycleFlow(aPlayer);
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return facing.offsetY == 0;
    }

    @Override
    public void onFacingChange() {
        super.onFacingChange();
        // The loop's direction stays across the new front
        getLoopInletSide();
    }

    @Override
    public ITexture[][][] getTextureSet(ITexture[] aTextures) {
        return new ITexture[0][0][0];
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity base, ForgeDirection side, ForgeDirection facing, int colorIndex,
        boolean active, boolean redstone) {
        ITexture casing = Textures.BlockIcons.MACHINE_CASINGS[mTier][colorIndex + 1];
        if (side == facing) return new ITexture[] { casing, frontOverlay(active) };
        ForgeDirection inlet = clientInletSide(facing);
        if (side == inlet) return new ITexture[] { casing, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_IN) };
        if (side == inlet.getOpposite())
            return new ITexture[] { casing, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_OUT) };
        return new ITexture[] { casing };
    }

    /** The inlet side as the client knows it: synced in the machine's update data. */
    private ForgeDirection clientInletSide(ForgeDirection facing) {
        ForgeDirection[] choices = flowChoices(facing);
        for (ForgeDirection d : choices) if (d == mInletSide) return d;
        return choices[0];
    }

    @Override
    public byte getUpdateData() {
        return (byte) getLoopInletSide().ordinal();
    }

    @Override
    public void onValueUpdate(byte aValue) {
        mInletSide = ForgeDirection.getOrientation(aValue);
    }

    // --- Power ---

    @Override
    public boolean isElectric() {
        return usesPower();
    }

    @Override
    public boolean isEnetInput() {
        return usesPower();
    }

    @Override
    public boolean isInputFacing(ForgeDirection side) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return usesPower() && base != null && side != base.getFrontFacing() && !isLoopPortSide(side);
    }

    @Override
    public long maxEUInput() {
        return usesPower() ? GTValues.V[mTier] : 0L;
    }

    @Override
    public long maxAmperesIn() {
        return usesPower() ? 2L : 0L;
    }

    @Override
    public long maxEUStore() {
        return usesPower() ? GTValues.V[mTier] * 64L : 0L;
    }

    @Override
    public long getMinimumStoredEU() {
        return usesPower() ? GTValues.V[mTier] * 2L : 0L;
    }

    /** EU the machine holds now. */
    protected long storedEnergy() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return base != null ? base.getStoredEU() : 0L;
    }

    protected boolean drainEnergy(long eu) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return eu <= 0L || base != null && base.decreaseStoredEnergyUnits(eu, false);
    }

    // --- Loop device ---

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
        return getBaseMetaTileEntity() != null;
    }

    @Override
    public double getLoopVolumeM3() {
        return tier().volumeM3;
    }

    @Override
    public double getLoopMaxPressureBar() {
        return tier().maxPressureBar;
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
        if (pump == null) {
            mBoundSegment = null;
            IGregTechTileEntity base = getBaseMetaTileEntity();
            if (base != null && base.isActive()) base.setActive(false);
        }
    }

    @Override
    public void processThermalExchange(double flowM3s, double dt, CoolantFluidProperty fluid, LoopSegment segment) {
        mBoundSegment = segment;
        mLoopFluid = fluid;
        mLastFlowM3s = flowM3s;
        onLoopTick(flowM3s, dt, fluid, segment);
        advanceGuiPhase(dt);
    }

    /** The gas loop the machine is on, or null if it is on none or on a liquid loop. */
    protected GasLoopEngine gasEngine() {
        if (mPump instanceof ICoolantPumpHost p && p.getEngine() instanceof GasLoopEngine g) return g;
        return null;
    }

    protected GasProperty gas() {
        GasLoopEngine g = gasEngine();
        return g != null ? g.getGas() : null;
    }

    /** Ambient temperature in K: the loop's pump's, or the machine's own biome's if it is on no loop. */
    protected double ambientK() {
        if (mPump instanceof ICoolantPumpHost p) return p.getBiomeTemperatureCelsius() + 273.15;
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null && te.getWorld() != null) {
            return CoolantFluidHelper
                .calculateAmbientTemperature(te.getWorld(), te.getXCoord(), te.getYCoord(), te.getZCoord()) + 273.15;
        }
        return 293.15;
    }

    /** Whether the machine may run: allowed to work and on a loop. */
    protected boolean mayRun() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return base != null && base.isAllowedToWork() && mPump != null;
    }

    // --- Readouts ---

    @Override
    public List<String> getReadoutLines() {
        List<String> l = new ArrayList<>();
        l.add("Status: " + mStatus);
        addReadoutLines(l);
        l.add(gasStateLine());
        ForgeDirection in = getLoopInletSide();
        l.add(
            String.format(
                "Loop: in from %s, out to %s",
                in.name()
                    .toLowerCase(),
                in.getOpposite()
                    .name()
                    .toLowerCase()));
        return l;
    }

    // --- GUI: see Note [Compact machine GUIs]

    /** How long the GUI's arrow takes to fill at full load, s. */
    private static final double ARROW_SECONDS = 2.0;

    /** Where the GUI's arrow is in its cycle, 0 to 1; it moves while the machine works. */
    private double mGuiPhase = 0.0;

    /** The player's inventory is a GregTech one-block machine's, whether or not the machine has slots. */
    @Override
    public boolean doesBindPlayerInventory() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        return new CompactInlineGui<>(this).build(guiData, syncManager, uiSettings);
    }

    /** The GUI's arrow; GregTech's plain arrow unless a machine has one of its own. */
    public UITexture getGuiProgressTexture() {
        return GTGuiTextures.PROGRESSBAR_ARROW_STANDARD;
    }

    /** How hard the machine works, 0 to 1, which sets the pace of its GUI's arrow. */
    protected double guiLoad() {
        return 1.0;
    }

    /** The GUI's arrow: how far through its cycle, or 0 while the machine is idle. */
    public double getGuiProgress() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return base != null && base.isActive() ? mGuiPhase : 0.0;
    }

    private void advanceGuiPhase(double dt) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isActive()) {
            mGuiPhase = 0.0;
            return;
        }
        double load = Math.max(0.05, Math.min(1.0, guiLoad()));
        mGuiPhase = (mGuiPhase + load * dt / ARROW_SECONDS) % 1.0;
    }

    /** The lines of the arrow's tooltip: the machine's status and its own readout. */
    public List<String> getGuiProgressLines() {
        List<String> l = new ArrayList<>();
        l.add(mStatus);
        addReadoutLines(l);
        return l;
    }

    /** What keeps the machine from working at all, for the GUI's error icon; empty for nothing. */
    public String getGuiProblem() {
        if (mPump == null) return "Not on a loop";
        GasProperty g = gas();
        if (g == null || mBoundSegment == null || !mBoundSegment.holdsGas()) return "Its loop holds no gas";
        return "";
    }

    /** The loop's gas as a fluid for the GUI's inlet and outlet, or null off a charged gas loop. */
    public FluidStack getGuiLoopFluid() {
        return loopFluid(gas(), mBoundSegment);
    }

    static FluidStack loopFluid(GasProperty g, LoopSegment s) {
        if (g == null || s == null || !s.holdsGas()) return null;
        Fluid f = FluidRegistry.getFluid(g.getFluidName());
        return f != null ? new FluidStack(f, 1000) : null;
    }

    /** Pressure the loop enters the machine at, bar: a pressure stage's own, else its segment's. */
    protected double guiInletBar() {
        return mBoundSegment != null ? mBoundSegment.getInletPressureBar() : 0.0;
    }

    /** The tooltip of the GUI's inlet (true) or outlet: which side, and the gas's state there. */
    public List<String> getGuiPortLines(boolean inlet) {
        List<String> l = new ArrayList<>();
        ForgeDirection in = getLoopInletSide();
        l.add(inlet ? "Loop in, from the " + sideName(in) : "Loop out, to the " + sideName(in.getOpposite()));
        GasProperty g = gas();
        LoopSegment s = mBoundSegment;
        if (g == null || s == null || !s.holdsGas()) {
            l.add(getGuiProblem());
        } else if (inlet) {
            l.add(stateLine(g, guiInletBar(), s.getGasIncomingEnthalpy()));
        } else {
            l.add(stateLine(g, s.getOutletPressureBar(), s.getGasEnthalpy()));
        }
        return l;
    }

    static String sideName(ForgeDirection side) {
        if (side == ForgeDirection.UP) return "top";
        if (side == ForgeDirection.DOWN) return "bottom";
        return side.name()
            .toLowerCase();
    }

    /** A gas's state as a short line: pressure, temperature and phase. */
    static String stateLine(GasProperty g, double bar, double enthalpy) {
        GasProperty.State st = g.state(bar * 1e5, enthalpy, new GasProperty.State());
        String phase = st.quality >= 1.0 ? "vapour"
            : st.quality <= 0.0 ? "liquid" : String.format("%.0f%% vapour", st.quality * 100.0);
        return String.format("%.2f bar, %.1f °C, %s", bar, st.temperatureCelsius(), phase);
    }

    /** The bound segment's gas state as a short line. */
    protected String gasStateLine() {
        GasProperty g = gas();
        LoopSegment s = mBoundSegment;
        if (mPump == null) return "Not on a loop";
        if (g == null || s == null || !s.holdsGas()) return "Loop not charged";
        double x = s.getGasQuality();
        String phase = x >= 1.0 ? "vapour" : x <= 0.0 ? "liquid" : String.format("%.0f%% vapour", x * 100.0);
        return String.format(
            "%s: %.2f bar, %.1f °C, %s",
            g.getFluidName(),
            s.getInletPressureBar(),
            s.getCurrentTemperatureCelsius(),
            phase);
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public String[] getInfoData() {
        return getReadoutLines().toArray(new String[0]);
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity base, EntityPlayer player) {
        openGui(player);
        return true;
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        NBTTagList lines = new NBTTagList();
        for (String line : getReadoutLines()) lines.appendTag(new NBTTagString(line));
        tag.setTag("compactReadout", lines);
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currentTip, accessor, config);
        NBTTagList lines = accessor.getNBTData()
            .getTagList("compactReadout", Constants.NBT.TAG_STRING);
        for (int i = 0; i < lines.tagCount(); i++) currentTip.add(lines.getStringTagAt(i));
    }

    // --- Tooltip ---

    /** Lines of the item's tooltip: the machine's own, then what all compact machines share. */
    @Override
    public String[] getDescription() {
        List<String> l = new ArrayList<>();
        addTooltipLines(l);
        l.add(
            StatCollector.translateToLocalFormatted("modularnuclear.compact.tooltip.pressure", tier().maxPressureBar));
        l.add(StatCollector.translateToLocal("modularnuclear.compact.tooltip.flow"));
        l.add(StatCollector.translateToLocal("modularnuclear.compact.tooltip.nei"));
        return l.toArray(new String[0]);
    }

    protected void addTooltipLines(List<String> lines) {
        lines.add(StatCollector.translateToLocal("modularnuclear.compact." + getKind() + ".tooltip"));
    }

    // --- Inventory and saving ---

    @Override
    public boolean allowPullStack(IGregTechTileEntity base, int index, ForgeDirection side, ItemStack stack) {
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity base, int index, ForgeDirection side, ItemStack stack) {
        return false;
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {
        nbt.setByte("mInletSide", (byte) getLoopInletSide().ordinal());
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        // A machine never saved has no side yet: getLoopInletSide gives it its default
        if (nbt.hasKey("mInletSide")) mInletSide = ForgeDirection.getOrientation(nbt.getByte("mInletSide"));
    }
}
