package com.gtnewhorizons.modularnuclear.common.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.ModularContainer;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.NuclearGridTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.INuclearTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification;

/**
 * Custom ModularUI 2 Container for the MPTR multiblock.
 * Intercepts player inventory shift-clicks to transfer compatible fuel rods and reactor components
 * directly into empty core grid slots (top-to-bottom, left-to-right).
 */
public class NuclearReactorContainer extends ModularContainer {

    private final MTENuclearReactor reactor;

    public NuclearReactorContainer(MTENuclearReactor reactor) {
        this.reactor = reactor;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        Slot slot = getSlot(slotIndex);
        if (slot != null && slot.getHasStack()) {
            ItemStack stack = slot.getStack();
            if (stack != null && stack.stackSize > 0) {
                boolean isCompatible = NuclearFuelClassification.isItemAllowedInBus(stack, null)
                    || MTEHatchNuclearControlRod.isControlRod(stack);

                if (isCompatible) {
                    if (player instanceof EntityPlayerMP playerMP && reactor.mGrid != null && reactor.gridSize > 0) {
                        boolean moved = false;
                        int N = reactor.gridSize;

                        // Traverse visual rows from top to bottom (renderGy = 0 .. N-1), left to right (gx = 0 .. N-1)
                        for (int renderGy = 0; renderGy < N && stack.stackSize > 0; renderGy++) {
                            int gy = (N - 1) - renderGy;
                            for (int gx = 0; gx < N && stack.stackSize > 0; gx++) {
                                if (NuclearSimulationEngine.isCornerNullCell(gx, gy, N, N)) continue;
                                INuclearTile tile = reactor.mGrid[gx][gy];
                                if (!(tile instanceof NuclearGridTile gt)) continue;

                                if (gt.isBus()) {
                                    MTEHatchNuclearBus bus = gt.getBus();
                                    if (bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT) == null
                                        && bus.isItemAllowed(stack)) {
                                        ItemStack single = stack.copy();
                                        single.stackSize = 1;
                                        bus.setInventorySlotContents(MTEHatchNuclearBus.SLOT_INPUT, single);
                                        bus.markTileDirty();
                                        stack.stackSize--;
                                        moved = true;
                                    }
                                } else if (gt.hasControlRod() && MTEHatchNuclearControlRod.isControlRod(stack)) {
                                    MTEHatchNuclearControlRod rod = gt.getBottomControlRod();
                                    if (rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] == null) {
                                        ItemStack single = stack.copy();
                                        single.stackSize = 1;
                                        rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = single;
                                        rod.markTileDirty();
                                        stack.stackSize--;
                                        moved = true;
                                    }
                                }
                            }
                        }

                        ItemStack copy = stack.copy();
                        if (moved) {
                            if (stack.stackSize <= 0) {
                                slot.putStack(null);
                            } else {
                                slot.onSlotChanged();
                            }
                            if (reactor.getBaseMetaTileEntity() != null) {
                                reactor.getBaseMetaTileEntity()
                                    .markDirty();
                            }
                            detectAndSendChanges();
                            return copy;
                        }
                    } else if (player.worldObj.isRemote) {
                        // Client side: prevent shift-click from moving the fuel rod into another inventory slot
                        return null;
                    }
                }
            }
        }
        return super.transferStackInSlot(player, slotIndex);
    }
}
