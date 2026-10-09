package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.nuclear.INuclearTile;

import ic2.api.reactor.IReactor;

public class ReactorDummy implements IReactor {

    private final MTENuclearReactor reactor;
    private NuclearGridTile currentTile;

    public ReactorDummy(MTENuclearReactor reactor) {
        this.reactor = reactor;
    }

    public void setCurrentTile(NuclearGridTile tile) {
        this.currentTile = tile;
    }

    public NuclearGridTile getCurrentTile() {
        return currentTile;
    }

    public MTENuclearReactor getReactor() {
        return reactor;
    }

    @Override
    public ChunkCoordinates getPosition() {
        if (reactor.getBaseMetaTileEntity() == null) return new ChunkCoordinates(0, 0, 0);
        return new ChunkCoordinates(
            reactor.getBaseMetaTileEntity()
                .getXCoord(),
            reactor.getBaseMetaTileEntity()
                .getYCoord(),
            reactor.getBaseMetaTileEntity()
                .getZCoord());
    }

    @Override
    public World getWorld() {
        return reactor.getBaseMetaTileEntity() != null ? reactor.getBaseMetaTileEntity()
            .getWorld() : null;
    }

    @Override
    public int getHeat() {
        return 0; // No concept of reactor hull heat
    }

    @Override
    public void setHeat(int heat) {}

    @Override
    public int addHeat(int amount) {
        return 0;
    }

    @Override
    public int getMaxHeat() {
        return 10000;
    }

    @Override
    public void setMaxHeat(int maxHeat) {}

    @Override
    public void addEmitHeat(int heat) {}

    @Override
    public float getHeatEffectModifier() {
        return 1.0f;
    }

    @Override
    public void setHeatEffectModifier(float modifier) {}

    @Override
    public float getReactorEnergyOutput() {
        return 0;
    }

    @Override
    public double getReactorEUEnergyOutput() {
        return 0;
    }

    @Override
    public float addOutput(float energy) {
        return energy;
    }

    @Override
    public ItemStack getItemAt(int x, int y) {
        if (reactor.mGrid != null && x >= 0 && x < reactor.gridSize && y >= 0 && y < reactor.gridSize) {
            INuclearTile t = reactor.mGrid[x][y];
            if (t instanceof NuclearGridTile gt && gt.isBus()) {
                return gt.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            }
        } else if (currentTile != null && currentTile.isBus()) {
            return currentTile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
        }
        return null;
    }

    @Override
    public void setItemAt(int x, int y, ItemStack item) {
        if (reactor.mGrid != null && x >= 0 && x < reactor.gridSize && y >= 0 && y < reactor.gridSize) {
            INuclearTile t = reactor.mGrid[x][y];
            if (t instanceof NuclearGridTile gt && gt.isBus()) {
                gt.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT] = item;
                gt.getBus()
                    .markTileDirty();
            }
        } else if (currentTile != null && currentTile.isBus()) {
            currentTile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT] = item;
            currentTile.getBus()
                .markTileDirty();
        }
    }

    @Override
    public void explode() {}

    @Override
    public int getTickRate() {
        return 20;
    }

    @Override
    public boolean produceEnergy() {
        return true;
    }

    @Override
    public void setRedstoneSignal(boolean redstone) {}

    @Override
    public boolean isFluidCooled() {
        return false;
    }
}
