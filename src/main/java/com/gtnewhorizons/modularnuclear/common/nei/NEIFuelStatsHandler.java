package com.gtnewhorizons.modularnuclear.common.nei;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.oredict.OreDictionary;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.nuclear.FuelDamageCurveSolver;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.PositionedStack;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerInputHandler;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.enums.ItemList;
import gregtech.api.util.GTUtility;

public class NEIFuelStatsHandler extends TemplateRecipeHandler {

    public static final String OVERLAY_ID = "modularnuclear.fuel_stats";

    public static final int PANEL_X = 6;
    public static final int PANEL_Y = 68;
    public static final int PANEL_W = 154;
    public static final int PANEL_H = 78;

    public static final int SCREEN_X = PANEL_X + 2;
    public static final int SCREEN_Y = PANEL_Y + 2;
    public static final int SCREEN_W = PANEL_W - 4;
    public static final int SCREEN_H = PANEL_H - 4;

    public static final int PLOT_X = 28;
    public static final int PLOT_Y = 80;
    public static final int PLOT_W = 104;
    public static final int PLOT_H = 50;

    // Ambient Temperature Control Bar bounds
    public static final int BAR_X = PANEL_X;
    public static final int BAR_Y = 148;
    public static final int BAR_W = PANEL_W;
    public static final int BAR_H = 14;

    public static final int INPUT_X = 52;
    public static final int INPUT_Y = 149;
    public static final int INPUT_W = 42;
    public static final int INPUT_H = 12;

    public static final int BTN_MINUS_X = 110;
    public static final int BTN_MINUS_Y = 149;
    public static final int BTN_MINUS_W = 12;
    public static final int BTN_MINUS_H = 12;

    public static final int BTN_PLUS_X = 124;
    public static final int BTN_PLUS_Y = 149;
    public static final int BTN_PLUS_W = 12;
    public static final int BTN_PLUS_H = 12;

    public static final int BTN_RESET_X = 138;
    public static final int BTN_RESET_Y = 149;
    public static final int BTN_RESET_W = 18;
    public static final int BTN_RESET_H = 12;

    // Dynamic Ambient Temperature State (defaults to 24.0 °C)
    public static double sAmbientTemp = 24.0;
    public static String sInputText = "24";
    public static boolean sInputFocused = false;

    public static boolean isMouseOverPlot(int x, int y) {
        return x >= PLOT_X && x <= PLOT_X + PLOT_W && y >= PLOT_Y && y <= PLOT_Y + PLOT_H;
    }

    public static boolean isMouseOverInput(int x, int y) {
        return x >= INPUT_X && x < INPUT_X + INPUT_W && y >= INPUT_Y && y < INPUT_Y + INPUT_H;
    }

    public static boolean isMouseOverMinus(int x, int y) {
        return x >= BTN_MINUS_X && x < BTN_MINUS_X + BTN_MINUS_W && y >= BTN_MINUS_Y && y < BTN_MINUS_Y + BTN_MINUS_H;
    }

    public static boolean isMouseOverPlus(int x, int y) {
        return x >= BTN_PLUS_X && x < BTN_PLUS_X + BTN_PLUS_W && y >= BTN_PLUS_Y && y < BTN_PLUS_Y + BTN_PLUS_H;
    }

    public static boolean isMouseOverReset(int x, int y) {
        return x >= BTN_RESET_X && x < BTN_RESET_X + BTN_RESET_W && y >= BTN_RESET_Y && y < BTN_RESET_Y + BTN_RESET_H;
    }

    public static boolean isMouseOverControlBar(int x, int y) {
        return x >= BAR_X && x < BAR_X + BAR_W && y >= BAR_Y && y < BAR_Y + BAR_H;
    }

    public static void updateAmbientFromInput() {
        if (sInputText == null || sInputText.isEmpty() || sInputText.equals("-") || sInputText.equals(".")) {
            return;
        }
        try {
            double val = Double.parseDouble(sInputText);
            if (val < -273.15) val = -273.15;
            if (val > 5000.0) val = 5000.0;
            sAmbientTemp = val;
        } catch (NumberFormatException ignored) {}
    }

    public static void commitInput() {
        updateAmbientFromInput();
        sInputText = (sAmbientTemp == (long) sAmbientTemp) ? String.valueOf((long) sAmbientTemp)
            : String.format(Locale.US, "%.1f", sAmbientTemp);
    }

    public static void adjustAmbientTemp(double delta) {
        sAmbientTemp = Math.max(-273.15, Math.min(5000.0, Math.round((sAmbientTemp + delta) * 10.0) / 10.0));
        sInputText = (sAmbientTemp == (long) sAmbientTemp) ? String.valueOf((long) sAmbientTemp)
            : String.format(Locale.US, "%.1f", sAmbientTemp);
    }

    public static void resetAmbientTemp() {
        sAmbientTemp = 24.0;
        sInputText = "24";
    }

    private static java.lang.reflect.Field fieldGuiLeft;
    private static java.lang.reflect.Field fieldGuiTop;

