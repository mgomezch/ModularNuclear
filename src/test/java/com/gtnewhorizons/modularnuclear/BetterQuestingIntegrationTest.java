package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.quest.ModularNuclearQuestIntegrationImpl;
import com.hfstudio.bqapi.BQApi;
import com.hfstudio.bqapi.api.definition.ChapterDefinition;
import com.hfstudio.bqapi.runtime.BQStableIdFactory;

import betterquesting.api.utils.UuidConverter;

public class BetterQuestingIntegrationTest {

    @Test
    public void testChapterRegistrationAndProperties() {
        ModularNuclearQuestIntegrationImpl.init();

        UUID expectedNuclearPhysicsUuid = ModularNuclearQuestIntegrationImpl.CHAPTER_NUCLEAR_PHYSICS_UUID;
        UUID expectedChapterUuid = BQStableIdFactory.chapter("modularnuclear");

        Optional<ChapterDefinition> optChapter = BQApi.getChapter("modularnuclear");
        assertTrue(optChapter.isPresent(), "modularnuclear chapter should be registered");
        ChapterDefinition chapter = optChapter.get();

        assertEquals("modularnuclear", chapter.getId());
        assertEquals(expectedChapterUuid, chapter.getUuid());
        assertEquals(UuidConverter.encodeUuid(expectedNuclearPhysicsUuid), chapter.getOrderAfterEncoded());
        assertNotNull(chapter.getIconNbt());
        assertTrue(
            chapter.getPlacements()
                .isEmpty(),
            "Chapter must be empty (no pre-filled quests)");
    }
}
