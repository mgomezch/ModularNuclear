package com.gtnewhorizons.modularnuclear;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

/**
 * Gives every test its own engine parameters, a copy of the installed set: whatever a test changes (or installs by
 * loading a configuration) is gone when it ends, so no test depends on which ran before it. Registered for every test
 * through {@code META-INF/services}.
 */
public class EngineParametersPerTest implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        NuclearSimulationEngine.beginScope();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        NuclearSimulationEngine.endScope();
    }
}
