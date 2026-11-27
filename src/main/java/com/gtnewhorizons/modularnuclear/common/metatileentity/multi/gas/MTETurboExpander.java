package com.gtnewhorizons.modularnuclear.common.metatileentity.multi.gas;

import static gregtech.api.util.GTUtility.validMTEList;

import java.util.ArrayList;
import java.util.Arrays;
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

import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * Turbo-Expander: the pressure stage of a Brayton loop. The gas expands through its nozzles and turns its turbine,
 * which cools it; the work goes out of its dynamo hatch as EU, and what the dynamo has no room for is lost in the
 * turbine's brake. Disabled, it free-wheels: the gas passes through without expanding work out, so without cooling.
 * A screwdriver sets its nozzle opening. See Note [Gas loop machines]
 */
public class MTETurboExpander extends MTEInlineLoopMachine<MTETurboExpander> implements GasPressureStage {

    private static final int CASING_META = 10; // Stainless Steel Turbine Casing (sBlockCasings4)
    private static IStructureDefinition<MTETurboExpander> DEFINITION;
    private static final double[] NOZZLE_OPENINGS = { 1.0, 0.75, 0.5, 0.25, 0.1 };

    private int mNozzle = 0;
    private double mLastMassFlow = 0.0, mLastWorkWatts = 0.0, mLastInBar = 0.0, mLastOutBar = 0.0;
    private double mEuCredit = 0.0;
    private long mLastEuPerTick = 0L;
    private boolean mLoaded = false;

