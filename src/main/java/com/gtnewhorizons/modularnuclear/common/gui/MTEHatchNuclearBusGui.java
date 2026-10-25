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
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.modularui2.GTWidgetThemes;
import gregtech.api.modularui2.common.CommonWidgets;
import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;

public class MTEHatchNuclearBusGui extends MTEHatchBaseGui<MTEHatchNuclearBus> {

    public MTEHatchNuclearBusGui(MTEHatchNuclearBus machine) {
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
            "neutronsGen",
            new IntSyncValue(() -> machine.mLastNeutronsGenerated, val -> machine.mLastNeutronsGenerated = val));
    }

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager syncManager) {
        Flow mainRow = Flow.row()
            .coverChildren()
            .childPadding(6);

        // Status and Telemetry Screen (width 118, height 54)
        ParentWidget<?> statsScreen = CommonWidgets.createFluidScreen(118, 54);
        Flow textColumn = Flow.column()
            .childPadding(1)
            .crossAxisAlignment(Alignment.CrossAxis.START);

        textColumn.child(
            IKey.dynamic(() -> EnumChatFormatting.GREEN + "Nuclear core bus")
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(
            IKey.dynamic(() -> EnumChatFormatting.GOLD + String.format("Temp: %.1f °C", machine.mTemperature))
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(IKey.dynamic(() -> {
            int tpc = machine.getTicksPerCycle();
            double cps = 20.0 / (double) tpc;
            long sec = Math.round(machine.mLastFastFlux * cps);
            return EnumChatFormatting.AQUA
                + String.format(Locale.US, "Fast: %,d n/s (%,d /cycle)", sec, machine.mLastFastFlux);
        })
            .asWidget()
            .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(IKey.dynamic(() -> {
            int tpc = machine.getTicksPerCycle();
            double cps = 20.0 / (double) tpc;
            long sec = Math.round(machine.mLastThermalFlux * cps);
            return EnumChatFormatting.BLUE
                + String.format(Locale.US, "Thermal: %,d n/s (%,d /cycle)", sec, machine.mLastThermalFlux);
        })
            .asWidget()
            .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(IKey.dynamic(() -> {
            net.minecraft.item.ItemStack stack = machine.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            double damp = com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor
                .getInsulationDampening(stack);
            if (damp >= 1.0) {
                return EnumChatFormatting.DARK_PURPLE + "Insulation: 100% (universal)";
            } else if (damp > 0.0) {
                return EnumChatFormatting.LIGHT_PURPLE + String.format(Locale.US, "Insulation: %.0f%%", damp * 100.0);
            }
            int tpc = machine.getTicksPerCycle();
            double cps = 20.0 / (double) tpc;
            long sec = Math.round(machine.mLastNeutronsGenerated * cps);
            return EnumChatFormatting.GRAY
                + String.format(Locale.US, "Neutrons: %,d n/s (%,d /cycle)", sec, machine.mLastNeutronsGenerated);
        })
            .asWidget()
            .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        statsScreen.child(textColumn);
        mainRow.child(statsScreen);

        // Input Slot Column (Slot 0 - active rod / component)
        Flow inputCol = Flow.column()
            .coverChildren()
            .childPadding(1)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER);

        inputCol.child(
            IKey.dynamic(() -> EnumChatFormatting.WHITE + "In")
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        inputCol.child(new ItemSlot().slot(new ModularSlot(machine.inventoryHandler, MTEHatchNuclearBus.SLOT_INPUT) {

            @Override
            public int getSlotStackLimit() {
                return 1;
            }

            @Override
            public int getItemStackLimit(ItemStack stack) {
                return (stack != null && machine.isItemAllowed(stack)) ? 1 : 0;
            }

            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack != null && machine.isItemAllowed(stack);
            }
        }.filter(machine::isItemAllowed)
            .singletonSlotGroup())
            .backgroundOverlay(GTGuiTextures.OVERLAY_SLOT_IN_STANDARD));

        mainRow.child(inputCol);

        return mainRow;
    }
}
