package com.gtnewhorizons.modularnuclear;

public class TestEnvironmentHelper {

    private static boolean initialized = false;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        Thread.currentThread()
            .setName("Server thread");
        System.setProperty("gt.recipebuilder.debug", "false");

        try {
            if (net.minecraft.launchwrapper.Launch.blackboard == null) {
                net.minecraft.launchwrapper.Launch.blackboard = new java.util.HashMap<>();
            }
            net.minecraft.launchwrapper.Launch.blackboard.put("fml.deobfuscatedEnvironment", true);
        } catch (Throwable ignored) {}

        try {
            java.lang.reflect.Field f = cpw.mods.fml.common.Loader.class.getDeclaredField("instance");
            f.setAccessible(true);
            cpw.mods.fml.common.Loader ldr = (cpw.mods.fml.common.Loader) f.get(null);
            if (ldr == null) {
                ldr = org.mockito.Mockito.mock(cpw.mods.fml.common.Loader.class);
                org.mockito.Mockito.when(ldr.getCallableCrashInformation())
                    .thenReturn(org.mockito.Mockito.mock(cpw.mods.fml.common.ICrashCallable.class));
                f.set(null, ldr);
            }
            try {
                java.lang.reflect.Field namedModsField = cpw.mods.fml.common.Loader.class.getDeclaredField("namedMods");
                namedModsField.setAccessible(true);
                if (namedModsField.get(ldr) == null) {
                    namedModsField.set(ldr, new java.util.HashMap<String, cpw.mods.fml.common.ModContainer>());
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            t.printStackTrace();
        }

        try {
            net.minecraft.init.Bootstrap.func_151354_b();
        } catch (Throwable t) {
            t.printStackTrace();
        }

        try {
            java.lang.reflect.Field mcHome = cpw.mods.fml.relauncher.FMLInjectionData.class
                .getDeclaredField("minecraftHome");
            mcHome.setAccessible(true);
            mcHome.set(null, new java.io.File("."));
        } catch (Throwable ignored) {}

        try {
            if (gregtech.GTMod.proxy == null) {
                gregtech.GTMod.proxy = org.mockito.Mockito.mock(gregtech.common.GTProxy.class);
            }
            if (gregtech.GTMod.GT == null) {
                gregtech.GTMod.GT = org.mockito.Mockito.mock(gregtech.GTMod.class);
            }
            if (gregtech.api.enums.GTValues.RA == null) {
                gregtech.api.enums.GTValues.RA = new gregtech.common.RecipeAdder();
            }
        } catch (Throwable ignored) {}

        try {
            java.lang.reflect.Field sideField = cpw.mods.fml.relauncher.FMLRelaunchLog.class.getDeclaredField("side");
            sideField.setAccessible(true);
            sideField.set(null, cpw.mods.fml.relauncher.Side.SERVER);
        } catch (Throwable t) {
            t.printStackTrace();
        }

        try {
            cpw.mods.fml.common.IFMLSidedHandler sided = org.mockito.Mockito.mock(cpw.mods.fml.common.IFMLSidedHandler.class);
            org.mockito.Mockito.when(sided.getSide()).thenReturn(cpw.mods.fml.relauncher.Side.SERVER);
            java.lang.reflect.Field sidedField = cpw.mods.fml.common.FMLCommonHandler.class.getDeclaredField("sidedDelegate");
            sidedField.setAccessible(true);
            sidedField.set(cpw.mods.fml.common.FMLCommonHandler.instance(), sided);
        } catch (Throwable ignored) {}
    }
}
