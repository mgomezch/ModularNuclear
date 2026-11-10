package com.gtnewhorizons.modularnuclear.common.gui;

import com.cleanroommc.modularui.api.IThemeApi;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.SlotTheme;
import com.cleanroommc.modularui.theme.WidgetTheme;

import gregtech.api.modularui2.GTWidgetThemes;

/**
 * Backgrounds our MUI2 widgets draw from the GUI's theme, as GregTech's own widgets do, so that GT's themes and
 * resource packs restyle them with the rest of the GUI.
 */
// See Note [Resource packs restyle our GUIs]
public final class ThemedDraw {

    /** An 18x18 slot's size. */
    public static final int SLOT = 18;

    private ThemedDraw() {}

    /** GregTech's terminal screen, as multiblock controllers show their status on; its text colour. */
    public static int terminal(ModularGuiContext context, int x, int y, int w, int h) {
        WidgetTheme theme = context.getTheme()
            .getWidgetTheme(GTWidgetThemes.BACKGROUND_TERMINAL)
            .getTheme();
        draw(context, theme.getBackground(), x, y, w, h, theme);
        return theme.getTextColor();
    }

    /** An empty item slot at (x, y). */
    public static void slot(ModularGuiContext context, int x, int y) {
        SlotTheme theme = slotTheme(context);
        draw(context, theme.getBackground(), x, y, SLOT, SLOT, theme);
    }

    /** The tint an item slot takes under the mouse, over its 16x16 inside. */
    public static int slotHoverColor(ModularGuiContext context) {
        return slotTheme(context).getSlotHoverColor();
    }

    private static SlotTheme slotTheme(ModularGuiContext context) {
        return context.getTheme()
            .getWidgetTheme(IThemeApi.ITEM_SLOT)
            .getTheme();
    }

    private static void draw(ModularGuiContext context, IDrawable drawable, int x, int y, int w, int h,
        WidgetTheme theme) {
        if (drawable != null) {
            drawable.draw(context, x, y, w, h, theme);
        }
    }
}
