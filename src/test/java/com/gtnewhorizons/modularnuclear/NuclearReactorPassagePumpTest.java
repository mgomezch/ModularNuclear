package com.gtnewhorizons.modularnuclear;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.coolantloop.ICoolantPassageHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;

/**
 * GT re-runs a reactor's structure check on every block update next to it. The check must keep its passages bound to
 * their loop pump: the pump binds them only when it crawls, and an unbound passage moderates as if it held no coolant.
 */
public class NuclearReactorPassagePumpTest {

    @BeforeAll
    public static void setUp() {
        TestEnvironmentHelper.init();
    }

    @Test
    public void structureCheckKeepsPassagePumps() {
        MTENuclearReactor reactor = new MTENuclearReactor("multimachine.nuclearreactor");
        ICoolantPassageHatch top = mock(ICoolantPassageHatch.class);
        ICoolantPassageHatch bottom = mock(ICoolantPassageHatch.class);
        reactor.mTopHighPressureHatches.add(top);
        reactor.mBottomHighPressureHatches.add(bottom);

        reactor.clearHatches();

        for (ICoolantPassageHatch hp : new ICoolantPassageHatch[] { top, bottom }) {
            verify(hp).setOppositeHatch(null);
            verify(hp, never()).setConnectedPump(any());
        }
    }
}
