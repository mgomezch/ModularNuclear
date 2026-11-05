package com.gtnewhorizons.modularnuclear.common.metatileentity.multi.util;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import ic2.api.reactor.IReactor;
import ic2.api.reactor.IReactorComponent;

/**
 * A coolant cell's stored heat, read and set the way a reactor does it (IReactorComponent): IC2 cells show their heat
 * as damage on a scale of their own, and GregTech's keep it in NBT with damage a 0..100 gauge, so neither's damage is
 * its heat. Items that are not reactor components fall back to their damage.
 */
public final class CellHeat {

    private CellHeat() {}

    /** The reactor component a stack keeps its heat as, or null. */
    public static IReactorComponent component(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof IReactorComponent comp)) return null;
        return comp.getMaxHeat(HOST, stack, 0, 0) > 0 ? comp : null;
    }

    public static int max(ItemStack stack) {
        IReactorComponent comp = component(stack);
        if (comp != null) return comp.getMaxHeat(HOST, stack, 0, 0);
        return stack.getMaxDamage() > 0 ? stack.getMaxDamage() : 10000;
    }

    /** The heat of an empty (cold) cell: 0, or damage 1 for the damage fallback. */
    public static int cold(ItemStack stack) {
        return component(stack) != null ? 0 : 1;
    }

    public static int get(ItemStack stack) {
        IReactorComponent comp = component(stack);
        return comp != null ? comp.getCurrentHeat(HOST, stack, 0, 0) : stack.getItemDamage();
    }

    /** Sets a cell's heat, kept within cold..max (a component overfilled would ask the reactor to remove it). */
    public static void set(ItemStack stack, int heat) {
        int h = Math.max(cold(stack), Math.min(max(stack), heat));
        IReactorComponent comp = component(stack);
        if (comp != null) comp.alterHeat(HOST, stack, 0, 0, h - comp.getCurrentHeat(HOST, stack, 0, 0));
        else stack.setItemDamage(h);
    }

    /**
     * The reactor a cell sees in a bath: none, really. Not fluid cooled, so GregTech cells keep their control tag as in
     * a reactor; nothing here is ever asked to explode or take items out.
     */
    private static final IReactor HOST = new IReactor() {

        @Override
        public ChunkCoordinates getPosition() {
            return new ChunkCoordinates();
        }

        @Override
        public World getWorld() {
            return null;
        }

        @Override
        public int getHeat() {
            return 0;
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
            return 0.0f;
        }

        @Override
        public double getReactorEUEnergyOutput() {
            return 0.0;
        }

        @Override
        public float addOutput(float energy) {
            return 0.0f;
        }

        @Override
        public ItemStack getItemAt(int x, int y) {
            return null;
        }

        @Override
        public void setItemAt(int x, int y, ItemStack item) {}

        @Override
        public void explode() {}

        @Override
        public int getTickRate() {
            return 20;
        }

        @Override
        public boolean produceEnergy() {
            return false;
        }

        @Override
        public void setRedstoneSignal(boolean redstone) {}

        @Override
        public boolean isFluidCooled() {
            return false;
        }
    };
}
