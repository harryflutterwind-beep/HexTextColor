//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.example.examplemod;

import com.example.examplemod.command.RarityLoreCommand;
import com.example.examplemod.command.SetDamageCommand;
import com.example.examplemod.dragon.CommandDragonLoot;
import com.example.examplemod.dragon.DragonCommand;
import com.example.examplemod.dragon.DragonEvents;
import com.example.examplemod.dragon.DragonLootHandler;
import com.example.examplemod.item.ItemTabIcon;
import com.example.examplemod.item.ItemTransformWeapon;
import com.example.examplemod.command.LorePagesCommand;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraftforge.common.MinecraftForge;

@Mod(
        modid = "hexcolorcodes",
        name = "HexColorCodes",
        version = "1.5.5",
        guiFactory = "com.example.examplemod.client.ModGuiFactory"
)
public class ExampleMod {

    public static final CreativeTabHex HEX_TAB = new CreativeTabHex();
    public static ItemTransformWeapon TRANSFORM_WEAPON;
    public static ItemTabIcon TAB_ICON_ITEM;

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

        TRANSFORM_WEAPON = new ItemTransformWeapon();
        TRANSFORM_WEAPON.setUnlocalizedName("hex_transform_weapon");
        TRANSFORM_WEAPON.setCreativeTab(HEX_TAB);
        TRANSFORM_WEAPON.setMaxStackSize(1);
        GameRegistry.registerItem(TRANSFORM_WEAPON, "hex_transform_weapon");

        TAB_ICON_ITEM = new ItemTabIcon();
        GameRegistry.registerItem(TAB_ICON_ITEM, "hex_tab_icon");
        HEX_TAB.setIcon(TAB_ICON_ITEM);

        // No client classes here
        proxy.preInit();
    }

    @EventHandler
    public void init(FMLInitializationEvent e) {
        // Let the proxy handle side-specific stuff
        proxy.init();

        // These handlers should be *common* (no client imports)
        MinecraftForge.EVENT_BUS.register(new DragonEvents());
        MinecraftForge.EVENT_BUS.register(new DragonLootHandler());
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent e) {
        // Commands are server-side only, all safe
        e.registerServerCommand(new RarityLoreCommand());
        e.registerServerCommand(new SetDamageCommand());
        e.registerServerCommand(new DragonCommand());
        e.registerServerCommand(new CommandDragonLoot());
        e.registerServerCommand(new com.example.examplemod.command.BeamTestCommand());
        e.registerServerCommand(new com.example.examplemod.command.HexHelpCommand());
        e.registerServerCommand(new LorePagesCommand());

        System.out.println("[HexColorCodes] Commands registered: /rarity, /setdamage, /dragon, /dragonloot, hexhelp");
    }
}
