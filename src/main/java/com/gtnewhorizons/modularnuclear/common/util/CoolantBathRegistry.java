package com.gtnewhorizons.modularnuclear.common.util;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.data.CoolantDeviceData;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ConvectiveHeatTransferModel;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.util.CellHeat;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.util.CoolantBathSlot;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.objects.ItemData;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

/**
 * Registry and physics helper for Coolant Bath compatible items.
 * Discovers and caches:
 * 1. Hot Ingot <-> Regular Ingot pairings from Vacuum Freezer recipes and Materials blast furnace temperatures.
 * 2. Coolant Cell heat capacities, damage scales, and temperature mappings.
 */
public class CoolantBathRegistry {

    private static final Map<ItemDamageKey, HotColdPair> HOT_TO_COLD_INGOTS = new HashMap<>();
    private static final Map<ItemDamageKey, HotColdPair> COLD_TO_HOT_INGOTS = new HashMap<>();
    private static boolean initialized = false;

    public static class HotColdPair {

        public final ItemStack hotStack;
        public final ItemStack coldStack;
        public final double blastFurnaceTempCelsius;
        public final Materials material;

        public HotColdPair(ItemStack hotStack, ItemStack coldStack, double blastFurnaceTempCelsius,
            Materials material) {
            this.hotStack = hotStack;
            this.coldStack = coldStack;
            this.blastFurnaceTempCelsius = blastFurnaceTempCelsius;
            this.material = material;
        }
    }

    public static class CryoCellPair {

        public final String name;
        public final ItemStack liquidCell;
        public final ItemStack gasCell;
        public final double boilingPointCelsius;
        /** Its latent heat of vaporization per liter, if the PHE has no entry for it. */
        public final double latentHeatJoules;

        public CryoCellPair(String name, ItemStack liquidCell, ItemStack gasCell, double boilingPointCelsius,
            double latentHeatJoules) {
            this.name = name;
            this.liquidCell = liquidCell;
            this.gasCell = gasCell;
            this.boilingPointCelsius = boilingPointCelsius;
            this.latentHeatJoules = latentHeatJoules;
        }
    }

    private static final Map<ItemDamageKey, CryoCellPair> CRYO_CELLS = new HashMap<>();

    public static synchronized void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        // 1. Scan Vacuum Freezer recipes for hot ingot -> cold ingot conversions
        try {
            if (RecipeMaps.vacuumFreezerRecipes != null) {
                for (GTRecipe recipe : RecipeMaps.vacuumFreezerRecipes.getAllRecipes()) {
                    if (recipe.mInputs != null && recipe.mInputs.length > 0
                        && recipe.mOutputs != null
                        && recipe.mOutputs.length > 0) {
                        ItemStack in = recipe.mInputs[0];
                        ItemStack out = recipe.mOutputs[0];
                        if (in == null || out == null) continue;

                        ItemData inData = GTOreDictUnificator.getAssociation(in);
                        ItemData outData = GTOreDictUnificator.getAssociation(out);
                        if (inData != null && inData.mPrefix == OrePrefixes.ingotHot
                            && outData != null
                            && outData.mPrefix == OrePrefixes.ingot) {
                            Materials mat = inData.mMaterial != null ? inData.mMaterial.mMaterial : null;
                            double bfTemp = getMaterialBlastFurnaceTemp(mat);
                            HotColdPair pair = new HotColdPair(in.copy(), out.copy(), bfTemp, mat);
                            ItemDamageKey inKey = ItemDamageKey.of(in);
                            ItemDamageKey outKey = ItemDamageKey.of(out);
                            if (inKey != null) HOT_TO_COLD_INGOTS.put(inKey, pair);
                            if (outKey != null) COLD_TO_HOT_INGOTS.put(outKey, pair);
                        }
                    }
                }
            }
        } catch (Throwable swallowed) {
            Swallowed.note(swallowed);
        }

