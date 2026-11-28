package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.coolantloop.ConvectiveHeatTransferModel;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasLoopEngine;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasPressureStage;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasStageModels;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.SegmentHeat;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.CompactTier;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.MTECompactBath;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.MTECompactCompressor;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.gas.GasMachineParts;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.util.CoolantBathSlot;

/**
 * A compact refrigeration loop of each tier cooling hot ingots, against the Vacuum Freezer: the figures Note [Compact
 * loop machines] gives. The loop is the machines' own physics (CompactTier, GasStageModels, MTECompactBath.exchange)
 * without a world: a compressor, a radiator in air at 20 °C, a thermostatic valve and a bath kept full of ingots.
 */
public class CompactLoopBalanceTest {

    static final double C = 273.15, DT = 0.05;
    static double LIQUID_FRACTION = System.getenv("COMPACT_LIQUID") != null
        ? Double.parseDouble(System.getenv("COMPACT_LIQUID"))
        : CompactTier.LIQUID_FRACTIONS[0];
    static boolean DEBUG = System.getenv("COMPACT_DEBUG") != null;
    static final double JOULES_PER_EU = GasMachineParts.SHAFT_JOULES_PER_EU;

    @BeforeAll
    static void init() {
        TestEnvironmentHelper.init();
    }

    /** An ingot as the freezer sees it: what it spends on it, and how long it takes. */
    static final class Ingot {

        final String name;
        final double freezerEu, freezerSeconds, hotC;

        Ingot(String name, double freezerEu, double freezerTicks, double hotC) {
            this.name = name;
            this.freezerEu = freezerEu;
            this.freezerSeconds = freezerTicks / 20.0;
            this.hotC = hotC;
        }

        CoolantBathSlot slot() {
            double heat = freezerEu * JOULES_PER_EU * 0.2;
            double capacity = heat / (hotC - 50.0);
            double area = capacity / (ConvectiveHeatTransferModel.DEFAULT.getHMax() * freezerSeconds);
            return new CoolantBathSlot(
                new ItemStack(Items.iron_ingot),
                hotC,
                50.0,
                0,
                1000,
                capacity,
                area,
                false,
                true,
                true,
                new ItemStack(Items.iron_ingot));
        }
    }

    static final Ingot KANTHAL = new Ingot("Kanthal", 16_200, 135, 1527);
    static final Ingot SILICON_SG = new Ingot("Silicon SG", 35_280, 294, 2000);
    static final Ingot TITANIUM = new Ingot("Titanium", 69_120, 144, 1668);
    static final Ingot TUNGSTENSTEEL = new Ingot("Tungstensteel", 685_440, 357, 2727);
    static final Ingot NAQUADAH = new Ingot("Naquadah", 2_457_600, 320, 4727);

    static final class Result {

        double ingots, compressorEu, fanEu, seconds, lowBar, highBar;

        double euPerIngot() {
            return (compressorEu + fanEu) / Math.max(1e-9, ingots);
        }
    }

