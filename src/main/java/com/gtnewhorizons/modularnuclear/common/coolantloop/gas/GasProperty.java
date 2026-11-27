package com.gtnewhorizons.modularnuclear.common.coolantloop.gas;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*-
Note [The gas model] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
A gas loop's working fluid is a GasProperty: an ideal-gas vapour that condenses into an incompressible liquid
under a saturation curve.  It is the simplest model in which both refrigeration cycles work: a condensing gas
flashes cold through a valve, and a permanent gas cools by doing work in an expander.  (An ideal gas throttled
through a valve keeps its temperature, so without a phase change a valve cools nothing.)

State.  A segment's state is its pressure P and specific enthalpy h (J/kg); everything else follows from them
in state():

    T_s = T_sat(P) = B / (A - ln P)         below the critical pressure, T_c at or above it
    L   = latent(T_s) = L_0 + (cp_v - cp_l) (T_s - T_0)        (Kirchhoff, constant heat capacities)
    h_l = cp_l (T_s - T_0)                  saturated liquid
    h_g = h_l + L = cp_v (T_s - T_0) + L_0  saturated vapour

    h >= h_g    vapour      T = T_0 + (h - L_0) / cp_v      rho = P / (R T)
    h <= h_l    liquid      T = T_0 + h / cp_l              rho = rho_l (1 + (P - P_sat(T)) / K)
    otherwise   two-phase   T = T_s, quality x = (h - h_l) / L,  1/rho = x R T_s / P + (1 - x) / rho_l

Enthalpy is zero for saturated liquid at the reference point (T_0, P_0), the normal boiling point or, for CO2,
the triple point.  The saturation curve passes through the reference point and the critical point (T_c, P_c).

Wrinkles:

(W1) Both phases' enthalpies depend on temperature alone, as for an ideal gas and an ideal liquid.  So throttling
     vapour leaves its temperature alone, a Brayton cycle follows the ideal-gas relations exactly, and liquid
     flashing through a valve takes the fraction it should: R-134a at 35 °C throttled to -10 °C flashes 30%
     (28% real).  The price is that the latent heat falls linearly and doesn't vanish at the critical point; the
     model is meant for loops that condense below it.

(W2) The liquid is slightly compressible (bulk modulus LIQUID_BULK_MODULUS_PA).  A zone full of liquid then still
     has a pressure that answers to its mass, so an overcharged high side drives its pressure up until a pipe
     bursts, as in a real system, instead of making the zone's pressure solve impossible.

(W3) At or above the critical pressure T_s is T_c, so between h_l(T_c) and h_g(T_c) the gas sits at T_c as a
     dense two-phase-like mixture: cooled at supercritical pressure it "condenses" at T_c.  This keeps every
     quantity continuous in P and h, and density rising with pressure, which GasLoopEngine's pressure solve needs.

(W4) Entropy (entropy()) is the ideal-gas entropy for vapour, s_v = L_0/T_0 + cp_v ln(T/T_0) - R ln(P/P_0), less
     the latent heat over T_s across the dome, and less cp_l ln(T_s/T) below it.  It is continuous and rises with
     h at fixed P, which is all isentropicEnthalpy's bisection needs, and it gives vapour the ideal-gas isentropes
     (T2/T1 = (P2/P1)^(R/cp)) whatever the saturation curve does.

The defaults (defaults()) fit their saturation curves to real data within a few per cent where loops run: R-134a
gives 10.0 bar at 40 °C (10.2 real) and 1.96 bar at -10 °C (2.01), CO2 64.6 bar at 25 °C (64.3).  Latent heats
are within 10% below 40 °C for R-134a.
GasPropertyTest checks these.
*/
/** A gas a gas loop can carry. See Note [The gas model] */
public final class GasProperty {

