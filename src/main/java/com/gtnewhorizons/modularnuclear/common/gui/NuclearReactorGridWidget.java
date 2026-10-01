package com.gtnewhorizons.modularnuclear.common.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor.NuclearGridTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.INuclearTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearColorMaps;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.ReactorGridSyncData;
import com.gtnewhorizons.modularui.api.GlStateManager;
import com.gtnewhorizons.modularui.api.drawable.FluidDrawable;
import com.gtnewhorizons.modularui.api.drawable.GuiHelper;
import com.gtnewhorizons.modularui.api.drawable.ItemDrawable;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.screen.Cursor;
import com.gtnewhorizons.modularui.api.screen.ModularUIContext;
import com.gtnewhorizons.modularui.api.widget.Interactable;
import com.gtnewhorizons.modularui.common.internal.wrapper.ModularGui;
import com.gtnewhorizons.modularui.common.widget.Scrollable;
import com.gtnewhorizons.modularui.common.widget.SyncedWidget;

import codechicken.lib.gui.GuiDraw;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.util.GTUtility;

public class NuclearReactorGridWidget extends SyncedWidget implements Interactable {

    public static final int PACKET_SLOT_CLICK = 10;
    public static final int PACKET_BATCH_SHIFT_INSERT = 11;
    public static final int PACKET_DRAG_STEP = 12;
    public static final int PACKET_SYNC_GRID = 13;

    private final MTENuclearReactor reactor;
    private Scrollable parentScrollable;
    private int mZoomIndex = 0;

    private boolean mIsDraggingGrid = false;
    private int mDragButton = 0;
    private final Set<Integer> mDragVisitedCells = new LinkedHashSet<>();

    public NuclearReactorGridWidget(MTENuclearReactor reactor) {
        this.reactor = reactor;
        setSize(126, 126);
        setTooltipShowUpDelay(0);
        setUpdateTooltipEveryTick(true);
        dynamicTooltip(this::getHoveredTooltip);
        resetZoom();
    }

    public void setParentScrollable(Scrollable parentScrollable) {
        this.parentScrollable = parentScrollable;
        updateWidgetSize();
    }

    public int getViewportWidth() {
        return (parentScrollable != null && parentScrollable.getSize().width > 0) ? parentScrollable.getSize().width
            : 126;
    }

    public int getViewportHeight() {
        return (parentScrollable != null && parentScrollable.getSize().height > 0) ? parentScrollable.getSize().height
            : 126;
    }

    public int getGridSize() {
        ReactorGridSyncData sync = reactor.getClientGridData();
        return (sync != null && sync.gridSize > 0) ? sync.gridSize : 0;
    }

    public int[] getZoomCellSizes(int N) {
        if (N <= 0) return new int[] { 18 };
        int vp = Math.min(getViewportWidth(), getViewportHeight());
        int fit = Math.max(4, vp / N);
        TreeSet<Integer> sizes = new TreeSet<>();
        sizes.add(18);
        sizes.add(fit);
        if (fit < 14) sizes.add(14);
        if (fit < 18) sizes.add(18);
        sizes.add(24);
        sizes.add(32);
        sizes.add(40);
        return sizes.stream()
            .mapToInt(Integer::intValue)
            .toArray();
    }

    public int getCurrentCellSize() {
        int N = getGridSize();
        int[] sizes = getZoomCellSizes(N);
        int idx = Math.max(0, Math.min(mZoomIndex, sizes.length - 1));
        return sizes[idx];
    }

    public int getZoomPercent() {
        int current = getCurrentCellSize();
        return Math.round(((float) current / 18.0f) * 100.0f);
    }

    public void zoomIn() {
        int[] sizes = getZoomCellSizes(getGridSize());
        if (mZoomIndex < sizes.length - 1) {
            mZoomIndex++;
            updateWidgetSize();
        }
    }

    public void zoomOut() {
        if (mZoomIndex > 0) {
            mZoomIndex--;
            updateWidgetSize();
        }
    }

    public void resetZoom() {
        int[] sizes = getZoomCellSizes(getGridSize());
        mZoomIndex = 0;
        for (int i = 0; i < sizes.length; i++) {
            if (sizes[i] == 18) {
                mZoomIndex = i;
                break;
            }
        }
        updateWidgetSize();
        if (parentScrollable != null) {
            parentScrollable.setHorizontalScrollOffset(0);
            parentScrollable.setVerticalScrollOffset(0);
        }
    }

