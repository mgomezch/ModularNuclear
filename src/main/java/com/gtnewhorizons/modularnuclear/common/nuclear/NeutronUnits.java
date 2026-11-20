package com.gtnewhorizons.modularnuclear.common.nuclear;

/*-
Note [Neutrons are shown in physical units] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
The engine moves a few thousand neutrons a cycle, which would read as nothing next to a real reactor's 10¹⁹ or so a
second.  Every neutron figure shown to players is therefore the engine's count scaled to a physical quantity, with
its unit beside it, by the methods of NeutronUnits:

    count  a number of neutrons, such as a control rod's capacity or what a coolant absorbs before it transmutes:
           count × NEUTRONS_PER_COUNT, in n;
    rate   neutrons emitted, absorbed or escaping per second, in a tile or in the whole core:
           per second × NEUTRONS_PER_COUNT, in n/s;
    flux   neutrons passing through a tile per second, over the area of the tile's face:
           per second × NEUTRONS_PER_COUNT / TILE_AREA_CM2, in n/cm²/s.

NEUTRONS_PER_COUNT is 10¹⁴ and a tile's face is one block, 1 m² = 10⁴ cm², so a tile's flux is its count per second
× 10¹⁰.  With them, a reactor making 2·10⁵ neutrons a second in the engine shows 2·10¹⁹ n/s, and a tile crossed by
10⁴ a second shows 10¹⁴ n/cm²/s: both are what a power reactor of a few hundred megawatts has.  Rates are made
per second before they're scaled, by NuclearSimulationEngine.perSecond; see
Note [Neutron and heat rates are per second].

The units are always written short (n, s, cm²), so that they fit narrow GUI screens and tooltips.  The total
production of a core is a rate, not a flux: no area goes with it.  Fluence, the flux summed over time (n/cm²), is
deliberately not shown anywhere.

The web simulator scales the same way in its own JavaScript, neutronRate and neutronFlux in core.js, whose
constants must stay equal to these.  What is not scaled: the OpenComputers API (NuclearControlEnvironment), which
gives scripts the engine's counts per second, and config files, the heatmaps' colour scales and the operators'
manual, which work in the engine's own counts.
*/
public final class NeutronUnits {

    /** Real neutrons one neutron of the engine stands for. */
    public static final double NEUTRONS_PER_COUNT = 1.0e14;

    /** The area of a tile's face, one block: 1 m². */
    public static final double TILE_AREA_CM2 = 1.0e4;

    private NeutronUnits() {}

    /** A number of the engine's neutrons, as real neutrons: {@code "4.00e15 n"}. */
    public static String count(double count) {
        return number(count * NEUTRONS_PER_COUNT) + " n";
    }

    /** The engine's neutrons per second, as real ones: {@code "2.10e19 n/s"}. */
    public static String rate(double perSecond) {
        return rateValue(perSecond) + " n/s";
    }

    /** The engine's neutrons per second through one tile, as a flux: {@code "1.23e14 n/cm²/s"}. */
    public static String flux(double perSecond) {
        return fluxValue(perSecond) + " n/cm²/s";
    }

    /** {@link #rate} without its unit, for lines that give several rates and the unit once. */
    public static String rateValue(double perSecond) {
        return number(perSecond * NEUTRONS_PER_COUNT);
    }

    /** {@link #flux} without its unit, for lines that give several fluxes and the unit once. */
    public static String fluxValue(double perSecond) {
        return number(perSecond * NEUTRONS_PER_COUNT / TILE_AREA_CM2);
    }

    /**
     * A number in scientific notation with three significant digits, {@code "1.23e14"}; zero, and anything that isn't
     * a number, is {@code "0"}.
     */
    public static String number(double v) {
        return number(v, 3);
    }

    /** {@link #number} with {@code digits} significant digits, 1 to 9: with 2, {@code "1.2e14"}. */
    public static String number(double v, int digits) {
        if (Double.isNaN(v) || Double.isInfinite(v) || v == 0.0) return "0";
        if (v < 0) return "-" + number(-v, digits);
        long low = 1;
        for (int i = 1; i < digits; i++) low *= 10;
        int exp = (int) Math.floor(Math.log10(v));
        long m = Math.round(v / Math.pow(10, exp - digits + 1));
        // log10 and the rounding can each land one decade off: 9.996 rounds to 10.00, and 1000 may come out as 999.99
        if (m >= low * 10) {
            exp++;
            m = Math.round(v / Math.pow(10, exp - digits + 1));
        } else if (m < low) {
            exp--;
            m = Math.round(v / Math.pow(10, exp - digits + 1));
        }
        String frac = Long.toString(m % low + low)
            .substring(1);
        return (m / low) + (frac.isEmpty() ? "" : "." + frac) + "e" + exp;
    }
}
