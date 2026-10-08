package com.gtnewhorizons.modularnuclear.common.nei;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.enums.ItemList;
import gregtech.api.util.GTUtility;

public class NEIFuelStatsHandler extends TemplateRecipeHandler {

    public static final String OVERLAY_ID = "modularnuclear.fuel_stats";

    public class CachedFuelStatsRecipe extends CachedRecipe {

        public final NuclearFuelType fuel;
        public final PositionedStack fuelStack;
        public final ResourceLocation chartTexture;

        public CachedFuelStatsRecipe(NuclearFuelType fuel, List<ItemStack> items) {
            this.fuel = fuel;
            List<ItemStack> displayItems = (items != null && !items.isEmpty()) ? items
                : Collections.singletonList(ItemList.RodThorium.get(1L));
            this.fuelStack = new PositionedStack(displayItems, 12, 10);
            this.chartTexture = new ResourceLocation(
                "modularnuclear",
                "textures/gui/nei/fuelstats/chart_" + fuel.name()
                    .toLowerCase(Locale.US) + ".png");
        }

        @Override
        public PositionedStack getIngredient() {
            return fuelStack;
        }

        @Override
        public PositionedStack getResult() {
            return fuelStack;
        }
    }

    private static void addIfSet(List<ItemStack> list, ItemList item) {
        if (item != null && item.hasBeenSet()) {
            ItemStack stack = item.get(1L);
            if (stack != null) {
                list.add(stack);
            }
        }
    }

    public static List<ItemStack> getFuelStacks(NuclearFuelType fuel) {
        List<ItemStack> list = new ArrayList<>();
        switch (fuel) {
            case GLOWSTONE -> addIfSet(list, ItemList.RodGlowstone);
            case LITHIUM -> addIfSet(list, ItemList.RodLithium);
            case THORIUM -> {
                addIfSet(list, ItemList.RodThorium);
                addIfSet(list, ItemList.RodThorium2);
                addIfSet(list, ItemList.RodThorium4);
            }
            case URANIUM -> {
                addIfSet(list, ItemList.RodUranium);
                addIfSet(list, ItemList.RodUranium2);
                addIfSet(list, ItemList.RodUranium4);
            }
            case MOX -> {
                addIfSet(list, ItemList.RodMOX);
                addIfSet(list, ItemList.RodMOX2);
                addIfSet(list, ItemList.RodMOX4);
            }
            case HD_URANIUM -> {
                addIfSet(list, ItemList.RodHighDensityUranium);
                addIfSet(list, ItemList.RodHighDensityUranium2);
                addIfSet(list, ItemList.RodHighDensityUranium4);
            }
            case HD_PLUTONIUM -> {
                addIfSet(list, ItemList.RodHighDensityPlutonium);
                addIfSet(list, ItemList.RodHighDensityPlutonium2);
                addIfSet(list, ItemList.RodHighDensityPlutonium4);
            }
            case EXCITED_URANIUM -> {
                addIfSet(list, ItemList.RodExcitedUranium);
                addIfSet(list, ItemList.RodExcitedUranium2);
                addIfSet(list, ItemList.RodExcitedUranium4);
            }
            case EXCITED_PLUTONIUM -> {
                addIfSet(list, ItemList.RodExcitedPlutonium);
                addIfSet(list, ItemList.RodExcitedPlutonium2);
                addIfSet(list, ItemList.RodExcitedPlutonium4);
            }
            case NAQUADAH -> {
                addIfSet(list, ItemList.RodNaquadah);
                addIfSet(list, ItemList.RodNaquadah2);
                addIfSet(list, ItemList.RodNaquadah4);
            }
            case NAQUADRIA -> {
                addIfSet(list, ItemList.RodNaquadria);
                addIfSet(list, ItemList.RodNaquadria2);
                addIfSet(list, ItemList.RodNaquadria4);
            }
            case TIBERIUM -> {
                addIfSet(list, ItemList.RodTiberium);
                addIfSet(list, ItemList.RodTiberium2);
                addIfSet(list, ItemList.RodTiberium4);
            }
            case THE_CORE -> addIfSet(list, ItemList.RodNaquadah32);
        }
        return list;
    }

    public NEIFuelStatsHandler() {}

    @Override
    public String getRecipeName() {
        return StatCollector.canTranslate("modularnuclear.nei.fuel_stats.name")
            ? StatCollector.translateToLocal("modularnuclear.nei.fuel_stats.name")
            : "MPTR fuels";
    }

    @Override
    public String getGuiTexture() {
        return "gregtech:textures/gui/nei/neutron_interaction_atlas.png";
    }

    @Override
    public String getOverlayIdentifier() {
        return OVERLAY_ID;
    }

    @Override
    public int recipiesPerPage() {
        return 1;
    }