    public int getZoomIndex() {
        int[] sizes = getZoomCellSizes(getGridSize());
        return Math.max(0, Math.min(mZoomIndex, sizes.length - 1));
    }

    public void setZoomIndex(int index) {
        int[] sizes = getZoomCellSizes(getGridSize());
        int clamped = Math.max(0, Math.min(index, sizes.length - 1));
        if (mZoomIndex != clamped) {
            mZoomIndex = clamped;
            updateWidgetSize();
        }
    }

    public float getZoomProgress() {
        int[] sizes = getZoomCellSizes(getGridSize());
        if (sizes.length <= 1) return 1.0f;
        int idx = getZoomIndex();
        return (float) idx / (float) (sizes.length - 1);
    }

    public void setZoomProgress(float progress) {
        int[] sizes = getZoomCellSizes(getGridSize());
        if (sizes.length <= 1) return;
        float p = Math.max(0.0f, Math.min(1.0f, progress));
        int targetIdx = Math.round(p * (sizes.length - 1));
        setZoomIndex(targetIdx);
    }

    public void updateWidgetSize() {
        int N = getGridSize();
        int cellSize = getCurrentCellSize();
        int gridPx = N * cellSize;
        int targetW = Math.max(getViewportWidth(), gridPx);
        int targetH = Math.max(getViewportHeight(), gridPx);
        setSize(targetW, targetH);
        if (parentScrollable != null) {
            parentScrollable.onRebuild();
        }
    }

    @Override
    public void onScreenUpdate() {
        super.onScreenUpdate();
        int N = getGridSize();
        int cellSize = getCurrentCellSize();
        int gridPx = N * cellSize;
        int targetW = Math.max(getViewportWidth(), gridPx);
        int targetH = Math.max(getViewportHeight(), gridPx);
        if (getSize().width != targetW || getSize().height != targetH) {
            setSize(targetW, targetH);
            if (parentScrollable != null) {
                parentScrollable.onRebuild();
            }
        }
    }