    public static final double R_UNIVERSAL = 8.314462618;
    /** One standard atmosphere, the pressure gas litres are counted at (with 0 °C). */
    public static final double ATM_PA = 101_325.0;
    public static final double STANDARD_TEMPERATURE_K = 273.15;
    /** See wrinkle (W2) of Note [The gas model] */
    public static final double LIQUID_BULK_MODULUS_PA = 1.0e8;

    /** What a gas is for: condensing gases run a valve cycle, permanent gases a Brayton cycle. */
    public enum Kind {
        CONDENSING,
        PERMANENT
    }

    private final String fluidName;
    private final Kind kind;
    private final double molarMass; // kg/mol
    private final double gasConstant; // J/(kg K)
    private final double cpVapor; // J/(kg K)
    private final double cpLiquid; // J/(kg K)
    private final double liquidDensity; // kg/m^3
    private final double referenceTemperatureK;
    private final double referencePressurePa;
    private final double criticalTemperatureK;
    private final double criticalPressurePa;
    private final double latentHeatAtReference; // J/kg
    private final double freezingPointK; // NaN for none
    private final double vaporConductivity; // W/(m K)
    private final double liquidConductivity; // W/(m K)
    private final double vaporViscosity; // Pa s
    private final double liquidViscosity; // Pa s
    // ln P = a - b / T
    private final double satA;
    private final double satB;
    private final double gamma;
    private final double standardDensity; // kg/m^3 at 0 °C, 1 atm, as an ideal gas

    private static final Map<String, GasProperty> REGISTRY = new ConcurrentHashMap<>();

    public GasProperty(String fluidName, Kind kind, double molarMassGramsPerMol, double cpVapor, double cpLiquid,
        double liquidDensity, double referenceTemperatureK, double referencePressureBar, double criticalTemperatureK,
        double criticalPressureBar, double latentHeatKjPerKg, double freezingPointCelsius, double vaporConductivity,
        double liquidConductivity, double vaporViscosity, double liquidViscosity) {
        this.fluidName = fluidName;
        this.kind = kind;
        this.molarMass = molarMassGramsPerMol / 1000.0;
        this.gasConstant = R_UNIVERSAL / this.molarMass;
        this.cpVapor = cpVapor;
        this.cpLiquid = cpLiquid;
        this.liquidDensity = liquidDensity;
        this.referenceTemperatureK = referenceTemperatureK;
        this.referencePressurePa = referencePressureBar * 1e5;
        this.criticalTemperatureK = criticalTemperatureK;
        this.criticalPressurePa = criticalPressureBar * 1e5;
        this.latentHeatAtReference = latentHeatKjPerKg * 1000.0;
        this.freezingPointK = Double.isNaN(freezingPointCelsius) ? Double.NaN : freezingPointCelsius + 273.15;
        this.vaporConductivity = vaporConductivity;
        this.liquidConductivity = liquidConductivity;
        this.vaporViscosity = vaporViscosity;
        this.liquidViscosity = liquidViscosity;
        this.satB = Math.log(criticalPressurePa / referencePressurePa)
            / (1.0 / referenceTemperatureK - 1.0 / criticalTemperatureK);
        this.satA = Math.log(referencePressurePa) + satB / referenceTemperatureK;
        this.gamma = cpVapor / (cpVapor - gasConstant);
        this.standardDensity = ATM_PA / (gasConstant * STANDARD_TEMPERATURE_K);
    }

    // --- Registry ---

    public static void register(GasProperty gas) {
        if (gas != null && gas.fluidName != null) REGISTRY.put(gas.fluidName, gas);
    }

    /** The gas registered under exactly this fluid name, or null: the fluid is no gas-loop gas. */
    public static GasProperty get(String fluidName) {
        return fluidName == null ? null : REGISTRY.get(fluidName);
    }

