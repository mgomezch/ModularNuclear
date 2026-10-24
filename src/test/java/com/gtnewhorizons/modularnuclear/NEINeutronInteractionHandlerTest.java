package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.item.ModItems;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.nei.NEIFuelStatsHandler;
import com.gtnewhorizons.modularnuclear.common.nei.NEINeutronInteractionHandler;
import com.gtnewhorizons.modularnuclear.common.nei.NEINeutronInteractionHandler.CachedComponentThermalRecipe;
import com.gtnewhorizons.modularnuclear.common.nei.NEINeutronInteractionHandler.CachedNeutronInteractionRecipe;
import com.gtnewhorizons.modularnuclear.common.nei.NEINeutronInteractionHandler.ComponentStatsData;

import codechicken.nei.recipe.TemplateRecipeHandler.CachedRecipe;

public class NEINeutronInteractionHandlerTest {

    @BeforeAll
    public static void setUp() {
        TestEnvironmentHelper.init();

        try {
            if (ModItems.emptyCoreTile == null) {
                ModItems.emptyCoreTile = new com.gtnewhorizons.modularnuclear.common.item.ItemEmptyCoreTile();
            }
            if (ModItems.radiovoltaicPlate == null) {
                ModItems.radiovoltaicPlate = new com.gtnewhorizons.modularnuclear.common.item.ItemRadiovoltaicPlate(
                    "modularnuclear.radiovoltaic.plate");
            }
            if (ModItems.radiovoltaicPlateHV == null) {
                ModItems.radiovoltaicPlateHV = new ItemStack(ModItems.radiovoltaicPlate, 1, 0);
            }
            if (ModItems.radiovoltaicPlateEV == null) {
                ModItems.radiovoltaicPlateEV = new ItemStack(ModItems.radiovoltaicPlate, 1, 1);
            }
        } catch (Throwable ignored) {}

        if (ModMetaTileEntities.reactor == null) {
            ModMetaTileEntities.reactor = new ItemStack(Items.diamond, 1, 0);
        }

        NEINeutronInteractionHandler.sTestMode = true;
        NEINeutronInteractionHandler.resetData();
    }

    @Test
    public void testHandlerIdAndOverlay() {
        NEINeutronInteractionHandler handler = new NEINeutronInteractionHandler();
        assertEquals("gt.nei.neutron_interaction", handler.getOverlayIdentifier());
        assertNotNull(handler.getRecipeName());
        assertEquals("gregtech:textures/gui/nei/neutron_interaction_atlas.png", handler.getGuiTexture());
    }

