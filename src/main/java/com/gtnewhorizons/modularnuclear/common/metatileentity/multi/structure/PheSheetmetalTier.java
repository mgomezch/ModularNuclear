package com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

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

    COPPER(0, Materials.Copper),
    ELECTRUM(1, Materials.Electrum),
    PLATINUM(2, Materials.Platinum),
    OSMIUM(3, Materials.Osmium),
    QUANTIUM(4, Materials.Quantium),
    FLUXED_ELECTRUM(5, Materials.ElectrumFlux),
    BLACK_PLUTONIUM(6, Materials.BlackPlutonium),
    NEUTRONIUM(7, Materials.Neutronium);

    private final int tier;
    private final Materials material;
    private final double heatTransferMultiplier;
    private final double maxTemperatureCelsius;
    private final String displayName;

    /** Heat transfer, temperature limit and name come from {@link PheGeometry}, which the simulator shares. */
    PheSheetmetalTier(int tier, Materials material) {
        this.tier = tier;
        this.material = material;
        this.heatTransferMultiplier = PheGeometry.SHEETMETAL_HEAT_TRANSFER[tier];
        this.maxTemperatureCelsius = PheGeometry.SHEETMETAL_MAX_TEMP_C[tier];
        this.displayName = PheGeometry.SHEETMETAL_NAMES[tier];
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

    /** This tier's GregTech sheetmetal block, or null without GregTech. */
    public ItemStack getItemStack() {
        if (GregTechAPI.sBlockSheetmetalGT == null) return null;
        return new ItemStack(GregTechAPI.sBlockSheetmetalGT, 1, material.mMetaItemSubID);
    }

    public static PheSheetmetalTier getByTier(int tier) {
        if (tier <= 0) return COPPER;
        if (tier >= values().length) return values()[values().length - 1];
        return values()[tier];
    }

    /**
     * The tier of a block: GregTech sheetmetal of exactly one of the tiers' materials, or null for anything else
     * (which fails the structure check like any other wrong block).
     */
    public static PheSheetmetalTier fromBlock(Block block, int meta) {
        if (block == null || block != GregTechAPI.sBlockSheetmetalGT) return null;
        for (PheSheetmetalTier tier : values()) {
            if (tier.material.mMetaItemSubID == meta) return tier;
        }
        return null;
    }

    public static PheSheetmetalTier fromTrigger(ItemStack trigger) {
        if (trigger == null) return COPPER;
        int tierVal = CoolantStructureChannels.PHE_SHEETMETAL.getValueClamped(trigger, 0, values().length - 1);
        return getByTier(tierVal);
    }
}
