package com.gtnewhorizons.modularnuclear.common.nei;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.oredict.OreDictionary;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.PositionedStack;
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

    public static boolean isMouseOverPlot(int x, int y) {
        return x >= PLOT_X && x <= PLOT_X + PLOT_W && y >= PLOT_Y && y <= PLOT_Y + PLOT_H;
    }

    public static Point getMouseInRecipe(GuiRecipe<?> gui, int recipeIndex) {
        if (gui == null) return null;
        Point mousepos = GuiDraw.getMousePosition();
        Dimension displaySize = GuiDraw.displaySize();
        int ySize = Math.min(Math.max(displaySize.height - 68, 166), 370);
        int guiLeft = (displaySize.width - 176) / 2;
        int guiTop = (displaySize.height - ySize) / 2 + 10;
        Point offset = gui.getRecipePosition(recipeIndex);
        if (offset == null) return null;
        return new Point(mousepos.x - guiLeft - offset.x, mousepos.y - guiTop - offset.y);
    }

    public class CachedFuelStatsRecipe extends CachedRecipe {

        public final NuclearFuelType fuel;
        public final PositionedStack fuelStack;

        public CachedFuelStatsRecipe(NuclearFuelType fuel, List<ItemStack> items) {
            this.fuel = fuel;
            List<ItemStack> displayItems = (items != null && !items.isEmpty()) ? items
                : Collections.singletonList(ItemList.RodThorium.get(1L));
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

        // --- DYNAMIC VECTOR PLOT RENDERING ---
        double maxTemp = Math.ceil((fuel.floorTemp + 200.0) / 500.0) * 500.0;
        double damageAtFloor = fuel.calculateTemperatureDamage(fuel.floorTemp, 20.0);
        double maxDamage = Math.max(6.0, Math.ceil(damageAtFloor * 1.1));

        // Sub-pixel 0.5x labels for axes and legend
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
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
            double d = fuel.calculateTemperatureDamage(temp);
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
            double dAtMouse = fuel.calculateTemperatureDamage(tempAtMouse);
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
        if (recipeIndex < 0 || recipeIndex >= arecipes.size()) return currenttip;
        CachedRecipe cached = this.arecipes.get(recipeIndex);
        if (!(cached instanceof CachedFuelStatsRecipe recipe)) return currenttip;

        Point mouseInRecipe = getMouseInRecipe(gui, recipeIndex);
        if (mouseInRecipe != null && isMouseOverPlot(mouseInRecipe.x, mouseInRecipe.y)) {
            NuclearFuelType fuel = recipe.fuel;
            double maxTemp = Math.ceil((fuel.floorTemp + 200.0) / 500.0) * 500.0;
            double temp = ((mouseInRecipe.x - PLOT_X) / (double) PLOT_W) * maxTemp;
            double r = fuel.calculateReactivity(temp);
            double d = fuel.calculateTemperatureDamage(temp);

            currenttip.add(
                EnumChatFormatting.WHITE + "" + EnumChatFormatting.BOLD + String.format(Locale.US, "%.0f °C", temp));

            String peakNote = (Math.abs(temp - fuel.peakReactivityTemp) <= (maxTemp / PLOT_W * 0.75))
                ? EnumChatFormatting.GOLD + " (Peak)"
                : "";
            currenttip
                .add(EnumChatFormatting.AQUA + String.format(Locale.US, "Reactivity: %.1f %%", r * 100.0) + peakNote);

            String dmgNote = (temp <= 20.0) ? EnumChatFormatting.GRAY + " (None)" : "";
            currenttip
                .add(EnumChatFormatting.GOLD + String.format(Locale.US, "Durability damage: %.2f / t", d) + dmgNote);
        }
        return currenttip;
    }
}