    @Test
    public void testFullComponentCoverageAcrossAllCategories() {
        NEINeutronInteractionHandler handler = new NEINeutronInteractionHandler();
        handler.loadCraftingRecipes(handler.getOverlayIdentifier());

        assertFalse(handler.arecipes.isEmpty(), "Handler recipes should not be empty");

        Set<String> categoriesFound = new HashSet<>();
        boolean foundReflector = false;
        boolean foundModerator = false;
        boolean foundRadiovoltaic = false;
        boolean foundInsulator = false;
        boolean foundCoolantCell = false;
        boolean foundFluidCoolant = false;
        boolean foundHeatVent = false;
        boolean foundHeatExchanger = false;
        boolean foundControlRod = false;
        boolean foundEmptyTile = false;

        for (CachedRecipe cr : handler.arecipes) {
            if (cr instanceof CachedNeutronInteractionRecipe r1) {
                ComponentStatsData d = r1.data;
                categoriesFound.add(d.category);

                switch (d.category) {
                    case "Reflector" -> {
                        foundReflector = true;
                        assertTrue(d.isReflector, "Reflector must have isReflector flag set");
                        assertTrue(d.reflectionFast > 0.0, "Reflector must specify fast reflection");
                        assertTrue(d.reflectionThermal > 0.0, "Reflector must specify thermal reflection");
                    }
                    case "Moderator" -> {
                        foundModerator = true;
                        assertTrue(d.slowingProbability > 0.40, "Moderators must have high slowing probability");
                        assertTrue(d.hasDurability, "Moderators must specify wear durability");
                        assertTrue(d.maxTemperature >= 2200.0, "Moderators must specify max operating temperature");
                    }
                    case "Radiovoltaic Plate" -> {
                        foundRadiovoltaic = true;
                        assertTrue(d.fastAbsorption == 0.40 || d.fastAbsorption == 0.60,
                            "Radiovoltaic plates must have 40% (HV) or 60% (EV) absorption");
                        assertEquals(d.fastAbsorption, d.thermalAbsorption, 1e-6);
                        assertTrue(d.producesDirectEU, "Radiovoltaic plates must produce direct EU");
                        assertTrue(d.maxDirectEU >= 1024L, "Radiovoltaic plates must specify max EU/t");
                        assertTrue(d.isRadiovoltaic, "Radiovoltaic plates must have isRadiovoltaic flag");
                        assertTrue(d.saturationFlux > 0, "Radiovoltaic plates must specify saturation flux");
                    }
                    case "Thermal Insulator" -> {
                        foundInsulator = true;
                        assertTrue(d.thermalInsulationFactor > 0.0, "Insulators must specify thermal barrier fraction");
                        assertTrue(d.heatTransferCoeff <= 0.05, "Insulators must have low heat transfer coefficient");
                    }
                    case "Coolant Cell" -> {
                        foundCoolantCell = true;
                        assertTrue(d.hasDurability, "Coolant cells must specify heat capacity");
                        assertTrue(d.durability >= 250_000L, "Coolant cells must have configured heat capacity in HU");
                    }
                    case "Fluid Coolant" -> {
                        foundFluidCoolant = true;
                        assertTrue(d.boilingPoint > 0.0, "Fluid coolants must specify boiling point");
                        assertTrue(d.specificHeat > 0.0, "Fluid coolants must specify specific heat");
                    }
                    case "Heat Vent" -> {
                        foundHeatVent = true;
                        assertTrue(d.producesDirectEU, "Heat vents must produce direct EU while active");
                        assertTrue(
                            d.selfCoolingRate > 0.0 || d.componentCoolingRate > 0.0,
                            "Heat vents must specify cooling rate");
                    }
                    case "Heat Exchanger" -> {
                        foundHeatExchanger = true;
                        assertTrue(d.producesDirectEU, "Heat exchangers must produce direct EU while active");
                        assertTrue(d.heatTransferRate > 0.0, "Heat exchangers must specify heat transfer rate");
                    }
                    case "Control Rod" -> {
                        foundControlRod = true;
                        assertTrue(d.thermalAbsorption >= 0.50, "Control rods must have high thermal absorption");
                        assertTrue(d.hasDurability, "Control rods must specify burnout capacity");
                        assertEquals(
                            100_000_000L,
                            d.durability,
                            "Control rods must have standard 100M burnout capacity");
                    }
                    case "Empty Core Tile" -> {
                        foundEmptyTile = true;
                        assertEquals(0.0, d.fastScattering, 1e-6);
                        assertEquals(0.0, d.fastAbsorption, 1e-6);
                        assertEquals(0.0, d.thermalScattering, 1e-6);
                        assertEquals(0.0, d.thermalAbsorption, 1e-6);
                        assertEquals(0.02, d.heatTransferCoeff, 1e-6);
                    }
                }
            }
        }

        assertTrue(foundReflector, "Reflectors must be present in NEI handler");
        assertTrue(foundModerator, "Moderators must be present in NEI handler");
        assertTrue(foundRadiovoltaic, "Radiovoltaic plates must be present in NEI handler");
        assertTrue(foundInsulator, "Insulators must be present in NEI handler");
        assertTrue(foundCoolantCell, "Coolant cells must be present in NEI handler");
        assertTrue(foundFluidCoolant, "Fluid coolants must be present in NEI handler");
        assertTrue(foundHeatVent, "Heat vents must be present in NEI handler");
        assertTrue(foundHeatExchanger, "Heat exchangers must be present in NEI handler");
        assertTrue(foundControlRod, "Control rods must be present in NEI handler");
        assertTrue(foundEmptyTile, "Empty core tile must be present in NEI handler");
    }

    @Test
    public void testSinglePagePerComponent() {
        NEINeutronInteractionHandler handler = new NEINeutronInteractionHandler();
        handler.loadCraftingRecipes(handler.getOverlayIdentifier());

        long p1Count = handler.arecipes.stream()
            .filter(r -> r instanceof CachedNeutronInteractionRecipe)
            .count();

        assertEquals(
            NEINeutronInteractionHandler.ALL_COMPONENTS.size(),
            handler.arecipes.size(),
            "Every component must have exactly 1 unified scrollable page");
        assertTrue(p1Count >= 20, "Should have at least 20 component recipes registered");
        assertEquals(304, handler.getRecipeHeight(0), "Recipe height must be 304 for full 4-card display");
    }

    @Test
    public void testReactorControllerCatalystLoadsAllComponents() {
        NEINeutronInteractionHandler handler = new NEINeutronInteractionHandler();
        ItemStack controller = NEIFuelStatsHandler.getControllerStack();
        assertNotNull(controller);

        handler.loadUsageRecipes(controller);
        assertFalse(handler.arecipes.isEmpty(), "Clicking 'U' on reactor controller should load all components");
    }

