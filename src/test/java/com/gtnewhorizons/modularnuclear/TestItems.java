package com.gtnewhorizons.modularnuclear;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry;

/**
 * Items registered under exact registry names, standing in for other mods' items: config entries match items by
 * registry name, so a test item needs one. The same name always gives the same item.
 */
public final class TestItems {

    private static final Map<String, Item> BY_NAME = new HashMap<>();
    private static int nextId = 30000;

    private TestItems() {}

    /** The item registered as {@code modid:name}, made the first time it is asked for. */
    public static synchronized Item item(String registryName) {
        TestEnvironmentHelper.init();
        Item existing = BY_NAME.get(registryName);
        if (existing != null) return existing;
        Object registered = Item.itemRegistry.getObject(registryName);
        if (registered instanceof Item item) {
            BY_NAME.put(registryName, item);
            return item;
        }
        Item item = new Item().setUnlocalizedName(registryName.substring(registryName.indexOf(':') + 1));
        register(item, registryName);
        BY_NAME.put(registryName, item);
        return item;
    }

    /** Registers an item made by the test (e.g. a subclass) under a registry name. */
    public static synchronized <T extends Item> T register(T item, String registryName) {
        TestEnvironmentHelper.init();
        try {
            Method raw = FMLControlledNamespacedRegistry.class
                .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
            raw.setAccessible(true);
            raw.invoke(Item.itemRegistry, nextId++, registryName, item);
            // The registry's delegate name is what the ore dictionary keys items by
            Method setName = Class.forName("cpw.mods.fml.common.registry.RegistryDelegate$Delegate")
                .getDeclaredMethod("setName", String.class);
            setName.setAccessible(true);
            setName.invoke(item.delegate, registryName);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not register test item " + registryName, e);
        }
        BY_NAME.put(registryName, item);
        return item;
    }

    /**
     * A stack of an item registered in the ore dictionary under exactly {@code oreName} (and nothing else), standing in
     * for any of that ore name's items. The same name always gives the same item.
     */
    public static synchronized ItemStack ore(String oreName) {
        String registryName = "test:ore." + oreName;
        boolean fresh = !BY_NAME.containsKey(registryName);
        Item item = item(registryName);
        if (fresh) OreDictionary.registerOre(oreName, new ItemStack(item, 1, 0));
        return new ItemStack(item, 1, 0);
    }

    public static ItemStack stack(String registryName) {
        return stack(registryName, 0);
    }

    public static ItemStack stack(String registryName, int meta) {
        return new ItemStack(item(registryName), 1, meta);
    }
}