    public int[] getCellUnderCursor() {
        ReactorGridSyncData sync = reactor.getClientGridData();
        if (sync == null || sync.gridSize <= 0) return null;
        if (getContext() == null) return null;
        Cursor cursor = getContext().getCursor();
        if (cursor == null) return null;

        Pos2d spos = parentScrollable != null ? parentScrollable.getAbsolutePos() : getAbsolutePos();
        int cx = cursor.getX();
        int cy = cursor.getY();
        int spx = spos.x;
        int spy = spos.y;
        int spw = parentScrollable != null ? parentScrollable.getSize().width : getSize().width;
        int sph = parentScrollable != null ? parentScrollable.getSize().height : getSize().height;

        if (cx < spx || cx >= spx + spw || cy < spy || cy >= spy + sph) {
            return null;
        }

        int N = sync.gridSize;
        int cellSize = getCurrentCellSize();
        int gridPx = N * cellSize;
        int vpW = getViewportWidth();
        int vpH = getViewportHeight();
        int offsetX = (gridPx < vpW) ? (vpW - gridPx) / 2 : 0;
        int offsetY = (gridPx < vpH) ? (vpH - gridPx) / 2 : 0;

        int scrollX = parentScrollable != null ? parentScrollable.getHorizontalScrollOffset() : 0;
        int scrollY = parentScrollable != null ? parentScrollable.getVerticalScrollOffset() : 0;

        int mx = cx - spx + scrollX;
        int my = cy - spy + scrollY;

        int hx = (mx - offsetX) / cellSize;
        int renderHy = (my - offsetY) / cellSize;
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
    public ClickResult onClick(int button, boolean isShiftDown) {
        int[] cell = getCellUnderCursor();
        if (cell != null) {
            ItemStack cursorStack = null;
            if (getContext() != null && getContext().getPlayer() != null) {
                cursorStack = getContext().getPlayer().inventory.getItemStack();
            }

            if (isShiftDown && cursorStack != null && cursorStack.stackSize > 0) {
                // Batch shift-insert from top-left into empty buses
                syncToServer(PACKET_BATCH_SHIFT_INSERT, buf -> buf.writeInt(button));
                Interactable.playButtonClickSound();
                return ClickResult.ACCEPT;
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
            syncToServer(PACKET_SLOT_CLICK, buf -> {
                buf.writeInt(hx);
                buf.writeInt(hy);
                buf.writeInt(button);
                buf.writeBoolean(isShiftDown);
            });
            Interactable.playButtonClickSound();
            return ClickResult.ACCEPT;
        }

        if (parentScrollable != null) {
            return parentScrollable.onClick(button, isShiftDown);
        }
        return ClickResult.IGNORE;
    }

    @Override
    public void onMouseDragged(int button, long timeSinceLastClick) {
        if (mIsDraggingGrid && button == mDragButton) {
            int[] cell = getCellUnderCursor();
            if (cell != null) {
                int code = cell[0] * 1000 + cell[1];
                if (!mDragVisitedCells.contains(code)) {
                    mDragVisitedCells.add(code);
                    final int hx = cell[0];
                    final int hy = cell[1];
                    syncToServer(PACKET_DRAG_STEP, buf -> {
                        buf.writeInt(hx);
                        buf.writeInt(hy);
                        buf.writeInt(button);
                    });
                    Interactable.playButtonClickSound();
                }
            }
            return;
        }

        if (parentScrollable != null) {
            parentScrollable.onMouseDragged(button, timeSinceLastClick);
        }
    }

    @Override
    public boolean onClickReleased(int button) {
        if (mIsDraggingGrid) {
            mIsDraggingGrid = false;
            mDragVisitedCells.clear();
            return true;
        }
        if (parentScrollable != null) {
            return parentScrollable.onClickReleased(button);
        }
        return false;
    }

    @Override
    public boolean onMouseScroll(int direction) {
        if (GuiScreen.isCtrlKeyDown()) {
            if (direction > 0) {
                zoomIn();
            } else {
                zoomOut();
            }
            return true;
        }
        if (parentScrollable != null) {
            return parentScrollable.onMouseScroll(direction);
        }
        return false;
    }

    @Override
    public void readOnClient(int id, PacketBuffer buf) throws IOException {
        if (id == PACKET_SYNC_GRID) {
            ReactorGridSyncData data = ReactorGridSyncData.readFromBuffer(buf);
            reactor.applyGridSyncData(data);
        }
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) throws IOException {
        if (id == PACKET_SLOT_CLICK) {
            int gx = buf.readInt();
            int gy = buf.readInt();
            int button = buf.readInt();
            boolean isShift = buf.readBoolean();
            handleSlotClickServer(gx, gy, button, isShift);
        } else if (id == PACKET_BATCH_SHIFT_INSERT) {
            int button = buf.readInt();
            handleBatchShiftInsertServer(button);
        } else if (id == PACKET_DRAG_STEP) {
            int gx = buf.readInt();
            int gy = buf.readInt();
            int button = buf.readInt();
            handleDragStepServer(gx, gy, button);
        }
    }

    private void handleSlotClickServer(int gx, int gy, int button, boolean isShift) {
        ModularUIContext ctx = getContext();
        if (ctx == null) return;
        EntityPlayer player = ctx.getPlayer();
        if (!(player instanceof EntityPlayerMP playerMP)) return;
        if (reactor.mGrid == null || gx < 0 || gx >= reactor.gridSize || gy < 0 || gy >= reactor.gridSize) return;
        INuclearTile nTile = reactor.mGrid[gx][gy];
        if (!(nTile instanceof NuclearGridTile gridTile)) return;

        ItemStack cursorStack = player.inventory.getItemStack();

        if (gridTile.isBus()) {
            MTEHatchNuclearBus bus = gridTile.getBus();
            ItemStack slotStack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];

            if (isShift) {
                if (cursorStack == null && slotStack != null) {
                    if (player.inventory.addItemStackToInventory(slotStack)) {
                        bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
                    } else if (slotStack.stackSize <= 0) {
                        bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
                    }
                    bus.markTileDirty();
                    syncPlayerAndReactor(playerMP);
                }
            } else if (button == 0) {
                if (cursorStack == null) {
                    if (slotStack != null) {
                        player.inventory.setItemStack(slotStack);
                        bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
                        bus.markTileDirty();
                        syncPlayerAndReactor(playerMP);
                    }
                } else {
                    if (slotStack == null) {
                        ItemStack placed = cursorStack.copy();
                        placed.stackSize = 1;
                        bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = placed;
                        bus.markTileDirty();
                        cursorStack.stackSize--;
                        if (cursorStack.stackSize <= 0) {
                            player.inventory.setItemStack(null);
                        }
                        syncPlayerAndReactor(playerMP);
                    } else if (cursorStack.stackSize == 1) {
                        bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = cursorStack;
                        player.inventory.setItemStack(slotStack);
                        bus.markTileDirty();
                        syncPlayerAndReactor(playerMP);
                    }
                }
            } else if (button == 1) {
                if (cursorStack == null) {
                    if (slotStack != null) {
                        player.inventory.setItemStack(slotStack);
                        bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
                        bus.markTileDirty();
                        syncPlayerAndReactor(playerMP);
                    }
                } else if (slotStack == null) {
                    ItemStack placed = cursorStack.copy();
                    placed.stackSize = 1;
                    bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = placed;
                    bus.markTileDirty();
                    cursorStack.stackSize--;
                    if (cursorStack.stackSize <= 0) {
                        player.inventory.setItemStack(null);
                    }
                    syncPlayerAndReactor(playerMP);
                }
            }
        } else if (gridTile.isControlRod()) {
            MTEHatchNuclearControlRod rod = gridTile.getControlRod();
            ItemStack slotStack = rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD];

            if (isShift) {
                if (cursorStack == null && slotStack != null) {
                    if (player.inventory.addItemStackToInventory(slotStack)) {
                        rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = null;
                    } else if (slotStack.stackSize <= 0) {
                        rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = null;
                    }
                    rod.markTileDirty();
                    syncPlayerAndReactor(playerMP);
                }
            } else if (button == 0) {
                if (cursorStack == null) {
                    if (slotStack != null) {
                        player.inventory.setItemStack(slotStack);
                        rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = null;
                        rod.markTileDirty();
                        syncPlayerAndReactor(playerMP);
                    }
                } else if (MTEHatchNuclearControlRod.isControlRod(cursorStack)) {
                    if (slotStack == null) {
                        ItemStack placed = cursorStack.copy();
                        placed.stackSize = 1;
                        rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = placed;
                        rod.markTileDirty();
                        cursorStack.stackSize--;
                        if (cursorStack.stackSize <= 0) {
                            player.inventory.setItemStack(null);
                        }
                        syncPlayerAndReactor(playerMP);
                    } else if (cursorStack.stackSize == 1) {
                        rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = cursorStack;
                        player.inventory.setItemStack(slotStack);
                        rod.markTileDirty();
                        syncPlayerAndReactor(playerMP);
                    }
                }
            } else if (button == 1) {
                if (cursorStack == null) {
                    if (slotStack != null) {
                        player.inventory.setItemStack(slotStack);
                        rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = null;
                        rod.markTileDirty();
                        syncPlayerAndReactor(playerMP);
                    }
                } else if (slotStack == null && MTEHatchNuclearControlRod.isControlRod(cursorStack)) {
                    ItemStack placed = cursorStack.copy();
                    placed.stackSize = 1;
                    rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = placed;
                    rod.markTileDirty();
                    cursorStack.stackSize--;
                    if (cursorStack.stackSize <= 0) {
                        player.inventory.setItemStack(null);
                    }
                    syncPlayerAndReactor(playerMP);
                }
            }
        } else if (gridTile.isHatch()) {
            MTEHatchNuclearHatch hatch = gridTile.getHatch();
            if (isShift && cursorStack == null) {
                handleFluidHatchShiftClick(hatch, playerMP);
            } else if (cursorStack != null) {
                handleFluidHatchClickWithContainer(hatch, playerMP, button);
            }
            syncPlayerAndReactor(playerMP);
        }
    }