    @Test
    public void testThermalNeutronArrowTooltips() {
        NEINeutronInteractionHandler handler = new NEINeutronInteractionHandler();
        handler.loadCraftingRecipes(handler.getOverlayIdentifier());

        CachedNeutronInteractionRecipe r1 = null;
        for (CachedRecipe cr : handler.arecipes) {
            if (cr instanceof CachedNeutronInteractionRecipe recipe && !recipe.data.isReflector) {
                r1 = recipe;
                break;
            }
        }
        assertNotNull(r1, "Should find non-reflector neutron recipe");

        // 1. Thermal scattering arrow and text (Card 2: dy = +33)
        java.util.List<String> tipScatTip = handler.getTooltipsAt(r1, 40, 123, null);
        assertFalse(tipScatTip.isEmpty(), "Thermal scattering arrow tip (40, 123) must show tooltip");
        assertTrue(
            tipScatTip.stream()
                .anyMatch(
                    s -> s.toLowerCase()
                        .contains("scattering")),
            "Should mention scattering");

        java.util.List<String> tipScatCurve = handler.getTooltipsAt(r1, 52, 128, null);
        assertFalse(tipScatCurve.isEmpty(), "Thermal scattering arrow curve (52, 128) must show tooltip");

        java.util.List<String> tipScatText = handler.getTooltipsAt(r1, 47, 115, null);
        assertFalse(tipScatText.isEmpty(), "Thermal scattering text (47, 115) must show tooltip");

        // 2. Thermal absorption downward arrow, curved arrow, and text (Card 2: dy = +33)
        java.util.List<String> tipAbsDown = handler.getTooltipsAt(r1, 74, 153, null);
        assertFalse(tipAbsDown.isEmpty(), "Thermal absorption downward arrow (74, 153) must show tooltip");
        assertTrue(
            tipAbsDown.stream()
                .anyMatch(
                    s -> s.toLowerCase()
                        .contains("absorption")),
            "Should mention absorption");

        java.util.List<String> tipAbsCurved = handler.getTooltipsAt(r1, 85, 125, null);
        assertFalse(tipAbsCurved.isEmpty(), "Thermal absorption curved arrow (85, 125) must show tooltip");
        assertTrue(
            tipAbsCurved.stream()
                .anyMatch(
                    s -> s.toLowerCase()
                        .contains("absorption")),
            "Should mention absorption");

        java.util.List<String> tipAbsText = handler.getTooltipsAt(r1, 97, 153, null);
        assertFalse(tipAbsText.isEmpty(), "Thermal absorption text (97, 153) must show tooltip");

        // 3. Fast neutron interaction elements (Card 1: dy = +31)
        // 3a. Fast reflection / incoming arrow (40, 55)
        java.util.List<String> tipFastRefl = handler.getTooltipsAt(r1, 40, 55, null);
        assertFalse(tipFastRefl.isEmpty(), "Fast reflection arrow/text (40, 55) must show tooltip");
        assertTrue(
            tipFastRefl.stream()
                .anyMatch(
                    s -> s.toLowerCase()
                        .contains("reflection")),
            "Should mention reflection");

        // 3b. Fast absorption arrow (83, 79) and text (83, 87)
        java.util.List<String> tipFastAbsArrow = handler.getTooltipsAt(r1, 83, 79, null);
        assertFalse(tipFastAbsArrow.isEmpty(), "Fast absorption arrow (83, 79) must show tooltip");
        assertTrue(
            tipFastAbsArrow.stream()
                .anyMatch(
                    s -> s.toLowerCase()
                        .contains("absorption")),
            "Should mention absorption");

        java.util.List<String> tipFastAbsText = handler.getTooltipsAt(r1, 83, 87, null);
        assertFalse(tipFastAbsText.isEmpty(), "Fast absorption text (83, 87) must show tooltip");

        // 3c. Fast scattering green arrows (102, 65)
        java.util.List<String> tipFastScatArrows = handler.getTooltipsAt(r1, 102, 65, null);
        assertFalse(tipFastScatArrows.isEmpty(), "Fast scattering arrows (102, 65) must show tooltip");
        assertTrue(
            tipFastScatArrows.stream()
                .anyMatch(
                    s -> s.toLowerCase()
                        .contains("scattering")),
            "Should mention scattering");

        // 3d. Fast scattered fraction arrow and text (136, 43)
        java.util.List<String> tipFastFraction = handler.getTooltipsAt(r1, 136, 43, null);
        assertFalse(tipFastFraction.isEmpty(), "Fast scattered fraction (136, 43) must show tooltip");
        assertTrue(
            tipFastFraction.stream()
                .anyMatch(
                    s -> s.toLowerCase()
                        .contains("fast")),
            "Should mention fast fraction");

        // 3e. Moderated scattered fraction arrow and text (136, 87)
        java.util.List<String> tipModFraction = handler.getTooltipsAt(r1, 136, 87, null);
        assertFalse(tipModFraction.isEmpty(), "Moderated scattered fraction (136, 87) must show tooltip");
        assertTrue(
            tipModFraction.stream()
                .anyMatch(
                    s -> s.toLowerCase()
                        .contains("thermal")),
            "Should mention thermal fraction");

        // 3f. Pie chart (136, 65)
        java.util.List<String> tipPieChart = handler.getTooltipsAt(r1, 136, 65, null);
        assertFalse(tipPieChart.isEmpty(), "Pie chart (136, 65) must show tooltip");

        // 3g. Slot positions on single page
        assertEquals(75, r1.input.relx, "Fast input stack X must be 75");
        assertEquals(57, r1.input.rely, "Fast input stack Y must be 57");
        assertEquals(67, r1.inputThermal.relx, "Thermal input stack X must be 67");
        assertEquals(130, r1.inputThermal.rely, "Thermal input stack Y must be 130");
        assertEquals(58, r1.inputHeat.relx, "Heat input stack X must be 58");
        assertEquals(194, r1.inputHeat.rely, "Heat input stack Y must be 194");
        assertNotNull(r1.catalyst, "Header catalyst stack must not be null");
        assertEquals(7, r1.catalyst.relx, "Header catalyst stack X must be 7");
        assertEquals(3, r1.catalyst.rely, "Header catalyst stack Y must be 3");

        // 4. Card 3 (Heat) & Card 4 (Durability) tooltips on the same page
        java.util.List<String> tipConduction = handler.getTooltipsAt(r1, 110, 190, null);
        assertFalse(tipConduction.isEmpty(), "Conduction tooltip in Card 3 must show");
        assertTrue(
            tipConduction.stream()
                .anyMatch(
                    s -> s.toLowerCase()
                        .contains("conduction")),
            "Should mention conduction");

        java.util.List<String> tipDurability = handler.getTooltipsAt(r1, 80, 275, null);
        assertFalse(tipDurability.isEmpty(), "Durability capacity tooltip in Card 4 must show");
    }