    static {
        try {
            fieldGuiLeft = net.minecraft.client.gui.inventory.GuiContainer.class.getDeclaredField("guiLeft");
            fieldGuiLeft.setAccessible(true);
        } catch (Throwable t) {
            try {
                fieldGuiLeft = net.minecraft.client.gui.inventory.GuiContainer.class.getDeclaredField("field_147003_i");
                fieldGuiLeft.setAccessible(true);
            } catch (Throwable ignored) {}
        }
        try {
            fieldGuiTop = net.minecraft.client.gui.inventory.GuiContainer.class.getDeclaredField("guiTop");
            fieldGuiTop.setAccessible(true);
        } catch (Throwable t) {
            try {
                fieldGuiTop = net.minecraft.client.gui.inventory.GuiContainer.class.getDeclaredField("field_147009_r");
                fieldGuiTop.setAccessible(true);
            } catch (Throwable ignored) {}
        }
        try {
            GuiContainerManager.addInputHandler(new FuelStatsInputHandler());
        } catch (Throwable ignored) {}
    }

    private static int getGuiLeft(GuiRecipe<?> gui) {
        if (fieldGuiLeft != null) {
            try {
                return fieldGuiLeft.getInt(gui);
            } catch (Throwable ignored) {}
        }
        return (gui.width - 176) / 2;
    }

    private static int getGuiTop(GuiRecipe<?> gui) {
        if (fieldGuiTop != null) {
            try {
                return fieldGuiTop.getInt(gui);
            } catch (Throwable ignored) {}
        }
        return (gui.height - 166) / 2;
    }

    public static Point getMouseInRecipe(GuiRecipe<?> gui, int recipeIndex) {
        if (gui == null) return null;
        Point mousepos = GuiDraw.getMousePosition();
        Point offset = gui.getRecipePosition(recipeIndex);
        if (offset == null) return null;
        return new Point(mousepos.x - getGuiLeft(gui) - offset.x, mousepos.y - getGuiTop(gui) - offset.y);
    }

    public class CachedFuelStatsRecipe extends CachedRecipe {

        public final NuclearFuelType fuel;
        public final PositionedStack fuelStack;

