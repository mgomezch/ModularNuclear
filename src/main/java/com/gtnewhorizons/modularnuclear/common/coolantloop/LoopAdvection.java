package com.gtnewhorizons.modularnuclear.common.coolantloop;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Heat carried around a coolant loop by its flow. Each segment is a well-mixed volume: over a step of dt seconds, the
 * coolant flowing in from upstream (flow x the segment's flow fraction x dt) mixes into the coolant it holds. Heat
 * moves between segments in proportion to the flow; a segment's volume only sets how quickly its temperature
 * follows.
 * <p>
 * The step is implicit: a segment's change follows the inflow at the start and at the end of the step,
 *
 * <pre>
 * V (T' - T) = c [theta (Tin' - T') + (1 - theta) (Tin - T)],   c = flow x fraction x dt
 * </pre>
 *
 * with theta between 1/2 (trapezoidal) and 1 (backward Euler) chosen so that no temperature overshoots. It is stable
 * at any flow and conserves the loop's heat exactly when the flow fractions are consistent (a junction's outflows add
 * up to its inflows). Each segment's new temperature is affine in its new inflow temperature, so
 * cutting the loop at its first segment leaves a graph that one pass in flow order resolves, closed by a single
 * equation for the first segment's inflow. The cost does not depend on the flow. A loop with a cycle that avoids the
 * first segment falls back to a direct solve.
 */
public final class LoopAdvection {

    /*-
    Note [The advection graph is kept between steps] (generated)
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    A step needs the loop's flow graph by segment index (Graph): each segment's upstreams, their mixing weights,
    and the order in which solveByCut resolves the segments once the edges into segment 0 are cut.  Building it
    takes an IdentityHashMap from segment to index, arrays per segment and a topological sort.  A loop of 40
    segments that did this every tick spent most of its advection time on it in the profiles of the showcase
    designs (about 0.16 of 0.19 ms per tick for four loops).
    
    A loop's graph changes only when its segments are replaced.  The rule that makes this so:
    
        A segment's wiring (its upstream segments) and its flow fraction are set before it goes into a
        CoolantLoopEngine, and never changed while it is in one.
    
    LoopGraphCrawler builds new segments on every crawl, sets their flow fractions and wires them, and the pump
    installs them with CoolantLoopEngine.replaceSegments or replaceSegmentsKeepingCoolant.  copyStateFrom carries
    temperatures, pressures and gases over from the old segments, not wiring.  CoolantLoopEngine.importState builds
    and wires new segments from a save in the same way.  The engine hands its segments out read-only
    (getSegments), so they change only through its own methods.
    
    So the engine keeps a Plan, which holds the last Graph, and every engine method that changes the segment list
    calls segmentsChanged, which drops it.  The next step builds a new graph: once per crawl, every 10 seconds or
    so for a running loop.  Nothing is checked per step except that the segment count still matches, so that a
    break of the rule can't index past the end of the graph's arrays.
    
    LoopAdvection.step without a Plan builds the graph afresh each time, for code (and tests) that wire segments
    by hand between steps.  CoolantLoopEngineTest.testReplacedSegmentsGetANewAdvectionGraph checks that an engine
    whose segments are replaced by differently wired ones advects exactly as a new engine with those segments.
    */

    private LoopAdvection() {}

    /** What a loop keeps between steps: its last graph. See Note [The advection graph is kept between steps] */
    public static final class Plan {

        private Graph graph;

        /** Drops the graph: the segments were replaced. */
        void segmentsChanged() {
            graph = null;
        }

        Graph graphFor(List<LoopSegment> segments) {
            Graph g = graph;
            if (g == null || g.upstreams.length != segments.size()) {
                g = Graph.of(segments);
                graph = g;
            }
            return g;
        }
    }

    /**
     * Advances the segments' temperatures by {@code dt} seconds at {@code flowM3s} volumetric flow, sets each
     * segment's incoming temperature, and returns the hottest new temperature.
     */
    public static double step(List<LoopSegment> segments, double flowM3s, double dt) {
        return step(new Plan(), segments, flowM3s, dt);
    }

    /** As {@link #step(List, double, double)}, reusing the plan's graph while the wiring stays the same. */
    public static double step(Plan plan, List<LoopSegment> segments, double flowM3s, double dt) {
        int n = segments.size();
        Graph g = plan.graphFor(segments);

        double[] old = g.old;
        for (int i = 0; i < n; i++) old[i] = segments.get(i)
            .getCurrentTemperatureCelsius();
        double theta = theta(segments, g, flowM3s, dt);

        // V (T' - T) = c [theta (Tin' - T') + (1 - theta) (Tin - T)], rearranged as T'_i = a_i + b_i * Tin'_i
        double[] a = g.a;
        double[] b = g.b;
        for (int i = 0; i < n; i++) {
            LoopSegment s = segments.get(i);
            double inflow = flowM3s * s.getFlowFraction() * dt;
            double volume = s.getVolume();
            if (g.upstreams[i].length == 0) {
                a[i] = old[i];
                b[i] = 0.0;
            } else if (volume > 0.0) {
                double explicit = (1.0 - theta) * inflow * (g.mix(i, old) - old[i]);
                a[i] = (volume * old[i] + explicit) / (volume + theta * inflow);
                b[i] = theta * inflow / (volume + theta * inflow);
            } else {
                a[i] = 0.0;
                b[i] = 1.0;
            }
        }

        double[] next = g.solveByCut(a, b);
        if (next == null) next = g.solveDirect(a, b);
        if (next == null) return maxTemperature(segments);

        double max = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            LoopSegment s = segments.get(i);
            s.setAdvectedTemperatureCelsius(next[i]);
            s.setIncomingTemperatureCelsius(g.upstreams[i].length == 0 ? next[i] : g.mix(i, next));
            s.markAdvected();
            max = Math.max(max, next[i]);
        }
        return max;
    }

    /**
     * The same implicit step for any quantity carried by the flow, with per-segment capacities and inflows: what a gas
     * loop advects (its enthalpy, with each segment's mass as capacity and its mass flow as inflow). A fixed segment
     * keeps the value given (a compressor's outlet, say) and only feeds its downstreams; a source adds to a
     * segment's content over the step (heat, in units of capacity x value). Updates {@code value} in place and
     * fills {@code incoming} with each segment's mixed inflow. backwardEuler fixes theta at 1, which conserves the
     * quantity exactly when inflows differ from outflows along the loop (a gas loop's mass flows). See Note [Gas
     * loop pressure zones]
     *
     * @param capacity what each segment holds (kg for a gas loop)
     * @param inflow   what flows into each segment over the step (kg)
     */
    public static void stepScalar(Plan plan, List<LoopSegment> segments, double[] capacity, double[] inflow,
        double[] source, boolean[] fixed, double[] value, double[] incoming, boolean backwardEuler) {
        int n = segments.size();
        Graph g = plan.graphFor(segments);
        double[] old = g.old;
        System.arraycopy(value, 0, old, 0, n);
        double theta = backwardEuler ? 1.0 : 0.5;
        for (int i = 0; i < n && !backwardEuler; i++) {
            if (fixed[i] || g.upstreams[i].length == 0 || inflow[i] <= 0.0) continue;
            theta = Math.max(theta, capacity[i] > 0.0 ? 1.0 - capacity[i] / inflow[i] : 1.0);
        }
        double[] a = g.a;
        double[] b = g.b;
        for (int i = 0; i < n; i++) {
            double c = capacity[i];
            if (fixed[i]) {
                a[i] = old[i];
                b[i] = 0.0;
            } else if (g.upstreams[i].length == 0) {
                a[i] = c > 0.0 ? old[i] + source[i] / c : old[i];
                b[i] = 0.0;
            } else if (c > 0.0 || inflow[i] > 0.0) {
                double in = inflow[i];
                double explicit = (1.0 - theta) * in * (g.mix(i, old) - old[i]);
                double denom = c + theta * in;
                a[i] = (c * old[i] + explicit + source[i]) / denom;
                b[i] = theta * in / denom;
            } else {
                a[i] = old[i];
                b[i] = 0.0;
            }
        }
        double[] next = g.solveByCut(a, b);
        if (next == null) next = g.solveDirect(a, b);
        if (next == null) return;
        for (int i = 0; i < n; i++) {
            value[i] = next[i];
            incoming[i] = g.upstreams[i].length == 0 ? next[i] : g.mix(i, next);
        }
    }

    /** Segment indices upstream of each segment, by the rule Graph.of uses (for code that needs the loop's shape). */
    public static int[][] upstreamIndices(Plan plan, List<LoopSegment> segments) {
        return plan.graphFor(segments).upstreams;
    }

    /**
     * How implicit the step is: 1/2 (trapezoidal, second order) when no segment takes in more than twice its volume
     * in the step, otherwise just enough to keep every new temperature a weighted average of old ones (no
     * overshoot), up to 1 (backward Euler) at very high flow. One value for the whole loop, as exact conservation
     * needs the inflow of each segment and the outflow of its upstream weighted alike.
     */
    private static double theta(List<LoopSegment> segments, Graph g, double flowM3s, double dt) {
        double theta = 0.5;
        for (int i = 0; i < segments.size(); i++) {
            if (g.upstreams[i].length == 0) continue;
            LoopSegment s = segments.get(i);
            double inflow = flowM3s * s.getFlowFraction() * dt;
            if (inflow <= 0.0) continue;
            double volume = s.getVolume();
            theta = Math.max(theta, volume > 0.0 ? 1.0 - volume / inflow : 1.0);
        }
        return theta;
    }

    private static double maxTemperature(List<LoopSegment> segments) {
        double max = -Double.MAX_VALUE;
        for (LoopSegment s : segments) max = Math.max(max, s.getCurrentTemperatureCelsius());
        return max;
    }

    /**
     * The loop's flow graph by segment index, with each segment's upstreams and their mixing weights, the order
     * solveByCut resolves them in. See Note [The advection graph is kept between steps]
     */
    private static final class Graph {

        final int[][] upstreams;
        final double[][] weights;
        /** Flow order with the edges into segment 0 cut; null when the cut graph still has a cycle. */
        final int[] cutOrder;

        // Scratch for a step
        final double[] old, a, b, alpha, beta;

        private Graph(int[][] upstreams, double[][] weights) {
            this.upstreams = upstreams;
            this.weights = weights;
            this.cutOrder = cutOrder(upstreams);
            int n = upstreams.length;
            old = new double[n];
            a = new double[n];
            b = new double[n];
            alpha = new double[n];
            beta = new double[n];
        }

        /**
         * A segment's upstreams are those it was wired to; one wired to nothing is fed by the segment before it in
         * the list (a plain serial loop). Upstreams mix in proportion to their flow fractions.
         */
        static Graph of(List<LoopSegment> segments) {
            int n = segments.size();
            LoopSegment[] segs = segments.toArray(new LoopSegment[0]);
            Map<LoopSegment, Integer> index = new IdentityHashMap<>(2 * n);
            for (int i = 0; i < n; i++) index.put(segs[i], i);
            int[][] ups = new int[n][];
            double[][] ws = new double[n][];
            for (int i = 0; i < n; i++) {
                List<LoopSegment> wired = segs[i].getUpstreamSegments();
                if (wired.isEmpty()) {
                    ups[i] = n > 1 ? new int[] { (i + n - 1) % n } : new int[0];
                    ws[i] = n > 1 ? new double[] { 1.0 } : new double[0];
                    continue;
                }
                int[] u = new int[wired.size()];
                double[] w = new double[wired.size()];
                int k = 0;
                double total = 0.0;
                for (LoopSegment up : wired) {
                    Integer j = index.get(up);
                    if (j == null) continue;
                    u[k] = j;
                    w[k] = Math.max(0.0, up.getFlowFraction());
                    total += w[k];
                    k++;
                }
                for (int m = 0; m < k; m++) w[m] = total > 0.0 ? w[m] / total : 1.0 / k;
                ups[i] = Arrays.copyOf(u, k);
                ws[i] = Arrays.copyOf(w, k);
            }
            return new Graph(ups, ws);
        }

        /** Kahn's algorithm over the graph with the edges into segment 0 cut; null if a cycle remains. */
        private static int[] cutOrder(int[][] upstreams) {
            int n = upstreams.length;
            int[] pending = new int[n];
            int[] downCount = new int[n];
            for (int i = 1; i < n; i++) {
                pending[i] = upstreams[i].length;
                for (int u : upstreams[i]) downCount[u]++;
            }
            int[][] downstream = new int[n][];
            for (int i = 0; i < n; i++) downstream[i] = new int[downCount[i]];
            int[] fill = new int[n];
            for (int i = 1; i < n; i++) for (int u : upstreams[i]) downstream[u][fill[u]++] = i;
            int[] order = new int[n];
            int head = 0, tail = 0;
            for (int i = 0; i < n; i++) if (pending[i] == 0) order[tail++] = i;
            while (head < tail) {
                int i = order[head++];
                for (int d : downstream[i]) if (--pending[d] == 0) order[tail++] = d;
            }
            return tail == n ? order : null;
        }

        /** Temperature of the coolant flowing into segment i, for the given segment temperatures. */
        double mix(int i, double[] temps) {
            double t = 0.0;
            for (int k = 0; k < upstreams[i].length; k++) t += weights[i][k] * temps[upstreams[i][k]];
            return t;
        }

        /**
         * Solves with segment 0's inflow temperature X as the unknown: with the edges into segment 0 cut, every
         * temperature is alpha + beta X, found in flow order; X then mixes segment 0's upstreams. Returns null when
         * the cut graph still has a cycle.
         */
        double[] solveByCut(double[] a, double[] b) {
            if (cutOrder == null) return null;
            int n = a.length;
            double[] alpha = this.alpha;
            double[] beta = this.beta;
            for (int i : cutOrder) {
                double inAlpha = 0.0, inBeta = 0.0;
                if (i == 0) {
                    inBeta = 1.0;
                } else {
                    for (int k = 0; k < upstreams[i].length; k++) {
                        inAlpha += weights[i][k] * alpha[upstreams[i][k]];
                        inBeta += weights[i][k] * beta[upstreams[i][k]];
                    }
                }
                alpha[i] = a[i] + b[i] * inAlpha;
                beta[i] = b[i] * inBeta;
            }

            double p = 0.0, r = 0.0;
            for (int k = 0; k < upstreams[0].length; k++) {
                p += weights[0][k] * alpha[upstreams[0][k]];
                r += weights[0][k] * beta[upstreams[0][k]];
            }
            // r < 1 whenever the loop holds any coolant; r = 1 would mean a loop of zero volume
            if (1.0 - r < 1e-15) return null;
            double x = p / (1.0 - r);
            double[] temps = new double[n];
            for (int i = 0; i < n; i++) temps[i] = alpha[i] + beta[i] * x;
            return temps;
        }

        /** Gaussian elimination with partial pivoting on T_i - b_i Tin_i = a_i; null if singular. */
        double[] solveDirect(double[] a, double[] b) {
            int n = a.length;
            double[][] m = new double[n][n + 1];
            for (int i = 0; i < n; i++) {
                m[i][i] = 1.0;
                for (int k = 0; k < upstreams[i].length; k++) m[i][upstreams[i][k]] -= b[i] * weights[i][k];
                m[i][n] = a[i];
            }
            for (int col = 0; col < n; col++) {
                int pivot = col;
                for (int row = col + 1; row < n; row++) {
                    if (Math.abs(m[row][col]) > Math.abs(m[pivot][col])) pivot = row;
                }
                if (Math.abs(m[pivot][col]) < 1e-15) return null;
                double[] tmp = m[col];
                m[col] = m[pivot];
                m[pivot] = tmp;
                for (int row = col + 1; row < n; row++) {
                    double f = m[row][col] / m[col][col];
                    if (f == 0.0) continue;
                    for (int c = col; c <= n; c++) m[row][c] -= f * m[col][c];
                }
            }
            double[] temps = new double[n];
            for (int i = n - 1; i >= 0; i--) {
                double s = m[i][n];
                for (int c = i + 1; c < n; c++) s -= m[i][c] * temps[c];
                temps[i] = s / m[i][i];
            }
            return temps;
        }
    }
}
