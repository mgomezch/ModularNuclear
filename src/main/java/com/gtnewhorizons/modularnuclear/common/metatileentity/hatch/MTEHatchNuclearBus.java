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

    @Override
    public int getSlotLimit(int aIndex) {
        return 1;
    }

    @Override
    public int getStackSizeLimit(int aIndex, ItemStack aStack) {
        if (aIndex == SLOT_INPUT) {
            if (aStack == null || !isItemAllowed(aStack)) {
                return 0;
            }
            return 1;
        }
        return super.getStackSizeLimit(aIndex, aStack);
    }

    @Override
    public void setInventorySlotContents(int aIndex, ItemStack aStack) {
        if (aIndex == SLOT_INPUT) {
            if (aStack != null) {
                if (!isItemAllowed(aStack)) {
                    return;
                }
                if (aStack.stackSize > 1) {
                    aStack = aStack.copy();
                    aStack.stackSize = 1;
                }
            }
        }
        super.setInventorySlotContents(aIndex, aStack);
    }

    @Override
    public void onContentsChanged(int aIndex) {
        if (aIndex == SLOT_INPUT && mInventory[SLOT_INPUT] != null) {
            if (!isItemAllowed(mInventory[SLOT_INPUT])) {
                mInventory[SLOT_INPUT] = null;
            } else if (mInventory[SLOT_INPUT].stackSize > 1) {
                mInventory[SLOT_INPUT].stackSize = 1;
            }
        }
        super.onContentsChanged(aIndex);
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
        return aIndex == SLOT_INPUT && aStack != null && isItemAllowed(aStack);
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
        }
        if (aNBT.hasKey("mCumulativeFluence")) {
            mCumulativeFluence = aNBT.getLong("mCumulativeFluence");
        }
        if (aNBT.hasKey("mUsedForCooling")) {
            mUsedForCooling = aNBT.getBoolean("mUsedForCooling");
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

    public void processMaxDamageFailsafe() {
        if (mReactor == null) return;
        ItemStack stack = mInventory[SLOT_INPUT];
        if (stack == null) return;

        if (com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.isFuelAtMaxDamage(stack)) {
            mReactor.mCycleZeroedFuelItems++;
            ItemStack depleted = com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.getItemDepletedForm(stack);
            mInventory[SLOT_INPUT] = null;
            if (depleted != null) {
                mReactor.addOutputPartial(depleted);
            }
            markTileDirty();
        } else if (com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.isModeratorAtMaxDamage(stack)) {
            mReactor.mCycleZeroedFuelItems++;
            ItemStack byproduct = com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.getModeratorSpentProduct(stack);
            if (stack.stackSize > 1) {
                stack.stackSize--;
                if (stack.stackTagCompound != null) {
                    stack.stackTagCompound.removeTag("advDmg");
                }
            } else {
                mInventory[SLOT_INPUT] = null;
            }
            if (byproduct != null) {
                mReactor.addOutputPartial(byproduct);
            }
            markTileDirty();
        }
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide()) {
            if (mReactor != null && mInventory[SLOT_INPUT] != null) {
                processMaxDamageFailsafe();
            }
        }
    }

    public static boolean isMoltenCheese(FluidStack fluid) {
        return com.gtnewhorizons.modularnuclear.common.recipe.CheeseRecipeRegistry.isMoltenCheese(fluid);
    }

    public static void loadCheeseRecipes() {
        com.gtnewhorizons.modularnuclear.common.recipe.CheeseRecipeRegistry.getInstance().loadRecipes();
    }

    public static void clearCheeseRecipeCache() {
        com.gtnewhorizons.modularnuclear.common.recipe.CheeseRecipeRegistry.getInstance().clear();
    }

    public static void registerCheeseRecipe(GTRecipe recipe) {
        com.gtnewhorizons.modularnuclear.common.recipe.CheeseRecipeRegistry.getInstance().registerRecipe(recipe);
    }

    public static class RecipeCacheKey extends com.gtnewhorizons.modularnuclear.common.recipe.CheeseRecipeRegistry.RecipeCacheKey {

        public RecipeCacheKey(ItemStack stack) {
            super(stack);
        }
    }

    public GTRecipe findCheeseExtractionRecipe(ItemStack stack) {
        return com.gtnewhorizons.modularnuclear.common.recipe.CheeseRecipeRegistry.getInstance().findRecipe(stack);
    }

    public static boolean isCheeseRecipeInput(ItemStack stack) {
        return com.gtnewhorizons.modularnuclear.common.recipe.CheeseRecipeRegistry.getInstance().isCheeseInput(stack);
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
