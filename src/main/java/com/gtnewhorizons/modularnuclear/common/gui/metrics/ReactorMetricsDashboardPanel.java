package com.gtnewhorizons.modularnuclear.common.gui.metrics;

import java.util.function.Supplier;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.DragHandle;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;

import gregtech.api.enums.ItemList;
import gregtech.api.modularui2.common.CommonButtons;

/**
 * Floating, resizable ModularUI 2 window displaying live reactor metrics over the last 100 cycles.
 * Activated when a Computer Screen cover is installed on the MPTR controller.
 */
public class ReactorMetricsDashboardPanel {

    public static final String PANEL_NAME = "reactorMetricsDashboardPanel";

    public static ModularPanel createPanel(
        PanelSyncManager syncManager,
        ModularPanel parent,
        MTENuclearReactor reactor
    ) {
        ModularPanel panel = new ModularPanel(PANEL_NAME).relativeToScreen()
            .center()
            .size(440, 310)
            .resizeableOnDrag(true);

        // Network synchronization of 100-cycle telemetry history
        GenericSyncValue<ReactorMetricsHistory, ?> metricsSyncer = new GenericSyncValue<>(
            reactor::collectMetricsHistory,
            reactor::applyMetricsHistory,
            ReactorMetricsHistory::readFromBuffer,
            ReactorMetricsHistory::writeToBuffer);
        syncManager.syncValue("metricsHistory", metricsSyncer);

        // Header bar with Computer Screen icon, drag handle, and title
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
                new TextWidget<>(
                    IKey.str(
                        EnumChatFormatting.BOLD
                            + StatCollector.translateToLocal("modularnuclear.gui.title.metrics_dashboard")))
                                .color(Color.WHITE.main)
                                .height(14)
                                .marginRight(6))
            .child(new DragHandle().expanded().height(14));

        // Window close button (top right)
        panel.child(
            CommonButtons.panelCloseButton()
                .size(14, 14));

        // Scrollable content area
        ScrollWidget<?> scrollWidget = new ScrollWidget<>(new VerticalScrollData());
        scrollWidget.fullWidth();
        scrollWidget.expanded();

        Flow chartsColumn = Flow.column()
            .fullWidth()
            .coverChildrenHeight()
            .padding(2);

        Supplier<ReactorMetricsHistory> historySupplier = () -> {
            ReactorMetricsHistory client = reactor.getClientMetricsHistory();
            if (client != null) return client;
            return reactor.getMetricsHistory();
        };

        // Add 7 telemetry plots
        chartsColumn.child(new ReactorPlotWidget(ReactorPlotWidget.Metric.CORE_TEMP, historySupplier));
        chartsColumn.child(new ReactorPlotWidget(ReactorPlotWidget.Metric.REACTIVITY, historySupplier));
        chartsColumn.child(new ReactorPlotWidget(ReactorPlotWidget.Metric.FAST_NEUTRONS, historySupplier));
        chartsColumn.child(new ReactorPlotWidget(ReactorPlotWidget.Metric.THERMAL_NEUTRONS, historySupplier));
        chartsColumn.child(new ReactorPlotWidget(ReactorPlotWidget.Metric.ESCAPED_NEUTRONS, historySupplier));
        chartsColumn.child(new ReactorPlotWidget(ReactorPlotWidget.Metric.DIRECT_EU, historySupplier));
        chartsColumn.child(new ReactorPlotWidget(ReactorPlotWidget.Metric.COOLANT_OUTPUT, historySupplier));

        scrollWidget.child(chartsColumn);

        panel.child(
            Flow.column()
                .full()
                .padding(4)
                .child(headerRow.marginRight(18))
                .child(scrollWidget));

        return panel;
    }
}
