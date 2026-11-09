package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData;
import com.gtnewhorizons.modularnuclear.common.nei.ComponentCardValues;
import com.gtnewhorizons.modularnuclear.common.nei.ComponentCardValues.HeatAction;
import com.gtnewhorizons.modularnuclear.common.nei.HeatRowSimulation;
import com.gtnewhorizons.modularnuclear.common.nei.NEINeutronInteractionHandler;
import com.gtnewhorizons.modularnuclear.common.nuclear.EngineParameters;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.TileContent;

/** The numbers on the thermal and heat cards, from the default config. See Note [Thermal and heat cards]. */
public class ComponentCardValuesTest {

    @BeforeAll
    public static void setUp() {
        TestEnvironmentHelper.init();
        ReactorDataRegistry.loadDefaults();
    }

    private static <T> T find(List<T> list, Function<T, String> id, String wanted) {
        return list.stream()
            .filter(x -> wanted.equals(id.apply(x)))
            .findFirst()
            .orElseThrow();
    }

    private static ComponentCardValues fluid(String id) {
        ComponentData.CoolantFluidData d = find(ReactorDataRegistry.getComponentData().coolantFluids, x -> x.id, id);
        return ComponentCardValues.ofContent(TileContent.of(TileContent.Kind.COOLANT_FLUID, d), null, 0.0);
    }

    private static ComponentCardValues vent() {
        ComponentData.HeatVentData d = find(
            ReactorDataRegistry.getComponentData().heatVents,
            x -> x.id,
            "vent_standard");
        return ComponentCardValues.ofContent(TileContent.of(TileContent.Kind.HEAT_VENT, d), null, 0.0);
    }

    private static ComponentCardValues exchanger() {
        ComponentData.HeatExchangerData d = find(
            ReactorDataRegistry.getComponentData().heatExchangers,
            x -> x.id,
            "exchanger_standard");
        return ComponentCardValues.ofContent(TileContent.of(TileContent.Kind.HEAT_EXCHANGER, d), null, 0.0);
    }

    private static ComponentCardValues insulator(String id) {
        ComponentData.InsulatorData d = find(ReactorDataRegistry.getComponentData().insulators, x -> x.id, id);
        return ComponentCardValues.ofContent(TileContent.of(TileContent.Kind.INSULATOR, d), null, 0.0);
    }

    private static ComponentCardValues reflector() {
        ComponentData.ReflectorData d = find(
            ReactorDataRegistry.getComponentData().reflectors,
            x -> x.id,
            "reflector_gold");
        return ComponentCardValues.ofContent(TileContent.of(TileContent.Kind.REFLECTOR, d), null, 0.0);
    }

    private static ComponentCardValues moderator() {
        ComponentData.ModeratorData d = find(
            ReactorDataRegistry.getComponentData().moderators,
            x -> x.id,
            "moderator_graphite");
        return ComponentCardValues.ofContent(TileContent.of(TileContent.Kind.MODERATOR, d), null, 0.0);
    }

    @Test
    public void thermalOutcomesFollowTheEngine() {
        // Scattering applies to what isn't absorbed, and the rest is lost
        ComponentCardValues water = fluid("coolant_distilled_water");
        assertEquals(0.10, water.absorbed, 1e-9);
        assertEquals(0.63, water.scattered, 1e-9);
        assertEquals(0.27, water.lost, 1e-9);
        assertEquals(0.63 / 6.0, water.perSide(), 1e-9);
        assertEquals(0.63 / 12.0, water.perCorner(), 1e-9);

        ComponentCardValues empty = ComponentCardValues.ofContent(TileContent.EMPTY, null, 0.0);
        assertEquals(0.97, empty.lost, 0.001);

        ComponentCardValues foil = insulator("insulator_naquarite");
        assertEquals(1.0, foil.absorbed, 1e-9);
        assertEquals(0.0, foil.scattered, 1e-9);
        assertEquals(0.0, foil.lost, 1e-9);

        assertEquals(1.0, reflector().absorbed + reflector().scattered + reflector().lost, 1e-9);
    }

    @Test
    public void heatActionsReadTheConfig() {
        ComponentCardValues vent = vent();
        assertEquals(HeatAction.VENTS, vent.action);
        assertEquals(512.0, vent.actionHeat, 1e-9);
        assertEquals(128.0, vent.actionRate, 1e-9);
        assertTrue(vent.removesHeat());

        ComponentCardValues exchanger = exchanger();
        assertEquals(HeatAction.EXCHANGES, exchanger.action);
        assertEquals(1024.0, exchanger.actionHeat, 1e-9);
        assertEquals(256.0, exchanger.actionRate, 1e-9);
        assertFalse(exchanger.removesHeat(), "An exchanger only moves heat around");

        ComponentCardValues water = fluid("coolant_distilled_water");
        assertEquals(HeatAction.BOILS, water.action);
        assertEquals(100.0, water.actionRate, 1e-9);
        assertEquals(17.65625, water.actionHeat, 1e-9);

        // IC2 coolant has a boiling point in its config, but it doesn't expand, so it never boils in a hatch
        ComponentCardValues ic2 = fluid("coolant_ic2");
        assertEquals(HeatAction.COOLS_TO_AMBIENT, ic2.action);
        assertEquals(20.0, ic2.actionHeat, 1e-9);

        assertEquals(HeatAction.BLOCKS, insulator("insulator_mica").action);
        assertEquals(0.6, insulator("insulator_mica").dampening, 1e-9);
        assertEquals(HeatAction.BURNS, moderator().action);
        assertEquals(2500.0, moderator().actionRate, 1e-9);
        assertEquals(HeatAction.CONDUCTS_ONLY, reflector().action);
    }

