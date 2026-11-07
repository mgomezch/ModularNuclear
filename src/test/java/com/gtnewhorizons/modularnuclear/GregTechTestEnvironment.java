package com.gtnewhorizons.modularnuclear;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import gregtech.api.enums.GTValues;
import gregtech.api.net.IGT_NetworkHandler;
import gregtech.api.util.GTLog;

/**
 * What GregTech has in a game and not in a unit test, for every test: a network to play sounds on, and an explosion
 * log (which needs a machine's world and its dimension). Registered for every test through {@code META-INF/services}.
 */
public class GregTechTestEnvironment implements BeforeEachCallback, AfterEachCallback {

    private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace
        .create(GregTechTestEnvironment.class);

    @Override
    public void beforeEach(ExtensionContext context) {
        if (GTValues.NW == null) GTValues.NW = Mockito.mock(IGT_NetworkHandler.class);
        context.getStore(NS)
            .put("log", Mockito.mockStatic(GTLog.class));
    }

    @Override
    public void afterEach(ExtensionContext context) {
        MockedStatic<?> log = context.getStore(NS)
            .remove("log", MockedStatic.class);
        if (log != null) log.close();
    }
}
