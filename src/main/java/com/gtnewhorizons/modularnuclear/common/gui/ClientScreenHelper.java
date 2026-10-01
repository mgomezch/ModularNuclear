package com.gtnewhorizons.modularnuclear.common.gui;

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

    @SideOnly(Side.CLIENT)
    private static int getClientScaledWidth() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return 800;
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        return sr.getScaledWidth();
    }

    @SideOnly(Side.CLIENT)
    private static int getClientScaledHeight() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return 600;
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        return sr.getScaledHeight();
    }
}