    @Test
    public void testFormatPercentAndFormatNumber() {
        // Percentage formatting from fraction (0..1)
        assertEquals("0%", NEINeutronInteractionHandler.formatPercent(0.0));
        assertEquals("50%", NEINeutronInteractionHandler.formatPercent(0.50));
        assertEquals("15%", NEINeutronInteractionHandler.formatPercent(0.15));
        assertEquals("25%", NEINeutronInteractionHandler.formatPercent(0.25));
        assertEquals("62.1%", NEINeutronInteractionHandler.formatPercent(0.621));
        assertEquals("0.2%", NEINeutronInteractionHandler.formatPercent(0.002));

        // Heavy water floating point precision: (1.0 - 0.9) = 0.09999999999999998
        assertEquals("10%", NEINeutronInteractionHandler.formatPercent(1.0 - 0.9));
        assertEquals("90%", NEINeutronInteractionHandler.formatPercent(0.90));

        // Up to two fractional digits and trailing zero trimming
        assertEquals("10.5%", NEINeutronInteractionHandler.formatPercent(0.105));
        assertEquals("10.25%", NEINeutronInteractionHandler.formatPercent(0.1025));
        assertEquals("10.26%", NEINeutronInteractionHandler.formatPercent(0.10256));

        // formatNumber checks
        assertEquals("0", NEINeutronInteractionHandler.formatNumber(0.0));
        assertEquals("10", NEINeutronInteractionHandler.formatNumber(10.0));
        assertEquals("10", NEINeutronInteractionHandler.formatNumber(9.999999999999998));
        assertEquals("10.5", NEINeutronInteractionHandler.formatNumber(10.50));
        assertEquals("10.25", NEINeutronInteractionHandler.formatNumber(10.25));
        assertEquals("0.7", NEINeutronInteractionHandler.formatNumber(0.70));
    }

    @Test
    public void testWordWrappingExtraInfo() {
        String longText = "Liquid metal coolant (melts at 97.8 °C, boils at 883 °C) | k = 0.70";
        java.util.List<String> lines = NEINeutronInteractionHandler.wrapExtraInfo(longText, 132, null);
        assertTrue(lines.size() >= 2, "Long extraInfo text must wrap into at least 2 lines");

        for (String line : lines) {
            assertTrue(line.length() <= 35, "Each wrapped line should fit within max character limit: " + line);
        }

        String shortText = "Short info";
        java.util.List<String> shortLines = NEINeutronInteractionHandler.wrapExtraInfo(shortText, 132, null);
        assertEquals(1, shortLines.size(), "Short text should remain a single line");
        assertEquals("Short info", shortLines.get(0));

        assertTrue(
            NEINeutronInteractionHandler.wrapExtraInfo(null, 132, null)
                .isEmpty());
        assertTrue(
            NEINeutronInteractionHandler.wrapExtraInfo("", 132, null)
                .isEmpty());
    }

