package com.gtnewhorizons.modularnuclear.client.renderer;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.modularnuclear.common.block.BlockControlRodHousing;
import com.gtnewhorizons.modularnuclear.common.metatileentity.pipe.MTECoolantPipe;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws the control rod housing as a straight small coolant pipe along its axis: the three boxes of the bevelled
 * cross-section, each drawn twice, the steel pipe and then the insulation over it.
 */
@SideOnly(Side.CLIENT)
public class RenderBlockControlRodHousing implements ISimpleBlockRenderingHandler {

    public static void init() {
        BlockControlRodHousing.renderID = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new RenderBlockControlRodHousing());
    }

    private static List<AxisAlignedBB> boxes(int meta) {
        float t = BlockControlRodHousing.THICKNESS;
        float min = (1.0F - t) / 2.0F, max = 1.0F - min;
        float b = BlockControlRodHousing.bezel();
        return switch (meta) {
            case BlockControlRodHousing.AXIS_X -> MTECoolantPipe.getBeveledSubBoxesX(0.0F, 1.0F, min, max, b);
            case BlockControlRodHousing.AXIS_Z -> MTECoolantPipe.getBeveledSubBoxesZ(0.0F, 1.0F, min, max, b);
            default -> MTECoolantPipe.getBeveledSubBoxesY(0.0F, 1.0F, min, max, b);
        };
    }

    private static IIcon insulation() {
        return MTECoolantPipe.INSULATION_OVERLAY_ICON.getIcon();
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        IIcon insulation = insulation();
        for (AxisAlignedBB box : boxes(world.getBlockMetadata(x, y, z))) {
            renderer.setRenderBounds(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            renderer.renderStandardBlock(block, x, y, z);
            if (insulation != null) {
                renderer.setOverrideBlockTexture(insulation);
                renderer.renderStandardBlock(block, x, y, z);
                renderer.clearOverrideBlockTexture();
            }
        }
        renderer.setRenderBounds(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        return true;
    }

    @Override
    public void renderInventoryBlock(Block block, int meta, int modelId, RenderBlocks renderer) {
        int color = block.getRenderColor(meta);
        GL11.glColor4f(((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, 1.0F);
        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
        IIcon insulation = insulation();
        for (AxisAlignedBB box : boxes(BlockControlRodHousing.AXIS_Y)) {
            renderer.setRenderBounds(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            renderInventoryBox(block, renderer, null);
            if (insulation != null) renderInventoryBox(block, renderer, insulation);
        }
        GL11.glTranslatef(0.5F, 0.5F, 0.5F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        renderer.setRenderBounds(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
    }

    /** The current render bounds' six faces, with the block's own icons or the given one. */
    private static void renderInventoryBox(Block block, RenderBlocks renderer, IIcon override) {
        Tessellator t = Tessellator.instance;
        int meta = BlockControlRodHousing.AXIS_Y;
        t.startDrawingQuads();
        t.setNormal(0.0F, -1.0F, 0.0F);
        renderer.renderFaceYNeg(block, 0, 0, 0, override != null ? override : block.getIcon(0, meta));
        t.setNormal(0.0F, 1.0F, 0.0F);
        renderer.renderFaceYPos(block, 0, 0, 0, override != null ? override : block.getIcon(1, meta));
        t.setNormal(0.0F, 0.0F, -1.0F);
        renderer.renderFaceZNeg(block, 0, 0, 0, override != null ? override : block.getIcon(2, meta));
        t.setNormal(0.0F, 0.0F, 1.0F);
        renderer.renderFaceZPos(block, 0, 0, 0, override != null ? override : block.getIcon(3, meta));
        t.setNormal(-1.0F, 0.0F, 0.0F);
        renderer.renderFaceXNeg(block, 0, 0, 0, override != null ? override : block.getIcon(4, meta));
        t.setNormal(1.0F, 0.0F, 0.0F);
        renderer.renderFaceXPos(block, 0, 0, 0, override != null ? override : block.getIcon(5, meta));
        t.draw();
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return BlockControlRodHousing.renderID;
    }
}
