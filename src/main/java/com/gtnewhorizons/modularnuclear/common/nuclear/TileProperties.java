package com.gtnewhorizons.modularnuclear.common.nuclear;

import java.util.Random;

import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantFluidData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantItemData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.InsulatorData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.RadiovoltaicData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ReflectorData;
import com.gtnewhorizons.modularnuclear.common.config.data.FuelData;

/**
 * Neutron and heat properties of a grid cell's contents, shared by the game and the standalone simulator. Configured
 * contents read their values from the config record; the generic kinds carry the fallbacks the game uses for items
 * that have no configuration entry.
 */
public final class TileProperties {

    /** Fluid amount (mB) that forms one emitted neutron packet in a liquid fuel hatch. */
    public static final double LIQUID_FUEL_MB_PER_PACKET = 8000.0;

    private static final Random RAND = new Random();

    private TileProperties() {}

    private static double pick(NeutronType type, double fast, double thermal) {
        return type == NeutronType.FAST ? fast : thermal;
    }

    /** Probability that a neutron entering the cell is absorbed by its contents (before any control rod). */
    public static double absorption(TileContent c, NeutronType t) {
        switch (c.kind) {
            case FUEL:
            case LIQUID_FUEL: {
                FuelData d = c.fuelData();
                return pick(t, d.absorptionFast, d.absorptionThermal);
            }
            case MODERATOR: {
                ModeratorData d = c.moderator();
                return pick(t, d.absorptionFast, d.absorptionThermal);
            }
            case REFLECTOR: {
                ReflectorData d = c.reflector();
                return pick(t, d.absorptionFast, d.absorptionThermal);
            }
            case INSULATOR: {
                InsulatorData d = c.insulator();
                return pick(t, d.absorptionFast, d.absorptionThermal);
            }
            case RADIOVOLTAIC: {
                RadiovoltaicData d = c.radiovoltaic();
                return pick(t, d.absorptionFast, d.absorptionThermal);
            }
            case COOLANT_ITEM: {
                CoolantItemData d = c.coolantItem();
                return pick(t, d.absorptionFast, d.absorptionThermal);
            }
            case COOLANT_FLUID: {
                CoolantFluidData d = c.coolantFluid();
                return pick(t, d.absorptionFast, d.absorptionThermal);
            }
            case GENERIC_FUEL:
                return pick(t, 0.25, 0.80);
            case GENERIC_MODERATOR:
                return pick(t, 0.002, 0.009);
            case GENERIC_REFLECTOR:
                return pick(t, 0.01, 0.02);
            case GENERIC_INSULATOR:
                return 0.01;
            case GENERIC_NAQUARITE:
                return 1.0;
            case GENERIC_RADIOVOLTAIC:
                return c.genericTier() >= 2 ? 0.60 : 0.40;
            case GENERIC_COOLANT:
                return 0.05;
            case OTHER_FLUID:
                return 0.05;
            case EMPTY:
            case EMPTY_HATCH:
            case NEUTRON_SOURCE:
                return 0.01;
            case HEAT_VENT:
            case HEAT_EXCHANGER:
            case OTHER:
            default:
                return 0.02;
        }
    }

    /** Probability that a neutron entering the cell is scattered by its contents (before any control rod). */
    public static double scattering(TileContent c, NeutronType t) {
        switch (c.kind) {
            case FUEL:
            case LIQUID_FUEL: {
                FuelData d = c.fuelData();
                return pick(t, d.scatteringFast, d.scatteringThermal);
            }
            case MODERATOR: {
                ModeratorData d = c.moderator();
                return pick(t, d.scatteringFast, d.scatteringThermal);
            }
            case REFLECTOR: {
                ReflectorData d = c.reflector();
                return pick(t, d.scatteringFast, d.scatteringThermal);
            }
            case INSULATOR: {
                InsulatorData d = c.insulator();
                return pick(t, d.scatteringFast, d.scatteringThermal);
            }
            case COOLANT_ITEM: {
                CoolantItemData d = c.coolantItem();
                return pick(t, d.scatteringFast, d.scatteringThermal);
            }
            case COOLANT_FLUID: {
                CoolantFluidData d = c.coolantFluid();
                return pick(t, d.scatteringFast, d.scatteringThermal);
            }
            case RADIOVOLTAIC:
            case GENERIC_RADIOVOLTAIC:
            case GENERIC_NAQUARITE:
                return 0.0;
            case GENERIC_FUEL:
                return pick(t, 0.15, 0.10);
            case GENERIC_MODERATOR:
                return pick(t, 0.93, 0.621);
            case GENERIC_REFLECTOR:
                return pick(t, 0.95, 0.98);
            case GENERIC_COOLANT:
                return 0.45;
            case OTHER_FLUID:
                return 0.10;
            case EMPTY:
            case EMPTY_HATCH:
            case NEUTRON_SOURCE:
                return 0.02;
            case GENERIC_INSULATOR:
            case HEAT_VENT:
            case HEAT_EXCHANGER:
            case OTHER:
            default:
                return 0.05;
        }
    }

