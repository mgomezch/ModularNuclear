package com.gtnewhorizons.modularnuclear.common.gui;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.gtnewhorizons.modularnuclear.client.renderer.GrayscaleShader;
import com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ControlRodData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantFluidData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantItemData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.RadiovoltaicData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ReflectorData;
import com.gtnewhorizons.modularnuclear.common.config.data.FuelData;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.NuclearGridTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.INuclearTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearColorMaps;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.ReactorGridSyncData;

import gregtech.api.util.GTUtility;

/**
 * ModularUI 2 Interactive Canvas Widget for the MPTR Reactor Core Hatches grid.
 * Supports smooth pan, zoom, cell placement, drag-placement, batch shift-insert, and hover telemetry tooltips.
 */
public class NuclearReactorGridWidget extends Widget<NuclearReactorGridWidget> implements Interactable {

    public static final float MIN_ZOOM = 0.001f;
    public static final float MAX_ZOOM = 1000.0f;

    private final MTENuclearReactor reactor;
    private final PanelSyncManager syncManager;

    private float panX = 0.0f;
    private float panY = 0.0f;
    private float zoom = 1.0f;
    private boolean hasInitialFit = false;
    private boolean autoFitLocked = false;
    private int lastWidth = -1;
    private int lastHeight = -1;

    // Pan dragging state
    private boolean isPanning = false;
    private int dragStartMouseX = 0;
    private int dragStartMouseY = 0;
    private float dragStartPanX = 0.0f;
    private float dragStartPanY = 0.0f;

    // Cell placement dragging state
    private boolean mIsDraggingGrid = false;
    private int mDragButton = 0;
    private final Set<Integer> mDragVisitedCells = new LinkedHashSet<>();

    public NuclearReactorGridWidget(MTENuclearReactor reactor) {
        this(reactor, null);
    }

    public NuclearReactorGridWidget(MTENuclearReactor reactor, PanelSyncManager syncManager) {
        this.reactor = reactor;
        this.syncManager = syncManager;
        tooltipPos(RichTooltip.Pos.NEXT_TO_MOUSE);
        tooltipDynamic(this::buildTooltip);
        tooltipAutoUpdate(true);
        tooltipShowUpTimer(0);
    }

    public int getGridSize() {
        ReactorGridSyncData sync = reactor.getClientGridData();
        return (sync != null && sync.gridSize > 0) ? sync.gridSize : 0;
    }

    public float getZoom() {
        return zoom;
    }

    public int getZoomPercent() {
        return Math.round(zoom * 100.0f);
    }

    public String getZoomString() {
        float pct = zoom * 100.0f;
        if (pct >= 10.0f) {
            return Math.round(pct) + "%";
        } else if (pct >= 1.0f) {
            return String.format(Locale.US, "%.1f%%", pct);
        } else {
            return String.format(Locale.US, "%.2f%%", pct);
        }
    }

    public boolean isAutoFitLocked() {
        return autoFitLocked;
    }

    public void setAutoFitLocked(boolean autoFitLocked) {
        this.autoFitLocked = autoFitLocked;
    }

    public void toggleAutoFitLocked() {
        this.autoFitLocked = !this.autoFitLocked;
        if (!this.autoFitLocked) {
            fitToView();
        }
    }

    public void zoomIn() {
        float cx = getArea() != null ? getArea().w() / 2.0f : 0.0f;
        float cy = getArea() != null ? getArea().h() / 2.0f : 0.0f;
        zoomAt(Math.min(MAX_ZOOM, zoom * 1.25f), cx, cy);
    }

    public void zoomOut() {
        float cx = getArea() != null ? getArea().w() / 2.0f : 0.0f;
        float cy = getArea() != null ? getArea().h() / 2.0f : 0.0f;
        zoomAt(Math.max(MIN_ZOOM, zoom / 1.25f), cx, cy);
    }

    public void resetZoom() {
        this.zoom = 1.0f;
        centerGrid();
    }

