package com.gtnewhorizons.modularnuclear.common.coolantloop.gas;

/*-
Note [Counterflow exchange between two streams] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
A PHE joining two loops (dual-loop mode) holds one segment of each loop.  Exchanging heat between two
well-mixed segments behaves like a parallel-flow exchanger: both outlets approach the same temperature, so at
best half the heat moves.  A recuperator in a Brayton loop needs much better than that (the expander's inlet has
to come out nearly as cold as the returning gas), and real plate exchangers run counterflow.

So exchange() treats the pair as a counterflow exchanger with the effectiveness-NTU method, from the two
streams' inlet states and flows over the step:

    Q_max = min(what the hot stream gives cooling to the cold inlet temperature,
                what the cold stream takes warming to the hot inlet temperature)
    C_i   = stream i's effective heat capacity rate over that range (its enthalpy change over the temperature
            change), C_min and C_max the smaller and larger, Cr = C_min / C_max
    NTU   = UA / C_min
    eps   = (1 - exp(-NTU (1 - Cr))) / (1 - Cr exp(-NTU (1 - Cr)))      (NTU / (1 + NTU) when Cr = 1)
    Q     = eps Q_max

A stream that condenses or boils has a large effective capacity, so Cr falls towards zero and the formula
becomes that of a phase-changing exchanger, eps = 1 - exp(-NTU).  Each side is a Stream, so liquid and gas
loops use the same code; the PHE applies Q to each (MTEPressurizedHeatExchanger).  CounterflowTest checks the
effectiveness against the textbook limits.
*/
/** Heat between two flowing streams, as a counterflow exchanger. See Note [Counterflow exchange between two streams] */
public final class Counterflow {

    private Counterflow() {}

    /** One side of the exchanger, as it enters. */
    public interface Stream {

        /** Inlet temperature, K. */
        double inletK();

        /** Mass through the exchanger over the step, kg (its heat capacity's flow). */
        double massPerStep();

        /** Specific enthalpy (J/kg) of the stream at its inlet. */
        double inletEnthalpy();

        /** Specific enthalpy (J/kg) the stream would have at temperature t (K), at its pressure. */
        double enthalpyAt(double t);
    }

    /** Effectiveness of a counterflow exchanger. */
    public static double effectiveness(double ntu, double cr) {
        if (ntu <= 0.0) return 0.0;
        if (cr < 1e-9) return 1.0 - Math.exp(-ntu);
        if (Math.abs(1.0 - cr) < 1e-6) return ntu / (1.0 + ntu);
        double e = Math.exp(-ntu * (1.0 - cr));
        return (1.0 - e) / (1.0 - cr * e);
    }

    /**
     * Heat (J) the step moves from stream a into stream b (negative if b is the hotter), through a conductance UA
     * (W/K) over dt seconds.
     */
    public static double exchange(Stream a, Stream b, double conductanceWPerK, double dt) {
        double ta = a.inletK(), tb = b.inletK();
        if (Math.abs(ta - tb) < 1e-6) return 0.0;
        Stream hot = ta > tb ? a : b, cold = ta > tb ? b : a;
        double th = hot.inletK(), tc = cold.inletK();
        double mh = hot.massPerStep(), mc = cold.massPerStep();
        if (mh <= 0.0 || mc <= 0.0) return 0.0;
        double gives = mh * Math.max(0.0, hot.inletEnthalpy() - hot.enthalpyAt(tc));
        double takes = mc * Math.max(0.0, cold.enthalpyAt(th) - cold.inletEnthalpy());
        double qMax = Math.min(gives, takes);
        if (qMax <= 0.0) return 0.0;
        double dT = th - tc;
        // Effective heat capacities of the two streams over the step, J/K
        double ch = gives / dT, cc = takes / dT;
        double cMin = Math.min(ch, cc), cMax = Math.max(ch, cc);
        double ntu = conductanceWPerK * dt / cMin;
        double q = effectiveness(ntu, cMin / cMax) * qMax;
        return hot == a ? q : -q;
    }
}
