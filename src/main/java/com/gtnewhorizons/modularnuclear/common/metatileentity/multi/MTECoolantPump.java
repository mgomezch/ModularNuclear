package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.pump.CoolantPumpCore;
import com.gtnewhorizons.modularnuclear.common.coolantloop.pump.CoolantPumpCore.LoopState;
import com.gtnewhorizons.modularnuclear.common.coolantloop.pump.CoolantPumpReservoir;
import com.gtnewhorizons.modularnuclear.common.coolantloop.pump.ICoolantPumpHost;
import com.gtnewhorizons.modularnuclear.common.coolantloop.pump.LoopPort;
import com.gtnewhorizons.modularnuclear.common.coolantloop.pump.PumpImpeller;
import com.gtnewhorizons.modularnuclear.common.gui.CoolantLoopMachineGui;
import com.gtnewhorizons.modularnuclear.common.metatileentity.CoolantStructureChannels;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure.CoolantPumpStructure;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.util.RailcraftPumpReservoir;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.util.ClientText;
import com.gtnewhorizons.modularnuclear.common.util.CoolantFluidHelper;
import com.gtnewhorizons.modularnuclear.common.util.LocalizedText;
import com.gtnewhorizons.modularnuclear.common.util.LocalizedTextNbt;
import com.gtnewhorizons.modularnuclear.common.util.MonitorCover;
import com.gtnewhorizons.modularnuclear.common.util.MultiblockStartup;
import com.gtnewhorizons.modularnuclear.common.util.RotorThermalHelper;
import com.gtnewhorizons.modularnuclear.common.util.StructureChunks;
import com.gtnewhorizons.modularui.api.widget.IWidgetBuilder;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.items.MetaGeneratedTool;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.TurbineStatCalculator;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * Multiblock Coolant Loop Pump.
 *
 * Positions directly underneath a Railcraft multiblock tank.
 * Solves the closed loop hydrodynamics and advective heat transport.
 */
