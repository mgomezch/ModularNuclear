package com.gtnewhorizons.modularnuclear.common.metatileentity.compact;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopDataRegistry;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ConvectiveHeatTransferModel;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.SegmentHeat;
import com.gtnewhorizons.modularnuclear.common.gui.CompactBathGui;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.gas.GasMachineParts;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.util.CoolantBathSlot;
import com.gtnewhorizons.modularnuclear.common.util.CoolantBathRegistry;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;

/*-
Note [The compact bath] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
A Compact Coolant Bath cools hot ingots in a gas loop's evaporator, a few at a time.  It is the compact loop's
answer to the Vacuum Freezer and, at MV, to cooling hot ingots in a Chemical Bath.

Inventory.  Slot IN takes hot ingots, slot OUT holds cooled ones, and the slots after them hold the ingots being
cooled, one each, as many as the tier's CompactTier.bathSlots.  Pipes, hoppers and conveyors put into IN and take
from OUT on any side but the front; a right click with hot ingots puts them in, and one with anything else takes
the cooled ingots out.  The ingots being cooled can't be reached, and drop with the rest when the bath is broken.

Heat.  Each ingot being cooled is a CoolantBathSlot made by CoolantBathRegistry.createSlot, as in the multiblock
bath, holding the same heat.  It gives that heat to the gas through the bath's coil (exchange) at COIL_PACE times
the pace its slot's area alone gives, whatever the gas's phase: a thermostatic valve keeps a one-segment
evaporator's gas just superheated, and the phase factor a multiblock bath applies would leave the coil at a
third of that pace for good.  The gas is never heated past SegmentHeat's coil bound, and the ingot leaves as its
cold ingot once it reaches bath.ingotCooledTempC.

The bath works on a gas loop only, and holds only hot ingots: coolant cells and cryogenic cells are the multiblock
bath's.
*/
/** Compact Coolant Bath. See Note [The compact bath] and Note [Compact loop machines] */
public class MTECompactBath extends MTECompactLoopMachine {

    /**
     * How much faster the bath's coil takes an ingot's heat than its slot's area alone gives: that area cools the
     * ingot towards the coolant with the freezer's duration as its time constant (CoolantBathRegistry.exchangeArea),
     * about three such durations from a hot ingot to a cold one.
     */
    public static final double COIL_PACE = 2.0;

    public static final int IN = 0, OUT = 1, FIRST_WORK = 2;

    private final CoolantBathSlot[] mSlots;
    private double mLastHeatWatts = 0.0;
    private long mCooledItems = 0L;

