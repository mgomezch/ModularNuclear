package com.gtnewhorizons.modularnuclear.common.gui;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.modularui.api.GlStateManager;
import com.gtnewhorizons.modularui.api.screen.ModularUIContext;
import com.gtnewhorizons.modularui.api.widget.IDraggable;
import com.gtnewhorizons.modularui.api.widget.Interactable;
import com.gtnewhorizons.modularui.api.widget.Widget;

import codechicken.lib.gui.GuiDraw;

public class ZoomSliderWidget extends Widget implements IDraggable, Interactable {

    public static final int WIDTH = 70;
    public static final int HEIGHT = 10;
    public static final int THUMB_W = 6;
    public static final int THUMB_H = 8;

    private final NuclearReactorGridWidget gridWidget;
    private boolean isDragging = false;

    public ZoomSliderWidget(NuclearReactorGridWidget gridWidget) {
        this.gridWidget = gridWidget;
        setSize(WIDTH, HEIGHT);
        setTooltipShowUpDelay(0);
        setUpdateTooltipEveryTick(true);
        dynamicTooltip(() -> {
            List<String> tt = new ArrayList<>();
            tt.add("Zoom: " + EnumChatFormatting.YELLOW + gridWidget.getZoomPercent() + "%");
            return tt;
        });
    }

    private void updateFromMouse(int rx) {
        int trackInner = getSize().width - THUMB_W;
        if (trackInner <= 0) return;
        int mouseInTrack = rx - (THUMB_W / 2);
        float progress = Math.max(0.0f, Math.min(1.0f, (float) mouseInTrack / (float) trackInner));
        gridWidget.setZoomProgress(progress);
    }

    @Override
    public ClickResult onClick(int button, boolean doubleClick) {
        if (button != 0) return ClickResult.REJECT;
        ModularUIContext ctx = getContext();
        if (ctx == null || ctx.getCursor() == null) return ClickResult.REJECT;
        int rx = ctx.getCursor()
            .getX() - getAbsolutePos().x;
        isDragging = true;
        updateFromMouse(rx);
        return ClickResult.ACCEPT;
    }

    @Override
    public boolean onClickReleased(int button) {
        if (button == 0 && isDragging) {
            isDragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean onDragStart(int button) {
        if (button != 0) return false;
        ModularUIContext ctx = getContext();
        if (ctx == null || ctx.getCursor() == null) return false;
        int rx = ctx.getCursor()
            .getX() - getAbsolutePos().x;
        int ry = ctx.getCursor()
            .getY() - getAbsolutePos().y;
        if (rx >= 0 && rx < getSize().width && ry >= 0 && ry < getSize().height) {
            isDragging = true;
            updateFromMouse(rx);
            return true;
        }
        return false;
    }

    @Override
    public void onDrag(int button, long timeSinceLastClick) {
        if (isDragging) {
            ModularUIContext ctx = getContext();
            if (ctx != null && ctx.getCursor() != null) {
                updateFromMouse(
                    ctx.getCursor()
                        .getX() - getAbsolutePos().x);
            }
        }
    }

    @Override
    public void onMouseDragged(int button, long timeSinceLastClick) {
        if (isDragging) {
            ModularUIContext ctx = getContext();
            if (ctx != null && ctx.getCursor() != null) {
                updateFromMouse(
                    ctx.getCursor()
                        .getX() - getAbsolutePos().x);
            }
        }
    }

    @Override
    public void onDragEnd(boolean cancel) {
        isDragging = false;
    }

    @Override
    public boolean isMoving() {
        return isDragging;
    }

    @Override
    public void setMoving(boolean moving) {
        this.isDragging = moving;
    }

    @Override
    public Rectangle getArea() {
        return new Rectangle(getAbsolutePos().x, getAbsolutePos().y, getSize().width, getSize().height);
    }

    @Override
    public void renderMovingState(float partialTicks) {
        if (isDragging) {
            ModularUIContext ctx = getContext();
            if (ctx != null && ctx.getCursor() != null) {
                updateFromMouse(
                    ctx.getCursor()
                        .getX() - getAbsolutePos().x);
            }
        }
    }

    @Override
    public void draw(float partialTicks) {
        int w = getSize().width;
        int h = getSize().height;

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        // 1. Draw track
        int trackY = (h - 2) / 2;
        GuiDraw.drawRect(0, trackY, w, 2, 0xFF141414);
        GuiDraw.drawRect(0, trackY - 1, w, 1, 0xFF333333);
        GuiDraw.drawRect(0, trackY + 2, w, 1, 0xFF444444);

        // 2. 1:1 baseline notch
        int[] sizes = gridWidget.getZoomCellSizes(gridWidget.getGridSize());
        if (sizes.length > 1) {
            for (int i = 0; i < sizes.length; i++) {
                if (sizes[i] == 18) {
                    float tickProg = (float) i / (float) (sizes.length - 1);
                    int tickX = (int) (tickProg * (w - THUMB_W)) + (THUMB_W / 2);
                    GuiDraw.drawRect(tickX, trackY - 1, 1, 4, 0xFF88AA88);
                    break;
                }
            }
        }

        // 3. Draw thumb
        float prog = gridWidget.getZoomProgress();
        int thumbX = (int) (prog * (w - THUMB_W));
        int thumbY = 1;
        boolean hover = isHovering() || isDragging;
        int border = hover ? 0xFF00DDFF : 0xFF222222;
        int fill = hover ? 0xFFCCCCCC : 0xFFAAAAAA;
        GuiDraw.drawRect(thumbX, thumbY, THUMB_W, THUMB_H, border);
        GuiDraw.drawRect(thumbX + 1, thumbY + 1, THUMB_W - 2, THUMB_H - 2, fill);

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
