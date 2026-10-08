package com.gtnewhorizons.modularnuclear.common.opencomputers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControl;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.nuclear.INuclearTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

import gregtech.api.items.ItemRadioactiveCell;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.ManagedEnvironment;

/**
 * OpenComputers component environment for {@link MTEHatchNuclearControl}.
 * <p>
 * ARCHITECTURAL DESIGN INVARIANT:
 * Reactor OpenComputers APIs are STRICTLY READ-ONLY telemetry interfaces.
 * They deliberately expose no methods to modify, configure, or actuate any aspect of
 * the reactor state (such as control rods, SCRAM, or setpoints) directly.
 * <p>
 * If players wish to automate control rod actuation or emergency trips via OpenComputers,
 * they must interface via OpenComputers Redstone components (e.g., Redstone I/O, bundled cable
 * cards) driving the physical redstone/ProjectRed inputs on individual hatches.
 */
public class NuclearControlEnvironment extends ManagedEnvironment implements NamedBlock {

    private final MTEHatchNuclearControl hatch;

    public NuclearControlEnvironment(MTEHatchNuclearControl hatch) {
        this.hatch = hatch;
        setNode(
            Network.newNode(this, Visibility.Network)
                .withComponent("nuclear_control_hatch")
                .create());
    }

    @Override
    public String preferredName() {
        return "nuclear_control_hatch";
    }

    @Override
    public int priority() {
        return 10;
    }

    /**
     * API 1: General reactor-level telemetry.
     * Exposes all general operating parameters, Nuclear Control 2 metrics, and multiblock status.
     */
    @Callback(
        doc = "function():table -- Returns overall reactor status, operating parameters, and telemetry metrics.",
        direct = true)
    public Object[] getReactorTelemetry(Context context, Arguments args) {
        Map<String, Object> data = new LinkedHashMap<>();

        if (hatch == null || hatch.mReactor == null) {
            data.put("isFormed", false);
            data.put("isActive", false);
            data.put("error", "Hatch not linked to a reactor controller");
            return new Object[] { data };
        }

        MTENuclearReactor reactor = hatch.mReactor;
        boolean formed = reactor.mMachine;
        boolean active = reactor.getBaseMetaTileEntity() != null && reactor.getBaseMetaTileEntity()
            .isActive();

        data.put("isFormed", formed);
        data.put("isActive", active);
        data.put("isOnline", active);

        if (!formed) {
            data.put("error", "Reactor multiblock is not formed");
            return new Object[] { data };
        }

        // Trigger telemetry calculation
        reactor.calculateTelemetry();

        data.put("gridSize", reactor.gridSize);
        data.put("coreDimension", reactor.coreDimension);
        data.put("casingTier", reactor.mPipeTier);
        data.put("casingName", NuclearSimulationEngine.getPipeTierName(reactor.mPipeTier));
        data.put("casingVoltage", NuclearSimulationEngine.getPipeTierVoltage(reactor.mPipeTier));
        data.put("maxSafeTemp", NuclearSimulationEngine.getMaxOperatingTemperature(reactor.mPipeTier));
        data.put("coreTemp", reactor.mCoreTemp);
        data.put("avgTemp", reactor.mAvgTemp);
        data.put("reactivityEfficiency", reactor.mReactivity);
        data.put("directPowerEUt", reactor.mDirectPowerEUt);
        data.put("neutronsProduced", reactor.mNeutronsProduced);
        data.put("fastNeutronsAbsorbed", reactor.mFastAbsorbed);
        data.put("thermalNeutronsAbsorbed", reactor.mThermalAbsorbed);
        data.put("escapedNeutrons", reactor.mEscapedNeutrons);
        data.put("outputCoolantRate", reactor.mOutputCoolantRate);
        data.put("outputCoolantName", reactor.mOutputCoolantName != null ? reactor.mOutputCoolantName : "");
        data.put("scram", reactor.mScram);
        data.put("ambientTemp", reactor.getAmbientTemperature());

        // Min / Avg / Max operating metrics
        Map<String, Object> tempMap = new LinkedHashMap<>();
        tempMap.put("min", reactor.mMinTileTemp);
        tempMap.put("avg", reactor.mAvgTileTemp);
        tempMap.put("max", reactor.mMaxTileTemp);
        data.put("temperature", tempMap);

        Map<String, Object> coolDurMap = new LinkedHashMap<>();
        coolDurMap.put("min", reactor.mMinCoolantItemDur);
        coolDurMap.put("avg", reactor.mAvgCoolantItemDur);
        coolDurMap.put("max", reactor.mMaxCoolantItemDur);
        data.put("coolantItemDurability", coolDurMap);

        Map<String, Object> fuelDurMap = new LinkedHashMap<>();
        fuelDurMap.put("min", reactor.mMinFuelItemDur);
        fuelDurMap.put("avg", reactor.mAvgFuelItemDur);
        fuelDurMap.put("max", reactor.mMaxFuelItemDur);
        data.put("fuelItemDurability", fuelDurMap);

        Map<String, Object> coolFillMap = new LinkedHashMap<>();
        coolFillMap.put("min", reactor.mMinCoolantHatchFill);
        coolFillMap.put("avg", reactor.mAvgCoolantHatchFill);
        coolFillMap.put("max", reactor.mMaxCoolantHatchFill);
        data.put("coolantHatchFill", coolFillMap);

        Map<String, Object> fuelFillMap = new LinkedHashMap<>();
        fuelFillMap.put("min", reactor.mMinFuelHatchFill);
        fuelFillMap.put("avg", reactor.mAvgFuelHatchFill);
        fuelFillMap.put("max", reactor.mMaxFuelHatchFill);
        data.put("fuelHatchFill", fuelFillMap);

        // Item & Fluid Totals
        Map<String, Object> itemCounts = new LinkedHashMap<>();
        itemCounts.put("fuel", reactor.mTotalFuelItems);
        itemCounts.put("coolant", reactor.mTotalCoolantItems);
        data.put("itemCounts", itemCounts);

        Map<String, Object> fluidVolumes = new LinkedHashMap<>();
        fluidVolumes.put("coolantAmount", reactor.mTotalCoolantFluid);
        fluidVolumes.put("coolantCapacity", reactor.mTotalCoolantCapacity);
        fluidVolumes.put("fuelAmount", reactor.mTotalFuelFluid);
        fluidVolumes.put("fuelCapacity", reactor.mTotalFuelCapacity);
        data.put("fluidVolumes", fluidVolumes);

        Map<String, Object> hatchCounts = new LinkedHashMap<>();
        hatchCounts.put("coolant", reactor.mCoolantHatchCount);
        hatchCounts.put("fuel", reactor.mFuelHatchCount);
        hatchCounts.put("controlRod", reactor.mBottomControlRodHatches.size());
        data.put("hatchCounts", hatchCounts);

        // Last Cycle Statistics
        Map<String, Object> lastCycle = new LinkedHashMap<>();
        lastCycle.put("zeroedCoolantItems", reactor.mZeroedCoolantItemsLastCycle);
        lastCycle.put("zeroedFuelItems", reactor.mZeroedFuelItemsLastCycle);
        lastCycle.put("consumedCoolant", reactor.mConsumedCoolantLastCycle);
        lastCycle.put("producedHotCoolant", reactor.mProducedHotCoolantLastCycle);
        lastCycle.put("transmutationLoss", reactor.mTransmutationLossLastCycle);
        lastCycle.put("transmutationByproducts", reactor.mTransmutationByproductsLastCycle);
        lastCycle.put("depletedLiquidFuel", reactor.mDepletedLiquidFuelLastCycle);
        data.put("lastCycle", lastCycle);

        return new Object[] { data };
    }

