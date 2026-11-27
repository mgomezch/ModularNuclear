package com.gtnewhorizons.modularnuclear.common.coolantloop.pump;

import gregtech.api.enums.Materials;

/** The rotor a pump drives a liquid loop with, and the wear on the machine around it. */
public interface PumpImpeller {

    /** Reads the rotor again; false when there is none, and the pump cannot run. */
    boolean updateRotorEfficiency();

    double getRotorEfficiency();

    Materials getRotorMaterial();

    /** Wears the rotor for a tick at this power. */
    void degradeRotor(long euPerTick);

    /** The coolant is hotter than the rotor's melting point: the rotor is lost and the pump stops. */
    void failOnImpellerMelting(double fluidTempCelsius, Materials rotorMaterial);

    void destroyImpeller();

    void checkMaintenance();

    /** The machine's efficiency from its maintenance state, 0 to 1. */
    double getEfficiencyFactor();
}
