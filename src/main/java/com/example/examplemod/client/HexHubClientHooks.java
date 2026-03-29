package com.example.examplemod.client;

import com.example.examplemod.client.gui.GuiHubMenu;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

@SideOnly(Side.CLIENT)
public class HexHubClientHooks {

    public static boolean OPEN_HUB_NEXT_TICK = false;

    private boolean openPending = false;
    private int openDelayTicks = 0;
    private boolean openedThisConnection = false;
    private boolean joinMessageSent = false;
    private boolean pendingJoinOpen = false;

    private void queueHubOpen(int delayTicks, boolean isJoinOpen) {
        openPending = true;
        openDelayTicks = delayTicks;
        pendingJoinOpen = isJoinOpen;
    }

    @SubscribeEvent
    public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        openedThisConnection = false;
        joinMessageSent = false;
        queueHubOpen(50, true);
    }

    @SubscribeEvent
    public void onClientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        openPending = false;
        openDelayTicks = 0;
        openedThisConnection = false;
        joinMessageSent = false;
        pendingJoinOpen = false;
        OPEN_HUB_NEXT_TICK = false;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc;

        if (event.phase != TickEvent.Phase.END) return;

        mc = Minecraft.getMinecraft();
        if (mc == null) return;
        if (mc.thePlayer == null) return;
        if (mc.theWorld == null) return;

        if (pendingJoinOpen && !openedThisConnection && !joinMessageSent) {
            mc.thePlayer.addChatMessage(new ChatComponentText("§d[HEX HUB] §fHub menu will auto-open shortly..."));
            joinMessageSent = true;
        }

        if (OPEN_HUB_NEXT_TICK) {
            OPEN_HUB_NEXT_TICK = false;
            queueHubOpen(12, false);
        }

        if (!openPending) return;
        if (mc.currentScreen != null) return;
        if (mc.currentScreen instanceof GuiHubMenu) return;

        if (openDelayTicks > 0) {
            openDelayTicks--;
            return;
        }

        openPending = false;

        if (pendingJoinOpen) {
            openedThisConnection = true;
            pendingJoinOpen = false;
        }

        mc.displayGuiScreen(new GuiHubMenu(mc.thePlayer));
    }
}