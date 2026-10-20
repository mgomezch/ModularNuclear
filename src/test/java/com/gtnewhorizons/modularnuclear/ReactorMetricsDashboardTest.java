package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.gui.metrics.ReactorCycleSnapshot;
import com.gtnewhorizons.modularnuclear.common.gui.metrics.ReactorMetricsHistory;
import com.gtnewhorizons.modularnuclear.common.gui.metrics.ReactorPlotWidget;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

import io.netty.buffer.Unpooled;

public class ReactorMetricsDashboardTest {

    @BeforeAll
    static void initEnvironment() {
        Thread.currentThread()
            .setName("Server thread");
        try {
            cpw.mods.fml.common.Loader mockLoader = org.mockito.Mockito.mock(cpw.mods.fml.common.Loader.class);
            org.mockito.Mockito.when(mockLoader.getCallableCrashInformation())
                .thenReturn(org.mockito.Mockito.mock(cpw.mods.fml.common.ICrashCallable.class));
            java.lang.reflect.Field f = cpw.mods.fml.common.Loader.class.getDeclaredField("instance");
            f.setAccessible(true);
            if (f.get(null) == null) {
                f.set(null, mockLoader);
            }
        } catch (Throwable ignored) {}

        try {
            net.minecraft.init.Bootstrap.func_151354_b();
        } catch (Throwable ignored) {}
    }

    @Test
    void testQuartilesEmptyAndSingle() {
        float[] empty = ReactorCycleSnapshot.calculateQuartiles(null);
        assertEquals(0f, empty[0]);
        assertEquals(0f, empty[2]);
        assertEquals(0f, empty[4]);

        float[] single = ReactorCycleSnapshot.calculateQuartiles(List.of(250.0));
        assertEquals(250f, single[0], 0.001f); // min
        assertEquals(250f, single[1], 0.001f); // Q1
        assertEquals(250f, single[2], 0.001f); // median
        assertEquals(250f, single[3], 0.001f); // Q3
        assertEquals(250f, single[4], 0.001f); // max
        assertEquals(250f, single[5], 0.001f); // avg
    }

    @Test
    void testQuartilesFiveValues() {
        List<Double> values = Arrays.asList(50.0, 10.0, 40.0, 20.0, 30.0);
        float[] stats = ReactorCycleSnapshot.calculateQuartiles(values);
        assertEquals(10f, stats[0], 0.001f); // min
        assertEquals(20f, stats[1], 0.001f); // Q1 (rank 1.0)
        assertEquals(30f, stats[2], 0.001f); // median (rank 2.0)
        assertEquals(40f, stats[3], 0.001f); // Q3 (rank 3.0)
        assertEquals(50f, stats[4], 0.001f); // max
        assertEquals(30f, stats[5], 0.001f); // avg
    }

    @Test
    void testQuartilesEvenCountInterpolation() {
        List<Double> values = Arrays.asList(10.0, 20.0, 30.0, 40.0);
        float[] stats = ReactorCycleSnapshot.calculateQuartiles(values);
        assertEquals(10f, stats[0], 0.001f); // min
        assertEquals(17.5f, stats[1], 0.001f); // Q1: 0.25 * 3 = 0.75 rank -> 10 + 0.75 * 10
        assertEquals(25.0f, stats[2], 0.001f); // median: 0.5 * 3 = 1.5 rank -> 20 + 0.5 * 10
        assertEquals(32.5f, stats[3], 0.001f); // Q3: 0.75 * 3 = 2.25 rank -> 30 + 0.25 * 10
        assertEquals(40.0f, stats[4], 0.001f); // max
        assertEquals(25.0f, stats[5], 0.001f); // avg
    }

    @Test
    void testHistorySlidingWindowCapacity() {
        ReactorMetricsHistory history = new ReactorMetricsHistory();
        for (int i = 0; i < 110; i++) {
            ReactorCycleSnapshot s = new ReactorCycleSnapshot();
            s.cycleIndex = i;
            history.addSnapshot(s);
        }

        assertEquals(100, history.size());
        List<ReactorCycleSnapshot> list = history.getSnapshots();
        assertEquals(10, list.get(0).cycleIndex);
        assertEquals(109, list.get(99).cycleIndex);
    }

