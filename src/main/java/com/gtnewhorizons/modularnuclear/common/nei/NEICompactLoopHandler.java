package com.gtnewhorizons.modularnuclear.common.nei;

import java.awt.Rectangle;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.CompactLoopMachines.Kind;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.CompactStats;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.CompactTier;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.util.GTUtility;

/**
 * NEI's page for the compact loop machines: one for each machine, with what it can do at its tier (CompactStats).
 * Looking up a compact machine's recipe or uses shows its page; the tab alone shows all of them, kind by kind. See
 * Note [Compact loop machines]
 */
public class NEICompactLoopHandler extends TemplateRecipeHandler {

    public static final String OVERLAY_ID = "modularnuclear.compact_loop";
    static final int HEIGHT = 132;

    public class CachedCompactMachine extends CachedRecipe {

        final Kind kind;
        final int tier;
        final PositionedStack stack;
        final List<String[]> rows;

        CachedCompactMachine(Kind kind, int tier) {
            this.kind = kind;
            this.tier = tier;
            this.stack = new PositionedStack(kind.item(tier), 12, 10);
            this.rows = CompactStats.rows(kind, tier);
        }

        @Override
        public PositionedStack getResult() {
            return stack;
        }
    }

    public static ItemStack getDisplayStack() {
        return Kind.PUMP.item(CompactTier.FIRST);
    }

    /** The kind and tier of a compact machine's item, as { kind ordinal, tier }, or null for any other item. */
    static int[] machineOf(ItemStack stack) {
        if (stack == null) return null;
        for (Kind kind : Kind.values()) {
            for (int tier = CompactTier.FIRST; tier <= CompactTier.LAST; tier++) {
                if (GTUtility.areStacksEqual(stack, kind.item(tier), true)) return new int[] { kind.ordinal(), tier };
            }
        }
        return null;
    }

    private void addPage(ItemStack stack) {
        int[] m = machineOf(stack);
        if (m != null) arecipes.add(new CachedCompactMachine(Kind.values()[m[0]], m[1]));
    }

    @Override
    public String getHandlerId() {
        return OVERLAY_ID;
    }

    @Override
    public String getOverlayIdentifier() {
        return OVERLAY_ID;
    }

    @Override
    public String getRecipeName() {
        return StatCollector.translateToLocal("modularnuclear.nei.compact.name");
    }

    @Override
    public String getGuiTexture() {
        return "gregtech:textures/gui/nei/neutron_interaction_atlas.png";
    }

    @Override
    public int recipiesPerPage() {
        return 1;
    }

    @Override
    public int getRecipeHeight(int recipe) {
        return HEIGHT;
    }

    @Override
    public void loadTransferRects() {
        transferRects.add(new RecipeTransferRect(new Rectangle(11, 9, 18, 18), getOverlayIdentifier()));
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        if (OVERLAY_ID.equals(outputId)) {
            for (Kind kind : Kind.values()) {
                for (int tier = CompactTier.FIRST; tier <= CompactTier.LAST; tier++) {
                    arecipes.add(new CachedCompactMachine(kind, tier));
                }
            }
        } else {
            super.loadCraftingRecipes(outputId, results);
        }
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        addPage(result);
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        addPage(ingredient);
    }

    @Override
    public void drawBackground(int recipeIndex) {
        // See Note [Resource packs restyle our GUIs]
        NEIPalette palette = NEIPalette.current();
        palette.drawSlot(11, 9);
        palette.drawPanel(4, 32, 158, HEIGHT - 36);
    }

    @Override
    public void drawExtras(int recipeIndex) {
        if (recipeIndex < 0 || recipeIndex >= arecipes.size()) return;
        if (!(arecipes.get(recipeIndex) instanceof CachedCompactMachine page)) return;
        NEIPalette palette = NEIPalette.current();
        GuiDraw.drawString(page.stack.item.getDisplayName(), 34, 10, palette.ink, false);
        GuiDraw.drawString(
            StatCollector.translateToLocal("modularnuclear.nei.compact.kind." + page.kind.name),
            34,
            20,
            palette.label,
            false);
        int y = 38;
        for (String[] row : page.rows) {
            GuiDraw.drawString(StatCollector.translateToLocal(row[0]), 9, y, palette.label, false);
            int w = GuiDraw.getStringWidth(row[1]);
            GuiDraw.drawString(row[1], 157 - w, y, palette.ink, false);
            y += 11;
        }
    }
}
