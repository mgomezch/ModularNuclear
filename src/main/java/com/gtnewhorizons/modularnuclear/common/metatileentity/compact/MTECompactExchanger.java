package com.gtnewhorizons.modularnuclear.common.metatileentity.compact;

import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;

import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.data.CoolantDeviceData;
import com.gtnewhorizons.modularnuclear.common.config.data.CoolantDeviceData.PHEData.SecondaryCoolantEntry;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ICompactLoopDevice;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ICoolantLoopPump;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasLoopEngine;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.SegmentHeat;
import com.gtnewhorizons.modularnuclear.common.coolantloop.pump.ICoolantPumpHost;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.gas.GasMachineParts;
import com.gtnewhorizons.modularnuclear.common.util.FreezerRecipeEnergy;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.render.TextureFactory;

/*-
Note [The compact heat exchanger] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
A Compact Heat Exchanger liquefies a cryogen's gas against a cold gas loop: the one PHE gas mode (Note [PHE gas
modes], liquefaction) a small loop is for.  Its gas tank fills from any side but the front and the loop's two,
and its liquid tank drains from the same sides; it has no hatches.

A loop colder than the cryogen's boiling point by the PHE's approach temperature condenses the gas at the tier's
conductance (CompactTier.exchangerConductance) times the difference, the heat going into the loop.  A litre
takes the heat a litre boiling in a PHE gives off: the vacuum freezer's energy for it
(FreezerRecipeEnergy.applyTo), so compact and multiblock exchangers price a litre alike.  Heat short of a litre
stays in mHeatJoules for the next, as PheGasModes.carriedHeat does for a PHE's batches.

Recuperator.  The exchanger takes a second pass of a loop through its other free axis (SecondPass, a loop device
of its own, as a PHE's second pair of hatches is: PheSecondSide): from bottom to top if the first pass runs
across, from left to right if the first runs up or down.  With gas flowing through both it moves heat from the
hotter pass to the colder as a counterflow exchanger (SegmentHeat.counterflow) and liquefies nothing.  Both passes
of one Brayton loop through it make its recuperator; several in a row make a better one.  The passes may also be
of two loops.

Which gases: the PHE's own table (devices.yaml secondaryCoolants with an outputGasName): nitrogen, oxygen, air
and helium.  Cooling them takes a permanent-gas loop with a Compact Turbo-Expander and a recuperator; a
refrigerant loop does not get cold enough.

  (W1) After a load the second pass is not bound again with the loop's other devices: only the block is saved
       (CoolantPumpNbt.nodePosition), and LoopDevices.at finds the block's first pass there.  The pump's next
       scheduled crawl, within ten seconds, binds it; until then the recuperator exchanges nothing.
*/
/** Compact Heat Exchanger. See Note [The compact heat exchanger] and Note [Compact loop machines] */
public class MTECompactExchanger extends MTECompactLoopMachine {

    private final FluidTank mGasTank;
    private final FluidTank mLiquidTank;
    private double mHeatJoules = 0.0;
    private double mLastHeatWatts = 0.0;
    private long mLitersMade = 0L;
    private Fluid mMatchedGas = null;
    private SecondaryCoolantEntry mMatched = null;
    private final SecondPass mSecond = new SecondPass();
    private double mLastRecuperatedWatts = 0.0;

    /** The second pass of a loop through the exchanger. See Note [The compact heat exchanger] */
    public final class SecondPass implements ICompactLoopDevice {

        ICoolantLoopPump pump;
        LoopSegment segment;
        long lastTick = Long.MIN_VALUE;

        @Override
        public String getDeviceId() {
            return MTECompactExchanger.this.getDeviceId() + "_b";
        }

        /** Across the first pass: bottom to top, or left to right if the first pass is the vertical one. */
        @Override
        public ForgeDirection getLoopInletSide() {
            ForgeDirection first = MTECompactExchanger.this.getLoopInletSide();
            if (first.offsetY == 0) return ForgeDirection.DOWN;
            IGregTechTileEntity base = getBaseMetaTileEntity();
            return flowChoices(base != null ? base.getFrontFacing() : ForgeDirection.NORTH)[0];
        }