    @Test
    public void aCoolantCellWithoutAnItemHoldsItsConfiguredHeat() {
        ComponentData.CoolantItemData cell = find(
            ReactorDataRegistry.getComponentData().coolantItems,
            x -> x.id,
            "coolant_cell_ic2");
        ComponentCardValues v = ComponentCardValues
            .ofContent(TileContent.of(TileContent.Kind.COOLANT_ITEM, cell), null, 0.0);
        assertEquals(HeatAction.STORES, v.action);
        assertEquals(250_000.0, v.actionHeat, 1e-9);
        assertEquals(2500.0, v.actionRate, 1e-9, "1% of its capacity per second");
    }

    @Test
    public void onlyHatchesHaveADifferentHeatCapacity() {
        EngineParameters p = NuclearSimulationEngine.params();
        assertEquals(p.huPerDegree, vent().minCapacity, 1e-9);
        assertEquals(p.huPerDegree, vent().maxCapacity, 1e-9);

        ComponentCardValues water = fluid("coolant_distilled_water");
        assertTrue(water.inHatch);
        assertEquals(32.0, water.minCapacity, 1e-9);
        assertEquals(8192.0, water.maxCapacity, 1e-9);
    }

    @Test
    public void conductanceFollowsTheConductionLaw() {
        EngineParameters p = NuclearSimulationEngine.params();
        double perK = p.huPerDegree * p.gridConductionMultiplier;
        // A reflector of k 0.3 next to k 0.05
        assertEquals(perK * (0.3 + 0.05) / 2.0, reflector().conductance(), 1e-9);
        // Mica's k of 0.01 lets 40% through
        assertEquals(perK * (0.01 + 0.05) / 2.0 * 0.4, insulator("insulator_mica").conductance(), 1e-9);
        assertEquals(0.0, insulator("insulator_naquarite").conductance(), 1e-9);
    }

    @Test
    public void heatRowStaysStableAtTheHighestConduction() {
        for (float t : settled(fluid("coolant_lead"))) {
            assertTrue(
                t >= HeatRowSimulation.COLD_END - 1e-3 && t <= HeatRowSimulation.HOT_END + 1e-3,
                "Overshot: " + t);
        }
    }

    private static float[] settled(ComponentCardValues v) {
        float[][] frames = v.heatRow();
        return frames[frames.length - 1];
    }

    @Test
    public void heatRowStartsColdAndHoldsItsEnds() {
        float[][] frames = reflector().heatRow();
        assertEquals(HeatRowSimulation.SECONDS * HeatRowSimulation.FRAMES_PER_SECOND + 1, frames.length);
        assertEquals(HeatRowSimulation.HOT_END, frames[0][0], 1e-3);
        for (int i = 1; i < HeatRowSimulation.CELLS; i++) {
            assertEquals(HeatRowSimulation.COLD_END, frames[0][i], 1e-3);
        }
        float[] end = frames[frames.length - 1];
        assertEquals(HeatRowSimulation.HOT_END, end[0], 1e-3);
        assertEquals(HeatRowSimulation.COLD_END, end[HeatRowSimulation.CELLS - 1], 1e-3);
        for (int i = 1; i < HeatRowSimulation.CELLS; i++) {
            assertTrue(end[i] < end[i - 1], "Heat runs downhill along the row");
        }
    }

    @Test
    public void heatRowShowsWhatTheComponentDoes() {
        int m = HeatRowSimulation.MIDDLE;
        float plain = settled(reflector())[m];

        assertTrue(settled(vent())[m] < plain - 100.0f, "A vent keeps its tile far cooler");
        assertTrue(settled(fluid("coolant_distilled_water"))[m] < plain, "Coolant takes heat away");

        float[] foil = settled(insulator("insulator_naquarite"));
        assertEquals(HeatRowSimulation.COLD_END, foil[m], 1e-3, "A total barrier lets nothing in");
        assertEquals(HeatRowSimulation.COLD_END, foil[m + 1], 1e-3, "and nothing past it");
        assertTrue(foil[m - 1] > plain, "Heat piles up in front of it");

        // A moderator's low k carries less heat past the middle than a better conductor does
        assertTrue(settled(moderator())[m + 1] < settled(reflector())[m + 1]);

        // An exchanger pulls its two neighbours and itself towards each other
        float[] even = settled(exchanger());
        float[] plainRow = settled(reflector());
        assertTrue(even[m - 1] - even[m + 1] < plainRow[m - 1] - plainRow[m + 1]);
    }

    @Test
    public void formatting() {
        assertEquals("0%", NEINeutronInteractionHandler.formatOutcome(0.0));
        assertEquals("63%", NEINeutronInteractionHandler.formatOutcome(0.63));
        assertEquals("84%", NEINeutronInteractionHandler.formatOutcome(0.8415));
        assertEquals("5.3%", NEINeutronInteractionHandler.formatOutcome(0.0525));
        assertEquals("<0.1%", NEINeutronInteractionHandler.formatOutcome(0.0004));
        assertEquals("1024", NEINeutronInteractionHandler.formatCardNumber(1024.0));
        assertEquals("14.59", NEINeutronInteractionHandler.formatCardNumber(14.59));
        assertEquals("5000", NEINeutronInteractionHandler.formatCardNumber(5000.25));
        assertEquals("250k", NEINeutronInteractionHandler.formatCardNumber(250_000.0));
        assertEquals("1.5M", NEINeutronInteractionHandler.formatCardNumber(1_500_000.0));
    }
}
