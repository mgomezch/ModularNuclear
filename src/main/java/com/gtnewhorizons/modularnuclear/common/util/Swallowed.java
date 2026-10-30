package com.gtnewhorizons.modularnuclear.common.util;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.gtnewhorizons.modularnuclear.ModularNuclear;

/**
 * For failures a caller deliberately survives (optional mods missing, Minecraft not bootstrapped in tests, a
 * defensive lookup), so they are not silently lost: each distinct failure is logged once per catching method, with
 * its stack trace. Errors of the JVM itself (out of memory, stack overflow) are never survived.
 */
public final class Swallowed {

    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    private Swallowed() {}

    public static void note(Throwable t) {
        if (t instanceof VirtualMachineError vme) throw vme;
        if (t instanceof ThreadDeath td) throw td;
        StackTraceElement[] here = new Throwable().getStackTrace();
        String site = here.length > 1 ? here[1].toString() : "unknown";
        if (!SEEN.add(
            site + ' '
                + t.getClass()
                    .getName()))
            return;
        if (isMissingOptionalCode(t)) {
            ModularNuclear.LOG.debug("Optional code unavailable at {}: {}", site, t.toString());
        } else {
            ModularNuclear.LOG.warn("Ignored failure at {} (logged once)", site, t);
        }
    }

    /** A class, method or field that is only there when another mod is installed. */
    private static boolean isMissingOptionalCode(Throwable t) {
        return t instanceof ClassNotFoundException || t instanceof NoClassDefFoundError
            || t instanceof NoSuchMethodException
            || t instanceof NoSuchFieldException
            || t instanceof NoSuchMethodError
            || t instanceof NoSuchFieldError;
    }
}
