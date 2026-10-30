package com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

public class OptionTableTest {

    private static final class Ctx {

        int n;
        boolean on;
        String s;
        boolean ran;
        Integer trace;
    }

    private static OptionTable<Ctx> table() {
        return new OptionTable<Ctx>().value(OptionTable.INT, (c, v) -> c.n = v, "fission_heat", "fh")
            .toggle((c, v) -> c.on = v, "strict")
            .value(OptionTable.STRING, (c, v) -> c.s = v, "layout")
            .action(c -> c.ran = true, "help")
            .optionalValue(OptionTable.INT, c -> c.trace = 0, (c, v) -> c.trace = v, "trace");
    }

    @Test
    void testSpellingsMatchOneOption() {
        for (String name : new String[] { "--fission-heat", "fission_heat", "fissionheat", "FISSION-HEAT", "fh" }) {
            Ctx c = new Ctx();
            table().apply(c, name, "7");
            assertEquals(7, c.n, name);
        }
    }

    @Test
    void testCommandLine() {
        Ctx c = new Ctx();
        table()
            .parseArgs(c, new String[] { "--fission-heat", "12", "--strict", "--layout", "A,B", "--trace", "--help" });
        assertEquals(12, c.n);
        assertTrue(c.on);
        assertEquals("A,B", c.s);
        assertEquals(0, c.trace, "--trace without a value");
        assertTrue(c.ran);

        Ctx d = new Ctx();
        d.on = true;
        table().parseArgs(d, new String[] { "--trace", "20", "--no-strict" });
        assertEquals(20, d.trace);
        assertFalse(d.on);
    }

    @Test
    void testTokensAndBadValues() {
        Ctx c = new Ctx();
        OptionTable<Ctx> t = table().onBadValue((name, value) -> {});
        t.parseTokens(c, "fission_heat=5 strict=1 layout=X unknown=3 fission_heat=oops", "\\s+");
        assertEquals(5, c.n, "a malformed value leaves the earlier one");
        assertTrue(c.on);
        assertEquals("X", c.s);
    }

    @Test
    void testDuplicateNamesAreRejected() {
        assertThrows(
            IllegalStateException.class,
            () -> new OptionTable<Ctx>().toggle((c, v) -> {}, "strict")
                .toggle((c, v) -> {}, "STRICT"));
    }

    @Test
    void testSimOptionsBuildAndApply() {
        NuclearSimulationEngine.resetDefaultParameters();
        RunConfig cfg = RunConfig.batchLine();
        SimOptions.table(true)
            .parseTokens(
                cfg,
                "ticks=50 tier=zpm fission_heat=1234 phe_count=8 circuit=2 loop_fluid=SODIUM seed=3",
                "\\s+");
        assertEquals(50, cfg.ticks);
        assertEquals(NuclearSimulationEngine.PIPE_TIER_QUANTIUM, (int) cfg.tier);
        assertEquals(1234.0, NuclearSimulationEngine.params().fissionHeatPerNeutron, 1e-9);
        assertEquals("phe_count=8,circuit=2,loop_fluid=SODIUM,", cfg.loopSettings.toString());
        assertEquals(3L, (long) cfg.seed);
        NuclearSimulationEngine.resetDefaultParameters();
    }
}