    public static Map<String, GasProperty> getRegistry() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    // The defaults. Fluid names are those GregTech and the mod register.
    public static final GasProperty R134A = new GasProperty(
        "r134a",
        Kind.CONDENSING,
        102.03,
        850.0,
        1400.0,
        1206.0,
        247.08,
        1.01325,
        374.21,
        40.59,
        217.0,
        -103.3,
        0.0145,
        0.081,
        1.2e-5,
        2.0e-4);
    public static final GasProperty AMMONIA = new GasProperty(
        "ammonia",
        Kind.CONDENSING,
        17.03,
        2160.0,
        4700.0,
        682.0,
        239.82,
        1.01325,
        405.4,
        113.33,
        1370.0,
        -77.7,
        0.025,
        0.50,
        1.0e-5,
        1.5e-4);
    public static final GasProperty PROPANE = new GasProperty(
        "propane",
        Kind.CONDENSING,
        44.10,
        1670.0,
        2500.0,
        500.0,
        231.04,
        1.01325,
        369.83,
        42.48,
        426.0,
        -187.7,
        0.018,
        0.094,
        8.0e-6,
        1.1e-4);
    public static final GasProperty CARBON_DIOXIDE = new GasProperty(
        "carbondioxide",
        Kind.CONDENSING,
        44.01,
        850.0,
        2400.0,
        1050.0,
        216.59,
        5.185,
        304.13,
        73.77,
        350.0,
        -56.6,
        0.0166,
        0.11,
        1.5e-5,
        1.0e-4);
    public static final GasProperty NITROGEN = new GasProperty(
        "nitrogen",
        Kind.PERMANENT,
        28.013,
        1040.0,
        2040.0,
        807.0,
        77.36,
        1.01325,
        126.19,
        33.96,
        199.0,
        -210.0,
        0.026,
        0.14,
        1.8e-5,
        1.6e-4);
    public static final GasProperty ARGON = new GasProperty(
        "argon",
        Kind.PERMANENT,
        39.948,
        520.3,
        1120.0,
        1395.0,
        87.30,
        1.01325,
        150.69,
        48.63,
        161.0,
        -189.3,
        0.0177,
        0.12,
        2.2e-5,
        2.5e-4);
    public static final GasProperty HELIUM = new GasProperty(
        "helium",
        Kind.PERMANENT,
        4.0026,
        5193.0,
        4500.0,
        125.0,
        4.222,
        1.01325,
        5.195,
        2.275,
        20.7,
        Double.NaN,
        0.152,
        0.019,
        2.0e-5,
        3.6e-6);

    public static GasProperty[] defaults() {
        return new GasProperty[] { R134A, AMMONIA, PROPANE, CARBON_DIOXIDE, NITROGEN, ARGON, HELIUM };
    }

    static {
        for (GasProperty g : defaults()) register(g);
    }

    // --- Constants ---