    public void fitToView() {
        int N = getGridSize();
        if (N <= 0 || getArea() == null) return;
        int vw = getArea().w();
        int vh = getArea().h();
        if (vw <= 0 || vh <= 0) return;

        float contentW = N * 18.0f;
        float contentH = N * 18.0f + 26.0f; // Grid + controller icon spacing
        float scaleX = (vw - 24.0f) / contentW;
        float scaleY = (vh - 24.0f) / contentH;
        this.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Math.min(scaleX, scaleY)));
        this.panX = (vw - contentW * zoom) / 2.0f;
        this.panY = (vh - contentH * zoom) / 2.0f;
    }

    private void centerGrid() {
        int N = getGridSize();
        if (N <= 0 || getArea() == null) return;
        int vw = getArea().w();
        int vh = getArea().h();
        float contentW = N * 18.0f;
        float contentH = N * 18.0f + 26.0f;
        this.panX = (vw - contentW * zoom) / 2.0f;
        this.panY = (vh - contentH * zoom) / 2.0f;
    }

    private void zoomAt(float newZoom, float mouseX, float mouseY) {
        newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoom));
        if (Math.abs(newZoom - zoom) < 0.00001f) return;
        float graphX = (mouseX - panX) / zoom;
        float graphY = (mouseY - panY) / zoom;
        this.zoom = newZoom;
        this.panX = mouseX - graphX * zoom;
        this.panY = mouseY - graphY * zoom;
    }

    public int[] getCellAtAbs(int absX, int absY) {
        Area area = getArea();
        if (area == null) return null;
        return getCellAt(absX - area.x, absY - area.y);
    }

    public boolean isOverControllerAbs(int absX, int absY) {
        Area area = getArea();
        if (area == null) return false;
        return isOverController(absX - area.x, absY - area.y);
    }

    public boolean isOverController(int mx, int my) {
        int N = getGridSize();
        if (N <= 0) return false;
        double gmx = (mx - panX) / zoom;
        double gmy = (my - panY) / zoom;
        int cx = (N * 18 - 18) / 2;
        int cy = N * 18 + 4;
        return gmx >= cx && gmx < cx + 18 && gmy >= cy && gmy < cy + 18;
    }

    public int[] getCellAt(int mx, int my) {
        ReactorGridSyncData sync = reactor.getClientGridData();
        if (sync == null || sync.gridSize <= 0) return null;
        int N = sync.gridSize;

        double gmx = (mx - panX) / zoom;
        double gmy = (my - panY) / zoom;

        int hx = (int) Math.floor(gmx / 18.0);
        int renderHy = (int) Math.floor(gmy / 18.0);
        int hy = (N - 1) - renderHy;

        if (hx < 0 || hx >= N || hy < 0 || hy >= N) return null;
        if (NuclearSimulationEngine.isCornerNullCell(hx, hy, N, N)) return null;

        int idx = hx * N + hy;
        if (idx < 0 || idx >= sync.cells.size()) return null;
        ReactorGridSyncData.ReactorGridCellData cellData = sync.cells.get(idx);
        if (cellData == null || !cellData.exists) return null;

        return new int[] { hx, hy };
    }

    @Override
    public Interactable.Result onMousePressed(int button) {
        ModularGuiContext ctx = getContext();
        if (ctx == null) return Interactable.Result.IGNORE;
        Area area = getArea();
        if (area == null || !area.isInside(ctx.getAbsMouseX(), ctx.getAbsMouseY())) {
            return Interactable.Result.IGNORE;
        }

        int[] cell = getCellAtAbs(ctx.getAbsMouseX(), ctx.getAbsMouseY());

        if (cell != null && (button == 0 || button == 1)) {
            boolean isShiftDown = GuiScreen.isShiftKeyDown();
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            ItemStack cursorStack = (player != null && player.inventory != null) ? player.inventory.getItemStack()
                : null;

            if (isShiftDown && cursorStack != null && cursorStack.stackSize > 0) {
                // Batch shift-insert
                if (syncManager != null) {
                    final int btn = button;
                    syncManager.callSyncedAction("batchShiftInsert", buf -> buf.writeInt(btn));
                }
                Interactable.playButtonClickSound();
                return Interactable.Result.SUCCESS;
            }

            if (cursorStack != null && cursorStack.stackSize > 0) {
                mIsDraggingGrid = true;
                mDragButton = button;
                mDragVisitedCells.clear();
                mDragVisitedCells.add(cell[0] * 1000 + cell[1]);
            } else {
                mIsDraggingGrid = false;
                mDragVisitedCells.clear();
            }

            final int hx = cell[0];
            final int hy = cell[1];
            final int btn = button;
            if (syncManager != null) {
                syncManager.callSyncedAction("slotClick", buf -> {
                    buf.writeInt(hx);
                    buf.writeInt(hy);
                    buf.writeInt(btn);
                    buf.writeBoolean(isShiftDown);
                });
            }
            Interactable.playButtonClickSound();
            return Interactable.Result.SUCCESS;
        }

        // Click on canvas background or middle-click -> start pan
        if (button == 2 || cell == null) {
            isPanning = true;
            dragStartMouseX = ctx.getAbsMouseX();
            dragStartMouseY = ctx.getAbsMouseY();
            dragStartPanX = panX;
            dragStartPanY = panY;
            return Interactable.Result.SUCCESS;
        }

        return Interactable.Result.IGNORE;
    }

    @Override
    public boolean onMouseRelease(int button) {
        if (isPanning) {
            isPanning = false;
            return true;
        }
        if (mIsDraggingGrid && button == mDragButton) {
            mIsDraggingGrid = false;
            mDragVisitedCells.clear();
            return true;
        }
        return false;
    }

    @Override
    public void onMouseDrag(int button, long time) {
        ModularGuiContext ctx = getContext();
        if (ctx == null) return;

        if (isPanning) {
            int amx = ctx.getAbsMouseX();
            int amy = ctx.getAbsMouseY();
            panX = dragStartPanX + (amx - dragStartMouseX);
            panY = dragStartPanY + (amy - dragStartMouseY);
            return;
        }

        if (mIsDraggingGrid && button == mDragButton) {
            int[] cell = getCellAtAbs(ctx.getAbsMouseX(), ctx.getAbsMouseY());
            if (cell != null) {
                int code = cell[0] * 1000 + cell[1];
                if (!mDragVisitedCells.contains(code)) {
                    mDragVisitedCells.add(code);
                    final int hx = cell[0];
                    final int hy = cell[1];
                    final int btn = button;
                    if (syncManager != null) {
                        syncManager.callSyncedAction("dragStep", buf -> {
                            buf.writeInt(hx);
                            buf.writeInt(hy);
                            buf.writeInt(btn);
                        });
                    }
                    Interactable.playButtonClickSound();
                }
            }
        }
    }

    @Override
    public boolean onMouseScroll(UpOrDown scroll, int delta) {
        ModularGuiContext ctx = getContext();
        Area area = getArea();
        float mx = (ctx != null && area != null) ? (ctx.getAbsMouseX() - area.x)
            : (area != null ? area.w() / 2.0f : 0.0f);
        float my = (ctx != null && area != null) ? (ctx.getAbsMouseY() - area.y)
            : (area != null ? area.h() / 2.0f : 0.0f);
        if (scroll == UpOrDown.UP) {
            zoomAt(Math.min(MAX_ZOOM, zoom * 1.15f), mx, my);
            return true;
        } else if (scroll == UpOrDown.DOWN) {
            zoomAt(Math.max(MIN_ZOOM, zoom / 1.15f), mx, my);
            return true;
        }
        return false;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        int rw = getArea().w();
        int rh = getArea().h();
        if (rw <= 0 || rh <= 0) return;

        ReactorGridSyncData sync = reactor.getClientGridData();

        // Auto-fit on first layout once data is formed, or on resize if not locked
        if (sync != null && sync.gridSize > 0) {
            if (!hasInitialFit) {
                fitToView();
                hasInitialFit = true;
                lastWidth = rw;
                lastHeight = rh;
            } else if (lastWidth != rw || lastHeight != rh) {
                if (!autoFitLocked) {
                    fitToView();
                }
                lastWidth = rw;
                lastHeight = rh;
            }
        } else {
            lastWidth = rw;
            lastHeight = rh;
        }

        // Draw background container panel and border in local coords
        GuiDraw.drawRect(0, 0, rw, rh, 0xEE12161E);
        GuiDraw.drawRect(0, 0, rw, 1, 0xFF2A3645);
        GuiDraw.drawRect(0, rh - 1, rw, 1, 0xFF2A3645);
        GuiDraw.drawRect(0, 0, 1, rh, 0xFF2A3645);
        GuiDraw.drawRect(rw - 1, 0, 1, rh, 0xFF2A3645);

        if (sync == null || sync.gridSize <= 0) {
            String msg = "Reactor Core Offline / Unformed";
            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
            int sw = fr.getStringWidth(msg);
            fr.drawStringWithShadow(msg, (rw - sw) / 2, (rh - 8) / 2, 0x888888);
            return;
        }

        int N = sync.gridSize;

        // Scissor clipping to widget viewport
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int scale = sr.getScaleFactor();

        int absX = (context != null) ? context.transformX(0, 0) : getArea().x;
        int absY = (context != null) ? context.transformY(0, 0) : getArea().y;
        int absEndX = (context != null) ? context.transformX(rw, rh) : (absX + rw);
        int absEndY = (context != null) ? context.transformY(rw, rh) : (absY + rh);
        int sx = absX * scale;
        int sy = mc.displayHeight - absEndY * scale;
        int sw = Math.max(0, (absEndX - absX) * scale);
        int sh = Math.max(0, (absEndY - absY) * scale);

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(sx, sy, sw, sh);

        GL11.glPushMatrix();
        GL11.glTranslatef(panX, panY, 0.0f);
        GL11.glScalef(zoom, zoom, 1.0f);

        double maxTemp = NuclearSimulationEngine.getMaxOperatingTemperature(sync.pipeTier);

        // Render grid cells
        for (int gx = 0; gx < N; gx++) {
            for (int gy = 0; gy < N; gy++) {
                int renderGy = (N - 1) - gy;
                int px = gx * 18;
                int py = renderGy * 18;

                if (NuclearSimulationEngine.isCornerNullCell(gx, gy, N, N)) {
                    continue;
                }

                int idx = gx * N + gy;
                ReactorGridSyncData.ReactorGridCellData cell = (idx < sync.cells.size()) ? sync.cells.get(idx) : null;

                // Slot background
                GuiDraw.drawRect(px, py, 18, 18, 0xFF373737);
                GuiDraw.drawRect(px + 1, py + 1, 16, 16, 0xFF1E1E1E);

                if (cell != null && cell.exists) {
                    boolean isHeatmap = reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_TEMPERATURE
                        || reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_HEAT_OUTPUT
                        || reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_NEUTRON_FLUX
                        || reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_NEUTRON_ABSORPTION;

                    // Contents (item, fluid, or empty hatch tint)
                    if (cell.itemStack != null) {
                        boolean shaderActive = isHeatmap && GrayscaleShader.begin();
                        try {
                            GuiDraw.drawItem(cell.itemStack, px + 1, py + 1, 16, 16, 0);
                        } finally {
                            if (shaderActive) {
                                GrayscaleShader.end();
                            }
                        }

                        // Stack size readout
                        if (cell.itemStack.stackSize > 1) {
                            String countStr = String.valueOf(cell.itemStack.stackSize);
                            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
                            fr.drawStringWithShadow(countStr, px + 17 - fr.getStringWidth(countStr), py + 9, 0xFFFFFF);
                        }

                        // Durability bar
                        if (cell.itemStack.isItemDamaged()) {
                            int maxD = cell.itemStack.getMaxDamage();
                            int curD = cell.itemStack.getItemDamage();
                            double health = (double) (maxD - curD) / (double) maxD;
                            int barW = (int) Math.round(13.0 * health);
                            int barColor = (int) Math.round(255.0 * health);
                            int color = isHeatmap ? ((barColor << 16) | (barColor << 8) | barColor)
                                : (((255 - barColor) << 16) | (barColor << 8));
                            GuiDraw.drawRect(px + 2, py + 14, 13, 2, 0xFF000000);
                            GuiDraw.drawRect(px + 2, py + 14, barW, 1, 0xFF000000 | color);
                        }
                    } else if (cell.fluidStack != null) {
                        boolean shaderActive = isHeatmap && GrayscaleShader.begin();
                        try {
                            GuiDraw.drawFluidTexture(cell.fluidStack, px + 1, py + 1, 16, 16, 0);
                        } finally {
                            if (shaderActive) {
                                GrayscaleShader.end();
                            }
                        }
                    } else if (!isHeatmap) {
                        if (cell.isHighPressure) {
                            GuiDraw.drawRect(px + 1, py + 1, 16, 16, 0x40FFA500);
                        } else if (cell.isFluid) {
                            GuiDraw.drawRect(px + 1, py + 1, 16, 16, 0x300055AA);
                        }
                    }

                    // Mode Overlays
                    if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_TEMPERATURE) {
                        int color = NuclearColorMaps.getTemperatureColor(cell.temperature, maxTemp);
                        GuiDraw.drawRect(px + 1, py + 1, 16, 16, color);
                    } else if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_HEAT_OUTPUT) {
                        if (cell.heatOutput > 0) {
                            int color = NuclearColorMaps.getHeatOutputColor(cell.heatOutput);
                            GuiDraw.drawRect(px + 1, py + 1, 16, 16, color);
                        }
                    } else if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_NEUTRON_FLUX) {
                        int color = NuclearColorMaps.getNeutronColor(cell.fastFlux + cell.thermalFlux);
                        GuiDraw.drawRect(px + 1, py + 1, 16, 16, color);
                    } else if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_NEUTRON_ABSORPTION) {
                        int color = NuclearColorMaps.getNeutronColor(5.0 * (cell.fastAbsorbed + cell.thermalAbsorbed));
                        GuiDraw.drawRect(px + 1, py + 1, 16, 16, color);
                    } else if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_CONTROL_RODS) {
                        if (cell.hasControlRod) {
                            // Dark backdrop tint
                            GuiDraw.drawRect(px + 1, py + 1, 16, 16, 0x881E293B);

                            // Control rod fill bar
                            int rodColor = switch (cell.controlRodType) {
                                case 1 -> 0xFFC0C0C0; // Silver
                                case 2 -> 0xFF5A5A6E; // Boron
                                case 3 -> 0xFF5588AA; // Cadmium
                                case 4 -> 0xFF3366BB; // Indium
                                case 5 -> 0xFFAA3333; // Hafnium
                                default -> 0x88666666; // None
                            };

                            int barH = Math.max(1, Math.round(12.0f * (cell.controlRodInsertion / 100.0f)));
                            GuiDraw.drawRect(px + 3, py + 15 - barH, 12, barH, rodColor);

                            // Control rod hatch border
                            GuiDraw.drawRect(px + 1, py + 1, 16, 1, 0xFF00AAFF);
                            GuiDraw.drawRect(px + 1, py + 16, 16, 1, 0xFF00AAFF);
                            GuiDraw.drawRect(px + 1, py + 1, 1, 16, 0xFF00AAFF);
                            GuiDraw.drawRect(px + 16, py + 1, 1, 16, 0xFF00AAFF);
                        } else {
                            GuiDraw.drawRect(px + 1, py + 1, 16, 16, 0x77000000);
                        }
                    }

                    // Flashing thermal hazard warning rect (>85% max operating temp)
                    if (cell.temperature > maxTemp * 0.85) {
                        if ((System.currentTimeMillis() / 400) % 2 == 0) {
                            GuiDraw.drawRect(px + 1, py + 1, 16, 16, 0x60FF0000);
                        }
                    }
                }
            }
        }

        // Draw drag-placement preview cells
        if (mIsDraggingGrid && !mDragVisitedCells.isEmpty()) {
            for (int code : mDragVisitedCells) {
                int dx = code / 1000;
                int dy = code % 1000;
                int renderDy = (N - 1) - dy;
                int dpx = dx * 18;
                int dpy = renderDy * 18;
                GuiDraw.drawRect(dpx + 1, dpy + 1, 16, 16, 0x6000FF00);
            }
        }

        // Controller Icon slot at center bottom (representing physical multiblock front face)
        int cx = (N * 18 - 18) / 2;
        int cy = N * 18 + 4;
        GuiDraw.drawRect(cx, cy, 18, 18, 0xFF4A5568);
        GuiDraw.drawRect(cx + 1, cy + 1, 16, 16, 0xFF1A202C);
        if (ModMetaTileEntities.reactor != null) {
            GuiDraw.drawItem(ModMetaTileEntities.reactor.copy(), cx + 1, cy + 1, 16, 16, 0);
        }

        int mx = (context != null) ? context.getMouseX() : 0;
        int my = (context != null) ? context.getMouseY() : 0;
        boolean mouseInside = (mx >= 0 && mx < rw && my >= 0 && my < rh);

        if (mouseInside && isOverController(mx, my)) {
            GuiDraw.drawRect(cx, cy, 18, 1, 0xFFFFFFFF);
            GuiDraw.drawRect(cx, cy + 17, 18, 1, 0xFFFFFFFF);
            GuiDraw.drawRect(cx, cy + 1, 1, 16, 0xFFFFFFFF);
            GuiDraw.drawRect(cx + 17, cy + 1, 1, 16, 0xFFFFFFFF);
            GuiDraw.drawRect(cx + 1, cy + 1, 16, 16, 0x30FFFFFF);
        }

        // Hover highlight with crisp 1px border and subtle tint
        int[] hCell = mouseInside ? getCellAt(mx, my) : null;
        if (hCell != null) {
            int hx = hCell[0];
            int hy = hCell[1];
            int renderHy = (N - 1) - hy;
            int hpx = hx * 18;
            int hpy = renderHy * 18;
            GuiDraw.drawRect(hpx, hpy, 18, 1, 0xFFFFFFFF);
            GuiDraw.drawRect(hpx, hpy + 17, 18, 1, 0xFFFFFFFF);
            GuiDraw.drawRect(hpx, hpy + 1, 1, 16, 0xFFFFFFFF);
            GuiDraw.drawRect(hpx + 17, hpy + 1, 1, 16, 0xFFFFFFFF);
            GuiDraw.drawRect(hpx + 1, hpy + 1, 16, 16, 0x30FFFFFF);
        }

        GL11.glPopMatrix();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // Reset state
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void buildTooltip(RichTooltip tooltip) {
        ModularGuiContext ctx = getContext();
        if (ctx == null) return;
        Area area = getArea();
        if (area == null) return;
        int absX = ctx.getAbsMouseX();
        int absY = ctx.getAbsMouseY();
        if (!area.isInside(absX, absY)) return;

        int mx = absX - area.x;
        int my = absY - area.y;

        if (isOverController(mx, my)) {
            tooltip.addLine(EnumChatFormatting.GOLD + "" + EnumChatFormatting.BOLD + "Reactor Controller");
            tooltip.addLine(EnumChatFormatting.GRAY + "Front center reference position");
            tooltip.addLine(EnumChatFormatting.DARK_GRAY + "Multiblock structure front face");
            return;
        }

        int[] hCell = getCellAt(mx, my);
        if (hCell == null) return;

        ReactorGridSyncData sync = reactor.getClientGridData();
        if (sync == null || sync.gridSize <= 0) return;

        int hx = hCell[0];
        int hy = hCell[1];
        int N = sync.gridSize;
        int idx = hx * N + hy;
        if (idx < 0 || idx >= sync.cells.size()) return;
        ReactorGridSyncData.ReactorGridCellData cell = sync.cells.get(idx);
        if (cell == null || !cell.exists) {
            tooltip.addLine(EnumChatFormatting.GRAY + "Empty core position (" + hx + ", " + hy + ")");
            return;
        }

        // 1. Content in Hatch (Item, Fluid, or Empty Hatch)
        if (cell.itemStack != null) {
            try {
                tooltip.addFromItem(cell.itemStack);
            } catch (Exception e) {
                tooltip.addLine(EnumChatFormatting.WHITE + cell.itemStack.getDisplayName());
            }
            if (cell.itemStack.stackSize > 1) {
                tooltip.addLine(
                    EnumChatFormatting.GRAY + "Stack size: " + EnumChatFormatting.WHITE + cell.itemStack.stackSize);
            }

            // Component Physics Registry Lookups
            ModeratorData mod = ReactorDataRegistry.findModerator(cell.itemStack);
            if (mod != null) {
                tooltip.addLine(
                    EnumChatFormatting.DARK_AQUA + "Moderator Component: " + EnumChatFormatting.WHITE + mod.name);
                tooltip.addLine(
                    EnumChatFormatting.GRAY + String.format(
                        Locale.US,
                        "  Moderation efficiency: %s%.1f%%",
                        EnumChatFormatting.GREEN,
                        mod.moderation * 100.0));
                tooltip.addLine(
                    EnumChatFormatting.GRAY + String.format(
                        Locale.US,
                        "  Scattering: Fast %s%.1f%%%s | Thermal %s%.1f%%",
                        EnumChatFormatting.BLUE,
                        mod.scatteringFast * 100.0,
                        EnumChatFormatting.GRAY,
                        EnumChatFormatting.DARK_AQUA,
                        mod.scatteringThermal * 100.0));
                tooltip.addLine(
                    EnumChatFormatting.GRAY + String.format(
                        Locale.US,
                        "  Absorption: Fast %s%.2f%%%s | Thermal %s%.2f%%",
                        EnumChatFormatting.RED,
                        mod.absorptionFast * 100.0,
                        EnumChatFormatting.GRAY,
                        EnumChatFormatting.DARK_RED,
                        mod.absorptionThermal * 100.0));
                tooltip.addLine(
                    EnumChatFormatting.GRAY + String.format(
                        Locale.US,
                        "  Heat transfer: %s%.2f%s | Max safe temp: %s%.0f °C",
                        EnumChatFormatting.AQUA,
                        mod.heatTransferCoeff,
                        EnumChatFormatting.GRAY,
                        EnumChatFormatting.GOLD,
                        mod.maxTemperature));
            }

            ReflectorData ref = ReactorDataRegistry.findReflector(cell.itemStack);
            if (ref != null) {
                tooltip.addLine(
                    EnumChatFormatting.DARK_AQUA + "Reflector Component: " + EnumChatFormatting.WHITE + ref.name);
                tooltip.addLine(
                    EnumChatFormatting.GRAY + String.format(
                        Locale.US,
                        "  Reflection: Fast %s%.1f%%%s | Thermal %s%.1f%%",
                        EnumChatFormatting.BLUE,
                        ref.scatteringFast * 100.0,
                        EnumChatFormatting.GRAY,
                        EnumChatFormatting.DARK_AQUA,
                        ref.scatteringThermal * 100.0));
                tooltip.addLine(
                    EnumChatFormatting.GRAY + String.format(
                        Locale.US,
                        "  Moderation factor: %s%.1f%%",
                        EnumChatFormatting.GREEN,
                        ref.moderation * 100.0));
                tooltip.addLine(
                    EnumChatFormatting.GRAY + String.format(
                        Locale.US,
                        "  Absorption: Fast %s%.2f%%%s | Thermal %s%.2f%%",
                        EnumChatFormatting.RED,
                        ref.absorptionFast * 100.0,
                        EnumChatFormatting.GRAY,
                        EnumChatFormatting.DARK_RED,
                        ref.absorptionThermal * 100.0));
                tooltip.addLine(
                    EnumChatFormatting.GRAY + String
                        .format(Locale.US, "  Heat transfer: %s%.2f", EnumChatFormatting.AQUA, ref.heatTransferCoeff));
            }

            FuelData fuel = ReactorDataRegistry.findFuel(cell.itemStack);
            if (fuel != null) {
                tooltip.addLine(EnumChatFormatting.GOLD + "Nuclear Fuel: " + EnumChatFormatting.WHITE + fuel.name);
                tooltip.addLine(
                    EnumChatFormatting.GRAY + String.format(
                        Locale.US,
                        "  Archetype: %s%s%s | Base neutrons: %s%d",
                        EnumChatFormatting.YELLOW,
                        fuel.archetype,
                        EnumChatFormatting.GRAY,
                        EnumChatFormatting.AQUA,
                        fuel.baseNeutrons));
                tooltip.addLine(
                    EnumChatFormatting.GRAY + String.format(
                        Locale.US,
                        "  Fission: Thermal %s%.2fx%s | Fast %s%.2fx",
                        EnumChatFormatting.GREEN,
                        fuel.thermalFissionMultiplier,
                        EnumChatFormatting.GRAY,
                        EnumChatFormatting.BLUE,
                        fuel.fastFissionMultiplier));
                tooltip.addLine(
                    EnumChatFormatting.GRAY + String.format(
                        Locale.US,
                        "  Moderation factor: %s%.1f%%",
                        EnumChatFormatting.GREEN,
                        fuel.moderation * 100.0));
                tooltip.addLine(
                    EnumChatFormatting.GRAY + String.format(
                        Locale.US,
                        "  Optimal temp: %s%.0f °C%s (floor: %s%.0f °C%s)",
                        EnumChatFormatting.GOLD,
                        fuel.peakReactivityTemp,
                        EnumChatFormatting.GRAY,
                        EnumChatFormatting.RED,
                        fuel.floorTemp,
                        EnumChatFormatting.GRAY));
            }

            ControlRodData rod = ReactorDataRegistry.findControlRod(cell.itemStack);
            if (rod != null) {
                tooltip
                    .addLine(EnumChatFormatting.GOLD + "Control Rod Material: " + EnumChatFormatting.WHITE + rod.name);
                tooltip.addLine(
                    EnumChatFormatting.GRAY + String.format(
                        Locale.US,
                        "  Absorption: Thermal %s%.0f%%%s | Fast %s%.0f%%",
                        EnumChatFormatting.DARK_RED,
                        rod.maxThermalAbsorption * 100.0,
                        EnumChatFormatting.GRAY,
                        EnumChatFormatting.RED,
                        rod.maxFastAbsorption * 100.0));
            }

            RadiovoltaicData rv = ReactorDataRegistry.findRadiovoltaic(cell.itemStack);
            if (rv != null) {
                tooltip
                    .addLine(EnumChatFormatting.AQUA + "Radiovoltaic Element: " + EnumChatFormatting.WHITE + rv.name);
                tooltip.addLine(
                    EnumChatFormatting.GRAY + String.format(
                        Locale.US,
                        "  Conversion efficiency: %s%.1f%%%s | Heat transfer: %s%.2f",
                        EnumChatFormatting.GREEN,
                        rv.euConversionEfficiency * 100.0,
                        EnumChatFormatting.GRAY,
                        EnumChatFormatting.AQUA,
                        rv.heatTransferCoeff));
                tooltip.addLine(
                    EnumChatFormatting.GRAY + String.format(
                        Locale.US,
                        "  Absorption: Fast %s%.0f%%%s | Thermal %s%.0f%%",
                        EnumChatFormatting.BLUE,
                        rv.absorptionFast * 100.0,
                        EnumChatFormatting.GRAY,
                        EnumChatFormatting.DARK_AQUA,
                        rv.absorptionThermal * 100.0));
            }

            CoolantItemData cl = ReactorDataRegistry.findCoolantItem(cell.itemStack);
            if (cl != null) {
                tooltip.addLine(EnumChatFormatting.BLUE + "Coolant Component: " + EnumChatFormatting.WHITE + cl.name);
                tooltip.addLine(
                    EnumChatFormatting.GRAY + String
                        .format(Locale.US, "  Heat capacity: %s%,d Hu", EnumChatFormatting.GOLD, cl.heatCapacity));
            }
        } else if (cell.fluidStack != null) {
            try {
                tooltip.addFromFluid(cell.fluidStack);
                tooltip.addAdditionalInfoFromFluid(cell.fluidStack);
            } catch (Exception e) {
                tooltip.addLine(EnumChatFormatting.AQUA + cell.fluidStack.getLocalizedName());
            }
            tooltip.addLine(
                EnumChatFormatting.GRAY
                    + String.format(Locale.US, "Amount: %s%,d L", EnumChatFormatting.WHITE, cell.fluidStack.amount));

            FuelData fFuel = ReactorDataRegistry.findFuel(cell.fluidStack);
            if (fFuel != null) {
                tooltip
                    .addLine(EnumChatFormatting.GOLD + "Nuclear Liquid Fuel: " + EnumChatFormatting.WHITE + fFuel.name);
                tooltip.addLine(
                    EnumChatFormatting.GRAY + String.format(
                        Locale.US,
                        "  Archetype: %s%s%s | Base neutrons: %s%d",
                        EnumChatFormatting.YELLOW,
                        fFuel.archetype,
                        EnumChatFormatting.GRAY,
                        EnumChatFormatting.AQUA,
                        fFuel.baseNeutrons));
                tooltip.addLine(
                    EnumChatFormatting.GRAY + String.format(
                        Locale.US,
                        "  Fission: Thermal %s%.2fx%s | Fast %s%.2fx",
                        EnumChatFormatting.GREEN,
                        fFuel.thermalFissionMultiplier,
                        EnumChatFormatting.GRAY,
                        EnumChatFormatting.BLUE,
                        fFuel.fastFissionMultiplier));
                tooltip.addLine(
                    EnumChatFormatting.GRAY + String.format(
                        Locale.US,
                        "  Moderation factor: %s%.1f%%",
                        EnumChatFormatting.GREEN,
                        fFuel.moderation * 100.0));
            }

            CoolantFluidData cFluid = ReactorDataRegistry.findCoolantFluid(cell.fluidStack);
            if (cFluid != null) {
                tooltip.addLine(
                    EnumChatFormatting.AQUA + "Reactor Coolant Fluid: " + EnumChatFormatting.WHITE + cFluid.name);
                tooltip.addLine(
                    EnumChatFormatting.GRAY + String.format(
                        Locale.US,
                        "  Heat capacity: %s%.2f Hu/L/°C%s | Moderation: %s%.1f%%",
                        EnumChatFormatting.GOLD,
                        cFluid.heatCapacity,
                        EnumChatFormatting.GRAY,
                        EnumChatFormatting.GREEN,
                        cFluid.moderation * 100.0));
                tooltip.addLine(
                    EnumChatFormatting.DARK_GRAY
                        + String.format(Locale.US, "  Boiling point: %.0f °C", cFluid.boilingTemperature));
            }
        } else if (cell.isHighPressure) {
            tooltip.addLine(EnumChatFormatting.GOLD + "" + EnumChatFormatting.BOLD + "Nuclear High-Pressure Hatch");
            tooltip.addLine(EnumChatFormatting.GRAY + "Hermetic fluid passage for coolant loop");
            tooltip.addLine(EnumChatFormatting.DARK_GRAY + "Status: Empty (no fluid present)");
        } else if (cell.isFluid) {
            tooltip.addLine(EnumChatFormatting.AQUA + "" + EnumChatFormatting.BOLD + "Nuclear Core Fluid Hatch");
            tooltip.addLine(EnumChatFormatting.GRAY + "Fluid container for liquid fuels or coolants");
            tooltip.addLine(EnumChatFormatting.DARK_GRAY + "Status: Empty (no fluid present)");
        } else {
            tooltip.addLine(EnumChatFormatting.WHITE + "" + EnumChatFormatting.BOLD + "Nuclear Component Bus");
            tooltip.addLine(EnumChatFormatting.GRAY + "Hatch for fuel rods, moderators, and reflectors");
            tooltip.addLine(EnumChatFormatting.DARK_GRAY + "Status: Empty (no component installed)");
        }

        // 2. Telemetry Section Header
        tooltip.addLine(EnumChatFormatting.DARK_GRAY + "--------------------------------");
        tooltip.addLine(
            EnumChatFormatting.YELLOW + ""
                + EnumChatFormatting.BOLD
                + "Cell Telemetry "
                + EnumChatFormatting.DARK_GRAY
                + "("
                + hx
                + ", "
                + hy
                + ")");

        // 3. Thermal Telemetry
        double maxTemp = NuclearSimulationEngine.getMaxOperatingTemperature(sync.pipeTier);
        String tempColor;
        if (cell.temperature > maxTemp) {
            tempColor = EnumChatFormatting.DARK_RED.toString();
        } else if (cell.temperature > maxTemp * 0.85) {
            tempColor = EnumChatFormatting.RED.toString();
        } else if (cell.temperature > maxTemp * 0.5) {
            tempColor = EnumChatFormatting.YELLOW.toString();
        } else {
            tempColor = EnumChatFormatting.GREEN.toString();
        }

        tooltip.addLine(
            EnumChatFormatting.GRAY + "Temperature: "
                + tempColor
                + String.format(Locale.US, "%.1f °C", cell.temperature)
                + EnumChatFormatting.DARK_GRAY
                + String.format(Locale.US, " (safe limit: %.0f °C)", maxTemp));

        if (cell.temperature > maxTemp) {
            tooltip.addLine(
                EnumChatFormatting.RED + "" + EnumChatFormatting.BOLD + "Thermal status: Melting point exceeded!");
        } else if (cell.temperature > maxTemp * 0.85) {
            tooltip.addLine(EnumChatFormatting.RED + "Thermal status: Approaching thermal limit");
        } else if (cell.temperature > maxTemp * 0.5) {
            tooltip.addLine(EnumChatFormatting.YELLOW + "Thermal status: Operating (elevated temp)");
        } else {
            tooltip.addLine(EnumChatFormatting.GREEN + "Thermal status: Nominal operation");
        }

        // 4. Neutron Telemetry
        int tpc = sync.ticksPerCycle > 0 ? sync.ticksPerCycle : NuclearSimulationEngine.params().ticksPerCycle;
        double cyclesPerSec = 20.0 / (double) tpc;
        int totalFlux = cell.fastFlux + cell.thermalFlux;
        if (totalFlux > 0) {
            tooltip.addLine(
                EnumChatFormatting.GRAY + "Neutron flux: "
                    + EnumChatFormatting.AQUA
                    + NuclearSimulationEngine.formatNeutronFlux(totalFlux, tpc)
                    + EnumChatFormatting.DARK_GRAY
                    + String.format(
                        Locale.US,
                        " (%,d fast/s, %,d thermal/s)",
                        Math.round(cell.fastFlux * cyclesPerSec),
                        Math.round(cell.thermalFlux * cyclesPerSec)));
        } else {
            tooltip.addLine(EnumChatFormatting.DARK_GRAY + "Neutron flux: 0 n/s");
        }

        int totalAbs = cell.fastAbsorbed + cell.thermalAbsorbed;
        if (totalAbs > 0) {
            long totalAbsSec = Math.round(totalAbs * cyclesPerSec);
            tooltip.addLine(
                EnumChatFormatting.GRAY + "Neutrons absorbed: "
                    + EnumChatFormatting.LIGHT_PURPLE
                    + String.format(Locale.US, "%,d /s (%,d /cycle)", totalAbsSec, totalAbs)
                    + EnumChatFormatting.DARK_GRAY
                    + String.format(
                        Locale.US,
                        " (%,d fast/s, %,d thermal/s)",
                        Math.round(cell.fastAbsorbed * cyclesPerSec),
                        Math.round(cell.thermalAbsorbed * cyclesPerSec)));
        }

        // 5. Radiovoltaic Power
        if (cell.directEU > 0) {
            tooltip.addLine(
                EnumChatFormatting.AQUA + "Radiovoltaic power: "
                    + EnumChatFormatting.GREEN
                    + "+"
                    + String.format(Locale.US, "%,d", cell.directEU)
                    + " EU/t");
        }

        // 5.5 Heat Output
        if (cell.heatOutput > 0) {
            double heatSec = cell.heatOutput * cyclesPerSec;
            tooltip.addLine(
                EnumChatFormatting.GRAY + "Heat output: "
                    + EnumChatFormatting.GOLD
                    + String.format(Locale.US, "%,.1f Hu/s (%,.1f Hu/cycle)", heatSec, (double) cell.heatOutput));
        }

        // 6. Control Rod Info
        if (cell.hasControlRod) {
            MTEHatchNuclearControlRod.ControlRodType rodType = (cell.controlRodType >= 0
                && cell.controlRodType < MTEHatchNuclearControlRod.ControlRodType.values().length)
                    ? MTEHatchNuclearControlRod.ControlRodType.values()[cell.controlRodType]
                    : MTEHatchNuclearControlRod.ControlRodType.NONE;
            if (cell.isAdvancedControlRod) {
                tooltip.addLine(EnumChatFormatting.LIGHT_PURPLE + "Advanced control rod:");
                tooltip.addLine(
                    EnumChatFormatting.GRAY + "  Rod: "
                        + EnumChatFormatting.WHITE
                        + rodType.displayName
                        + EnumChatFormatting.YELLOW
                        + " ("
                        + cell.controlRodInsertion
                        + "%)");
                tooltip.addLine(
                    EnumChatFormatting.GRAY + "  Mod: "
                        + EnumChatFormatting.AQUA
                        + (cell.moderatorName == null || cell.moderatorName.isEmpty() ? "None" : cell.moderatorName)
                        + EnumChatFormatting.YELLOW
                        + " ("
                        + cell.moderatorInsertion
                        + "%)");
            } else {
                tooltip.addLine(
                    EnumChatFormatting.GOLD + "Control rod: "
                        + EnumChatFormatting.WHITE
                        + rodType.displayName
                        + EnumChatFormatting.YELLOW
                        + " ("
                        + cell.controlRodInsertion
                        + "%)");
            }
            tooltip.addLine(
                EnumChatFormatting.DARK_GRAY + String.format(
                    Locale.US,
                    "Max absorption: %.0f%% therm, %.0f%% fast",
                    rodType.maxThermalAbsorption * 100.0,
                    rodType.maxFastAbsorption * 100.0));
            if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_CONTROL_RODS) {
                if (sync.scram) {
                    tooltip.addLine(EnumChatFormatting.RED + "State: SCRAMMED (100% insertion)");
                } else {
                    tooltip.addLine(EnumChatFormatting.GRAY + "Controlled via hatch redstone signal");
                }
                if (cell.isAdvancedControlRod) {
                    tooltip.addLine(EnumChatFormatting.DARK_GRAY + "Click with rod/moderator item to insert/extract");
                } else {
                    tooltip.addLine(EnumChatFormatting.DARK_GRAY + "Click with rod item to insert/extract");
                }
            }
        } else if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_CONTROL_RODS) {
            tooltip.addLine(EnumChatFormatting.DARK_GRAY + "No control rod hatch installed");
        }

        // 7. Mode Context Telemetry
        if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_TEMPERATURE) {
            float delta = cell.temperature - sync.avgTemp;
            String deltaSign = delta >= 0 ? "+" : "";
            tooltip.addLine(
                EnumChatFormatting.DARK_GRAY
                    + String.format(Locale.US, "Core temp delta: %s%.1f °C", deltaSign, delta));
        } else if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_HEAT_OUTPUT) {
            double totalHeat = 0;
            if (sync.cells != null) {
                for (ReactorGridSyncData.ReactorGridCellData c : sync.cells) {
                    if (c != null && c.exists) {
                        totalHeat += c.heatOutput;
                    }
                }
            }
            if (totalHeat > 0 && cell.heatOutput > 0) {
                double share = (cell.heatOutput / totalHeat) * 100.0;
                tooltip.addLine(
                    EnumChatFormatting.DARK_GRAY + String.format(Locale.US, "Core heat output share: %.1f%%", share));
            }
        } else if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_NEUTRON_FLUX) {
            if (sync.neutronsProduced > 0 && totalFlux > 0) {
                double share = ((double) totalFlux / sync.neutronsProduced) * 100.0;
                tooltip
                    .addLine(EnumChatFormatting.DARK_GRAY + String.format(Locale.US, "Core flux share: %.1f%%", share));
            }
        } else if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_NEUTRON_ABSORPTION) {
            int totalCoreAbs = sync.fastAbsorbed + sync.thermalAbsorbed;
            if (totalCoreAbs > 0 && totalAbs > 0) {
                double share = ((double) totalAbs / totalCoreAbs) * 100.0;
                tooltip.addLine(
                    EnumChatFormatting.DARK_GRAY + String.format(Locale.US, "Core absorption share: %.1f%%", share));
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Server-Side Handlers
    // ---------------------------------------------------------------------------------------------------------

    public static void handleSlotClickServer(MTENuclearReactor reactor, EntityPlayerMP playerMP, int gx, int gy,
        int button, boolean isShift) {
        if (reactor.mGrid == null || gx < 0 || gx >= reactor.gridSize || gy < 0 || gy >= reactor.gridSize) return;
        INuclearTile nTile = reactor.mGrid[gx][gy];
        if (!(nTile instanceof NuclearGridTile gridTile)) return;

        ItemStack cursorStack = playerMP.inventory.getItemStack();

        if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_CONTROL_RODS && gridTile.hasControlRod()) {
            MTEHatchNuclearControlRod rod = gridTile.getBottomControlRod();
            ItemStack slotStack = rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD];
            boolean hasModSlot = rod.mInventory.length > MTEHatchNuclearControlRod.SLOT_MODERATOR;
            ItemStack modStack = hasModSlot ? rod.mInventory[MTEHatchNuclearControlRod.SLOT_MODERATOR] : null;

            if (isShift) {
                if (cursorStack == null) {
                    if (slotStack != null) {
                        if (playerMP.inventory.addItemStackToInventory(slotStack)) {
                            rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = null;
                        } else if (slotStack.stackSize <= 0) {
                            rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = null;
                        }
                        rod.markTileDirty();
                        syncPlayerAndReactor(reactor, playerMP);
                    } else if (modStack != null) {
                        if (playerMP.inventory.addItemStackToInventory(modStack)) {
                            rod.mInventory[MTEHatchNuclearControlRod.SLOT_MODERATOR] = null;
                        } else if (modStack.stackSize <= 0) {
                            rod.mInventory[MTEHatchNuclearControlRod.SLOT_MODERATOR] = null;
                        }
                        rod.markTileDirty();
                        syncPlayerAndReactor(reactor, playerMP);
                    }
                }
            } else if (button == 0) {
                if (cursorStack == null) {
                    if (slotStack != null) {
                        playerMP.inventory.setItemStack(slotStack);
                        rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = null;
                        rod.markTileDirty();
                        syncPlayerAndReactor(reactor, playerMP);
                    } else if (modStack != null) {
                        playerMP.inventory.setItemStack(modStack);
                        rod.mInventory[MTEHatchNuclearControlRod.SLOT_MODERATOR] = null;
                        rod.markTileDirty();
                        syncPlayerAndReactor(reactor, playerMP);
                    }
                } else if (MTEHatchNuclearControlRod.isControlRod(cursorStack)) {
                    if (slotStack == null) {
                        ItemStack placed = cursorStack.copy();
                        placed.stackSize = 1;
                        rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = placed;
                        rod.markTileDirty();
                        cursorStack.stackSize--;
                        if (cursorStack.stackSize <= 0) {
                            playerMP.inventory.setItemStack(null);
                        }
                        syncPlayerAndReactor(reactor, playerMP);
                    } else if (cursorStack.stackSize == 1) {
                        rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = cursorStack;
                        playerMP.inventory.setItemStack(slotStack);
                        rod.markTileDirty();
                        syncPlayerAndReactor(reactor, playerMP);
                    }
                } else if (hasModSlot && MTEHatchNuclearControlRod.isModerator(cursorStack)) {
                    if (modStack == null) {
                        ItemStack placed = cursorStack.copy();
                        placed.stackSize = 1;
                        rod.mInventory[MTEHatchNuclearControlRod.SLOT_MODERATOR] = placed;
                        rod.markTileDirty();
                        cursorStack.stackSize--;
                        if (cursorStack.stackSize <= 0) {
                            playerMP.inventory.setItemStack(null);
                        }
                        syncPlayerAndReactor(reactor, playerMP);
                    } else if (cursorStack.stackSize == 1) {
                        rod.mInventory[MTEHatchNuclearControlRod.SLOT_MODERATOR] = cursorStack;
                        playerMP.inventory.setItemStack(modStack);
                        rod.markTileDirty();
                        syncPlayerAndReactor(reactor, playerMP);
                    }
                }
            } else if (button == 1) {
                if (cursorStack == null) {
                    if (slotStack != null) {
                        playerMP.inventory.setItemStack(slotStack);
                        rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = null;
                        rod.markTileDirty();
                        syncPlayerAndReactor(reactor, playerMP);
                    } else if (modStack != null) {
                        playerMP.inventory.setItemStack(modStack);
                        rod.mInventory[MTEHatchNuclearControlRod.SLOT_MODERATOR] = null;
                        rod.markTileDirty();
                        syncPlayerAndReactor(reactor, playerMP);
                    }
                } else if (slotStack == null && MTEHatchNuclearControlRod.isControlRod(cursorStack)) {
                    ItemStack placed = cursorStack.copy();
                    placed.stackSize = 1;
                    rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = placed;
                    rod.markTileDirty();
                    cursorStack.stackSize--;
                    if (cursorStack.stackSize <= 0) {
                        playerMP.inventory.setItemStack(null);
                    }
                    syncPlayerAndReactor(reactor, playerMP);
                } else if (hasModSlot && modStack == null && MTEHatchNuclearControlRod.isModerator(cursorStack)) {
                    ItemStack placed = cursorStack.copy();
                    placed.stackSize = 1;
                    rod.mInventory[MTEHatchNuclearControlRod.SLOT_MODERATOR] = placed;
                    rod.markTileDirty();
                    cursorStack.stackSize--;
                    if (cursorStack.stackSize <= 0) {
                        playerMP.inventory.setItemStack(null);
                    }
                    syncPlayerAndReactor(reactor, playerMP);
                }
            }
            return;
        }

        if (gridTile.isBus()) {
            MTEHatchNuclearBus bus = gridTile.getBus();
            ItemStack slotStack = bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT);

            if (isShift) {
                if (cursorStack == null && slotStack != null) {
                    if (playerMP.inventory.addItemStackToInventory(slotStack)) {
                        bus.setInventorySlotContents(MTEHatchNuclearBus.SLOT_INPUT, null);
                    } else if (slotStack.stackSize <= 0) {
                        bus.setInventorySlotContents(MTEHatchNuclearBus.SLOT_INPUT, null);
                    }
                    bus.markTileDirty();
                    syncPlayerAndReactor(reactor, playerMP);
                }
            } else if (button == 0) {
                if (cursorStack == null) {
                    if (slotStack != null) {
                        playerMP.inventory.setItemStack(slotStack);
                        bus.setInventorySlotContents(MTEHatchNuclearBus.SLOT_INPUT, null);
                        bus.markTileDirty();
                        syncPlayerAndReactor(reactor, playerMP);
                    }
                } else if (bus.isItemAllowed(cursorStack)) {
                    if (slotStack == null) {
                        ItemStack placed = cursorStack.copy();
                        placed.stackSize = 1;
                        bus.setInventorySlotContents(MTEHatchNuclearBus.SLOT_INPUT, placed);
                        bus.markTileDirty();
                        cursorStack.stackSize--;
                        if (cursorStack.stackSize <= 0) {
                            playerMP.inventory.setItemStack(null);
                        }
                        syncPlayerAndReactor(reactor, playerMP);
                    } else if (cursorStack.stackSize == 1) {
                        bus.setInventorySlotContents(MTEHatchNuclearBus.SLOT_INPUT, cursorStack);
                        playerMP.inventory.setItemStack(slotStack);
                        bus.markTileDirty();
                        syncPlayerAndReactor(reactor, playerMP);
                    }
                }
            } else if (button == 1) {
                if (cursorStack == null) {
                    if (slotStack != null) {
                        playerMP.inventory.setItemStack(slotStack);
                        bus.setInventorySlotContents(MTEHatchNuclearBus.SLOT_INPUT, null);
                        bus.markTileDirty();
                        syncPlayerAndReactor(reactor, playerMP);
                    }
                } else if (slotStack == null && bus.isItemAllowed(cursorStack)) {
                    ItemStack placed = cursorStack.copy();
                    placed.stackSize = 1;
                    bus.setInventorySlotContents(MTEHatchNuclearBus.SLOT_INPUT, placed);
                    bus.markTileDirty();
                    cursorStack.stackSize--;
                    if (cursorStack.stackSize <= 0) {
                        playerMP.inventory.setItemStack(null);
                    }
                    syncPlayerAndReactor(reactor, playerMP);
                }
            }
        } else if (gridTile.isHatch()) {
            MTEHatchNuclearHatch hatch = gridTile.getHatch();
            if (isShift && cursorStack == null) {
                handleFluidHatchShiftClick(hatch, playerMP);
            } else if (cursorStack != null) {
                handleFluidHatchClickWithContainer(hatch, playerMP, button);
            }
            syncPlayerAndReactor(reactor, playerMP);
        }
    }

    public static void handleBatchShiftInsertServer(MTENuclearReactor reactor, EntityPlayerMP playerMP, int button) {
        ItemStack cursorStack = playerMP.inventory.getItemStack();
        if (cursorStack == null || cursorStack.stackSize <= 0) return;
        if (reactor.mGrid == null || reactor.gridSize <= 0) return;

        int N = reactor.gridSize;
        boolean insertedAny = false;

        // Traverse visual rows from top to bottom (renderGy = 0 .. N-1), left to right (gx = 0 .. N-1)
        for (int renderGy = 0; renderGy < N && cursorStack.stackSize > 0; renderGy++) {
            int gy = (N - 1) - renderGy;
            for (int gx = 0; gx < N && cursorStack.stackSize > 0; gx++) {
                if (NuclearSimulationEngine.isCornerNullCell(gx, gy, N, N)) continue;
                INuclearTile nTile = reactor.mGrid[gx][gy];
                if (!(nTile instanceof NuclearGridTile gridTile)) continue;

                if (gridTile.isBus()) {
                    MTEHatchNuclearBus bus = gridTile.getBus();
                    if (bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT) == null && bus.isItemAllowed(cursorStack)) {
                        ItemStack placed = cursorStack.copy();
                        placed.stackSize = 1;
                        bus.setInventorySlotContents(MTEHatchNuclearBus.SLOT_INPUT, placed);
                        bus.markTileDirty();
                        cursorStack.stackSize--;
                        insertedAny = true;
                    }
                } else if (gridTile.hasControlRod()) {
                    MTEHatchNuclearControlRod rod = gridTile.getBottomControlRod();
                    if (MTEHatchNuclearControlRod.isControlRod(cursorStack)) {
                        if (rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] == null) {
                            ItemStack placed = cursorStack.copy();
                            placed.stackSize = 1;
                            rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = placed;
                            rod.markTileDirty();
                            cursorStack.stackSize--;
                            insertedAny = true;
                        }
                    } else if (MTEHatchNuclearControlRod.isModerator(cursorStack)
                        && rod.mInventory.length > MTEHatchNuclearControlRod.SLOT_MODERATOR) {
                            if (rod.mInventory[MTEHatchNuclearControlRod.SLOT_MODERATOR] == null) {
                                ItemStack placed = cursorStack.copy();
                                placed.stackSize = 1;
                                rod.mInventory[MTEHatchNuclearControlRod.SLOT_MODERATOR] = placed;
                                rod.markTileDirty();
                                cursorStack.stackSize--;
                                insertedAny = true;
                            }
                        }
                }
            }
        }

        if (cursorStack.stackSize <= 0) {
            playerMP.inventory.setItemStack(null);
        }

        if (insertedAny) {
            syncPlayerAndReactor(reactor, playerMP);
        }
    }

    public static void handleDragStepServer(MTENuclearReactor reactor, EntityPlayerMP playerMP, int gx, int gy,
        int button) {
        ItemStack cursorStack = playerMP.inventory.getItemStack();
        if (cursorStack == null || cursorStack.stackSize <= 0) return;
        if (reactor.mGrid == null || gx < 0 || gx >= reactor.gridSize || gy < 0 || gy >= reactor.gridSize) return;
        INuclearTile nTile = reactor.mGrid[gx][gy];
        if (!(nTile instanceof NuclearGridTile gridTile)) return;

        if (gridTile.isBus()) {
            MTEHatchNuclearBus bus = gridTile.getBus();
            if (bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT) == null && bus.isItemAllowed(cursorStack)) {
                ItemStack placed = cursorStack.copy();
                placed.stackSize = 1;
                bus.setInventorySlotContents(MTEHatchNuclearBus.SLOT_INPUT, placed);
                bus.markTileDirty();
                cursorStack.stackSize--;
                if (cursorStack.stackSize <= 0) {
                    playerMP.inventory.setItemStack(null);
                }
                syncPlayerAndReactor(reactor, playerMP);
            }
        } else if (gridTile.hasControlRod()) {
            MTEHatchNuclearControlRod rod = gridTile.getBottomControlRod();
            if (MTEHatchNuclearControlRod.isControlRod(cursorStack)) {
                if (rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] == null) {
                    ItemStack placed = cursorStack.copy();
                    placed.stackSize = 1;
                    rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = placed;
                    rod.markTileDirty();
                    cursorStack.stackSize--;
                    if (cursorStack.stackSize <= 0) {
                        playerMP.inventory.setItemStack(null);
                    }
                    syncPlayerAndReactor(reactor, playerMP);
                }
            } else if (MTEHatchNuclearControlRod.isModerator(cursorStack)
                && rod.mInventory.length > MTEHatchNuclearControlRod.SLOT_MODERATOR) {
                    if (rod.mInventory[MTEHatchNuclearControlRod.SLOT_MODERATOR] == null) {
                        ItemStack placed = cursorStack.copy();
                        placed.stackSize = 1;
                        rod.mInventory[MTEHatchNuclearControlRod.SLOT_MODERATOR] = placed;
                        rod.markTileDirty();
                        cursorStack.stackSize--;
                        if (cursorStack.stackSize <= 0) {
                            playerMP.inventory.setItemStack(null);
                        }
                        syncPlayerAndReactor(reactor, playerMP);
                    }
                }
        }
    }

    private static void handleFluidHatchClickWithContainer(MTEHatchNuclearHatch hatch, EntityPlayerMP player,
        int button) {
        ItemStack cursorStack = player.inventory.getItemStack();
        if (cursorStack == null || cursorStack.stackSize <= 0) return;

        FluidStack fluidInItem = GTUtility.getFluidForFilledItem(cursorStack, true);
        if (fluidInItem != null && fluidInItem.amount > 0) {
            if (!hatch.isFluidInputAllowed(fluidInItem)) return;
            int space = hatch.mCapacity - (hatch.mInputFluid != null ? hatch.mInputFluid.amount : 0);
            if (space <= 0) return;
            if (hatch.mInputFluid != null && !hatch.mInputFluid.isFluidEqual(fluidInItem)) return;

            if (cursorStack.stackSize == 1) {
                if (cursorStack.getItem() instanceof IFluidContainerItem containerItem) {
                    FluidStack drained = containerItem.drain(cursorStack, space, true);
                    if (drained != null && drained.amount > 0) {
                        hatch.fill(ForgeDirection.UNKNOWN, drained, true);
                    }
                } else if (space >= fluidInItem.amount) {
                    hatch.fill(ForgeDirection.UNKNOWN, fluidInItem, true);
                    ItemStack emptyCont = GTUtility.getContainerForFilledItem(cursorStack, true);
                    player.inventory.setItemStack(emptyCont);
                }
            } else {
                ItemStack single = cursorStack.copy();
                single.stackSize = 1;
                FluidStack singleFluid = GTUtility.getFluidForFilledItem(single, true);
                if (singleFluid != null && space >= singleFluid.amount) {
                    hatch.fill(ForgeDirection.UNKNOWN, singleFluid, true);
                    cursorStack.stackSize--;
                    if (cursorStack.stackSize <= 0) {
                        player.inventory.setItemStack(null);
                    }
                    ItemStack emptyCont = GTUtility.getContainerForFilledItem(single, true);
                    if (emptyCont != null) {
                        if (!player.inventory.addItemStackToInventory(emptyCont)) {
                            player.dropPlayerItemWithRandomChoice(emptyCont, false);
                        }
                    }
                }
            }
            hatch.markTileDirty();
        } else if (hatch.mInputFluid != null && hatch.mInputFluid.amount > 0) {
            ItemStack filled = GTUtility.fillFluidContainer(hatch.mInputFluid.copy(), cursorStack, false, true);
            if (filled != null) {
                FluidStack filledFluid = GTUtility.getFluidForFilledItem(filled, true);
                int needed = (filledFluid != null) ? filledFluid.amount : 1000;
                if (hatch.mInputFluid.amount >= needed) {
                    hatch.mInputFluid.amount -= needed;
                    if (hatch.mInputFluid.amount <= 0) hatch.mInputFluid = null;
                    if (cursorStack.stackSize == 1) {
                        player.inventory.setItemStack(filled);
                    } else {
                        cursorStack.stackSize--;
                        if (cursorStack.stackSize <= 0) {
                            player.inventory.setItemStack(null);
                        }
                        if (!player.inventory.addItemStackToInventory(filled)) {
                            player.dropPlayerItemWithRandomChoice(filled, false);
                        }
                    }
                    hatch.markTileDirty();
                }
            }
        }
    }

    private static void handleFluidHatchShiftClick(MTEHatchNuclearHatch hatch, EntityPlayerMP player) {
        int space = hatch.mCapacity - (hatch.mInputFluid != null ? hatch.mInputFluid.amount : 0);
        boolean changed = false;

        if (space > 0) {
            for (int i = 0; i < player.inventory.mainInventory.length; i++) {
                ItemStack invStack = player.inventory.mainInventory[i];
                if (invStack == null) continue;
                FluidStack fluidInItem = GTUtility.getFluidForFilledItem(invStack, true);
                if (fluidInItem == null || fluidInItem.amount <= 0) continue;
                if (!hatch.isFluidInputAllowed(fluidInItem)) continue;
                if (hatch.mInputFluid != null && !hatch.mInputFluid.isFluidEqual(fluidInItem)) continue;

                if (invStack.stackSize == 1) {
                    if (invStack.getItem() instanceof IFluidContainerItem containerItem) {
                        FluidStack drained = containerItem.drain(invStack, space, true);
                        if (drained != null && drained.amount > 0) {
                            hatch.fill(ForgeDirection.UNKNOWN, drained, true);
                            changed = true;
                            space = hatch.mCapacity - (hatch.mInputFluid != null ? hatch.mInputFluid.amount : 0);
                        }
                    } else if (space >= fluidInItem.amount) {
                        hatch.fill(ForgeDirection.UNKNOWN, fluidInItem, true);
                        player.inventory.mainInventory[i] = GTUtility.getContainerForFilledItem(invStack, true);
                        changed = true;
                        space = hatch.mCapacity - (hatch.mInputFluid != null ? hatch.mInputFluid.amount : 0);
                    }
                } else {
                    ItemStack single = invStack.copy();
                    single.stackSize = 1;
                    FluidStack singleFluid = GTUtility.getFluidForFilledItem(single, true);
                    if (singleFluid != null && space >= singleFluid.amount) {
                        hatch.fill(ForgeDirection.UNKNOWN, singleFluid, true);
                        invStack.stackSize--;
                        if (invStack.stackSize <= 0) {
                            player.inventory.mainInventory[i] = null;
                        }
                        ItemStack emptyCont = GTUtility.getContainerForFilledItem(single, true);
                        if (emptyCont != null) {
                            if (!player.inventory.addItemStackToInventory(emptyCont)) {
                                player.dropPlayerItemWithRandomChoice(emptyCont, false);
                            }
                        }
                        changed = true;
                        space = hatch.mCapacity - (hatch.mInputFluid != null ? hatch.mInputFluid.amount : 0);
                    }
                }
                if (space <= 0) break;
            }
        }

        if (!changed && hatch.mInputFluid != null && hatch.mInputFluid.amount > 0) {
            for (int i = 0; i < player.inventory.mainInventory.length; i++) {
                ItemStack invStack = player.inventory.mainInventory[i];
                if (invStack == null) continue;
                ItemStack filled = GTUtility.fillFluidContainer(hatch.mInputFluid.copy(), invStack, false, true);
                if (filled != null) {
                    FluidStack filledFluid = GTUtility.getFluidForFilledItem(filled, true);
                    int needed = (filledFluid != null) ? filledFluid.amount : 1000;
                    if (hatch.mInputFluid.amount >= needed) {
                        hatch.mInputFluid.amount -= needed;
                        if (hatch.mInputFluid.amount <= 0) hatch.mInputFluid = null;
                        if (invStack.stackSize == 1) {
                            player.inventory.mainInventory[i] = filled;
                        } else {
                            invStack.stackSize--;
                            if (invStack.stackSize <= 0) player.inventory.mainInventory[i] = null;
                            if (!player.inventory.addItemStackToInventory(filled)) {
                                player.dropPlayerItemWithRandomChoice(filled, false);
                            }
                        }
                        changed = true;
                        if (hatch.mInputFluid == null) break;
                    }
                }
            }
        }

        if (changed) {
            hatch.markTileDirty();
        }
    }

    private static void syncPlayerAndReactor(MTENuclearReactor reactor, EntityPlayerMP playerMP) {
        if (playerMP.openContainer != null) {
            playerMP.openContainer.detectAndSendChanges();
            playerMP.sendContainerToPlayer(playerMP.openContainer);
        }
        if (playerMP.playerNetServerHandler != null) {
            playerMP.playerNetServerHandler.sendPacket(new S2FPacketSetSlot(-1, -1, playerMP.inventory.getItemStack()));
        }
        if (reactor.getBaseMetaTileEntity() != null) {
            reactor.getBaseMetaTileEntity()
                .markDirty();
        }
    }
}
