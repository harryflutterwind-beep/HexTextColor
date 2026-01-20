package com.example.examplemod;

import com.example.examplemod.item.ItemGemIcons;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;

import com.example.examplemod.server.DropTagHandler;
import com.example.examplemod.server.WearableRightClickEquip;
import com.example.examplemod.server.HexOrbEffectsController;
import com.example.examplemod.server.HexDBCBridgeDamageApplier; // <-- add this
import com.example.examplemod.server.HexDBCProcDamageProvider;

public class CommonProxy {
    /**
     * Keep a reference so other code can use it if needed.
     * Registration MUST happen on both client + dedicated server.
     */
    public static Item HEX_GEM_ICONS;

    public void preInit() {
        // Ensure item is registered on BOTH sides so NEI/cheat-give works on dedicated servers.
        ensureGemIconsRegistered();
    }

    public void init() {
        // In case the mod only calls proxy.init() (some setups do), keep this idempotent.
        ensureGemIconsRegistered();

        // Runs on both physical server and integrated server (logic side)
        MinecraftForge.EVENT_BUS.register(new DropTagHandler());
        MinecraftForge.EVENT_BUS.register(new WearableRightClickEquip());
        MinecraftForge.EVENT_BUS.register(new HexOrbEffectsController());

        // Route proc damage through JRMCore for players (DBC Body damage)
        HexOrbEffectsController.API.setDamageApplier(new HexDBCBridgeDamageApplier());
        HexOrbEffectsController.PROC_DAMAGE_PROVIDER = new HexDBCProcDamageProvider();

    }

    public void postInit() {}

    private static void ensureGemIconsRegistered() {
        if (HEX_GEM_ICONS != null) return;

        // Try a few likely registry keys (domain depends on modid / active container).
        Item existing = null;
        try {
            Object o;
            o = Item.itemRegistry.getObject("hexcolorcodes:hex_gem");
            if (o instanceof Item) existing = (Item) o;
            if (existing == null) {
                o = Item.itemRegistry.getObject("examplemod:hex_gem");
                if (o instanceof Item) existing = (Item) o;
            }
            if (existing == null) {
                o = Item.itemRegistry.getObject("hex_gem");
                if (o instanceof Item) existing = (Item) o;
            }
        } catch (Throwable ignored) {
        }

        if (existing != null) {
            HEX_GEM_ICONS = existing;
            return;
        }

        // Not registered yet -> register now (common-side).
        try {
            HEX_GEM_ICONS = new ItemGemIcons();
            GameRegistry.registerItem(HEX_GEM_ICONS, "hex_gem");
        } catch (Throwable ignored) {
            // Intentionally no console spam.
        }
    }
}