    /** Probability that a fast neutron is moderated to thermal in the cell (before any control rod). */
    public static double moderation(TileContent c) {
        switch (c.kind) {
            case FUEL:
            case LIQUID_FUEL:
                return c.fuelData().moderation;
            case MODERATOR:
                return c.moderator().moderation;
            case REFLECTOR:
                return c.reflector().moderation;
            case HEAT_VENT:
                return c.heatVent().moderation;
            case HEAT_EXCHANGER:
                return c.heatExchanger().moderation;
            case COOLANT_ITEM:
                return c.coolantItem().moderation;
            case COOLANT_FLUID:
                return c.coolantFluid().moderation;
            case RADIOVOLTAIC:
            case GENERIC_RADIOVOLTAIC:
                return 0.0;
            case GENERIC_MODERATOR:
                return 0.50;
            case GENERIC_REFLECTOR:
                return 0.20;
            case GENERIC_FUEL:
                return 0.10;
            default:
                return 0.05;
        }
    }

    /** Relative conduction coefficient of the cell's contents. */
    public static double heatTransferCoeff(TileContent c) {
        switch (c.kind) {
            case FUEL:
            case LIQUID_FUEL:
                return c.fuelData().heatTransferCoeff;
            case MODERATOR:
                return c.moderator().heatTransferCoeff;
            case REFLECTOR:
                return c.reflector().heatTransferCoeff;
            case INSULATOR:
                return c.insulator().heatTransferCoeff;
            case RADIOVOLTAIC:
                return c.radiovoltaic().heatTransferCoeff;
            case HEAT_VENT:
                return c.heatVent().heatTransferCoeff;
            case HEAT_EXCHANGER:
                return c.heatExchanger().heatTransferCoeff;
            case COOLANT_ITEM:
                return c.coolantItem().heatTransferCoeff;
            case COOLANT_FLUID:
                return c.coolantFluid().heatTransferCoeff;
            case GENERIC_FUEL:
                return 0.05;
            case GENERIC_INSULATOR:
            case GENERIC_NAQUARITE:
                return 0.01;
            case GENERIC_RADIOVOLTAIC:
                return 0.10;
            case GENERIC_COOLANT:
            case GENERIC_VENT:
                return 0.40;
            case GENERIC_REFLECTOR:
                return 0.15;
            case OTHER_FLUID:
                return 0.15;
            case EMPTY_HATCH:
                return 0.05;
            case EMPTY:
            case NEUTRON_SOURCE:
                return 0.02;
            case GENERIC_MODERATOR:
            case OTHER:
            default:
                return 0.03;
        }
    }

    /** Number of directional packets a solid fuel rod emits per cycle. */
    public static int solidPacketCount(FuelData fd) {
        return fd != null ? fd.packetCount : 1;
    }

    /** Number of packets a liquid fuel hatch emits: one per 8000 mB stored, the remainder emitted stochastically. */
    public static int liquidPacketCount(int fluidAmount) {
        if (fluidAmount <= 0) return 0;
        double expected = fluidAmount / LIQUID_FUEL_MB_PER_PACKET;
        int base = (int) Math.floor(expected);
        return base + (RAND.nextDouble() < expected - base ? 1 : 0);
    }

    /**
     * Time constant (seconds) with which a fuel's chain-reaction emission follows its absorptions. A fixed physical
     * generation time, rather than "one cycle", makes chain build-up, decay and runaway the same for any cycle length;
     * 0.25 s is one cycle at the game's default of 5 ticks per cycle.
     */
    public static final double CHAIN_TIME_CONSTANT = 0.25;

    /**
     * Updates a fuel's chain-reaction emission rate (neutrons per second) from last cycle's absorptions: the rate
     * relaxes toward absorptions per second times the fission multipliers with time constant
     * {@link #CHAIN_TIME_CONSTANT}. At steady state it equals that product, as with a one-cycle delay.
     */
    public static double chainRate(double previousRate, int lastThermalAbsorbed, int lastFastAbsorbed,
        double thermalFissionMult, double fastFissionMult, double dt) {
        if (dt <= 0.0) return previousRate;
        double effThermal = thermalFissionMult * NuclearSimulationEngine.globalThermalFissionMultiplier;
        double effFast = fastFissionMult * NuclearSimulationEngine.globalFastFissionMultiplier;
        double target = (lastThermalAbsorbed * effThermal + lastFastAbsorbed * effFast) / dt;
        double alpha = 1.0 - Math.exp(-dt / CHAIN_TIME_CONSTANT);
        return previousRate + alpha * (target - previousRate);
    }

    /**
     * Neutrons a fuel emits in one cycle: its spontaneous base yield plus its chain-reaction emission (both per
     * second) over the cycle, scaled by efficiency and rounded stochastically so the expected yield is exact.
     */
    public static int fuelNeutrons(int baseNeutronsPerSecond, double chainRate, double efficiency, double dt) {
        return TileTick.stochasticRound((baseNeutronsPerSecond + Math.max(0.0, chainRate)) * dt * efficiency);
    }

    /** Base neutrons per second after the archetype multiplier. */
    public static int effectiveBaseNeutrons(FuelData fd) {
        double mult = NuclearSimulationEngine.getArchetypeNbaseMultiplier(fd.archetype);
        return Math.max(1, (int) Math.round(fd.baseNeutrons * mult));
    }

    /** Thermal fission multiplier after the archetype multiplier (before the global multiplier). */
    public static double effectiveThermalFissionMultiplier(FuelData fd) {
        return fd.thermalFissionMultiplier * NuclearSimulationEngine.getArchetypeFissionMultiplier(fd.archetype);
    }

    /** Fast fission multiplier after the archetype multiplier (before the global multiplier). */
    public static double effectiveFastFissionMultiplier(FuelData fd) {
        return fd.fastFissionMultiplier * NuclearSimulationEngine.getArchetypeFissionMultiplier(fd.archetype);
    }
}
