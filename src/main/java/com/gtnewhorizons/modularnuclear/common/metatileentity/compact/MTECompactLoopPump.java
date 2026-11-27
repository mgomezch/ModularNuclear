package com.gtnewhorizons.modularnuclear.common.metatileentity.compact;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ILoopPortSides;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasLoopEngine;
import com.gtnewhorizons.modularnuclear.common.coolantloop.pump.CoolantPumpCore;
import com.gtnewhorizons.modularnuclear.common.coolantloop.pump.CoolantPumpReservoir;
import com.gtnewhorizons.modularnuclear.common.coolantloop.pump.ICoolantPumpHost;
import com.gtnewhorizons.modularnuclear.common.coolantloop.pump.LoopPort;
import com.gtnewhorizons.modularnuclear.common.coolantloop.pump.PumpImpeller;
import com.gtnewhorizons.modularnuclear.common.coolantloop.pump.PumpLimits;
import com.gtnewhorizons.modularnuclear.common.gui.CompactPumpGui;
import com.gtnewhorizons.modularnuclear.common.gui.IReadoutLines;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.util.CoolantFluidHelper;
import com.gtnewhorizons.modularnuclear.common.util.StructureChunks;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gregtech.api.util.shutdown.ShutDownReason;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * Compact Loop Pump: a one-block pump that owns a gas loop, charging it from a stack of Iron Tanks standing on it.
 * It runs its loop with the multiblock pump's own code (CoolantPumpCore), on gas loops only, within its tier's limits.
 * See Note [Compact loop machines], Note [Pump cores and their hosts] and Note [The compact pump's tanks]
 */
public class MTECompactLoopPump extends MTETieredMachineBlock
    implements ICoolantPumpHost, ILoopPortSides, IReadoutLines {

    private final CoolantPumpCore mCore = new CoolantPumpCore(this);
    /** The side the loop returns through; it leaves through the opposite one. */
    private ForgeDirection mSuctionSide = ForgeDirection.UNKNOWN;
    private PumpLimits mLimits;

    public MTECompactLoopPump(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier, 0, new String[0]);
    }

    public MTECompactLoopPump(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTECompactLoopPump(mName, mTier, mDescriptionArray, mTextures);
    }

    public CompactTier tier() {
        return CompactTier.of(mTier);
    }

    // --- The host ---

    @Override
    public CoolantPumpCore getPumpCore() {
        return mCore;
    }

    @Override
    public CoolantPumpReservoir createReservoir() {
        return new IronTankReservoir(this);
    }

    private IronTankReservoir tanks() {
        return (IronTankReservoir) mCore.getReservoir();
    }

    @Override
    public boolean isAllowedToWork() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return base != null && base.isAllowedToWork();
    }

    /**
     * It has no structure to break. Its tanks are no part of one: a pump whose tanks are taken away keeps its loop,
     * waits for gas if the loop is empty and has nowhere to return a charge to, where a multiblock pump whose
     * structure breaks under a running loop bursts it.
     */
    @Override
    public boolean isPumpStructureFormed() {
        return true;
    }

    /** Nothing to shut down: the pump carries on when its power returns. */
    @Override
    public void stopMachine(ShutDownReason reason) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) base.setActive(false);
    }

    @Override
    public boolean drainEnergy(long eu) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return eu <= 0L || base != null && base.decreaseStoredEnergyUnits(eu, false);
    }

    /** A quarter ampere of its tier, while it charges its loop. */
    @Override
    public long getRequiredPowerDrawEU() {
        return Math.max(1L, tier().euPerTick / 4L);
    }

    @Override
    public double getBiomeTemperatureCelsius() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null && te.getWorld() != null) {
            return CoolantFluidHelper
                .calculateAmbientTemperature(te.getWorld(), te.getXCoord(), te.getYCoord(), te.getZCoord());
        }
        return NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;
    }

    public ForgeDirection getSuctionSide() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        ForgeDirection[] choices = MTECompactLoopMachine
            .flowChoices(base != null ? base.getFrontFacing() : ForgeDirection.NORTH);
        for (ForgeDirection d : choices) if (d == mSuctionSide) return d;
        mSuctionSide = choices[0];
        return mSuctionSide;
    }

    @Override
    public boolean isLoopPortSide(ForgeDirection side) {
        ForgeDirection suction = getSuctionSide();
        return side == suction || side == suction.getOpposite();
    }

    private LoopPort port(ForgeDirection side) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return base == null ? null : new LoopPort(base.getXCoord(), base.getYCoord(), base.getZCoord(), side);
    }

    @Override
    public LoopPort getDischargePort() {
        return port(getSuctionSide().getOpposite());
    }

    @Override
    public LoopPort getSuctionPort() {
        return port(getSuctionSide());
    }

    /** The ports are the pump's own sides. */
    @Override
    public Boolean restoreLoopPorts(World world) {
        return Boolean.TRUE;
    }

    @Override
    public void collectPumpChunks(Set<Long> chunks, boolean beforeStructureCheck) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) StructureChunks.addBlock(base.getXCoord(), base.getZCoord(), chunks);
    }

    /** No impeller: gas loops only. See wrinkle (W1) of Note [Compact loop machines] */
    @Override
    public PumpImpeller getImpeller() {
        return null;
    }

    @Override
    public PumpLimits getPumpLimits() {
        if (mLimits == null) {
            CompactTier t = tier();
            mLimits = new PumpLimits(
                t.maxLoopVolumeLiters,
                t.maxPressureBar,
                t.fillRateFactor,
                true,
                CompactTier.LIQUID_FRACTIONS,
                t.chargePressuresBar());
        }
        return mLimits;
    }

    /** Stopped, it takes its loop's charge back into its tanks, so the loop can be rebuilt or the pump moved. */
    @Override
    public boolean recoversChargeWhenStopped() {
        return true;
    }

    // --- The machine ---

    @Override
    public void onPostTick(IGregTechTileEntity base, long tick) {
        super.onPostTick(base, tick);
        if (!base.isServerSide()) return;
        mCore.onPostTick(base, tick);
        // Without tanks an empty loop has nothing to charge from: say so in place of the core's own line
        String problem = tanks().problem();
        if (problem != null && mCore.getCurrentFillLiters() <= 0L) mCore.setLoopStatusKey(problem);
    }

    @Override
    public void onFirstTick(IGregTechTileEntity base) {
        super.onFirstTick(base);
        if (!base.isServerSide()) return;
        mCore.beforeFirstTick(base);
        mCore.afterFirstTick(base);
    }

    @Override
    public void onRemoval() {
        mCore.onRemoval();
        super.onRemoval();
    }

    @Override
    public void onUnload() {
        mCore.onUnload();
        super.onUnload();
    }

    /** A screwdriver cycles the loop's charge; sneaking, or with no gas in the tanks, the loop's direction. */
    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        if (!aPlayer.isSneaking() && mCore.cycleGasCharge(aPlayer)) return;
        if (mCore.isLoopFormed() && mCore.getCurrentFillLiters() > 0L) {
            GTUtility
                .sendChatToPlayer(aPlayer, StatCollector.translateToLocal("modularnuclear.compact.pump.flow_charged"));
            return;
        }
        IGregTechTileEntity base = getBaseMetaTileEntity();
        ForgeDirection[] choices = MTECompactLoopMachine
            .flowChoices(base != null ? base.getFrontFacing() : ForgeDirection.NORTH);
        ForgeDirection now = getSuctionSide();
        int i = 0;
        while (i < choices.length && choices[i] != now) i++;
        mSuctionSide = choices[(i + 1) % choices.length];
        if (base != null) {
            base.issueTextureUpdate();
            base.issueBlockUpdate();
        }
        GTUtility.sendChatToPlayer(
            aPlayer,
            StatCollector.translateToLocalFormatted(
                "modularnuclear.compact.flow",
                mSuctionSide.name()
                    .toLowerCase(),
                mSuctionSide.getOpposite()
                    .name()
                    .toLowerCase()));
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return facing.offsetY == 0;
    }

    @Override
    public void onFacingChange() {
        super.onFacingChange();
        getSuctionSide();
    }

    @Override
    public ITexture[][][] getTextureSet(ITexture[] aTextures) {
        return new ITexture[0][0][0];
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity base, ForgeDirection side, ForgeDirection facing, int colorIndex,
        boolean active, boolean redstone) {
        ITexture casing = Textures.BlockIcons.MACHINE_CASINGS[mTier][colorIndex + 1];
        if (side == facing) return new ITexture[] { casing, TextureFactory.of(Textures.BlockIcons.OVERLAY_PUMP) };
        ForgeDirection[] choices = MTECompactLoopMachine.flowChoices(facing);
        ForgeDirection suction = choices[0];
        for (ForgeDirection d : choices) if (d == mSuctionSide) suction = d;
        if (side == suction) return new ITexture[] { casing, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_IN) };
        if (side == suction.getOpposite())
            return new ITexture[] { casing, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_OUT) };
        return new ITexture[] { casing };
    }

    @Override
    public byte getUpdateData() {
        return (byte) getSuctionSide().ordinal();
    }

    @Override
    public void onValueUpdate(byte aValue) {
        mSuctionSide = ForgeDirection.getOrientation(aValue);
    }

    // --- Power ---

    @Override
    public boolean isElectric() {
        return true;
    }

    @Override
    public boolean isEnetInput() {
        return true;
    }

    @Override
    public boolean isInputFacing(ForgeDirection side) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return base != null && side != base.getFrontFacing() && side != ForgeDirection.UP && !isLoopPortSide(side);
    }

    @Override
    public long maxEUInput() {
        return GTValues.V[mTier];
    }

    @Override
    public long maxAmperesIn() {
        return 1L;
    }

    @Override
    public long maxEUStore() {
        return GTValues.V[mTier] * 64L;
    }

    @Override
    public long getMinimumStoredEU() {
        return GTValues.V[mTier] * 2L;
    }

    // --- Readouts ---

    // --- GUI: see Note [Compact machine GUIs]

    /** The player's inventory is a GregTech one-block machine's. See Note [Compact machine GUIs] */
    @Override
    public boolean doesBindPlayerInventory() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        return new CompactPumpGui(this).build(guiData, syncManager, uiSettings);
    }

    public int getChargeSetting() {
        return mCore.mGasChargeSetting;
    }

    /** The GUI's button: a charge setting among the pump's, as a screwdriver's click sets the next. */
    public void setChargeSetting(int setting) {
        mCore.mGasChargeSetting = Math.floorMod(setting, getPumpLimits().liquidFractions.length);
    }

    public int getChargeSettingCount() {
        return getPumpLimits().liquidFractions.length;
    }

    /** A charge setting in words: what it charges a condensing gas to, and a permanent one. */
    public String describeChargeSetting(int setting) {
        PumpLimits limits = getPumpLimits();
        int i = Math.floorMod(setting, limits.liquidFractions.length);
        return StatCollector.translateToLocalFormatted(
            "modularnuclear.compact.pump.gui.charge",
            limits.liquidFractions[i] * 100.0,
            limits.chargePressuresBar[i]);
    }

    // --- GUI: see Note [Compact machine GUIs]

    /** The arrow: how far the loop is charged. */
    public double getGuiProgress() {
        long required = mCore.getRequiredFillLiters();
        return required > 0L ? Math.min(1.0, (double) mCore.getCurrentFillLiters() / required) : 0.0;
    }

    public List<String> getGuiProgressLines() {
        List<String> l = new ArrayList<>();
        l.add(getLoopStatus());
        l.add(
            "State: " + mCore.getLoopStateName()
                + (isAllowedToWork() ? "" : " (stopped: the charge returns to the tanks)"));
        return l;
    }

    /** What keeps the pump from running its loop at all, for the GUI's error icon; empty for nothing. */
    public String getGuiProblem() {
        String problem = tanks().problem();
        if (problem != null) return StatCollector.translateToLocal(problem);
        return mCore.isLoopFormed() ? "" : getLoopStatus();
    }

    public FluidStack getGuiReservoirFluid() {
        FluidStack fluid = mCore.getReservoirFluid();
        return fluid != null ? new FluidStack(fluid.getFluid(), 1000) : null;
    }

    public List<String> getGuiReservoirLines() {
        List<String> l = new ArrayList<>();
        IronTankReservoir tanks = tanks();
        if (tanks.problem() != null) {
            l.add(StatCollector.translateToLocal(tanks.problem()));
            return l;
        }
        l.add(
            String.format(
                "%d × %s",
                tanks.getTankCount(),
                tanks.getTankBlock()
                    .getLocalizedName()));
        l.add(String.format("%,d / %,d L", tanks.getTotalCoolantLiters(), tanks.getTotalTankCapacityLiters()));
        return l;
    }

    /** The loop's gas as a fluid, or null for a loop not yet charged with one. */
    public FluidStack getGuiLoopFluid() {
        if (!(mCore.getEngine() instanceof GasLoopEngine g) || g.getGas() == null || mCore.getCurrentFillLiters() <= 0L)
            return null;
        Fluid f = FluidRegistry.getFluid(
            g.getGas()
                .getFluidName());
        return f != null ? new FluidStack(f, 1000) : null;
    }

    public List<String> getGuiLoopLines() {
        List<String> l = new ArrayList<>();
        l.add(
            "Loop: out to the " + MTECompactLoopMachine.sideName(getSuctionSide().getOpposite())
                + ", back from the "
                + MTECompactLoopMachine.sideName(getSuctionSide()));
        if (mCore.isLoopFormed()) {
            double liters = 0.0;
            if (mCore.getEngine() instanceof GasLoopEngine g) liters = g.getTotalVolumeM3() * 1000.0;
            l.add(String.format("%,.0f of %,.0f L of pipes and machines", liters, tier().maxLoopVolumeLiters));
            l.add(
                String
                    .format("Charge: %,d / %,d L of gas", mCore.getCurrentFillLiters(), mCore.getRequiredFillLiters()));
        } else {
            l.add(getLoopStatus());
        }
        l.add(String.format("Rated %.0f bar at the pump", tier().maxPressureBar));
        return l;
    }

    @Override
    public List<String> getReadoutLines() {
        List<String> l = new ArrayList<>();
        l.add(
            "State: " + mCore.getLoopStateName()
                + (isAllowedToWork() ? "" : " (stopped: the charge returns to the tanks)"));
        l.add(getLoopStatus());
        IronTankReservoir tanks = tanks();
        FluidStack fluid = mCore.getReservoirFluid();
        if (tanks.problem() == null) {
            l.add(
                String.format(
                    "Tanks: %d x %s, %,d / %,d L%s",
                    tanks.getTankCount(),
                    tanks.getTankBlock()
                        .getLocalizedName(),
                    tanks.getTotalCoolantLiters(),
                    tanks.getTotalTankCapacityLiters(),
                    fluid != null ? " of " + fluid.getLocalizedName() : ""));
        }
        if (mCore.isLoopFormed()) {
            double liters = 0.0;
            if (mCore.getEngine() instanceof GasLoopEngine g) liters = g.getTotalVolumeM3() * 1000.0;
            l.add(
                String.format(
                    "Loop: %,.0f of %,.0f L, charge %,d / %,d L of gas",
                    liters,
                    tier().maxLoopVolumeLiters,
                    mCore.getCurrentFillLiters(),
                    mCore.getRequiredFillLiters()));
        }
        l.add(String.format("Rated %.0f bar at the pump", tier().maxPressureBar));
        return l;
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

    @Override
    public String[] getDescription() {
        CompactTier t = tier();
        return new String[] { StatCollector.translateToLocal("modularnuclear.compact.pump.tooltip.1"),
            StatCollector.translateToLocal("modularnuclear.compact.pump.tooltip.2"),
            StatCollector.translateToLocal("modularnuclear.compact.pump.tooltip.3"),
            StatCollector
                .translateToLocalFormatted("modularnuclear.compact.pump.tooltip.limits", t.maxLoopVolumeLiters),
            StatCollector.translateToLocalFormatted("modularnuclear.compact.tooltip.pressure", t.maxPressureBar),
            StatCollector.translateToLocal("modularnuclear.compact.tooltip.flow"),
            StatCollector.translateToLocal("modularnuclear.compact.tooltip.nei") };
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
        nbt.setByte("mSuctionSide", (byte) getSuctionSide().ordinal());
        mCore.saveNBTData(nbt);
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        if (nbt.hasKey("mSuctionSide")) mSuctionSide = ForgeDirection.getOrientation(nbt.getByte("mSuctionSide"));
        mCore.loadNBTData(nbt);
    }
}