public class MTECoolantPump extends MTEEnhancedMultiBlockBase<MTECoolantPump>
    implements ICoolantPumpHost, PumpImpeller, ISurvivalConstructable {

    /** The pump's loop. See Note [Pump cores and their hosts] */
    protected final CoolantPumpCore mCore = createPumpCore();

    protected CoolantPumpCore createPumpCore() {
        return new CoolantPumpCore(this);
    }

    @Override
    public CoolantPumpCore getPumpCore() {
        return mCore;
    }

    @Override
    public PumpImpeller getImpeller() {
        return this;
    }

    @Override
    public boolean isPumpStructureFormed() {
        return mMachine;
    }

    @Override
    public LoopPort getDischargePort() {
        return mDischargeHatch != null ? LoopPort.ofHatch(mDischargeHatch.getBaseMetaTileEntity()) : null;
    }

    @Override
    public LoopPort getSuctionPort() {
        return mSuctionHatch != null ? LoopPort.ofHatch(mSuctionHatch.getBaseMetaTileEntity()) : null;
    }

    /** The hatch saved at a position, or null if it is gone; UNLOADED while its chunk is not loaded. */
    private static Object savedHatch(World w, int[] coord) {
        if (coord == null) return null;
        if (!w.blockExists(coord[0], coord[1], coord[2])) return UNLOADED;
        return w.getTileEntity(coord[0], coord[1], coord[2]) instanceof IGregTechTileEntity gte
            && gte.getMetaTileEntity() instanceof MTEHatchPressurizedFluid hatch ? hatch : null;
    }

    private static final Object UNLOADED = new Object();

    @Override
    public Boolean restoreLoopPorts(World w) {
        Object discharge = savedHatch(w, mSavedDischargeCoord);
        if (discharge == UNLOADED) return null;
        if (discharge != null) mDischargeHatch = (MTEHatchPressurizedFluid) discharge;
        Object suction = savedHatch(w, mSavedSuctionCoord);
        if (suction == UNLOADED) return null;
        if (suction != null) mSuctionHatch = (MTEHatchPressurizedFluid) suction;
        return discharge != null && suction != null;
    }

    @Override
    public void collectPumpChunks(Set<Long> chunks, boolean beforeStructureCheck) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        // The pump's width is not known before its structure check, so that covers the widest pump and tank
        if (base != null) collectPumpChunks(base, beforeStructureCheck ? MAX_PUMP_WIDTH : mWidth, chunks);
    }

    // Multiblock dimensions (dynamic footprint W x L, fixed height 2)
    protected int mWidth = 3;
    protected int mLength = 3;
    protected final int mHeight = 2;

    // Hatches
    public MTEHatchPressurizedFluid mDischargeHatch = null;
    public MTEHatchPressurizedFluid mSuctionHatch = null;
    /** The widest pump, matching the widest Railcraft tank interior. */
    private static final int MAX_PUMP_WIDTH = 9;
    protected Materials mTankMaterial = Materials.Steel;

    // Impeller slot
    protected ItemStack mRotorStack = null;
    protected double mRotorEfficiency = 0.85;
    protected Materials mRotorMaterial = null;

    /** The reservoir over the Railcraft tank above the pump. */
    @Override
    public CoolantPumpReservoir createReservoir() {
        return new RailcraftPumpReservoir(this);
    }

    protected int[] mSavedDischargeCoord = null;
    protected int[] mSavedSuctionCoord = null;

    public boolean hasComputerMonitorCover() {
        return MonitorCover.isOn(getBaseMetaTileEntity());
    }

    @Override
    public boolean allowCoverOnSide(ForgeDirection side, ItemStack coverItem) {
        if (MonitorCover.is(coverItem)) return true;
        return super.allowCoverOnSide(side, coverItem);
    }

    @Override
    public void onEnableWorking() {
        super.onEnableWorking();
        mCore.onEnableWorking();
    }

    public boolean isMachineFormed() {
        return mMachine;
    }

    public int getWidth() {
        return mWidth;
    }

    public void setWidth(int width) {
        this.mWidth = width;
    }

    public int getLength() {
        return mLength;
    }

    public void setLength(int length) {
        this.mLength = length;
    }

    public boolean isDischargeHatchDisabled() {
        if (mDischargeHatch == null) return false;
        return !mDischargeHatch.isAllowedToWork();
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        if (mCore.cycleGasCharge(aPlayer)) return;
        super.onScrewdriverRightClick(side, aPlayer, aX, aY, aZ, aTool);
    }

    public MTECoolantPump(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTECoolantPump(String aName) {
        super(aName);
    }

    @Override
    public boolean shouldCheckMaintenance() {
        return true;
    }

    public double getEfficiencyFactor() {
        int eff = getCurrentEfficiency(null);
        if (eff < 0) eff = 0;
        if (eff > 10000) eff = 10000;
        return eff / 10000.0;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTECoolantPump(mName);
    }

    @Override
    public ItemStack getMachineCraftingIcon() {
        if (getBaseMetaTileEntity() == null) {
            return null;
        }
        return super.getMachineCraftingIcon();
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockCasings2, 15),
                TextureFactory.of(
                    aActive ? Textures.BlockIcons.OVERLAY_FRONT_WATER_PUMP_ACTIVE
                        : Textures.BlockIcons.OVERLAY_FRONT_WATER_PUMP) };
        }
        return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockCasings2, 15) };
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> aErrors) {
        mDischargeHatch = null;
        mSuctionHatch = null;
        mEnergyHatches.clear();
        mMaintenanceHatches.clear();
        mInputBusses.clear();

        // Check Railcraft tank presence above pump
        boolean hasValidTank = verifyRailcraftTankAbove(aBaseMetaTileEntity);
        if (!hasValidTank) {
            if ("modularnuclear.coolant_loop.pump.status.formed".equals(mCore.mLoopStatus.key)
                || mCore.mLoopStatus.equals(LocalizedText.EMPTY)) {
                setLoopStatusKey("modularnuclear.coolant_loop.pump.status.missing_or_mismatched_tank");
            }
            aErrors
                .add(StructureErrors.of("modularnuclear.coolant_loop.structure.error.pump.missing_or_mismatched_tank"));
            return;
        }

        World world = aBaseMetaTileEntity.getWorld();
        if (world != null) {
            List<MTEHatchPressurizedFluid> pressHatches = CoolantPumpStructureScan
                .scan(this, aBaseMetaTileEntity, world, aErrors);
            if (pressHatches == null) return;

            if (mDischargeHatch == null) {
                setLoopStatusKey("modularnuclear.coolant_loop.pump.status.req_hatches");
                aErrors.add(StructureErrors.of("modularnuclear.coolant_loop.structure.error.missing_discharge_hatch"));
            }
            if (mSuctionHatch == null) {
                setLoopStatusKey("modularnuclear.coolant_loop.pump.status.req_hatches");
                aErrors.add(StructureErrors.of("modularnuclear.coolant_loop.structure.error.missing_suction_hatch"));
            }

            checkOneMaintenanceHatch(aErrors);

            if (mEnergyHatches.isEmpty()) {
                checkHasEnergyHatch(aErrors);
            } else if (mEnergyHatches.size() > 2) {
                checkHatchMax(aErrors, HatchElement.Energy, 2);
            }

            if (mInputBusses.size() > 1) {
                checkHatchMax(aErrors, HatchElement.InputBus, 1);
            }

            if (!aErrors.isEmpty()) {
                return;
            }

            CoolantPumpStructureScan.texture(this, pressHatches);
        }

        checkMaintenance();
        setLoopStatusKey("modularnuclear.coolant_loop.pump.status.formed");
        // A saved loop is restored by rebindRestoredLoop, which re-crawls only if it changed
        if (mCore.mActiveCrawl != null && !mCore.mActiveCrawl.isDone() || mCore.mNeedsPostLoadRebind) {
            return;
        }
        crawlLoopGraph();
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        return checkMachine_TT(aBaseMetaTileEntity);
    }

    /**
     * Checks if a Railcraft multiblock tank sits directly above this pump structure,
     * fully formed with matching footprint and containing coolant fluid.
     */
    public boolean verifyRailcraftTankAbove(IGregTechTileEntity aBaseMetaTileEntity) {
        return ((RailcraftPumpReservoir) mCore.mReservoir).verifyRailcraftTankAbove(aBaseMetaTileEntity);
    }

    public static boolean isPlainRegularWater(net.minecraftforge.fluids.Fluid fluid) {
        return CoolantFluidHelper.isPlainRegularWater(fluid);
    }

    public static boolean isPlainRegularWater(FluidStack stack) {
        return CoolantFluidHelper.isPlainRegularWater(stack);
    }

    public static boolean isLiquidNuclearFuel(FluidStack stack) {
        return CoolantFluidHelper.isLiquidNuclearFuel(stack);
    }

    public static boolean isLiquidNuclearFuel(String name) {
        return CoolantFluidHelper.isLiquidNuclearFuel(name);
    }

    public static boolean isGaseousFluid(net.minecraftforge.fluids.Fluid fluid, FluidStack stack) {
        return CoolantFluidHelper.isGaseousFluid(fluid, stack);
    }

    public static boolean isGaseousFluid(FluidStack stack) {
        return CoolantFluidHelper.isGaseousFluid(stack);
    }

    public static boolean isLava(String name) {
        return CoolantFluidHelper.isLava(name);
    }

    public static boolean isLava(net.minecraftforge.fluids.Fluid fluid, FluidStack stack) {
        return CoolantFluidHelper.isLava(fluid, stack);
    }

    public static boolean isLava(FluidStack stack) {
        return CoolantFluidHelper.isLava(stack);
    }

    public static boolean isNetherWorld(World world) {
        return CoolantFluidHelper.isNetherWorld(world);
    }

    public static boolean isMoltenFluid(net.minecraftforge.fluids.Fluid fluid, FluidStack stack,
        CoolantFluidProperty prop) {
        return CoolantFluidHelper.isMoltenFluid(fluid, stack, prop);
    }

    public static boolean isMoltenFluid(FluidStack stack) {
        return CoolantFluidHelper.isMoltenFluid(stack);
    }

    public static double getDeclaredGregTechFluidTemperatureCelsius(net.minecraftforge.fluids.Fluid fluid,
        FluidStack stack, CoolantFluidProperty prop) {
        return CoolantFluidHelper.getDeclaredGregTechFluidTemperatureCelsius(fluid, stack, prop);
    }

    public static double getFluidStackTemperatureCelsius(FluidStack stack) {
        return CoolantFluidHelper.getFluidStackTemperatureCelsius(stack);
    }

    public static double calculateAmbientTemperature(World world, int x, int y, int z) {
        return CoolantFluidHelper.calculateAmbientTemperature(world, x, y, z);
    }

    public double getBiomeTemperatureCelsius() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null && te.getWorld() != null) {
            return calculateAmbientTemperature(te.getWorld(), te.getXCoord(), te.getYCoord(), te.getZCoord());
        }
        return NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity != null && aBaseMetaTileEntity.isServerSide()) {
            if (!hasRotor()) {
                checkAndLoadImpellerFromInputBus();
            }
            mCore.onPostTick(aBaseMetaTileEntity, aTick);
        }
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        if (!aBaseMetaTileEntity.isServerSide()) return;
        mCore.beforeFirstTick(aBaseMetaTileEntity);
        MultiblockStartup.formNow(this);
        mCore.afterFirstTick(aBaseMetaTileEntity);
    }

    public List<MTEHatchEnergy> getValidEnergyHatches() {
        List<MTEHatchEnergy> list = new ArrayList<>();
        if (mEnergyHatches != null) {
            for (MTEHatchEnergy hatch : mEnergyHatches) {
                if (hatch != null && (hatch.isValid() || hatch.getBaseMetaTileEntity() != null)) {
                    list.add(hatch);
                }
            }
        }
        return list;
    }

    public long getRequiredPowerDrawEU() {
        List<MTEHatchEnergy> hatches = getValidEnergyHatches();
        if (hatches.isEmpty()) {
            return 512L;
        }
        if (hatches.size() == 1) {
            int tier = Math.max(0, Math.min(hatches.get(0).mTier, GTValues.V.length - 1));
            return GTValues.V[tier];
        }
        int maxTier = 0;
        for (MTEHatchEnergy h : hatches) {
            if (h.mTier > maxTier) {
                maxTier = h.mTier;
            }
        }
        maxTier = Math.max(0, Math.min(maxTier, GTValues.V.length - 1));
        return 4L * GTValues.V[maxTier];
    }

    public boolean drainEnergy(long aEU) {
        if (aEU <= 0) {
            mCore.mPowerFailed = false;
            return true;
        }

        List<MTEHatchEnergy> hatches = getValidEnergyHatches();
        if (!hatches.isEmpty()) {
            long totalStored = 0L;
            for (MTEHatchEnergy h : hatches) {
                IGregTechTileEntity bte = h.getBaseMetaTileEntity();
                if (bte != null) {
                    totalStored += bte.getStoredEU();
                }
            }

            if (totalStored < aEU) {
                return false;
            }

            long remaining = aEU;
            for (MTEHatchEnergy h : hatches) {
                IGregTechTileEntity bte = h.getBaseMetaTileEntity();
                if (bte != null) {
                    long stored = bte.getStoredEU();
                    if (stored > 0) {
                        long toDrain = Math.min(stored, remaining);
                        bte.decreaseStoredEnergyUnits(toDrain, false);
                        remaining -= toDrain;
                        if (remaining <= 0) {
                            mCore.mPowerFailed = false;
                            return true;
                        }
                    }
                }
            }
            if (remaining <= 0) {
                mCore.mPowerFailed = false;
                return true;
            }
            return false;
        }

        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().getStoredEU() >= aEU) {
            boolean drained = getBaseMetaTileEntity().decreaseStoredEnergyUnits(aEU, false);
            if (drained) {
                mCore.mPowerFailed = false;
            }
            return drained;
        }
        return false;
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

    /**
     * The chunks of the pump and the Railcraft tank on it. The tank is one block wider than the pump on every side
     * (its walls), and Railcraft checks one more block around it, so its structure can reach into the next chunk.
     */
    static void collectPumpChunks(IGregTechTileEntity base, int width, Set<Long> chunks) {
        ForgeDirection front = base.getFrontFacing();
        boolean faced = front != null && front != ForgeDirection.UNKNOWN;
        ForgeDirection back = faced ? front.getOpposite() : ForgeDirection.SOUTH;
        ForgeDirection right = faced ? front.getRotation(ForgeDirection.UP) : ForgeDirection.WEST;
        int reach = width / 2 + 2;
        for (int side : new int[] { -reach, reach }) {
            for (int depth : new int[] { -2, width + 1 }) {
                StructureChunks.addBlock(
                    base.getXCoord() + right.offsetX * side + back.offsetX * depth,
                    base.getZCoord() + right.offsetZ * side + back.offsetZ * depth,
                    chunks);
            }
        }
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        return true;
    }

    public static boolean isMetaGeneratedTool(Item item) {
        if (item == null) return false;
        if (item instanceof MetaGeneratedTool) {
            return true;
        }
        Class<?> clazz = item.getClass();
        while (clazz != null && clazz != Object.class) {
            if (clazz.getName()
                .endsWith("MetaGeneratedTool")) {
                return true;
            }
            clazz = clazz.getSuperclass();
        }

        return false;
    }

    public static boolean isValidRotor(ItemStack aStack) {
        if (aStack == null || aStack.getItem() == null) return false;
        if (isMetaGeneratedTool(aStack.getItem())) {
            int damage = aStack.getItemDamage();
            return damage >= 170 && damage <= 179;
        }
        return false;
    }

    public boolean hasImpeller() {
        return hasRotor();
    }

    public boolean checkAndLoadImpellerFromInputBus() {
        return CoolantPumpImpeller.checkAndLoadImpellerFromInputBus(this);
    }

    public ItemStack getRotor() {
        ItemStack controller = getControllerSlot();
        if (isValidRotor(controller)) {
            return controller;
        }
        if (mRotorStack != null) {
            if (isValidRotor(mRotorStack)) {
                return mRotorStack;
            }
            if (getBaseMetaTileEntity() == null || getBaseMetaTileEntity().getWorld() == null) {
                return mRotorStack;
            }
        }
        if (checkAndLoadImpellerFromInputBus()) {
            ItemStack loaded = getControllerSlot();
            if (isValidRotor(loaded)) {
                return loaded;
            }
            return mRotorStack;
        }
        return null;
    }

    ItemStack mCachedRotorObj = null;
    int mCachedRotorDamage = -1;

    public boolean updateRotorEfficiency() {
        return CoolantPumpImpeller.updateRotorEfficiency(this);
    }

    public void setRotorStack(ItemStack stack) {
        this.mRotorStack = stack;
        this.mCachedRotorObj = stack;
        this.mCachedRotorDamage = stack != null ? stack.getItemDamage() : -1;
        this.mRotorMaterial = RotorThermalHelper.getRotorMaterial(stack);
        if (stack != null && isMetaGeneratedTool(stack.getItem())) {
            TurbineStatCalculator calc = new TurbineStatCalculator((MetaGeneratedTool) stack.getItem(), stack);
            this.mRotorEfficiency = calc.getBaseEfficiency();

        }
    }

    public ItemStack getRotorStack() {
        return getRotor();
    }

    public double getRotorEfficiency() {
        return mRotorEfficiency;
    }

    @Override
    public Materials getRotorMaterial() {
        if (mRotorMaterial != null) return mRotorMaterial;
        ItemStack r = getRotor();
        if (r != null) {
            mRotorMaterial = RotorThermalHelper.getRotorMaterial(r);
        }
        return mRotorMaterial;
    }

    public void setRotorMaterial(Materials mat) {
        this.mRotorMaterial = mat;
    }

    @Override
    public double getHomologousTemperature() {
        return RotorThermalHelper.calculateHomologousTemperature(getPumpFluidTemperatureCelsius(), getRotorMaterial());
    }

    @Override
    public double getThermalWearMultiplier() {
        return RotorThermalHelper.calculateThermalWearMultiplier(getHomologousTemperature());
    }

    @Override
    public double getRotorMeltingPointCelsius() {
        return RotorThermalHelper.getMeltingPointCelsius(getRotorMaterial());
    }

    @Override
    public double getRotorSofteningCelsius() {
        return RotorThermalHelper.getThermalSofteningCelsius(getRotorMaterial());
    }

    @Override
    public boolean isCorrectMachinePart(ItemStack aStack) {
        return isValidRotor(aStack);
    }

    @Override
    public int getDamageToComponent(ItemStack aStack) {
        return 1;
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        if (aStack == null) return 10000;
        if (isValidRotor(aStack)) return 10000;
        return 0;
    }

    public void failOnImpellerMelting(double fluidTemp, Materials mat) {
        destroyImpeller();
        mCore.mLoopState = LoopState.STOPPED;
        mCore.mEngine.setPumpPowered(false);
        mCore.mEngine.setPumpMechanicalPowerWatts(0.0);
        mCore.mEngine.setBraking(false);
        // Do NOT abruptly zero flow rate! Fluid continues circulating through inertia and slowing with drag.
        String matName = mat != null ? RotorThermalHelper.getMaterialLocalizedName(mat) : "impeller";
        double meltC = RotorThermalHelper.getMeltingPointCelsius(mat);
        setLoopStatusKey("modularnuclear.coolant_loop.pump.status.catastrophic_melt", fluidTemp, meltC, matName);
    }

    public void destroyImpeller() {
        CoolantPumpImpeller.destroyImpeller(this);
    }

    double mRotorDamageAccumulator = 0.0;

    public double getRotorDamageAccumulator() {
        return mRotorDamageAccumulator;
    }

    public void setRotorDamageAccumulator(double accumulator) {
        this.mRotorDamageAccumulator = accumulator;
    }

    public void degradeRotor(long euPerTick) {
        CoolantPumpImpeller.degradeRotor(this, euPerTick);
    }

    @Override
    public boolean hasRotor() {
        return getRotor() != null;
    }

    @Override
    public int getRotorDamage() {
        ItemStack rotor = getRotor();
        if (rotor == null) return 0;
        if (rotor.getItem() instanceof MetaGeneratedTool) {
            return (int) Math.min(Integer.MAX_VALUE, MetaGeneratedTool.getToolDamage(rotor));
        }
        return rotor.getItemDamage();
    }

    @Override
    public int getRotorMaxDamage() {
        ItemStack rotor = getRotor();
        if (rotor == null) return 0;
        if (rotor.getItem() instanceof MetaGeneratedTool) {
            return (int) Math.min(Integer.MAX_VALUE, MetaGeneratedTool.getToolMaxDamage(rotor));
        }
        return rotor.getMaxDamage();
    }

    @Override
    public double getRotorDurabilityPercent() {
        ItemStack rotor = getRotor();
        if (rotor != null) {
            if (rotor.getItem() instanceof MetaGeneratedTool) {
                long max = MetaGeneratedTool.getToolMaxDamage(rotor);
                if (max > 0) {
                    long dmg = MetaGeneratedTool.getToolDamage(rotor);
                    return Math.max(0.0, 100.0 * (1.0 - (double) dmg / max));
                }
            } else if (rotor.getMaxDamage() > 0) {
                return Math.max(0.0, 100.0 * (1.0 - (double) rotor.getItemDamage() / rotor.getMaxDamage()));
            }
        }
        return 0.0;
    }

    @Override
    public boolean checkRecipe(ItemStack aStack) {
        return mCore.mLoopFormed;
    }

    @Override
    public boolean showRecipeTextInGUI() {
        return false;
    }

    @Override
    public boolean shouldDisplayCheckRecipeResult() {
        return false;
    }

    @Override
    public boolean hasRunningText() {
        return mCore.mLoopFormed && super.hasRunningText();
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
    public boolean supportsVoidProtection() {
        return false;
    }

    @Override
    public boolean supportsInputSeparation() {
        return false;
    }

    @Override
    public boolean supportsBatchMode() {
        return false;
    }

    @Override
    public ButtonWidget createLockToSingleRecipeButton(IWidgetBuilder<?> builder) {
        return (ButtonWidget) new ButtonWidget().setSize(0, 0)
            .setEnabled(false);
    }

    @Override
    public Materials getTankMaterial() {
        return mTankMaterial;
    }

    public void setTankMaterial(Materials mat) {
        this.mTankMaterial = mat;
    }

    public static Materials getRailcraftTankMaterial(TileEntity te) {
        return RailcraftPumpReservoir.getRailcraftTankMaterial(te);
    }

    /** GregTech's scanner takes finished lines, as its own are: they are formatted in the game's language here. */
    @Override
    public String[] getInfoData() {
        String tankMatName = mTankMaterial != null ? mTankMaterial.mDefaultLocalName : "Unknown";
        double maxFlowRateLPerSec = mCore.mEngine.getMaxFlowRateLitersPerSecond();
        String maxFlowStr = (maxFlowRateLPerSec == Double.MAX_VALUE)
            ? ClientText.get("gt.scanner.coolant_pump.max_pipe_flow.unlimited")
            : String.format("%.1f L/s", maxFlowRateLPerSec);
        String hatchState = isDischargeHatchDisabled()
            ? ClientText.get("gt.scanner.coolant_pump.discharge_hatch.disabled")
            : ClientText.get("gt.scanner.coolant_pump.discharge_hatch.enabled");
        Materials rMat = getRotorMaterial();
        String impellerStr;
        String thermalWearStr;
        if (rMat != null || hasRotor()) {
            String matName = rMat != null ? RotorThermalHelper.getMaterialLocalizedName(rMat) : "Unknown";
            double meltC = RotorThermalHelper.getMeltingPointCelsius(rMat);
            impellerStr = ClientText
                .format("gt.scanner.coolant_pump.impeller", matName, mRotorEfficiency * 100.0, meltC);
            double th = getHomologousTemperature();
            double thermalWear = getThermalWearMultiplier();
            String softeningActive = th >= 0.5 ? ClientText.get("gt.scanner.coolant_pump.impeller_thermal.softening")
                : "";
            thermalWearStr = ClientText
                .format("gt.scanner.coolant_pump.impeller_thermal_wear", thermalWear, th, softeningActive);
        } else {
            impellerStr = ClientText.get("gt.scanner.coolant_pump.impeller.none");
            thermalWearStr = ClientText.format("gt.scanner.coolant_pump.impeller_thermal_wear", 1.0, 0.0, "");
        }

        String loopStatusStr = mCore.mLoopFormed
            ? ClientText.format(
                "gt.scanner.coolant_pump.loop_active",
                mCore.mEngine.getSegments()
                    .size(),
                mCore.mLoopDevices.size())
            : ClientText.format("gt.scanner.coolant_pump.loop_incomplete", ClientText.of(mCore.mLoopStatus));

        return new String[] { ClientText.format("gt.scanner.coolant_pump.state", mCore.mLoopState.name()),
            ClientText.format("gt.scanner.coolant_pump.discharge_hatch", hatchState),
            ClientText.format("gt.scanner.coolant_pump.tank_material", tankMatName),
            ClientText.format("gt.scanner.coolant_pump.current_flow", mCore.mEngine.getFlowRateLitersPerSecond()),
            ClientText.format("gt.scanner.coolant_pump.max_pipe_flow", maxFlowStr),
            ClientText.format("gt.scanner.coolant_pump.peak_pressure", mCore.mEngine.getPeakLoopPressureBar()),
            ClientText.format("gt.scanner.coolant_pump.peak_temp", mCore.mEngine.getPeakLoopTempCelsius()),
            ClientText
                .format("gt.scanner.coolant_pump.loop_fluid", mCore.mCurrentFillLiters, mCore.mRequiredFillLiters),
            ClientText.format("gt.scanner.coolant_pump.dissolved_gas", getDissolvedGasFraction() * 100.0), impellerStr,
            thermalWearStr, loopStatusStr };
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        if (player != null) {
            super.getWailaNBTData(player, tile, tag, world, x, y, z);
        }
        tag.setBoolean("loopFormed", mCore.mLoopFormed);
        tag.setTag("loopStatus", LocalizedTextNbt.write(mCore.mLoopStatus));
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        final NBTTagCompound tag = accessor.getNBTData();
        if (tag.getBoolean("incompleteStructure")) {
            super.getWailaBody(itemStack, currentTip, accessor, config);
            return;
        }
        if (!tag.getBoolean("loopFormed")) {
            String status = ClientText.of(LocalizedTextNbt.read(tag.getCompoundTag("loopStatus")));
            if (status.isEmpty()) {
                status = ClientText.get("modularnuclear.coolant_loop.pump.status.waiting");
            }
            currentTip.add(
                EnumChatFormatting.RED
                    + ClientText.format("modularnuclear.coolant_loop.waila.loop_not_formed", status));
            return;
        }
        super.getWailaBody(itemStack, currentTip, accessor, config);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        mCore.saveNBTData(aNBT);
        aNBT.setDouble("mRotorDamageAccumulator", mRotorDamageAccumulator);
        if (mTankMaterial != null && mTankMaterial.mName != null) {
            aNBT.setString("mTankMaterial", mTankMaterial.mName);
        }
        if (mRotorMaterial != null && mRotorMaterial.mName != null) {
            aNBT.setString("mRotorMaterial", mRotorMaterial.mName);
        }

        if (mDischargeHatch != null && mDischargeHatch.getBaseMetaTileEntity() != null) {
            IGregTechTileEntity dte = mDischargeHatch.getBaseMetaTileEntity();
            aNBT.setIntArray("discCoord", new int[] { dte.getXCoord(), dte.getYCoord(), dte.getZCoord() });
        } else if (mSavedDischargeCoord != null) {
            aNBT.setIntArray("discCoord", mSavedDischargeCoord);
        }
        if (mSuctionHatch != null && mSuctionHatch.getBaseMetaTileEntity() != null) {
            IGregTechTileEntity ste = mSuctionHatch.getBaseMetaTileEntity();
            aNBT.setIntArray("sucCoord", new int[] { ste.getXCoord(), ste.getYCoord(), ste.getZCoord() });
        } else if (mSavedSuctionCoord != null) {
            aNBT.setIntArray("sucCoord", mSavedSuctionCoord);
        }

    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mCore.loadNBTData(aNBT);
        if (aNBT.hasKey("mRotorDamageAccumulator")) {
            mRotorDamageAccumulator = aNBT.getDouble("mRotorDamageAccumulator");
        }
        if (aNBT.hasKey("mTankMaterial")) {
            Materials mat = Materials.get(aNBT.getString("mTankMaterial"));
            if (mat != null) {
                mTankMaterial = mat;
            }
        }
        if (aNBT.hasKey("mRotorMaterial")) {
            Materials mat = Materials.get(aNBT.getString("mRotorMaterial"));
            if (mat != null) {
                mRotorMaterial = mat;
            }
        }
        if (aNBT.hasKey("discCoord")) {
            mSavedDischargeCoord = aNBT.getIntArray("discCoord");
        }
        if (aNBT.hasKey("sucCoord")) {
            mSavedSuctionCoord = aNBT.getIntArray("sucCoord");
        }

    }

    public boolean addBottomHatch(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;
        if (aMetaTileEntity instanceof MTEHatchPressurizedFluid) {
            MTEHatchPressurizedFluid ph = (MTEHatchPressurizedFluid) aMetaTileEntity;
            ph.updateTexture(aBaseCasingIndex);
            if (getBaseMetaTileEntity() != null) {
                ph.updateCraftingIcon(getMachineCraftingIcon());
            }
            if (ph.isOutput()) {
                if (mDischargeHatch == null) {
                    mDischargeHatch = ph;
                    return true;
                }
                return false;
            } else if (ph.isInput()) {
                if (mSuctionHatch == null) {
                    mSuctionHatch = ph;
                    return true;
                }
                return false;
            }
            return false;
        }
        if (aMetaTileEntity instanceof MTEHatchMaintenance) {
            return addMaintenanceToMachineList(aTileEntity, aBaseCasingIndex);
        }
        if (aMetaTileEntity instanceof MTEHatchEnergy) {
            if (!MTEPressurizedHeatExchanger.isSingleAmpEnergyHatch((MTEHatchEnergy) aMetaTileEntity)) {
                return false;
            }
            return addEnergyInputToMachineList(aTileEntity, aBaseCasingIndex);
        }
        if (aMetaTileEntity instanceof MTEHatchInputBus) {
            if (mInputBusses.size() >= 1) {
                return false;
            }
            if (aMetaTileEntity.getBaseMetaTileEntity() == null) {
                aMetaTileEntity.setBaseMetaTileEntity(aTileEntity);
            }
            return addInputBusToMachineList(aTileEntity, aBaseCasingIndex);
        }
        return false;
    }

    public static Materials getMaterialForTier(int tier) {
        return CoolantPumpStructure.getMaterialForTier(tier);
    }

    public static Materials getMaterialFromTrigger(ItemStack trigger) {
        return CoolantPumpStructure.getMaterialFromTrigger(trigger);
    }

    public static int getWidthFromTrigger(ItemStack trigger) {
        return CoolantPumpStructure.getWidthFromTrigger(trigger);
    }

    public static int getHeightFromTrigger(ItemStack trigger) {
        return CoolantPumpStructure.getHeightFromTrigger(trigger);
    }

    public static ItemStack getTankWallItem(Materials mat) {
        return CoolantPumpStructure.getTankWallItem(mat);
    }

    public static ItemStack getTankValveItem(Materials mat) {
        return CoolantPumpStructure.getTankValveItem(mat);
    }

    public static ItemStack getTankGaugeItem(Materials mat) {
        return CoolantPumpStructure.getTankGaugeItem(mat);
    }

    public static IStructureElement<MTECoolantPump> ofCornerFrame() {
        return CoolantPumpStructure.ofCornerFrame();
    }

    public static IStructureElement<MTECoolantPump> ofTankWall() {
        return CoolantPumpStructure.ofTankWall();
    }

    public static String[] makePumpBaseShape0(int width) {
        return CoolantPumpStructure.makePumpBaseShape0(width);
    }

    public static String[] makePumpBaseShape1(int width) {
        return CoolantPumpStructure.makePumpBaseShape1(width);
    }

    public static String[] makeTankBottomShape(int width) {
        return CoolantPumpStructure.makeTankBottomShape(width);
    }

    public static String[] makeTankMidShape(int width) {
        return CoolantPumpStructure.makeTankMidShape(width);
    }

    public static String[] makeTankTopShape(int width) {
        return CoolantPumpStructure.makeTankTopShape(width);
    }

    @Override
    public IStructureDefinition<MTECoolantPump> getStructureDefinition() {
        return CoolantPumpStructure.getStructureDefinition();
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        int width = getWidthFromTrigger(stackSize);
        int height = getHeightFromTrigger(stackSize);
        int hOffset = width / 2;
        int dOffset = 0;

        buildPiece("pump_" + width + "_base_0", stackSize, hintsOnly, hOffset, 0, dOffset);
        buildPiece("pump_" + width + "_base_1", stackSize, hintsOnly, hOffset, 1, dOffset);
        buildPiece("tank_" + width + "_bottom", stackSize, hintsOnly, hOffset, 2, dOffset);
        for (int i = 1; i < height - 1; i++) {
            buildPiece("tank_" + width + "_mid", stackSize, hintsOnly, hOffset, 2 + i, dOffset);
        }
        buildPiece("tank_" + width + "_top", stackSize, hintsOnly, hOffset, 2 + height - 1, dOffset);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int width = getWidthFromTrigger(stackSize);
        int height = getHeightFromTrigger(stackSize);
        int hOffset = width / 2;
        int dOffset = 0;

        int built;
        built = survivalBuildPiece(
            "pump_" + width + "_base_0",
            stackSize,
            hOffset,
            0,
            dOffset,
            elementBudget,
            env,
            false,
            true);
        if (built >= 0) return built;
        built = survivalBuildPiece(
            "pump_" + width + "_base_1",
            stackSize,
            hOffset,
            1,
            dOffset,
            elementBudget,
            env,
            false,
            true);
        if (built >= 0) return built;
        built = survivalBuildPiece(
            "tank_" + width + "_bottom",
            stackSize,
            hOffset,
            2,
            dOffset,
            elementBudget,
            env,
            false,
            true);
        if (built >= 0) return built;
        for (int i = 1; i < height - 1; i++) {
            built = survivalBuildPiece(
                "tank_" + width + "_mid",
                stackSize,
                hOffset,
                2 + i,
                dOffset,
                elementBudget,
                env,
                false,
                true);
            if (built >= 0) return built;
        }
        return survivalBuildPiece(
            "tank_" + width + "_top",
            stackSize,
            hOffset,
            2 + height - 1,
            dOffset,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gt.multiblock.coolant_pump.machine_type"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.coolant_pump.desc1"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.coolant_pump.desc2"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.coolant_pump.desc3"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.coolant_pump.desc4"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.coolant_pump.desc5"))
            .addInfo(StatCollector.translateToLocal("gt.multiblock.coolant_pump.desc6"))
            .addPerfectOCInfo()
            .addSeparator()
            .beginVariableStructureBlock(3, 9, 6, 10, 3, 9, false)
            .addController(StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.controller"))
            .addCasingInfo(
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.casings"),
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.casings_pos"),
                false)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.frames"),
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.frames_pos"))
            .addEnergyHatch(StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.energy"), 1)
            .addMaintenanceHatch(StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.maintenance"), 1)
            .addInputBus(StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.input_bus"), 1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.hatch_discharge"),
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.hatch_discharge_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.hatch_suction"),
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.hatch_suction_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.rc_tank"),
                StatCollector.translateToLocal("gt.multiblock.coolant_pump.structure.rc_tank_pos"))
            .addSubChannel(CoolantStructureChannels.STRUCTURE_WIDTH)
            .addSubChannel(CoolantStructureChannels.STRUCTURE_HEIGHT)
            .addSubChannel(CoolantStructureChannels.TANK_MATERIAL)
            .toolTipFinisher(StatCollector.translateToLocal("gt.multiblock.modularnuclear.coolant_loop.finisher"));
        return tt;
    }

    /** Our GUIs show no GregTech logo. */
    @Override
    public boolean supportsLogo() {
        return false;
    }
}
