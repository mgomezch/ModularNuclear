package com.gtnewhorizons.modularnuclear.common.coolantloop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A loop id is the same however it was made, and its string form reads back to it. */
class LoopIdTest {

    @Test
    void positionedIdsEqualTheirStringForm() {
        LoopId pipe = LoopId.at("pipe", 10, 64, -3);
        assertEquals("pipe_10_64_-3", pipe.toString());
        assertEquals(pipe, LoopId.of("pipe_10_64_-3"));
        assertEquals(
            pipe.hashCode(),
            LoopId.of("pipe_10_64_-3")
                .hashCode());
        assertTrue(pipe.isKind("pipe"));
        assertEquals(LoopId.at("manifold_group", 1, 2, 3), LoopId.of("manifold_group_1_2_3"));
        assertEquals(LoopId.at("coolant_bath", 0, 5, 0), LoopId.of("coolant_bath_0_5_0"));
    }

    @Test
    void differentPartsDiffer() {
        assertNotEquals(LoopId.at("pipe", 1, 2, 3), LoopId.at("pipe", 1, 2, 4));
        assertNotEquals(LoopId.at("pipe", 1, 2, 3), LoopId.at("heater", 1, 2, 3));
        assertNotEquals(LoopId.of("pipe"), LoopId.at("pipe", 0, 0, 0));
    }

    @Test
    void otherNamesAreNamesOnly() {
        LoopId name = LoopId.of("cp1_pipe_3");
        assertEquals("cp1_pipe_3", name.toString());
        assertFalse(name.isKind("cp1"));
        assertEquals(name, LoopId.of("cp1_pipe_3"));
        assertFalse(
            LoopId.of("pipe_1_2_x")
                .isKind("pipe"));
    }
}
