package com.gtnewhorizons.modularnuclear.common.metatileentity.multi.gas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.SegmentHeat;

import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * Radiator: fans blow ambient air over the loop's fluid and carry its heat away, never below the loop pump's ambient
 * temperature. The condenser of an early refrigeration loop, and works on liquid loops too. The fans draw EU; with
 * them stopped (disabled or unpowered) only still air cools it. See Note [Gas loop machines]
 */
public class MTERadiator extends MTEInlineLoopMachine<MTERadiator> {

    private static IStructureDefinition<MTERadiator> DEFINITION;

    private double mLastHeatWatts = 0.0;
    private long mLastEuPerTick = 0L;
    private boolean mFansRunning = false;

    public MTERadiator(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTERadiator(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTERadiator(mName);
    }

    @Override
    public int getMinLength() {
        return 3;
    }

    @Override
    public int getMaxLength() {
        return 9;
    }

    @Override
    protected String getKind() {
        return "radiator";
    }

    @Override
    protected IStructureElement<MTERadiator> casingElement() {
        return StructureUtility.ofBlock(GasMachineParts.casingsNH(), GasMachineParts.VENT_META);
    }

    @Override
    protected IStructureElement<MTERadiator> ringElement() {
        return GasMachineParts.tiered(
            GasMachineParts.casingsNH(),
            GasMachineParts.FAN_METAS,
            (t, v) -> t.mRingTier = v,
            t -> t.mRingTier);
    }

    @Override
    protected IStructureElement<MTERadiator> coreElement() {
        return GasMachineParts.tiered(
            GasMachineParts.casings2(),
            GasMachineParts.PIPE_CASING_METAS,
            (t, v) -> t.mCoreTier = v,
            t -> t.mCoreTier);
    }

    @Override
    protected int getCasingTextureIndex() {
        return GasMachineParts.textureIndex(GasMachineParts.casingsNH(), GasMachineParts.VENT_META);
    }

    @Override
    protected List<IHatchElement<? super MTERadiator>> casingHatches() {
        return new ArrayList<>(Arrays.asList(HatchElement.Maintenance, HatchElement.Energy));
    }

    @Override
    protected void checkHatches(List<StructureError> errors) {
        checkOneMaintenanceHatch(errors);
        if (mEnergyHatches.isEmpty()) checkHasEnergyHatch(errors);
    }

    @Override
    public IStructureDefinition<MTERadiator> getStructureDefinition() {
        if (DEFINITION == null) DEFINITION = buildDefinition();
        return DEFINITION;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity base, ForgeDirection side, ForgeDirection facing, int color,
        boolean active, boolean redstone) {
        ITexture casing = TextureFactory.of(GasMachineParts.casingsNH(), GasMachineParts.VENT_META);
        if (side != facing) return new ITexture[] { casing };
        return new ITexture[] { casing,
            TextureFactory.of(
                active ? Textures.BlockIcons.OVERLAY_FRONT_VACUUM_FREEZER_ACTIVE
                    : Textures.BlockIcons.OVERLAY_FRONT_VACUUM_FREEZER) };
    }

    public int getFanTier() {
        return Math.max(1, mRingTier);
    }

    public long getFanEuPerTick() {
        return getRingCount() * GasMachineParts.radiatorFanEuPerRing(getFanTier());
    }

    /** Conductance (W/K) with the fans running. */
    public double getConductance() {
        return getRingCount() * GasMachineParts.radiatorConductancePerRing(getFanTier()) * getEfficiencyFactor();
    }

    @Override
    protected void onLoopTick(double flowM3s, double dt, CoolantFluidProperty fluid, LoopSegment segment) {
        mLastHeatWatts = 0.0;
        mLastEuPerTick = 0L;
        mFansRunning = false;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (!mMachine || segment == null || base == null) return;
        checkMaintenance();
        boolean flowing = segment.holdsGas() ? segment.getGasMassFlow() > 0.0 : flowM3s > 1e-9;
        if (!flowing) {
            base.setActive(false);
            return;
        }
        double ua = getConductance() * GasMachineParts.RADIATOR_STILL_AIR;
        long fanEu = getFanEuPerTick();
        if (isAllowedToWork() && storedEnergy() >= fanEu && drainEnergyInput(fanEu)) {
            ua = getConductance();
            mFansRunning = true;
            mLastEuPerTick = fanEu;
        }
        double ambient = ambientK();
        double heat;
        GasProperty gas = gas();
        if (segment.holdsGas() && gas != null) {
            heat = -SegmentHeat.exchangeThrough(gas, segment, ambient, ua, dt);
        } else {
            heat = coolLiquid(segment, fluid, flowM3s, ua, ambient - 273.15, dt);
        }
        mLastHeatWatts = heat / dt;
        base.setActive(mFansRunning);
    }

    /**
     * Cools liquid coolant flowing through towards ambient, as the gas path does: effectiveness-NTU from the inlet
     * temperature, applied over what flows through in the step. Returns the heat taken out, J.
     */
    public static double coolLiquid(LoopSegment s, CoolantFluidProperty fluid, double flowM3s, double ua,
        double ambientC, double dt) {
        if (fluid == null || flowM3s <= 1e-9) return 0.0;
        double rhoCp = fluid.getDensity() * fluid.getSpecificHeat();
        double streamWPerK = flowM3s * rhoCp;
        double tIn = s.getIncomingTemperatureCelsius();
        if (tIn <= ambientC) return 0.0;
        double eps = 1.0 - Math.exp(-ua / streamWPerK);
        double heat = eps * streamWPerK * (tIn - ambientC) * dt;
        double volume = Math.max(s.getVolume(), flowM3s * dt);
        double t = s.getCurrentTemperatureCelsius();
        double next = Math.max(ambientC, t - heat / (volume * rhoCp));
        s.setCurrentTemperatureCelsius(next);
        return (t - next) * volume * rhoCp;
    }

    @Override
    public double getLoopVolumeM3() {
        return GasMachineParts.VOLUME_PER_RING_M3 * getRingCount();
    }

    public double getLastHeatWatts() {
        return mLastHeatWatts;
    }

    @Override
    public List<String> getReadoutLines() {
        List<String> l = new ArrayList<>();
        l.add(
            String.format(
                "Fans: tier %d, %d rings, %s",
                getFanTier(),
                getRingCount(),
                mFansRunning ? "running" : "stopped"));
        l.add(
            String.format(
                "Conductance: %sW/K (%d EU/t for the fans)",
                GasMachineParts.si(getConductance()),
                getFanEuPerTick()));
        l.add(String.format("Ambient: %.1f °C", ambientK() - 273.15));
        l.add(String.format("Heat rejected: %sW", GasMachineParts.si(mLastHeatWatts)));
        l.add(gasStateLine());
        return l;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gt.multiblock.radiator.machine_type"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.radiator.desc1"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.radiator.desc2"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.radiator.desc3"))
            .addSeparator()
            .beginVariableStructureBlock(3, 3, 3, 3, 3, 9, false)
            .addController(StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.controller"))
            .addCasingInfoMin(StatCollector.translateToLocal("gt.multiblock.radiator.structure.casing"), 10, false)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.radiator.structure.rings"),
                StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.rings_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.core"),
                StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.core_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.inlet"),
                StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.inlet_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.outlet"),
                StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.outlet_pos"))
            .addEnergyHatch(StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.front_back"), 1)
            .addMaintenanceHatch(StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.front_back"), 1)
            .toolTipFinisher(StatCollector.translateToLocal("gt.multiblock.modularnuclear.coolant_loop.finisher"));
        return tt;
    }
}
