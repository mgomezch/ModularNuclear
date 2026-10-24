package com.gtnewhorizons.modularnuclear.common.metatileentity.hatch;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.modularnuclear.common.gui.MTEHatchNuclearAdvancedControlRodGui;
import com.gtnewhorizons.modularui.api.math.Color;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTSplit;

/**
 * Advanced Nuclear Core Control Rod Hatch.
 * Dual-slot variant holding both a control rod and a moderator displacer.
 * Slot 0 holds a control rod (silver, boron, cadmium, indium, hafnium).
 * Slot 1 holds a moderator item (graphite, beryllium, etc.).
 * The moderator displaces the control rod: insertion level is 1 - x where x is control rod insertion.
 */
public class MTEHatchNuclearAdvancedControlRod extends MTEHatchNuclearControlRod {

    public MTEHatchNuclearAdvancedControlRod(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier, 2);
    }

    public MTEHatchNuclearAdvancedControlRod(String aName, int aTier, String[] aDescription,
        ITexture[][][] aTextures) {
        super(aName, aTier, 2, aDescription, aTextures);
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalized("gt.blockmachines.hatch.nuclearadvancedcontrolrod.desc");
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchNuclearAdvancedControlRod(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public boolean isValidSlot(int aIndex) {
        return aIndex == SLOT_ROD || aIndex == SLOT_MODERATOR;
    }

    @Override
    public boolean isItemValidForSlot(int aIndex, ItemStack aStack) {
        if (aIndex == SLOT_ROD) {
            return mInventory[SLOT_ROD] == null && isControlRod(aStack);
        }
        if (aIndex == SLOT_MODERATOR) {
            return mInventory[SLOT_MODERATOR] == null && isModerator(aStack);
        }
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection aSide,
        ItemStack aStack) {
        return isItemValidForSlot(aIndex, aStack);
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection aSide,
        ItemStack aStack) {
        return aIndex == SLOT_ROD || aIndex == SLOT_MODERATOR;
    }

    @Override
    public ModularPanel buildUI(com.cleanroommc.modularui.factory.PosGuiData data, PanelSyncManager syncManager,
        UISettings uiSettings) {
        return new MTEHatchNuclearAdvancedControlRodGui(this).build(data, syncManager, uiSettings);
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        builder.widget(
            new DrawableWidget().setDrawable(GTUITextures.PICTURE_SCREEN_BLACK)
                .setPos(7, 16)
                .setSize(100, 60))
            .widget(
                new TextWidget("Adv. control rod").setDefaultColor(Color.rgb(180, 100, 255))
                    .setPos(10, 20))
            .widget(
                new TextWidget().setStringSupplier(() -> String.format("Temp: %.1f °C", mTemperature))
                    .setDefaultColor(Color.rgb(255, 200, 0))
                    .setPos(10, 31))
            .widget(new TextWidget().setStringSupplier(() -> {
                int fine = getFineRedstoneSignal();
                if (fine >= 0) {
                    return String.format("Rod: %d%% | Mod: %d%%", getInsertionPercent(), getModeratorInsertionPercent());
                }
                return String.format("Rod: %d%% | Mod: %d%%", getInsertionPercent(), getModeratorInsertionPercent());
            })
                .setDefaultColor(Color.rgb(100, 255, 200))
                .setPos(10, 42))
            .widget(
                new TextWidget().setStringSupplier(() -> "Rod: " + getRodType(mInventory[SLOT_ROD]).displayName)
                    .setDefaultColor(Color.rgb(200, 200, 255))
                    .setPos(10, 53))
            .widget(
                new TextWidget().setStringSupplier(() -> "Mod: " + getModeratorDisplayName())
                    .setDefaultColor(Color.rgb(150, 255, 150))
                    .setPos(10, 64))
            .widget(
                new TextWidget("Rod").setDefaultColor(0xFFFFFFFF)
                    .setPos(116, 16))
            .widget(
                new SlotWidget(inventoryHandler, SLOT_ROD).setFilter(MTEHatchNuclearControlRod::isControlRod)
                    .setBackground(getGUITextureSet().getItemSlot(), GTUITextures.OVERLAY_SLOT_IN)
                    .setPos(113, 28))
            .widget(
                new TextWidget("Mod").setDefaultColor(0xFFFFFFFF)
                    .setPos(142, 16))
            .widget(
                new SlotWidget(inventoryHandler, SLOT_MODERATOR).setFilter(MTEHatchNuclearControlRod::isModerator)
                    .setBackground(getGUITextureSet().getItemSlot(), GTUITextures.OVERLAY_SLOT_IN)
                    .setPos(139, 28));
    }
}
