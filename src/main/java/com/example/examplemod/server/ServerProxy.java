package com.example.examplemod.server;

import com.example.examplemod.CommonProxy;
import net.minecraftforge.common.MinecraftForge;

public class ServerProxy extends CommonProxy {
    @Override public void init() {
        super.init();
        // server-only event handler
        MinecraftForge.EVENT_BUS.register(new ChatHexServer());
    }
}