    public MTECompactBath(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier, FIRST_WORK + CompactTier.of(aTier).bathSlots);
        mSlots = new CoolantBathSlot[CompactTier.of(aTier).bathSlots];
    }

    public MTECompactBath(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, FIRST_WORK + CompactTier.of(aTier).bathSlots, aDescription, aTextures);
        mSlots = new CoolantBathSlot[CompactTier.of(aTier).bathSlots];
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTECompactBath(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    protected String getKind() {
        return "compact_bath";
    }

    @Override
    protected boolean usesPower() {
        return false;
    }

    @Override
    protected ITexture frontOverlay(boolean active) {
        return TextureFactory.of(
            active ? Textures.BlockIcons.OVERLAY_FRONT_HEAT_EXCHANGER_ACTIVE
                : Textures.BlockIcons.OVERLAY_FRONT_HEAT_EXCHANGER);
    }

    // --- Items ---

    /** Whether the bath cools this item: a hot ingot with a cold one to become. */
    public static boolean cools(ItemStack stack) {
        if (stack == null) return false;
        CoolantBathSlot slot = CoolantBathRegistry.createSlot(stack, 20.0);
        return slot != null && slot.isIngot && slot.cooling && slot.transformedOutput != null;
    }

    /** A slot for a hot ingot, or null for anything else. */
    static CoolantBathSlot newSlot(ItemStack hotIngot, double coolantTempC) {
        CoolantBathSlot slot = CoolantBathRegistry.createSlot(hotIngot, coolantTempC);
        return slot != null && slot.isIngot && slot.cooling && slot.transformedOutput != null ? slot : null;
    }

    @Override
    public boolean isValidSlot(int index) {
        return index == IN || index == OUT;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity base, int index, ForgeDirection side, ItemStack stack) {
        return index == IN && side != base.getFrontFacing() && cools(stack);
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity base, int index, ForgeDirection side, ItemStack stack) {
        return index == OUT && side != base.getFrontFacing();
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return index == IN && cools(stack);
    }

    public int getWorkSlotCount() {
        return mSlots.length;
    }

    /** Its slots are worth the player's inventory in its GUI. */
    @Override
    public boolean doesBindPlayerInventory() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        return new CompactBathGui(this).build(guiData, syncManager, uiSettings);
    }

    /** Hot ingots in hand go in; otherwise the GUI opens. */
    @Override
    public boolean onRightclick(IGregTechTileEntity base, EntityPlayer player) {
        if (!base.isServerSide()) return true;
        ItemStack held = player.getCurrentEquippedItem();
        if (held != null && cools(held)) {
            ItemStack in = mInventory[IN];
            if (in == null) {
                mInventory[IN] = held.copy();
                held.stackSize = 0;
            } else if (GTUtility.areStacksEqual(in, held)) {
                int moved = Math.min(held.stackSize, in.getMaxStackSize() - in.stackSize);
                in.stackSize += moved;
                held.stackSize -= moved;
            }
            if (held.stackSize <= 0) player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            player.inventoryContainer.detectAndSendChanges();
            base.markDirty();
            return true;
        }
        return super.onRightclick(base, player);
    }

    /**
     * Moves cooled ingots to OUT, and one ingot from IN into a free working slot: one at a time, half a second
     * apart, so the slots' ingots are at different temperatures and the loop's load is steadier than a batch's.
     */
    private void moveItems() {
        boolean loaded = false;
        for (int i = 0; i < mSlots.length; i++) {
            CoolantBathSlot slot = mSlots[i];
            if (slot != null && !slot.isEmpty() && slot.isAutoEjectReady()) {
                ItemStack cold = slot.transformedOutput.copy();
                cold.stackSize = 1;
                ItemStack out = mInventory[OUT];
                if (out == null) {
                    mInventory[OUT] = cold;
                } else if (GTUtility.areStacksEqual(out, cold) && out.stackSize < out.getMaxStackSize()) {
                    out.stackSize++;
                } else {
                    continue;
                }
                mSlots[i] = null;
                mInventory[FIRST_WORK + i] = null;
                mCooledItems++;
            }
            if (!loaded && (mSlots[i] == null || mSlots[i].isEmpty()) && mInventory[IN] != null) {
                ItemStack one = mInventory[IN].copy();
                one.stackSize = 1;
                double coolant = mBoundSegment != null ? mBoundSegment.getCurrentTemperatureCelsius() : 20.0;
                CoolantBathSlot next = newSlot(one, coolant);
                if (next == null) continue;
                loaded = true;
                mSlots[i] = next;
                mInventory[FIRST_WORK + i] = one;
                if (--mInventory[IN].stackSize <= 0) mInventory[IN] = null;
            }
        }
    }

    public int getActiveItemCount() {
        int n = 0;
        for (CoolantBathSlot s : mSlots) if (s != null && !s.isEmpty()) n++;
        return n;
    }

    @Override
    public void onPostTick(IGregTechTileEntity base, long tick) {
        super.onPostTick(base, tick);
        if (base.isServerSide() && tick % 10 == 0) moveItems();
    }

    // --- Heat ---

    @Override
    protected void onLoopTick(double flowM3s, double dt, CoolantFluidProperty fluid, LoopSegment segment) {
        mLastHeatWatts = 0.0;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (segment == null || base == null) return;
        GasProperty gas = gas();
        if (gas == null || !segment.holdsGas()) {
            mStatus = "Needs a gas loop";
            base.setActive(false);
            return;
        }
        int active = getActiveItemCount();
        if (active == 0 || segment.getGasMassFlow() <= 0.0) {
            mStatus = active == 0 ? "Empty" : "No flow";
            base.setActive(false);
            return;
        }
        mLastHeatWatts = exchange(mSlots, gas, segment, dt) / dt;
        mStatus = String.format("Cooling %d of %d", active, mSlots.length);
        base.setActive(true);
    }

    /**
     * One tick of the slots' ingots giving heat to the gas in the bath's coil: each at the pace that cools it in the
     * freezer's time in boiling refrigerant, all scaled down together to what the gas takes. Returns the heat moved
     * into the gas, J.
     */
    public static double exchange(CoolantBathSlot[] slots, GasProperty gas, LoopSegment segment, double dt) {
        ConvectiveHeatTransferModel conv = CoolantLoopDataRegistry.getConvectiveModel();
        if (conv == null) conv = ConvectiveHeatTransferModel.DEFAULT;
        double gasC = segment.getCurrentTemperatureCelsius();
        double[] proposed = new double[slots.length];
        double total = 0.0, hottestC = gasC;
        for (int i = 0; i < slots.length; i++) {
            CoolantBathSlot slot = slots[i];
            if (slot == null || slot.isEmpty() || slot.currentTemp <= gasC) continue;
            double q = COIL_PACE * conv.getHMax() * slot.surfaceArea * (slot.currentTemp - gasC) * dt;
            // Never past the gas's temperature within a step
            q = Math.min(q, slot.heatCapacity * (slot.currentTemp - gasC));
            proposed[i] = q;
            total += q;
            hottestC = Math.max(hottestC, slot.currentTemp);
        }
        if (total <= 0.0) return 0.0;
        double boundK = Math.min(hottestC + 273.15, SegmentHeat.coilBoundK(gas, segment));
        double moved = SegmentHeat.exchange(gas, segment, boundK, total, dt);
        double scale = moved / total;
        for (int i = 0; i < slots.length; i++) {
            if (proposed[i] > 0.0) slots[i].currentTemp -= proposed[i] * scale / slots[i].heatCapacity;
        }
        return moved;
    }

    // --- Readouts ---

    @Override
    protected void addReadoutLines(List<String> l) {
        l.add(String.format("Heat taken: %sW, %d ingots cooled", GasMachineParts.si(mLastHeatWatts), mCooledItems));
        for (CoolantBathSlot s : mSlots) {
            if (s == null || s.isEmpty()) continue;
            l.add(String.format("  %s: %.0f °C", s.itemStack.getDisplayName(), s.currentTemp));
        }
        ItemStack in = mInventory[IN], out = mInventory[OUT];
        l.add(
            String.format(
                "Waiting: %d, cooled and ready: %d",
                in != null ? in.stackSize : 0,
                out != null ? out.stackSize : 0));
    }

    @Override
    protected void addTooltipLines(List<String> lines) {
        lines.add(StatCollector.translateToLocal("modularnuclear.compact.compact_bath.tooltip"));
        lines.add(
            StatCollector
                .translateToLocalFormatted("modularnuclear.compact.compact_bath.tooltip.stats", tier().bathSlots));
        lines.add(StatCollector.translateToLocal("modularnuclear.compact.compact_bath.tooltip.use"));
    }

    public long getCooledItems() {
        return mCooledItems;
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {
        super.saveNBTData(nbt);
        nbt.setLong("mCooledItems", mCooledItems);
        for (int i = 0; i < mSlots.length; i++) {
            if (mSlots[i] != null && !mSlots[i].isEmpty()) nbt.setTag("bathSlot" + i, mSlots[i].writeToNBT());
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        super.loadNBTData(nbt);
        mCooledItems = nbt.getLong("mCooledItems");
        for (int i = 0; i < mSlots.length; i++) {
            mSlots[i] = nbt.hasKey("bathSlot" + i) ? CoolantBathSlot.readFromNBT(nbt.getCompoundTag("bathSlot" + i))
                : null;
        }
    }
}
