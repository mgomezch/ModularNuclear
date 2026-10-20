package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ControlRodData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ReflectorData;
import com.gtnewhorizons.modularnuclear.common.config.data.FuelData;
import com.gtnewhorizons.modularnuclear.common.item.NuclearFuelTooltipHandler;

public class NuclearFuelTooltipHandlerTest {

    @BeforeAll
    public static void setUp() {
        TestEnvironmentHelper.init();
        ReactorDataRegistry.loadDefaults();
    }

    @Test
    public void testFuelRodTooltipReplacesDurability() {
        NuclearFuelTooltipHandler handler = new NuclearFuelTooltipHandler();

        // Create a custom item stack and register a matching test fuel in ReactorDataRegistry
        Item testItem = new Item() {

            @Override
            public String getUnlocalizedName(ItemStack stack) {
                return "item.testRodUranium";
            }
        };
        ItemStack stack = new ItemStack(testItem, 1, 0);

        FuelData testFuel = new FuelData();
        testFuel.id = "test_uranium";
        testFuel.name = "Test Uranium Fuel Rod";
        testFuel.archetype = "standard";
        testFuel.baseNeutrons = 4;
        testFuel.thermalFissionMultiplier = 1.0;
        testFuel.fastFissionMultiplier = 0.10;
        testFuel.defaultDurability = 160000;
        testFuel.peakReactivityTemp = 770.0;
        testFuel.floorTemp = 2300.0;
        testFuel.matching.add("item.testRodUranium");

        ReactorDataRegistry.getAllFuels(); // Ensure loaded
        // Add to fuels list via save/load or findFuel check
        ReactorDataRegistry.findFuel(stack); // Might be null initially

        // We can test by adding testFuel into ReactorDataRegistry
        // Or using an existing default fuel matching pattern
        FuelData defaultUranium = ReactorDataRegistry.getAllFuels()
            .stream()
            .filter(f -> "fuel_uranium_1".equalsIgnoreCase(f.id))
            .findFirst()
            .orElse(null);
        assertNotNull(defaultUranium);
        assertEquals(160000, defaultUranium.defaultDurability);
        assertEquals(770.0, defaultUranium.peakReactivityTemp);
        assertEquals(2300.0, defaultUranium.floorTemp);

        // Test with item that matches default uranium matching pattern
        Item uItem = new Item() {

            @Override
            public String getUnlocalizedName(ItemStack s) {
                return "gt.rodUranium";
            }
        };
        ItemStack uStack = new ItemStack(uItem, 1, 0);

        List<String> tip = new ArrayList<>();
        tip.add("Durability: 10,000 / 10,000");

        ItemTooltipEvent event = new ItemTooltipEvent(uStack, null, tip, false);
        handler.onItemTooltip(event);

        // Verify durability was replaced from 10k to configured 160k
        boolean hasConfiguredDurability = false;
        boolean hasPeakReactivity = false;
        boolean hasFloorTemp = false;
        boolean hasBaseNeutrons = false;
        boolean hasFission = false;

        for (String line : event.toolTip) {
            String stripped = net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(line);
            if (stripped.contains("160,000")) {
                hasConfiguredDurability = true;
            }
            if (stripped.contains("770 °C (peak)")) {
                hasPeakReactivity = true;
            }
            if (stripped.contains("2,300 °C (floor)")) {
                hasFloorTemp = true;
            }
            if (stripped.contains("Base neutrons: 4 n/t")) {
                hasBaseNeutrons = true;
            }
            if (stripped.contains("Fission: 1.25x (thermal)")) {
                hasFission = true;
            }
        }

        assertTrue(hasConfiguredDurability, "Tooltip should show configured 160,000 durability");
        assertTrue(hasPeakReactivity, "Tooltip should show peak reactivity 770 °C");
        assertTrue(hasFloorTemp, "Tooltip should show floor temp 2,300 °C");
        assertTrue(hasBaseNeutrons, "Tooltip should show base neutrons");
        assertTrue(hasFission, "Tooltip should show fission multipliers");
    }

    @Test
    public void testModeratorTooltipUsesSentenceCase() {
        NuclearFuelTooltipHandler handler = new NuclearFuelTooltipHandler();

        ModeratorData graphite = ReactorDataRegistry.getComponentData().moderators.stream()
            .filter(m -> "moderator_graphite".equalsIgnoreCase(m.id))
            .findFirst()
            .orElse(null);
        assertNotNull(graphite);

        Item gItem = new Item() {

            @Override
            public String getUnlocalizedName(ItemStack s) {
                return "stickLongGraphite";
            }
        };
        ItemStack gStack = new ItemStack(gItem, 1, 0);

        List<String> tip = new ArrayList<>();
        ItemTooltipEvent event = new ItemTooltipEvent(gStack, null, tip, false);
        handler.onItemTooltip(event);

        boolean hasModerator = false;
        for (String line : event.toolTip) {
            String stripped = net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(line);
            if (stripped.startsWith("MPTR moderator:")) {
                hasModerator = true;
            }
            // Verify sentence case
            assertFalse(stripped.startsWith("MPTR Moderator:"), "Must use sentence case 'MPTR moderator:'");
            assertFalse(stripped.startsWith("Wigner Energy:"), "Must use sentence case 'Wigner energy:'");
            assertFalse(stripped.startsWith("Wigner Kinetics:"), "Must use sentence case 'Wigner kinetics:'");
        }
        assertTrue(hasModerator, "Should include moderator tooltip");
    }

    @Test
    public void testReflectorTooltip() {
        NuclearFuelTooltipHandler handler = new NuclearFuelTooltipHandler();

        ReflectorData tc = ReactorDataRegistry.getComponentData().reflectors.stream()
            .filter(r -> "reflector_tungsten_carbide".equalsIgnoreCase(r.id))
            .findFirst()
            .orElse(null);
        assertNotNull(tc);

        Item bItem = new Item() {

            @Override
            public String getUnlocalizedName(ItemStack s) {
                return "plateTungstenCarbide";
            }
        };
        ItemStack bStack = new ItemStack(bItem, 1, 0);

        List<String> tip = new ArrayList<>();
        ItemTooltipEvent event = new ItemTooltipEvent(bStack, null, tip, false);
        handler.onItemTooltip(event);

        boolean hasReflector = false;
        for (String line : event.toolTip) {
            String stripped = net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(line);
            if (stripped.startsWith("MPTR neutron reflector:")) {
                hasReflector = true;
            }
        }
        assertTrue(hasReflector, "Should include reflector tooltip");
    }

    @Test
    public void testControlRodTooltip() {
        NuclearFuelTooltipHandler handler = new NuclearFuelTooltipHandler();

        ControlRodData cd = ReactorDataRegistry.getComponentData().controlRods.stream()
            .filter(c -> "rod_cadmium".equalsIgnoreCase(c.id))
            .findFirst()
            .orElse(null);
        assertNotNull(cd);

        Item cItem = new Item() {

            @Override
            public String getUnlocalizedName(ItemStack s) {
                return "rodCadmium";
            }
        };
        ItemStack cStack = new ItemStack(cItem, 1, 0);

        List<String> tip = new ArrayList<>();
        ItemTooltipEvent event = new ItemTooltipEvent(cStack, null, tip, false);
        handler.onItemTooltip(event);

        boolean hasControlRod = false;
        for (String line : event.toolTip) {
            String stripped = net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(line);
            if (stripped.startsWith("MPTR control rod:")) {
                hasControlRod = true;
            }
        }
        assertTrue(hasControlRod, "Should include control rod tooltip");
    }
}