        public CachedFuelStatsRecipe(NuclearFuelType fuel, List<ItemStack> items) {
            this.fuel = fuel;
            ItemStack fallback = (ItemList.RodThorium.hasBeenSet()) ? ItemList.RodThorium.get(1L)
                : new ItemStack(net.minecraft.init.Items.coal);
            List<ItemStack> displayItems = (items != null && !items.isEmpty()) ? items
                : Collections.singletonList(fallback);
            this.fuelStack = new PositionedStack(displayItems, 12, 10);
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

    public static ItemStack getControllerStack() {
        if (ModMetaTileEntities.reactor != null) {
            return ModMetaTileEntities.reactor.copy();
        }
        if (gregtech.api.GregTechAPI.METATILEENTITIES[ModMetaTileEntities.ID_NUCLEAR_REACTOR] != null) {
            return gregtech.api.GregTechAPI.METATILEENTITIES[ModMetaTileEntities.ID_NUCLEAR_REACTOR].getStackForm(1L);
        }
        return null;
    }

    @Override
    public String getHandlerId() {
        return OVERLAY_ID;
    }

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
        this.transferRects.add(new RecipeTransferRect(new Rectangle(11, 9, 18, 18), getOverlayIdentifier()));
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
        if (isController(result)) {
            for (NuclearFuelType fuel : NuclearFuelType.values()) {
                this.arecipes.add(new CachedFuelStatsRecipe(fuel, getFuelStacks(fuel)));
            }
            return;
        }
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
        if (isController(ingredient)) {
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

    private static boolean isController(ItemStack stack) {
        if (stack == null) return false;
        ItemStack ctrl = getControllerStack();
        return ctrl != null && ctrl.isItemEqual(stack);
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
        NEINeutronInteractionHandler.drawPanel(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);

        // Lab monitor dark background screen
        GuiDraw.drawRect(SCREEN_X, SCREEN_Y, SCREEN_W, SCREEN_H, 0xFF14171D);

        // Inner plot area backdrop
        GuiDraw.drawRect(PLOT_X, PLOT_Y, PLOT_W, PLOT_H, 0xFF1B1F27);

        // Inner plot border
        GuiDraw.drawRect(PLOT_X - 1, PLOT_Y - 1, PLOT_W + 2, 1, 0xFF2D333F);
        GuiDraw.drawRect(PLOT_X - 1, PLOT_Y + PLOT_H, PLOT_W + 2, 1, 0xFF2D333F);
        GuiDraw.drawRect(PLOT_X - 1, PLOT_Y, 1, PLOT_H, 0xFF2D333F);
        GuiDraw.drawRect(PLOT_X + PLOT_W, PLOT_Y, 1, PLOT_H, 0xFF2D333F);

        NuclearFuelType fuel = recipe.fuel;
        double maxTemp = Math.ceil((fuel.floorTemp + 200.0) / 500.0) * 500.0;

        // Horizontal grid lines (25%, 50%, 75%, 100%)
        for (int p = 25; p <= 100; p += 25) {
            int gy = PLOT_Y + PLOT_H - (int) Math.round((p / 100.0) * PLOT_H);
            GuiDraw.drawRect(PLOT_X, gy, PLOT_W, 1, 0x1FFFFFFF);
        }

        // Vertical grid lines (e.g. 500°C steps)
        double step = (maxTemp > 3000.0) ? 1000.0 : 500.0;
        for (double t = step; t < maxTemp; t += step) {
            int gx = PLOT_X + (int) Math.round((t / maxTemp) * PLOT_W);
            GuiDraw.drawRect(gx, PLOT_Y, 1, PLOT_H, 0x1FFFFFFF);
        }
    }

    @Override
    public void drawExtras(int recipeIndex) {
        if (recipeIndex < 0 || recipeIndex >= arecipes.size()) return;
        CachedRecipe cached = this.arecipes.get(recipeIndex);
        if (!(cached instanceof CachedFuelStatsRecipe recipe)) return;

        NuclearFuelType fuel = recipe.fuel;
        FuelDamageCurveSolver.FuelCurveStats stats = FuelDamageCurveSolver.solve(fuel, sAmbientTemp);

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        // Fuel display title (auto-scaled if longer than header width)
        String title = EnumChatFormatting.BOLD + fuel.displayName + " Fuel";
        int titleWidth = font.getStringWidth(title);
        float titleScale = (titleWidth > 115) ? (115.0f / titleWidth) : 1.0f;
        drawScaledString(title, 36, 10, titleScale, 0x111111);

        // Non-heading stats scaled at 0.75x to prevent right-edge clipping
        final float STATS_SCALE = 0.75f;

        // Line 1: Peak & Floor Reactivity Temperatures
        String l1 = String.format(
            Locale.US,
            "Peak: %s%.0f °C%s | Floor: %s%.0f °C",
            EnumChatFormatting.DARK_AQUA,
            fuel.peakReactivityTemp,
            EnumChatFormatting.DARK_GRAY,
            EnumChatFormatting.RED,
            fuel.floorTemp);
        drawScaledString(l1, 36, 22, STATS_SCALE, 0x333333);

        // Line 2: 10% Reactivity Point & Damage Ratio (calculated dynamically for sAmbientTemp)
        String l2 = String.format(
            Locale.US,
            "10%% Point: %s%.0f °C%s (Ratio: %s%.1f%%%s)",
            EnumChatFormatting.BLUE,
            fuel.temp10Percent,
            EnumChatFormatting.DARK_GRAY,
            EnumChatFormatting.DARK_GREEN,
            stats.damageRatioPercent,
            EnumChatFormatting.DARK_GRAY);
        drawScaledString(l2, 12, 33, STATS_SCALE, 0x333333);

        // Line 3: Fission Multiplier & Base Neutron Output
        com.gtnewhorizons.modularnuclear.common.config.data.PhysicsData physics = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .getPhysics();
        double effFiss = fuel.getEffectiveFissionMultiplier(physics);
        double effFastFiss = fuel.getEffectiveFastFissionMultiplier(physics);
        int effNeutrons = fuel.getEffectiveBaseNeutrons(physics);

        String l3 = String.format(
            Locale.US,
            "Th: %s%.2fx%s | Fast: %s%.2fx%s | %s%d n/t",
            EnumChatFormatting.DARK_PURPLE,
            effFiss,
            EnumChatFormatting.DARK_GRAY,
            EnumChatFormatting.BLUE,
            effFastFiss,
            EnumChatFormatting.DARK_GRAY,
            EnumChatFormatting.DARK_RED,
            effNeutrons);
        drawScaledString(l3, 12, 44, STATS_SCALE, 0x333333);

        // Line 4: Durability & Growth Parameter k (calculated dynamically for sAmbientTemp)
        String l4 = String.format(
            Locale.US,
            "Durability: %s%,d%s | k: %s%.6f",
            EnumChatFormatting.DARK_GREEN,
            fuel.defaultDurability,
            EnumChatFormatting.DARK_GRAY,
            EnumChatFormatting.BLACK,
            stats.k);
        drawScaledString(l4, 12, 55, STATS_SCALE, 0x333333);

        // --- DYNAMIC VECTOR PLOT RENDERING ---
        double maxTemp = Math.ceil((fuel.floorTemp + 200.0) / 500.0) * 500.0;
        double damageAtFloor = stats.calculateDamage(fuel.floorTemp);
        double maxDamage = Math.max(6.0, Math.ceil(damageAtFloor * 1.1));

        // Sub-pixel 0.5x labels for axes and legend
        GL11.glPushMatrix();
        GL11.glScalef(0.5f, 0.5f, 1.0f);

        // 1. Legend at top of plot screen
        GuiDraw.drawRect((SCREEN_X + 6) * 2, (SCREEN_Y + 4) * 2 + 3, 10, 2, 0xFF00D4FF);
        font.drawString("Reactivity", (SCREEN_X + 13) * 2, (SCREEN_Y + 3) * 2, 0xFF00D4FF);

        GuiDraw.drawRect((SCREEN_X + 78) * 2, (SCREEN_Y + 4) * 2 + 3, 10, 2, 0xFFFF5500);
        font.drawString("Durability loss / t", (SCREEN_X + 85) * 2, (SCREEN_Y + 3) * 2, 0xFFFF5500);

        // 2. Left Axis: Reactivity % labels
        for (int p = 0; p <= 100; p += 25) {
            int gy = PLOT_Y + PLOT_H - (int) Math.round((p / 100.0) * PLOT_H);
            String pStr = p + "%";
            int sw = font.getStringWidth(pStr);
            font.drawString(pStr, (PLOT_X - 2) * 2 - sw, (gy - 2) * 2, 0xFF38BDF8);
        }

        // 3. Right Axis: Damage rate labels
        for (int p = 0; p <= 100; p += 25) {
            int gy = PLOT_Y + PLOT_H - (int) Math.round((p / 100.0) * PLOT_H);
            double dVal = (p / 100.0) * maxDamage;
            String dStr = String.format(Locale.US, "%.1f", dVal);
            font.drawString(dStr, (PLOT_X + PLOT_W + 3) * 2, (gy - 2) * 2, 0xFFF59E0B);
        }

        // 4. Bottom Axis: Temperature labels
        double step = (maxTemp > 3000.0) ? 1000.0 : 500.0;
        font.drawString("0", (PLOT_X - 1) * 2, (PLOT_Y + PLOT_H + 3) * 2, 0xFF94A3B8);
        for (double t = step; t < maxTemp; t += step) {
            int gx = PLOT_X + (int) Math.round((t / maxTemp) * PLOT_W);
            String tStr = (t >= 1000.0 && t % 1000.0 == 0) ? String.format(Locale.US, "%.0fk", t / 1000.0)
                : String.valueOf((int) t);
            int sw = font.getStringWidth(tStr);
            font.drawString(tStr, gx * 2 - sw / 2, (PLOT_Y + PLOT_H + 3) * 2, 0xFF94A3B8);
        }
        font.drawString("°C", (PLOT_X + PLOT_W + 1) * 2, (PLOT_Y + PLOT_H + 3) * 2, 0xFF94A3B8);

        GL11.glPopMatrix();

        // 5. Draw Dynamic Anti-aliased Vector Curves
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(2.0f);

        // Reactivity curve (Cyan: #00D4FF)
        GL11.glColor4f(0.0f, 0.83f, 1.0f, 1.0f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i <= PLOT_W; i++) {
            double temp = (i / (double) PLOT_W) * maxTemp;
            double r = fuel.calculateReactivity(temp);
            float y = (float) (PLOT_Y + PLOT_H - Math.max(0.0, Math.min(1.0, r)) * PLOT_H);
            GL11.glVertex2f(PLOT_X + i, y);
        }
        GL11.glEnd();

        // Durability damage curve (Orange-Red: #FF5500)
        GL11.glColor4f(1.0f, 0.33f, 0.0f, 1.0f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i <= PLOT_W; i++) {
            double temp = (i / (double) PLOT_W) * maxTemp;
            double d = stats.calculateDamage(temp);
            float y = (float) (PLOT_Y + PLOT_H - Math.max(0.0, Math.min(1.0, d / maxDamage)) * PLOT_H);
            GL11.glVertex2f(PLOT_X + i, y);
        }
        GL11.glEnd();

        // 6. Interactive Crosshair & Guide Lines on Hover
        Point mouseInRecipe = null;
        if (Minecraft.getMinecraft().currentScreen instanceof GuiRecipe<?>guiRecipe) {
            mouseInRecipe = getMouseInRecipe(guiRecipe, recipeIndex);
        }

        if (mouseInRecipe != null && isMouseOverPlot(mouseInRecipe.x, mouseInRecipe.y)) {
            int mx = mouseInRecipe.x;
            double tempAtMouse = ((mx - PLOT_X) / (double) PLOT_W) * maxTemp;
            double rAtMouse = fuel.calculateReactivity(tempAtMouse);
            double dAtMouse = stats.calculateDamage(tempAtMouse);
            float yr = (float) (PLOT_Y + PLOT_H - Math.max(0.0, Math.min(1.0, rAtMouse)) * PLOT_H);
            float yd = (float) (PLOT_Y + PLOT_H - Math.max(0.0, Math.min(1.0, dAtMouse / maxDamage)) * PLOT_H);

            // Vertical cursor line
            GL11.glLineWidth(1.0f);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 0.6f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(mx, PLOT_Y);
            GL11.glVertex2f(mx, PLOT_Y + PLOT_H);
            GL11.glEnd();

            // Horizontal projection to left axis for Reactivity
            GL11.glColor4f(0.0f, 0.83f, 1.0f, 0.45f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(PLOT_X, yr);
            GL11.glVertex2f(mx, yr);
            GL11.glEnd();

            // Horizontal projection to right axis for Damage
            GL11.glColor4f(1.0f, 0.33f, 0.0f, 0.45f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(mx, yd);
            GL11.glVertex2f(PLOT_X + PLOT_W, yd);
            GL11.glEnd();

            // Draw snap indicator dots (diamonds)
            drawIndicatorDot(mx, yr, 0.0f, 0.83f, 1.0f);
            drawIndicatorDot(mx, yd, 1.0f, 0.33f, 0.0f);
        }

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.0f);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glPopMatrix();

        // 7. Interactive Tooltip rendering for Plot
        if (mouseInRecipe != null && isMouseOverPlot(mouseInRecipe.x, mouseInRecipe.y)
            && Minecraft.getMinecraft().currentScreen instanceof GuiRecipe<?>guiRecipe) {
            Point offset = guiRecipe.getRecipePosition(recipeIndex);
            if (offset != null) {
                int mx = mouseInRecipe.x;
                double tempAtMouse = ((mx - PLOT_X) / (double) PLOT_W) * maxTemp;
                double rAtMouse = fuel.calculateReactivity(tempAtMouse);
                double dAtMouse = stats.calculateDamage(tempAtMouse);

                List<String> tip = new ArrayList<>();
                tip.add(
                    EnumChatFormatting.WHITE + ""
                        + EnumChatFormatting.BOLD
                        + String.format(Locale.US, "%.0f °C", tempAtMouse));

                String peakNote = (Math.abs(tempAtMouse - fuel.peakReactivityTemp) <= (maxTemp / PLOT_W * 0.75))
                    ? EnumChatFormatting.GOLD + " (Peak)"
                    : "";
                tip.add(
                    EnumChatFormatting.AQUA + String.format(Locale.US, "Reactivity: %.1f %%", rAtMouse * 100.0)
                        + peakNote);

                String dmgNote = (tempAtMouse <= sAmbientTemp) ? EnumChatFormatting.GRAY + " (None)" : "";
                tip.add(
                    EnumChatFormatting.GOLD + String.format(Locale.US, "Durability damage: %.2f / t", dAtMouse)
                        + dmgNote);

                GL11.glPushMatrix();
                GL11.glTranslatef(-getGuiLeft(guiRecipe) - offset.x, -getGuiTop(guiRecipe) - offset.y, 400.0f);
                Point mousepos = GuiDraw.getMousePosition();
                GuiDraw.drawMultilineTip(mousepos.x, mousepos.y, tip);
                GL11.glPopMatrix();
            }
        }

        // 8. Interactive Ambient Temperature Control Bar
        // Bar container background & border
        GuiDraw.drawRect(BAR_X, BAR_Y, BAR_W, BAR_H, 0xFF14171D);
        GuiDraw.drawRect(BAR_X, BAR_Y, BAR_W, 1, 0xFF2D333F);
        GuiDraw.drawRect(BAR_X, BAR_Y + BAR_H - 1, BAR_W, 1, 0xFF2D333F);
        GuiDraw.drawRect(BAR_X, BAR_Y, 1, BAR_H, 0xFF2D333F);
        GuiDraw.drawRect(BAR_X + BAR_W - 1, BAR_Y, 1, BAR_H, 0xFF2D333F);

        // Label: "Ambient:"
        font.drawString("Ambient:", BAR_X + 4, BAR_Y + 3, 0xFF9CA3AF);

        // Input box
        boolean overInput = mouseInRecipe != null && isMouseOverInput(mouseInRecipe.x, mouseInRecipe.y);
        int inputBorderCol = sInputFocused ? 0xFF38BDF8 : (overInput ? 0xFF60A5FA : 0xFF374151);
        GuiDraw.drawRect(INPUT_X, INPUT_Y, INPUT_W, INPUT_H, 0xFF0D0F12);
        GuiDraw.drawRect(INPUT_X, INPUT_Y, INPUT_W, 1, inputBorderCol);
        GuiDraw.drawRect(INPUT_X, INPUT_Y + INPUT_H - 1, INPUT_W, 1, inputBorderCol);
        GuiDraw.drawRect(INPUT_X, INPUT_Y, 1, INPUT_H, inputBorderCol);
        GuiDraw.drawRect(INPUT_X + INPUT_W - 1, INPUT_Y, 1, INPUT_H, inputBorderCol);

        String dispText = sInputFocused ? sInputText
            : ((sAmbientTemp == (long) sAmbientTemp) ? String.valueOf((long) sAmbientTemp)
                : String.format(Locale.US, "%.1f", sAmbientTemp));
        if (sInputFocused && ((System.currentTimeMillis() / 500) % 2 == 0)) {
            dispText += "_";
        }
        int textCol = sInputFocused ? 0xFFFFFFFF : 0xFFFCD34D;
        int tw = font.getStringWidth(dispText);
        int tx = INPUT_X + Math.max(2, (INPUT_W - tw) / 2);
        font.drawString(dispText, tx, INPUT_Y + 2, textCol);

        // Unit "°C"
        font.drawString("°C", INPUT_X + INPUT_W + 3, BAR_Y + 3, 0xFF94A3B8);

        // Decrement button [-]
        boolean overMinus = mouseInRecipe != null && isMouseOverMinus(mouseInRecipe.x, mouseInRecipe.y);
        int btnMinusBg = overMinus ? 0xFF333B4A : 0xFF1F242D;
        GuiDraw.drawRect(BTN_MINUS_X, BTN_MINUS_Y, BTN_MINUS_W, BTN_MINUS_H, btnMinusBg);
        GuiDraw.drawRect(BTN_MINUS_X, BTN_MINUS_Y, BTN_MINUS_W, 1, 0xFF3E4856);
        GuiDraw.drawRect(BTN_MINUS_X, BTN_MINUS_Y + BTN_MINUS_H - 1, BTN_MINUS_W, 1, 0xFF3E4856);
        GuiDraw.drawRect(BTN_MINUS_X, BTN_MINUS_Y, 1, BTN_MINUS_H, 0xFF3E4856);
        GuiDraw.drawRect(BTN_MINUS_X + BTN_MINUS_W - 1, BTN_MINUS_Y, 1, BTN_MINUS_H, 0xFF3E4856);
        font.drawString("-", BTN_MINUS_X + 4, BTN_MINUS_Y + 2, overMinus ? 0xFF38BDF8 : 0xFFE5E7EB);

        // Increment button [+]
        boolean overPlus = mouseInRecipe != null && isMouseOverPlus(mouseInRecipe.x, mouseInRecipe.y);
        int btnPlusBg = overPlus ? 0xFF333B4A : 0xFF1F242D;
        GuiDraw.drawRect(BTN_PLUS_X, BTN_PLUS_Y, BTN_PLUS_W, BTN_PLUS_H, btnPlusBg);
        GuiDraw.drawRect(BTN_PLUS_X, BTN_PLUS_Y, BTN_PLUS_W, 1, 0xFF3E4856);
        GuiDraw.drawRect(BTN_PLUS_X, BTN_PLUS_Y + BTN_PLUS_H - 1, BTN_PLUS_W, 1, 0xFF3E4856);
        GuiDraw.drawRect(BTN_PLUS_X, BTN_PLUS_Y, 1, BTN_PLUS_H, 0xFF3E4856);
        GuiDraw.drawRect(BTN_PLUS_X + BTN_PLUS_W - 1, BTN_PLUS_Y, 1, BTN_PLUS_H, 0xFF3E4856);
        font.drawString("+", BTN_PLUS_X + 3, BTN_PLUS_Y + 2, overPlus ? 0xFF38BDF8 : 0xFFE5E7EB);

        // Reset button [24]
        boolean overReset = mouseInRecipe != null && isMouseOverReset(mouseInRecipe.x, mouseInRecipe.y);
        int btnResetBg = overReset ? 0xFF333B4A : 0xFF1F242D;
        GuiDraw.drawRect(BTN_RESET_X, BTN_RESET_Y, BTN_RESET_W, BTN_RESET_H, btnResetBg);
        GuiDraw.drawRect(BTN_RESET_X, BTN_RESET_Y, BTN_RESET_W, 1, 0xFF3E4856);
        GuiDraw.drawRect(BTN_RESET_X, BTN_RESET_Y + BTN_RESET_H - 1, BTN_RESET_W, 1, 0xFF3E4856);
        GuiDraw.drawRect(BTN_RESET_X, BTN_RESET_Y, 1, BTN_RESET_H, 0xFF3E4856);
        GuiDraw.drawRect(BTN_RESET_X + BTN_RESET_W - 1, BTN_RESET_Y, 1, BTN_RESET_H, 0xFF3E4856);
        font.drawString("24", BTN_RESET_X + 4, BTN_RESET_Y + 2, overReset ? 0xFFFCD34D : 0xFF38BDF8);

        // 9. Tooltip for Control Bar elements
        if (mouseInRecipe != null && Minecraft.getMinecraft().currentScreen instanceof GuiRecipe<?>guiRecipe) {
            Point offset = guiRecipe.getRecipePosition(recipeIndex);
            if (offset != null) {
                List<String> tip = null;
                if (isMouseOverInput(mouseInRecipe.x, mouseInRecipe.y)) {
                    tip = new ArrayList<>();
                    tip.add(
                        EnumChatFormatting.WHITE + "Ambient Temperature: "
                            + EnumChatFormatting.YELLOW
                            + String.format(Locale.US, "%.1f °C", sAmbientTemp));
                    tip.add(EnumChatFormatting.GRAY + "Click to edit directly");
                    tip.add(EnumChatFormatting.DARK_GRAY + "Scroll wheel: adjust ±1 °C (Shift: ±5 °C)");
                } else if (isMouseOverMinus(mouseInRecipe.x, mouseInRecipe.y)) {
                    tip = new ArrayList<>();
                    tip.add(
                        EnumChatFormatting.WHITE + "Decrease Ambient Temp ("
                            + EnumChatFormatting.RED
                            + "-1.0 °C"
                            + EnumChatFormatting.WHITE
                            + ")");
                    tip.add(EnumChatFormatting.GRAY + "Shift-click: -5.0 °C");
                } else if (isMouseOverPlus(mouseInRecipe.x, mouseInRecipe.y)) {
                    tip = new ArrayList<>();
                    tip.add(
                        EnumChatFormatting.WHITE + "Increase Ambient Temp ("
                            + EnumChatFormatting.GREEN
                            + "+1.0 °C"
                            + EnumChatFormatting.WHITE
                            + ")");
                    tip.add(EnumChatFormatting.GRAY + "Shift-click: +5.0 °C");
                } else if (isMouseOverReset(mouseInRecipe.x, mouseInRecipe.y)) {
                    tip = new ArrayList<>();
                    tip.add(EnumChatFormatting.AQUA + "Reset to Default (24.0 °C)");
                }

                if (tip != null) {
                    GL11.glPushMatrix();
                    GL11.glTranslatef(-getGuiLeft(guiRecipe) - offset.x, -getGuiTop(guiRecipe) - offset.y, 400.0f);
                    Point mousepos = GuiDraw.getMousePosition();
                    GuiDraw.drawMultilineTip(mousepos.x, mousepos.y, tip);
                    GL11.glPopMatrix();
                }
            }
        }
    }

    private static void drawScaledString(String text, float x, float y, float scale, int color) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0.0f);
        GL11.glScalef(scale, scale, 1.0f);
        font.drawString(text, 0, 0, color, false);
        GL11.glPopMatrix();
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void drawIndicatorDot(float x, float y, float r, float g, float b) {
        // Outer colored diamond (radius 2.5)
        GL11.glColor4f(r, g, b, 1.0f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x - 2.5f, y);
        GL11.glVertex2f(x, y + 2.5f);
        GL11.glVertex2f(x + 2.5f, y);
        GL11.glVertex2f(x, y - 2.5f);
        GL11.glEnd();

        // Inner white diamond core (radius 1.0)
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x - 1.0f, y);
        GL11.glVertex2f(x, y + 1.0f);
        GL11.glVertex2f(x + 1.0f, y);
        GL11.glVertex2f(x, y - 1.0f);
        GL11.glEnd();
    }

    @Override
    public List<String> handleTooltip(GuiRecipe<?> gui, List<String> currenttip, int recipeIndex) {
        currenttip = super.handleTooltip(gui, currenttip, recipeIndex);
        if (currenttip == null) currenttip = new ArrayList<>();
        if (recipeIndex < 0 || recipeIndex >= arecipes.size()) return currenttip;
        CachedRecipe cached = this.arecipes.get(recipeIndex);
        if (!(cached instanceof CachedFuelStatsRecipe recipe)) return currenttip;

        Point mouseInRecipe = getMouseInRecipe(gui, recipeIndex);
        if (mouseInRecipe != null) {
            NuclearFuelType fuel = recipe.fuel;
            FuelDamageCurveSolver.FuelCurveStats stats = FuelDamageCurveSolver.solve(fuel, sAmbientTemp);

            if (isMouseOverPlot(mouseInRecipe.x, mouseInRecipe.y)) {
                double maxTemp = Math.ceil((fuel.floorTemp + 200.0) / 500.0) * 500.0;
                double temp = ((mouseInRecipe.x - PLOT_X) / (double) PLOT_W) * maxTemp;
                double r = fuel.calculateReactivity(temp);
                double d = stats.calculateDamage(temp);

                currenttip.add(
                    EnumChatFormatting.WHITE + ""
                        + EnumChatFormatting.BOLD
                        + String.format(Locale.US, "%.0f °C", temp));

                String peakNote = (Math.abs(temp - fuel.peakReactivityTemp) <= (maxTemp / PLOT_W * 0.75))
                    ? EnumChatFormatting.GOLD + " (Peak)"
                    : "";
                currenttip.add(
                    EnumChatFormatting.AQUA + String.format(Locale.US, "Reactivity: %.1f %%", r * 100.0) + peakNote);

                String dmgNote = (temp <= sAmbientTemp) ? EnumChatFormatting.GRAY + " (None)" : "";
                currenttip.add(
                    EnumChatFormatting.GOLD + String.format(Locale.US, "Durability damage: %.2f / t", d) + dmgNote);
            } else if (isMouseOverInput(mouseInRecipe.x, mouseInRecipe.y)) {
                currenttip.add(
                    EnumChatFormatting.WHITE + "Ambient Temperature: "
                        + EnumChatFormatting.YELLOW
                        + String.format(Locale.US, "%.1f °C", sAmbientTemp));
                currenttip.add(EnumChatFormatting.GRAY + "Click to edit directly");
                currenttip.add(EnumChatFormatting.DARK_GRAY + "Scroll wheel: adjust ±1 °C (Shift: ±5 °C)");
            } else if (isMouseOverMinus(mouseInRecipe.x, mouseInRecipe.y)) {
                currenttip.add(
                    EnumChatFormatting.WHITE + "Decrease Ambient Temp ("
                        + EnumChatFormatting.RED
                        + "-1.0 °C"
                        + EnumChatFormatting.WHITE
                        + ")");
                currenttip.add(EnumChatFormatting.GRAY + "Shift-click: -5.0 °C");
            } else if (isMouseOverPlus(mouseInRecipe.x, mouseInRecipe.y)) {
                currenttip.add(
                    EnumChatFormatting.WHITE + "Increase Ambient Temp ("
                        + EnumChatFormatting.GREEN
                        + "+1.0 °C"
                        + EnumChatFormatting.WHITE
                        + ")");
                currenttip.add(EnumChatFormatting.GRAY + "Shift-click: +5.0 °C");
            } else if (isMouseOverReset(mouseInRecipe.x, mouseInRecipe.y)) {
                currenttip.add(EnumChatFormatting.AQUA + "Reset to Default (24.0 °C)");
            }
        }
        return currenttip;
    }

    @Override
    public boolean mouseClicked(GuiRecipe<?> gui, int button, int recipeIndex) {
        Point m = getMouseInRecipe(gui, recipeIndex);
        if (m != null) {
            if (isMouseOverInput(m.x, m.y)) {
                sInputFocused = true;
                sInputText = (sAmbientTemp == (long) sAmbientTemp) ? String.valueOf((long) sAmbientTemp)
                    : String.format(Locale.US, "%.1f", sAmbientTemp);
                return true;
            } else if (isMouseOverMinus(m.x, m.y)) {
                double delta = GuiScreen.isShiftKeyDown() ? -5.0 : -1.0;
                adjustAmbientTemp(delta);
                return true;
            } else if (isMouseOverPlus(m.x, m.y)) {
                double delta = GuiScreen.isShiftKeyDown() ? 5.0 : 1.0;
                adjustAmbientTemp(delta);
                return true;
            } else if (isMouseOverReset(m.x, m.y)) {
                resetAmbientTemp();
                return true;
            } else if (sInputFocused) {
                commitInput();
                sInputFocused = false;
            }
        }
        return super.mouseClicked(gui, button, recipeIndex);
    }

    @Override
    public boolean mouseScrolled(GuiRecipe<?> gui, int direction, int recipeIndex) {
        Point m = getMouseInRecipe(gui, recipeIndex);
        if (m != null && isMouseOverControlBar(m.x, m.y)) {
            double delta = (direction > 0 ? 1.0 : -1.0) * (GuiScreen.isShiftKeyDown() ? 5.0 : 1.0);
            adjustAmbientTemp(delta);
            return true;
        }
        return super.mouseScrolled(gui, direction, recipeIndex);
    }

    public static class FuelStatsInputHandler implements IContainerInputHandler {

        @Override
        public boolean keyTyped(GuiContainer gui, char keyChar, int keyCode) {
            if (sInputFocused && gui instanceof GuiRecipe) {
                if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN
                    || keyCode == Keyboard.KEY_NUMPADENTER) {
                    commitInput();
                    sInputFocused = false;
                    return true;
                }
                if (keyCode == Keyboard.KEY_BACK) {
                    if (sInputText != null && !sInputText.isEmpty()) {
                        sInputText = sInputText.substring(0, sInputText.length() - 1);
                        updateAmbientFromInput();
                    }
                    return true;
                }
                if ((keyChar >= '0' && keyChar <= '9') && sInputText != null && sInputText.length() < 7) {
                    sInputText += keyChar;
                    updateAmbientFromInput();
                    return true;
                }
                if (keyChar == '.' && sInputText != null && !sInputText.contains(".") && sInputText.length() < 7) {
                    sInputText += ".";
                    updateAmbientFromInput();
                    return true;
                }
                if (keyChar == '-') {
                    if (sInputText == null || sInputText.isEmpty()) {
                        sInputText = "-";
                    } else if (sInputText.startsWith("-")) {
                        sInputText = sInputText.substring(1);
                        updateAmbientFromInput();
                    } else {
                        sInputText = "-" + sInputText;
                        updateAmbientFromInput();
                    }
                    return true;
                }
                // Suppress any other keys while input is focused
                return true;
            }
            return false;
        }

        @Override
        public void onKeyTyped(GuiContainer gui, char keyChar, int keyCode) {}

        @Override
        public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyCode) {
            return false;
        }

        @Override
        public boolean mouseClicked(GuiContainer gui, int mousex, int mousey, int button) {
            if (sInputFocused) {
                if (!(gui instanceof GuiRecipe<?>guiRecipe)) {
                    commitInput();
                    sInputFocused = false;
                } else {
                    Point m = getMouseInRecipe(guiRecipe, 0);
                    if (m == null || !isMouseOverInput(m.x, m.y)) {
                        commitInput();
                        sInputFocused = false;
                    }
                }
            }
            return false;
        }

        @Override
        public void onMouseClicked(GuiContainer gui, int mousex, int mousey, int button) {}

        @Override
        public void onMouseUp(GuiContainer gui, int mousex, int mousey, int button) {}

        @Override
        public boolean mouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {
            return false;
        }

        @Override
        public void onMouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {}

        @Override
        public void onMouseDragged(GuiContainer gui, int mousex, int mousey, int button, long heldTime) {}
    }
}