    /**
     * Alias for getReactorTelemetry
     */
    @Callback(doc = "function():table -- Alias for getReactorTelemetry.", direct = true)
    public Object[] getTelemetry(Context context, Arguments args) {
        return getReactorTelemetry(context, args);
    }

    /**
     * API 2: Full grid data of reactor cells.
     * Exposes per-cell coordinates, temperature, flux, absorption, direct EU, and contained items/fluids.
     */
    @Callback(
        doc = "function():table -- Returns full detailed per-cell status and contents across the entire reactor grid.",
        direct = true)
    public Object[] getGridData(Context context, Arguments args) {
        if (hatch == null || hatch.mReactor == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Hatch not linked to a reactor controller");
            return new Object[] { err };
        }

        MTENuclearReactor reactor = hatch.mReactor;
        if (!reactor.mMachine || reactor.mGrid == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Reactor multiblock is not formed");
            return new Object[] { err };
        }

        int size = reactor.gridSize;
        List<Map<String, Object>> cellList = new ArrayList<>(size * size);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                Map<String, Object> cellMap = new LinkedHashMap<>();
                // Provide both 1-based (Lua idiomatic) and 0-based coordinates
                cellMap.put("x", x + 1);
                cellMap.put("y", y + 1);
                cellMap.put("gx", x);
                cellMap.put("gy", y);

                INuclearTile rawTile = reactor.mGrid[x][y];
                if (!(rawTile instanceof MTENuclearReactor.NuclearGridTile gt)) {
                    cellMap.put("exists", false);
                    cellMap.put("type", "EMPTY");
                    cellList.add(cellMap);
                    continue;
                }

                cellMap.put("exists", true);
                cellMap.put("temperature", gt.getTemperature());

                if (gt.isBus()) {
                    cellMap.put("type", "BUS");
                    cellMap.put("isFluid", false);
                    MTEHatchNuclearBus bus = gt.getBus();
                    cellMap.put("fastFlux", bus.mLastFastFlux);
                    cellMap.put("thermalFlux", bus.mLastThermalFlux);
                    cellMap.put("fastAbsorbed", bus.mLastFastAbsorbed);
                    cellMap.put("thermalAbsorbed", bus.mLastThermalAbsorbed);
                    cellMap.put("directEU", bus.mDirectEUProduced);

                    ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
                    if (stack != null) {
                        Map<String, Object> itemMap = serializeItemStack(stack);
                        // Add fuel-specific metadata if fuel
                        NuclearFuelType fuel = reactor.getTileFuelType(gt);
                        if (fuel != null) {
                            itemMap.put("isFuel", true);
                            itemMap.put("fuelType", fuel.displayName);
                            itemMap.put("fuelTier", fuel.ordinal() + 1);
                            itemMap.put("baseNeutrons", fuel.baseNeutrons);
                            itemMap.put("defaultDurability", fuel.defaultDurability);
                        } else {
                            itemMap.put("isFuel", false);
                        }
                        cellMap.put("item", itemMap);
                    } else {
                        cellMap.put("item", null);
                    }

                } else if (gt.isHatch()) {
                    cellMap.put("type", "HATCH");
                    cellMap.put("isFluid", true);
                    MTEHatchNuclearHatch nhatch = gt.getHatch();
                    cellMap.put("fastFlux", nhatch.mLastFastFlux);
                    cellMap.put("thermalFlux", nhatch.mLastThermalFlux);
                    cellMap.put("fastAbsorbed", nhatch.mLastFastAbsorbed);
                    cellMap.put("thermalAbsorbed", nhatch.mLastThermalAbsorbed);
                    cellMap.put("directEU", 0L);

                    FluidStack fluid = nhatch.mInputFluid;
                    if (fluid != null && fluid.getFluid() != null) {
                        Map<String, Object> fluidMap = new LinkedHashMap<>();
                        fluidMap.put(
                            "name",
                            fluid.getFluid()
                                .getName());
                        fluidMap.put(
                            "localizedName",
                            fluid.getFluid()
                                .getLocalizedName(fluid));
                        fluidMap.put("amount", fluid.amount);
                        fluidMap.put("capacity", nhatch.getCapacity());
                        fluidMap.put(
                            "temperature",
                            fluid.getFluid()
                                .getTemperature(fluid));
                        cellMap.put("fluid", fluidMap);
                    } else {
                        cellMap.put("fluid", null);
                    }

                }

                if (gt.hasControlRod()) {
                    MTEHatchNuclearControlRod rod = gt.getBottomControlRod();
                    Map<String, Object> rodMap = new LinkedHashMap<>();
                    rodMap.put("hasRod", true);
                    rodMap.put("insertion", rod.getInsertionPercent());
                    ItemStack rodStack = rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD];
                    if (rodStack != null) {
                        Map<String, Object> itemMap = serializeItemStack(rodStack);
                        itemMap.put("rodType", MTEHatchNuclearControlRod.getRodType(rodStack).displayName);
                        itemMap.put("rodDamage", rodStack.getItemDamage());
                        itemMap.put("rodMaxDamage", rodStack.getMaxDamage());
                        rodMap.put("item", itemMap);
                    } else {
                        rodMap.put("item", null);
                    }
                    cellMap.put("controlRod", rodMap);
                } else {
                    cellMap.put("controlRod", null);
                }

                cellList.add(cellMap);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gridSize", size);
        result.put("cells", cellList);
        return new Object[] { result };
    }

    /**
     * Alias for getGridData
     */
    @Callback(doc = "function():table -- Alias for getGridData.", direct = true)
    public Object[] getReactorGrid(Context context, Arguments args) {
        return getGridData(context, args);
    }

    private static Map<String, Object> serializeItemStack(ItemStack stack) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", stack.getUnlocalizedName());
        map.put("displayName", stack.getDisplayName());
        map.put("size", stack.stackSize);
        map.put("damage", stack.getItemDamage());
        map.put("maxDamage", stack.getMaxDamage());

        if (stack.getItem() instanceof ItemRadioactiveCell radCell) {
            int max = radCell.getMaxDamageEx();
            int cur = radCell.getDamageOfStack(stack);
            map.put("durability", max - cur);
            map.put("maxDurability", max);
        } else if (stack.isItemStackDamageable() && stack.getMaxDamage() > 0) {
            map.put("durability", stack.getMaxDamage() - stack.getItemDamage());
            map.put("maxDurability", stack.getMaxDamage());
        }
        return map;
    }
}