    public String getFluidName() {
        return fluidName;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isCondensing() {
        return kind == Kind.CONDENSING;
    }

    public double getMolarMass() {
        return molarMass;
    }

    public double getGasConstant() {
        return gasConstant;
    }

    public double getCpVapor() {
        return cpVapor;
    }

    public double getCpLiquid() {
        return cpLiquid;
    }

    public double getLiquidDensity() {
        return liquidDensity;
    }

    public double getGamma() {
        return gamma;
    }

    public double getCriticalTemperatureK() {
        return criticalTemperatureK;
    }

    public double getCriticalPressurePa() {
        return criticalPressurePa;
    }

    public double getReferenceTemperatureK() {
        return referenceTemperatureK;
    }

    public double getReferencePressurePa() {
        return referencePressurePa;
    }

    /** Freezing point in K, or NaN if the gas never freezes in a loop (helium). */
    public double getFreezingPointK() {
        return freezingPointK;
    }

    public double getVaporConductivity() {
        return vaporConductivity;
    }

    public double getLiquidConductivity() {
        return liquidConductivity;
    }

    public double getVaporViscosity() {
        return vaporViscosity;
    }

    public double getLiquidViscosity() {
        return liquidViscosity;
    }

    /** Density of the gas at 0 °C and 1 atm: what one litre of the gas fluid weighs, per litre. */
    public double getStandardDensity() {
        return standardDensity;
    }

    /** Kilograms of gas in this many litres of the gas fluid (litres at 0 °C and 1 atm). */
    public double massOfLiters(double liters) {
        return liters / 1000.0 * standardDensity;
    }

    /** Litres of the gas fluid (at 0 °C and 1 atm) holding this mass. */
    public double litersOfMass(double kg) {
        return kg / standardDensity * 1000.0;
    }

    // --- Saturation ---

    /** Saturation temperature in K at a pressure in Pa; the critical temperature at or above the critical pressure. */
    public double saturationTemperature(double pressurePa) {
        if (pressurePa >= criticalPressurePa) return criticalTemperatureK;
        return satB / (satA - Math.log(Math.max(pressurePa, 1e-3)));
    }

    /** Saturation pressure in Pa at a temperature in K; the critical pressure at or above the critical temperature. */
    public double saturationPressure(double temperatureK) {
        if (temperatureK >= criticalTemperatureK) return criticalPressurePa;
        return Math.exp(satA - satB / Math.max(temperatureK, 1e-3));
    }

    /**
     * Latent heat of vaporization in J/kg at a saturation temperature in K. See wrinkle (W1) of Note [The gas model]
     */
    public double latentHeat(double saturationTemperatureK) {
        double t = Math.min(saturationTemperatureK, criticalTemperatureK);
        return Math.max(0.0, latentHeatAtReference + (cpVapor - cpLiquid) * (t - referenceTemperatureK));
    }

    /** Enthalpy of liquid at a temperature (any pressure: see wrinkle (W1) of Note [The gas model]). */
    public double liquidEnthalpy(double temperatureK) {
        return cpLiquid * (temperatureK - referenceTemperatureK);
    }

    /** Enthalpy of saturated vapour at a saturation temperature. */
    public double saturatedVaporEnthalpy(double saturationTemperatureK) {
        return cpVapor * (saturationTemperatureK - referenceTemperatureK) + latentHeatAtReference;
    }

    /** Enthalpy of vapour at a temperature (any pressure: see wrinkle (W1) of Note [The gas model]). */
    public double vaporEnthalpy(double temperatureK) {
        return cpVapor * (temperatureK - referenceTemperatureK) + latentHeatAtReference;
    }

    // --- State ---

    public enum Phase {
        LIQUID,
        TWO_PHASE,
        VAPOR
    }

    /** The derived state at a pressure and enthalpy. Mutable, so the engine can reuse one per call site. */
    public static final class State {

        public double pressurePa;
        public double enthalpy;
        public double temperatureK;
        public double saturationTemperatureK;
        /** Vapour mass fraction: 0 for liquid, 1 for vapour. */
        public double quality;
        public double density;
        public Phase phase;

        public double temperatureCelsius() {
            return temperatureK - 273.15;
        }

        /** Degrees above saturation; negative and growing with the liquid fraction below it. */
        public double superheatK() {
            if (phase == Phase.VAPOR) return temperatureK - saturationTemperatureK;
            return -(1.0 - quality) * 20.0 - (saturationTemperatureK - temperatureK);
        }
    }

    /** Fills {@code out} with the state at pressure p (Pa) and enthalpy h (J/kg), and returns it. */
    public State state(double p, double h, State out) {
        double ts = saturationTemperature(p);
        double latent = latentHeat(ts);
        double hg = saturatedVaporEnthalpy(ts);
        double hl = hg - latent;
        out.pressurePa = p;
        out.enthalpy = h;
        out.saturationTemperatureK = ts;
        if (h >= hg) {
            double t = referenceTemperatureK + (h - latentHeatAtReference) / cpVapor;
            out.temperatureK = t;
            out.quality = 1.0;
            out.density = p / (gasConstant * t);
            out.phase = Phase.VAPOR;
        } else if (h <= hl) {
            double t = Math.max(1.0, referenceTemperatureK + h / cpLiquid);
            out.temperatureK = t;
            out.quality = 0.0;
            out.density = liquidDensity(p, t);
            out.phase = Phase.LIQUID;
        } else {
            double x = (h - hl) / latent;
            out.temperatureK = ts;
            out.quality = x;
            out.density = 1.0 / (x * gasConstant * ts / p + (1.0 - x) / liquidDensity);
            out.phase = Phase.TWO_PHASE;
        }
        return out;
    }

    /** Density alone at p and h, without filling a State: the pressure solve's inner loop. */
    public double density(double p, double h, double ts, double hg, double latent) {
        if (h >= hg) return p / (gasConstant * (referenceTemperatureK + (h - latentHeatAtReference) / cpVapor));
        double hl = hg - latent;
        if (h <= hl) return liquidDensity(p, Math.max(1.0, referenceTemperatureK + h / cpLiquid));
        double x = (h - hl) / latent;
        return 1.0 / (x * gasConstant * ts / p + (1.0 - x) / liquidDensity);
    }

    /** See wrinkle (W2) of Note [The gas model] */
    private double liquidDensity(double p, double t) {
        double excess = Math.max(0.0, p - saturationPressure(t));
        return liquidDensity * (1.0 + excess / LIQUID_BULK_MODULUS_PA);
    }

    /** Enthalpy at a pressure and temperature: liquid below saturation, vapour above, saturated vapour at it. */
    public double enthalpy(double p, double temperatureK) {
        double ts = saturationTemperature(p);
        return temperatureK >= ts ? vaporEnthalpy(temperatureK) : liquidEnthalpy(temperatureK);
    }

    /** Specific entropy in J/(kg K) at p and h. See wrinkle (W4) of Note [The gas model] */
    public double entropy(double p, double h) {
        double ts = saturationTemperature(p);
        double latent = latentHeat(ts);
        double hg = saturatedVaporEnthalpy(ts);
        if (h >= hg) {
            return vaporEntropy(p, referenceTemperatureK + (h - latentHeatAtReference) / cpVapor);
        }
        double sg = vaporEntropy(p, ts);
        double hl = hg - latent;
        if (h > hl) {
            double x = (h - hl) / latent;
            return sg - (1.0 - x) * latent / ts;
        }
        double t = Math.max(1.0, referenceTemperatureK + h / cpLiquid);
        return sg - latent / ts - cpLiquid * Math.log(ts / t);
    }

    private double vaporEntropy(double p, double t) {
        return latentHeatAtReference / referenceTemperatureK + cpVapor * Math.log(t / referenceTemperatureK)
            - gasConstant * Math.log(p / referencePressurePa);
    }

    /**
     * The enthalpy at pressure pOut with the entropy the state (pIn, hIn) has: where an ideal compressor or expander
     * takes the gas. Vapour that stays vapour has a closed form; otherwise it bisects on h.
     */
    public double isentropicEnthalpy(double pIn, double hIn, double pOut) {
        double s = entropy(pIn, hIn);
        double tsOut = saturationTemperature(pOut);
        // Vapour at the outlet: s = sv(pOut, T)
        double tVapor = referenceTemperatureK * Math.exp(
            (s - latentHeatAtReference / referenceTemperatureK + gasConstant * Math.log(pOut / referencePressurePa))
                / cpVapor);
        if (tVapor >= tsOut) return vaporEnthalpy(tVapor);
        double hi = saturatedVaporEnthalpy(tsOut);
        double lo = liquidEnthalpy(1.0);
        for (int i = 0; i < 80 && hi - lo > 1e-6; i++) {
            double mid = 0.5 * (lo + hi);
            if (entropy(pOut, mid) < s) lo = mid;
            else hi = mid;
        }
        return 0.5 * (lo + hi);
    }

    @Override
    public String toString() {
        return "GasProperty[" + fluidName + "]";
    }
}