    public MTETurboExpander(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTETurboExpander(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTETurboExpander(mName);
    }

    @Override
    public int getMinLength() {
        return 3;
    }

    @Override
    public int getMaxLength() {
        return 7;
    }

    @Override
    protected String getKind() {
        return "turbo_expander";
    }

    @Override
    protected IStructureElement<MTETurboExpander> casingElement() {
        return StructureUtility.ofBlock(GasMachineParts.casings4(), CASING_META);
    }

    @Override
    protected IStructureElement<MTETurboExpander> ringElement() {
        return GasMachineParts.tiered(
            GasMachineParts.casings2(),
            GasMachineParts.GEARBOX_METAS,
            (t, v) -> t.mRingTier = v,
            t -> t.mRingTier);
    }

    @Override
    protected IStructureElement<MTETurboExpander> coreElement() {
        return GasMachineParts.tiered(
            GasMachineParts.casings2(),
            GasMachineParts.PIPE_CASING_METAS,
            (t, v) -> t.mCoreTier = v,
            t -> t.mCoreTier);
    }

    @Override
    protected int getCasingTextureIndex() {
        return GasMachineParts.textureIndex(GasMachineParts.casings4(), CASING_META);
    }

    @Override
    protected List<IHatchElement<? super MTETurboExpander>> casingHatches() {
        return new ArrayList<>(Arrays.asList(HatchElement.Maintenance, HatchElement.Dynamo));
    }

    @Override
    protected void checkHatches(List<StructureError> errors) {
        checkOneMaintenanceHatch(errors);
        if (mDynamoHatches.isEmpty())
            errors.add(StructureErrors.of("modularnuclear.gas_loop.structure.error.no_dynamo"));
    }

    @Override
    public IStructureDefinition<MTETurboExpander> getStructureDefinition() {
        if (DEFINITION == null) DEFINITION = buildDefinition();
        return DEFINITION;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity base, ForgeDirection side, ForgeDirection facing, int color,
        boolean active, boolean redstone) {
        ITexture casing = TextureFactory.of(GasMachineParts.casings4(), CASING_META);
        if (side != facing) return new ITexture[] { casing };
        return new ITexture[] { casing, TextureFactory
            .of(active ? Textures.BlockIcons.LARGETURBINE_NEW_ACTIVE5 : Textures.BlockIcons.LARGETURBINE_NEW5) };
    }

    public double getNozzleArea() {
        return getRingCount() * GasMachineParts.expanderNozzlePerRing(Math.max(1, mRingTier))
            * NOZZLE_OPENINGS[mNozzle];
    }

    /** EU the dynamo hatches can still take this tick. */
    private long dynamoRoom() {
        long room = 0L;
        for (MTEHatchDynamo d : validMTEList(mDynamoHatches)) {
            IGregTechTileEntity b = d.getBaseMetaTileEntity();
            room += Math.max(0L, d.maxEUStore() - b.getStoredEU());
        }
        return room;
    }

    @Override
    public void computeStage(GasPressureStage.Context c) {
        mLastInBar = c.pressureIn / 1e5;
        mLastOutBar = c.pressureOut / 1e5;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (!mMachine || base == null) {
            c.clearAnswers();
            mLastMassFlow = 0.0;
            return;
        }
        checkMaintenance();
        double eta = GasMachineParts.EXPANDER_ISENTROPIC_EFFICIENCY * Math.max(0.3, getEfficiencyFactor());
        GasStageModels.Expander model = new GasStageModels.Expander(getNozzleArea(), eta);
        // Loaded while it works: its brake takes what the dynamo can't
        boolean loaded = isAllowedToWork() && c.loopRunning;
        model.compute(c, loaded);
        double watts = -c.shaftPowerWatts;
        double eu = watts * GasMachineParts.EXPANDER_GENERATOR_EFFICIENCY / (GasMachineParts.SHAFT_JOULES_PER_EU * 20.0)
            + mEuCredit;
        long whole = (long) Math.floor(eu);
        mEuCredit = eu - whole;
        long room = dynamoRoom();
        if (whole > room) {
            // The rest is lost in the brake
            whole = room;
            mEuCredit = 0.0;
        }
        if (whole > 0) addEnergyOutput(whole);
        mLoaded = loaded;
        mLastMassFlow = c.massFlow;
        mLastWorkWatts = watts;
        mLastEuPerTick = whole;
        base.setActive(loaded && c.massFlow > 0.0);
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer player, float x, float y, float z,
        ItemStack tool) {
        mNozzle = (mNozzle + 1) % NOZZLE_OPENINGS.length;
        GTUtility.sendChatToPlayer(
            player,
            String.format("Turbo-expander nozzles: %.0f%% open", NOZZLE_OPENINGS[mNozzle] * 100.0));
    }

    public void setNozzleSetting(int setting) {
        mNozzle = Math.floorMod(setting, NOZZLE_OPENINGS.length);
    }

    @Override
    public double getLoopVolumeM3() {
        return GasMachineParts.VOLUME_PER_RING_M3 * getRingCount();
    }

    @Override
    public List<String> getReadoutLines() {
        List<String> l = new ArrayList<>();
        l.add(String.format("Nozzles: %.0f%% open, %.4f m²", NOZZLE_OPENINGS[mNozzle] * 100.0, getNozzleArea()));
        l.add(mLoaded ? "Turbine loaded: expanding" : "Free-wheeling: disabled or stopped");
        l.add(String.format("%.2f bar -> %.2f bar", mLastInBar, mLastOutBar));
        l.add(String.format("Mass flow: %.1f kg/s", mLastMassFlow));
        l.add(String.format("Work: %sW, %d EU/t out", GasMachineParts.si(mLastWorkWatts), mLastEuPerTick));
        l.add(gasStateLine());
        return l;
    }

    public long getLastEuPerTick() {
        return mLastEuPerTick;
    }

    public double getLastMassFlow() {
        return mLastMassFlow;
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {
        super.saveNBTData(nbt);
        nbt.setInteger("mNozzle", mNozzle);
        nbt.setDouble("mEuCredit", mEuCredit);
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        super.loadNBTData(nbt);
        mNozzle = Math.floorMod(nbt.getInteger("mNozzle"), NOZZLE_OPENINGS.length);
        mEuCredit = nbt.getDouble("mEuCredit");
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gt.multiblock.turbo_expander.machine_type"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.turbo_expander.desc1"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.turbo_expander.desc2"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.turbo_expander.desc3"))
            .addSeparator()
            .beginVariableStructureBlock(3, 3, 3, 3, 3, 7, false)
            .addController(StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.controller"))
            .addCasingInfoMin(
                StatCollector.translateToLocal("gt.multiblock.turbo_expander.structure.casing"),
                10,
                false)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.gas_compressor.structure.rings"),
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
            .addDynamoHatch(StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.front_back"), 1)
            .addMaintenanceHatch(StatCollector.translateToLocal("gt.multiblock.gas_loop.structure.front_back"), 1)
            .toolTipFinisher(StatCollector.translateToLocal("gt.multiblock.modularnuclear.coolant_loop.finisher"));
        return tt;
    }
}
