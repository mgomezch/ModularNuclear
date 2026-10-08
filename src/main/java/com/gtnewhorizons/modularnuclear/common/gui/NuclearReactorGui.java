package com.gtnewhorizons.modularnuclear.common.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.ReactorGridSyncData;
import com.gtnewhorizons.modularui.api.GlStateManager;
import com.gtnewhorizons.modularui.api.drawable.IDrawable;
import com.gtnewhorizons.modularui.api.drawable.ItemDrawable;
import com.gtnewhorizons.modularui.api.drawable.UITexture;
import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.api.math.Color;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.math.Size;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.api.widget.IWidgetBuilder;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.Scrollable;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import codechicken.lib.gui.GuiDraw;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.util.GTOreDictUnificator;

public class NuclearReactorGui {

    public static final int REACTOR_GRID_WINDOW_ID = 20;
    private static final int TOOLTIP_DELAY = 10;

    private static final UITexture TAB_NEI_SELECTED = UITexture
        .partly(new ResourceLocation("nei", "textures/nei_tabbed_sprites.png"), 256, 256, 0, 16, 24, 40);
    private static final UITexture TAB_NEI_UNSELECTED = UITexture
        .partly(new ResourceLocation("nei", "textures/nei_tabbed_sprites.png"), 256, 256, 24, 16, 48, 40);

    public static ButtonWidget createScramButton(MTENuclearReactor reactor, IWidgetBuilder<?> builder) {
        ButtonWidget scramButton = new ButtonWidget() {

            @Override
            public void draw(float partialTicks) {
                int x = 0;
                int y = 0;
                int w = getSize().width;
                int h = getSize().height;
                // Outer dark red/black border
                GuiDraw.drawRect(x, y, w, h, 0xFF330000);
                // Background red
                boolean active = reactor.mScram;
                int bgColor = isHovering() ? (active ? 0xFFFF3333 : 0xFFDD1111) : (active ? 0xFFCC0000 : 0xFFAA0000);
                GuiDraw.drawRect(x + 1, y + 1, w - 2, h - 2, bgColor);
                // Highlight line at top
                GuiDraw.drawRect(x + 1, y + 1, w - 2, 1, 0x55FFFFFF);
                // Text: SCRAM or SCRAMMED in bold white letters
                String text = active ? (EnumChatFormatting.BOLD + "SCRAMMED") : (EnumChatFormatting.BOLD + "SCRAM");
                int strW = GuiDraw.getStringWidth(text);
                int strH = 8;
                GuiDraw.drawString(text, (w - strW) / 2, (h - strH) / 2, 0xFFFFFFFF, true);
            }
        };
        scramButton.setPos(8, 91)
            .setSize(64, 16);
        scramButton.setPlayClickSound(true);
        scramButton.dynamicTooltip(() -> {
            List<String> tt = new ArrayList<>();
            tt.add(EnumChatFormatting.RED + "" + EnumChatFormatting.BOLD + "Emergency SCRAM");
            tt.add(EnumChatFormatting.GRAY + "Immediately inserts all control rods to 100%.");
            if (reactor.mScram) {
                tt.add(EnumChatFormatting.YELLOW + "Status: active (click to reset/disengage)");
            } else {
                tt.add(EnumChatFormatting.GREEN + "Status: disengaged (normal redstone control)");
            }
            return tt;
        });
        scramButton.setUpdateTooltipEveryTick(true);
        scramButton.setOnClick((clickData, widget) -> { reactor.setScram(!reactor.mScram); });
        return scramButton;
    }

    public static ButtonWidget createReactorGridButton(MTENuclearReactor reactor) {
        ButtonWidget button = (ButtonWidget) ButtonWidget.openSyncedWindowButton(REACTOR_GRID_WINDOW_ID)
            .setPlayClickSound(true)
            .setBackground(
                () -> new IDrawable[] { GTUITextures.BUTTON_STANDARD, new ItemDrawable(ItemList.RodUranium.get(1L)) })
            .setPos(174, 91)
            .setSize(16, 16);
        button.addTooltip(StatCollector.translateToLocal("GT5U.gui.button.reactor_hatches"))
            .setTooltipShowUpDelay(TOOLTIP_DELAY);
        return button;
    }

