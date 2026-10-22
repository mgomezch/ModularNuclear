package com.gtnewhorizons.modularnuclear.common.gui;

import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressure;

import gregtech.api.modularui2.GTWidgetThemes;
import gregtech.api.modularui2.common.CommonWidgets;
import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;

public class MTEHatchNuclearHighPressureGui extends MTEHatchBaseGui<MTEHatchNuclearHighPressure> {

    public MTEHatchNuclearHighPressureGui(MTEHatchNuclearHighPressure machine) {
        super(machine);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue(
            "temperature",
            new DoubleSyncValue(machine::getTemperature, val -> machine.mTemperature = val));
        syncManager.syncValue(
            "fastFlux",
            new IntSyncValue(() -> machine.mLastFastFlux, val -> machine.mLastFastFlux = val));
        syncManager.syncValue(
            "thermalFlux",
            new IntSyncValue(() -> machine.mLastThermalFlux, val -> machine.mLastThermalFlux = val));
        syncManager.syncValue(
            "isInlet",
            new BooleanSyncValue(machine::isPassageInlet, machine::setIsInlet));
        syncManager.syncValue(
            "isPaired",
            new BooleanSyncValue(machine::hasOppositeHatch, val -> {}));
        syncManager.syncValue(
            "hasPump",
            new BooleanSyncValue(machine::hasConnectedPump, val -> {}));
    }

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager syncManager) {
        Flow mainRow = Flow.row()
            .coverChildren()
            .childPadding(4);

        // Status and telemetry screen (width 160, height 70)
        ParentWidget<?> statsScreen = CommonWidgets.createFluidScreen(160, 70);
        Flow textColumn = Flow.column()
            .childPadding(1)
            .crossAxisAlignment(Alignment.CrossAxis.START);

        textColumn.child(
            IKey.dynamic(() -> EnumChatFormatting.GOLD + "High-pressure passage")
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(
            IKey.dynamic(
                () -> EnumChatFormatting.YELLOW
                    + (machine.isPassageInlet() ? "Role: Core inlet (slice 0)" : "Role: Core outlet (slice 4)"))
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(
            IKey.dynamic(() -> EnumChatFormatting.GOLD + String.format("Core temp: %.1f °C", machine.getTemperature()))
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(
            IKey.dynamic(
                () -> EnumChatFormatting.AQUA
                    + String.format("Fast: %d n/t  Thrm: %d n/t", machine.mLastFastFlux, machine.mLastThermalFlux))
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(
            IKey.dynamic(
                () -> EnumChatFormatting.GREEN
                    + (machine.hasOppositeHatch() ? "Paired: Linked" : "Paired: Unpaired"))
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        textColumn.child(
            IKey.dynamic(
                () -> EnumChatFormatting.BLUE
                    + (machine.hasConnectedPump() ? "Pump: Connected" : "Pump: Disconnected"))
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE));

        statsScreen.child(textColumn);
        mainRow.child(statsScreen);

        return mainRow;
    }
}
