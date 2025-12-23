package com.example.examplemod;

import net.minecraftforge.common.MinecraftForge;
import com.example.examplemod.server.DropTagHandler;

public class CommonProxy {
    public void preInit() {}

    public void init() {
        // Runs on both physical server and integrated server (logic side)
        // DropTagHandler itself only writes on server side where needed.
        MinecraftForge.EVENT_BUS.register(new DropTagHandler());
    }

    public void postInit() {}
}