    public static void buildMainUI(MTENuclearReactor reactor, ModularWindow.Builder builder,
        UIBuildContext buildContext) {
        builder.widget(
            new DrawableWidget().setDrawable(GTUITextures.PICTURE_SCREEN_BLACK)
                .setPos(4, 4)
                .setSize(190, 85));
        final SlotWidget inventorySlot = new SlotWidget(reactor.inventoryHandler, 1);
        builder.widget(
            inventorySlot.setPos(173, 167)
                .setBackground(GTUITextures.SLOT_DARK_GRAY));

        final DynamicPositionedColumn screenElements = new DynamicPositionedColumn();
        drawTexts(reactor, screenElements, inventorySlot);
        builder.widget(
            new Scrollable().setVerticalScroll()
                .widget(screenElements)
                .setPos(10, 7)
                .setSize(182, 79));

        builder.widget(reactor.createStructureUpdateButton(builder));
        builder.widget(createReactorGridButton(reactor));
        builder.widget(reactor.createScramButton(builder));

        buildContext.addSyncedWindow(REACTOR_GRID_WINDOW_ID, player -> createReactorGridWindow(reactor, player));

        builder.widget(
            new FakeSyncWidget<>(
                reactor::collectGridSyncData,
                reactor::applyGridSyncData,
                ReactorGridSyncData::writeToBuffer,
                ReactorGridSyncData::readFromBuffer));
    }

    public static void drawTexts(MTENuclearReactor reactor, DynamicPositionedColumn screenElements,
        SlotWidget inventorySlot) {
        screenElements.widget(
            new TextWidget()
                .setStringSupplier(
                    () -> String.format(
                        "Core Temp: %.1f / %.0f °C",
                        reactor.mCoreTemp,
                        NuclearSimulationEngine.getMaxOperatingTemperature(reactor.mPipeTier)))
                .setDefaultColor(Color.rgb(255, 200, 0))
                .setTextAlignment(Alignment.CenterLeft)
                .setEnabled(widget -> reactor.mMachine));
        screenElements.widget(
            new TextWidget()
                .setStringSupplier(() -> String.format("Avg Reactivity: %.1f%%", reactor.mReactivity * 100.0))
                .setDefaultColor(Color.rgb(100, 200, 255))
                .setTextAlignment(Alignment.CenterLeft)
                .setEnabled(widget -> reactor.mMachine));
        screenElements.widget(
            new TextWidget()
                .setStringSupplier(
                    () -> "Flux: " + NuclearSimulationEngine.formatNeutronFlux(reactor.mNeutronsProduced))
                .setDefaultColor(Color.rgb(100, 220, 255))
                .setTextAlignment(Alignment.CenterLeft)
                .setEnabled(widget -> reactor.mMachine));
        screenElements.widget(
            new TextWidget()
                .setStringSupplier(
                    () -> String.format(
                        "Neutrons: %d fast, %d therm, %d esc",
                        reactor.mFastAbsorbed,
                        reactor.mThermalAbsorbed,
                        reactor.mEscapedNeutrons))
                .setDefaultColor(Color.rgb(200, 200, 200))
                .setTextAlignment(Alignment.CenterLeft)
                .setEnabled(widget -> reactor.mMachine));
        screenElements.widget(
            new TextWidget().setStringSupplier(() -> String.format(Locale.US, "Damage: %.1f%%", reactor.mReactorDamage))
                .setDefaultColor(Color.rgb(255, 80, 80))
                .setTextAlignment(Alignment.CenterLeft)
                .setEnabled(widget -> reactor.mMachine && reactor.mReactorDamage > 0.0));
    }

