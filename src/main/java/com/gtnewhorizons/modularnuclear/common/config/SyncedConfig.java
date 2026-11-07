package com.gtnewhorizons.modularnuclear.common.config;

import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.gtnewhorizons.modularnuclear.CoolantLoops;
import com.gtnewhorizons.modularnuclear.ModularNuclear;
import com.gtnewhorizons.modularnuclear.network.PacketSyncConfig;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A client on a dedicated server uses the server's configuration (fuels, components, coolant loop data, the Forge
 * config's lists), so NEI, tooltips and GUIs show what the server does; on leaving it goes back to its own files.
 * Singleplayer and LAN games share one configuration with their integrated server, so nothing is sent there.
 */
public final class SyncedConfig {

    private static Map<String, Object> localGameplay;
    private static boolean usingServer;

    private SyncedConfig() {}

    /** What a dedicated server sends a joining player. */
    public static PacketSyncConfig snapshot() {
        return new PacketSyncConfig(
            ReactorDataRegistry.overrideTexts(),
            CoolantLoopDataRegistry.fileTexts(),
            ModularNuclearConfig.gameplaySettings());
    }

    /** Switches this client to a server's configuration. */
    public static synchronized void useServer(PacketSyncConfig config) {
        if (!usingServer) localGameplay = ModularNuclearConfig.gameplaySettings();
        ReactorDataRegistry.loadFromTexts(config.reactorFiles);
        CoolantLoopDataRegistry.loadFromTexts(config.coolantLoopFiles);
        ModularNuclearConfig.applyGameplaySettings(config.gameplay);
        usingServer = true;
        ModularNuclear.LOG.info("Using the server's configuration");
    }

    /** Switches this client back to its own configuration files. */
    public static synchronized void useLocal() {
        if (!usingServer) return;
        ReactorDataRegistry.reload();
        CoolantLoopDataRegistry.loadFromDir();
        ModularNuclearConfig.applyGameplaySettings(localGameplay);
        usingServer = false;
        ModularNuclear.LOG.info("Using the local configuration again");
    }

    public static boolean isUsingServer() {
        return usingServer;
    }

    /** Server side: each player joining a dedicated server gets its configuration. */
    public static class ServerEvents {

        @SubscribeEvent
        public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null && server.isDedicatedServer() && event.player instanceof EntityPlayerMP player) {
                CoolantLoops.network.sendTo(snapshot(), player);
            }
        }
    }

    /** Client side: leaving a server restores the local configuration. */
    @SideOnly(Side.CLIENT)
    public static class ClientEvents {

        @SubscribeEvent
        public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
            net.minecraft.client.Minecraft.getMinecraft()
                .func_152344_a(SyncedConfig::useLocal);
        }
    }
}
