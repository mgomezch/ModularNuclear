package com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizons.modularnuclear.common.metatileentity.CoolantStructureChannels;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;

/**
 * Material tiers for pressurized heat exchanger (PHE) sheetmetal.
 * Base tier is Copper (1x heat transfer, 2000 °C max operating temp).
 * Each subsequent tier doubles heat transfer effectiveness (2x, 4x, 8x, 16x, 32x)
 * and increases safe maximum operating temperature for coolant loops.
 */
public enum PheSheetmetalTier {

    COPPER(0, Materials.Copper, 1.0, 2000.0, "Copper", 35),
    STEEL(1, Materials.Steel, 2.0, 2500.0, "Steel", 305),
    STAINLESS_STEEL(2, Materials.StainlessSteel, 4.0, 3000.0, "Stainless Steel", 306),
    TITANIUM(3, Materials.Titanium, 8.0, 5000.0, "Titanium", 28),
    TUNGSTEN_STEEL(4, Materials.TungstenSteel, 16.0, 7500.0, "Tungstensteel", 316),
    NEUTRONIUM(5, Materials.Neutronium, 32.0, 1000000.0, "Neutronium", 129);

    private final int tier;
    private final Materials material;
    private final double heatTransferMultiplier;
    private final double maxTemperatureCelsius;
    private final String displayName;
    private final int fallbackSubId;

    PheSheetmetalTier(int tier, Materials material, double heatTransferMultiplier, double maxTemperatureCelsius,
        String displayName, int fallbackSubId) {
        this.tier = tier;
        this.material = material;
        this.heatTransferMultiplier = heatTransferMultiplier;
        this.maxTemperatureCelsius = maxTemperatureCelsius;
        this.displayName = displayName;
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
                    return ores.get(0).copy();
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
        if (mat == Materials.Bronze || mat == Materials.Brass || mat == Materials.Iron) {
            return COPPER;
        }
        if (mat == Materials.Invar) {
            return STEEL;
        }
        if (mat == Materials.Aluminium) {
            return STAINLESS_STEEL;
        }
        if (mat == Materials.Tungsten) {
            return TUNGSTEN_STEEL;
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
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    public static PheSheetmetalTier fromTrigger(ItemStack trigger) {
        if (trigger == null) return COPPER;
        int tierVal = CoolantStructureChannels.PHE_SHEETMETAL.getValueClamped(trigger, 0, 5);
        return getByTier(tierVal);
    }
}
