package com.gtnewhorizons.modularnuclear.common.nei;

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

import com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.util.GTUtility;

public class NEIModeratorStatsHandler extends TemplateRecipeHandler {

    public static final String OVERLAY_ID = "modularnuclear.moderator_stats";

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

    public class CachedModeratorStatsRecipe extends CachedRecipe {

        public final ModeratorData moderator;
        public final PositionedStack modStack;

        public CachedModeratorStatsRecipe(ModeratorData moderator, List<ItemStack> items) {
            this.moderator = moderator;
            ItemStack fallback = new ItemStack(net.minecraft.init.Items.coal);
            List<ItemStack> displayItems = (items != null && !items.isEmpty()) ? items
                : Collections.singletonList(fallback);
            this.modStack = new PositionedStack(displayItems, 12, 10);
        }

        @Override
        public PositionedStack getIngredient() {
            return modStack;
        }

        @Override
        public PositionedStack getResult() {
            return modStack;
        }
    }

    public static List<ItemStack> getModeratorStacks(ModeratorData mod) {
        List<ItemStack> list = new ArrayList<>();
        if (mod == null || mod.matching == null) return list;
        for (String pattern : mod.matching) {
            ItemStack stack = ReactorDataRegistry.resolveItemStack(pattern);
            if (stack != null) {
                boolean exists = false;
                for (ItemStack existing : list) {
                    if (GTUtility.areStacksEqual(existing, stack, true)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    list.add(stack);
                }
            }
        }
        return list;
    }

    public NEIModeratorStatsHandler() {}

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
        return StatCollector.canTranslate("modularnuclear.nei.moderator_stats.name")
            ? StatCollector.translateToLocal("modularnuclear.nei.moderator_stats.name")
            : "MPTR Moderators";
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
            for (ModeratorData mod : ReactorDataRegistry.getComponentData().moderators) {
                this.arecipes.add(new CachedModeratorStatsRecipe(mod, getModeratorStacks(mod)));
            }
        } else {
            super.loadCraftingRecipes(outputId, results);
        }
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        if (result == null) return;
        if (isController(result)) {
            for (ModeratorData mod : ReactorDataRegistry.getComponentData().moderators) {
                this.arecipes.add(new CachedModeratorStatsRecipe(mod, getModeratorStacks(mod)));
            }
            return;
        }
        ModeratorData matched = ReactorDataRegistry.findModerator(result);
        if (matched != null) {
            this.arecipes.add(new CachedModeratorStatsRecipe(matched, getModeratorStacks(matched)));
            return;
        }
        for (ModeratorData mod : ReactorDataRegistry.getComponentData().moderators) {
            if (matches(result, mod)) {
                this.arecipes.add(new CachedModeratorStatsRecipe(mod, getModeratorStacks(mod)));
            }
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        if (ingredient == null) return;
        if (isController(ingredient)) {
            for (ModeratorData mod : ReactorDataRegistry.getComponentData().moderators) {
                this.arecipes.add(new CachedModeratorStatsRecipe(mod, getModeratorStacks(mod)));
            }
            return;
        }
        ModeratorData matched = ReactorDataRegistry.findModerator(ingredient);
        if (matched != null) {
            this.arecipes.add(new CachedModeratorStatsRecipe(matched, getModeratorStacks(matched)));
            return;
        }
        for (ModeratorData mod : ReactorDataRegistry.getComponentData().moderators) {
            if (matches(ingredient, mod)) {
                this.arecipes.add(new CachedModeratorStatsRecipe(mod, getModeratorStacks(mod)));
            }
        }
    }

    private static boolean isController(ItemStack stack) {
        if (stack == null) return false;
        ItemStack ctrl = getControllerStack();
        return ctrl != null && ctrl.isItemEqual(stack);
    }