    @Test
    void testSnapshotPacketBufferSerialization() {
        ReactorCycleSnapshot original = ReactorCycleSnapshot.create(
            42L,
            Arrays.asList(100.0, 200.0, 300.0, 400.0, 500.0),
            Arrays.asList(50.0, 80.0, 100.0),
            1200,
            45000,
            38000,
            8192L,
            128000);

        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        original.writeToBuffer(buf);

        ReactorCycleSnapshot deserialized = ReactorCycleSnapshot.readFromBuffer(buf);
        assertEquals(original.cycleIndex, deserialized.cycleIndex);
        assertEquals(original.tempMin, deserialized.tempMin, 0.001f);
        assertEquals(original.tempQ1, deserialized.tempQ1, 0.001f);
        assertEquals(original.tempMedian, deserialized.tempMedian, 0.001f);
        assertEquals(original.tempQ3, deserialized.tempQ3, 0.001f);
        assertEquals(original.tempMax, deserialized.tempMax, 0.001f);
        assertEquals(original.tempAvg, deserialized.tempAvg, 0.001f);

        assertEquals(original.reactivityMin, deserialized.reactivityMin, 0.001f);
        assertEquals(original.reactivityMedian, deserialized.reactivityMedian, 0.001f);
        assertEquals(original.reactivityMax, deserialized.reactivityMax, 0.001f);

        assertEquals(original.escapedNeutrons, deserialized.escapedNeutrons);
        assertEquals(original.fastNeutronsProduced, deserialized.fastNeutronsProduced);
        assertEquals(original.thermalNeutronsAbsorbed, deserialized.thermalNeutronsAbsorbed);
        assertEquals(original.directEU, deserialized.directEU);
        assertEquals(original.coolantOutput, deserialized.coolantOutput);
    }

    @Test
    void testMetricsHistoryPacketBufferSerialization() {
        ReactorMetricsHistory original = new ReactorMetricsHistory();
        for (int i = 0; i < 25; i++) {
            ReactorCycleSnapshot s = new ReactorCycleSnapshot();
            s.cycleIndex = i + 1;
            s.tempMedian = 300f + i;
            s.directEU = i * 100L;
            original.addSnapshot(s);
        }

        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        ReactorMetricsHistory.writeToBuffer(buf, original);

        ReactorMetricsHistory deserialized = ReactorMetricsHistory.readFromBuffer(buf);
        assertEquals(25, deserialized.size());
        assertEquals(
            1L,
            deserialized.getSnapshots()
                .get(0).cycleIndex);
        assertEquals(
            25L,
            deserialized.getSnapshots()
                .get(24).cycleIndex);
        assertEquals(
            324f,
            deserialized.getSnapshots()
                .get(24).tempMedian,
            0.001f);
        assertEquals(
            2400L,
            deserialized.getSnapshots()
                .get(24).directEU);
    }

    @Test
    void testComputerMonitorCoverGating() {
        MTENuclearReactor reactor = new MTENuclearReactor("mptr_test");

        // Initially no cover
        assertFalse(reactor.hasComputerMonitorCover());

        // Simulate cover attached
        reactor.mSimulateComputerMonitorCover = true;
        assertTrue(reactor.hasComputerMonitorCover());

        // Simulate cycle
        NuclearSimulationEngine.SimulationResult res = new NuclearSimulationEngine.SimulationResult();
        res.totalNeutronsGenerated = 5000;
        res.neutronsEscaped = 200;
        res.thermalNeutronsAbsorbed = 4800;

        reactor.gridSize = 3;
        reactor.recordMetricsCycleSnapshot(res, 1.0);

        assertEquals(
            1,
            reactor.getMetricsHistory()
                .size());
        ReactorCycleSnapshot s = reactor.getMetricsHistory()
            .getSnapshots()
            .get(0);
        assertEquals(5000, s.fastNeutronsProduced);
        assertEquals(200, s.escapedNeutrons);

        // When cover is removed, history is cleared
        reactor.mSimulateComputerMonitorCover = false;
        assertFalse(reactor.hasComputerMonitorCover());

        if (!reactor.hasComputerMonitorCover() && !reactor.getMetricsHistory()
            .isEmpty()) {
            reactor.getMetricsHistory()
                .clear();
        }
        assertEquals(
            0,
            reactor.getMetricsHistory()
                .size());
    }

