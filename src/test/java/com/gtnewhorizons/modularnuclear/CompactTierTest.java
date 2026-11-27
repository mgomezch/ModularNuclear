package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasPressureStage;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasStageModels;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.CompactLoopMachines.Kind;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.CompactStats;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.CompactTier;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.MTECompactCompressor;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.MTECompactLoopMachine;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.gas.GasMachineParts;

/** The compact loop machines' tier table and what follows from it. See Note [Compact loop machines] */
public class CompactTierTest {

    @BeforeAll
    static void init() {
        TestEnvironmentHelper.init();
    }

    @Test
    public void everyTierIsStrongerThanTheLastAndWeakerThanAMultiblock() {
        CompactTier last = null;
        for (CompactTier t : CompactTier.all()) {
            String at = "at " + t.voltageName();
            // A multiblock compressor's shaft gets all of its EU, at a better isentropic efficiency, with no
            // built-in ratio
            assertTrue(t.driveEfficiency < 0.5, at);
            assertTrue(t.compressorEfficiency < GasMachineParts.COMPRESSOR_ISENTROPIC_EFFICIENCY, at);
            assertTrue(t.expanderEfficiency < GasMachineParts.EXPANDER_ISENTROPIC_EFFICIENCY, at);
            assertTrue(t.builtInRatio > 1.0, at);
            assertTrue(t.fillRateFactor < 1.0, at);
            if (last != null) {
                assertEquals(last.euPerTick * 4, t.euPerTick, at);
                assertTrue(t.maxPressureBar > last.maxPressureBar, at);
                assertTrue(t.driveEfficiency > last.driveEfficiency, at);
                assertTrue(t.compressorEfficiency > last.compressorEfficiency, at);
                assertTrue(t.builtInRatio < last.builtInRatio, at);
                assertTrue(t.displacement > last.displacement, at);
                assertTrue(t.radiatorConductance > last.radiatorConductance, at);
                assertTrue(t.bathSlots > last.bathSlots, at);
                assertTrue(t.maxLoopVolumeLiters > last.maxLoopVolumeLiters, at);
                assertTrue(t.fillRateFactor > last.fillRateFactor, at);
            }
            last = t;
        }
        assertEquals(120, CompactTier.of(2).euPerTick);
        assertEquals(
            "MV",
            CompactTier.of(2)
                .voltageName());
        assertEquals(
            "LuV",
            CompactTier.of(6)
                .voltageName());
    }

    /** A loop of six machines and forty blocks of medium pipe fits its tier's pump. */
    @Test
    public void aPumpRunsALoopOfItsTiersMachines() {
        for (CompactTier t : CompactTier.all()) {
            double liters = 6 * t.volumeM3 * 1000.0 + 40 * Math.PI * 0.05 * 0.05 * 1000.0;
            assertTrue(liters < t.maxLoopVolumeLiters, t.voltageName() + ": " + liters);
            // And a loop of the next tier's machines does not
            if (t.tier < CompactTier.LAST) {
                assertTrue(6 * CompactTier.of(t.tier + 1).volumeM3 * 1000.0 * 4.0 > t.maxLoopVolumeLiters);
            }
        }
    }

    @Test
    public void aCompressorWorksAtLeastItsBuiltInRatioAndStopsPastIt() {
        CompactTier t = CompactTier.of(2);
        GasStageModels.Compressor model = MTECompactCompressor.model(t);
        GasProperty gas = GasProperty.R134A;
        GasPressureStage.Context c = new GasPressureStage.Context();
        c.gas = gas;
        c.loopRunning = true;
        c.dt = 0.05;
        c.pressureIn = 3e5;
        c.enthalpyIn = gas.enthalpy(c.pressureIn, 283.15);
        gas.state(c.pressureIn, c.enthalpyIn, c.in);
        // On a loop with no pressure difference it still does its built-in ratio's work
        c.pressureOut = c.pressureIn;
        double idleWork = model.specificWork(c);
        assertTrue(idleWork > 0.0);
        c.pressureOut = c.pressureIn * t.builtInRatio;
        assertEquals(idleWork, model.specificWork(c), idleWork * 1e-9);
        assertEquals(1.0, model.unloading(c), 1e-12);
        // Past it the work grows and the speed falls, to nothing at one and a half times it
        c.pressureOut = c.pressureIn * t.builtInRatio * 1.25;
        assertTrue(model.specificWork(c) > idleWork);
        assertEquals(0.5, model.unloading(c), 1e-9);
        c.pressureOut = c.pressureIn * t.builtInRatio * 1.5;
        assertEquals(0.0, model.unloading(c), 1e-9);
        // A multiblock's model has neither
        GasStageModels.Compressor multi = new GasStageModels.Compressor(20.0, 0.75, 0.04);
        assertEquals(1.0, multi.unloading(c), 0.0);
        c.pressureOut = c.pressureIn;
        assertEquals(0.0, multi.specificWork(c), 0.0);
    }

    @Test
    public void theLoopRunsAcrossTheFrontLeftToRight() {
        for (ForgeDirection front : new ForgeDirection[] { ForgeDirection.NORTH, ForgeDirection.SOUTH,
            ForgeDirection.EAST, ForgeDirection.WEST }) {
            ForgeDirection[] choices = MTECompactLoopMachine.flowChoices(front);
            assertEquals(4, choices.length);
            Set<ForgeDirection> seen = new HashSet<>();
            for (ForgeDirection d : choices) {
                assertFalse(d == front || d == front.getOpposite(), front + ": " + d);
                seen.add(d);
            }
            assertEquals(4, seen.size());
        }
        // Looking at a north-facing machine's front one looks south, and east is on the left
        assertEquals(ForgeDirection.EAST, MTECompactLoopMachine.flowChoices(ForgeDirection.NORTH)[0]);
    }

    @Test
    public void everyStatRowHasItsLabel() throws IOException {
        String lang = new String(
            Files.readAllBytes(Paths.get("src/main/resources/assets/modularnuclear/lang/en_US.lang")),
            StandardCharsets.UTF_8);
        for (Kind kind : Kind.values()) {
            assertTrue(lang.contains("modularnuclear.nei.compact.kind." + kind.name + "="), kind.name);
            assertTrue(lang.contains("modularnuclear.nei.group." + kind.name + "="), kind.name);
            for (int tier = CompactTier.FIRST; tier <= CompactTier.LAST; tier++) {
                List<String[]> rows = CompactStats.rows(kind, tier);
                assertTrue(rows.size() >= 4 && rows.size() <= 8, kind + " " + tier + ": " + rows.size());
                for (String[] row : rows) {
                    assertTrue(lang.contains(row[0] + "="), row[0]);
                    assertFalse(row[1].isEmpty(), row[0]);
                }
            }
        }
    }
}
