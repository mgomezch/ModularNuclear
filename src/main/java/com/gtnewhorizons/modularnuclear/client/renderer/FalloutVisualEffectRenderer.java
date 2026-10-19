package com.gtnewhorizons.modularnuclear.client.renderer;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.client.shader.ShaderUniform;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.modularnuclear.ModularNuclear;
import com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig;
import com.gtnewhorizons.modularnuclear.common.entity.EntityMeltdownFallout;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.hazards.Hazard;
import gregtech.api.hazards.HazardProtection;

@SideOnly(Side.CLIENT)
public class FalloutVisualEffectRenderer {

    public static final FalloutVisualEffectRenderer INSTANCE = new FalloutVisualEffectRenderer();

    private static final Set<EntityMeltdownFallout> CLIENT_FALLOUT_ENTITIES = Collections
        .synchronizedSet(new HashSet<>());

    private ShaderGroup shaderGroup;
    private int lastDisplayWidth = -1;
    private int lastDisplayHeight = -1;
    private float currentIntensity = 0.0f;
    private float targetIntensity = 0.0f;
    private boolean shaderLoadFailed = false;

    private FalloutVisualEffectRenderer() {}

    public static void init() {
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
    }

    public static void registerFallout(EntityMeltdownFallout fallout) {
        if (fallout != null) {
            CLIENT_FALLOUT_ENTITIES.add(fallout);
        }
    }

    public static void unregisterFallout(EntityMeltdownFallout fallout) {
        if (fallout != null) {
            CLIENT_FALLOUT_ENTITIES.remove(fallout);
        }
    }

    public static float getCurrentIntensity() {
        return INSTANCE.currentIntensity;
    }

    public static int getHazmatPieceCount(EntityPlayer player) {
        if (player == null) return 0;
        try {
            if (HazardProtection.isWearingFullRadioHazmat(player)) {
                return 4;
            }
        } catch (Throwable ignored) {}

        int count = 0;
        for (int i = 1; i <= 4; i++) {
            ItemStack stack = player.getEquipmentInSlot(i);
            if (stack == null) continue;
            boolean protects = false;
            try {
                if (HazardProtection.protectsAgainstHazard(player, stack, Hazard.RADIOLOGICAL)
                    || HazardProtection.protectsAgainstHazardFully(player, stack, Hazard.RADIOLOGICAL)
                    || HazardProtection.protectsAgainstHazard(stack, Hazard.RADIOLOGICAL)) {
                    protects = true;
                }
            } catch (Throwable ignored) {}
            if (!protects && stack.getUnlocalizedName() != null) {
                String lower = stack.getUnlocalizedName().toLowerCase();
                if (lower.contains("hazmat") || lower.contains("rubboot") || lower.contains("rubber") || lower.contains("radiation")) {
                    protects = true;
                }
            }
            if (!protects && stack.getItem() != null) {
                String id = (String) net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
                if (id != null) {
                    String lowerId = id.toLowerCase();
                    if (lowerId.contains("hazmat") || lowerId.contains("rubboot") || lowerId.contains("rubber") || lowerId.contains("radiation")) {
                        protects = true;
                    }
                }
            }
            if (protects) count++;
        }
        return count;
    }

    public static boolean isWearingFullHazmat(EntityPlayer player) {
        if (player == null) return false;
        try {
            if (HazardProtection.isWearingFullRadioHazmat(player)) {
                return true;
            }
        } catch (Throwable ignored) {}
        return getHazmatPieceCount(player) >= 4;
    }

    public static float computeIntensity(double distance, double maxDist, boolean fullHazmat) {
        if (distance >= maxDist) return 0.0f;
        if (fullHazmat) {
            // Full 4-piece hazmat protection shifts effective distance outward by 40m (or half maxDist),
            // ensuring maximum effect intensity at d=0 never exceeds no-hazmat intensity at 40m,
            // and completely clearing the visual static outside 40m.
            double hazmatDistOffset = Math.min(40.0, maxDist * 0.5);
            double effectiveDist = distance + hazmatDistOffset;
            if (effectiveDist >= maxDist) return 0.0f;

            float norm = (float) (effectiveDist / maxDist);
            float t = Math.max(0.0f, Math.min(1.0f, 1.0f - norm));
            return t * t * (3.0f - 2.0f * t);
        } else {
            // Partial hazmat (< 4 pieces) or no hazmat provides zero effect reduction
            float norm = (float) (distance / maxDist);
            float t = Math.max(0.0f, Math.min(1.0f, 1.0f - norm));
            return t * t * (3.0f - 2.0f * t);
        }
    }

    public static float computeIntensity(double distance, double maxDist, int hazmatPieces) {
        return computeIntensity(distance, maxDist, hazmatPieces >= 4);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            currentIntensity = 0.0f;
            targetIntensity = 0.0f;
            CLIENT_FALLOUT_ENTITIES.clear();
            return;
        }

        if (!ModularNuclearConfig.enableFalloutVisualEffect) {
            targetIntensity = 0.0f;
            currentIntensity = 0.0f;
            return;
        }

        // Periodic rescan to catch any fallout entities loaded outside immediate tracking
        if (mc.theWorld.getTotalWorldTime() % 20 == 0) {
            for (Object obj : mc.theWorld.loadedEntityList) {
                if (obj instanceof EntityMeltdownFallout fallout && !fallout.isDead) {
                    CLIENT_FALLOUT_ENTITIES.add(fallout);
                }
            }
        }

