package com.gtnewhorizons.modularnuclear.common.gui.metrics;

import java.util.function.Supplier;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.modularnuclear.common.gui.PanelDragHandle;
import com.gtnewhorizons.modularnuclear.common.gui.ResizeableFloatingPanel;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;

import gregtech.api.enums.ItemList;

/**
 * Floating, resizable ModularUI 2 window displaying live reactor metrics over the last 100 cycles.
 * Activated when a Computer Screen cover is installed on the MPTR controller.
 */
public class ReactorMetricsDashboardPanel {

    public static final String PANEL_NAME = "reactorMetricsDashboardPanel";

    public static ModularPanel createPanel(PanelSyncManager syncManager, ModularPanel parent,
        MTENuclearReactor reactor) {
        ModularPanel panel = new ResizeableFloatingPanel(PANEL_NAME, parent).relativeToScreen()
            .center()
            .size(400, 240)
            .widgetTheme(gregtech.api.modularui2.GTWidgetThemes.BACKGROUND_POPUP)
            .resizeableOnDrag(true);

        // Network synchronization of 100-cycle telemetry history
        GenericSyncValue<ReactorMetricsHistory, ?> metricsSyncer = new GenericSyncValue<>(
            reactor::collectMetricsHistory,
            reactor::applyMetricsHistory,
            ReactorMetricsHistory::readFromBuffer,
            ReactorMetricsHistory::writeToBuffer);
        syncManager.syncValue("metricsHistory", metricsSyncer);

        // Header bar with Computer Screen icon, drag handle (no title), and close button
        Flow headerRow = Flow.row()
            .fullWidth()
            .height(18)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .marginBottom(4)
            .child(
                new ItemDisplayWidget().item(ItemList.Cover_Screen.get(1L))
                    .size(16, 16)
                    .displayAmount(false)
                    .marginRight(4))
            .child(
                new PanelDragHandle(panel).expanded()
                    .height(14))
            .child(
                new ButtonWidget<>().size(14, 14)
                    .child(
                        IKey.str("x")
                            .asWidget()
                            .center())
                    .tooltipBuilder(t -> t.addLine("Close window"))
                    .onMousePressed(d -> {
                        panel.closeIfOpen();
                        return true;
                    }));

        // Scrollable list area with vertical scrollbar
        ListWidget<Widget<?>, ?> listWidget = new ListWidget<>();
        listWidget.fullWidth();
        listWidget.expanded();
        listWidget.scrollDirection(new VerticalScrollData(false, 8));
        listWidget.showScrollShadows(true);

        Supplier<ReactorMetricsHistory> historySupplier = () -> {
            ReactorMetricsHistory client = reactor.getClientMetricsHistory();
            if (client != null) return client;
            return reactor.getMetricsHistory();
        };

        // Add 7 telemetry plots
        listWidget.child(new ReactorPlotWidget(ReactorPlotWidget.Metric.CORE_TEMP, historySupplier));
        listWidget.child(new ReactorPlotWidget(ReactorPlotWidget.Metric.REACTIVITY, historySupplier));
        listWidget.child(new ReactorPlotWidget(ReactorPlotWidget.Metric.FAST_NEUTRONS, historySupplier));
        listWidget.child(new ReactorPlotWidget(ReactorPlotWidget.Metric.THERMAL_NEUTRONS, historySupplier));
        listWidget.child(new ReactorPlotWidget(ReactorPlotWidget.Metric.ESCAPED_NEUTRONS, historySupplier));
        listWidget.child(new ReactorPlotWidget(ReactorPlotWidget.Metric.DIRECT_EU, historySupplier));
        listWidget.child(new ReactorPlotWidget(ReactorPlotWidget.Metric.COOLANT_OUTPUT, historySupplier));

        panel.child(
            Flow.column()
                .full()
                .padding(4)
                .child(headerRow)
                .child(listWidget));

        return panel;
    }
}
