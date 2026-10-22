package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class NuclearReactorCustomNameTest {

    @BeforeAll
    public static void setUp() {
        TestEnvironmentHelper.init();
    }

    @Test
    public void testDefaultNameAndNoCustomName() {
        MTENuclearReactor reactor = new MTENuclearReactor("multimachine.nuclearreactor");
        assertFalse(reactor.hasCustomName());
        assertNull(reactor.getCustomName());
        assertFalse(reactor.hasCustomInventoryName());

        String baseName = reactor.getBaseLocalName();
        assertNotNull(baseName);
        assertEquals(baseName, reactor.getLocalName());
        assertEquals(baseName, reactor.getInventoryName());
    }

    @Test
    public void testSetCustomName() {
        MTENuclearReactor reactor = new MTENuclearReactor("multimachine.nuclearreactor");
        reactor.setCustomName("Reactor Unit #1");

        assertTrue(reactor.hasCustomName());
        assertEquals("Reactor Unit #1", reactor.getCustomName());
        assertTrue(reactor.hasCustomInventoryName());
        assertEquals("Reactor Unit #1", reactor.getLocalName());
        assertEquals("Reactor Unit #1", reactor.getInventoryName());

        // Clearing custom name with empty string or null
        reactor.setCustomName("");
        assertFalse(reactor.hasCustomName());
        assertNull(reactor.getCustomName());
        assertEquals(reactor.getBaseLocalName(), reactor.getLocalName());

        reactor.setCustomName("   ");
        assertFalse(reactor.hasCustomName());
        assertNull(reactor.getCustomName());
    }

    @Test
    public void testNBTSerializationAndAnvilLoading() {
        MTENuclearReactor reactor1 = new MTENuclearReactor("multimachine.nuclearreactor");
        reactor1.setCustomName("Primary Core Alpha");

        NBTTagCompound tag = new NBTTagCompound();
        reactor1.saveNBTData(tag);

        assertTrue(tag.hasKey("mCustomName"));
        assertEquals("Primary Core Alpha", tag.getString("mCustomName"));
        assertTrue(tag.hasKey("customName"));
        assertEquals("Primary Core Alpha", tag.getString("customName"));
        assertTrue(tag.hasKey("display"));
        assertEquals(
            "Primary Core Alpha",
            tag.getCompoundTag("display")
                .getString("Name"));

        // Load into fresh reactor
        MTENuclearReactor reactor2 = new MTENuclearReactor("multimachine.nuclearreactor");
        reactor2.loadNBTData(tag);
        assertTrue(reactor2.hasCustomName());
        assertEquals("Primary Core Alpha", reactor2.getCustomName());
        assertEquals("Primary Core Alpha", reactor2.getLocalName());

        // Test loading specifically from vanilla anvil NBT format (only display.Name)
        NBTTagCompound anvilTag = new NBTTagCompound();
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", "Chernobyl Unit 4");
        anvilTag.setTag("display", display);

        MTENuclearReactor reactor3 = new MTENuclearReactor("multimachine.nuclearreactor");
        reactor3.loadNBTData(anvilTag);
        assertTrue(reactor3.hasCustomName());
        assertEquals("Chernobyl Unit 4", reactor3.getCustomName());
        assertEquals("Chernobyl Unit 4", reactor3.getLocalName());
    }

    @Test
    public void testSetItemNBT() {
        MTENuclearReactor reactor = new MTENuclearReactor("multimachine.nuclearreactor");
        reactor.setCustomName("Fukushima Unit 1");

        NBTTagCompound itemTag = new NBTTagCompound();
        reactor.setItemNBT(itemTag);

        assertEquals("Fukushima Unit 1", itemTag.getString("mCustomName"));
        assertEquals("Fukushima Unit 1", itemTag.getString("customName"));
        assertEquals(
            "Fukushima Unit 1",
            itemTag.getCompoundTag("display")
                .getString("Name"));

        ItemStack stack = new ItemStack(net.minecraft.init.Blocks.stone);
        stack.setTagCompound(itemTag);
        assertEquals("Fukushima Unit 1", MTENuclearReactor.getCustomNameFromStack(stack));
    }

    @Test
    public void testTooltipModificationWhenRenamedVsStandard() {
        MTENuclearReactor reactor = new MTENuclearReactor("multimachine.nuclearreactor");
        String baseName = reactor.getBaseLocalName();

        // 1. Unnamed stack
        ItemStack unnamedStack = new ItemStack(net.minecraft.init.Blocks.stone);
        List<String> unnamedTooltip = new ArrayList<>();
        unnamedTooltip.add(baseName);
        unnamedTooltip.add("Thermonuclear Reactor");

        reactor.addAdditionalTooltipInformation(unnamedStack, unnamedTooltip);
        assertEquals(2, unnamedTooltip.size());
        assertEquals(baseName, unnamedTooltip.get(0));
        assertEquals("Thermonuclear Reactor", unnamedTooltip.get(1));

        // 2. Renamed stack (e.g. from Anvil)
        ItemStack renamedStack = new ItemStack(net.minecraft.init.Blocks.stone);
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", "Reactor Core Gamma");
        tag.setTag("display", display);
        renamedStack.setTagCompound(tag);

        List<String> renamedTooltip = new ArrayList<>();
        renamedTooltip.add("Reactor Core Gamma");
        renamedTooltip.add("Thermonuclear Reactor");

        reactor.addAdditionalTooltipInformation(renamedStack, renamedTooltip);
        assertEquals(3, renamedTooltip.size());
        assertEquals(
            EnumChatFormatting.ITALIC + "Reactor Core Gamma" + EnumChatFormatting.RESET,
            renamedTooltip.get(0));
        assertEquals(EnumChatFormatting.GRAY + baseName, renamedTooltip.get(1));
        assertEquals("Thermonuclear Reactor", renamedTooltip.get(2));
    }

    @Test
    public void testStreamSynchronization() {
        MTENuclearReactor serverReactor = new MTENuclearReactor("multimachine.nuclearreactor");
        serverReactor.setCustomName("Remote Synced Unit");

        ByteBuf buf = Unpooled.buffer();
        serverReactor.writeToStream(buf);

        MTENuclearReactor clientReactor = new MTENuclearReactor("multimachine.nuclearreactor");
        clientReactor.readFromStream(buf);

        assertTrue(clientReactor.hasCustomName());
        assertEquals("Remote Synced Unit", clientReactor.getCustomName());
        assertEquals("Remote Synced Unit", clientReactor.getLocalName());
    }
}
