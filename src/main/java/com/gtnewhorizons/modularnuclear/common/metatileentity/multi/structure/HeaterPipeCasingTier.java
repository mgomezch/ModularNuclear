package com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.modularnuclear.common.coolantloop.HeaterCasing;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.common.blocks.BlockCasings11;

/**
 * Item pipe casing tiers ({@link GregTechAPI#sBlockCasings11}, meta 0..7, casingTier 1..8) used in the centerline of
 * the Pressurized Fluid Heater.
 *
 * <p>
 * Each tier maps to its casing material, passage voltage tier (used for heat capacity and convective heat transfer) and
 * maximum operating temperature (°C, exceeding which causes a powerfail and loop disconnection). Any casing tier works
 * in a loop of any pipe material: the heater's pressure drop and burst pressure are those of the loop pipe it is
 * attached to, while the casings set its heat transfer and temperature limit.
 */
public enum HeaterPipeCasingTier {

    TIN(1, 0, "Tin", Materials.Tin, 1),
    BRASS(2, 1, "Brass", Materials.Brass, 2),
    ELECTRUM(3, 2, "Electrum", Materials.Electrum, 3),
    PLATINUM(4, 3, "Platinum", Materials.Platinum, 4),
    OSMIUM(5, 4, "Osmium", Materials.Osmium, 5),
    QUANTIUM(6, 5, "Quantium", Materials.Quantium, 6),
    FLUXED_ELECTRUM(7, 6, "Fluxed Electrum", Materials.ElectrumFlux, 7),
    BLACK_PLUTONIUM(8, 7, "Black Plutonium", Materials.BlackPlutonium, 8);

    private final int casingTier;
    private final int meta;
    private final String displayName;
    private final Materials casingMaterial;
    private final int passageVoltageTier;

    HeaterPipeCasingTier(int casingTier, int meta, String displayName, Materials casingMaterial,
        int passageVoltageTier) {
        this.casingTier = casingTier;
        this.meta = meta;
        this.displayName = displayName;
        this.casingMaterial = casingMaterial;
        this.passageVoltageTier = passageVoltageTier;
    }

    /** 1-based casing tier (1..8) matching {@code GTStructureUtility.chainItemPipeCasings}. */
    public int getCasingTier() {
        return casingTier;
    }

    /** Block metadata in {@link GregTechAPI#sBlockCasings11} (0..7). */
    public int getMeta() {
        return meta;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Materials getCasingMaterial() {
        return casingMaterial;
    }

    /** Voltage tier (1..8) passed to {@code CoolantPassage.heatCapacity(tier)} and {@code heatTransferFactor(tier)}. */
    public int getPassageVoltageTier() {
        return passageVoltageTier;
    }

    /** Maximum inner operating temperature in °C before the fluid heater powerfails. */
    public double getMaxOperatingTemperatureCelsius() {
        return heaterCasing().maxTemperatureCelsius();
    }

    /** The casing as the game and the simulator share it (its temperature limit). */
    public HeaterCasing heaterCasing() {
        return HeaterCasing.byCasingTier(casingTier);
    }

    public ItemStack getItemStack() {
        if (GregTechAPI.sBlockCasings11 != null) {
            return new ItemStack(GregTechAPI.sBlockCasings11, 1, meta);
        }
        return new ItemStack(Blocks.stone, 1, meta);
    }

    public static HeaterPipeCasingTier getByCasingTier(int casingTier) {
        for (HeaterPipeCasingTier t : values()) {
            if (t.casingTier == casingTier) return t;
        }
        if (casingTier <= 1) return TIN;
        return BLACK_PLUTONIUM;
    }

    public static HeaterPipeCasingTier getByMeta(int meta) {
        for (HeaterPipeCasingTier t : values()) {
            if (t.meta == meta) return t;
        }
        if (meta <= 0) return TIN;
        return BLACK_PLUTONIUM;
    }

    public static HeaterPipeCasingTier fromBlock(Block block, int meta) {
        if (block == null) return null;
        if (block == GregTechAPI.sBlockCasings11 || block instanceof BlockCasings11) {
            if (meta >= 0 && meta < values().length) {
                return getByMeta(meta);
            }
        }
        return null;
    }
}
