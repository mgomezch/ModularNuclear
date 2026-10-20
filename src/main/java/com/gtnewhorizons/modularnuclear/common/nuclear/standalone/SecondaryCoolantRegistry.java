package com.gtnewhorizons.modularnuclear.common.nuclear.standalone;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dynamic registry of secondary coolants for heat exchangers and power generation.
 */
public final class SecondaryCoolantRegistry {

    private static final Map<String, SecondaryCoolant> REGISTRY = new LinkedHashMap<>();

    public static final SecondaryCoolant DISTILLED_WATER = register(
        new SecondaryCoolant(
            "DISTILLED_WATER",
            "Distilled Water",
            "ic2distilledwater",
            100.0,
            2260.0,
            160,
            "steam",
            true));
    public static final SecondaryCoolant WATER = register(
        new SecondaryCoolant("WATER", "Water", "water", 100.0, 2260.0, 160, "steam", true));
    public static final SecondaryCoolant LIQUID_AIR = register(
        new SecondaryCoolant("LIQUID_AIR", "Liquid Air", "liquidair", -194.0, 174.0, 160, "air", false));
    public static final SecondaryCoolant LIQUID_NITROGEN = register(
        new SecondaryCoolant(
            "LIQUID_NITROGEN",
            "Liquid Nitrogen",
            "liquidnitrogen",
            -196.0,
            160.0,
            160,
            "nitrogen",
            false));
    public static final SecondaryCoolant LIQUID_OXYGEN = register(
        new SecondaryCoolant("LIQUID_OXYGEN", "Liquid Oxygen", "liquidoxygen", -183.0, 243.0, 160, "oxygen", false));
    public static final SecondaryCoolant LIQUID_HELIUM = register(
        new SecondaryCoolant("LIQUID_HELIUM", "Liquid Helium", "liquidhelium", -269.0, 20.7, 160, "helium", false));

    private SecondaryCoolantRegistry() {}

    public static synchronized SecondaryCoolant register(SecondaryCoolant coolant) {
        if (coolant == null) return null;
        REGISTRY.put(coolant.name.toUpperCase(Locale.ROOT), coolant);
        if (coolant.fluidName != null && !coolant.fluidName.isEmpty()) {
            REGISTRY.put(coolant.fluidName.toLowerCase(Locale.ROOT), coolant);
        }
        return coolant;
    }

    public static synchronized SecondaryCoolant get(String key) {
        if (key == null) return DISTILLED_WATER;
        SecondaryCoolant c = REGISTRY.get(key.toUpperCase(Locale.ROOT));
        if (c != null) return c;
        c = REGISTRY.get(key.toLowerCase(Locale.ROOT));
        if (c != null) return c;
        return null;
    }

    public static SecondaryCoolant fromFluidName(String fluidName) {
        if (fluidName == null) return DISTILLED_WATER;
        for (SecondaryCoolant c : getAll()) {
            if (c.fluidName.equalsIgnoreCase(fluidName)) return c;
        }
        return fromString(fluidName);
    }

    public static SecondaryCoolant fromString(String str) {
        if (str == null) return DISTILLED_WATER;
        String s = str.trim()
            .toUpperCase(Locale.ROOT)
            .replace(" ", "_");
        switch (s) {
            case "DISTILLED_WATER":
            case "DISTILLEDWATER":
            case "IC2DISTILLEDWATER":
                return DISTILLED_WATER;
            case "WATER":
                return WATER;
            case "LIQUID_AIR":
            case "LIQUIDAIR":
            case "AIR":
                return LIQUID_AIR;
            case "LIQUID_NITROGEN":
            case "LIQUIDNITROGEN":
            case "NITROGEN":
                return LIQUID_NITROGEN;
            case "LIQUID_OXYGEN":
            case "LIQUIDOXYGEN":
            case "OXYGEN":
                return LIQUID_OXYGEN;
            case "LIQUID_HELIUM":
            case "LIQUIDHELIUM":
            case "HELIUM":
                return LIQUID_HELIUM;
            default:
                break;
        }
        for (SecondaryCoolant c : getAll()) {
            if (c.name.equalsIgnoreCase(s) || c.displayName.equalsIgnoreCase(str.trim())
                || c.fluidName.equalsIgnoreCase(str.trim())) {
                return c;
            }
        }
        return DISTILLED_WATER;
    }

    public static synchronized List<SecondaryCoolant> getAll() {
        List<SecondaryCoolant> list = new ArrayList<>();
        for (SecondaryCoolant c : REGISTRY.values()) {
            if (!list.contains(c)) {
                list.add(c);
            }
        }
        return list;
    }

    public static SecondaryCoolant[] values() {
        List<SecondaryCoolant> all = getAll();
        return all.toArray(new SecondaryCoolant[0]);
    }
}
