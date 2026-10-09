package com.gtnewhorizons.modularnuclear.common.quest;

import java.util.UUID;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.modularnuclear.ModularNuclear;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.hfstudio.bqapi.BQApi;
import com.hfstudio.bqapi.api.builder.Chapters;
import com.hfstudio.bqapi.api.definition.ChapterDefinition;

public class ModularNuclearQuestIntegrationImpl {

    // GTNH "Powerful Nuclear Physics" chapter UUID: 00000000-0000-0000-0000-00000000001a (high: 0, low: 26)
    public static final UUID CHAPTER_NUCLEAR_PHYSICS_UUID = UUID.fromString("00000000-0000-0000-0000-00000000001a");
    public static final String CHAPTER_ID = "modularnuclear";

    public static void init() {
        ItemStack iconStack = ModMetaTileEntities.reactor != null ? ModMetaTileEntities.reactor.copy() : null;

        ChapterDefinition chapter;
        if (iconStack != null) {
            chapter = Chapters.chapter(CHAPTER_ID)
                .orderAfter(CHAPTER_NUCLEAR_PHYSICS_UUID)
                .icon(iconStack)
                .build();
        } else {
            chapter = Chapters.chapter(CHAPTER_ID)
                .orderAfter(CHAPTER_NUCLEAR_PHYSICS_UUID)
                .icon("modularnuclear:modularNuclearReactor", 1, 0)
                .build();
        }

        BQApi.register(chapter);
        ModularNuclear.LOG.info(
            "Registered BetterQuesting chapter '{}' (UUID: {}) ordered after Powerful Nuclear Physics",
            CHAPTER_ID,
            chapter.getUuid());
    }
}
