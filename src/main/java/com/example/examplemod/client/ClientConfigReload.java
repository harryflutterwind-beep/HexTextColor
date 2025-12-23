package com.example.examplemod.client;

import com.example.examplemod.ModConfig;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class ClientConfigReload {
    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent e) {
        if ("hexcolorcodes".equals(e.modID)) {
            ModConfig.syncFromConfig();
            ModConfig.saveIfNeeded();
        }
    }
}