        // 2. Scan all GregTech materials for ingotHot <-> ingot pairs
        try {
            for (Materials mat : Materials.values()) {
                if (mat == null) continue;
                ItemStack hot = GTOreDictUnificator.get(OrePrefixes.ingotHot, mat, 1L);
                ItemStack cold = GTOreDictUnificator.get(OrePrefixes.ingot, mat, 1L);
                if (hot != null && cold != null) {
                    double bfTemp = getMaterialBlastFurnaceTemp(mat);
                    HotColdPair pair = new HotColdPair(hot, cold, bfTemp, mat);
                    ItemDamageKey hotKey = ItemDamageKey.of(hot);
                    ItemDamageKey coldKey = ItemDamageKey.of(cold);
                    if (hotKey != null && !HOT_TO_COLD_INGOTS.containsKey(hotKey)) {
                        HOT_TO_COLD_INGOTS.put(hotKey, pair);
                    }
                    if (coldKey != null && !COLD_TO_HOT_INGOTS.containsKey(coldKey)) {
                        COLD_TO_HOT_INGOTS.put(coldKey, pair);
                    }
                }
            }
        } catch (Throwable swallowed) {
            Swallowed.note(swallowed);
        }

        // 3. Register cryogenic cells
        try {
            registerCryoCellPair(
                "liquidnitrogen",
                GTOreDictUnificator.get(OrePrefixes.cell, Materials.LiquidNitrogen, 1L),
                GTOreDictUnificator.get(OrePrefixes.cell, Materials.Nitrogen, 1L),
                -196.0,
                160000.0);
            registerCryoCellPair(
                "liquidoxygen",
                GTOreDictUnificator.get(OrePrefixes.cell, Materials.LiquidOxygen, 1L),
                GTOreDictUnificator.get(OrePrefixes.cell, Materials.Oxygen, 1L),
                -183.0,
                243000.0);
            ItemStack airCell = null;
            try {
                airCell = gregtech.api.util.GTModHandler.getIC2Item("airCell", 1L);
            } catch (Throwable swallowed) {
                Swallowed.note(swallowed);
            }
            if (airCell == null) {
                airCell = GTOreDictUnificator.get(OrePrefixes.cell, Materials.Air, 1L);
            }
            registerCryoCellPair(
                "liquidair",
                GTOreDictUnificator.get(OrePrefixes.cell, Materials.LiquidAir, 1L),
                airCell,
                -194.0,
                174000.0);
            ItemStack heliumLiquidCell = null;
            try {
                heliumLiquidCell = bartworks.system.material.WerkstoffLoader.LiquidHelium.get(OrePrefixes.cell);
            } catch (Throwable swallowed) {
                Swallowed.note(swallowed);
            }
            if (heliumLiquidCell == null) {
                heliumLiquidCell = GTOreDictUnificator.get(OrePrefixes.cell, Materials.Helium, 1L);
            }
            registerCryoCellPair(
                "liquidhelium",
                heliumLiquidCell,
                GTOreDictUnificator.get(OrePrefixes.cell, Materials.Helium, 1L),
                -269.0,
                20700.0);
        } catch (Throwable swallowed) {
            Swallowed.note(swallowed);
        }
    }

    public static void registerCryoCellPair(String name, ItemStack liquidCell, ItemStack gasCell,
        double boilingPointCelsius, double latentHeatJoules) {
        if (liquidCell == null || gasCell == null) return;
        CryoCellPair pair = new CryoCellPair(
            name,
            liquidCell.copy(),
            gasCell.copy(),
            boilingPointCelsius,
            latentHeatJoules);
        ItemDamageKey key = ItemDamageKey.of(liquidCell);
        if (key != null) {
            CRYO_CELLS.put(key, pair);
        }
    }

    public static CryoCellPair getCryoCellPair(ItemStack stack) {
        if (stack == null) return null;
        ensureInitialized();
        ItemDamageKey key = ItemDamageKey.of(stack);
        if (key != null) {
            CryoCellPair pair = CRYO_CELLS.get(key);
            if (pair != null) return pair;
        }
        // Fallback by checking item data associations or unlocalized names
        ItemData data = GTOreDictUnificator.getAssociation(stack);
        if (data != null && data.mPrefix == OrePrefixes.cell
            && data.mMaterial != null
            && data.mMaterial.mMaterial != null) {
            String matName = data.mMaterial.mMaterial.mName.toLowerCase();
            if (matName.contains("liquidnitrogen")) {
                return new CryoCellPair(
                    "liquidnitrogen",
                    stack,
                    GTOreDictUnificator.get(OrePrefixes.cell, Materials.Nitrogen, 1L),
                    -196.0,
                    160000.0);
            }
            if (matName.contains("liquidoxygen")) {
                return new CryoCellPair(
                    "liquidoxygen",
                    stack,
                    GTOreDictUnificator.get(OrePrefixes.cell, Materials.Oxygen, 1L),
                    -183.0,
                    243000.0);
            }
            if (matName.contains("liquidair")) {
                ItemStack airOut = null;
                try {
                    airOut = gregtech.api.util.GTModHandler.getIC2Item("airCell", 1L);
                } catch (Throwable swallowed) {
                    Swallowed.note(swallowed);
                }
                if (airOut == null) airOut = GTOreDictUnificator.get(OrePrefixes.cell, Materials.Air, 1L);
                return new CryoCellPair("liquidair", stack, airOut, -194.0, 174000.0);
            }
            if (matName.contains("liquidhelium")) {
                return new CryoCellPair(
                    "liquidhelium",
                    stack,
                    GTOreDictUnificator.get(OrePrefixes.cell, Materials.Helium, 1L),
                    -269.0,
                    20700.0);
            }
        }
        String unloc = stack.getUnlocalizedName();
        if (unloc != null) {
            String lower = unloc.toLowerCase();
            if (lower.contains("liquidnitrogen") || lower.contains("liquid_nitrogen")) {
                return new CryoCellPair(
                    "liquidnitrogen",
                    stack,
                    GTOreDictUnificator.get(OrePrefixes.cell, Materials.Nitrogen, 1L),
                    -196.0,
                    160000.0);
            }
            if (lower.contains("liquidoxygen") || lower.contains("liquid_oxygen")) {
                return new CryoCellPair(
                    "liquidoxygen",
                    stack,
                    GTOreDictUnificator.get(OrePrefixes.cell, Materials.Oxygen, 1L),
                    -183.0,
                    243000.0);
            }
            if (lower.contains("liquidair") || lower.contains("liquid_air")) {
                ItemStack airOut = null;
                try {
                    airOut = gregtech.api.util.GTModHandler.getIC2Item("airCell", 1L);
                } catch (Throwable swallowed) {
                    Swallowed.note(swallowed);
                }
                if (airOut == null) airOut = GTOreDictUnificator.get(OrePrefixes.cell, Materials.Air, 1L);
                return new CryoCellPair("liquidair", stack, airOut, -194.0, 174000.0);
            }
            if (lower.contains("liquidhelium") || lower.contains("liquid_helium")) {
                return new CryoCellPair(
                    "liquidhelium",
                    stack,
                    GTOreDictUnificator.get(OrePrefixes.cell, Materials.Helium, 1L),
                    -269.0,
                    20700.0);
            }
        }
        return null;
    }

    public static boolean isCryoCell(ItemStack stack) {
        return getCryoCellPair(stack) != null;
    }

    public static double getMaterialBlastFurnaceTemp(Materials mat) {
        if (mat == null) return 1200.0;
        if (mat.mBlastFurnaceTemp > 0) {
            // GT blast furnace temp is stored in Kelvin, convert to Celsius
            double celsius = mat.mBlastFurnaceTemp - 273.15;
            return Math.max(200.0, celsius);
        }
        return 1200.0; // Default fallback for high-temperature metals
    }

    /** isCompatibleItem's answers per item kind: the bath asks for every input bus slot every tick. */
    private static final LongKeyCache<Boolean> COMPATIBLE = new LongKeyCache<>();

    /** Whether an item stack can be processed in the Coolant Bath. */
    public static boolean isCompatibleItem(ItemStack stack) {
        if (stack == null) return false;
        return COMPATIBLE.get(ReactorDataRegistry.getItemKey(stack), stack, (s, k) -> scanIsCompatibleItem(s));
    }

    private static boolean scanIsCompatibleItem(ItemStack stack) {
        ensureInitialized();

        if (isCryoCell(stack)) {
            return true;
        }

        if (isCoolantCell(stack)) {
            return true;
        }

        ItemDamageKey key = ItemDamageKey.of(stack);
        ItemData data = GTOreDictUnificator.getAssociation(stack);
        if (data != null) {
            if (data.mPrefix == OrePrefixes.ingotHot) {
                return true;
            }
            if (data.mPrefix == OrePrefixes.ingot) {
                return (key != null && COLD_TO_HOT_INGOTS.containsKey(key))
                    || GTOreDictUnificator.get(OrePrefixes.ingotHot, data.mMaterial.mMaterial, 1L) != null;
            }
        }

        return key != null && (HOT_TO_COLD_INGOTS.containsKey(key) || COLD_TO_HOT_INGOTS.containsKey(key));
    }

    /**
     * Checks if the stack is an IC2 or GregTech coolant cell.
     */
    public static boolean isCoolantCell(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;

        String className = stack.getItem()
            .getClass()
            .getName();
        if (className.contains("CoolantCell") || className.contains("ItemReactorHeatStorage")
            || className.contains("ItemReactorCondensator")) {
            return true;
        }

        String unloc = stack.getUnlocalizedName();
        if (unloc != null) {
            String lower = unloc.toLowerCase();
            if (lower.contains("coolantcell") || lower.contains("reactorcoolant")
                || lower.contains("heatcapacitor")
                || lower.contains("heatstorage")) {
                return true;
            }
        }

        return false;
    }

    /** The temperature a hot ingot has cooled into its ingot at (devices.yaml bath.ingotCooledTempC). */
    public static double ingotCooledTemperatureCelsius() {
        CoolantDeviceData devData = CoolantLoopDataRegistry.getDeviceData();
        return devData != null && devData.getBath() != null ? devData.getBath().ingotCooledTempC
            : CoolantDeviceData.CoolantBathData.TOUCH_SAFE_TEMP_C;
    }

    /** The fluid a cell holds, as GregTech and the fluid container registry know it. */
    private static FluidStack heldFluid(ItemStack cell) {
        try {
            FluidStack fluid = GTUtility.getFluidForFilledItem(cell, true);
            if (fluid != null) return fluid;
        } catch (Throwable swallowed) {
            Swallowed.note(swallowed);
        }
        return FluidContainerRegistry.getFluidForFilledItem(cell);
    }

    /** The share of a freezer's electricity for a change of state an item holds (bath.recoverableEnergyFraction). */
    public static double recoverableEnergyFraction() {
        CoolantDeviceData devData = CoolantLoopDataRegistry.getDeviceData();
        return devData != null && devData.getBath() != null
            ? Math.max(0.0, Math.min(1.0, devData.getBath().recoverableEnergyFraction))
            : 0.2;
    }

    /** The heat one EU is worth (PHEData.joulesPerEU). */
    private static double joulesPerEU() {
        CoolantDeviceData devData = CoolantLoopDataRegistry.getDeviceData();
        return devData != null && devData.getPhe() != null ? devData.getPhe()
            .joulesPerEU() : new CoolantDeviceData.PHEData().joulesPerEU();
    }

    /** bath.itemOverrides by item, resolved once per configuration. */
    private static Map<ItemDamageKey, CoolantDeviceData.CoolantBathData.ItemOverride> overrides;
    private static CoolantDeviceData.CoolantBathData overridesOf;

    private static CoolantDeviceData.CoolantBathData.ItemOverride overrideFor(ItemStack stack) {
        CoolantDeviceData devData = CoolantLoopDataRegistry.getDeviceData();
        CoolantDeviceData.CoolantBathData bath = devData != null ? devData.getBath() : null;
        if (bath == null || stack == null) return null;
        if (overrides == null || overridesOf != bath) {
            Map<ItemDamageKey, CoolantDeviceData.CoolantBathData.ItemOverride> resolved = new HashMap<>();
            for (CoolantDeviceData.CoolantBathData.ItemOverride o : bath.itemOverrides) {
                if (o == null || o.item == null) continue;
                String[] parts = o.item.trim()
                    .split(":");
                if (parts.length < 2) continue;
                try {
                    Item item = GameRegistry.findItem(parts[0], parts[1]);
                    int meta = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                    if (item != null) resolved.put(ItemDamageKey.of(item, meta), o);
                } catch (Throwable swallowed) {
                    Swallowed.note(swallowed);
                }
            }
            overrides = resolved;
            overridesOf = bath;
        }
        return overrides.isEmpty() ? null : overrides.get(ItemDamageKey.of(stack));
    }

    /**
     * The heat an ingot holds between its hot ingot's temperature and the temperature it counts as cooled at, J: what
     * a vacuum freezer's electricity for cooling it is worth as heat, times the bath's recoverable share, unless the
     * configuration gives the item its own. A material the freezer has no recipe for is counted as the freezer's rule
     * for hot ingots would have it: three ticks per unit of material mass at MV.
     */
    static double ingotHeat(ItemStack inBath, ItemStack hotStack, ItemStack coldStack, Materials mat) {
        CoolantDeviceData.CoolantBathData.ItemOverride o = overrideFor(inBath);
        if (o != null && o.heatJoules > 0.0) return o.heatJoules;
        double eu = FreezerRecipeEnergy.euToFreeze(hotStack);
        if (eu <= 0.0) eu = FreezerRecipeEnergy.euToMake(coldStack);
        if (eu <= 0.0) eu = unitMass(mat) * 3.0 * TierEU.RECIPE_MV;
        return eu * joulesPerEU() * recoverableEnergyFraction();
    }

    /** The seconds the freezer takes over an ingot (see exchangeArea), unless the configuration gives its own. */
    static double ingotSeconds(ItemStack inBath, ItemStack hotStack, ItemStack coldStack, Materials mat) {
        CoolantDeviceData.CoolantBathData.ItemOverride o = overrideFor(inBath);
        if (o != null && o.seconds > 0.0) return o.seconds;
        double seconds = FreezerRecipeEnergy.secondsToFreeze(hotStack);
        if (seconds <= 0.0) seconds = FreezerRecipeEnergy.secondsToMake(coldStack);
        if (seconds <= 0.0) seconds = unitMass(mat) * 3.0 / 20.0;
        return seconds;
    }

    private static double unitMass(Materials mat) {
        return mat != null ? Math.max(1.0, mat.getMass()) : 1.0;
    }

    /**
     * The exchange area that gives an item the vacuum freezer's pace: with it, in a bath at full flow (the convective
     * model's greatest coefficient), the item settles toward the coolant's temperature with the freezer recipe's
     * duration as its time constant (thermal mass / (coefficient x area)). Less flow is slower in proportion.
     */
    public static double exchangeArea(double heatCapacity, double seconds) {
        ConvectiveHeatTransferModel conv = CoolantLoopDataRegistry.getConvectiveModel();
        if (conv == null) conv = ConvectiveHeatTransferModel.DEFAULT;
        return heatCapacity / (Math.max(1.0, conv.getHMax()) * Math.max(0.05, seconds));
    }

    /** The temperature a cryogenic cell's pace is set for: it boils off in the freezer's time in coolant this warm. */
    private static final double AMBIENT_CELSIUS = 20.0;

    /** Creates a new CoolantBathSlot for an accepted item stack based on current loop coolant temperature. */
    public static CoolantBathSlot createSlot(ItemStack inputStack, double currentCoolantTemp) {
        if (inputStack == null) return null;
        ensureInitialized();

        ItemStack stack = inputStack.copy();
        stack.stackSize = 1;

        CryoCellPair cryo = getCryoCellPair(stack);
        if (cryo != null) {
            // It boils off at its boiling point, taking up what a liter of it takes up boiling in a PHE (the
            // electricity a vacuum freezer spends making that liter, as heat), for each liter the cell holds
            CoolantDeviceData devData = CoolantLoopDataRegistry.getDeviceData();
            CoolantDeviceData.PHEData phe = devData != null ? devData.getPhe() : null;
            FluidStack held = heldFluid(stack);
            String fluidName = held != null && held.getFluid() != null ? held.getFluid()
                .getName() : cryo.name;
            CoolantDeviceData.PHEData.SecondaryCoolantEntry boil = phe != null ? phe.findSecondaryCoolant(fluidName)
                : null;
            if (boil == null && phe != null) boil = phe.findSecondaryCoolant(cryo.name);
            FreezerRecipeEnergy.applyTo(boil, fluidName, phe);
            double boilingPoint = boil != null ? boil.boilingPointCelsius : cryo.boilingPointCelsius;
            double latentPerLiter = boil != null ? boil.latentHeat() : cryo.latentHeatJoules;
            double cellTemp = boilingPoint;
            double targetTemp = boilingPoint;
            int maxDmg = 1000;
            int initialDmg = 0;
            double liters = held != null && held.amount > 0 ? held.amount : FluidContainerRegistry.BUCKET_VOLUME;
            CoolantDeviceData.CoolantBathData.ItemOverride own = overrideFor(stack);
            double heatCap = own != null && own.heatJoules > 0.0 ? own.heatJoules
                : Math.max(1000.0, liters * latentPerLiter * recoverableEnergyFraction());
            // At the freezer's pace: boiled off in the time the freezer takes making it, in ambient coolant at full
            // flow (where the freezer does not make it, a minute, as it takes over nitrogen and oxygen)
            double seconds = own != null && own.seconds > 0.0 ? own.seconds : FreezerRecipeEnergy.secondsToMake(stack);
            if (seconds <= 0.0) seconds = 60.0;
            double area = exchangeArea(heatCap / Math.max(1.0, AMBIENT_CELSIUS - boilingPoint), seconds);
            ItemStack gasOutput = cryo.gasCell != null ? cryo.gasCell.copy() : null;

            return new CoolantBathSlot(
                stack,
                cellTemp,
                targetTemp,
                initialDmg,
                maxDmg,
                heatCap,
                area,
                false,
                false,
                false, // cooling = false: item absorbs heat / boils
                gasOutput,
                true);
        }

        if (isCoolantCell(stack)) {
            // Its stored heat (IC2 and GregTech cells keep it their own ways: CellHeat), cold 20 C to full 1000 C
            int maxDmg = CellHeat.max(stack);
            int curDmg = CellHeat.get(stack);
            int cold = CellHeat.cold(stack);
            double cellTemp = 20.0 + ((double) Math.max(0, curDmg - cold) / Math.max(1, maxDmg - cold)) * 980.0;

            boolean cooling = cellTemp > currentCoolantTemp;
            double targetTemp = cooling ? 20.0 : 1000.0;
            double heatCap = maxDmg * 1.0; // 1 HU per damage point, ~1000-60000 J/K
            double area = 0.25; // m^2 surface area

            return new CoolantBathSlot(
                stack,
                cellTemp,
                targetTemp,
                curDmg,
                maxDmg,
                heatCap,
                area,
                true,
                false,
                cooling,
                null);
        }

        ItemDamageKey key = ItemDamageKey.of(stack);
        ItemData data = GTOreDictUnificator.getAssociation(stack);
        Materials mat = data != null && data.mMaterial != null ? data.mMaterial.mMaterial : null;

        // Check Hot Ingot cooling down
        HotColdPair hotPair = key != null ? HOT_TO_COLD_INGOTS.get(key) : null;
        if ((data != null && data.mPrefix == OrePrefixes.ingotHot) || hotPair != null) {
            double hotTemp = hotPair != null ? hotPair.blastFurnaceTempCelsius : getMaterialBlastFurnaceTemp(mat);
            ItemStack coldOutput = hotPair != null ? hotPair.coldStack.copy()
                : (mat != null ? GTOreDictUnificator.get(OrePrefixes.ingot, mat, 1L) : null);

            double initialTemp = hotTemp;
            double targetTemp = ingotCooledTemperatureCelsius();
            int maxDmg = 1000;
            int initialDmg = 0;
            double heatCap = ingotHeat(stack, stack, coldOutput, mat) / Math.max(1.0, hotTemp - targetTemp);
            double area = exchangeArea(heatCap, ingotSeconds(stack, stack, coldOutput, mat));

            return new CoolantBathSlot(
                stack,
                initialTemp,
                targetTemp,
                initialDmg,
                maxDmg,
                heatCap,
                area,
                false,
                true,
                true,
                coldOutput);
        }

        // Check Cold Ingot heating up
        HotColdPair coldPair = key != null ? COLD_TO_HOT_INGOTS.get(key) : null;
        if ((data != null && data.mPrefix == OrePrefixes.ingot) || coldPair != null) {
            double hotTemp = coldPair != null ? coldPair.blastFurnaceTempCelsius : getMaterialBlastFurnaceTemp(mat);
            ItemStack hotOutput = coldPair != null ? coldPair.hotStack.copy()
                : (mat != null ? GTOreDictUnificator.get(OrePrefixes.ingotHot, mat, 1L) : null);

            double initialTemp = 20.0;
            double targetTemp = hotTemp;
            int maxDmg = 1000;
            int initialDmg = 0;
            double heatCap = ingotHeat(stack, hotOutput, stack, mat)
                / Math.max(1.0, hotTemp - ingotCooledTemperatureCelsius());
            double area = exchangeArea(heatCap, ingotSeconds(stack, hotOutput, stack, mat));

            return new CoolantBathSlot(
                stack,
                initialTemp,
                targetTemp,
                initialDmg,
                maxDmg,
                heatCap,
                area,
                false,
                true,
                false,
                hotOutput);
        }

        return null;
    }
}
