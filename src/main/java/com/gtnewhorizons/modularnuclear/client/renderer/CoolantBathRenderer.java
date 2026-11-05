package com.gtnewhorizons.modularnuclear.client.renderer;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.world.WorldEvent;

import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTECoolantBath;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * Shows the items in the coolant baths the client knows of: each bath gets a {@link CoolantBathItemsFX} once its slots
 * first arrive, which draws them under its glass roof for as long as the bath stays.
 */
@SideOnly(Side.CLIENT)
public class CoolantBathRenderer {

    private static final Map<MTECoolantBath, CoolantBathItemsFX> EFFECTS = new WeakHashMap<>();

    /** Gives a bath whose slots just arrived its items' particle, if it has none alive. */
    public static void ensureEffect(MTECoolantBath bath) {
        Minecraft mc = Minecraft.getMinecraft();
        IGregTechTileEntity bte = bath.getBaseMetaTileEntity();
        if (mc.theWorld == null || mc.effectRenderer == null || bte == null || bte.getWorld() != mc.theWorld) return;
        CoolantBathItemsFX fx = EFFECTS.get(bath);
        if (fx != null && fx.isFor(bath)) return;
        fx = new CoolantBathItemsFX(bath, bte);
        EFFECTS.put(bath, fx);
        mc.effectRenderer.addEffect(fx);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world.isRemote) {
            MTECoolantBath.ACTIVE_CLIENT_BATHS.clear();
            EFFECTS.clear();
        }
    }
}
