package com.gtnewhorizons.modularnuclear.common.nuclear;

import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ControlRodData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantFluidData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData;

/**
 * Per-cycle tile behaviour (wear, coolant turnover, coolant-cell absorption, transmutation and control-rod
 * combination) shared by the game and the standalone simulator. Callers own the state (item damage, fluid amounts,
 * stored heat); these functions only compute how it changes.
 */
public final class TileTick {

    private TileTick() {}

    /** Rounds down and adds one with probability equal to the fractional part. */
    public static int stochasticRound(double value) {
        if (value <= 0.0) return 0;
        int whole = (int) value;
        return whole + (NuclearSimulationEngine.random()
            .nextDouble() < value - whole ? 1 : 0);
    }

    /**
     * Fuel wear in durability units for one cycle: a quarter of the neutrons it emitted, its absorptions weighted by
     * the absorption damage multipliers, and temperature damage (given per second) over the cycle, all scaled by the
     * fuel burnup multiplier.
     */
    public static double fuelWear(double tempDamagePerSecond, int neutronsGenerated, int fastAbsorbed,
        int thermalAbsorbed, double dt) {
        double wear = Math.max(0.0, neutronsGenerated * 0.25)
            + fastAbsorbed * NuclearSimulationEngine.fastAbsorptionDamageMultiplier
            + thermalAbsorbed * NuclearSimulationEngine.thermalAbsorptionDamageMultiplier
            + tempDamagePerSecond * dt;
        return wear * NuclearSimulationEngine.fuelBurnupMultiplier;
    }

    /** Temperature damage per second for a fuel without a configuration entry. */
    public static double genericFuelTemperatureDamage(double temp) {
        return temp > 20.0 ? (temp - 20.0) / 100.0 : 0.0;
    }

    /**
     * Moderator wear in durability units for one cycle: weighted absorptions, 5% of the flux through it, and its
     * temperature damage over the cycle.
     */
    public static double moderatorWear(ModeratorData mod, double temp, int fastAbsorbed, int thermalAbsorbed,
        int totalFlux, double dt) {
        return fastAbsorbed * NuclearSimulationEngine.fastAbsorptionDamageMultiplier
            + thermalAbsorbed * NuclearSimulationEngine.thermalAbsorptionDamageMultiplier
            + totalFlux * 0.05
            + mod.calculateTemperatureDamage(temp) * dt;
    }

    /** Lowest temperature a coolant can cool a hatch to: its boiling point if it boils into steam, else ambient. */
    public static double coolantSinkTemperature(CoolantFluidData cfd, double ambient) {
        return cfd.expansionRatio > 1 ? Math.max(ambient, cfd.boilingTemperature) : ambient;
    }

    /**
     * Heat (HU) a coolant hatch transfers into its coolant over {@code dtSub} seconds: exact exponential cooling of the
     * hatch's heat capacity toward the sink at the hatch's conductance, limited to the heat the remaining coolant can
     * still absorb this cycle.
     */
    public static double coolantSubstepHeat(double temp, double sinkTemp, int hatchTier, double efficiency,
        double dtSub, double remainingCapacityHu) {
        double ch = NuclearSimulationEngine.getCoreHatchHeatCapacity(hatchTier);
        if (temp <= sinkTemp || remainingCapacityHu <= 0.0) return 0.0;
        double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
        double conductance = NuclearSimulationEngine.getHatchConductance(hatchTier) * effFactor;
        double q = ch * (temp - sinkTemp) * (1.0 - Math.exp(-conductance * dtSub / ch));
        return Math.min(q, remainingCapacityHu);
    }

    /** Millilitres of coolant consumed for the heat it absorbed this cycle (rounded stochastically). */
    public static int coolantConsumed(CoolantFluidData cfd, double heatHu) {
        return cfd.heatPerLiter > 0.0 ? stochasticRound(heatHu / cfd.heatPerLiter) : 0;
    }

    /**
     * Whether fast neutron capture transmutes 2 mB of the coolant this cycle (for example water to deuterium), given
     * a uniform random integer in [0, 10000).
     */
    public static boolean transmutes(CoolantFluidData cfd, int fastAbsorbed, int random10000) {
        if (cfd == null || cfd.transmutationProduct == null || cfd.transmutationChancePerNeutron <= 0.0) return false;
        double chance = Math.min(1.0, fastAbsorbed * cfd.transmutationChancePerNeutron);
        return random10000 < (int) Math.round(chance * 10000);
    }

