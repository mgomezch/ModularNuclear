package com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizons.modularnuclear.common.coolantloop.PheGeometry;
import com.gtnewhorizons.modularnuclear.common.metatileentity.CoolantStructureChannels;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;

/**
 * Material tiers for pressurized heat exchanger (PHE) sheetmetal.
 * Base tier is Copper (1x heat transfer, 1084 °C max operating temp matching GregTech molten copper).
 * Each subsequent tier doubles heat transfer effectiveness (2x, 4x, 8x, 16x, 32x, 64x, 128x)
 * and increases safe maximum operating temperature for coolant loops.
 */
public enum PheSheetmetalTier {

    COPPER(0, Materials.Copper, 0),
    ELECTRUM(1, Materials.Electrum, 1),
    PLATINUM(2, Materials.Platinum, 2),
    OSMIUM(3, Materials.Osmium, 3),
    QUANTIUM(4, Materials.Quantium, 4),
    FLUXED_ELECTRUM(5, Materials.ElectrumFlux, 5),
    BLACK_PLUTONIUM(6, Materials.BlackPlutonium, 6),
    NEUTRONIUM(7, Materials.Neutronium, 7);

    private final int tier;
    private final Materials material;
    private final double heatTransferMultiplier;
    private final double maxTemperatureCelsius;
    private final String displayName;
    private final int fallbackSubId;

    /** Heat transfer, temperature limit and name come from {@link PheGeometry}, which the simulator shares. */
    PheSheetmetalTier(int tier, Materials material, int fallbackSubId) {
        this.tier = tier;
        this.material = material;
        this.heatTransferMultiplier = PheGeometry.SHEETMETAL_HEAT_TRANSFER[tier];
        this.maxTemperatureCelsius = PheGeometry.SHEETMETAL_MAX_TEMP_C[tier];
        this.displayName = PheGeometry.SHEETMETAL_NAMES[tier];
        this.fallbackSubId = fallbackSubId;
    }

    public int getTier() {
        return tier;
    }

    public Materials getMaterial() {
        return material;
    }

    public double getHeatTransferMultiplier() {
        return heatTransferMultiplier;
    }

    public double getMaxTemperatureCelsius() {
        return maxTemperatureCelsius;
    }

    public double getBonusTemperatureCelsius() {
        return maxTemperatureCelsius - COPPER.maxTemperatureCelsius;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getFallbackSubId() {
        return fallbackSubId;
    }

    public ItemStack getItemStack() {
        try {
            if (material != null) {
                List<ItemStack> ores = OreDictionary.getOres("sheetmetal" + material.mName);
                if (ores != null && !ores.isEmpty() && ores.get(0) != null) {
                    return ores.get(0)
                        .copy();
                }
            }
        } catch (Throwable ignored) {}

        try {
            if (GregTechAPI.sBlockSheetmetalGT != null) {
                int subId = (material != null && material.mMetaItemSubID >= 0) ? material.mMetaItemSubID
                    : fallbackSubId;
                return new ItemStack(GregTechAPI.sBlockSheetmetalGT, 1, subId);
            }
        } catch (Throwable ignored) {}

        return new ItemStack(Blocks.stone, 1, fallbackSubId);
    }

    public static PheSheetmetalTier getByTier(int tier) {
        if (tier <= 0) return COPPER;
        if (tier >= values().length) return values()[values().length - 1];
        return values()[tier];
    }

    public static PheSheetmetalTier getByMaterial(Materials mat) {
        if (mat == null) return COPPER;
        for (PheSheetmetalTier t : values()) {
            if (t.material == mat) return t;
        }
        if (mat == Materials.Bronze || mat == Materials.Brass
            || mat == Materials.Iron
            || mat == Materials.CastIron
            || mat == Materials.WroughtIron) {
            return COPPER;
        }
        if (mat == Materials.Steel || mat == Materials.Invar) {
            return ELECTRUM;
        }
        if (mat == Materials.StainlessSteel || mat == Materials.Aluminium) {
            return PLATINUM;
        }
        if (mat == Materials.Titanium) {
            return OSMIUM;
        }
        if (mat == Materials.TungstenSteel || mat == Materials.Tungsten) {
            return QUANTIUM;
        }
        if (mat == Materials.CosmicNeutronium || mat == Materials.Infinity) {
            return NEUTRONIUM;
        }
        return COPPER;
    }

    public static PheSheetmetalTier fromBlock(Block block, int meta) {
        if (block == null) return null;

        if (block == GregTechAPI.sBlockSheetmetalGT) {
            for (PheSheetmetalTier tier : values()) {
                if (tier.material != null && tier.material.mMetaItemSubID == meta) {
                    return tier;
                }
                if (tier.fallbackSubId == meta) {
                    return tier;
                }
            }
            try {
                if (meta >= 0 && meta < GregTechAPI.sGeneratedMaterials.length) {
                    Materials m = GregTechAPI.sGeneratedMaterials[meta];
                    if (m != null) {
                        return getByMaterial(m);
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (block == Blocks.stone) {
            for (PheSheetmetalTier tier : values()) {
                if (tier.fallbackSubId == meta) {
                    return tier;
                }
            }
            if (meta == 0 || meta == 35) {
                return COPPER;
            }
        }

        try {
            ItemStack stack = new ItemStack(block, 1, meta);
            int[] oreIds = OreDictionary.getOreIDs(stack);
            for (int id : oreIds) {
                String name = OreDictionary.getOreName(id);
                if (name != null && name.startsWith("sheetmetal")) {
                    String sub = name.substring("sheetmetal".length());
                    for (PheSheetmetalTier tier : values()) {
                        if (tier.material != null && tier.material.mName.equalsIgnoreCase(sub)) {
                            return tier;
                        }
                    }
                    if ("FluxedElectrum".equalsIgnoreCase(sub) || "ElectrumFlux".equalsIgnoreCase(sub)) {
                        return FLUXED_ELECTRUM;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    public static PheSheetmetalTier fromTrigger(ItemStack trigger) {
        if (trigger == null) return COPPER;
        int tierVal = CoolantStructureChannels.PHE_SHEETMETAL.getValueClamped(trigger, 0, values().length - 1);
        return getByTier(tierVal);
    }
}
