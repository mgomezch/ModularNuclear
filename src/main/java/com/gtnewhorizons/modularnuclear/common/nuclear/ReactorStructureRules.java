package com.gtnewhorizons.modularnuclear.common.nuclear;

/**
 * Structure rules of the MPTR that do not depend on blocks in the world, shared by the game's controller and the
 * standalone simulator so the simulator accepts exactly what the game accepts. Each rule names the lang key the game
 * shows and an English message the simulator reports.
 */
public final class ReactorStructureRules {

    /** A broken rule: the game's lang key and the simulator's message. */
    public static final class Violation {

        public final String langKey;
        public final String message;

        Violation(String langKey, String message) {
            this.langKey = langKey;
            this.message = message;
        }

        @Override
        public String toString() {
            return message;
        }
    }

    /** Core grid sizes the chambers have (7x7, 11x11 and 15x15 footprints). */
    public static final int[] GRID_SIZES = { 5, 9, 13 };

    private ReactorStructureRules() {}

    /** Chamber size tier (1 to 3) of a core grid size, or 0 for a size no chamber has. */
    public static int sizeTier(int gridSize) {
        for (int i = 0; i < GRID_SIZES.length; i++) if (GRID_SIZES[i] == gridSize) return i + 1;
        return 0;
    }

    /** Weakest pipe casing tier a chamber of the given size tier forms with: Electrum, Platinum, Osmium. */
    public static int minPipeTier(int sizeTier) {
        if (sizeTier >= 3) return NuclearSimulationEngine.PIPE_TIER_OSMIUM;
        if (sizeTier == 2) return NuclearSimulationEngine.PIPE_TIER_PLATINUM;
        return NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
    }

    /** The chamber's size or its pipe casings, if they break a rule. */
    public static Violation chamber(int gridSize, int pipeTier) {
        int sizeTier = sizeTier(gridSize);
        if (sizeTier == 0) {
            return new Violation(
                "GT5U.gui.text.structure_error.invalid_chamber_size",
                "Core grid must be 5x5, 9x9 or 13x13, not " + gridSize + "x" + gridSize);
        }
        if (pipeTier >= minPipeTier(sizeTier)) return null;
        if (sizeTier == 2) {
            return new Violation(
                "GT5U.gui.text.structure_error.invalid_pipe_tier_t2",
                "A 9x9 core needs Platinum item pipe casings or better");
        }
        if (sizeTier == 3) {
            return new Violation(
                "GT5U.gui.text.structure_error.invalid_pipe_tier_t3",
                "A 13x13 core needs Osmium item pipe casings or better");
        }
        return new Violation(
            "GT5U.gui.text.structure_error.invalid_pipe_tier",
            "Item pipe casings must be Electrum or better");
    }

    /** High-pressure passages, if the pipe casing tier does not allow coolant loops. */
    public static Violation passages(int pipeTier) {
        if (NuclearSimulationEngine.allowsCoolantLoops(pipeTier)) return null;
        return new Violation(
            "GT5U.gui.text.structure_error.hp_hatch_tier",
            "High-pressure passages need Osmium item pipe casings or better");
    }

    /** A core hatch's fluid, if the pipe casing tier does not accept it (the game's hatch refuses to take it). */
    public static Violation hatchFluid(String fluidName, int pipeTier) {
        if (fluidName == null || fluidName.isEmpty() || NuclearSimulationEngine.isFluidAllowed(fluidName, pipeTier)) {
            return null;
        }
        return new Violation(
            "GT5U.gui.text.structure_error.hatch_fluid_tier",
            "A core hatch holds " + fluidName + ", which these item pipe casings do not accept");
    }

    /** A loop attached to a core cell that is not a high-pressure passage, which the game cannot build. */
    public static Violation loopAttachment(int x, int y) {
        return new Violation(
            "GT5U.gui.text.structure_error.loop_not_on_passage",
            "A coolant loop is attached at (" + x + ", " + y + "), which is not a high-pressure passage");
    }
}
