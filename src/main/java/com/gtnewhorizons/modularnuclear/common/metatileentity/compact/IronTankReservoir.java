package com.gtnewhorizons.modularnuclear.common.metatileentity.compact;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.IFluidHandler;

import com.gtnewhorizons.modularnuclear.common.coolantloop.pump.CoolantPumpReservoir;
import com.gtnewhorizons.modularnuclear.common.coolantloop.pump.ICoolantPumpHost;
import com.gtnewhorizons.modularnuclear.common.coolantloop.pump.ReservoirTank;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/*-
Note [The compact pump's tanks] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
A compact pump's reservoir is the stack of Iron Tanks standing on it.  Iron Tanks are BuildCraft tanks of other
sizes: stacked, they act as one tank, and the bottom one's fluid handler fills and drains the whole stack
(buildcraft.factory.TileTank).  So IronTankReservoir reaches the stack through that handler and needs no
dependency on either mod: it tells the tanks by their blocks' registry names (isIronTank).

The stack may be as tall as the player likes, but must be of one kind of tank: BuildCraft joins any tanks stacked
on each other, whatever they are made of, and a pump's stack with a plain BuildCraft tank or another Iron Tank in
it is refused (problem) rather than counted at some mixed capacity.  The stack's kind does not have to match the
loop's pipes, unlike a multiblock pump's Railcraft tank.

The stack is walked again once a second (find), not at every use: the pump asks for its tank several times a tick.
*/
/** A compact pump's reservoir. See Note [The compact pump's tanks] */
public class IronTankReservoir extends CoolantPumpReservoir {

    private static final Map<Block, Boolean> IRON_TANKS = new IdentityHashMap<>();
    private static final Map<Block, Boolean> ANY_TANKS = new IdentityHashMap<>();

    private ReservoirTank cached;
    private long cachedAt = Long.MIN_VALUE;
    private String problem = "modularnuclear.compact.pump.status.no_tank";
    private Block tankBlock;
    private int tankCount;

    public IronTankReservoir(ICoolantPumpHost host) {
        super(host);
    }

    /** Whether a block is one of the Iron Tanks mod's tanks. */
    public static boolean isIronTank(Block block) {
        return IRON_TANKS.computeIfAbsent(block, b -> "irontank".equals(modOf(b)));
    }

    /** Whether a block is a tank BuildCraft stacks: an Iron Tank or BuildCraft's own. */
    private static boolean stacks(Block block) {
        return ANY_TANKS.computeIfAbsent(block, b -> {
            GameRegistry.UniqueIdentifier id = b != null ? GameRegistry.findUniqueIdentifierFor(b) : null;
            return id != null && ("irontank".equals(id.modId)
                || "BuildCraft|Factory".equals(id.modId) && "tankBlock".equals(id.name));
        });
    }

    private static String modOf(Block block) {
        GameRegistry.UniqueIdentifier id = block != null ? GameRegistry.findUniqueIdentifierFor(block) : null;
        return id != null ? id.modId : null;
    }

    @Override
    protected ReservoirTank findTank() {
        IGregTechTileEntity base = host.getBaseMetaTileEntity();
        if (base == null || base.getWorld() == null) return null;
        long now = base.getTimer();
        if (now - cachedAt >= 0 && now - cachedAt < 20 && cachedAt != Long.MIN_VALUE) return cached;
        cachedAt = now;
        cached = find(base.getWorld(), base.getXCoord(), base.getYCoord(), base.getZCoord());
        return cached;
    }

    /** Forgets the stack found last, so the next use looks again. */
    public void invalidate() {
        cachedAt = Long.MIN_VALUE;
    }

    private ReservoirTank find(World world, int x, int y, int z) {
        tankBlock = null;
        tankCount = 0;
        Block bottom = world.getBlock(x, y + 1, z);
        TileEntity tile = world.getTileEntity(x, y + 1, z);
        if (!isIronTank(bottom) || !(tile instanceof IFluidHandler handler)) {
            problem = "modularnuclear.compact.pump.status.no_tank";
            return null;
        }
        int count = 1;
        for (int ty = y + 2; ty < world.getHeight(); ty++) {
            Block above = world.getBlock(x, ty, z);
            if (!stacks(above)) break;
            if (above != bottom) {
                problem = "modularnuclear.compact.pump.status.mixed_tanks";
                return null;
            }
            count++;
        }
        problem = null;
        tankBlock = bottom;
        tankCount = count;
        return ReservoirTank.of(handler, ForgeDirection.DOWN);
    }

    /** Why the pump has no tank, as a status key, or null if it has one. */
    public String problem() {
        findTank();
        return problem;
    }

    /** How many tanks the stack has, and of which block; 0 and null without a valid stack. */
    public int getTankCount() {
        return tankCount;
    }

    public Block getTankBlock() {
        return tankBlock;
    }
}
