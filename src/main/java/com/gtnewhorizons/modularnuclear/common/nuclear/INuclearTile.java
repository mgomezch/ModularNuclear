package com.gtnewhorizons.modularnuclear.common.nuclear;

import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData;
import com.gtnewhorizons.modularnuclear.common.config.data.FuelData;

public interface INuclearTile {

    /** The parameters this tile's physics runs with: its owner's (the reactor's or the simulator grid's). */
    EngineParameters parameters();

    /**
     * Current temperature of this grid tile in degrees Celsius.
     */
    double getTemperature();

    /**
     * Sets the temperature of this tile.
     */
    void setTemperature(double temperature);

    /**
     * Heat (HU) that raises this tile's temperature by 1 °C: a nuclear core hatch's grows with its tier, every other
     * tile holds the configured HU per degree.
     */
    default double getHeatCapacity() {
        return parameters().huPerDegree;
    }

    /**
     * Deposits heat into this tile in Hu (heat units, default 32 Hu = 1 °C).
     */
    void addHeat(double heatHu);

    /**
     * Heat transfer coefficient between 0.0 and 1.0.
     */
    double getHeatTransferCoeff();

    /**
     * Checks if this tile contains nuclear fuel.
     */
    boolean isFuel();

    /**
     * Checks if this tile is an external neutron injection source.
     */
    default boolean isNeutronSource() {
        return false;
    }

    /**
     * Generates fast neutrons for the current tick, scaled by reactivity efficiency.
     */
    int generateNeutrons(double efficiency);

    /**
     * Share of the full fission heat per neutron that this cycle's emission deposits as prompt heat (see
     * {@link TileProperties#promptHeatWeight}); 1 for tiles that track no chain reaction.
     */
    default double getPromptHeatWeight() {
        return 1.0;
    }

    /**
     * Probability [0.0, 1.0] of absorbing a neutron of the given type.
     */
    double getAbsorptionProbability(NeutronType type);

    /**
     * Probability [0.0, 1.0] of scattering a neutron of the given type.
     */
    double getScatteringProbability(NeutronType type);

    /**
     * Probability [0.0, 1.0] that a scattered fast neutron slows down to a thermal neutron.
     */
    double getModerationProbability();

    /**
     * Callback when one or more neutrons are absorbed by this tile.
     */
    void onNeutronAbsorbed(NeutronType type, int count);

    /**
     * Callback when one or more neutrons are scattered by this tile.
     */
    void onNeutronScattered(NeutronType type, int count);

    /**
     * Registers neutron flux traversing this tile.
     */
    void addNeutronFlux(NeutronType type, int count);

    /**
     * Called at the end of the simulation cycle to update durability, cooling, and transmutation.
     */
    void nuclearTick(double efficiency);

    /**
     * Fraction of incoming heat transfer dampened/blocked by insulation [0.0, 1.0].
     * 0.0 means uninsulated (normal heat transfer).
     * 1.0 means complete insulation (no incoming heat transfer).
     */
    default double getInsulationDampening() {
        return 0.0;
    }

    /**
     * Coolant heat removal over {@code dtSub} seconds, run once per reactor cycle after the cycle's heat conduction: a
     * coolant hatch cools itself toward its coolant's sink temperature and accumulates the heat removed, which its
     * nuclear tick then converts into coolant consumed and hot fluid produced. Returns the heat removed (HU); other
     * tiles return 0.
     */
    default double coolantSubstep(double dtSub, double efficiency) {
        return 0.0;
    }

    /**
     * Number of discrete fast neutron rays/packets emitted (1 for single rod, 2 for dual, 4 for quad/fluid).
     */
    default int getNeutronEmissionCount() {
        return 1;
    }

    /**
     * Nuclear fuel type if this tile is a fuel rod or liquid fuel hatch, or null otherwise.
     */
    default NuclearFuelType getFuelType() {
        return null;
    }

    /**
     * Custom fuel mix if this tile contains a custom compounded fuel rod, or null otherwise.
     */
    default CustomFuelMix getCustomFuelMix() {
        return null;
    }

    /**
     * Data-driven YAML fuel configuration if this tile is a fuel rod or liquid fuel hatch, or null otherwise.
     */
    default FuelData getFuelData() {
        return null;
    }

    /**
     * Data-driven YAML moderator configuration if this tile contains a moderator component, or null otherwise.
     */
    default ComponentData.ModeratorData getModeratorData() {
        return null;
    }

    /**
     * Stores Wigner energy in this tile from fast neutron interactions (Hu).
     */
    default void addWignerEnergy(double amountHu) {}

    /**
     * Releases stored Wigner energy as heat (Hu) based on the moderator's temperature curve,
     * consuming the released energy from the stored pool.
     */
    default double releaseWignerEnergy() {
        return 0.0;
    }

    /**
     * Current stored Wigner energy in this tile (Hu).
     */
    default double getStoredWignerEnergy() {
        return 0.0;
    }

    /**
     * Returns the number of secondary thermal neutrons generated upon absorbing fast neutrons
     * (deprecated: graphite stores Wigner energy instead of emitting secondary thermal neutrons).
     */
    default double getThermalNeutronEmissionYield(int fastNeutronsAbsorbed) {
        return 0.0;
    }

    /**
     * Checks if this tile contains an active heat vent.
     */
    default boolean isHeatVent() {
        return false;
    }

    /**
     * Returns data-driven configuration for active heat vent component if present.
     */
    default ComponentData.HeatVentData getHeatVentData() {
        return null;
    }

    /**
     * Checks if this tile contains an active heat exchanger.
     */
    default boolean isHeatExchanger() {
        return false;
    }

    /**
     * Returns data-driven configuration for active heat exchanger component if present.
     */
    default ComponentData.HeatExchangerData getHeatExchangerData() {
        return null;
    }

    /**
     * Drains energy from reactor buffer (EU). Returns true if sufficient energy was drained.
     */
    default boolean drainEnergy(long eu) {
        return true;
    }

    /**
     * Energy (EU) currently available to this tile's active components from the reactor buffer. All tiles of one
     * reactor draw from the same buffer. Defaults to unlimited.
     */
    default long getAvailableEnergy() {
        return Long.MAX_VALUE;
    }

    /**
     * Instantly voids / destroys the component inside this tile (e.g. on over-temperature).
     */
    default void voidComponent() {}

    /**
     * Checks if this tile has an active control rod hatch beneath it.
     */
    default boolean hasControlRod() {
        return false;
    }

    /**
     * Fraction of absorbed neutrons of the given type captured by the control rod [0.0, 1.0].
     * 0.0 means all absorbed neutrons are captured by the base tile (e.g. no control rod).
     * 1.0 means all absorbed neutrons are captured by the control rod.
     */
    default double getControlRodAbsorptionFraction(NeutronType type) {
        return 0.0;
    }

    /**
     * Gets the number of control rod housings the rod can withdraw into (0..5).
     * Defaults to 5 (full travel) when no housing restriction is present.
     */
    default int getControlRodHousings() {
        return 5;
    }
}
