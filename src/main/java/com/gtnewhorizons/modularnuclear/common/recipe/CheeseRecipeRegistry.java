package com.gtnewhorizons.modularnuclear.common.recipe;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.util.Swallowed;

import gregtech.api.enums.Materials;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;

/**
 * Global singleton managing cached molten-cheese fluid extraction recipes and input item sets.
 * Avoids redundant recipe lookups across multiple reactors and cycle ticks.
 */
public class CheeseRecipeRegistry {

    private static final CheeseRecipeRegistry INSTANCE = new CheeseRecipeRegistry();

    public static CheeseRecipeRegistry getInstance() {
        return INSTANCE;
    }

    private final List<GTRecipe> cheeseRecipes = new CopyOnWriteArrayList<>();
    private final Set<Item> cheeseInputItems = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<RecipeCacheKey, Optional<GTRecipe>> recipeCache = new ConcurrentHashMap<>();
    private volatile boolean isLoaded = false;
    private volatile boolean hasDynamicOrUnknownInputs = false;

    private CheeseRecipeRegistry() {}

    /**
     * Cache key for item inputs matching fluid extraction recipes.
     */
    public static class RecipeCacheKey {

        private final Item item;
        private final int damage;
        private final NBTTagCompound nbt;

        public RecipeCacheKey(ItemStack stack) {
            Item it = null;
            try {
                it = stack.getItem();
            } catch (Exception swallowed) {
                Swallowed.note(swallowed);
            }
            this.item = it;

            int dmg = 0;
            if (Items.feather != null) {
                try {
                    dmg = Items.feather.getDamage(stack);
                } catch (Exception swallowed) {
                    Swallowed.note(swallowed);
                    try {
                        dmg = stack.getItemDamage();
                    } catch (Exception swallowedAgain) {
                        Swallowed.note(swallowedAgain);
                    }
                }
            } else {
                try {
                    dmg = stack.getItemDamage();
                } catch (Exception swallowed) {
                    Swallowed.note(swallowed);
                }
            }
            this.damage = dmg;
            this.nbt = stack.getTagCompound();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || !(o instanceof RecipeCacheKey)) return false;
            RecipeCacheKey that = (RecipeCacheKey) o;
            if (damage != that.damage || item != that.item) return false;
            return (nbt == null && that.nbt == null) || (nbt != null && nbt.equals(that.nbt));
        }

