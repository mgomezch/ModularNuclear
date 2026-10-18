package com.gtnewhorizons.modularnuclear.common.metatileentity.hatch;

import static gregtech.api.enums.Textures.BlockIcons.ITEM_IN_SIGN;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_COLORS;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_IN;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.block.BlockNuclearCasing;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularui.api.math.Color;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTSplit;

/**
 * Dumb item container hatch for the modular nuclear reactor.
 * Holds 1 input slot (slot 0).
 * All nuclear physics, depletion, and energy generation logic is processed by MTENuclearReactor.
 */
public class MTEHatchNuclearBus extends MTEHatch {

    public static final int SLOT_INPUT = 0;

    public MTENuclearReactor mReactor;
    public double mTemperature = NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;
    public double mHeatHu = 0.0;
    public double mHeatEU = 0.0; // Backwards-compatible alias
    public long mCumulativeFluence = 0L;

    public double getAmbientTemperature() {
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().getWorld() != null) {
            return MTENuclearReactor.calculateAmbientTemperature(
                getBaseMetaTileEntity().getWorld(),
                getBaseMetaTileEntity().getXCoord(),
                getBaseMetaTileEntity().getYCoord(),
                getBaseMetaTileEntity().getZCoord());
        }
        return NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;
    }

    public int mFastFlux = 0;
    public int mThermalFlux = 0;
    public int mFastAbsorbed = 0;
    public int mThermalAbsorbed = 0;
    public int mLastFastFlux = 0;
    public int mLastThermalFlux = 0;
    public int mLastFastAbsorbed = 0;
    public int mLastThermalAbsorbed = 0;
    public int mLastNeutronsGenerated = 0;
    public long mDirectEUProduced = 0;
    public double mLastHeatOutput = 0.0;
    public boolean mUsedForCooling = false;
    public long mLastCheeseTick = -1;

    public MTEHatchNuclearBus(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier, 1, (String) null);
    }

    public MTEHatchNuclearBus(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 1, aDescription, aTextures);
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalized("gt.blockmachines.hatch.nuclearbus.desc");
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchNuclearBus(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public int getCapacity() {
        return 0;
    }

    @Override
    public boolean doesFillContainers() {
        return false;
    }

    @Override
    public boolean doesEmptyContainers() {
        return false;
    }

    @Override
    public boolean canTankBeFilled() {
        return false;
    }

    @Override
    public boolean canTankBeEmptied() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    public boolean isItemAllowed(ItemStack aStack) {
        return com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.isItemAllowedInBus(aStack, this);
    }

    @Override
    public boolean isValidSlot(int aIndex) {
        return aIndex == SLOT_INPUT;
    }

    @Override
    public boolean isItemValidForSlot(int aIndex, ItemStack aStack) {
        return aIndex == SLOT_INPUT && isItemAllowed(aStack);
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection aSide,
        ItemStack aStack) {
        // Automation can ONLY insert allowlisted components into slot 0, and only if empty (stack size 1)
        return aIndex == SLOT_INPUT && mInventory[SLOT_INPUT] == null && isItemAllowed(aStack);
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection aSide,
        ItemStack aStack) {
        // Input-only bus: automation cannot pull components out
        return false;
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return true;
    }

    @Override
    public ITexture getCasingTexture() {
        ITexture tex = super.getCasingTexture();
        if (tex != null) return tex;
        tex = Textures.BlockIcons.getCasingTextureForId(BlockNuclearCasing.CASING_TEXTURE_INDEX);
        if (tex != null) return tex;
        return Textures.BlockIcons.MACHINE_CASINGS[mTier][0];
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        byte color = getBaseMetaTileEntity() == null ? -1 : getBaseMetaTileEntity().getColorization();
        ITexture coloredOverlay = TextureFactory.of(OVERLAY_PIPE_COLORS[color + 1]);
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_PIPE_IN), coloredOverlay,
            TextureFactory.of(ITEM_IN_SIGN) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        byte color = getBaseMetaTileEntity() == null ? -1 : getBaseMetaTileEntity().getColorization();
        ITexture coloredOverlay = TextureFactory.of(OVERLAY_PIPE_COLORS[color + 1]);
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_PIPE_IN), coloredOverlay,
            TextureFactory.of(ITEM_IN_SIGN) };
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setDouble("mTemperature", mTemperature);
        aNBT.setDouble("mHeatHu", mHeatHu);
        aNBT.setDouble("mHeatEU", mHeatHu);
        aNBT.setLong("mCumulativeFluence", mCumulativeFluence);
        aNBT.setBoolean("mUsedForCooling", mUsedForCooling);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("mTemperature")) {
            mTemperature = aNBT.getDouble("mTemperature");
        } else {
            mTemperature = getAmbientTemperature();
        }
        if (aNBT.hasKey("mHeatHu")) {
            mHeatHu = aNBT.getDouble("mHeatHu");
        } else if (aNBT.hasKey("mHeatEU")) {
            mHeatHu = aNBT.getDouble("mHeatEU");
        }
        mHeatEU = mHeatHu;
        if (aNBT.hasKey("mCumulativeFluence")) {
            mCumulativeFluence = aNBT.getLong("mCumulativeFluence");
        }
        if (aNBT.hasKey("mUsedForCooling")) {
            mUsedForCooling = aNBT.getBoolean("mUsedForCooling");
        }
        // Legacy migration: if older world save has mWignerEnergyStored on the bus, migrate it to the inserted rod
        if (aNBT.hasKey("mWignerEnergyStored")) {
            double legacyWigner = aNBT.getDouble("mWignerEnergyStored");
            if (legacyWigner > 0.0 && mInventory[SLOT_INPUT] != null) {
                ItemStack stack = mInventory[SLOT_INPUT];
                if (stack.stackTagCompound == null) {
                    stack.stackTagCompound = new NBTTagCompound();
                }
                if (!stack.stackTagCompound.hasKey("wignerEnergy")) {
                    stack.stackTagCompound.setDouble("wignerEnergy", legacyWigner);
                }
            }
        }
    }

    public void addWignerEnergy(double amountHu) {
        if (amountHu <= 0.0) return;
        ItemStack stack = mInventory[SLOT_INPUT];
        if (stack == null) return;
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData mod = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findModerator(stack);
        if (mod != null && mod.wignerEnergyPerFast > 0.0) {
            double maxCap = mod.wignerMaxEnergy > 0.0 ? mod.wignerMaxEnergy : 10000.0;
            double current = 0.0;
            if (stack.stackTagCompound != null && stack.stackTagCompound.hasKey("wignerEnergy")) {
                current = stack.stackTagCompound.getDouble("wignerEnergy");
            }
            double next = Math.min(maxCap, current + amountHu);
            if (stack.stackTagCompound == null) {
                stack.stackTagCompound = new NBTTagCompound();
            }
            stack.stackTagCompound.setDouble("wignerEnergy", next);
            markTileDirty();
        }
    }

    public double releaseWignerEnergy() {
        ItemStack stack = mInventory[SLOT_INPUT];
        if (stack == null) return 0.0;
        if (stack.stackTagCompound == null || !stack.stackTagCompound.hasKey("wignerEnergy")) {
            return 0.0;
        }
        double current = stack.stackTagCompound.getDouble("wignerEnergy");
        if (current <= 0.0) return 0.0;
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData mod = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findModerator(stack);
        if (mod == null || mod.wignerEnergyPerFast <= 0.0) {
            stack.stackTagCompound.removeTag("wignerEnergy");
            markTileDirty();
            return 0.0;
        }
        double released = mod.calculateWignerHeatRelease(mTemperature, current);
        if (released > 0.0) {
            double remaining = Math.max(0.0, current - released);
            if (remaining <= 0.0) {
                stack.stackTagCompound.removeTag("wignerEnergy");
            } else {
                stack.stackTagCompound.setDouble("wignerEnergy", remaining);
            }
            markTileDirty();
        }
        return released;
    }

    public double getStoredWignerEnergy() {
        ItemStack stack = mInventory[SLOT_INPUT];
        if (stack != null && stack.stackTagCompound != null && stack.stackTagCompound.hasKey("wignerEnergy")) {
            return stack.stackTagCompound.getDouble("wignerEnergy");
        }
        return 0.0;
    }

    public void markTileDirty() {
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().markDirty();
        }
    }

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
     * Cached list of fluid extraction recipes that output molten cheese, queried by output (like NEI).
     */
    private static final List<GTRecipe> CHEESE_EXTRACTION_RECIPES = new CopyOnWriteArrayList<>();
    private static volatile boolean sCheeseRecipesLoaded = false;

    /**
     * Cache mapping individual item inputs to their matched cheese extraction recipe (or empty).
     */
    private static final Map<RecipeCacheKey, Optional<GTRecipe>> CHEESE_RECIPE_CACHE = new ConcurrentHashMap<>();

    /**
     * Queries and caches all fluid extraction recipes by output to isolate molten cheese recipes.
     */
    public static void loadCheeseRecipes() {
        if (sCheeseRecipesLoaded) return;
        synchronized (CHEESE_EXTRACTION_RECIPES) {
            if (sCheeseRecipesLoaded) return;
            try {
                if (RecipeMaps.fluidExtractionRecipes != null) {
                    for (GTRecipe recipe : RecipeMaps.fluidExtractionRecipes.getAllRecipes()) {
                        if (recipe.mFluidOutputs != null) {
                            for (FluidStack out : recipe.mFluidOutputs) {
                                if (out != null && isMoltenCheese(out)) {
                                    CHEESE_EXTRACTION_RECIPES.add(recipe);
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
            sCheeseRecipesLoaded = true;
        }
    }

    /**
     * Clears all cached cheese recipes and input lookup results, primarily for unit testing.
     */
    public static void clearCheeseRecipeCache() {
        synchronized (CHEESE_EXTRACTION_RECIPES) {
            CHEESE_EXTRACTION_RECIPES.clear();
            CHEESE_RECIPE_CACHE.clear();
            sCheeseRecipesLoaded = false;
        }
    }

    /**
     * Adds a cheese extraction recipe to the cached cheese recipes list, for registration or testing.
     */
    public static void registerCheeseRecipe(GTRecipe recipe) {
        if (recipe == null) return;
        loadCheeseRecipes();
        CHEESE_EXTRACTION_RECIPES.add(recipe);
        CHEESE_RECIPE_CACHE.clear();
    }

    /**
     * Immutable cache key for input items matching fluid extraction recipes.
     */
    public static final class RecipeCacheKey {

        private final Item item;
        private final int damage;
        private final NBTTagCompound nbt;

        public RecipeCacheKey(ItemStack stack) {
            Item it = null;
            try {
                it = stack.getItem();
            } catch (Exception ignored) {}
            this.item = it;

            int dmg = 0;
            if (Items.feather != null) {
                dmg = Items.feather.getDamage(stack);
            } else {
                try {
                    dmg = stack.getItemDamage();
                } catch (Exception ignored) {}
            }
            this.damage = dmg;
            this.nbt = stack.getTagCompound();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
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

    public GTRecipe findCheeseExtractionRecipe(ItemStack stack) {
        if (stack == null) return null;
        if (!sCheeseRecipesLoaded) {
            loadCheeseRecipes();
        }

        RecipeCacheKey key = new RecipeCacheKey(stack);
        Optional<GTRecipe> cached = CHEESE_RECIPE_CACHE.get(key);
        if (cached != null) {
            return cached.orElse(null);
        }

        for (GTRecipe recipe : CHEESE_EXTRACTION_RECIPES) {
            if (recipe.isRecipeInputEqual(false, true, null, stack)) {
                CHEESE_RECIPE_CACHE.put(key, Optional.of(recipe));
                return recipe;
            }
        }

        CHEESE_RECIPE_CACHE.put(key, Optional.empty());
        return null;
    }

    public static boolean isCheeseRecipeInput(ItemStack stack) {
        if (stack == null) return false;
        if (!sCheeseRecipesLoaded) {
            loadCheeseRecipes();
        }

        RecipeCacheKey key = new RecipeCacheKey(stack);
        Optional<GTRecipe> cached = CHEESE_RECIPE_CACHE.get(key);
        if (cached != null) {
            return cached.isPresent();
        }

        for (GTRecipe recipe : CHEESE_EXTRACTION_RECIPES) {
            if (recipe.isRecipeInputEqual(false, true, null, stack)) {
                CHEESE_RECIPE_CACHE.put(key, Optional.of(recipe));
                return true;
            }
        }

        CHEESE_RECIPE_CACHE.put(key, Optional.empty());
        return false;
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity,
        net.minecraft.entity.player.EntityPlayer aPlayer) {
        if (aPlayer != null && aPlayer.getHeldItem() != null && mReactor != null) {
            if (mReactor.handleSensorCardLinking(aPlayer.getHeldItem(), aPlayer, mReactor.getBaseMetaTileEntity())) {
                return true;
            }
        }
        openGui(aPlayer);
        return true;
    }

    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    public com.cleanroommc.modularui.screen.ModularPanel buildUI(com.cleanroommc.modularui.factory.PosGuiData data,
        com.cleanroommc.modularui.value.sync.PanelSyncManager syncManager,
        com.cleanroommc.modularui.screen.UISettings uiSettings) {
        return new com.gtnewhorizons.modularnuclear.common.gui.MTEHatchNuclearBusGui(this)
            .build(data, syncManager, uiSettings);
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        builder.widget(
            new DrawableWidget().setDrawable(GTUITextures.PICTURE_SCREEN_BLACK)
                .setPos(7, 16)
                .setSize(120, 56))
            .widget(
                new TextWidget("Nuclear core bus").setDefaultColor(Color.rgb(0, 255, 128))
                    .setPos(10, 20))
            .widget(
                new TextWidget().setStringSupplier(() -> String.format("Temp: %.1f °C", mTemperature))
                    .setDefaultColor(Color.rgb(255, 200, 0))
                    .setPos(10, 31))
            .widget(
                new TextWidget().setStringSupplier(() -> String.format("Fast: %d n/t", mLastFastFlux))
                    .setDefaultColor(Color.rgb(100, 200, 255))
                    .setPos(10, 42))
            .widget(
                new TextWidget().setStringSupplier(() -> String.format("Thrm: %d n/t", mLastThermalFlux))
                    .setDefaultColor(Color.rgb(150, 180, 255))
                    .setPos(10, 53))
            .widget(
                new TextWidget("In").setDefaultColor(0xFFFFFFFF)
                    .setPos(134, 16))
            .widget(
                new SlotWidget(inventoryHandler, SLOT_INPUT).setFilter(this::isItemAllowed)
                    .setBackground(getGUITextureSet().getItemSlot(), GTUITextures.OVERLAY_SLOT_IN)
                    .setPos(131, 28));
    }
}
