package com.gtnewhorizons.modularnuclear.common.util;

/**
 * The one counter every configuration load moves on (the reactor and coolant loop YAML files, and the Forge config's
 * lists). The caches built from configuration ({@link LongKeyCache}, {@link IdentityCache}, {@link NameCache}) start
 * over when it does. Plain Java: the standalone simulator uses it too.
 */
public final class ConfigGeneration {

    private static volatile int value;

    private ConfigGeneration() {}

    public static int current() {
        return value;
    }

    /** A configuration changed: every cache built from it is stale. */
    public static synchronized void bump() {
        value++;
    }
}
