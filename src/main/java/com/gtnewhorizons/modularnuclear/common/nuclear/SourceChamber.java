package com.gtnewhorizons.modularnuclear.common.nuclear;

/*-
Note [Source Chamber beams] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
A reactor's neutron source tile takes a neutron beam through the beam input hatch under it.  The beam comes from
gtnhlanth's Source Chamber (MTESourceChamber in GT5-Unofficial), which this class models so that the simulator can
show what a beam costs.  Everything here mirrors the game, as the in-world suite test_beamline_source_chamber checks:

  - A recipe (SourceChamber.Recipe, from BeamlineRecipeLoader) burns one item a second and runs on any energy
    hatch whose practical voltage VP = V · 30/32 reaches its EU/t.  The chamber draws the hatch's full VP every tick,
    whatever the recipe needs: a higher tier costs more and adds nothing below the energy cap.
  - Beam energy E = min(E_material · (1 − 1.001^(−ratio · (V − EU/t_recipe))), E_max(neutron) = 15,000 keV).  Both
    neutron recipes have ratio 999, so E reaches its cap at the recipe's own tier.
  - The beam's rate (particles per packet) is the recipe's: 10 for both.

The reactor turns a beam into fast neutrons (beamPacketsPerSecond): E · rate · beam_energy_to_fast_neutron_multiplier
packets a second, of 4 fast neutrons each, emitted from the source tile.  NuclearReactorPhysics (in game) and SimTile
(simulator) both use it.  With the multiplier at 0.01, Californium dust from an EV hatch costs 1,920 EU/t and a dust
a second for 900 packets, 3,600 fast neutrons a second.

Balance.  A beam is meant to pay off, in EU and as a neutron source for driving or irradiating fuel: its price is the
item it burns every second and a ZPM-tier chamber, not EU.  But a reactor with no fuel must not beat the tier's
simple design (about 30 A at EV, 60 A above) on one beam.  Without fuel nothing multiplies neutrons, so a beam
neutron brings at most 12 HU (8 when moderated, then 4 when absorbed as thermal), and that heat is worth at most:

    in a radiovoltaic plate, at vanishing flux:   artanh(0.95) / 0.95 · m_R · κ   EU/HU  (Note [Radiovoltaic energy])
    as steam: water a coolant hatch boils straight into superheated steam, at 1 EU/L:   160 / 17.65625 EU/HU

maxFuelFreeEuPerTick bounds a fuel-free reactor's output with that, and SourceChamberTest checks the bound stays
under the simple design for every recipe: raising m_R, or the multiplier, must keep it there.  With the multiplier at
0.01 the bound for Californium is about 25,700 EU/t (12.5 A at EV); the best plates-only layout in the simulator, a
9x9 of EV plates around the source, makes about 20,600.

Wrinkles.
(W1) Only the particle counts, not its focus: the reactor ignores focus.  A beam of any other particle explodes the
     reactor (MTENuclearReactor.explodeReactor), which the simulator doesn't model.
(W2) The input hatch keeps a packet for one beamline move (20 ticks) after the chamber stops, so in game the neutrons
     stop about one to three seconds after the beam does.  The simulator switches them off at once.
*/
public final class SourceChamber {

    /** GregTech's voltage of each tier, ULV to MAX (GTValues.V). */
    private static final long[] V = { 8L, 32L, 128L, 512L, 2048L, 8192L, 32_768L, 131_072L, 524_288L, 2_097_152L,
        8_388_608L, 33_554_432L, 134_217_728L, 536_870_912L, Integer.MAX_VALUE - 7 };
    public static final String[] TIER_NAMES = { "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV",
        "UIV", "UMV", "UXV", "MAX" };

    /** A neutron's highest source energy, keV (gtnhlanth's Particle.NEUTRON). */
    public static final double NEUTRON_MAX_KEV = 15_000.0;
    /** Fast neutrons in a packet of beam neutrons. */
    public static final int NEUTRONS_PER_PACKET = 4;

    /** The Source Chamber's neutron recipes (gtnhlanth's BeamlineRecipeLoader). */
    public enum Recipe {

        CALIFORNIUM("CF", "Californium Dust", 1920, 9000.0, 999.0, 10),
        BERYLLIUM("BE", "Long Beryllium Rod", 30720, 100_000.0, 999.0, 10);

        public final String code;
        public final String displayName;
        public final long euPerTick;
        public final double maxEnergyKeV;
        public final double energyRatio;
        public final int rate;

        Recipe(String code, String displayName, long euPerTick, double maxEnergyKeV, double energyRatio, int rate) {
            this.code = code;
            this.displayName = displayName;
            this.euPerTick = euPerTick;
            this.maxEnergyKeV = maxEnergyKeV;
            this.energyRatio = energyRatio;
            this.rate = rate;
        }

        /** The lowest energy hatch tier that runs it. */
        public int minTier() {
            for (int t = 0; t < V.length; t++) if (practicalVoltage(t) >= euPerTick) return t;
            return V.length - 1;
        }

        /** The beam energy (keV) from an energy hatch of a tier, 0 if the hatch can't run the recipe. */
        public double beamEnergyKeV(int tier) {
            if (tier < minTier()) return 0.0;
            double e = maxEnergyKeV * (1.0 - Math.pow(1.001, -energyRatio * (voltage(tier) - euPerTick)));
            return Math.min(e, NEUTRON_MAX_KEV);
        }

        public static Recipe fromCode(String code) {
            if (code == null) return null;
            for (Recipe r : values()) if (r.code.equalsIgnoreCase(code) || r.name()
                .equalsIgnoreCase(code)) return r;
            return null;
        }
    }

    private SourceChamber() {}

    public static long voltage(int tier) {
        return V[Math.max(0, Math.min(V.length - 1, tier))];
    }

    /** EU/t a chamber draws from an energy hatch of a tier while it runs (GTValues.VP). */
    public static long practicalVoltage(int tier) {
        return voltage(tier) * 30 / 32;
    }

    public static int tierFromName(String name) {
        for (int t = 0; t < TIER_NAMES.length; t++) if (TIER_NAMES[t].equalsIgnoreCase(name)) return t;
        return -1;
    }

    /** EU a heat unit makes at most as steam: water boiled straight into superheated steam, at 1 EU/L. */
    public static final double BEST_STEAM_EU_PER_HU = 160.0 / 17.65625;

    /**
     * The most EU/t any reactor without fuel could make from one chamber running a recipe at its own tier: each beam
     * neutron's heat at the best rate a plate or steam can turn it into EU. See Note [Source Chamber beams].
     */
    public static double maxFuelFreeEuPerTick(Recipe recipe, EngineParameters p) {
        double plate = 0.5 * Math.log(1.95 / 0.05) / 0.95 * p.radiovoltaicEuPerHu();
        double perNeutron = Math.max(8.0 * plate, 8.0 * BEST_STEAM_EU_PER_HU + 4.0 * plate);
        double neutronsPerSecond = NEUTRONS_PER_PACKET * beamPacketsPerSecond(
            recipe.beamEnergyKeV(recipe.minTier()),
            recipe.rate,
            p.beamEnergyToFastNeutronMultiplier);
        return neutronsPerSecond * perNeutron / 20.0;
    }

    /** Beam packets a second the reactor makes of a beam. See Note [Source Chamber beams]. */
    public static double beamPacketsPerSecond(double energyKeV, int rate, double multiplier) {
        return energyKeV * rate * multiplier;
    }
}