        @Override
        public boolean isLoopReady() {
            return MTECompactExchanger.this.isLoopReady();
        }

        @Override
        public double getLoopVolumeM3() {
            return MTECompactExchanger.this.getLoopVolumeM3();
        }

        @Override
        public double getLoopMaxPressureBar() {
            return MTECompactExchanger.this.getLoopMaxPressureBar();
        }

        @Override
        public double getMinorLossK() {
            return MTECompactExchanger.this.getMinorLossK();
        }

        @Override
        public double getDeviceTemperatureCelsius() {
            return segment != null ? segment.getCurrentTemperatureCelsius() : 20.0;
        }

        @Override
        public long getFillVolumeLiters() {
            return MTECompactExchanger.this.getFillVolumeLiters();
        }

        @Override
        public void setLoopPump(ICoolantLoopPump pump) {
            this.pump = pump;
            if (pump == null) segment = null;
        }

        @Override
        public void processThermalExchange(double flowM3s, double dt, CoolantFluidProperty fluid, LoopSegment segment) {
            this.segment = segment;
            IGregTechTileEntity base = getBaseMetaTileEntity();
            if (base != null) lastTick = base.getTimer();
        }

        /** The gas of the loop this pass is on, or null. */
        GasProperty gas() {
            return pump instanceof ICoolantPumpHost p && p.getEngine() instanceof GasLoopEngine g ? g.getGas() : null;
        }

        /** Whether gas flowed through this pass within the last two ticks. */
        boolean flowing() {
            IGregTechTileEntity base = getBaseMetaTileEntity();
            return base != null && segment != null
                && segment.holdsGas()
                && base.getTimer() - lastTick <= 2
                && segment.getGasMassFlow() > 0.0;
        }
    }

    @Override
    public ICompactLoopDevice getSecondPass() {
        return mSecond;
    }

