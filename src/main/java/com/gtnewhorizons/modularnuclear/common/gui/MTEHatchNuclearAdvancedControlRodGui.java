package com.gtnewhorizons.modularnuclear.common.gui;

import java.util.Locale;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearAdvancedControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.modularui2.GTWidgetThemes;
import gregtech.api.modularui2.common.CommonWidgets;
import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;

public class MTEHatchNuclearAdvancedControlRodGui extends MTEHatchBaseGui<MTEHatchNuclearAdvancedControlRod> {

    public MTEHatchNuclearAdvancedControlRodGui(MTEHatchNuclearAdvancedControlRod machine) {
        super(machine);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue(
            "temperature",
            new DoubleSyncValue(() -> machine.mTemperature, val -> machine.mTemperature = val));
        syncManager
            .syncValue("fastFlux", new IntSyncValue(() -> machine.mLastFastFlux, val -> machine.mLastFastFlux = val));
        syncManager.syncValue(
            "thermalFlux",
            new IntSyncValue(() -> machine.mLastThermalFlux, val -> machine.mLastThermalFlux = val));
        syncManager.syncValue(
            "fastAbsorbed",
            new IntSyncValue(() -> machine.mLastFastAbsorbed, val -> machine.mLastFastAbsorbed = val));
        syncManager.syncValue(
            "thermalAbsorbed",
            new IntSyncValue(() -> machine.mLastThermalAbsorbed, val -> machine.mLastThermalAbsorbed = val));
        syncManager.syncValue(
            "scram",
            new com.cleanroommc.modularui.value.sync.BooleanSyncValue(
                () -> machine.mScram,
                val -> machine.mScram = val));
        syncManager.syncValue(
            "inputChannel",
            new IntSyncValue(() -> machine.mInputChannel, val -> machine.mInputChannel = val));
        syncManager.syncValue(
            "miningPipes",
            new IntSyncValue(() -> machine.mMiningPipeCount, val -> machine.mMiningPipeCount = val));
    }

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager syncManager) {
        Flow mainRow = Flow.row()
            .coverChildren()
            .childPadding(4);

        // Status and Telemetry Screen (width 110, height 64)
        ParentWidget<?> statsScreen = CommonWidgets.createFluidScreen(110, 64);
        Flow textColumn = Flow.column()
            .childPadding(0)
            .crossAxisAlignment(Alignment.CrossAxis.START);

        textColumn.child(
            IKey.dynamic(() -> EnumChatFormatting.LIGHT_PURPLE + "Adv. control rod")
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(
            IKey.dynamic(() -> EnumChatFormatting.GOLD + String.format("Temp: %.1f °C", machine.mTemperature))
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(IKey.dynamic(() -> {
            if (machine.mScram) {
                return EnumChatFormatting.RED + "Rod: 100% (SCRAM) | Mod: 0%";
            }
            int fine = machine.getFineRedstoneSignal();
            String src = (fine >= 0) ? "PR:" + fine : "RS:" + machine.getRedstoneSignal();
            return EnumChatFormatting.GREEN + String.format("Rod: %d%% (%s)", machine.getInsertionPercent(), src);
        })
            .asWidget()
            .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(
            IKey.dynamic(
                () -> {
                    return EnumChatFormatting.AQUA
                        + String.format("Mod: %d%% (displaced)", machine.getModeratorInsertionPercent());
                })
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(IKey.dynamic(() -> {
            ItemStack rod = machine.mInventory[MTEHatchNuclearControlRod.SLOT_ROD];
            String rodName = MTEHatchNuclearControlRod.getRodType(rod).displayName;
            return EnumChatFormatting.WHITE + "Rod: " + rodName;
        })
            .asWidget()
            .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(
            IKey.dynamic(() -> { return EnumChatFormatting.WHITE + "Mod: " + machine.getModeratorDisplayName(); })
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(IKey.dynamic(() -> {
            int abs = machine.mLastFastAbsorbed + machine.mLastThermalAbsorbed;
            int tpc = machine.getTicksPerCycle();
            double cps = 20.0 / (double) tpc;
            long absSec = Math.round(abs * cps);
            return EnumChatFormatting.GRAY + String.format(Locale.US, "Abs: %,d n/s (%,d /cycle)", absSec, abs);
        })
            .asWidget()
            .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        statsScreen.child(textColumn);
        mainRow.child(statsScreen);

        // Control Rod Slot Column
        Flow rodCol = Flow.column()
            .coverChildren()
            .childPadding(1)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER);

        rodCol.child(
            IKey.dynamic(() -> EnumChatFormatting.WHITE + "Rod")
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        rodCol.child(
            new ItemSlot()
                .slot(
                    new ModularSlot(machine.inventoryHandler, MTEHatchNuclearControlRod.SLOT_ROD)
                        .filter(MTEHatchNuclearControlRod::isControlRod)
                        .singletonSlotGroup())
                .backgroundOverlay(GTGuiTextures.OVERLAY_SLOT_IN_STANDARD));

        mainRow.child(rodCol);

        // Moderator Displacer Slot Column
        Flow modCol = Flow.column()
            .coverChildren()
            .childPadding(1)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER);

        modCol.child(
            IKey.dynamic(() -> EnumChatFormatting.WHITE + "Mod")
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        modCol.child(
            new ItemSlot()
                .slot(
                    new ModularSlot(machine.inventoryHandler, MTEHatchNuclearControlRod.SLOT_MODERATOR)
                        .filter(MTEHatchNuclearControlRod::isModerator)
                        .singletonSlotGroup())
                .backgroundOverlay(GTGuiTextures.OVERLAY_SLOT_IN_STANDARD));

        mainRow.child(modCol);

        return mainRow;
    }
}