    private void handleBatchShiftInsertServer(int button) {
        ModularUIContext ctx = getContext();
        if (ctx == null) return;
        EntityPlayer player = ctx.getPlayer();
        if (!(player instanceof EntityPlayerMP playerMP)) return;
        ItemStack cursorStack = player.inventory.getItemStack();
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
                    if (bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] == null) {
                        ItemStack placed = cursorStack.copy();
                        placed.stackSize = 1;
                        bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = placed;
                        bus.markTileDirty();
                        cursorStack.stackSize--;
                        insertedAny = true;
                    }
                } else if (gridTile.isControlRod() && MTEHatchNuclearControlRod.isControlRod(cursorStack)) {
                    MTEHatchNuclearControlRod rod = gridTile.getControlRod();
                    if (rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] == null) {
                        ItemStack placed = cursorStack.copy();
                        placed.stackSize = 1;
                        rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = placed;
                        rod.markTileDirty();
                        cursorStack.stackSize--;
                        insertedAny = true;
                    }
                }
            }
        }

        if (cursorStack.stackSize <= 0) {
            player.inventory.setItemStack(null);
        }

        if (insertedAny) {
            syncPlayerAndReactor(playerMP);
        }
    }

    private void handleDragStepServer(int gx, int gy, int button) {
        ModularUIContext ctx = getContext();
        if (ctx == null) return;
        EntityPlayer player = ctx.getPlayer();
        if (!(player instanceof EntityPlayerMP playerMP)) return;
        ItemStack cursorStack = player.inventory.getItemStack();
        if (cursorStack == null || cursorStack.stackSize <= 0) return;
        if (reactor.mGrid == null || gx < 0 || gx >= reactor.gridSize || gy < 0 || gy >= reactor.gridSize) return;
        INuclearTile nTile = reactor.mGrid[gx][gy];
        if (!(nTile instanceof NuclearGridTile gridTile)) return;

        if (gridTile.isBus()) {
            MTEHatchNuclearBus bus = gridTile.getBus();
            if (bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] == null) {
                ItemStack placed = cursorStack.copy();
                placed.stackSize = 1;
                bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = placed;
                bus.markTileDirty();
                cursorStack.stackSize--;
                if (cursorStack.stackSize <= 0) {
                    player.inventory.setItemStack(null);
                }
                syncPlayerAndReactor(playerMP);
            }
        } else if (gridTile.isControlRod() && MTEHatchNuclearControlRod.isControlRod(cursorStack)) {
            MTEHatchNuclearControlRod rod = gridTile.getControlRod();
            if (rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] == null) {
                ItemStack placed = cursorStack.copy();
                placed.stackSize = 1;
                rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = placed;
                rod.markTileDirty();
                cursorStack.stackSize--;
                if (cursorStack.stackSize <= 0) {
                    player.inventory.setItemStack(null);
                }
                syncPlayerAndReactor(playerMP);
            }
        } else if (gridTile.isHatch()) {
            MTEHatchNuclearHatch hatch = gridTile.getHatch();
            handleFluidHatchClickWithContainer(hatch, playerMP, button);
            syncPlayerAndReactor(playerMP);
        }
    }

    private void handleFluidHatchClickWithContainer(MTEHatchNuclearHatch hatch, EntityPlayerMP player, int button) {
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
                        if (cursorStack.getItem() instanceof IFluidContainerItem containerItem) {
                            containerItem.fill(cursorStack, filledFluid, true);
                        } else {
                            player.inventory.setItemStack(filled);
                        }
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

    private void handleFluidHatchShiftClick(MTEHatchNuclearHatch hatch, EntityPlayerMP player) {
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
                            if (invStack.getItem() instanceof IFluidContainerItem containerItem) {
                                containerItem.fill(invStack, filledFluid, true);
                            } else {
                                player.inventory.mainInventory[i] = filled;
                            }
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

    private void syncPlayerAndReactor(EntityPlayerMP playerMP) {
        if (playerMP.openContainer != null) {
            playerMP.openContainer.detectAndSendChanges();
            playerMP.sendContainerToPlayer(playerMP.openContainer);
        }
        if (reactor.getBaseMetaTileEntity() != null) {
            reactor.getBaseMetaTileEntity()
                .markDirty();
        }
        ReactorGridSyncData sync = reactor.collectGridSyncData();
        syncToClient(PACKET_SYNC_GRID, buf -> ReactorGridSyncData.writeToBuffer(buf, sync));
    }

    private void prepareGuiState() {
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @SideOnly(Side.CLIENT)
    private void renderItemOverlay(ItemStack stack, int x, int y, int size) {
        if (stack == null) return;
        RenderItem itemRenderer = ModularGui.getItemRenderer();
        if (itemRenderer == null) return;
        FontRenderer fontRenderer = GuiHelper.getFontRenderer(stack);
        if (fontRenderer == null) {
            fontRenderer = Minecraft.getMinecraft().fontRenderer;
        }
        TextureManager textureManager = Minecraft.getMinecraft()
            .getTextureManager();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        float scale = (float) size / 16.0f;
        GlStateManager.scale(scale, scale, 1.0f);

        float prevZ = itemRenderer.zLevel;
        itemRenderer.zLevel = 200.0f;

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();

        itemRenderer.renderItemOverlayIntoGUI(fontRenderer, textureManager, stack, 0, 0, null);

        itemRenderer.zLevel = prevZ;
        GlStateManager.popMatrix();
    }

    @Override
    public void draw(float partialTicks) {
        ReactorGridSyncData sync = reactor.getClientGridData();
        if (sync == null || sync.gridSize <= 0) {
            String msg = "Offline / unformed";
            int w = GuiDraw.getStringWidth(msg);
            GuiDraw.drawString(
                msg,
                (getViewportWidth() - w) / 2,
                Math.max(10, (getViewportHeight() - 10) / 2),
                0x888888,
                false);
            return;
        }

        int N = sync.gridSize;
        int cellSize = getCurrentCellSize();
        int gridPx = N * cellSize;
        int vpW = getViewportWidth();
        int vpH = getViewportHeight();
        int offsetX = (gridPx < vpW) ? (vpW - gridPx) / 2 : 0;
        int offsetY = (gridPx < vpH) ? (vpH - gridPx) / 2 : 0;

        GlStateManager.pushMatrix();
        prepareGuiState();

        for (int gx = 0; gx < N; gx++) {
            for (int gy = 0; gy < N; gy++) {
                int renderGy = (N - 1) - gy;
                int px = offsetX + gx * cellSize;
                int py = offsetY + renderGy * cellSize;

                if (NuclearSimulationEngine.isCornerNullCell(gx, gy, N, N)) {
                    continue;
                }

                int idx = gx * N + gy;
                ReactorGridSyncData.ReactorGridCellData cell = (idx < sync.cells.size()) ? sync.cells.get(idx) : null;

                prepareGuiState();

                if (cellSize == 18) {
                    GTUITextures.SLOT_DARK_GRAY.draw(px, py, 18, 18, partialTicks);
                } else {
                    GuiDraw.drawRect(px, py, cellSize, cellSize, 0xFF373737);
                    GuiDraw.drawRect(px + 1, py + 1, cellSize - 2, cellSize - 2, 0xFF1E1E1E);
                }

                prepareGuiState();

                if (cell != null && cell.exists) {
                    int innerSize = Math.max(1, cellSize - 2);
                    if (cell.itemStack != null) {
                        GlStateManager.pushMatrix();
                        GlStateManager.translate(px + 1, py + 1, 0);
                        new ItemDrawable(cell.itemStack).draw(0, 0, innerSize, innerSize, partialTicks);
                        GlStateManager.popMatrix();
                        prepareGuiState();
                    } else if (cell.fluidStack != null) {
                        new FluidDrawable().setFluid(cell.fluidStack)
                            .draw(px + 1, py + 1, innerSize, innerSize, partialTicks);
                        prepareGuiState();
                    } else if (cell.isFluid) {
                        GuiDraw.drawRect(px + 1, py + 1, innerSize, innerSize, 0x300055AA);
                        prepareGuiState();
                    }

                    if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_TEMPERATURE) {
                        double maxTemp = NuclearSimulationEngine.getMaxOperatingTemperature(sync.pipeTier);
                        int color = NuclearColorMaps.getTemperatureColor(cell.temperature, maxTemp);
                        GuiDraw.drawRect(px + 1, py + 1, innerSize, innerSize, color);
                        prepareGuiState();
                    } else if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_NEUTRON_FLUX) {
                        int color = NuclearColorMaps.getNeutronColor(cell.fastFlux + cell.thermalFlux);
                        GuiDraw.drawRect(px + 1, py + 1, innerSize, innerSize, color);
                        prepareGuiState();
                    } else if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_NEUTRON_ABSORPTION) {
                        int color = NuclearColorMaps.getNeutronColor(5.0 * (cell.fastAbsorbed + cell.thermalAbsorbed));
                        GuiDraw.drawRect(px + 1, py + 1, innerSize, innerSize, color);
                        prepareGuiState();
                    }

                    double maxTemp = NuclearSimulationEngine.getMaxOperatingTemperature(sync.pipeTier);
                    if (cell.temperature > maxTemp * 0.85) {
                        if ((System.currentTimeMillis() / 400) % 2 == 0) {
                            GuiDraw.drawRect(px + 1, py + 1, innerSize, innerSize, 0x60FF0000);
                            prepareGuiState();
                        }
                    }

                    if (cell.itemStack != null) {
                        renderItemOverlay(cell.itemStack, px + 1, py + 1, innerSize);
                        prepareGuiState();
                    }
                }
            }
        }

        // Draw dragged cells highlight preview if active
        if (mIsDraggingGrid && !mDragVisitedCells.isEmpty()) {
            for (int code : mDragVisitedCells) {
                int dx = code / 1000;
                int dy = code % 1000;
                int renderDy = (N - 1) - dy;
                int dpx = offsetX + dx * cellSize;
                int dpy = offsetY + renderDy * cellSize;
                prepareGuiState();
                GuiDraw.drawRect(dpx + 1, dpy + 1, Math.max(1, cellSize - 2), Math.max(1, cellSize - 2), 0x6000FF00);
                prepareGuiState();
            }
        }

        // Slot Hover Highlight (only active when window is stationary and not dragging)
        boolean isWindowMoving = getWindow() != null && !getWindow().isEnabled();
        boolean isDragging = (getContext() != null && getContext().getCursor() != null
            && getContext().getCursor()
                .hasDraggable())
            || mIsDraggingGrid;
        if (!isWindowMoving && !isDragging
            && (isHovering() || (parentScrollable != null && parentScrollable.isHovering()))
            && getContext() != null) {
            int[] hCell = getCellUnderCursor();
            if (hCell != null) {
                int hx = hCell[0];
                int hy = hCell[1];
                int renderHy = (N - 1) - hy;
                int hpx = offsetX + hx * cellSize;
                int hpy = offsetY + renderHy * cellSize;
                prepareGuiState();
                GuiDraw.drawRect(hpx + 1, hpy + 1, Math.max(1, cellSize - 2), Math.max(1, cellSize - 2), 0x80FFFFFF);
                prepareGuiState();
            }
        }

        prepareGuiState();
        GlStateManager.popMatrix();
    }

    public List<String> getHoveredTooltip() {
        List<String> list = new ArrayList<>();
        boolean isWindowMoving = getWindow() != null && !getWindow().isEnabled();
        boolean isDragging = (getContext() != null && getContext().getCursor() != null
            && getContext().getCursor()
                .hasDraggable())
            || mIsDraggingGrid;
        if (isWindowMoving || isDragging
            || (!isHovering() && (parentScrollable == null || !parentScrollable.isHovering()))
            || getContext() == null) {
            return list;
        }
        ReactorGridSyncData sync = reactor.getClientGridData();
        if (sync == null || sync.gridSize <= 0) return list;

        int[] hCell = getCellUnderCursor();
        if (hCell == null) return list;
        int hx = hCell[0];
        int hy = hCell[1];

        int N = sync.gridSize;
        int idx = hx * N + hy;
        if (idx < 0 || idx >= sync.cells.size()) return list;
        ReactorGridSyncData.ReactorGridCellData cell = sync.cells.get(idx);
        if (cell == null || !cell.exists) {
            list.add(EnumChatFormatting.GRAY + "Empty core position (" + hx + ", " + hy + ")");
            return list;
        }

        double maxTemp = NuclearSimulationEngine.getMaxOperatingTemperature(sync.pipeTier);

        // 1. Component Identity
        if (cell.itemStack != null) {
            list.add(EnumChatFormatting.WHITE + cell.itemStack.getDisplayName());
            if (cell.itemStack.stackSize > 1) {
                list.add(EnumChatFormatting.GRAY + "Amount: " + cell.itemStack.stackSize);
            }
        } else if (cell.fluidStack != null) {
            list.add(EnumChatFormatting.AQUA + cell.fluidStack.getLocalizedName());
            if (MTENuclearReactor.isFluidFuel(cell.fluidStack)) {
                list.add(EnumChatFormatting.GOLD + "Nuclear liquid fuel");
            }
            list.add(EnumChatFormatting.GRAY + String.format("Amount: %,d L", cell.fluidStack.amount));
        } else if (cell.isFluid) {
            list.add(EnumChatFormatting.GRAY + "Empty nuclear fluid hatch");
        } else {
            list.add(EnumChatFormatting.GRAY + "Empty nuclear component bus");
        }

        // 2. Position
        list.add(EnumChatFormatting.DARK_GRAY + "Position: (" + hx + ", " + hy + ")");

        // 3. Thermal Telemetry
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

        list.add(
            EnumChatFormatting.GRAY + "Temperature: "
                + tempColor
                + String.format("%.1f °C", cell.temperature)
                + EnumChatFormatting.DARK_GRAY
                + String.format(" (safe limit: %.0f °C)", maxTemp));

        if (cell.temperature > maxTemp) {
            list.add(EnumChatFormatting.RED + "" + EnumChatFormatting.BOLD + "Status: melting point exceeded!");
        } else if (cell.temperature > maxTemp * 0.85) {
            list.add(EnumChatFormatting.RED + "Status: thermal warning - high temperature");
        } else if (cell.temperature > maxTemp * 0.5) {
            list.add(EnumChatFormatting.YELLOW + "Status: operating - elevated temperature");
        } else {
            list.add(EnumChatFormatting.GREEN + "Status: nominal operation");
        }

        // 4. Neutron Telemetry
        int totalFlux = cell.fastFlux + cell.thermalFlux;
        if (totalFlux > 0) {
            list.add(
                EnumChatFormatting.GRAY + "Neutron flux: "
                    + EnumChatFormatting.AQUA
                    + String.format("%,d /s", totalFlux)
                    + EnumChatFormatting.DARK_GRAY
                    + String.format(" (%d fast, %d thermal)", cell.fastFlux, cell.thermalFlux));
        }

        int totalAbs = cell.fastAbsorbed + cell.thermalAbsorbed;
        if (totalAbs > 0) {
            list.add(
                EnumChatFormatting.GRAY + "Neutrons absorbed: "
                    + EnumChatFormatting.LIGHT_PURPLE
                    + String.format("%,d /s", totalAbs)
                    + EnumChatFormatting.DARK_GRAY
                    + String.format(" (%d fast, %d thermal)", cell.fastAbsorbed, cell.thermalAbsorbed));
        }

        // 5. Radiovoltaic Power
        if (cell.directEU > 0) {
            list.add(
                EnumChatFormatting.AQUA + "Radiovoltaic power: "
                    + EnumChatFormatting.GREEN
                    + "+"
                    + String.format("%,d", cell.directEU)
                    + " EU/t");
        }

        // 6. Mode Context Telemetry
        if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_TEMPERATURE) {
            float delta = cell.temperature - sync.avgTemp;
            String deltaSign = delta >= 0 ? "+" : "";
            list.add(EnumChatFormatting.DARK_GRAY + String.format("Core temp delta: %s%.1f °C", deltaSign, delta));
        } else if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_NEUTRON_FLUX) {
            if (sync.neutronsProduced > 0 && totalFlux > 0) {
                double share = ((double) totalFlux / sync.neutronsProduced) * 100.0;
                list.add(EnumChatFormatting.DARK_GRAY + String.format("Core flux share: %.1f%%", share));
            }
        } else if (reactor.mCurrentGuiMode == MTENuclearReactor.GUI_MODE_NEUTRON_ABSORPTION) {
            int totalCoreAbs = sync.fastAbsorbed + sync.thermalAbsorbed;
            if (totalCoreAbs > 0 && totalAbs > 0) {
                double share = ((double) totalAbs / totalCoreAbs) * 100.0;
                list.add(EnumChatFormatting.DARK_GRAY + String.format("Core absorption share: %.1f%%", share));
            }
        }

        return list;
    }
}