    @Test
    public void testLangFilePercentEscaping() throws Exception {
        java.util.Properties props = new java.util.Properties();
        try (java.io.InputStream in = getClass().getResourceAsStream("/assets/modularnuclear/lang/en_US.lang")) {
            assertNotNull(in, "en_US.lang must be present in classpath");
            props.load(in);
        }

        String[] testKeys = {
            "gt.nei.neutron_interaction.fast_fraction.desc",
            "gt.nei.neutron_interaction.thermal_fraction.desc",
            "gt.nei.neutron_interaction.conduction.desc",
            "gt.nei.neutron_interaction.fast_stats",
            "gt.nei.neutron_interaction.thermal_stats"
        };

        for (String key : testKeys) {
            String raw = props.getProperty(key);
            assertNotNull(raw, "Key " + key + " must exist in en_US.lang");
            assertFalse(raw.contains("%%%%"), "Key " + key + " must not contain four percent signs (%%%%): " + raw);

            // Test String.format with preformatted concise arguments to ensure single % and no double %%
            String formatted;
            if (key.endsWith("stats")) {
                if (key.contains("fast")) {
                    formatted = String.format(
                        java.util.Locale.US,
                        raw,
                        NEINeutronInteractionHandler.formatPercent(0.80),
                        NEINeutronInteractionHandler.formatPercent(0.10),
                        NEINeutronInteractionHandler.formatPercent(1.0 - 0.90));
                    assertEquals("Fast: Scatter 80%, Absorb 10%, Slow 10%", formatted);
                } else {
                    formatted = String.format(
                        java.util.Locale.US,
                        raw,
                        NEINeutronInteractionHandler.formatPercent(0.15),
                        NEINeutronInteractionHandler.formatPercent(0.85));
                    assertEquals("Thermal: Scatter 15%, Absorb 85%", formatted);
                }
            } else if (key.contains("conduction")) {
                formatted = String.format(java.util.Locale.US, raw, NEINeutronInteractionHandler.formatNumber(0.70));
                assertEquals("Thermal conduction coefficient k = 0.7 with adjacent reactor cells", formatted);
            } else if (key.contains("fast_fraction")) {
                formatted = String.format(java.util.Locale.US, raw, NEINeutronInteractionHandler.formatPercent(1.0 - 0.90));
                assertEquals("10% of scattered neutrons remain fast", formatted);
            } else {
                formatted = String.format(java.util.Locale.US, raw, NEINeutronInteractionHandler.formatPercent(0.90));
                assertEquals("90% of scattered neutrons moderate to thermal", formatted);
            }

            assertFalse(formatted.contains("%%"), "Formatted string must NOT contain double percent sign (%%): " + formatted);
        }
    }

    @Test
    public void testRadiovoltaicCurveAndTooltips() {
        NEINeutronInteractionHandler handler = new NEINeutronInteractionHandler();
        handler.loadCraftingRecipes(handler.getOverlayIdentifier());

        CachedNeutronInteractionRecipe rvHvRecipe = null;
        CachedNeutronInteractionRecipe rvEvRecipe = null;
        int rvHvIndex = -1;
        int rvEvIndex = -1;

        for (int i = 0; i < handler.arecipes.size(); i++) {
            CachedRecipe cr = handler.arecipes.get(i);
            if (cr instanceof CachedNeutronInteractionRecipe recipe && recipe.data.isRadiovoltaic) {
                if ("HV Radiovoltaic Plate".equals(recipe.data.name)) {
                    rvHvRecipe = recipe;
                    rvHvIndex = i;
                } else if ("EV Radiovoltaic Plate".equals(recipe.data.name)) {
                    rvEvRecipe = recipe;
                    rvEvIndex = i;
                }
            }
        }

        assertNotNull(rvHvRecipe, "HV Radiovoltaic recipe must be registered");
        assertNotNull(rvEvRecipe, "EV Radiovoltaic recipe must be registered");

        // 1. Absorption rates: 40% for HV, 60% for EV
        assertEquals(0.40, rvHvRecipe.data.fastAbsorption, 1e-4, "HV absorption fast must be 40%");
        assertEquals(0.40, rvHvRecipe.data.thermalAbsorption, 1e-4, "HV absorption thermal must be 40%");
        assertEquals(0.60, rvEvRecipe.data.fastAbsorption, 1e-4, "EV absorption fast must be 60%");
        assertEquals(0.60, rvEvRecipe.data.thermalAbsorption, 1e-4, "EV absorption thermal must be 60%");

        // 2. Saturation and max EU
        assertEquals(1024L, rvHvRecipe.data.maxDirectEU, "HV max direct EU must be 1024 EU/t (2A HV)");
        assertEquals(4096L, rvEvRecipe.data.maxDirectEU, "EV max direct EU must be 4096 EU/t (2A EV)");
        assertEquals(50.0, rvHvRecipe.data.saturationFlux, 1e-4, "HV saturation flux must be 50.0");
        assertEquals(150.0, rvEvRecipe.data.saturationFlux, 1e-4, "EV saturation flux must be 150.0");

        // 3. Recipe height: 314 for radiovoltaics (to comfortably accommodate the curve), 304 for standard
        assertEquals(314, handler.getRecipeHeight(rvHvIndex), "HV Radiovoltaic recipe height must be 314");
        assertEquals(314, handler.getRecipeHeight(rvEvIndex), "EV Radiovoltaic recipe height must be 314");

        // 4. Power & heat curve calculations
        double hvPowerLow = rvHvRecipe.data.calculatePower(25.0);
        double hvHeatLow = rvHvRecipe.data.calculateHeat(25.0);
        assertTrue(hvPowerLow > 0.0, "Low flux must generate power");
        assertTrue(hvHeatLow < 50.0, "Low flux must generate minimal excess heat");

        double hvPowerHigh = rvHvRecipe.data.calculatePower(200.0);
        double hvHeatHigh = rvHvRecipe.data.calculateHeat(200.0);
        assertTrue(hvPowerHigh >= 1020.0, "High flux power must approach 1024 EU/t saturation");
        assertTrue(hvHeatHigh > 2500.0, "High flux excess energy must convert to heat");

        // 5. Interactive tooltips on hover over Card 4 curve
        List<String> tip = new ArrayList<>();
        int midPlotX = NEINeutronInteractionHandler.RV_PLOT_X + NEINeutronInteractionHandler.RV_PLOT_W / 2;
        int midPlotY = NEINeutronInteractionHandler.RV_PLOT_Y + NEINeutronInteractionHandler.RV_PLOT_H / 2;

        assertTrue(NEINeutronInteractionHandler.isMouseOverRVPlot(midPlotX, midPlotY), "Center of plot must be mouse over");
        assertFalse(NEINeutronInteractionHandler.isMouseOverRVPlot(5, 5), "Top left of GUI is not mouse over");

        List<String> hoverTips = handler.getTooltipsAt(rvHvRecipe, midPlotX, midPlotY, tip);
        assertFalse(hoverTips.isEmpty(), "Hovering over plot must generate tooltips");

        boolean hasFlux = false;
        boolean hasPower = false;
        boolean hasHeat = false;
        for (String line : hoverTips) {
            if (line.contains("n/t flux")) hasFlux = true;
            if (line.contains("Power output:")) hasPower = true;
            if (line.contains("Excess heating:")) hasHeat = true;
        }
        assertTrue(hasFlux, "Tooltip must contain flux information");
        assertTrue(hasPower, "Tooltip must contain power output information");
        assertTrue(hasHeat, "Tooltip must contain excess heat information");
    }

