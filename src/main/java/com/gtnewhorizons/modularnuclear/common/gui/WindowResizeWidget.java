package com.gtnewhorizons.modularnuclear.common.gui;

import java.awt.Rectangle;
import java.lang.reflect.Field;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.modularui.api.GlStateManager;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.math.Size;
import com.gtnewhorizons.modularui.api.screen.Cursor;
import com.gtnewhorizons.modularui.api.screen.ModularUIContext;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.widget.IDraggable;
import com.gtnewhorizons.modularui.api.widget.Interactable;
import com.gtnewhorizons.modularui.api.widget.Widget;

import codechicken.lib.gui.GuiDraw;

public class WindowResizeWidget extends Widget implements IDraggable, Interactable {

    public static final int BORDER = 5;
    public static final int CORNER = 12;
    public static final int MIN_WIDTH = 140;
    public static final int MIN_HEIGHT = 70;

    private static Field sizeField;

    static {
        try {
            sizeField = ModularWindow.class.getDeclaredField("size");
            sizeField.setAccessible(true);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public enum Handle {
        NONE,
        N,
        S,
        W,
        E,
        NW,
        NE,
        SW,
        SE
    }

    private final NuclearReactorGridWidget gridWidget;
    private boolean isMoving = false;
    private Handle activeHandle = Handle.NONE;
    private int startMouseX, startMouseY;
    private int startWinX, startWinY;
    private int startWinW, startWinH;

    public WindowResizeWidget(NuclearReactorGridWidget gridWidget) {
        this.gridWidget = gridWidget;
        setPos(0, 0);
        setSizeProvider((size, window, parent) -> window.getSize());
    }

    @Override
    public void onInit() {
        super.onInit();
        ModularWindow win = getWindow();
        if (win == null) return;
        ModularUIContext ctx = win.getContext();
        if (ctx == null || !ctx.isClient()) return;

        Size screenSize = ctx.getScaledScreenSize();
        int screenW = (screenSize != null && screenSize.width > 0) ? screenSize.width
            : ClientScreenHelper.getScaledScreenWidth();
        int screenH = (screenSize != null && screenSize.height > 0) ? screenSize.height
            : ClientScreenHelper.getScaledScreenHeight();
        int topLimit = ClientScreenHelper.getTopReservedHeight();

        ModularWindow mainWin = ctx.getMainWindow();
        int mainY = (mainWin != null) ? mainWin.getPos().y : (screenH - 192) / 2;
        int playerInvY = mainY + 104;
        int bottomLimit = playerInvY - 2;
        int availH = Math.max(70, bottomLimit - topLimit);

        int curW = win.getSize().width;
        int curH = win.getSize().height;

        int newW = Math.min(curW, screenW - 8);
        int newH = Math.min(curH, availH);

        int newX = Math.max(2, (screenW - newW) / 2);
        int newY = Math.max(topLimit, topLimit + (availH - newH) / 2);

        setWindowBounds(win, newX, newY, newW, newH);
    }

    public Handle getHandleAt(int rx, int ry, int w, int h) {
        // Exclude top-right close/mode button area (w-38..w-2, 0..18)
        if (ry < 18 && rx >= w - 38 && rx < w - 2) {
            return Handle.NONE;
        }

        boolean left = rx < BORDER;
        boolean right = rx >= w - BORDER;
        boolean top = ry < BORDER;
        boolean bottom = ry >= h - BORDER;

        boolean cLeft = rx < CORNER;
        boolean cRight = rx >= w - CORNER;
        boolean cTop = ry < CORNER;
        boolean cBottom = ry >= h - CORNER;

        if (cTop && cLeft) return Handle.NW;
        if (cTop && cRight) return Handle.NE;
        if (cBottom && cLeft) return Handle.SW;
        if (cBottom && cRight) return Handle.SE;

        if (top) return Handle.N;
        if (bottom) return Handle.S;
        if (left) return Handle.W;
        if (right) return Handle.E;

        return Handle.NONE;
    }

    @Override
    public boolean isUnderMouse(Pos2d mousePos) {
        if (mousePos == null) return false;
        Pos2d abs = getAbsolutePos();
        Size sz = getSize();
        int rx = mousePos.x - abs.x;
        int ry = mousePos.y - abs.y;
        if (rx < 0 || rx >= sz.width || ry < 0 || ry >= sz.height) {
            return false;
        }
        return getHandleAt(rx, ry, sz.width, sz.height) != Handle.NONE;
    }

    @Override
    public boolean onDragStart(int button) {
        if (button != 0) return false;
        ModularUIContext ctx = getContext();
        if (ctx == null) return false;
        Cursor cursor = ctx.getCursor();
        if (cursor == null) return false;

        Pos2d abs = getAbsolutePos();
        Size sz = getSize();
        int rx = cursor.getX() - abs.x;
        int ry = cursor.getY() - abs.y;

        Handle handle = getHandleAt(rx, ry, sz.width, sz.height);
        if (handle == Handle.NONE) {
            return false;
        }

        this.activeHandle = handle;
        this.startMouseX = cursor.getX();
        this.startMouseY = cursor.getY();

        ModularWindow win = getWindow();
        if (win != null) {
            this.startWinX = win.getPos().x;
            this.startWinY = win.getPos().y;
            this.startWinW = win.getSize().width;
            this.startWinH = win.getSize().height;
        }

        this.isMoving = true;
        return true;
    }

    @Override
    public void onDragEnd(boolean cancel) {
        this.isMoving = false;
        this.activeHandle = Handle.NONE;
    }

    @Override
    public void onDrag(int button, long timeSinceLastClick) {
        updateResize();
    }

    @Override
    public void onMouseDragged(int button, long timeSinceLastClick) {
        updateResize();
    }

    @Override
    public boolean onClickReleased(int button) {
        if (button == 0 && isMoving) {
            this.isMoving = false;
            this.activeHandle = Handle.NONE;
            return true;
        }
        return false;
    }

    @Override
    public boolean isMoving() {
        return isMoving;
    }

    @Override
    public void setMoving(boolean moving) {
        this.isMoving = moving;
        if (!moving) {
            this.activeHandle = Handle.NONE;
        }
    }

    @Override
    public Rectangle getArea() {
        return new Rectangle(getAbsolutePos().x, getAbsolutePos().y, getSize().width, getSize().height);
    }

    @Override
    public void renderMovingState(float partialTicks) {
        updateResize();
    }

    private void updateResize() {
        if (!isMoving || activeHandle == Handle.NONE) return;
        ModularUIContext ctx = getContext();
        if (ctx == null) return;
        Cursor cursor = ctx.getCursor();
        if (cursor == null) return;
        ModularWindow win = getWindow();
        if (win == null) return;

        int curMouseX = cursor.getX();
        int curMouseY = cursor.getY();
        int dx = curMouseX - startMouseX;
        int dy = curMouseY - startMouseY;

        Size screenSz = ctx.getScaledScreenSize();
        int screenW = (screenSz != null) ? screenSz.width : 480;
        int screenH = (screenSz != null) ? screenSz.height : 270;

        int newX = startWinX;
        int newY = startWinY;
        int newW = startWinW;
        int newH = startWinH;

        switch (activeHandle) {
            case E:
            case NE:
            case SE: {
                int maxW = screenW - startWinX - 2;
                newW = Math.max(MIN_WIDTH, Math.min(startWinW + dx, maxW));
                break;
            }
            case W:
            case NW:
            case SW: {
                int targetW = startWinW - dx;
                int maxW = startWinX + startWinW - 2;
                newW = Math.max(MIN_WIDTH, Math.min(targetW, maxW));
                newX = startWinX + (startWinW - newW);
                break;
            }
            default:
                break;
        }

        switch (activeHandle) {
            case S:
            case SW:
            case SE: {
                int maxH = screenH - startWinY - 2;
                newH = Math.max(MIN_HEIGHT, Math.min(startWinH + dy, maxH));
                break;
            }
            case N:
            case NW:
            case NE: {
                int topLimit = ClientScreenHelper.getTopReservedHeight();
                int targetH = startWinH - dy;
                int maxH = startWinY + startWinH - topLimit;
                newH = Math.max(MIN_HEIGHT, Math.min(targetH, maxH));
                newY = startWinY + (startWinH - newH);
                break;
            }
            default:
                break;
        }

        if (newW != win.getSize().width || newH != win.getSize().height
            || newX != win.getPos().x
            || newY != win.getPos().y) {
            setWindowBounds(win, newX, newY, newW, newH);
            if (gridWidget != null) {
                gridWidget.updateWidgetSize();
            }
        }
    }

    public static void setWindowBounds(ModularWindow win, int x, int y, int w, int h) {
        if (win == null) return;
        try {
            if (sizeField != null) {
                sizeField.set(win, new Size(w, h));
            }
        } catch (Throwable ignored) {}
        win.setPos(new Pos2d(x, y));
        if (win.getContext() != null) {
            win.getContext()
                .storeWindowPos(win, new Pos2d(x, y));
        }
        win.markNeedsRebuild();
    }

    @Override
    public void draw(float partialTicks) {
        int w = getSize().width;
        int h = getSize().height;

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        // Draw classic resize gripper in the bottom-right corner
        int dotColor1 = 0x99555555;
        int dotColor2 = 0xCCAAAAAA;

        // 1st diagonal (outer)
        GuiDraw.drawRect(w - 3, h - 3, 2, 2, dotColor2);
        GuiDraw.drawRect(w - 4, h - 4, 1, 1, dotColor1);

        // 2nd diagonal
        GuiDraw.drawRect(w - 6, h - 3, 2, 2, dotColor2);
        GuiDraw.drawRect(w - 7, h - 4, 1, 1, dotColor1);
        GuiDraw.drawRect(w - 3, h - 6, 2, 2, dotColor2);
        GuiDraw.drawRect(w - 4, h - 7, 1, 1, dotColor1);

        // 3rd diagonal
        GuiDraw.drawRect(w - 9, h - 3, 2, 2, dotColor2);
        GuiDraw.drawRect(w - 10, h - 4, 1, 1, dotColor1);
        GuiDraw.drawRect(w - 6, h - 6, 2, 2, dotColor2);
        GuiDraw.drawRect(w - 7, h - 7, 1, 1, dotColor1);
        GuiDraw.drawRect(w - 3, h - 9, 2, 2, dotColor2);
        GuiDraw.drawRect(w - 4, h - 10, 1, 1, dotColor1);

        // Hover or Active edge/corner highlight
        Handle currentHandle = isMoving ? activeHandle : Handle.NONE;
        if (!isMoving && getContext() != null && getContext().getCursor() != null) {
            Cursor cursor = getContext().getCursor();
            Pos2d abs = getAbsolutePos();
            int rx = cursor.getX() - abs.x;
            int ry = cursor.getY() - abs.y;
            currentHandle = getHandleAt(rx, ry, w, h);
        }

        if (currentHandle != Handle.NONE) {
            int hlColor = isMoving ? 0xAA00DDFF : 0x7055AAFF;
            switch (currentHandle) {
                case N:
                    GuiDraw.drawRect(0, 0, w, 2, hlColor);
                    break;
                case S:
                    GuiDraw.drawRect(0, h - 2, w, 2, hlColor);
                    break;
                case W:
                    GuiDraw.drawRect(0, 0, 2, h, hlColor);
                    break;
                case E:
                    GuiDraw.drawRect(w - 2, 0, 2, h, hlColor);
                    break;
                case NW:
                    GuiDraw.drawRect(0, 0, CORNER, 2, hlColor);
                    GuiDraw.drawRect(0, 0, 2, CORNER, hlColor);
                    break;
                case NE:
                    GuiDraw.drawRect(w - CORNER, 0, CORNER, 2, hlColor);
                    GuiDraw.drawRect(w - 2, 0, 2, CORNER, hlColor);
                    break;
                case SW:
                    GuiDraw.drawRect(0, h - 2, CORNER, 2, hlColor);
                    GuiDraw.drawRect(0, h - CORNER, 2, CORNER, hlColor);
                    break;
                case SE:
                    GuiDraw.drawRect(w - CORNER, h - 2, CORNER, 2, hlColor);
                    GuiDraw.drawRect(w - 2, h - CORNER, 2, CORNER, hlColor);
                    break;
                default:
                    break;
            }
        }

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