    /** Runs a tier's loop on an endless supply of one kind of ingot, tallying after a warm-up. */
    static Result run(CompactTier t, Ingot ingot, double seconds) {
        GasProperty gas = GasProperty.R134A;
        GasLoopEngine e = new GasLoopEngine(gas);
        List<LoopSegment> l = GasLoopEngineTest.ring("comp", "p1", "rad", "p2", "valve", "p3", "bath", "p4");
        for (LoopSegment s : l) {
            if (!s.getId()
                .startsWith("p")) s.setCustomVolumeM3(t.volumeM3);
            e.addSegment(s);
        }
        LoopSegment rad = GasLoopEngineTest.find(e, "rad"), bath = GasLoopEngineTest.find(e, "bath");
        GasStageModels.Compressor model = MTECompactCompressor.model(t);
        double[] work = { 0.0 }, opening = { 0.0 };
        e.bindStage(
            GasLoopEngineTest.find(e, "comp")
                .getKey(),
            new GasPressureStage() {

                @Override
                public void computeStage(Context c) {
                    double speed = model.speedForPower(c, t.euPerTick * t.shaftJoulesPerEu() * 20.0);
                    model.compute(c, speed * model.unloading(c));
                    work[0] += c.shaftPowerWatts * c.dt;
                }

                @Override
                public boolean isPassive() {
                    return false;
                }
            });
        GasStageModels.Thermostat thermostat = new GasStageModels.Thermostat();
        GasStageModels.Valve valve = new GasStageModels.Valve(t.valveArea);
        e.bindStage(
            GasLoopEngineTest.find(e, "valve")
                .getKey(),
            c -> {
                opening[0] = c.loopRunning ? thermostat.opening(c.downstreamExitSuperheatK, c.dt) : 1.0;
                valve.compute(c, opening[0]);
            });
        double ambient = 20 + C;
        e.equilibrate(
            GasLoopEngine.requiredChargeKg(gas, e.getTotalVolumeM3(), ambient, LIQUID_FRACTION, 10e5),
            ambient);
        e.setPumpPowered(true);
        CoolantBathSlot[] slots = new CoolantBathSlot[t.bathSlots];
        Result r = new Result();
        int steps = (int) (seconds / DT), warm = steps / 3;
        double work0 = 0.0;
        for (int i = 0; i < steps; i++) {
            for (int s = 0; s < slots.length; s++) {
                if (slots[s] == null) slots[s] = ingot.slot();
            }
            e.step(DT);
            assertFalse(e.isRuptured(), () -> t.voltageName() + ": " + e.getFailureReason());
            double duty = CompactTier.fanDuty(rad.getCurrentTemperatureCelsius() + C, ambient);
            SegmentHeat.exchangeThrough(gas, rad, ambient, t.radiatorConductance * duty, DT);
            if (i >= warm) r.fanEu += Math.max(1L, Math.round(t.radiatorFanEu * duty));
            MTECompactBath.exchange(slots, gas, bath, DT);
            if (i == warm) work0 = work[0];
            if (DEBUG && i % 2000 == 0) {
                StringBuilder sb = new StringBuilder(String.format("%s t=%.0f", t.voltageName(), i * DT));
                for (LoopSegment s : e.getSegments()) sb.append(
                    String.format(
                        " | %s %.2fbar %.0fC x%.2f %.0fkg/s %.1fkg",
                        s.getId(),
                        s.getInletPressureBar(),
                        s.getCurrentTemperatureCelsius(),
                        s.getGasQuality(),
                        s.getGasMassFlow(),
                        s.getGasMassKg()));
                sb.append(String.format(" | slot0 %.0fC valve %.3f", slots[0].currentTemp, opening[0]));
                System.out.println(sb);
            }
            for (int s = 0; s < slots.length; s++) {
                if (slots[s].isAutoEjectReady()) {
                    slots[s] = null;
                    if (i >= warm) r.ingots++;
                }
            }
        }
        r.seconds = (steps - warm) * DT;
        r.compressorEu = (work[0] - work0) / t.shaftJoulesPerEu();
        double[] zones = e.getZonePressures();
        r.lowBar = Math.min(zones[0], zones[zones.length - 1]) / 1e5;
        r.highBar = Math.max(zones[0], zones[zones.length - 1]) / 1e5;
        return r;
    }

    static Ingot ingotOf(CompactTier t) {
        switch (t.tier) {
            case 2:
                return SILICON_SG;
            case 3:
                return TITANIUM;
            case 4:
                return TUNGSTENSTEEL;
            default:
                return NAQUADAH;
        }
    }

    @Test
    public void debugOneTier() {
        if (!DEBUG) return;
        Result r = run(CompactTier.of(DEBUG ? Integer.parseInt(System.getenv("COMPACT_DEBUG")) : 2), KANTHAL, 300.0);
        System.out.println("ingots " + r.ingots);
    }

    @Test
    public void eachTierBeatsTheFreezerByItsShare() {
        double[] least = { 0.85, 1.45, 1.95, 2.4, 2.4 }, most = { 1.3, 2.0, 2.6, 3.2, 3.3 };
        StringBuilder report = new StringBuilder();
        boolean ok = true;
        for (CompactTier t : CompactTier.all()) {
            for (Ingot ingot : new Ingot[] { KANTHAL, ingotOf(t) }) {
                Result r = run(t, ingot, 600.0);
                double efficiency = ingot.freezerEu / r.euPerIngot();
                double pace = r.ingots / r.seconds * ingot.freezerSeconds;
                report.append(
                    String.format(
                        "%s %-13s: %.2f x the freezer's EU efficiency, %.2f x its pace, %.0f EU/t, %.1f/%.1f bar%n",
                        t.voltageName(),
                        ingot.name,
                        efficiency,
                        pace,
                        (r.compressorEu + r.fanEu) / (r.seconds * 20.0),
                        r.lowBar,
                        r.highBar));
                int i = t.tier - CompactTier.FIRST;
                if (ingot != KANTHAL && (efficiency < least[i] || efficiency > most[i])) ok = false;
            }
        }
        System.out.println(report);
        assertTrue(ok, report.toString());
    }
}
