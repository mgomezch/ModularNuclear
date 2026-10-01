package com.gtnewhorizons.modularnuclear.common.gui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ClientScreenHelper {

    public static int getScaledScreenWidth() {
        if (!FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            return 800;
        }
        return getClientScaledWidth();
    }

    public static int getScaledScreenHeight() {
        if (!FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            return 600;
        }
        return getClientScaledHeight();
    }

    public static int getTopReservedHeight() {
        if (!FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            return 22;
        }
        return getClientTopReservedHeight();
    }

    @SideOnly(Side.CLIENT)
    private static int getClientScaledWidth() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return 800;
        if (mc.currentScreen != null && mc.currentScreen.width > 0) {
            return mc.currentScreen.width;
        }
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        return sr.getScaledWidth();
    }

    @SideOnly(Side.CLIENT)
    private static int getClientScaledHeight() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return 600;
        if (mc.currentScreen != null && mc.currentScreen.height > 0) {
            return mc.currentScreen.height;
        }
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        return sr.getScaledHeight();
    }

    @SideOnly(Side.CLIENT)
    private static int getClientTopReservedHeight() {
        int reserved = 0;
        try {
            Class<?> cfgClass = Class.forName("codechicken.nei.NEIClientConfig");
            Method isHiddenMethod = cfgClass.getMethod("isHidden");
            boolean hidden = (Boolean) isHiddenMethod.invoke(null);
            if (hidden) {
                return 4;
            }

            Class<?> lmClass = Class.forName("codechicken.nei.LayoutManager");
            // Check dropDown ("Item Subsets")
            try {
                Field ddField = lmClass.getField("dropDown");
                Object dropDown = ddField.get(null);
                if (dropDown != null) {
                    Field yField = dropDown.getClass()
                        .getField("y");
                    Field hField = dropDown.getClass()
                        .getField("h");
                    int y = yField.getInt(dropDown);
                    int h = hField.getInt(dropDown);
                    if (y >= 0 && h > 0) {
                        reserved = Math.max(reserved, y + h);
                    }
                }
            } catch (Throwable ignored) {}

            // Check drawWidgets set in LayoutManager
            try {
                Field dwField = lmClass.getDeclaredField("drawWidgets");
                dwField.setAccessible(true);
                Set<?> widgets = (Set<?>) dwField.get(null);
                if (widgets != null) {
                    for (Object w : widgets) {
                        if (w == null) continue;
                        Field yField = w.getClass()
                            .getField("y");
                        Field hField = w.getClass()
                            .getField("h");
                        int y = yField.getInt(w);
                        int h = hField.getInt(w);
                        // Only consider top-bar buttons (y < 24 and h <= 24)
                        if (y >= 0 && y < 24 && h > 0 && h <= 24) {
                            reserved = Math.max(reserved, y + h);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}

        if (reserved > 0) {
            return Math.min(reserved + 3, 26); // at most 26px below top bar
        }
        return 4;
    }
}
