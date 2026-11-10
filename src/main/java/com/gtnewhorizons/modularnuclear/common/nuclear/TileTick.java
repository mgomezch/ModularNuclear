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

    /**
     * Fuel wear in durability units for one cycle: a quarter of the neutrons it emitted, its absorptions weighted by
     * the absorption damage multipliers, and temperature damage (given per second) over the cycle, all scaled by the
     * fuel burnup multiplier.
     */
    public static double fuelWear(EngineParameters p, double tempDamagePerSecond, int neutronsGenerated,
        int fastAbsorbed, int thermalAbsorbed, double dt) {
        double wear = Math.max(0.0, neutronsGenerated * 0.25) + fastAbsorbed * p.fastAbsorptionDamageMultiplier
            + thermalAbsorbed * p.thermalAbsorptionDamageMultiplier
            + tempDamagePerSecond * dt;
        return wear * p.fuelBurnupMultiplier;
    }

    /** Temperature damage per second for a fuel without a configuration entry. */
    public static double genericFuelTemperatureDamage(double temp) {
        return temp > 20.0 ? (temp - 20.0) / 100.0 : 0.0;
    }

    /**
     * Moderator wear in durability units for one cycle: weighted absorptions, 5% of the flux through it, and its
     * temperature damage over the cycle.
     */
    public static double moderatorWear(EngineParameters p, ModeratorData mod, double temp, int fastAbsorbed,
        int thermalAbsorbed, int totalFlux, double dt) {
        return fastAbsorbed * p.fastAbsorptionDamageMultiplier + thermalAbsorbed * p.thermalAbsorptionDamageMultiplier
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
    public static double coolantSubstepHeat(EngineParameters p, double temp, double sinkTemp, int hatchTier,
        double efficiency, double dtSub, double remainingCapacityHu) {
        double ch = p.hatchHeatCapacity(hatchTier);
        if (temp <= sinkTemp || remainingCapacityHu <= 0.0) return 0.0;
        double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
        double conductance = p.hatchConductance(hatchTier) * effFactor;
        double q = ch * (temp - sinkTemp) * (1.0 - Math.exp(-conductance * dtSub / ch));
        return Math.min(q, remainingCapacityHu);
    }

    /** Millilitres of coolant consumed for the heat it absorbed this cycle (rounded stochastically). */
    public static int coolantConsumed(EngineParameters p, CoolantFluidData cfd, double heatHu) {
        return cfd.heatPerLiter > 0.0 ? p.stochasticRound(heatHu / cfd.heatPerLiter) : 0;
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
    public static int coolantCellUptake(EngineParameters p, int maxHeat, int currentHeat, double temp, double ambient,
        double huPerDurability, double efficiency, double dt) {
        if (maxHeat <= 0 || temp <= ambient || huPerDurability <= 0.0) return 0;
        int maxTransfer = Math.max(1, (int) Math.round((maxHeat / 100.0) * dt));
        double heatAvailable = (temp - ambient) * p.huPerDegree;
        double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
        int take = (int) Math.round(Math.min(heatAvailable / huPerDurability, (double) maxTransfer) * effFactor);
        return Math.max(0, Math.min(take, maxHeat - currentHeat));
    }

    /** Time constant of the radiovoltaic absorption-rate average, in seconds. */
    public static final double RADIOVOLTAIC_RATE_TIME_CONSTANT = 2.0;

    /**
     * Updates a radiovoltaic plate's running estimate of the heat its absorbed neutrons bring (HU/s) with this cycle's
     * absorbed heat. The output curve is concave, so applying it to each cycle's noisy count would make output depend
     * on the cycle length; an exponential average over a fixed time keeps the noise, and therefore the output, the same
     * for any cycle length.
     */
    public static double radiovoltaicRate(double previousRate, double absorbedHeat, double dt) {
        if (dt <= 0.0) return previousRate;
        double alpha = 1.0 - Math.exp(-dt / RADIOVOLTAIC_RATE_TIME_CONSTANT);
        return previousRate + alpha * (absorbedHeat / dt - previousRate);
    }

    // ---- Control rods -------------------------------------------------------------------------------------------

    /**
     * Absorption probability of a control rod at the given insertion (0..1). Like its scattering, it is proportional to
     * insertion: a fully withdrawn rod does nothing, exactly as if the column had no rod.
     */
    public static double rodAbsorption(ControlRodData rod, double insertion, NeutronType type) {
        double max = type == NeutronType.THERMAL ? rod.maxThermalAbsorption : rod.maxFastAbsorption;
        return clampInsertion(insertion) * max;
    }

    /** Scattering probability of a control rod at the given insertion (0..1), proportional to insertion. */
    public static double rodScattering(ControlRodData rod, double insertion, NeutronType type) {
        double scattering = type == NeutronType.THERMAL ? rod.scatteringThermal : rod.scatteringFast;
        return clampInsertion(insertion) * scattering;
    }

    /**
     * Moderation of an advanced control rod hatch's moderator follower, given the rod's insertion (0..1): the follower
     * is
     * withdrawn as the rod goes in, so it acts at 1 − insertion of its moderator's. An unknown moderator acts as a
     * generic one.
     */
    public static double followerModeration(ModeratorData moderator, double rodInsertion) {
        return clampInsertion((1.0 - clampInsertion(rodInsertion)) * (moderator != null ? moderator.moderation : 0.50));
    }

    /** Absorption of a moderator follower (see {@link #followerModeration}). */
    public static double followerAbsorption(ModeratorData moderator, double rodInsertion, NeutronType type) {
        double abs = moderator != null
            ? (type == NeutronType.FAST ? moderator.absorptionFast : moderator.absorptionThermal)
            : (type == NeutronType.FAST ? 0.002 : 0.009);
        return clampInsertion((1.0 - clampInsertion(rodInsertion)) * abs);
    }

    /** Scattering of a moderator follower (see {@link #followerModeration}). */
    public static double followerScattering(ModeratorData moderator, double rodInsertion, NeutronType type) {
        double scattering = moderator != null
            ? (type == NeutronType.FAST ? moderator.scatteringFast : moderator.scatteringThermal)
            : (type == NeutronType.FAST ? 0.83 : 0.621);
        return clampInsertion((1.0 - clampInsertion(rodInsertion)) * scattering);
    }

    /** Fast neutron reflection of a moderator follower (see {@link #followerModeration}). */
    public static double followerReflectionFast(ModeratorData moderator, double rodInsertion) {
        double reflection = moderator != null ? moderator.reflectionFast : 0.10;
        return clampInsertion((1.0 - clampInsertion(rodInsertion)) * reflection);
    }

    private static double clampInsertion(double insertion) {
        return Math.max(0.0, Math.min(1.0, insertion));
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

    /**
     * Fast neutron reflection of a cell with a control rod (see {@link #combinedAbsorption}). The rod itself reflects
     * nothing; its moderator follower does.
     */
    public static double combinedReflectionFast(double pBase, double pRodFastAbsorption, double pRodModeration,
        double pRodModeratorReflection) {
        double pBaseEff = Math.max(0.0, 1.0 - pRodFastAbsorption - pRodModeration);
        return Math.min(1.0, pRodModeratorReflection + pBaseEff * pBase);
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