    @Test
    public void testModeratorsExclusivelyAcceptLongRodsInNEI() {
        NEINeutronInteractionHandler handler = new NEINeutronInteractionHandler();
        handler.loadCraftingRecipes(handler.getOverlayIdentifier());

        List<CachedNeutronInteractionRecipe> moderatorRecipes = new ArrayList<>();
        for (CachedRecipe cr : handler.arecipes) {
            if (cr instanceof CachedNeutronInteractionRecipe nr && "Moderator".equals(nr.data.category)) {
                moderatorRecipes.add(nr);
            }
        }

        assertFalse(moderatorRecipes.isEmpty(), "Moderator recipes must be registered");

        // 1. Verify all registered moderator recipes have name and stack strictly as long rod
        for (CachedNeutronInteractionRecipe nr : moderatorRecipes) {
            ComponentStatsData d = nr.data;
            assertTrue(d.name.contains("Long Rod"), "Moderator component name must specify Long Rod: " + d.name);
            assertFalse(d.name.toLowerCase().contains("plate"), "Moderator component must never be a plate: " + d.name);
            assertNotNull(d.stack, "Moderator recipe must have non-null stack");
            assertNotEquals(
                OreDictionary.WILDCARD_VALUE,
                d.stack.getItemDamage(),
                "Moderator item damage must never be WILDCARD_VALUE");
            assertTrue(d.alternateStacks.isEmpty(), "Moderators should not register arbitrary alternates");
        }

        // 2. Querying usage on long rods must match their respective moderator recipes
        ItemStack carbonLongRod = NEINeutronInteractionHandler.createTestPlaceholder("Carbon Long Rod");
        ItemStack graphiteLongRod = NEINeutronInteractionHandler.createTestPlaceholder("Graphite Long Rod");
        ItemStack berylliumLongRod = NEINeutronInteractionHandler.createTestPlaceholder("Beryllium Long Rod");

        NEINeutronInteractionHandler carbonUsage = new NEINeutronInteractionHandler();
        carbonUsage.loadUsageRecipes(carbonLongRod);
        assertTrue(
            carbonUsage.arecipes.stream()
                .anyMatch(r -> r instanceof CachedNeutronInteractionRecipe nr && nr.data.name.equals("Carbon Long Rod")),
            "Querying usage on Carbon Long Rod must find Carbon Long Rod moderator recipe");

        NEINeutronInteractionHandler graphiteUsage = new NEINeutronInteractionHandler();
        graphiteUsage.loadUsageRecipes(graphiteLongRod);
        assertTrue(
            graphiteUsage.arecipes.stream()
                .anyMatch(r -> r instanceof CachedNeutronInteractionRecipe nr && nr.data.name.equals("Graphite Long Rod")),
            "Querying usage on Graphite Long Rod must find Graphite Long Rod moderator recipe");

        NEINeutronInteractionHandler berylliumUsage = new NEINeutronInteractionHandler();
        berylliumUsage.loadUsageRecipes(berylliumLongRod);
        assertTrue(
            berylliumUsage.arecipes.stream()
                .anyMatch(r -> r instanceof CachedNeutronInteractionRecipe nr && nr.data.name.equals("Beryllium Long Rod")),
            "Querying usage on Beryllium Long Rod must find Beryllium Long Rod moderator recipe");

        // 3. Querying usage on plates, short rods, or dusts must NEVER match moderator recipes
        ItemStack carbonPlate = NEINeutronInteractionHandler.createTestPlaceholder("Carbon Plate");
        ItemStack graphitePlate = NEINeutronInteractionHandler.createTestPlaceholder("Graphite Plate");
        ItemStack berylliumRod = NEINeutronInteractionHandler.createTestPlaceholder("Beryllium Rod");
        ItemStack carbonDust = NEINeutronInteractionHandler.createTestPlaceholder("Carbon Dust");

        for (ItemStack nonModerator : new ItemStack[] { carbonPlate, graphitePlate, berylliumRod, carbonDust }) {
            NEINeutronInteractionHandler nonModUsage = new NEINeutronInteractionHandler();
            nonModUsage.loadUsageRecipes(nonModerator);
            boolean matchedModerator = nonModUsage.arecipes.stream()
                .anyMatch(r -> r instanceof CachedNeutronInteractionRecipe nr && "Moderator".equals(nr.data.category));
            assertFalse(
                matchedModerator,
                "Non-long-rod item " + nonModerator.getDisplayName() + " must never match any moderator recipe");
        }

        // 4. Verify cross-meta matching behavior simulating GregTech MetaGeneratedItem01
        // (same Item class, different metadata/damage values on non-damageable items)
        ItemStack itemA0 = new ItemStack(net.minecraft.init.Items.paper, 1, 100);
        ItemStack itemA1 = new ItemStack(net.minecraft.init.Items.paper, 1, 101);
        ItemStack itemAWildcard = new ItemStack(net.minecraft.init.Items.paper, 1, OreDictionary.WILDCARD_VALUE);

        assertTrue(handler.matchesStack(itemA0, itemA0), "Identical item and meta must match");
        assertFalse(handler.matchesStack(itemA0, itemA1), "Different metadata on same non-damageable item must not match");
        assertFalse(handler.matchesStack(itemAWildcard, itemA0), "Wildcard metadata query must not match specific non-damageable item");
        assertFalse(handler.matchesStack(itemA0, itemAWildcard), "Specific item must not match wildcard non-damageable component");
    }

