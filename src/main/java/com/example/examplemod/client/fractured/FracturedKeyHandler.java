package com.example.examplemod.client.fractured;

import com.example.examplemod.client.hud.VoidOrbHudOverlay;
import com.example.examplemod.network.PacketFracturedAction;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentText;
import org.lwjgl.input.Keyboard;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Client-side key handler for Fractured + Solar + Void(Null Shell) CTRL actions.
 *
 * Actions (server packet):
 *  1 = Fractured small blast (consume 1 shard)
 *  2 = Fractured big blast  (consume 5 shards)
 *  3 = Solar Beam          (consume 10% radiance)
 *  4 = Super Solar Beam    (consume 80% radiance)
 *
 * Void / Null Shell:
 *  5 = 25% dash (tap LCTRL; fires on release)
 *  6 = 50% defense (LCTRL + LSHIFT; fires immediately)
 *  7 = 100% push start (hold LCTRL past threshold)
 *  8 = 100% push release (release LCTRL after push started)
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

    // Null Shell input constants
    private static final int NS_ACTION_DASH   = 5;
    private static final int NS_ACTION_DEF    = 6;
    private static final int NS_ACTION_PUSH_S = 7;
    private static final int NS_ACTION_PUSH_R = 8;

    // How long (ms) you must hold after the 2nd tap to start charging push
    private static final long NS_PUSH_HOLD_START_MS = 350L;

    // Client-side key debug (prints when we send 5/6/7/8)
    private static final boolean NS_KEY_DEBUG = true;

    private final KeyBinding keyCtrlTap =
            new KeyBinding("<#ff007e>Ability (CTRL)</#>", Keyboard.KEY_LCONTROL, "HexColorText");

    private long lastTapMs = 0L;
    private int tapCount = 0;

    // When an "upgrade" is possible (max shards / enough radiance), we hold the double-tap
    // briefly so a third tap can upgrade into the bigger action.
    private boolean pendingAction = false;
    private long pendingAtMs = 0L;
    private int pendingNormalAction = 0;
    private int pendingUpgradeAction = 0;

    // =========================
    // Null Shell (hold-based)
    // =========================
    private boolean nsPrevDown = false;
    private long nsPressStartMs = 0L;
    private long nsSecondPressMs = 0L;
    private int nsTapCount = 0;
    private long nsLastTapMs = 0L;
    private boolean nsDoubleTapActive = false;
    private boolean nsPushStarted = false;
    private long nsPushStartMs = 0L;

    // New Null Shell mapping helpers
    private boolean nsActionConsumed = false;
    private boolean nsDefenseSent = false;

    public FracturedKeyHandler() {
        ClientRegistry.registerKeyBinding(keyCtrlTap);
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (Minecraft.getMinecraft() == null) return;
        if (Minecraft.getMinecraft().thePlayer == null) return;

        // KeyBinding "just pressed"
        if (!keyCtrlTap.isPressed()) return;

        EntityPlayer p = Minecraft.getMinecraft().thePlayer;

        // Null Shell is handled in tick using press+hold+release, not the old tap logic.
        if (hasVoidNullShellOrb(p)) {
            return;
        }

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

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;

        EntityPlayer p = mc.thePlayer;

        // Null Shell overrides CTRL behavior.
        if (hasVoidNullShellOrb(p)) {
            handleNullShellCtrl(p);
            // kill any pending fractured/solar actions
            clearPending();
            tapCount = 0;
            return;
        } else {
            resetNullShellState();
        }

        // Original pending triple-tap upgrade logic
        if (!pendingAction) return;

        long now = System.currentTimeMillis();
        if (now - pendingAtMs > TAP_WINDOW_MS) {
            // Window expired -> commit the normal action
            fireAction(pendingNormalAction);
            clearPending();
        }
    }

    private void resetNullShellState() {
        nsPrevDown = false;
        nsPressStartMs = 0L;
        nsSecondPressMs = 0L;
        nsTapCount = 0;
        nsLastTapMs = 0L;
        nsDoubleTapActive = false;
        nsPushStarted = false;
        nsPushStartMs = 0L;
        VoidOrbHudOverlay.clearNullShellHold();
    }

    private void handleNullShellCtrl(EntityPlayer p) {
        boolean down = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL);
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT);
        long now = System.currentTimeMillis();

        // edge detect
        boolean pressed = down && !nsPrevDown;
        boolean released = !down && nsPrevDown;
        nsPrevDown = down;

        if (pressed) {
            nsPressStartMs = now;
            nsPushStarted = false;
            nsPushStartMs = 0L;
            nsActionConsumed = false;
            nsDefenseSent = false;

            // Start a hold bar (used for push threshold / charge)
            VoidOrbHudOverlay.setNullShellHoldPct(0f);

            // CTRL + SHIFT => Defense immediately
            if (shift) {
                nsDefenseSent = true;
                nsActionConsumed = true;
                nsDbg(p, "send action 6 (defense)");
                fireAction(NS_ACTION_DEF);
                VoidOrbHudOverlay.clearNullShellHold();
            }
        }

        if (down && !nsActionConsumed) {
            // If SHIFT is pressed while holding CTRL (and we haven't used anything yet), treat as defense.
            if (shift && !nsDefenseSent && !nsPushStarted) {
                nsDefenseSent = true;
                nsActionConsumed = true;
                nsDbg(p, "send action 6 (defense)");
                fireAction(NS_ACTION_DEF);
                VoidOrbHudOverlay.clearNullShellHold();
                return;
            }

            // CTRL alone: hold to start ultimate push, tap to dash.
            long held = now - nsPressStartMs;

            if (!nsPushStarted) {
                // show progress until push-start threshold
                float pct = (float) held / (float) NS_PUSH_HOLD_START_MS;
                if (pct < 0f) pct = 0f;
                if (pct > 1f) pct = 1f;
                VoidOrbHudOverlay.setNullShellHoldPct(pct);

                if (held >= NS_PUSH_HOLD_START_MS) {
                    nsPushStarted = true;
                    nsPushStartMs = now;
                    nsDbg(p, "send action 7 (push start)");
                    fireAction(NS_ACTION_PUSH_S);
                }
            } else {
                // push charging bar (0..2) where 1.0 == fully charged (5s)
                long pushHeld = now - nsPushStartMs;
                float pct = (float) pushHeld / 5000.0f;
                if (pct < 0f) pct = 0f;
                if (pct > 2f) pct = 2f;
                VoidOrbHudOverlay.setNullShellHoldPct(pct);
            }
        }

        if (released) {
            VoidOrbHudOverlay.clearNullShellHold();

            if (!nsActionConsumed) {
                if (nsPushStarted) {
                    nsDbg(p, "send action 8 (push release)");
                    fireAction(NS_ACTION_PUSH_R);
                } else {
                    // quick tap => dash
                    nsDbg(p, "send action 5 (dash)");
                    fireAction(NS_ACTION_DASH);
                }
            }

            // reset per-press state
            nsPushStarted = false;
            nsPushStartMs = 0L;
            nsActionConsumed = false;
            nsDefenseSent = false;
        }
    }

    private void clearPending() {
        pendingAction = false;
        pendingAtMs = 0L;
        pendingNormalAction = 0;
        pendingUpgradeAction = 0;
    }

    private void nsDbg(EntityPlayer p, String msg) {
        if (!NS_KEY_DEBUG) return;
        try {
            if (p != null) {
                p.addChatMessage(new ChatComponentText("\u00A78[NSKEY] \u00A77" + msg));
            }
        } catch (Throwable ignored) {}
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
            int rad = tag.getInteger(TAG_LIGHT_RAD);
            if (rad >= SOLAR_COST_SUPER) {
                return new ActionChoice(3, 4, true); // double = beam, triple = super
            }
            if (rad >= SOLAR_COST_BEAM) {
                return new ActionChoice(3, 0, false);
            }
            return null;
        }

        // Fractured?
        if (tag.hasKey(TAG_SHARDS)) {
            int shards = tag.getInteger(TAG_SHARDS);
            if (shards >= MAX_SHARDS) {
                return new ActionChoice(1, 2, true); // double = small, triple = big
            }
            if (shards >= 1) {
                return new ActionChoice(1, 0, false);
            }
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

    // =========================
    // Void / Null Shell detection
    // =========================

    private static boolean hasVoidNullShellOrb(EntityPlayer p) {
        if (p == null) return false;

        // 1) Prefer the server-stamped HUD type on the player entity (optional)
        try {
            NBTTagCompound ed = p.getEntityData();
            if (ed != null && ed.hasKey("HexVoidHudType")) {
                String t = ed.getString("HexVoidHudType");
                if (isNullShellType(t)) return true;
            }
        } catch (Throwable ignored) {}

        // 2) Check held item (some setups keep the "host" on the held stack)
        try {
            ItemStack held = p.getCurrentEquippedItem();
            if (isNullShellOnStack(held)) return true;
        } catch (Throwable ignored) {}

        // 3) Check armor (socketed / worn orbs commonly live here)
        try {
            ItemStack[] armor = p.inventory != null ? p.inventory.armorInventory : null;
            if (armor != null) {
                for (int i = 0; i < armor.length; i++) {
                    if (isNullShellOnStack(armor[i])) return true;
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    private static boolean isNullShellOnStack(ItemStack stack) {
        if (stack == null) return false;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return false;

        // Direct tag path (preferred if present)
        if (tag.hasKey("HexVoidHudType")) {
            String t = tag.getString("HexVoidHudType");
            if (isNullShellType(t)) return true;
        }

        // Deep scan fallback
        String v = findStringDeep(tag, "HexVoidType");
        if (v == null) v = findStringDeep(tag, "HexVoidOrbType");
        if (v == null) v = findStringDeep(tag, "VoidType");
        return v != null && isNullShellType(v);
    }

    private static boolean isNullShellType(String t) {
        if (t == null) return false;
        String s = t.trim().toLowerCase();
        return (s.contains("null") && s.contains("shell")) || "ns".equals(s) || "nullshell".equals(s) || "voidnullshell".equals(s);
    }

    private static String findStringDeep(NBTTagCompound root, String key) {
        if (root == null || key == null) return null;
        try {
            if (root.hasKey(key)) {
                String v = root.getString(key);
                if (v != null && v.length() > 0) return v;
            }
            for (Object o : root.func_150296_c()) {
                String k = String.valueOf(o);
                NBTBase b = root.getTag(k);
                if (b instanceof NBTTagCompound) {
                    String v = findStringDeep((NBTTagCompound) b, key);
                    if (v != null) return v;
                } else if (b instanceof NBTTagList) {
                    NBTTagList l = (NBTTagList) b;
                    for (int i = 0; i < l.tagCount(); i++) {
                        NBTBase ib = l.getCompoundTagAt(i);
                        if (ib instanceof NBTTagCompound) {
                            String v = findStringDeep((NBTTagCompound) ib, key);
                            if (v != null) return v;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
