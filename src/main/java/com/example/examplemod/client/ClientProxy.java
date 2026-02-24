// src/main/java/com/example/examplemod/client/ClientProxy.java
package com.example.examplemod.client;

import com.example.examplemod.client.fractured.FracturedClientBootstrap;
import com.example.examplemod.client.hud.*;
import com.example.examplemod.client.ShadowFireStitch;

import com.example.examplemod.client.fx.ShadowBurnClientFX;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import cpw.mods.fml.client.registry.RenderingRegistry;
import com.example.examplemod.entity.EntityHexBlast;
import com.example.examplemod.client.render.RenderHexBlast;

import net.minecraftforge.common.MinecraftForge;

@SideOnly(Side.CLIENT)
public class ClientProxy extends com.example.examplemod.CommonProxy {

    @Override
    public void init() {
        super.init();

        ChatHexHandler handler = new ChatHexHandler();
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance().bus().register(handler);
        LoreKeybinds.init(); // <-- correct method name

// LorePageController is NOT an event handler. Do not instantiate/register it.
        MinecraftForge.EVENT_BUS.register(new LoreTooltipPager());


        // ✅ IMPORTANT: register config change listener (GuiConfig "Done" -> apply/save)
        FMLCommonHandler.instance().bus().register(new ClientConfigReload());

        // Main menu big logo overlay
        // MinecraftForge.EVENT_BUS.register(new MainMenuLogoOverlay());

        try {
            ItemBeamRenderer.register();
        } catch (Throwable t) {
            System.out.println("[ItemBeamRenderer] Registration skipped: " + t);
        }

        HexSlotOverlay.register();

        // Orb HUD overlays (non-fractured)
        MinecraftForge.EVENT_BUS.register(new HexLightHudOverlay());
        MinecraftForge.EVENT_BUS.register(new HexChaoticHudOverlay());
        MinecraftForge.EVENT_BUS.register(new VoidOrbHudOverlay());
        MinecraftForge.EVENT_BUS.register(new DarkfirePillHudOverlay());
        MinecraftForge.EVENT_BUS.register(new SwirlyOrbHudOverlay());
        MinecraftForge.EVENT_BUS.register(new OverwriteHudOverlay());
        MinecraftForge.EVENT_BUS.register(new FirePillHudOverlay());
      // Shadow Fire client hooks (icons + shadow-burn overlay)
      try {
         ShadowBurnClientFX fx = new ShadowBurnClientFX();
         MinecraftForge.EVENT_BUS.register(new ShadowFireStitch());
         MinecraftForge.EVENT_BUS.register(fx);
         FMLCommonHandler.instance().bus().register(fx);
      } catch (Throwable var2) {
      }


        // Fractured-only HUD + CTRL tap handler
        FracturedClientBootstrap.init();
        RenderingRegistry.registerEntityRenderingHandler(
                EntityHexBlast.class,
                new RenderHexBlast()
        );
    }
}
