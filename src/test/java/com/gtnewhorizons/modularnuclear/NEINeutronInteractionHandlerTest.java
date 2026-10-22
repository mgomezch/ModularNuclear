package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

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
                        assertEquals(1.0, d.fastAbsorption, 1e-6);
                        assertEquals(1.0, d.thermalAbsorption, 1e-6);
                        assertTrue(d.producesDirectEU, "Radiovoltaic plates must produce direct EU");
                        assertTrue(d.maxDirectEU >= 1024L, "Radiovoltaic plates must specify max EU/t");
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
}