    /**
     * Durability-heat units a coolant cell takes from its cell this cycle: the heat above ambient converted at
     * huPerDurability, limited to 1% of the cell's capacity per second and to the room left in the cell.
     */
    public static int coolantCellUptake(int maxHeat, int currentHeat, double temp, double ambient,
        double huPerDurability, double efficiency, double dt) {
        if (maxHeat <= 0 || temp <= ambient || huPerDurability <= 0.0) return 0;
        int maxTransfer = Math.max(1, (int) Math.round((maxHeat / 100.0) * dt));
        double heatAvailable = (temp - ambient) * NuclearSimulationEngine.HU_PER_DEGREE;
        double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
        int take = (int) Math.round(Math.min(heatAvailable / huPerDurability, (double) maxTransfer) * effFactor);
        return Math.max(0, Math.min(take, maxHeat - currentHeat));
    }

    /** Time constant of the radiovoltaic absorption-rate average, in seconds. */
    public static final double RADIOVOLTAIC_RATE_TIME_CONSTANT = 2.0;

    /**
     * Updates a radiovoltaic plate's running estimate of its weighted absorption rate (neutrons per second, fast
     * counting 4x) with this cycle's absorptions. The output curve is concave, so applying it to each cycle's noisy
     * count would make output depend on the cycle length; an exponential average over a fixed time keeps the noise,
     * and therefore the output, the same for any cycle length.
     */
    public static double radiovoltaicRate(double previousRate, double weightedAbsorbed, double dt) {
        if (dt <= 0.0) return previousRate;
        double alpha = 1.0 - Math.exp(-dt / RADIOVOLTAIC_RATE_TIME_CONSTANT);
        return previousRate + alpha * (weightedAbsorbed / dt - previousRate);
    }

    // ---- Control rods -------------------------------------------------------------------------------------------

    /** Absorption probability of a control rod at the given insertion (0..1). */
    public static double rodAbsorption(ControlRodData rod, double insertion, NeutronType type) {
        double max = type == NeutronType.THERMAL ? rod.maxThermalAbsorption : rod.maxFastAbsorption;
        return Math.max(0.01, insertion * max);
    }

    /** Scattering probability of a control rod at the given insertion (0..1). */
    public static double rodScattering(ControlRodData rod, double insertion, NeutronType type) {
        double scattering = type == NeutronType.THERMAL ? rod.scatteringThermal : rod.scatteringFast;
        return Math.max(0.01, scattering * (1.0 - insertion));
    }

    /**
     * Absorption of a cell with a control rod: the rod and any moderator in the rod housing act first, and the cell's
     * contents see only what they leave.
     */
    public static double combinedAbsorption(double pBase, double pRod, double pRodModeration,
        double pRodModeratorAbsorption) {
        double pBaseEff = Math.max(0.0, 1.0 - pRod - pRodModeration);
        return Math.min(1.0, pRod + pRodModeratorAbsorption + pBaseEff * pBase);
    }

    /** Scattering of a cell with a control rod (see {@link #combinedAbsorption}). */
    public static double combinedScattering(double pBase, double pRodAbsorption, double pRodModeration,
        double pRodScattering, double pRodModeratorScattering) {
        double pBaseEff = Math.max(0.0, 1.0 - pRodAbsorption - pRodModeration);
        return Math.min(1.0, pRodScattering + pRodModeratorScattering + pBaseEff * pBase);
    }

    /** Moderation of a cell with a control rod (see {@link #combinedAbsorption}). */
    public static double combinedModeration(double pBase, double pRodFastAbsorption, double pRodModeration) {
        double pBaseEff = Math.max(0.0, 1.0 - pRodFastAbsorption - pRodModeration);
        return Math.min(1.0, pRodModeration + pBaseEff * pBase);
    }

    /** How many of a cell's absorbed neutrons the control rod took, splitting in proportion to the probabilities. */
    public static int rodShareOfAbsorbed(int count, double pBase, double pRod) {
        double sum = pBase + pRod;
        int nRod = (sum > 0) ? (int) Math.round(count * (pRod / sum)) : count / 2;
        return Math.min(count, Math.max(0, nRod));
    }
}
