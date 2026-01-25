package com.example.examplemod.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * SimpleNetworkWrapper setup for Fractured-only HUD/actions.
 * MC/Forge 1.7.10-safe.
 */
public final class HexFracNet {

    // Keep this in sync with your @Mod(modid=...) value
    private static final String MODID = "hexcolorcodes";

    public static SimpleNetworkWrapper CHANNEL;

    private HexFracNet() {}

    public static void init() {
        if (CHANNEL != null) return;

        // Channel name must be <= 20 chars in 1.7.10
        CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(MODID + "_frac");

        // PacketFracturedAction is the IMessage; Handler is the IMessageHandler.
        CHANNEL.registerMessage(PacketFracturedAction.Handler.class, PacketFracturedAction.class, 0, Side.SERVER);
    }
}