    public static ModularWindow createReactorGridWindow(final MTENuclearReactor reactor, final EntityPlayer player) {
        ReactorGridSyncData sync = reactor.getClientGridData();
        int N = 7;
        if (sync != null && sync.gridSize > 0) {
            N = sync.gridSize;
        } else if (reactor.mGrid != null && reactor.mGrid.length > 0) {
            N = reactor.mGrid.length;
        }

        final int idealH = N * 18 + 52;
        final int idealW = Math.max(154, N * 18 + 20);

        int screenW = ClientScreenHelper.getScaledScreenWidth();
        int screenH = ClientScreenHelper.getScaledScreenHeight();
        int topLimit = ClientScreenHelper.getTopReservedHeight();
        int parentH = reactor.getGUIHeight();
        int mainY = (screenH - parentH) / 2;
        int playerInvY = mainY + 104;
        int bottomLimit = playerInvY - 2;
        int availH = Math.max(70, bottomLimit - topLimit);

        final int w = Math.min(idealW, screenW - 8);
        final int h = Math.min(idealH, availH);

        ModularWindow.Builder builder = ModularWindow.builder(w, h);
        builder.setBackground(GTUITextures.BACKGROUND_SINGLEBLOCK_DEFAULT);
        builder.setGuiTint(reactor.getGUIColorization());
        builder.setDraggable(true);
        builder.setPos((screenSize, mainWindow) -> {
            int scW = (screenSize != null && screenSize.width > 0) ? screenSize.width
                : ClientScreenHelper.getScaledScreenWidth();
            int scH = (screenSize != null && screenSize.height > 0) ? screenSize.height
                : ClientScreenHelper.getScaledScreenHeight();
            int top = ClientScreenHelper.getTopReservedHeight();
            int mY = (mainWindow != null) ? mainWindow.getPos().y : (scH - reactor.getGUIHeight()) / 2;
            int pInvY = mY + 104;
            int bLimit = pInvY - 2;
            int avail = Math.max(70, bLimit - top);

            int targetH = Math.min(idealH, avail);
            int targetW = Math.min(idealW, scW - 8);

            int px = Math.max(2, (scW - targetW) / 2);
            int py = Math.max(top, top + (avail - targetH) / 2);
            return new Pos2d(px, py);
        });

        NuclearReactorGridWidget gridWidget = new NuclearReactorGridWidget(reactor);
        Scrollable scrollable = new Scrollable().setVerticalScroll()
            .setHorizontalScroll();
        scrollable.widget(gridWidget);
        scrollable.setPos(10, 26)
            .setSizeProvider(
                (size, window, parent) -> new Size(
                    Math.max(60, window.getSize().width - 20),
                    Math.max(30, window.getSize().height - 52)));
        gridWidget.setParentScrollable(scrollable);
        builder.widget(scrollable);

        final int[] MODES = { MTENuclearReactor.GUI_MODE_COMPONENTS, MTENuclearReactor.GUI_MODE_TEMPERATURE,
            MTENuclearReactor.GUI_MODE_NEUTRON_FLUX, MTENuclearReactor.GUI_MODE_NEUTRON_ABSORPTION,
            MTENuclearReactor.GUI_MODE_CONTROL_RODS };
        final String[] TAB_NAMES = { StatCollector.translateToLocal("gt.gui.modularnuclear.tab.components"),
            StatCollector.translateToLocal("gt.gui.modularnuclear.tab.temperature"),
            StatCollector.translateToLocal("gt.gui.modularnuclear.tab.neutron_flux"),
            StatCollector.translateToLocal("gt.gui.modularnuclear.tab.neutron_absorption"),
            StatCollector.translateToLocal("gt.gui.modularnuclear.tab.control_rods") };
        final String[] TAB_DESCS = { StatCollector.translateToLocal("gt.gui.modularnuclear.tab.components.desc"),
            StatCollector.translateToLocal("gt.gui.modularnuclear.tab.temperature.desc"),
            StatCollector.translateToLocal("gt.gui.modularnuclear.tab.neutron_flux.desc"),
            StatCollector.translateToLocal("gt.gui.modularnuclear.tab.neutron_absorption.desc"),
            StatCollector.translateToLocal("gt.gui.modularnuclear.tab.control_rods.desc") };

        for (int i = 0; i < MODES.length; i++) {
            final int mode = MODES[i];
            final String tabName = TAB_NAMES[i];
            final String tabDesc = TAB_DESCS[i];

            ButtonWidget tabBtn = new ButtonWidget() {

                @Override
                public void draw(float partialTicks) {
                    boolean active = (reactor.mCurrentGuiMode == mode);
                    UITexture tabTex = active ? TAB_NEI_SELECTED : TAB_NEI_UNSELECTED;

                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
                    GlStateManager.disableLighting();
                    tabTex.draw(0, 0, getSize().width, getSize().height);

                    GlStateManager.pushMatrix();
                    ItemStack icon = switch (mode) {
                        case MTENuclearReactor.GUI_MODE_COMPONENTS -> ItemList.RodUranium.get(1L);
                        case MTENuclearReactor.GUI_MODE_TEMPERATURE -> new ItemStack(Items.fire_charge);
                        case MTENuclearReactor.GUI_MODE_NEUTRON_FLUX -> new ItemStack(Items.nether_star);
                        case MTENuclearReactor.GUI_MODE_NEUTRON_ABSORPTION -> new ItemStack(Blocks.iron_bars);
                        case MTENuclearReactor.GUI_MODE_CONTROL_RODS -> {
                            ItemStack rod = GTOreDictUnificator.get(OrePrefixes.stick, Materials.Graphite, 1L);
                            if (rod == null)
                                rod = GTOreDictUnificator.get(OrePrefixes.stickLong, Materials.Graphite, 1L);
                            if (rod == null) rod = GTOreDictUnificator.get(OrePrefixes.stick, Materials.Carbon, 1L);
                            if (rod == null) rod = GTOreDictUnificator.get(OrePrefixes.stickLong, Materials.Carbon, 1L);
                            if (rod == null) rod = GTOreDictUnificator.get(OrePrefixes.stickLong, Materials.Silver, 1L);
                            yield rod != null ? rod : ModMetaTileEntities.nuclearControlRodHatch;
                        }
                        default -> new ItemStack(Blocks.stone);
                    };
                    if (icon != null) {
                        new ItemDrawable(icon).draw(4, 4, 16, 16, partialTicks);
                    }
                    GlStateManager.popMatrix();
                    GlStateManager.disableLighting();
                    GlStateManager.disableDepth();
                    GlStateManager.enableBlend();
                    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
                }
            };
            tabBtn.setPos(6 + i * 25, 2)
                .setSize(24, 24);
            tabBtn.setUpdateTooltipEveryTick(true);
            tabBtn.dynamicTooltip(() -> {
                List<String> tt = new ArrayList<>();
                boolean active = (reactor.mCurrentGuiMode == mode);
                tt.add(
                    (active ? (EnumChatFormatting.GREEN + "" + EnumChatFormatting.BOLD) : EnumChatFormatting.WHITE)
                        + tabName);
                tt.add(EnumChatFormatting.GRAY + tabDesc);
                if (active) {
                    tt.add(
                        EnumChatFormatting.AQUA + StatCollector.translateToLocal("gt.gui.modularnuclear.tab.active"));
                } else {
                    tt.add(
                        EnumChatFormatting.DARK_GRAY
                            + StatCollector.translateToLocal("gt.gui.modularnuclear.tab.switch"));
                }
                return tt;
            });
            tabBtn.setOnClick((clickData, widget) -> { reactor.mCurrentGuiMode = mode; });
            builder.widget(tabBtn);
        }

        builder.widget(
            ButtonWidget.closeWindowButton(true)
                .setPosProvider((size, window, parent) -> new Pos2d(window.getSize().width - 18, 2))
                .setSize(16, 16));

        builder.widget(new com.gtnewhorizons.modularui.api.widget.Widget() {

            private final ItemDrawable drawable = new ItemDrawable(ModMetaTileEntities.reactor.copy());

            @Override
            public void draw(float partialTicks) {
                GlStateManager.pushMatrix();
                drawable.draw(0, 0, 12, 12, partialTicks);
                GlStateManager.popMatrix();
                GlStateManager.disableLighting();
                GlStateManager.disableDepth();
                GlStateManager.enableBlend();
                GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            }
        }.setPosProvider(
            (size, window, parent) -> new Pos2d((window.getSize().width - 12) / 2, window.getSize().height - 25))
            .setSize(12, 12)
            .addTooltip("Reactor controller (front face)"));

        ButtonWidget zoomOutBtn = new ButtonWidget() {

            @Override
            public void draw(float partialTicks) {
                super.draw(partialTicks);
                String str = "-";
                int sw = GuiDraw.getStringWidth(str);
                GuiDraw.drawString(str, (getSize().width - sw) / 2, 1, 0xFFFFFF, false);
            }
        };
        zoomOutBtn
            .setPosProvider(
                (size, window, parent) -> new Pos2d((window.getSize().width - 119) / 2, window.getSize().height - 12))
            .setSize(12, 10);
        zoomOutBtn.setBackground(GTUITextures.BUTTON_STANDARD);
        zoomOutBtn.addTooltip("Zoom out");
        zoomOutBtn.setOnClick((clickData, widget) -> gridWidget.zoomOut());
        builder.widget(zoomOutBtn);

        ZoomSliderWidget zoomSlider = new ZoomSliderWidget(gridWidget);
        zoomSlider.setPosProvider(
            (size, window, parent) -> new Pos2d((window.getSize().width - 119) / 2 + 15, window.getSize().height - 12));
        builder.widget(zoomSlider);

        ButtonWidget zoomInBtn = new ButtonWidget() {

            @Override
            public void draw(float partialTicks) {
                super.draw(partialTicks);
                String str = "+";
                int sw = GuiDraw.getStringWidth(str);
                GuiDraw.drawString(str, (getSize().width - sw) / 2, 1, 0xFFFFFF, false);
            }
        };
        zoomInBtn.setPosProvider(
            (size, window, parent) -> new Pos2d((window.getSize().width - 119) / 2 + 88, window.getSize().height - 12))
            .setSize(12, 10);
        zoomInBtn.setBackground(GTUITextures.BUTTON_STANDARD);
        zoomInBtn.addTooltip("Zoom in");
        zoomInBtn.setOnClick((clickData, widget) -> gridWidget.zoomIn());
        builder.widget(zoomInBtn);

        ButtonWidget zoomResetBtn = new ButtonWidget() {

            @Override
            public void draw(float partialTicks) {
                super.draw(partialTicks);
                String str = "1:1";
                int color = (gridWidget.getCurrentCellSize() == 18) ? 0x88FF88 : 0xFFFFFF;
                GlStateManager.pushMatrix();
                float scale = 0.75f;
                int strW = GuiDraw.getStringWidth(str);
                float textW = strW * scale;
                float textH = 8 * scale;
                float posX = (getSize().width - textW) / 2.0f;
                float posY = (getSize().height - textH) / 2.0f;
                GlStateManager.translate(posX, posY, 0);
                GlStateManager.scale(scale, scale, 1.0f);
                GuiDraw.drawString(str, 0, 0, color, false);
                GlStateManager.popMatrix();
            }
        };
        zoomResetBtn.setPosProvider(
            (size, window, parent) -> new Pos2d((window.getSize().width - 119) / 2 + 103, window.getSize().height - 12))
            .setSize(16, 10);
        zoomResetBtn.setBackground(GTUITextures.BUTTON_STANDARD);
        zoomResetBtn.dynamicTooltip(() -> {
            List<String> tt = new ArrayList<>();
            tt.add("Reset zoom (1:1)");
            tt.add(EnumChatFormatting.GRAY + "Current zoom: " + gridWidget.getZoomPercent() + "%");
            return tt;
        });
        zoomResetBtn.setUpdateTooltipEveryTick(true);
        zoomResetBtn.setOnClick((clickData, widget) -> gridWidget.resetZoom());
        builder.widget(zoomResetBtn);

        builder.widget(new WindowResizeWidget(gridWidget));

        return builder.build();
    }
}