    @Test
    void testNonPersistenceOfMetricsHistory() {
        MTENuclearReactor reactor = new MTENuclearReactor("mptr_test_nbt");
        reactor.mSimulateComputerMonitorCover = true;

        NuclearSimulationEngine.SimulationResult res = new NuclearSimulationEngine.SimulationResult();
        res.totalNeutronsGenerated = 1000;
        reactor.gridSize = 3;
        reactor.recordMetricsCycleSnapshot(res, 1.0);
        assertEquals(
            1,
            reactor.getMetricsHistory()
                .size());

        // Save NBT
        NBTTagCompound tag = new NBTTagCompound();
        reactor.saveNBTData(tag);

        // Verify metrics are not written to NBT
        assertFalse(tag.hasKey("mMetricsHistory"));
        assertFalse(tag.hasKey("metricsHistory"));
        assertFalse(tag.hasKey("mCumulativeNeutrons"));
    }

    @Test
    void testMetricUnitsAndScaling() {
        assertEquals("°C", ReactorPlotWidget.Metric.CORE_TEMP.getUnit());
        assertEquals("%", ReactorPlotWidget.Metric.REACTIVITY.getUnit());
        assertEquals("n/s", ReactorPlotWidget.Metric.FAST_NEUTRONS.getUnit());
        assertEquals("n/s", ReactorPlotWidget.Metric.THERMAL_NEUTRONS.getUnit());
        assertEquals("n/s", ReactorPlotWidget.Metric.ESCAPED_NEUTRONS.getUnit());
        assertEquals("EU/s", ReactorPlotWidget.Metric.DIRECT_EU.getUnit());
        assertEquals("L/s", ReactorPlotWidget.Metric.COOLANT_OUTPUT.getUnit());

        ReactorCycleSnapshot s = new ReactorCycleSnapshot();
        s.directEU = 125L;
        s.fastNeutronsProduced = 5000;
        s.coolantOutput = 32000;

        // Direct EU per tick (125 EU/t) scales to 20x for per-second output (2500 EU/s)
        float directEUSec = ReactorPlotWidget.Metric.DIRECT_EU.getScalarVal()
            .apply(s);
        assertEquals(2500f, directEUSec, 0.001f);
    }

    @Test
    void testPercentageMetricRange() {
        // Test with varying reactivity snapshots
        ReactorCycleSnapshot s1 = new ReactorCycleSnapshot();
        s1.reactivityMin = 10f;
        s1.reactivityMedian = 50f;
        s1.reactivityMax = 95f;

        float[] bounds = ReactorPlotWidget.calculateBounds(ReactorPlotWidget.Metric.REACTIVITY, List.of(s1));
        // Vertical axis must strictly be exactly 0 to 100% (range = 100.0f, not 110.0f)
        assertEquals(0.0f, bounds[0], 0.001f, "minVal must be 0");
        assertEquals(100.0f, bounds[1], 0.001f, "maxVal must be 100");
        assertEquals(100.0f, bounds[2], 0.001f, "range must be exactly 100 (never 110%)");

        // Even with an empty list or 0 reactivity
        float[] emptyBounds = ReactorPlotWidget.calculateBounds(ReactorPlotWidget.Metric.REACTIVITY, List.of());
        assertEquals(0.0f, emptyBounds[0], 0.001f);
        assertEquals(100.0f, emptyBounds[1], 0.001f);
        assertEquals(100.0f, emptyBounds[2], 0.001f);
    }
}
