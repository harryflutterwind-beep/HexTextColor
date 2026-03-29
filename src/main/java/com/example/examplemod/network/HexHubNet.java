package com.example.examplemod.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.player.EntityPlayerMP;

public final class HexHubNet {

    public static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel("hexhubnet");

    private HexHubNet() {}

    public static void init() {
        CHANNEL.registerMessage(OpenHexHubMessage.Handler.class, OpenHexHubMessage.class, 0, Side.CLIENT);
    }

    public static void openFor(EntityPlayerMP player) {
        CHANNEL.sendTo(new OpenHexHubMessage(), player);
    }
}