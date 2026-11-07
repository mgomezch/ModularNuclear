package com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli;

import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData;
import com.gtnewhorizons.modularnuclear.common.config.data.FuelData;
import com.gtnewhorizons.modularnuclear.common.nuclear.TileContent;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile;

/** The tile codes the simulator knows, with the items and fluids each stands for. */
public final class TileCatalog {

    private TileCatalog() {}

    /**
     * Every tile code with its configuration entry and that entry's item/fluid matching patterns, as JSON. Tools that
     * build simulated layouts in game resolve items from this, so a code always places an item the game gives the
     * same properties the simulator uses.
     */
    public static String json() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (SimTile.TileType type : SimTile.TileType.values()) {
            TileContent c = SimTile.contentOf(type);
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"code\":")
                .append(jsonString(type.code))
                .append(",\"type\":")
                .append(jsonString(type.name()))
                .append(",\"name\":")
                .append(jsonString(type.displayName))
                .append(",\"configId\":")
                .append(type.configId == null ? "null" : jsonString(type.configId))
                .append(",\"kind\":")
                .append(jsonString(c.kind.name()))
                .append(",\"matching\":[");
            java.util.List<String> m = matchingOf(c);
            for (int i = 0; i < m.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(jsonString(m.get(i)));
            }
            sb.append("]}");
        }
        return sb.append(']')
            .toString();
    }

    private static java.util.List<String> matchingOf(TileContent c) {
        Object d = c.data;
        if (d instanceof FuelData f) return f.matching;
        if (d instanceof ComponentData.ModeratorData x) return x.matching;
        if (d instanceof ComponentData.ReflectorData x) return x.matching;
        if (d instanceof ComponentData.InsulatorData x) return x.matching;
        if (d instanceof ComponentData.RadiovoltaicData x) return x.matching;
        if (d instanceof ComponentData.HeatVentData x) return x.matching;
        if (d instanceof ComponentData.HeatExchangerData x) return x.matching;
        if (d instanceof ComponentData.CoolantItemData x) return x.matching;
        if (d instanceof ComponentData.CoolantFluidData x) return x.matching;
        return java.util.Collections.emptyList();
    }

    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' || ch == '\\') sb.append('\\');
            sb.append(ch);
        }
        return sb.append('"')
            .toString();
    }
}
