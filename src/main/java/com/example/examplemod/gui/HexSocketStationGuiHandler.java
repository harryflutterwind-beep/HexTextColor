package com.example.examplemod.gui;

import cpw.mods.fml.common.network.IGuiHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Common-side GUI handler (safe for dedicated servers).
 *
 * IDs:
 *  - 5510: Socket Station
 *  - 5511: Socket Opener
 */
public class HexSocketStationGuiHandler implements IGuiHandler {

    private static final Logger LOG = LogManager.getLogger("HexSocketGUI");

    public static final int GUI_ID_SOCKET_STATION = 5510;
    public static final int GUI_ID_SOCKET_OPENER  = 5511;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == GUI_ID_SOCKET_STATION) {
            // ContainerHexSocketStation is defined server/common-side.
            // Your current container ctor is (EntityPlayer), so don't pass InventoryPlayer.
            return new ContainerHexSocketStation(player);
        }
        if (id == GUI_ID_SOCKET_OPENER) {
            return new ContainerHexSocketOpener(player.inventory, player);
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        try {
            if (id == GUI_ID_SOCKET_STATION) {
                return newClientGui(
                        "com.example.examplemod.client.gui.GuiHexSocketStation",
                        "com.example.examplemod.gui.ContainerHexSocketStation",
                        player
                );
            }
            if (id == GUI_ID_SOCKET_OPENER) {
                return newClientGui(
                        "com.example.examplemod.client.gui.GuiHexSocketOpener",
                        "com.example.examplemod.gui.ContainerHexSocketOpener",
                        player
                );
            }
        } catch (Throwable t) {
            LOG.error("[HexSocket] Failed to create client GUI (id={})", id, t);
        }
        return null;
    }

    /**
     * Dedicated-server safe: uses reflection so this class can live in common code.
     * Supports either GUI ctors:
     *  - (EntityPlayer)
     *  - (InventoryPlayer, EntityPlayer)
     *  - (Container)
     *  - (Container, EntityPlayer)
     */
    private static Object newClientGui(String guiClassName, String containerClassName, EntityPlayer player) throws Exception {
        Class<?> guiCls = Class.forName(guiClassName);

        // 1) Try the most common simple signatures first.
        try {
            return guiCls.getConstructor(EntityPlayer.class).newInstance(player);
        } catch (NoSuchMethodException ignored) {}

        try {
            return guiCls.getConstructor(InventoryPlayer.class, EntityPlayer.class).newInstance(player.inventory, player);
        } catch (NoSuchMethodException ignored) {}

        // 2) Build a container instance (client mirrors server) and try container-based GUI ctors.
        Object containerObj = null;
        try {
            Class<?> contCls = Class.forName(containerClassName);
            try {
                containerObj = contCls.getConstructor(EntityPlayer.class).newInstance(player);
            } catch (NoSuchMethodException ignored) {
                containerObj = contCls.getConstructor(InventoryPlayer.class, EntityPlayer.class).newInstance(player.inventory, player);
            }
        } catch (Throwable t) {
            // container creation is best-effort; GUI might not need it
        }

        if (containerObj instanceof Container) {
            Container c = (Container) containerObj;
            try {
                return guiCls.getConstructor(Container.class).newInstance(c);
            } catch (NoSuchMethodException ignored) {}
            try {
                return guiCls.getConstructor(Container.class, EntityPlayer.class).newInstance(c, player);
            } catch (NoSuchMethodException ignored) {}
        }

        throw new NoSuchMethodException("No compatible constructor found for " + guiClassName);
    }
}
