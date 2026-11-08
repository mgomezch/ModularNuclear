package com.gtnewhorizons.modularnuclear.common.recipe;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.enums.Materials;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;

/**
 * Global singleton managing cached molten-cheese fluid extraction recipes and input item sets.
 * Avoids redundant recipe lookups across multiple reactors and cycle ticks.
 */
public class CheeseRecipeRegistry {

    private static final CheeseRecipeRegistry INSTANCE = new CheeseRecipeRegistry(
        () -> RecipeMaps.fluidExtractionRecipes != null ? RecipeMaps.fluidExtractionRecipes.getAllRecipes()
            : Collections.emptyList(),
        () -> Materials.Cheese != null ? Materials.Cheese.mStandardMoltenFluid : null);

    /** The registry over GregTech's fluid extractor recipes, which the game's nuclear buses use. */
    public static CheeseRecipeRegistry getInstance() {
        return INSTANCE;
    }

    /** Where the registry finds its candidate recipes; it keeps those that make molten cheese. */
    private final Supplier<? extends Collection<GTRecipe>> source;
    /** Where it finds the molten cheese fluid. */
    private final Supplier<Fluid> moltenCheeseSource;
    /** The molten cheese fluid, resolved with the recipes. */
    private volatile Fluid moltenCheese;

    private final List<GTRecipe> cheeseRecipes = new CopyOnWriteArrayList<>();
    private final Set<Item> cheeseInputItems = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<RecipeCacheKey, Optional<GTRecipe>> recipeCache = new ConcurrentHashMap<>();
    private volatile boolean isLoaded = false;
    private volatile boolean hasDynamicOrUnknownInputs = false;

    /**
     * A registry over the recipes among {@code source}'s that make {@code moltenCheese}'s fluid, both read on first
     * use.
     */
    public CheeseRecipeRegistry(Supplier<? extends Collection<GTRecipe>> source, Supplier<Fluid> moltenCheese) {
        this.source = source;
        this.moltenCheeseSource = moltenCheese;
    }

    /**
     * Cache key for item inputs matching fluid extraction recipes.
     */
    public static class RecipeCacheKey {

        private final Item item;
        private final int damage;
        private final NBTTagCompound nbt;

        public RecipeCacheKey(ItemStack stack) {
            this.item = stack.getItem();
            this.damage = stack.getItemDamage();
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

    /** Whether a fluid stack is molten cheese: GregTech's Cheese material's molten fluid. */
    public boolean isMoltenCheese(FluidStack fluid) {
        loadRecipes();
        return fluid != null && moltenCheese != null && fluid.getFluid() == moltenCheese;
    }

    /**
     * Scans fluid extraction recipes once globally and indexes all molten cheese recipes and input items.
     */
    public void loadRecipes() {
        if (isLoaded) return;
        synchronized (this) {
            if (isLoaded) return;
            moltenCheese = moltenCheeseSource.get();
            for (GTRecipe recipe : source.get()) {
                if (recipe.mFluidOutputs != null) {
                    for (FluidStack out : recipe.mFluidOutputs) {
                        if (out != null && moltenCheese != null && out.getFluid() == moltenCheese) {
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

            isLoaded = true;
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
}
