package com.gtnewhorizons.modularnuclear.common.config;

import java.util.List;

import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizons.modularnuclear.ModularNuclear;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData;
import com.gtnewhorizons.modularnuclear.common.config.data.FuelData;
import com.gtnewhorizons.modularnuclear.common.config.data.TierData;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Warns, once the registries are complete, about every config entry that names no item or fluid in this game: config
 * names are exact, so a typo or a name from another pack would otherwise just never match.
 */
public final class ConfigNameCheck {

    private ConfigNameCheck() {}

    public static void warnUnmatched() {
        int count = 0;
        for (FuelData f : ReactorConfigData.fuels) {
            count += f.isLiquid ? fluids("fuels.yaml " + f.id, f.matching) : items("fuels.yaml " + f.id, f.matching);
            if (f.spentProduct != null && !f.spentProduct.isEmpty()
                && !isItem(f.spentProduct)
                && !isFluid(f.spentProduct)) {
                count += warn("fuels.yaml " + f.id + " spent_product", f.spentProduct);
            }
        }
        ComponentData c = ReactorConfigData.componentData;
        for (var d : c.reflectors) count += items("components.yaml " + d.id, d.matching);
        for (var d : c.moderators) {
            count += items("components.yaml " + d.id, d.matching);
            if (d.spentProduct != null && !d.spentProduct.isEmpty() && !isItem(d.spentProduct)) {
                count += warn("components.yaml " + d.id + " spent_product", d.spentProduct);
            }
        }
        for (var d : c.controlRods) count += items("components.yaml " + d.id, d.matching);
        for (var d : c.heatVents) count += items("components.yaml " + d.id, d.matching);
        for (var d : c.heatExchangers) count += items("components.yaml " + d.id, d.matching);
        for (var d : c.insulators) count += items("components.yaml " + d.id, d.matching);
        for (var d : c.radiovoltaics) count += items("components.yaml " + d.id, d.matching);
        for (var d : c.coolantItems) count += items("components.yaml " + d.id, d.matching);
        for (var d : c.coolantFluids) count += fluids("components.yaml " + d.id, d.matching);
        for (TierData t : ReactorConfigData.tierDataMap.values()) {
            count += fluids("tiers.yaml tier " + t.tier + " allowed_coolants", t.allowedCoolants);
        }
        count += items("blockedItems", ModularNuclearConfig.blockedItems);
        count += items("allowedFuelItems", ModularNuclearConfig.allowedFuelItems);
        count += fluids("allowedFuelFluids", ModularNuclearConfig.allowedFuelFluids);
        count += fluids("blockedFuelFluids", ModularNuclearConfig.blockedFuelFluids);
        count += items("allowedCoolantItems", ModularNuclearConfig.allowedCoolantItems);
        count += fluids("allowedCoolantFluids", ModularNuclearConfig.allowedCoolantFluids);
        count += items("allowedControlRods", ModularNuclearConfig.allowedControlRods);
        count += items("allowedReflectors", ModularNuclearConfig.allowedReflectors);
        count += items("allowedModerators", ModularNuclearConfig.allowedModerators);
        count += items("allowedHeatVents", ModularNuclearConfig.allowedHeatVents);
        count += items("allowedHeatExchangers", ModularNuclearConfig.allowedHeatExchangers);
        count += items("allowedInsulators", ModularNuclearConfig.allowedInsulators);
        count += items("allowedRadiovoltaics", ModularNuclearConfig.allowedRadiovoltaics);
        count += items("allowedNeutronSources", ModularNuclearConfig.allowedNeutronSources);
        if (count > 0) {
            ModularNuclear.LOG.warn(
                "{} config entries name nothing in this game. Items are named modid:name, modid:name:meta or "
                    + "ore:oreName, and fluids by registry name, exactly.",
                count);
        }
    }

    private static int items(String where, String[] entries) {
        return entries == null ? 0 : items(where, java.util.Arrays.asList(entries));
    }

    private static int items(String where, List<String> entries) {
        int n = 0;
        if (entries == null) return 0;
        for (String e : entries) {
            // Forge list entries may carry a value: item=TYPE or item=fraction
            String id = e.contains("=") ? e.substring(0, e.indexOf('=')) : e;
            if (!isItem(id)) n += warn(where, e);
        }
        return n;
    }

    private static int fluids(String where, String[] entries) {
        return entries == null ? 0 : fluids(where, java.util.Arrays.asList(entries));
    }

    private static int fluids(String where, List<String> entries) {
        int n = 0;
        if (entries == null) return 0;
        for (String e : entries) {
            if (!isFluid(e)) n += warn(where, e);
        }
        return n;
    }

    static boolean isItem(String id) {
        if (id.startsWith(ReactorDataRegistry.ORE_PREFIX)) {
            // A name can be registered with no items in it
            return !OreDictionary.getOres(id.substring(ReactorDataRegistry.ORE_PREFIX.length()))
                .isEmpty();
        }
        String[] parts = id.split(":", -1);
        if (parts.length != 2 && parts.length != 3) return false;
        if (parts.length == 3 && !parts[2].matches("\\d+")) return false;
        return GameRegistry.findItem(parts[0], parts[1]) != null;
    }

    static boolean isFluid(String name) {
        return FluidRegistry.isFluidRegistered(name);
    }

    private static int warn(String where, String entry) {
        ModularNuclear.LOG.warn("Config {}: \"{}\" names no item or fluid in this game", where, entry);
        return 1;
    }
}
