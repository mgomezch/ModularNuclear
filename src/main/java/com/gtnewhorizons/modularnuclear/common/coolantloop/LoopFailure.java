package com.gtnewhorizons.modularnuclear.common.coolantloop;

/**
 * Why the loop engine ruptured a loop. The game shows it through its lang key, the standalone simulator through its
 * English pattern; both take the same arguments.
 */
public enum LoopFailure {

    /** Segment id, pressure (bar), rating (bar). */
    BURST("coolantloops.engine.burst", "Pipe segment %s burst! Pressure %.2f bar exceeded maximum rating %.2f bar"),
    /** Segment id, temperature (°C), rating (°C). */
    MELTED("coolantloops.engine.melted", "Pipe segment %s melted! Temperature %.1f C exceeded maximum rating %.1f C"),
    /** Temperature (°C), segment id, melting point (°C), fluid name. */
    SOLIDIFIED("coolantloops.engine.solidified",
        "Catastrophic coolant solidification: Coolant temperature (%.1f °C) in segment %s dropped below its melting point (%.1f °C) for %s! Solidified plug ruptured loop."),
    /** Temperature (°C), segment id, freezing point (°C), fluid name. */
    FROZE("coolantloops.engine.froze",
        "Catastrophic coolant freezing: Coolant temperature (%.1f °C) in segment %s dropped below freezing point (%.1f °C) for %s! Solidified ice/plug ruptured loop.");

    public final String langKey;
    public final String english;

    LoopFailure(String langKey, String english) {
        this.langKey = langKey;
        this.english = english;
    }
}
