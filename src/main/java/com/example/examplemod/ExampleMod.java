//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.example.examplemod;

import com.example.examplemod.server.HexOrbRoller;
import cpw.mods.fml.common.FMLCommonHandler;
import com.example.examplemod.command.RarityLoreCommand;
import com.example.examplemod.command.SetDamageCommand;
import com.example.examplemod.item.ItemTabIcon;
import com.example.examplemod.item.ItemGemIcons;
import com.example.examplemod.command.LorePagesCommand;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.network.NetworkRegistry;
import com.example.examplemod.gui.HexSocketStationGuiHandler;
import net.minecraftforge.common.MinecraftForge;

@Mod(
        modid = "hexcolorcodes",
        name = "HexColorCodes",
        version = "1.5.5",
        guiFactory = "com.example.examplemod.client.ModGuiFactory"
)
public class ExampleMod {

    // ✅ Needed so commands can call p.openGui(ExampleMod.INSTANCE, ...)
    @Mod.Instance("hexcolorcodes")
    public static ExampleMod INSTANCE;

    public static final CreativeTabHex HEX_TAB = new CreativeTabHex();

    public static ItemTabIcon TAB_ICON_ITEM;
    public static ItemGemIcons GEM_ICONS;

    @SidedProxy(
            clientSide = "com.example.examplemod.client.ClientProxy",
            serverSide = "com.example.examplemod.server.ServerProxy"
    )
    public static CommonProxy proxy;

    public ExampleMod() {}

    @EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        // Config + items are safe on both sides
        ModConfig.load(e.getSuggestedConfigurationFile());

        // GUI handler registration (safe on both sides)
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new HexSocketStationGuiHandler());

        TAB_ICON_ITEM = new ItemTabIcon();
        GameRegistry.registerItem(TAB_ICON_ITEM, "hex_tab_icon");
        HEX_TAB.setIcon(TAB_ICON_ITEM);
// Gems: one item, many subtypes (meta variants) mapped to textures/gems/*.png
        GEM_ICONS = new ItemGemIcons();
        GameRegistry.registerItem(GEM_ICONS, "hex_gem");

// Rolls orb NBT stats one time when created/dropped/picked up
        HexOrbRoller roller = new HexOrbRoller(GEM_ICONS);

        MinecraftForge.EVENT_BUS.register(roller);
        FMLCommonHandler.instance().bus().register(roller);

// No client classes here
        proxy.preInit();
    }

    @EventHandler
    public void init(FMLInitializationEvent e) {
        // Let the proxy handle side-specific stuff
        proxy.init();

        // These handlers should be *common* (no client imports)
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent e) {
        // Commands are server-side only, all safe
        e.registerServerCommand(new RarityLoreCommand());
        e.registerServerCommand(new SetDamageCommand());
        e.registerServerCommand(new com.example.examplemod.command.BeamTestCommand());
        e.registerServerCommand(new com.example.examplemod.command.HexHelpCommand());
        e.registerServerCommand(new LorePagesCommand());
        e.registerServerCommand(new com.example.examplemod.command.HexSocketCommand());

        System.out.println("[HexColorCodes] Commands registered: /rarity, /setdamage, /dragon, /dragonloot, hexhelp");
    }
}
