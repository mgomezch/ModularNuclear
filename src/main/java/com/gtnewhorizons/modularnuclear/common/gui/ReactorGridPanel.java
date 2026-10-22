package com.gtnewhorizons.modularnuclear.common.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.coolantloops.common.gui.LockIconWidget;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.nuclear.ReactorGridSyncData;

import gregtech.api.enums.ItemList;
import ic2.core.Ic2Items;

/**
 * ModularUI 2 Floating, movable, resizable panel for the Reactor Core Hatches grid.
 */
public class ReactorGridPanel {

    public static final String PANEL_NAME = "reactorGridPanel";

    public static ModularPanel createPanel(PanelSyncManager syncManager, ModularPanel parent,
        MTENuclearReactor reactor) {
        ModularPanel panel = new ResizeableFloatingPanel(PANEL_NAME, parent).relativeToScreen()
            .center()
            .size(380, 230)
            .widgetTheme(gregtech.api.modularui2.GTWidgetThemes.BACKGROUND_POPUP)
            .resizeableOnDrag(true);

        NuclearReactorGridWidget gridWidget = new NuclearReactorGridWidget(reactor, syncManager);

        // Server-client network sync of reactor grid telemetry data
        GenericSyncValue<ReactorGridSyncData, ?> gridSyncer = new GenericSyncValue<>(
            reactor::collectGridSyncData,
            reactor::applyGridSyncData,
            ReactorGridSyncData::readFromBuffer,
            ReactorGridSyncData::writeToBuffer);
        syncManager.syncValue("reactorGridData", gridSyncer);

        IntSyncValue modeSyncer = new IntSyncValue(() -> reactor.mCurrentGuiMode, v -> reactor.mCurrentGuiMode = v);
        syncManager.syncValue("reactorGuiMode", modeSyncer);

        // Synced actions for cell clicks and dragging
        syncManager.registerSyncedAction("slotClick", false, true, buf -> {
            int gx = buf.readInt();
            int gy = buf.readInt();
            int button = buf.readInt();
            boolean isShift = buf.readBoolean();
            EntityPlayer player = syncManager.getPlayer();
            if (player instanceof EntityPlayerMP playerMP) {
                NuclearReactorGridWidget.handleSlotClickServer(reactor, playerMP, gx, gy, button, isShift);
            }
        });

        syncManager.registerSyncedAction("batchShiftInsert", false, true, buf -> {
            int button = buf.readInt();
            EntityPlayer player = syncManager.getPlayer();
            if (player instanceof EntityPlayerMP playerMP) {
                NuclearReactorGridWidget.handleBatchShiftInsertServer(reactor, playerMP, button);
            }
        });

        syncManager.registerSyncedAction("dragStep", false, true, buf -> {
            int gx = buf.readInt();
            int gy = buf.readInt();
            int button = buf.readInt();
            EntityPlayer player = syncManager.getPlayer();
            if (player instanceof EntityPlayerMP playerMP) {
                NuclearReactorGridWidget.handleDragStepServer(reactor, playerMP, gx, gy, button);
            }
        });

        syncManager
            .registerSyncedAction("setGuiMode", false, true, buf -> { reactor.mCurrentGuiMode = buf.readInt(); });

        // Top Header Row with mode selection tabs, drag handle, zoom controls, and close button
        Flow headerRow = Flow.row()
            .fullWidth()
            .height(18)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .marginBottom(3);

        // 6 Mode Selection Tabs
        final int[] MODES = { MTENuclearReactor.GUI_MODE_COMPONENTS, MTENuclearReactor.GUI_MODE_TEMPERATURE,
            MTENuclearReactor.GUI_MODE_HEAT_OUTPUT, MTENuclearReactor.GUI_MODE_NEUTRON_FLUX,
            MTENuclearReactor.GUI_MODE_NEUTRON_ABSORPTION, MTENuclearReactor.GUI_MODE_CONTROL_RODS };
        final String[] TAB_NAMES = { StatCollector.translateToLocal("gt.gui.modularnuclear.tab.components"),
            StatCollector.translateToLocal("gt.gui.modularnuclear.tab.temperature"),
            StatCollector.translateToLocal("gt.gui.modularnuclear.tab.heat_output"),
            StatCollector.translateToLocal("gt.gui.modularnuclear.tab.neutron_flux"),
            StatCollector.translateToLocal("gt.gui.modularnuclear.tab.neutron_absorption"),
            StatCollector.translateToLocal("gt.gui.modularnuclear.tab.control_rods") };

        for (int i = 0; i < MODES.length; i++) {
            final int mode = MODES[i];
            final String tabName = TAB_NAMES[i];
            ItemStack icon = switch (mode) {
                case MTENuclearReactor.GUI_MODE_COMPONENTS -> ItemList.RodUranium.get(1L);
                case MTENuclearReactor.GUI_MODE_TEMPERATURE -> new ItemStack(Items.fire_charge);
                case MTENuclearReactor.GUI_MODE_HEAT_OUTPUT -> (Ic2Items.hotcoolantCell != null
                    ? Ic2Items.hotcoolantCell.copy()
                    : ItemList.Cell_Empty.get(1L));
                case MTENuclearReactor.GUI_MODE_NEUTRON_FLUX -> new ItemStack(Items.nether_star);
                case MTENuclearReactor.GUI_MODE_NEUTRON_ABSORPTION -> new ItemStack(Blocks.iron_bars);
                case MTENuclearReactor.GUI_MODE_CONTROL_RODS -> ModMetaTileEntities.nuclearControlRodHatch;
                default -> new ItemStack(Blocks.stone);
            };

            ButtonWidget<?> tabBtn = new ButtonWidget<>().size(16, 16)
                .marginRight(2)
                .overlay(new ItemDrawable(icon))
                .onMousePressed(d -> {
                    reactor.mCurrentGuiMode = mode;
                    syncManager.callSyncedAction("setGuiMode", b -> b.writeInt(mode));
                    return true;
                })
                .tooltipBuilder(t -> {
                    boolean active = (reactor.mCurrentGuiMode == mode);
                    t.addLine(
                        (active ? (EnumChatFormatting.GREEN + "" + EnumChatFormatting.BOLD) : EnumChatFormatting.WHITE)
                            + tabName);
                    if (active) {
                        t.addLine(
                            EnumChatFormatting.AQUA
                                + StatCollector.translateToLocal("gt.gui.modularnuclear.tab.active"));
                    } else {
                        t.addLine(
                            EnumChatFormatting.DARK_GRAY
                                + StatCollector.translateToLocal("gt.gui.modularnuclear.tab.switch"));
                    }
                });
            headerRow.child(tabBtn);
        }

        // Draggable Handle
        headerRow.child(
            new PanelDragHandle(panel).expanded()
                .height(14));

        // Zoom Controls
        // Zoom Out
        headerRow.child(
            new ButtonWidget<>().size(14, 14)
                .marginRight(2)
                .child(
                    IKey.str("-")
                        .asWidget()
                        .center())
                .tooltipBuilder(t -> t.addLine("Zoom out"))
                .onMousePressed(d -> {
                    gridWidget.zoomOut();
                    return true;
                }));

        // Zoom Readout (also acts as drag handle)
        headerRow.child(
            new PanelDragHandle(panel).title(IKey.dynamic(gridWidget::getZoomString))
                .color(Color.WHITE.main)
                .textAlign(Alignment.Center)
                .size(36, 14)
                .marginRight(2));

        // Zoom In
        headerRow.child(
            new ButtonWidget<>().size(14, 14)
                .marginRight(2)
                .child(
                    IKey.str("+")
                        .asWidget()
                        .center())
                .tooltipBuilder(t -> t.addLine("Zoom in"))
                .onMousePressed(d -> {
                    gridWidget.zoomIn();
                    return true;
                }));

        // Reset Zoom (1:1)
        headerRow.child(
            new ButtonWidget<>().size(22, 14)
                .marginRight(2)
                .child(
                    IKey.str("1:1")
                        .asWidget()
                        .center())
                .tooltipBuilder(t -> t.addLine("Reset zoom (1:1)"))
                .onMousePressed(d -> {
                    gridWidget.resetZoom();
                    return true;
                }));

        // Fit to View
        headerRow.child(
            new ButtonWidget<>().size(22, 14)
                .marginRight(2)
                .child(
                    IKey.str("Fit")
                        .asWidget()
                        .center())
                .tooltipBuilder(t -> t.addLine("Fit grid to view"))
                .onMousePressed(d -> {
                    gridWidget.fitToView();
                    return true;
                }));

        // Auto-fit lock toggle button
        headerRow.child(
            new ButtonWidget<>().size(14, 14)
                .marginRight(6)
                .child(new LockIconWidget(gridWidget::isAutoFitLocked).center())
                .tooltipBuilder(t -> {
                    if (gridWidget.isAutoFitLocked()) {
                        t.addLine(EnumChatFormatting.YELLOW + "Auto-fit on resize: locked");
                        t.addLine(EnumChatFormatting.GRAY + "Click to unlock and auto-fit on window resize");
                    } else {
                        t.addLine(EnumChatFormatting.GREEN + "Auto-fit on resize: unlocked");
                        t.addLine(EnumChatFormatting.GRAY + "Click to lock zoom and pan on resize");
                    }
                })
                .onMousePressed(d -> {
                    gridWidget.toggleAutoFitLocked();
                    return true;
                }));

        // Close button on the same level
        headerRow.child(
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

        // Layout header row and main grid widget vertically
        panel.child(
            Flow.column()
                .full()
                .padding(4)
                .child(headerRow)
                .child(
                    gridWidget.fullWidth()
                        .expanded()));

        return panel;
    }
}
