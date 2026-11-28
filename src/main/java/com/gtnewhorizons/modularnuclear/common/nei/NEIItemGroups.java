package com.gtnewhorizons.modularnuclear.common.nei;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantPipeSpec;
import com.gtnewhorizons.modularnuclear.common.metatileentity.CoolantLoopMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.CompactLoopMachines;

/*-
Note [NEI item groups from the mod] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
NEI folds related items into one collapsible stack in its item list.  The groups come from the
pack's config/NEI/collapsibleitems.cfg and from each player's presets; NEI has no API for a mod
to add its own.  While ModularNuclear is an addon for playtesting, it adds its groups itself:

    MixinCollapsibleItems runs at the end of NEI's CollapsibleItems.load(), just before NEI
    reads which groups are expanded, and hands NEI's own parser, CollapsibleItems.parseFile,
    the lines that NEIItemGroups.lines() builds.

The lines use collapsibleitems.cfg's syntax, so each group is parsed and shown exactly like the
pack's: a settings line, "; {...}" with the group's name as a lang key, then the filter line,
":gt.blockmachines <ids>", as the pack's own GregTech groups have.  The groups are:

  - the nuclear core fluid hatches, LV to UHV (not the bus, the high-pressure hatches or the
    control rod hatches);
  - the nuclear core high-pressure input hatches, of every material;
  - the nuclear core high-pressure output hatches, of every material;
  - the coolant pipes, one group per size, with every material of that size.

NEI puts an item in the first group that matches it, and ours come after the pack's, so a pack
group that took one of these items would win.  None does in GTNH 2.9.

This calls a private NEI method, which a GTNH mod shouldn't do.  If ModularNuclear is
integrated into GTNH, these lines move into the pack's collapsibleitems.cfg and the mixin goes:
mgomezch/ModularNuclear#44.
*/
public final class NEIItemGroups {

    private NEIItemGroups() {}

    /** The groups, as lines of NEI's collapsibleitems.cfg. */
    public static List<String> lines() {
        List<String> lines = new ArrayList<>();

        group(lines, "nuclear_core_hatches", range(ModMetaTileEntities.ID_NUCLEAR_HATCH_BASE, 9));

        int highPressureMaterials = CoolantPipeSpec.MATERIAL_NAMES.length;
        group(
            lines,
            "high_pressure_input_hatches",
            ids(highPressureMaterials, ModMetaTileEntities::highPressureInputId));
        group(
            lines,
            "high_pressure_output_hatches",
            ids(highPressureMaterials, ModMetaTileEntities::highPressureOutputId));

        int pipeMaterials = CoolantLoopMetaTileEntities.COOLANT_PIPE_MATERIALS.length;
        for (int s = 0; s < CoolantLoopMetaTileEntities.SIZE_NAMES.length; s++) {
            final int size = s;
            group(
                lines,
                "coolant_pipes." + CoolantLoopMetaTileEntities.SIZE_NAMES[s].toLowerCase(),
                ids(pipeMaterials, m -> CoolantLoopMetaTileEntities.getPipeId(m, size)));
        }
        for (CompactLoopMachines.Kind kind : CompactLoopMachines.Kind.values()) {
            group(lines, kind.name, range(kind.firstId, CompactLoopMachines.TIERS));
        }
        return lines;
    }

    private static void group(List<String> lines, String key, String ids) {
        lines.add("; {\"unlocalizedName\": \"modularnuclear.nei.group." + key + "\"}");
        lines.add(":gt.blockmachines " + ids);
    }

    private static String range(int first, int count) {
        return first + "-" + (first + count - 1);
    }

    private static String ids(int count, IntUnaryOperator id) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(',');
            sb.append(id.applyAsInt(i));
        }
        return sb.toString();
    }
}
