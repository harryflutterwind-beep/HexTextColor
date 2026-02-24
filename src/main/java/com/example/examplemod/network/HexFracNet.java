package com.example.examplemod.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * SimpleNetworkWrapper setup for Fractured HUD/actions + Shadow Burn sync.
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

        // 0: client -> server fractured actions
        CHANNEL.registerMessage(PacketFracturedAction.Handler.class, PacketFracturedAction.class, 0, Side.SERVER);

        // 1: server -> client shadow-burn ticks (for custom flame rendering + overlay cancel)
        CHANNEL.registerMessage(PacketShadowBurnSync.Handler.class, PacketShadowBurnSync.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(PacketBlackflameSprayFX.Handler.class, PacketBlackflameSprayFX.class, 2, Side.CLIENT);

        // 3: client -> server selection sync (TAB cycling)
        CHANNEL.registerMessage(PacketOrbSelect.Handler.class, PacketOrbSelect.class, 3, Side.SERVER);

    }
}