    /** Pipes join it on both passes' sides. */
    @Override
    public boolean isLoopPortSide(ForgeDirection side) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return base == null || side != base.getFrontFacing() && side != base.getBackFacing();
    }

    public MTECompactExchanger(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier, 0);
        mGasTank = new FluidTank(capacity(aTier));
        mLiquidTank = new FluidTank(capacity(aTier));
    }

    public MTECompactExchanger(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
        mGasTank = new FluidTank(capacity(aTier));
        mLiquidTank = new FluidTank(capacity(aTier));
    }

    static int capacity(int tier) {
        return 8000 << (tier - CompactTier.FIRST);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTECompactExchanger(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    protected String getKind() {
        return "compact_exchanger";
    }

    @Override
    protected boolean usesPower() {
        return false;
    }

    @Override
    protected ITexture frontOverlay(boolean active) {
        return TextureFactory.of(
            active ? Textures.BlockIcons.OVERLAY_FRONT_HEAT_EXCHANGER_ACTIVE
                : Textures.BlockIcons.OVERLAY_FRONT_HEAT_EXCHANGER);
    }

    // --- Fluids ---

    /** The PHE's entry for the cryogen this gas condenses into, or null if it is no such gas. */
    public static SecondaryCoolantEntry cryogenOf(Fluid gas) {
        CoolantDeviceData dev = CoolantLoopDataRegistry.getDeviceData();
        CoolantDeviceData.PHEData cfg = dev != null ? dev.getPhe() : null;
        if (gas == null || cfg == null || cfg.secondaryCoolants == null) return null;
        for (SecondaryCoolantEntry e : cfg.secondaryCoolants) {
            if (e == null || e.isSteam || e.outputGasName == null) continue;
            if (!e.outputGasName.equalsIgnoreCase(gas.getName())) continue;
            if (FluidRegistry.getFluid(e.inputFluidName) == null) continue;
            // Its latent heat from the freezer's recipes, as boiling it in a PHE takes
            SecondaryCoolantEntry own = cfg.findSecondaryCoolant(e.inputFluidName);
            SecondaryCoolantEntry entry = own != null ? own : e;
            FreezerRecipeEnergy.applyTo(entry, e.inputFluidName, cfg);
            return entry;
        }
        return null;
    }

    private SecondaryCoolantEntry matched() {
        FluidStack gas = mGasTank.getFluid();
        Fluid fluid = gas != null ? gas.getFluid() : null;
        if (fluid != mMatchedGas) {
            mMatchedGas = fluid;
            mMatched = cryogenOf(fluid);
        }
        return mMatched;
    }

    /** Fluids go in and out through its back, and through the second pass's sides while no loop uses them. */
    private boolean fluidSide(ForgeDirection side) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || side == base.getFrontFacing()) return false;
        ForgeDirection first = getLoopInletSide();
        return side != first && side != first.getOpposite();
    }

    @Override
    public boolean isLiquidInput(ForgeDirection side) {
        return fluidSide(side);
    }

    @Override
    public boolean isLiquidOutput(ForgeDirection side) {
        return fluidSide(side);
    }

    @Override
    public int fill(ForgeDirection side, FluidStack fluid, boolean doFill) {
        if (fluid == null || cryogenOf(fluid.getFluid()) == null) return 0;
        return mGasTank.fill(fluid, doFill);
    }

    @Override
    public FluidStack drain(ForgeDirection side, int maxDrain, boolean doDrain) {
        return mLiquidTank.drain(maxDrain, doDrain);
    }

    @Override
    public FluidStack drain(ForgeDirection side, FluidStack fluid, boolean doDrain) {
        if (fluid == null || !fluid.isFluidEqual(mLiquidTank.getFluid())) return null;
        return mLiquidTank.drain(fluid.amount, doDrain);
    }

    @Override
    public boolean canFill(ForgeDirection side, Fluid fluid) {
        return fluidSide(side) && cryogenOf(fluid) != null;
    }

    @Override
    public boolean canDrain(ForgeDirection side, Fluid fluid) {
        return fluidSide(side);
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection side) {
        return new FluidTankInfo[] { mGasTank.getInfo(), mLiquidTank.getInfo() };
    }

    // --- Heat ---

    @Override
    protected void onLoopTick(double flowM3s, double dt, CoolantFluidProperty fluid, LoopSegment segment) {
        mLastHeatWatts = 0.0;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (segment == null || base == null) return;
        GasProperty gas = gas();
        SecondaryCoolantEntry cryogen = matched();
        mLastRecuperatedWatts = 0.0;
        if (gas == null || !segment.holdsGas()) {
            mStatus = "Needs a gas loop";
        } else if (mSecond.flowing() && mSecond.gas() != null && segment.getGasMassFlow() > 0.0) {
            // Recuperator: heat from the hotter pass to the colder
            double moved = SegmentHeat
                .counterflow(gas, segment, mSecond.gas(), mSecond.segment, tier().exchangerConductance, dt);
            mLastRecuperatedWatts = Math.abs(moved) / dt;
            mStatus = "Exchanging between its two passes";
            base.setActive(true);
            return;
        } else if (cryogen == null) {
            mStatus = "No gas to liquefy";
        } else if (segment.getGasMassFlow() <= 0.0) {
            mStatus = "No flow";
        } else {
            mLastHeatWatts = condense(cryogen, gas, segment, dt) / dt;
        }
        base.setActive(mLastHeatWatts > 0.0);
    }

    /** One tick of liquefaction. Returns the heat moved into the loop, J. */
    private double condense(SecondaryCoolantEntry cryogen, GasProperty gas, LoopSegment segment, double dt) {
        CoolantDeviceData dev = CoolantLoopDataRegistry.getDeviceData();
        double approach = dev != null && dev.getPhe() != null ? dev.getPhe()
            .getApproachMinDeltaT() : 5.0;
        double loopC = segment.getCurrentTemperatureCelsius();
        double ceilingC = cryogen.boilingPointCelsius - approach;
        Fluid liquid = FluidRegistry.getFluid(cryogen.inputFluidName);
        double latent = Math.max(1.0, cryogen.latentHeat());
        if (loopC >= ceilingC) {
            mStatus = String.format("Loop too warm: %.0f °C, needs below %.0f °C", loopC, ceilingC);
            return 0.0;
        }
        int room = mLiquidTank.getCapacity() - mLiquidTank.getFluidAmount();
        int available = Math.min(mGasTank.getFluidAmount(), room);
        if (liquid == null || available <= 0) {
            mStatus = room <= 0 ? "Liquid tank full" : "No gas to liquefy";
            return 0.0;
        }
        double q = tier().exchangerConductance * (cryogen.boilingPointCelsius - loopC) * dt;
        q = Math.min(q, Math.max(0.0, available * latent - mHeatJoules));
        double moved = q > 0.0 ? SegmentHeat.exchange(gas, segment, ceilingC + 273.15, q, dt) : 0.0;
        mHeatJoules += moved;
        int liters = Math.min(available, (int) Math.floor(mHeatJoules / latent));
        if (liters > 0) {
            mGasTank.drain(liters, true);
            mLiquidTank.fill(new FluidStack(liquid, liters), true);
            mHeatJoules -= liters * latent;
            mLitersMade += liters;
        }
        mStatus = "Liquefying " + cryogen.inputFluidName;
        return moved;
    }

    @Override
    protected void addReadoutLines(List<String> l) {
        l.add(
            String.format(
                "Heat taken: %sW, %,d L made, %.0f%% of the next",
                GasMachineParts.si(mLastHeatWatts),
                mLitersMade,
                mMatched != null ? 100.0 * mHeatJoules / Math.max(1.0, mMatched.latentHeat()) : 0.0));
        if (mSecond.segment != null) {
            l.add(
                String.format(
                    "Second pass: %.1f °C, %sW exchanged",
                    mSecond.segment.getCurrentTemperatureCelsius(),
                    GasMachineParts.si(mLastRecuperatedWatts)));
        }
        l.add(String.format("Gas: %s", describe(mGasTank)));
        l.add(String.format("Liquid: %s", describe(mLiquidTank)));
    }

    private static String describe(FluidTank tank) {
        FluidStack f = tank.getFluid();
        return f == null || f.amount <= 0 ? "empty"
            : String.format("%,d / %,d L of %s", f.amount, tank.getCapacity(), f.getLocalizedName());
    }

    @Override
    protected void addTooltipLines(List<String> lines) {
        lines.add(StatCollector.translateToLocal("modularnuclear.compact.compact_exchanger.tooltip"));
        lines.add(
            StatCollector.translateToLocalFormatted(
                "modularnuclear.compact.compact_exchanger.tooltip.stats",
                GasMachineParts.si(tier().exchangerConductance),
                capacity(mTier)));
    }

    public long getLitersMade() {
        return mLitersMade;
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {
        super.saveNBTData(nbt);
        nbt.setDouble("mHeatJoules", mHeatJoules);
        nbt.setLong("mLitersMade", mLitersMade);
        nbt.setTag("mGasTank", mGasTank.writeToNBT(new NBTTagCompound()));
        nbt.setTag("mLiquidTank", mLiquidTank.writeToNBT(new NBTTagCompound()));
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        super.loadNBTData(nbt);
        mHeatJoules = nbt.getDouble("mHeatJoules");
        mLitersMade = nbt.getLong("mLitersMade");
        mGasTank.readFromNBT(nbt.getCompoundTag("mGasTank"));
        mLiquidTank.readFromNBT(nbt.getCompoundTag("mLiquidTank"));
    }
}
