package com.example.examplemod.client.fractured;

import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraftforge.common.MinecraftForge;

public final class FracturedClientBootstrap {
    private static boolean inited = false;

    private FracturedClientBootstrap() {}

    public static void init() {
        if (inited) return;
        inited = true;

        // Key + HUD
        FMLCommonHandler.instance().bus().register(new FracturedKeyHandler());
        MinecraftForge.EVENT_BUS.register(new FracturedHudOverlay());
    }
}