    private boolean matches(ItemStack target, ModeratorData mod) {
        if (target == null || mod == null) return false;
        if (ReactorDataRegistry.matchesItem(target, mod.matching)) return true;
        List<ItemStack> items = getModeratorStacks(mod);
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
        if (!(cached instanceof CachedModeratorStatsRecipe recipe)) return;

        // Slot box for moderator icon
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

        ModeratorData mod = recipe.moderator;
        boolean isWigner = mod.wignerEnergyPerFast > 0.0;
        double maxTemp = isWigner ? 3500.0 : mod.maxTemperature;
        if (maxTemp <= 0.0) maxTemp = 2500.0;

        // Horizontal grid lines (25%, 50%, 75%, 100%)
        for (int p = 25; p <= 100; p += 25) {
            int gy = PLOT_Y + PLOT_H - (int) Math.round((p / 100.0) * PLOT_H);
            GuiDraw.drawRect(PLOT_X, gy, PLOT_W, 1, 0x1FFFFFFF);
        }

        // Vertical grid lines (e.g. 500°C or 1000°C steps)
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
        if (!(cached instanceof CachedModeratorStatsRecipe recipe)) return;

        ModeratorData mod = recipe.moderator;
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        // Title
        String title = EnumChatFormatting.BOLD + mod.name;
        int titleWidth = font.getStringWidth(title);
        float titleScale = (titleWidth > 115) ? (115.0f / titleWidth) : 1.0f;
        drawScaledString(title, 36, 10, titleScale, 0x111111);

        final float STATS_SCALE = 0.75f;

        // Line 1: Moderation Efficiency & Heat Transfer
        String l1 = String.format(
            Locale.US,
            "Moderation: %s%.0f%%%s | Heat Transfer: %s%.2f",
            EnumChatFormatting.DARK_GREEN,
            mod.moderation * 100.0,
            EnumChatFormatting.DARK_GRAY,
            EnumChatFormatting.AQUA,
            mod.heatTransferCoeff);
        drawScaledString(l1, 36, 22, STATS_SCALE, 0x333333);

        // Line 2: Scattering Probabilities
        String l2 = String.format(
            Locale.US,
            "Scattering: Fast %s%.1f%%%s | Thermal %s%.1f%%",
            EnumChatFormatting.BLUE,
            mod.scatteringFast * 100.0,
            EnumChatFormatting.DARK_GRAY,
            EnumChatFormatting.DARK_AQUA,
            mod.scatteringThermal * 100.0);
        drawScaledString(l2, 12, 33, STATS_SCALE, 0x333333);

        // Line 3: Absorption Probabilities
        String l3 = String.format(
            Locale.US,
            "Absorption: Fast %s%.2f%%%s | Thermal %s%.2f%%",
            EnumChatFormatting.RED,
            mod.absorptionFast * 100.0,
            EnumChatFormatting.DARK_GRAY,
            EnumChatFormatting.DARK_RED,
            mod.absorptionThermal * 100.0);
        drawScaledString(l3, 12, 44, STATS_SCALE, 0x333333);

        // Line 4: Durability & Spent Product
        String spentDisplay = (mod.spentProduct != null && !mod.spentProduct.isEmpty()) ? mod.spentProduct : "None";
        if (spentDisplay.startsWith("ore:")) spentDisplay = spentDisplay.substring(4);
        String l4 = String.format(
            Locale.US,
            "Durability: %s%,d%s | Spent: %s%s",
            EnumChatFormatting.GOLD,
            mod.durability,
            EnumChatFormatting.DARK_GRAY,
            EnumChatFormatting.WHITE,
            spentDisplay);
        drawScaledString(l4, 12, 55, STATS_SCALE, 0x333333);

        // Plot Axis Labels & Curves
        boolean isWigner = mod.wignerEnergyPerFast > 0.0;
        double maxTemp = isWigner ? 3500.0 : mod.maxTemperature;
        if (maxTemp <= 0.0) maxTemp = 2500.0;

        GL11.glPushMatrix();
        GL11.glScalef(0.5f, 0.5f, 0.5f);

        // Graph Banner Title inside panel
        String chartTitle = isWigner ? "Wigner Heat Release Rate (HU/t max)" : "Thermal Durability Wear Rate (/tick)";
        int chartTitleCol = isWigner ? 0xFF38BDF8 : 0xFFF59E0B;
        font.drawString(chartTitle, (PLOT_X + 2) * 2, (PLOT_Y - 9) * 2, chartTitleCol);

        // Left Axis: Values
        double maxAxis = isWigner ? mod.wignerMaxReleaseRate : 10.0;
        for (int p = 0; p <= 100; p += 50) {
            int gy = PLOT_Y + PLOT_H - (int) Math.round((p / 100.0) * PLOT_H);
            double val = (p / 100.0) * maxAxis;
            String valStr = isWigner ? String.format(Locale.US, "%.0f", val) : String.format(Locale.US, "%.0f", val);
            font.drawString(valStr, (PLOT_X - 14) * 2, (gy - 2) * 2, chartTitleCol);
        }

        // Bottom Axis: Temperature labels
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

        // Draw Dynamic Vector Curve
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(2.0f);

        if (isWigner) {
            // Wigner heat release rate curve (Cyan / Blue: #00D4FF)
            GL11.glColor4f(0.0f, 0.83f, 1.0f, 1.0f);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i <= PLOT_W; i++) {
                double temp = (i / (double) PLOT_W) * maxTemp;
                double rate = mod.calculateWignerHeatRelease(temp, mod.wignerMaxReleaseRate);
                float y = (float) (PLOT_Y + PLOT_H - Math.max(0.0, Math.min(1.0, rate / maxAxis)) * PLOT_H);
                GL11.glVertex2f(PLOT_X + i, y);
            }
            GL11.glEnd();
        } else {
            // Temperature wear curve (Orange: #FF5500)
            GL11.glColor4f(1.0f, 0.33f, 0.0f, 1.0f);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i <= PLOT_W; i++) {
                double temp = (i / (double) PLOT_W) * maxTemp;
                double wear = mod.calculateTemperatureDamage(temp, 20.0);
                float y = (float) (PLOT_Y + PLOT_H - Math.max(0.0, Math.min(1.0, wear / maxAxis)) * PLOT_H);
                GL11.glVertex2f(PLOT_X + i, y);
            }
            GL11.glEnd();
        }

        // Interactive Crosshair & Cursor Line
        Point mouseInRecipe = null;
        if (Minecraft.getMinecraft().currentScreen instanceof GuiRecipe<?>guiRecipe) {
            mouseInRecipe = getMouseInRecipe(guiRecipe, recipeIndex);
        }

        if (mouseInRecipe != null && isMouseOverPlot(mouseInRecipe.x, mouseInRecipe.y)) {
            int mx = mouseInRecipe.x;
            double tempAtMouse = ((mx - PLOT_X) / (double) PLOT_W) * maxTemp;
            double valAtMouse = isWigner ? mod.calculateWignerHeatRelease(tempAtMouse, mod.wignerMaxReleaseRate)
                : mod.calculateTemperatureDamage(tempAtMouse, 20.0);
            float yVal = (float) (PLOT_Y + PLOT_H - Math.max(0.0, Math.min(1.0, valAtMouse / maxAxis)) * PLOT_H);

            // Vertical cursor line
            GL11.glLineWidth(1.0f);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 0.6f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(mx, PLOT_Y);
            GL11.glVertex2f(mx, PLOT_Y + PLOT_H);
            GL11.glEnd();

            // Horizontal projection
            if (isWigner) {
                GL11.glColor4f(0.0f, 0.83f, 1.0f, 0.5f);
            } else {
                GL11.glColor4f(1.0f, 0.33f, 0.0f, 0.5f);
            }
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(PLOT_X, yVal);
            GL11.glVertex2f(mx, yVal);
            GL11.glEnd();

            drawIndicatorDot(mx, yVal, isWigner ? 0.0f : 1.0f, isWigner ? 0.83f : 0.33f, isWigner ? 1.0f : 0.0f);
        }

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.0f);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glPopMatrix();

        // Interactive Tooltip rendering
        if (mouseInRecipe != null && isMouseOverPlot(mouseInRecipe.x, mouseInRecipe.y)
            && Minecraft.getMinecraft().currentScreen instanceof GuiRecipe<?>guiRecipe) {
            Point offset = guiRecipe.getRecipePosition(recipeIndex);
            if (offset != null) {
                int mx = mouseInRecipe.x;
                double tempAtMouse = ((mx - PLOT_X) / (double) PLOT_W) * maxTemp;
                List<String> tip = new ArrayList<>();
                tip.add(
                    EnumChatFormatting.WHITE + ""
                        + EnumChatFormatting.BOLD
                        + String.format(Locale.US, "%.0f °C", tempAtMouse));

                if (isWigner) {
                    double releaseRate = mod.calculateWignerHeatRelease(tempAtMouse, mod.wignerMaxReleaseRate);
                    String statusNote = "";
                    if (tempAtMouse < mod.wignerReleaseOnsetTemp) {
                        statusNote = EnumChatFormatting.GRAY + " (Sub-threshold)";
                    } else if (tempAtMouse >= mod.wignerReleaseHalfTemp) {
                        statusNote = EnumChatFormatting.RED + " (Saturated > 50%)";
                    } else {
                        statusNote = EnumChatFormatting.GOLD + " (Active Release)";
                    }
                    tip.add(
                        EnumChatFormatting.AQUA + String.format(Locale.US, "Release Rate: %.0f HU/t", releaseRate)
                            + statusNote);
                    tip.add(
                        EnumChatFormatting.GRAY + String.format(
                            Locale.US,
                            "Storage: %.0f HU / fast (Cap: %,.0f HU)",
                            mod.wignerEnergyPerFast,
                            mod.wignerMaxEnergy));
                } else {
                    double wear = mod.calculateTemperatureDamage(tempAtMouse, 20.0);
                    String tempNote = (tempAtMouse > mod.maxTemperature) ? EnumChatFormatting.RED + " (OVERHEATING)"
                        : (wear <= 0.001) ? EnumChatFormatting.GRAY + " (Nominal)" : "";
                    tip.add(
                        EnumChatFormatting.GOLD + String.format(Locale.US, "Temp wear: %.2f / tick", wear) + tempNote);
                }

                GL11.glPushMatrix();
                GL11.glTranslatef(-getGuiLeft(guiRecipe) - offset.x, -getGuiTop(guiRecipe) - offset.y, 400.0f);
                Point mousepos = GuiDraw.getMousePosition();
                GuiDraw.drawMultilineTip(mousepos.x, mousepos.y, tip);
                GL11.glPopMatrix();
            }
        }
    }

    private static void drawIndicatorDot(float x, float y, float r, float g, float b) {
        GL11.glColor4f(r, g, b, 1.0f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x - 2.5f, y);
        GL11.glVertex2f(x, y - 2.5f);
        GL11.glVertex2f(x + 2.5f, y);
        GL11.glVertex2f(x, y + 2.5f);
        GL11.glEnd();

        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x - 1.0f, y);
        GL11.glVertex2f(x, y - 1.0f);
        GL11.glVertex2f(x + 1.0f, y);
        GL11.glVertex2f(x, y + 1.0f);
        GL11.glEnd();
    }

    private static void drawScaledString(String text, int x, int y, float scale, int color) {
        if (scale == 1.0f) {
            Minecraft.getMinecraft().fontRenderer.drawString(text, x, y, color);
            return;
        }
        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, 1.0f);
        Minecraft.getMinecraft().fontRenderer.drawString(text, (int) (x / scale), (int) (y / scale), color);
        GL11.glPopMatrix();
    }
}