    @Test
    public void testCard1FastNeutronPieChartLayoutAndConditionalPercentages() {
        NEINeutronInteractionHandler handler = new NEINeutronInteractionHandler();
        handler.loadCraftingRecipes(handler.getOverlayIdentifier());

        // Verify the constant is moved further to the right than the old position 136
        assertEquals(142, NEINeutronInteractionHandler.FAST_PIE_X, "Fast pie chart X must be positioned at 142");
        assertTrue(NEINeutronInteractionHandler.FAST_PIE_X > 136, "Fast pie chart must be further to the right than 136");

        CachedNeutronInteractionRecipe graphiteRecipe = null;
        CachedNeutronInteractionRecipe berylliumRecipe = null;
        CachedNeutronInteractionRecipe goldReflectorRecipe = null;

        for (CachedRecipe cr : handler.arecipes) {
            if (cr instanceof CachedNeutronInteractionRecipe nr) {
                if ("Graphite Long Rod".equals(nr.data.name)) {
                    graphiteRecipe = nr;
                } else if ("Beryllium Long Rod".equals(nr.data.name)) {
                    berylliumRecipe = nr;
                } else if ("Gold Plate Reflector".equals(nr.data.name)) {
                    goldReflectorRecipe = nr;
                }
            }
        }

        assertNotNull(graphiteRecipe, "Graphite Long Rod must be present");
        assertNotNull(berylliumRecipe, "Beryllium Long Rod must be present");
        assertNotNull(goldReflectorRecipe, "Gold Plate Reflector must be present");

        int pieX = NEINeutronInteractionHandler.FAST_PIE_X;

        // 1. Graphite: slowingProbability = 0.50 -> fast conditional fraction = 50%, thermal = 50%
        assertEquals(0.50, graphiteRecipe.data.slowingProbability, 1e-4);
        assertEquals("50%", NEINeutronInteractionHandler.formatPercent(1.0 - graphiteRecipe.data.slowingProbability));
        assertEquals("50%", NEINeutronInteractionHandler.formatPercent(graphiteRecipe.data.slowingProbability));

        List<String> graphiteFastTip = handler.getTooltipsAt(graphiteRecipe, pieX, 43, null);
        assertFalse(graphiteFastTip.isEmpty(), "Graphite fast tooltip at (FAST_PIE_X, 43) must be present");
        assertTrue(
            graphiteFastTip.stream().anyMatch(s -> s.toLowerCase().contains("fast_fraction")),
            "Graphite fast fraction tooltip must be present at (FAST_PIE_X, 43)");

        List<String> graphiteModTip = handler.getTooltipsAt(graphiteRecipe, pieX, 87, null);
        assertFalse(graphiteModTip.isEmpty(), "Graphite moderated tooltip at (FAST_PIE_X, 87) must be present");
        assertTrue(
            graphiteModTip.stream().anyMatch(s -> s.toLowerCase().contains("thermal_fraction")),
            "Graphite thermal fraction tooltip must be present at (FAST_PIE_X, 87)");

        List<String> graphitePieTip = handler.getTooltipsAt(graphiteRecipe, pieX, 65, null);
        assertFalse(graphitePieTip.isEmpty(), "Graphite pie chart tooltip at (FAST_PIE_X, 65) must be present");
        assertTrue(
            graphitePieTip.stream().anyMatch(s -> s.toLowerCase().contains("fast_fraction")),
            "Graphite pie chart tooltip must contain fast fraction");
        assertTrue(
            graphitePieTip.stream().anyMatch(s -> s.toLowerCase().contains("thermal_fraction")),
            "Graphite pie chart tooltip must contain thermal fraction");

        // 2. Beryllium: slowingProbability = 0.65 -> fast conditional = 35%, thermal = 65%
        assertEquals(0.65, berylliumRecipe.data.slowingProbability, 1e-4);
        assertEquals("35%", NEINeutronInteractionHandler.formatPercent(1.0 - berylliumRecipe.data.slowingProbability));
        assertEquals("65%", NEINeutronInteractionHandler.formatPercent(berylliumRecipe.data.slowingProbability));

        List<String> beryFastTip = handler.getTooltipsAt(berylliumRecipe, pieX, 43, null);
        assertTrue(
            beryFastTip.stream().anyMatch(s -> s.toLowerCase().contains("fast_fraction")),
            "Beryllium fast fraction tooltip must be present at (FAST_PIE_X, 43)");

        List<String> beryModTip = handler.getTooltipsAt(berylliumRecipe, pieX, 87, null);
        assertTrue(
            beryModTip.stream().anyMatch(s -> s.toLowerCase().contains("thermal_fraction")),
            "Beryllium thermal fraction tooltip must be present at (FAST_PIE_X, 87)");

        // 3. Gold Reflector: slowingProbability = 0.0 -> fast conditional = 100%, thermal = 0%
        assertEquals(0.0, goldReflectorRecipe.data.slowingProbability, 1e-4);
        assertEquals("100%", NEINeutronInteractionHandler.formatPercent(1.0 - goldReflectorRecipe.data.slowingProbability));
        assertEquals("0%", NEINeutronInteractionHandler.formatPercent(goldReflectorRecipe.data.slowingProbability));

        List<String> goldFastTip = handler.getTooltipsAt(goldReflectorRecipe, pieX, 43, null);
        assertTrue(
            goldFastTip.stream().anyMatch(s -> s.toLowerCase().contains("fast_fraction")),
            "Gold Reflector fast fraction tooltip must be present at (FAST_PIE_X, 43)");

        List<String> goldModTip = handler.getTooltipsAt(goldReflectorRecipe, pieX, 87, null);
        assertTrue(
            goldModTip.stream().anyMatch(s -> s.toLowerCase().contains("thermal_fraction")),
            "Gold Reflector thermal fraction tooltip must be present at (FAST_PIE_X, 87)");

        // 4. Overall fast scattering probability label
        assertEquals(120, NEINeutronInteractionHandler.FAST_SCAT_LABEL_X, "Fast scattering label X must be positioned at 120");
        assertTrue(NEINeutronInteractionHandler.FAST_SCAT_LABEL_X < NEINeutronInteractionHandler.FAST_PIE_X,
            "Fast scattering label must be to the left of the pie chart");
        assertTrue(NEINeutronInteractionHandler.FAST_SCAT_LABEL_X > 107,
            "Fast scattering label must be to the right of the green scattering arrows");

        assertEquals("93%", NEINeutronInteractionHandler.formatPercent(graphiteRecipe.data.fastScattering));
        assertEquals("96%", NEINeutronInteractionHandler.formatPercent(berylliumRecipe.data.fastScattering));
        assertEquals("85%", NEINeutronInteractionHandler.formatPercent(goldReflectorRecipe.data.fastScattering));

        int scatLabelX = NEINeutronInteractionHandler.FAST_SCAT_LABEL_X;
        List<String> scatLabelTip = handler.getTooltipsAt(graphiteRecipe, scatLabelX, 61, null);
        assertFalse(scatLabelTip.isEmpty(), "Hovering over scattering probability label (120, 61) must show tooltip");
        assertTrue(
            scatLabelTip.stream().anyMatch(s -> s.toLowerCase().contains("scattering")),
            "Scattering label tooltip must mention scattering");
    }
}
