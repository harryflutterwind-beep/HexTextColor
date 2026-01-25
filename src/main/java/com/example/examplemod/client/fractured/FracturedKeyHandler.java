package com.example.examplemod.client.fractured;

import com.example.examplemod.network.PacketFracturedAction;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.lwjgl.input.Keyboard;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Client-side key handler for Fractured + Solar CTRL double/triple tap actions.
 *
 * Actions (server packet):
 *  1 = Fractured small blast (consume 1 shard)
 *  2 = Fractured big blast  (consume 5 shards)
 *  3 = Solar Beam          (consume 10% radiance)
 *  4 = Super Solar Beam    (consume 80% radiance)
 *
 * NOTE:
 * - Beam *rendering* is now handled via PacketSolarBeamFX -> ItemBeamRenderer transient beams,
 *   so this class only needs to send the action packet.
 */
public class FracturedKeyHandler {

    // Shared orb identity
    private static final String TAG_PROFILE    = "HexOrbProfile";
    private static final String TAG_ROLLED     = "HexOrbRolled";

    // Fractured
    private static final String TAG_SHARDS     = "HexFracShards";
    private static final int MAX_SHARDS        = 5;

    // Light (Solar-type) orb tags
    private static final String TAG_LIGHT_TYPE = "HexLightType";
    private static final String TAG_LIGHT_RAD  = "HexLightRadiance";

    // Radiance costs (0..100)
    private static final int SOLAR_COST_BEAM   = 10;
    private static final int SOLAR_COST_SUPER  = 80;

    // Tap window in ms
    private static final int TAP_WINDOW_MS = 350;

    private final KeyBinding keyCtrlTap =
            new KeyBinding("<#ff007e>Ability (double/triple CTRL)</#>", Keyboard.KEY_LCONTROL, "HexColorText");

    private long lastTapMs = 0L;
    private int tapCount = 0;

    // When an "upgrade" is possible (max shards / enough radiance), we hold the double-tap
    // briefly so a third tap can upgrade into the bigger action.
    private boolean pendingAction = false;
    private long pendingAtMs = 0L;
    private int pendingNormalAction = 0;
    private int pendingUpgradeAction = 0;

    public FracturedKeyHandler() {
        ClientRegistry.registerKeyBinding(keyCtrlTap);
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (Minecraft.getMinecraft() == null) return;
        if (Minecraft.getMinecraft().thePlayer == null) return;

        // KeyBinding "just pressed"
        if (!keyCtrlTap.isPressed()) return;

        long now = System.currentTimeMillis();
        if (now - lastTapMs <= TAP_WINDOW_MS) {
            tapCount++;
        } else {
            tapCount = 1;
        }
        lastTapMs = now;

        // If we are waiting for a possible triple-tap upgrade, a 3rd tap triggers immediately.
        if (pendingAction && tapCount >= 3) {
            fireAction(pendingUpgradeAction);
            clearPending();
            tapCount = 0;
            return;
        }

        // If we reached double-tap, decide what to do.
        if (tapCount >= 2) {
            tapCount = 0; // consume the double-tap

            EntityPlayer p = Minecraft.getMinecraft().thePlayer;
            ItemStack orb = findEquippedOrb(p);
            if (orb == null) return;

            ActionChoice choice = chooseActionForOrb(orb);
            if (choice == null) return;

            if (choice.canUpgrade) {
                // Delay slightly to allow a 3rd tap to upgrade.
                pendingAction = true;
                pendingAtMs = now;
                pendingNormalAction = choice.normalAction;
                pendingUpgradeAction = choice.upgradeAction;
            } else {
                fireAction(choice.normalAction);
            }
        }
    }
// ---------------------------------------------------------------------
// Network helper (no hard HexFracNet.NET dependency)
// ---------------------------------------------------------------------

    private static volatile SimpleNetworkWrapper CACHED_NET;

    private static SimpleNetworkWrapper resolveNetWrapper() {
        if (CACHED_NET != null) return CACHED_NET;
        try {
            Class<?> netCls = Class.forName("com.example.examplemod.network.HexFracNet");

            String[] fields = {"NET","CHANNEL","WRAPPER","NETWORK","INSTANCE"};
            for (String fn : fields) {
                try {
                    Field f;
                    try { f = netCls.getField(fn); }
                    catch (Throwable t) { f = netCls.getDeclaredField(fn); }
                    f.setAccessible(true);
                    Object v = Modifier.isStatic(f.getModifiers()) ? f.get(null) : null;
                    if (v instanceof SimpleNetworkWrapper) {
                        return CACHED_NET = (SimpleNetworkWrapper) v;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void sendToServerSafe(IMessage msg) {
        try {
            SimpleNetworkWrapper net = resolveNetWrapper();
            if (net != null && msg != null) {
                net.sendToServer(msg);
            }
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!pendingAction) return;

        long now = System.currentTimeMillis();
        if (now - pendingAtMs > TAP_WINDOW_MS) {
            // Window expired -> commit the normal action
            fireAction(pendingNormalAction);
            clearPending();
        }
    }

    private void clearPending() {
        pendingAction = false;
        pendingAtMs = 0L;
        pendingNormalAction = 0;
        pendingUpgradeAction = 0;
    }

    private void fireAction(int actionId) {
        try {
            sendToServerSafe(new PacketFracturedAction(actionId));
        } catch (Throwable ignored) {}
    }

    private static class ActionChoice {
        final int normalAction;
        final int upgradeAction;
        final boolean canUpgrade;

        ActionChoice(int normalAction, int upgradeAction, boolean canUpgrade) {
            this.normalAction = normalAction;
            this.upgradeAction = upgradeAction;
            this.canUpgrade = canUpgrade;
        }
    }

    private static ActionChoice chooseActionForOrb(ItemStack orb) {
        if (orb == null) return null;

        NBTTagCompound tag = orb.getTagCompound();
        if (tag == null) return null;

        // Ensure it is one of our rolled orbs (prevents random items from firing packets)
        if (!tag.getBoolean(TAG_ROLLED)) return null;

        // Solar?
        if (tag.hasKey(TAG_LIGHT_TYPE)) {
            String type = tag.getString(TAG_LIGHT_TYPE);
            if (type != null && "Solar".equalsIgnoreCase(type.trim())) {
                int rad = tag.getInteger(TAG_LIGHT_RAD);
                boolean canUpgrade = (rad >= SOLAR_COST_SUPER);
                boolean canNormal = (rad >= SOLAR_COST_BEAM);
                if (!canNormal) return null;
                return new ActionChoice(3, 4, canUpgrade);
            }
        }

        // Fractured?
        if (tag.hasKey(TAG_SHARDS)) {
            int shards = tag.getInteger(TAG_SHARDS);
            boolean canUpgrade = (shards >= MAX_SHARDS);
            boolean canNormal = (shards >= 1);
            if (!canNormal) return null;
            return new ActionChoice(1, 2, canUpgrade);
        }

        return null;
    }

    private static ItemStack findEquippedOrb(EntityPlayer p) {
        if (p == null) return null;

        // Main hand
        ItemStack held = p.getCurrentEquippedItem();
        if (isOurOrb(held)) return held;

        // Armor slots
        for (int i = 0; i < 4; i++) {
            ItemStack a = p.inventory.armorInventory[i];
            if (isOurOrb(a)) return a;
        }
        return null;
    }

    private static boolean isOurOrb(ItemStack s) {
        if (s == null) return false;
        NBTTagCompound tag = s.getTagCompound();
        if (tag == null) return false;
        if (!tag.hasKey(TAG_PROFILE)) return false;
        return tag.getBoolean(TAG_ROLLED);
    }
}