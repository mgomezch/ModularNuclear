package com.gtnewhorizons.modularnuclear.common.quest;

import com.gtnewhorizons.modularnuclear.ModularNuclear;

import cpw.mods.fml.common.Loader;

public class ModularNuclearQuestIntegration {

    private static boolean initialized = false;

    public static void init() {
        if (initialized) {
            return;
        }
        boolean bqapiLoaded = Loader.isModLoaded("bqapi");
        if (!bqapiLoaded) {
            ModularNuclear.LOG.info("BetterQuestingAPI (bqapi) is not loaded; skipping questbook page registration.");
            return;
        }
        try {
            ModularNuclearQuestIntegrationImpl.init();
            initialized = true;
        } catch (LinkageError t) {
            ModularNuclear.LOG.error("Failed to register Modular Nuclear questbook chapter via BetterQuestingAPI", t);
        }
    }
}
