package com.gtnewhorizons.modularnuclear.common.metatileentity.hatch;

/*-
Note [Hatches that work without ticking] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
A reactor has one hatch per core cell, and the reactor does all of their work, so we don't want each of them to tick.
We also need its fluid hatches to accept coolant and fuel from the moment they load.  A reactor reloading at steady
state must not lose its supply, even briefly: that disturbs the steady state, which can damage it or melt it down.

Stock GregTech can't do both.  BaseMetaTileEntity.fill and canFill refuse fluid while the tile's tick counter
(mTickTimer) is at 5 or less.  isUseableByPlayer closes the GUI at 1 or less, and BlockMachines.onBlockActivated
ignores clicks at 0.  The counter starts at 0 on every load, and BaseMetaTileEntity.validate puts it back to 0.  So a
hatch refuses fluid for its first ticks, and a hatch with its ticking switched off can be left at 0 for good, refusing
fluid and clicks forever.

So hatches implementing IReadyBeforeFirstTick depart from GregTech in three ways, the first two in
MixinBaseMetaTileEntity:

1. fill and canFill read the tick counter as past any limit, whatever its value.  Their other checks still apply: a
   disabled input, covers and fluid direction.
2. validate leaves the counter alone once the hatch has switched its ticking off.  A running hatch's counter can't
   then go back to 0, so everything else that reads it keeps working without having to be patched.
3. The hatches call tryDisableTicking from onPostTick at tick 20.

A hatch still has to tick after loading, because every GregTech tile starts out ticking.  Tick 20 leaves time for the
tile's first-tick setup and its first updates to clients.

(W1) Covers.  tryDisableTicking does nothing while the tile has a cover, and placing a cover calls enableTicking, so a
     covered hatch ticks and its covers work.  Removing the cover lets onPostTick switch ticking off again.
(W2) Fill rules right after loading.  MTEHatchNuclearHatch.fill checks mReactorPipeTier, which is saved with the hatch,
     so it's right before the first tick.  The reactor sets it when it forms.

inworld-tests/test_hatch_ticking.py checks all of this against a stock GregTech hatch, which refuses fluid in the same
state.  inworld-tests/test_reactor_reload.py checks a running reactor across a reload.  Commit fa4ddec describes the
reactors this reset left with no coolant, and bot/reactor_parity.py in nh-dev measures that case against the simulator.
*/

/** A hatch whose base tile doesn't wait for its tick counter before accepting fluid or opening its GUI. */
public interface IReadyBeforeFirstTick {
}
