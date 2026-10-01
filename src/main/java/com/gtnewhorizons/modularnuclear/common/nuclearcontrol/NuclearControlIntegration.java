package com.gtnewhorizons.modularnuclear.common.nuclearcontrol;

import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;

public class NuclearControlIntegration {

    public static ItemCardModularNuclear cardModularNuclear;
    public static ItemKitModularNuclear kitModularNuclear;
    public static boolean isLoaded = false;

    public static void init() {
        if (!Loader.isModLoaded("IC2NuclearControl")) {
            return;
        }
        try {
            cardModularNuclear = new ItemCardModularNuclear();
            GameRegistry.registerItem(cardModularNuclear, "card_modular_nuclear");

            kitModularNuclear = new ItemKitModularNuclear();
            GameRegistry.registerItem(kitModularNuclear, "kit_modular_nuclear");

            isLoaded = true;
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static boolean isSensorItem(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        if (isLoaded && (stack.getItem() == cardModularNuclear || stack.getItem() == kitModularNuclear)) {
            return true;
        }
        String unloc = stack.getItem()
            .getUnlocalizedName();
        return unloc != null && (unloc.contains("cardReactor") || unloc.contains("kitReactor"));
    }
}
