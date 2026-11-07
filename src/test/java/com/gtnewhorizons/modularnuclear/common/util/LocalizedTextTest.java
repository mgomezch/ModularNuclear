package com.gtnewhorizons.modularnuclear.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class LocalizedTextTest {

    @Test
    void nestedLinesFormatInEnglish() {
        LocalizedText inner = LocalizedText.of("coolantloops.crawler.no_tile", 1, 2, 3);
        LocalizedText outer = LocalizedText.of("coolantloops.pump.status.loop_disconnect", inner);
        String english = outer.english();
        assertEquals(
            String.format(EnglishLang.pattern("coolantloops.pump.status.loop_disconnect"), inner.english()),
            english);
    }

    @Test
    void savedLinesComeBackEqual() {
        LocalizedText text = LocalizedText.of(
            "coolantloops.pump.status.catastrophic_melt",
            1234.5,
            1000L,
            LocalizedText.literal("Tungstensteel"),
            true);
        assertEquals(text, LocalizedTextNbt.read(LocalizedTextNbt.write(text)));
        assertEquals(
            LocalizedText.literal("as is"),
            LocalizedTextNbt.read(LocalizedTextNbt.write(LocalizedText.literal("as is"))));
    }

    @Test
    void integersAndLongsAreTheSameArgument() {
        assertEquals(LocalizedText.of("k", 5), LocalizedText.of("k", 5L));
        assertNotEquals(LocalizedText.of("k", 5), LocalizedText.of("k", 6));
    }

    @Test
    void unknownKeysShowThemselves() {
        assertEquals(
            "no.such.key",
            LocalizedText.of("no.such.key", 1)
                .english());
    }
}