    @Override
    public void loadTransferRects() {
        this.transferRects.add(new RecipeTransferRect(new Rectangle(6, 68, 154, 76), getOverlayIdentifier()));
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        if (outputId != null && outputId.equals(getOverlayIdentifier())) {
            for (NuclearFuelType fuel : NuclearFuelType.values()) {
                this.arecipes.add(new CachedFuelStatsRecipe(fuel, getFuelStacks(fuel)));
            }
        } else {
            super.loadCraftingRecipes(outputId, results);
        }
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        if (result == null) return;
        for (NuclearFuelType fuel : NuclearFuelType.values()) {
            if (matches(result, fuel)) {
                this.arecipes.add(new CachedFuelStatsRecipe(fuel, getFuelStacks(fuel)));
            }
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        if (ingredient == null) return;

        // Clicking on the nuclear reactor catalyst shows all fuels
        if (ModMetaTileEntities.reactor != null && ModMetaTileEntities.reactor.isItemEqual(ingredient)) {
            for (NuclearFuelType fuel : NuclearFuelType.values()) {
                this.arecipes.add(new CachedFuelStatsRecipe(fuel, getFuelStacks(fuel)));
            }
            return;
        }

        for (NuclearFuelType fuel : NuclearFuelType.values()) {
            if (matches(ingredient, fuel)) {
                this.arecipes.add(new CachedFuelStatsRecipe(fuel, getFuelStacks(fuel)));
            }
        }
    }

    private boolean matches(ItemStack target, NuclearFuelType fuel) {
        if (target == null || fuel == null) return false;
        NuclearFuelType resolved = NuclearFuelType.fromName(target.getUnlocalizedName());
        if (resolved == fuel) return true;

        List<ItemStack> items = getFuelStacks(fuel);
        for (ItemStack item : items) {
            if (GTUtility.areStacksEqual(target, item, true)) return true;
            if (target.getItem() == item.getItem()) {
                return target.getItemDamage() == OreDictionary.WILDCARD_VALUE
                    || item.getItemDamage() == OreDictionary.WILDCARD_VALUE
                    || target.getItemDamage() == item.getItemDamage();
            }
        }
        return false;
    }

    @Override
    public void drawBackground(int recipeIndex) {
        if (recipeIndex < 0 || recipeIndex >= arecipes.size()) return;
        CachedRecipe cached = this.arecipes.get(recipeIndex);
        if (!(cached instanceof CachedFuelStatsRecipe recipe)) return;

        // Slot box for the fuel rod icon
        NEINeutronInteractionHandler.drawSlotBox(11, 9);

        // Chart container panel
        NEINeutronInteractionHandler.drawPanel(6, 68, 154, 78);

        // Render dual reactivity & durability damage curve chart
        GuiDraw.changeTexture(recipe.chartTexture);
        NEINeutronInteractionHandler.drawCustomTexturedModalRect(6, 69, 0, 0, 154, 76, 154, 76);
    }

    @Override
    public void drawExtras(int recipeIndex) {
        if (recipeIndex < 0 || recipeIndex >= arecipes.size()) return;
        CachedRecipe cached = this.arecipes.get(recipeIndex);
        if (!(cached instanceof CachedFuelStatsRecipe recipe)) return;

        NuclearFuelType fuel = recipe.fuel;

        // Fuel display title
        String title = EnumChatFormatting.BOLD + fuel.displayName + " Fuel";
        GuiDraw.drawString(title, 36, 10, 0x111111, false);

        // Line 1: Peak & Floor Reactivity Temperatures
        String l1 = String.format(
            Locale.US,
            "Peak: %s%.0f °C%s | Floor: %s%.0f °C",
            EnumChatFormatting.DARK_AQUA,
            fuel.peakReactivityTemp,
            EnumChatFormatting.DARK_GRAY,
            EnumChatFormatting.RED,
            fuel.floorTemp);
        GuiDraw.drawString(l1, 36, 21, 0x333333, false);

        // Line 2: 10% Reactivity Point & Damage Ratio
        String l2 = String.format(
            Locale.US,
            "10%% Point: %s%.0f °C%s (Ratio: %s%.1f%%%s)",
            EnumChatFormatting.BLUE,
            fuel.temp10Percent,
            EnumChatFormatting.DARK_GRAY,
            EnumChatFormatting.DARK_GREEN,
            fuel.damageRatio,
            EnumChatFormatting.DARK_GRAY);
        GuiDraw.drawString(l2, 12, 33, 0x333333, false);

        // Line 3: Fission Multiplier & Base Neutron Output
        String l3 = String.format(
            Locale.US,
            "Fission Mult: %s%.2fx%s | Neutrons: %s%d/t",
            EnumChatFormatting.DARK_PURPLE,
            fuel.baseThermalFissionMultiplier,
            EnumChatFormatting.DARK_GRAY,
            EnumChatFormatting.DARK_RED,
            fuel.baseNeutrons);
        GuiDraw.drawString(l3, 12, 44, 0x333333, false);

        // Line 4: Durability & Growth Parameter k
        String l4 = String.format(
            Locale.US,
            "Durability: %s%,d%s | k: %s%.6f",
            EnumChatFormatting.DARK_GREEN,
            fuel.defaultDurability,
            EnumChatFormatting.DARK_GRAY,
            EnumChatFormatting.BLACK,
            fuel.damageK);
        GuiDraw.drawString(l4, 12, 55, 0x333333, false);
    }
}