        @Override
        public int hashCode() {
            int result = item != null ? item.hashCode() : 0;
            result = 31 * result + damage;
            result = 31 * result + (nbt != null ? nbt.hashCode() : 0);
            return result;
        }
    }

    /**
     * Determines whether a fluid stack represents molten cheese.
     */
    public static boolean isMoltenCheese(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) return false;
        if (Materials.Cheese != null && (fluid.getFluid() == Materials.Cheese.mStandardMoltenFluid
            || Materials.FLUID_MAP.get(fluid.getFluid()) == Materials.Cheese)) {
            return true;
        }
        String name = fluid.getFluid()
            .getName();
        return "molten.cheese".equalsIgnoreCase(name) || "cheese".equalsIgnoreCase(name)
            || "fluid.cheese".equalsIgnoreCase(name);
    }

    /**
     * Scans fluid extraction recipes once globally and indexes all molten cheese recipes and input items.
     */
    public void loadRecipes() {
        if (isLoaded) return;
        synchronized (this) {
            if (isLoaded) return;
            try {
                if (RecipeMaps.fluidExtractionRecipes != null) {
                    for (GTRecipe recipe : RecipeMaps.fluidExtractionRecipes.getAllRecipes()) {
                        if (recipe.mFluidOutputs != null) {
                            for (FluidStack out : recipe.mFluidOutputs) {
                                if (out != null && isMoltenCheese(out)) {
                                    cheeseRecipes.add(recipe);
                                    if (recipe.mInputs != null && recipe.mInputs.length > 0) {
                                        for (ItemStack in : recipe.mInputs) {
                                            if (in != null && in.getItem() != null) {
                                                cheeseInputItems.add(in.getItem());
                                                recipeCache.put(new RecipeCacheKey(in), Optional.of(recipe));
                                            }
                                        }
                                    } else {
                                        hasDynamicOrUnknownInputs = true;
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable swallowed) {
                Swallowed.note(swallowed);
            }
            isLoaded = true;
        }
    }

    /**
     * Clears cached recipes and input lookups. Primarily for unit testing or recipe reloading.
     */
    public void clear() {
        synchronized (this) {
            cheeseRecipes.clear();
            cheeseInputItems.clear();
            recipeCache.clear();
            hasDynamicOrUnknownInputs = false;
            isLoaded = false;
        }
    }

    /**
     * Registers a single cheese extraction recipe into the global singleton.
     */
    public void registerRecipe(GTRecipe recipe) {
        if (recipe == null) return;
        loadRecipes();
        recipeCache.clear();
        cheeseRecipes.add(recipe);
        if (recipe.mInputs != null && recipe.mInputs.length > 0) {
            for (ItemStack in : recipe.mInputs) {
                if (in != null && in.getItem() != null) {
                    cheeseInputItems.add(in.getItem());
                    recipeCache.put(new RecipeCacheKey(in), Optional.of(recipe));
                }
            }
        } else {
            hasDynamicOrUnknownInputs = true;
        }
    }

    /**
     * Fast check whether an ItemStack can be extracted into molten cheese.
     * Uses O(1) item set lookup before falling back to cached recipe evaluations.
     */
    public boolean isCheeseInput(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        if (!isLoaded) {
            loadRecipes();
        }

        // Fast O(1) set lookup if all recipe inputs are known
        if (!hasDynamicOrUnknownInputs && !cheeseInputItems.contains(stack.getItem())) {
            return false;
        }

        RecipeCacheKey key = new RecipeCacheKey(stack);
        Optional<GTRecipe> cached = recipeCache.get(key);
        if (cached != null) {
            return cached.isPresent();
        }

        for (GTRecipe recipe : cheeseRecipes) {
            if (recipe.isRecipeInputEqual(false, true, null, stack)) {
                recipeCache.put(key, Optional.of(recipe));
                cheeseInputItems.add(stack.getItem());
                return true;
            }
        }

        recipeCache.put(key, Optional.empty());
        return false;
    }

    /**
     * Finds the matching molten cheese fluid extraction recipe for an ItemStack.
     * Uses O(1) item set filtering and memoized recipe cache.
     */
    public GTRecipe findRecipe(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        if (!isLoaded) {
            loadRecipes();
        }

        // Fast O(1) set lookup if all recipe inputs are known
        if (!hasDynamicOrUnknownInputs && !cheeseInputItems.contains(stack.getItem())) {
            return null;
        }

        RecipeCacheKey key = new RecipeCacheKey(stack);
        Optional<GTRecipe> cached = recipeCache.get(key);
        if (cached != null) {
            return cached.orElse(null);
        }

        for (GTRecipe recipe : cheeseRecipes) {
            if (recipe.isRecipeInputEqual(false, true, null, stack)) {
                recipeCache.put(key, Optional.of(recipe));
                cheeseInputItems.add(stack.getItem());
                return recipe;
            }
        }

        recipeCache.put(key, Optional.empty());
        return null;
    }

    public Set<Item> getCheeseInputItems() {
        if (!isLoaded) {
            loadRecipes();
        }
        return Collections.unmodifiableSet(cheeseInputItems);
    }

    public List<GTRecipe> getCheeseRecipes() {
        if (!isLoaded) {
            loadRecipes();
        }
        return Collections.unmodifiableList(cheeseRecipes);
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    public boolean hasDynamicOrUnknownInputs() {
        return hasDynamicOrUnknownInputs;
    }
}