        // Clean up dead or unloaded entities
        CLIENT_FALLOUT_ENTITIES.removeIf(e -> e.isDead || e.worldObj != mc.theWorld);

        if (CLIENT_FALLOUT_ENTITIES.isEmpty()) {
            targetIntensity = 0.0f;
        } else {
            double maxDist = ModularNuclearConfig.falloutEffectMaxDistance;
            double maxDistSq = maxDist * maxDist;
            double minDistanceSq = Double.MAX_VALUE;

            for (EntityMeltdownFallout fallout : CLIENT_FALLOUT_ENTITIES) {
                double distSq = mc.thePlayer.getDistanceSqToEntity(fallout);
                if (distSq < minDistanceSq) {
                    minDistanceSq = distSq;
                }
            }

            if (minDistanceSq <= maxDistSq) {
                double dist = Math.sqrt(minDistanceSq);
                boolean fullHazmat = isWearingFullHazmat(mc.thePlayer);
                targetIntensity = computeIntensity(dist, maxDist, fullHazmat);
            } else {
                targetIntensity = 0.0f;
            }
        }

        // Smooth transition over ~6-7 ticks
        currentIntensity += (targetIntensity - currentIntensity) * 0.15f;
        if (Math.abs(currentIntensity - targetIntensity) < 0.001f) {
            currentIntensity = targetIntensity;
        }
    }

    @SubscribeEvent
    public void onRenderOverlayPre(RenderGameOverlayEvent.Pre event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (currentIntensity <= 0.001f) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;

        if (OpenGlHelper.shadersSupported && OpenGlHelper.isFramebufferEnabled()) {
            renderShader(mc, event.partialTicks);
        } else {
            renderFallbackOverlay(mc, event.resolution);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        CLIENT_FALLOUT_ENTITIES.clear();
        currentIntensity = 0.0f;
        targetIntensity = 0.0f;
    }

    private void initShader(Minecraft mc) {
        if (shaderLoadFailed) return;
        try {
            ResourceLocation shaderLoc = new ResourceLocation("shaders/post/fallout.json");
            shaderGroup = new ShaderGroup(
                mc.getTextureManager(),
                mc.getResourceManager(),
                mc.getFramebuffer(),
                shaderLoc);
            lastDisplayWidth = mc.displayWidth;
            lastDisplayHeight = mc.displayHeight;
            shaderGroup.createBindFramebuffers(lastDisplayWidth, lastDisplayHeight);
        } catch (Throwable t) {
            ModularNuclear.LOG.error("Failed to initialize fallout post-processing shader: " + t.getMessage(), t);
            shaderLoadFailed = true;
            if (shaderGroup != null) {
                try {
                    shaderGroup.deleteShaderGroup();
                } catch (Throwable ignored) {}
                shaderGroup = null;
            }
        }
    }

    private void updateUniforms(float partialTicks) {
        if (shaderGroup == null) return;
        try {
            List<?> shaders = ReflectionHelper
                .getPrivateValue(ShaderGroup.class, shaderGroup, "listShaders", "field_148031_d", "d");
            if (shaders != null && !shaders.isEmpty()) {
                Shader falloutPass = (Shader) shaders.get(0);
                ShaderUniform intensityUni = falloutPass.getShaderManager()
                    .func_147991_a("Intensity");
                if (intensityUni != null) {
                    intensityUni.func_148090_a(currentIntensity);
                }
                ShaderUniform seedUni = falloutPass.getShaderManager()
                    .func_147991_a("Seed");
                if (seedUni != null) {
                    seedUni.func_148090_a((float) (Math.random() * 1000.0));
                }
            }
        } catch (Throwable ignored) {}
    }

    private void renderShader(Minecraft mc, float partialTicks) {
        if (shaderGroup == null) {
            initShader(mc);
            if (shaderGroup == null) {
                return;
            }
        }

        if (mc.displayWidth != lastDisplayWidth || mc.displayHeight != lastDisplayHeight) {
            lastDisplayWidth = mc.displayWidth;
            lastDisplayHeight = mc.displayHeight;
            shaderGroup.createBindFramebuffers(lastDisplayWidth, lastDisplayHeight);
        }

        updateUniforms(partialTicks);

        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            shaderGroup.loadShaderGroup(partialTicks);
        } catch (Throwable t) {
            ModularNuclear.LOG.warn("Failed to execute fallout shader: " + t.getMessage());
        } finally {
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            mc.getFramebuffer()
                .bindFramebuffer(true);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private void renderFallbackOverlay(Minecraft mc, ScaledResolution res) {
        int width = res.getScaledWidth();
        int height = res.getScaledHeight();

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        float darkAlpha = Math.min(0.65f, currentIntensity * 0.5f);
        GL11.glColor4f(0.02f, 0.02f, 0.02f, darkAlpha);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertex(0.0, height, -90.0);
        tessellator.addVertex(width, height, -90.0);
        tessellator.addVertex(width, 0.0, -90.0);
        tessellator.addVertex(0.0, 0.0, -90.0);
        tessellator.draw();

        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glPopMatrix();
    }
}
