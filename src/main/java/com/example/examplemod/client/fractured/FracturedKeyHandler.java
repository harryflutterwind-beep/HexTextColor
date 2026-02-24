package com.example.examplemod.client.fractured;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.api.HexSocketAPI;
import com.example.examplemod.client.hud.VoidOrbHudOverlay;
import com.example.examplemod.client.hud.DarkfirePillHudOverlay;
import com.example.examplemod.network.PacketFracturedAction;
import com.example.examplemod.network.PacketOrbSelect;
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
import org.lwjgl.input.Mouse;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

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
 *
 * Darkfire:
 *  9 = 25% Blackfire Burn (double-tap LCTRL while holding Darkfire pill)
 */
public class FracturedKeyHandler {

    // Shared orb identity

    // Darkfire pill NBT (client reads for gating before sending packet)
    private static final String DF_TAG_TYPE       = "HexDarkFireType";
    private static final String DF_TAG_CHARGE     = "HexDarkFireCharge";
    private static final String DF_TAG_CHARGE_MAX = "HexDarkFireChargeMax";
    private static final int DF_META_FLAT = 24;
    private static final int DF_META_ANIM = 25;

    private static final int ACTION_DARKFIRE_BLACKBURN = 9;
    private static final int ACTION_DARKFIRE_BLACKBURN_BIG = 10; // 50% charge (SHIFT+CTRL)
    private static final int ACTION_DARKFIRE_SHADOWFLAME_TRAIL = 16; // 25% charge dash + void trail + black flames
    // Darkfire Pill action (11): 100% Rapid Fire (hold CTRL)
    private static final int ACTION_DARKFIRE_RAPIDFIRE_SHOT = 11;
    // Darkfire Pill action (12): stop rapid fire (release CTRL / cancel)
    private static final int ACTION_DARKFIRE_RAPIDFIRE_STOP = 12;

    // Ember Detonation (Darkfire Pill) actions
// 13 = 10% mark single (projectile / hitscan)
// 14 = 10% mark multi  (AoE mark near impact)
// 15 = detonate all current marks (hold sneak + action ~5s)
    private static final int ACTION_EMBER_MARK_SINGLE   = 13;
    private static final int ACTION_EMBER_MARK_MULTI    = 14;
    private static final int ACTION_EMBER_DETONATE_HELD = 15;


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


    // =========================
    // Active Action Gem selection (double-tap TAB)
    // =========================
    private boolean tabPrevDown = false;
    private long tabLastTapMs = 0L;
    private int tabTapCount = 0;

    // Which action-capable gem is currently selected (index in collectActionOrbs list).
    // -1 means OFF (nothing selected; no action HUDs; actives require explicit TAB cycling).
    private static int activeActionOrbIndex = -1;
    // Remember last selection we told the server about (so we don't spam packets every tick)
    private static int lastSelFamilyId = -1;
    private static int lastSelHostKind = -1;
    private static int lastSelHostSlot = -1;
    private static int lastSelSocketIdx = -2;

    private static int familyIdForCandidate(ActionOrbCandidate c) {
        if (c == null) return 0;
        if (c.kind == ORB_KIND_DARKFIRE) return 3; // DARKFIRE
        if (c.kind == ORB_KIND_VOID) return 2;     // VOID
        if (c.kind == ORB_KIND_FRACTURED_OR_SOLAR) {
            ItemStack s = c.stack;
            if (s != null) {
                try {
                    if (isLightOrbStack(s)) return 1;       // LIGHT
                    if (isFracturedOrbStack(s)) return 4;   // FRACTURED
                    if (s.hasTagCompound()) {
                        NBTTagCompound tag = s.getTagCompound();
                        if (tag != null) {
                            if (tag.hasKey(TAG_LIGHT_TYPE, 8)) return 1;
                            if (tag.hasKey(TAG_SHARDS, 3)) return 4;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        return 0;
    }


    private static String familyNameForId(int familyId) {
        switch (familyId) {
            case 1: return "LIGHT";
            case 2: return "VOID";
            case 3: return "DARKFIRE";
            case 4: return "FRACTURED";
            default: return "";
        }
    }

    private static void syncOrbSelectionToServer(EntityPlayer p, ActionOrbCandidate c) {
        if (p == null) return;

        // OFF selection: clear any prior server/client selection so actives are truly disabled.
        if (c == null) {
            int familyId = 0;
            int hk = 0;
            int hs = 0;
            int si = -1;

            if (familyId == lastSelFamilyId && hk == lastSelHostKind && hs == lastSelHostSlot && si == lastSelSocketIdx) {
                return;
            }

            lastSelFamilyId = familyId;
            lastSelHostKind = hk;
            lastSelHostSlot = hs;
            lastSelSocketIdx = si;

            try {
                NBTTagCompound ed = p.getEntityData();
                if (ed != null) {
                    ed.setString("HexSelFamily", "");
                    ed.setInteger("HexSelFamilyId", 0);
                    ed.setInteger("HexSelHostKind", 0);
                    ed.setInteger("HexSelHostSlot", 0);
                    ed.setInteger("HexSelSocketIdx", -1);
                }
            } catch (Throwable ignored) {}

            try {
                PacketOrbSelect.sendToServer(0, 0, 0, -1);
            } catch (Throwable ignored) {}
            return;
        }

        int familyId = familyIdForCandidate(c);
        if (familyId <= 0) return;

        int hk = c.hostKind;
        int hs = c.hostSlot;
        int si = c.socketIdx;

        if (familyId == lastSelFamilyId && hk == lastSelHostKind && hs == lastSelHostSlot && si == lastSelSocketIdx) {
            return;
        }

        lastSelFamilyId = familyId;
        lastSelHostKind = hk;
        lastSelHostSlot = hs;
        lastSelSocketIdx = si;

        // Keep client-side HUD selectors in sync immediately (the server may not echo these back).
        try {
            NBTTagCompound ed = p.getEntityData();
            if (ed != null) {
                ed.setString("HexSelFamily", familyNameForId(familyId));
                ed.setInteger("HexSelFamilyId", familyId);
                ed.setInteger("HexSelHostKind", hk);
                ed.setInteger("HexSelHostSlot", hs);
                ed.setInteger("HexSelSocketIdx", si);
            }
        } catch (Throwable ignored) {}

        try {
            PacketOrbSelect.sendToServer(familyId, hk, hs, si);
        } catch (Throwable ignored) {}
    }

    private static final int ORB_KIND_FRACTURED_OR_SOLAR = 1;
    private static final int ORB_KIND_VOID = 2;
    private static final int ORB_KIND_DARKFIRE = 3;

    private static class ActionOrbCandidate {
        final int kind;
        final ItemStack stack; // may be null for the synthetic Null Shell entry
        final String label;
        final String voidType; // used for kind == ORB_KIND_VOID (and for HUD labeling)

        // Where the orb lives (so the server can resolve the same socket/orb)
        // hostKind: 0 = held item, 1 = armor slot
        // hostSlot: armor index (0 boots .. 3 helm) when hostKind==1, otherwise 0
        // socketIdx: >=0 when the orb is in a socket on the host item; -1 when the orb *is* the host item
        final int hostKind;
        final int hostSlot;
        final int socketIdx;

        ActionOrbCandidate(int kind, ItemStack stack, String label) {
            this(kind, stack, label, null, 0, 0, -1);
        }

        ActionOrbCandidate(int kind, ItemStack stack, String label, String voidType) {
            this(kind, stack, label, voidType, 0, 0, -1);
        }

        ActionOrbCandidate(int kind, ItemStack stack, String label, int hostKind, int hostSlot, int socketIdx) {
            this(kind, stack, label, null, hostKind, hostSlot, socketIdx);
        }

        ActionOrbCandidate(int kind, ItemStack stack, String label, String voidType, int hostKind, int hostSlot, int socketIdx) {
            this.kind = kind;
            this.stack = stack;
            this.label = label;
            this.voidType = voidType;
            this.hostKind = hostKind;
            this.hostSlot = hostSlot;
            this.socketIdx = socketIdx;
        }
    }

    // Null Shell input constants
    private static final int NS_ACTION_DASH   = 5;
    private static final int NS_ACTION_DEF    = 6;
    private static final int NS_ACTION_PUSH_S = 7;
    private static final int NS_ACTION_PUSH_R = 8;

    // How long (ms) you must hold after the 2nd tap to start charging push
    private static final long NS_PUSH_HOLD_START_MS = 350L;

    // Client-side key debug (prints when we send 5/6/7/8)
    private static final boolean NS_KEY_DEBUG = true;

    // Darkfire key debug (prints only on double-tap attempts)
    private static final boolean DF_KEY_DEBUG = true;


    // Darkfire 50% (SHIFT+CTRL) one-shot gating
    private boolean dfPrevCtrlDown = false;
    private boolean dfBigSentThisHold = false;
    private long dfHoldStartMs = 0L;

    // Darkfire 100% (CTRL) rapid-fire gating (client-side)
    private boolean dfRapidActive = false;
    private boolean dfRapidPrevCtrlDown = false;
    private long dfRapidHoldStartMs = 0L;
    private long dfRapidLastShotMs = 0L;
    private boolean dfRapidWarnedNoCharge = false;

    // Ember Detonation input state (client-side)
    private boolean emberTapPending = false;     // waiting to commit single-tap (action 13)
    private long emberTapAtMs = 0L;              // when the first tap happened
    private boolean emberDetonateSentThisHold = false;
    private long emberDetHoldStartMs = 0L;
    private long emberLocalBlockUntilTick = 0L;  // local anti-spam; server still validates


    private final KeyBinding keyCtrlTap =
            new KeyBinding("<#ff007e>Ability (CTRL)</#>", Keyboard.KEY_LCONTROL, "HexColorText");


    // Double-tap this key to cycle selected action orb (defaults to TAB)
    private final KeyBinding keyOrbCycle =
            new KeyBinding("<#00e5ff>Orb Cycle (Double Tap)</#>", Keyboard.KEY_TAB, "HexColorText");


    /** Treat the configured Ability (CTRL) key as the "action key", but also accept Left CTRL for convenience. */
    private boolean isActionOrLeftCtrlDown() {
        boolean left = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL);
        boolean right = Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean act = false;
        try {
            int code = keyCtrlTap.getKeyCode();
            if (code > 0) act = Keyboard.isKeyDown(code);
        } catch (Throwable ignored) {}
        return left || right || act;
    }

    /** True if the provided KeyBinding is currently held down (supports mouse bindings). */
    private static boolean isKeyBindingDown(KeyBinding kb) {
        if (kb == null) return false;
        int code;
        try { code = kb.getKeyCode(); } catch (Throwable t) { return false; }
        if (code == 0) return false; // unbound
        try {
            if (code > 0) return Keyboard.isKeyDown(code);
            // Mouse buttons in 1.7.10 are negative codes starting at -100.
            if (code <= -100) {
                int btn = code + 100;
                return btn >= 0 && Mouse.isButtonDown(btn);
            }
        } catch (Throwable ignored) {}
        return false;
    }


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
        ClientRegistry.registerKeyBinding(keyOrbCycle);
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (Minecraft.getMinecraft() == null) return;
        if (Minecraft.getMinecraft().thePlayer == null) return;

        // KeyBinding "just pressed"
        if (!keyCtrlTap.isPressed()) return;

        EntityPlayer p = Minecraft.getMinecraft().thePlayer;

        // Are we holding a Darkfire pill? (Darkfire should be allowed even if Null Shell orb is equipped)
        ItemStack dfHeldForOverride = getHeldDarkfirePill(p);
        boolean holdingDarkfire = (dfHeldForOverride != null);

        // Null Shell is handled in tick using press+hold+release, not the old tap logic.
        if (hasVoidNullShellOrb(p) && !holdingDarkfire && isNullShellSelected(p)) {
            return;
        }
        // Darkfire 50% (SHIFT+CTRL) is handled in tick as a hold-charge.
        // Suppress tap logic while SHIFT is down so CTRL taps don't trigger the 25% double-tap path.
        if (holdingDarkfire && Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastTapMs <= TAP_WINDOW_MS) {
            tapCount++;
        } else {
            tapCount = 1;
        }
        lastTapMs = now;

// Ember Detonation (Darkfire pill) uses SINGLE tap (13) and DOUBLE tap (14).
// We commit the single tap after the tap window expires, unless a second tap arrives.
        if (holdingDarkfire) {
            ItemStack dfHeld = dfHeldForOverride;
            if (dfHeld != null && isEmberDetonationType(dfHeld)) {
                // If we are waiting on a prior single-tap commit and got a 2nd tap -> fire multi mark now.
                if (tapCount >= 2) {
                    tapCount = 0;
                    emberTapPending = false;
                    emberTapAtMs = 0L;

                    if (canFireEmber10(dfHeld)) {
                        dfDbg(p, "send action 14 (Ember mark multi)");
                        fireAction(ACTION_EMBER_MARK_MULTI);
                    } else {
                        dfDbg(p, "blocked (need at least 10% Darkfire charge)");
                    }
                    // Don't fall through into other orb logic.
                    clearPending();
                    return;
                }

                // First tap: arm a pending single mark commit.
                emberTapPending = true;
                emberTapAtMs = now;

                // Don't mix with other tap-upgrade systems.
                clearPending();
                return;
            }
        }

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

            // Darkfire takes priority when the pill is held OR when Darkfire is the currently selected orb.
            ItemStack dfCandidate = dfHeldForOverride;
            if (dfCandidate == null) {
                ItemStack selOrb = getSelectedTapOrb(p);
                if (isDarkfirePillStack(selOrb)) dfCandidate = selOrb;
            }

            if (dfCandidate != null) {
                boolean isShadow = isShadowflameTrailType(dfCandidate);
                boolean isBlack  = isBlackflameBurnType(dfCandidate);

                if (isShadow) {
                    if (canFireDarkfire25(dfCandidate)) {
                        dfDbg(p, "send action 16 (shadowflame trail)");
                        fireAction(ACTION_DARKFIRE_SHADOWFLAME_TRAIL);
                    } else {
                        try {
                            NBTTagCompound tag = dfCandidate.getTagCompound();
                            int max = (tag != null && tag.hasKey(DF_TAG_CHARGE_MAX)) ? tag.getInteger(DF_TAG_CHARGE_MAX) : 1000;
                            int cur = (tag != null && tag.hasKey(DF_TAG_CHARGE)) ? tag.getInteger(DF_TAG_CHARGE) : 0;
                            int need = (int) Math.ceil(Math.max(1, max) * 0.25D);
                            dfDbg(p, "blocked (need §f" + cur + "§7/§f" + max + "§7, requires §f" + need + "§7)");
                        } catch (Throwable ignored) {
                            dfDbg(p, "blocked (need at least 25% Darkfire charge)");
                        }
                    }
                    return;
                }

                if (isBlack) {
                    if (canFireDarkfire25(dfCandidate)) {
                        dfDbg(p, "send action 9 (blackfire burn)");
                        fireAction(ACTION_DARKFIRE_BLACKBURN);
                    } else {
                        try {
                            NBTTagCompound tag = dfCandidate.getTagCompound();
                            int max = (tag != null && tag.hasKey(DF_TAG_CHARGE_MAX)) ? tag.getInteger(DF_TAG_CHARGE_MAX) : 1000;
                            int cur = (tag != null && tag.hasKey(DF_TAG_CHARGE)) ? tag.getInteger(DF_TAG_CHARGE) : 0;
                            int need = (int) Math.ceil(Math.max(1, max) * 0.25D);
                            dfDbg(p, "blocked (need §f" + cur + "§7/§f" + max + "§7, requires §f" + need + "§7)");
                        } catch (Throwable ignored) {
                            dfDbg(p, "blocked (need at least 25% Darkfire charge)");
                        }
                    }
                    return;
                }

                // Unknown Darkfire type: let other orb logic proceed.
            }

            ItemStack orb = getSelectedTapOrb(p);
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


        // Double-tap TAB to cycle which socketed action gem is currently "active".
        // This prevents multiple action gems (e.g., Solar + Fractured + NullShell) from fighting over CTRL input.
        if (mc.currentScreen == null) {
            boolean tabDown = isKeyBindingDown(keyOrbCycle);
            if (tabDown && !tabPrevDown) {
                long nowTab = System.currentTimeMillis();
                if (nowTab - tabLastTapMs <= TAP_WINDOW_MS) {
                    tabTapCount++;
                } else {
                    tabTapCount = 1;
                }
                tabLastTapMs = nowTab;

                if (tabTapCount >= 2) {
                    tabTapCount = 0;
                    cycleActiveActionOrb(p);
                }
            }
            tabPrevDown = tabDown;
        } else {
            // Reset tap detection while a GUI is open (TAB is often used by other screens).
            tabPrevDown = false;
            tabTapCount = 0;
        }

        // Darkfire override: if you're holding the Darkfire pill, don't let Null Shell consume CTRL.
        boolean holdingDarkfire = (getHeldDarkfirePill(p) != null);

        // Null Shell overrides CTRL behavior only when it is the selected active action gem.
        if (hasVoidNullShellOrb(p) && !holdingDarkfire && isNullShellSelected(p)) {
            handleNullShellCtrl(p);
            // kill any pending fractured/solar actions
            clearPending();
            tapCount = 0;
            return;
        } else {
            resetNullShellState();
        }

        // Darkfire abilities while holding the pill
        if (holdingDarkfire) {
            ItemStack dfHeld = getHeldDarkfirePill(p);
            if (dfHeld != null && isEmberDetonationType(dfHeld)) {
                // Ember Detonation: hold sneak + action key (~5s) to detonate marks
                tryHandleEmberDetonationHold(p, dfHeld);
                // Ensure other Darkfire hold HUDs don't overlap
                try { DarkfirePillHudOverlay.clearHold(); } catch (Throwable ignored) {}
                try { DarkfirePillHudOverlay.clearRapidHold(); } catch (Throwable ignored) {}
            } else {
                // 50%: SHIFT+CTRL one-shot
                tryHandleDarkfire50Hold(p);
                // 100%: CTRL rapid-fire
                tryHandleDarkfire100RapidFireHold(p);
                // Clear Ember hold meter if we swap away from Ember Detonation
                emberDetHoldStartMs = 0L;
                emberDetonateSentThisHold = false;
                hudClearEmberHold();
            }

        } else {
            // Not holding the pill: ensure HUD + state are cleared
            if (dfRapidActive) {
                fireAction(ACTION_DARKFIRE_RAPIDFIRE_STOP);
            }
            dfRapidActive = false;
            dfRapidPrevCtrlDown = false;
            dfRapidHoldStartMs = 0L;
            dfRapidLastShotMs = 0L;
            dfRapidWarnedNoCharge = false;
            try { DarkfirePillHudOverlay.clearRapidHold(); } catch (Throwable ignored) {}

// Clear Ember Detonation hold meter/state when not holding the pill
            emberTapPending = false;
            emberTapAtMs = 0L;
            emberDetHoldStartMs = 0L;
            emberDetonateSentThisHold = false;
            emberLocalBlockUntilTick = 0L;
            hudClearEmberHold();

            // Also clear 50% hold state if you swap items mid-hold
            dfPrevCtrlDown = false;
            dfBigSentThisHold = false;
            dfHoldStartMs = 0L;
            try { DarkfirePillHudOverlay.clearHold(); } catch (Throwable ignored) {}
        }

// Commit Ember Detonation single-tap (action 13) when the tap window expires.
        if (emberTapPending) {
            ItemStack dfHeld = getHeldDarkfirePill(p);
            if (dfHeld == null || !isEmberDetonationType(dfHeld)) {
                emberTapPending = false;
                emberTapAtMs = 0L;
            } else {
                long nowMs = System.currentTimeMillis();
                if (nowMs - emberTapAtMs > TAP_WINDOW_MS) {
                    emberTapPending = false;
                    emberTapAtMs = 0L;
                    tapCount = 0;

                    if (canFireEmber10(dfHeld)) {
                        dfDbg(p, "send action 13 (Ember mark single)");
                        fireAction(ACTION_EMBER_MARK_SINGLE);
                    } else {
                        dfDbg(p, "blocked (need at least 10% Darkfire charge)");
                    }
                }
            }
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


    private void tryHandleDarkfire50Hold(EntityPlayer p) {
        if (p == null) return;

        boolean ctrl = isActionOrLeftCtrlDown();
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT);

        long now = System.currentTimeMillis();

        // If we are NOT holding both keys, clear the hold HUD and re-arm.
        if (!ctrl || !shift) {
            if (dfPrevCtrlDown || dfBigSentThisHold) {
                // releasing ends the hold
                DarkfirePillHudOverlay.clearHold();
            }
            dfBigSentThisHold = false;
            dfPrevCtrlDown = ctrl;
            dfHoldStartMs = 0L;
            return;
        }

        // first frame of a new hold
        if (dfHoldStartMs == 0L) {
            dfHoldStartMs = now;
            dfBigSentThisHold = false;
            // clear any tap logic so we don't mix inputs
            tapCount = 0;
            clearPending();
        }

        dfPrevCtrlDown = ctrl;

        // Drive the hold meter (0..1). Full charge at 0.60s.
        final float FULL_MS = 600f;
        float pct0to1 = (now - dfHoldStartMs) / FULL_MS;
        if (pct0to1 < 0f) pct0to1 = 0f;
        if (pct0to1 > 1f) pct0to1 = 1f;

        DarkfirePillHudOverlay.setHoldPct(pct0to1);

        // Fire once when we hit 100% charge.
        if (pct0to1 < 1f || dfBigSentThisHold) return;

        ItemStack dfHeld = getHeldDarkfirePill(p);
        if (dfHeld == null) {
            dfBigSentThisHold = true;
            return;
        }

        if (canFireDarkfire50(dfHeld)) {
            dfDbg(p, "release action 10 (50% big blackfire burn)");
            dfBigSentThisHold = true;
            fireAction(ACTION_DARKFIRE_BLACKBURN_BIG);
        } else {
            // Helpful feedback once per hold
            try {
                NBTTagCompound tag = dfHeld.getTagCompound();
                int max = (tag != null && tag.hasKey(DF_TAG_CHARGE_MAX)) ? tag.getInteger(DF_TAG_CHARGE_MAX) : 1000;
                int cur = (tag != null && tag.hasKey(DF_TAG_CHARGE)) ? tag.getInteger(DF_TAG_CHARGE) : 0;
                if (max <= 0) max = 1000;
                if (cur < 0) cur = 0;
                int need = (int) Math.ceil(Math.max(1, max) * 0.50D);
                dfDbg(p, "blocked (need §f" + cur + "§7/§f" + max + "§7, requires §f" + need + "§7)");
            } catch (Throwable ignored) {
                dfDbg(p, "blocked (need at least 50% Darkfire charge)");
            }
            dfBigSentThisHold = true;
        }
    }

    /**
     * Darkfire 100% ability: Rapid-fire while holding CTRL (no SHIFT).
     * - Requires the Darkfire charge bar to be FULL (100%) to begin.
     * - While held, fires repeated hitscan "flame blasts" that vary in size.
     * - Overcharge ramps during the hold to reach 2x damage.
     *
     * This is intentionally client-driven (one packet per shot) to keep the server simple (no extra tick hooks).
     */
    private void tryHandleDarkfire100RapidFireHold(EntityPlayer p) {
        if (p == null) return;

        final boolean ctrl  = isActionOrLeftCtrlDown();
        final boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT);

        // Rapid-fire is CTRL-only. If SHIFT is held, that's reserved for the 50% hold.
        if (!ctrl || shift) {
            // releasing ends the rapid session
            if (dfRapidPrevCtrlDown) {
                if (dfRapidActive) {
                    fireAction(ACTION_DARKFIRE_RAPIDFIRE_STOP);
                }
                try { DarkfirePillHudOverlay.clearRapidHold(); } catch (Throwable ignored) {}
            }
            dfRapidActive = false;
            dfRapidPrevCtrlDown = ctrl;
            dfRapidHoldStartMs = 0L;
            dfRapidLastShotMs = 0L;
            dfRapidWarnedNoCharge = false;
            return;
        }

        // CTRL down (no SHIFT): begin / continue charging
        long now = System.currentTimeMillis();

        if (!dfRapidPrevCtrlDown) {
            dfRapidHoldStartMs = now;
            dfRapidLastShotMs = 0L;
            dfRapidWarnedNoCharge = false;
        } else if (dfRapidHoldStartMs == 0L) {
            dfRapidHoldStartMs = now;
        }

        // Phase 1: 0..100% (spool), Phase 2: 100..200% (overcharge to 2x)
        final long FULL_MS = 650L;   // time to reach 100%
        final long OVER_MS = 1500L;  // time to reach 200% (2x)

        long elapsed = now - dfRapidHoldStartMs;
        if (elapsed < 0L) elapsed = 0L;

        float pct0to2;
        if (elapsed <= FULL_MS) {
            pct0to2 = (float) elapsed / (float) FULL_MS; // 0..1
        } else {
            float t = (float) (elapsed - FULL_MS) / (float) Math.max(1L, (OVER_MS - FULL_MS));
            if (t < 0f) t = 0f;
            if (t > 1f) t = 1f;
            pct0to2 = 1f + t; // 1..2
        }
        if (pct0to2 > 2f) pct0to2 = 2f;

        // HUD: show the rapid-fire charge / overcharge meter
        try { DarkfirePillHudOverlay.setRapidHoldPct(pct0to2); } catch (Throwable ignored) {}

        // Not ready to fire until we reach 100%
        if (pct0to2 < 1f) {
            dfRapidPrevCtrlDown = true;
            return;
        }

        // Fire-rate limiter (client-side)
        final long SHOT_MS = 150L; // ~6.7 shots/sec
        if (dfRapidLastShotMs != 0L && (now - dfRapidLastShotMs) < SHOT_MS) {
            dfRapidPrevCtrlDown = true;
            return;
        }

        ItemStack held = getHeldDarkfirePill(p);
        if (!canFireDarkfire100(held, dfRapidActive)) {
            // Warn once per hold if we can't start / continue
            if (!dfRapidWarnedNoCharge) {
                try {
                    p.addChatMessage(new ChatComponentText("§7[Darkfire] §cNeed §e100% charge§c to rapid-fire."));
                } catch (Throwable ignored) {}
                dfRapidWarnedNoCharge = true;
            }
            dfRapidLastShotMs = now;
            dfRapidPrevCtrlDown = true;
            return;
        }

        // Send one shot to the server
        try {
            fireAction(ACTION_DARKFIRE_RAPIDFIRE_SHOT);
            dfRapidActive = true;
        } catch (Throwable ignored) {}

        dfRapidLastShotMs = now;
        dfRapidPrevCtrlDown = true;
    }

    /**
     * Client-side gate: require full charge to BEGIN rapid-fire; after that, require enough charge per shot.
     * Server still validates.
     */
    private static boolean canFireDarkfire100(ItemStack heldDarkfire, boolean alreadyFiring) {
        if (heldDarkfire == null) return false;

        try {
            NBTTagCompound tag = heldDarkfire.getTagCompound();
            if (tag == null || !tag.hasKey(DF_TAG_TYPE)) return false;

            int max = tag.hasKey(DF_TAG_CHARGE_MAX) ? tag.getInteger(DF_TAG_CHARGE_MAX) : 1000;
            if (max <= 0) max = 1000;

            int cur = tag.hasKey(DF_TAG_CHARGE) ? tag.getInteger(DF_TAG_CHARGE) : 0;
            if (cur < 0) cur = 0;

            // To BEGIN rapid-fire, require full bar (100%).
            if (!alreadyFiring) {
                return cur >= max;
            }

            // Continuing: soft-gate by a small per-shot minimum so we don't spam when empty.
            // Server is authoritative on actual cost/deduction.
            int cost = (int) Math.ceil((double) max * 0.05D); // ~5% per shot (client hint only)
            if (cost < 1) cost = 1;
            return cur >= cost;
        } catch (Throwable ignored) {}

        return false;
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

    private void dfDbg(EntityPlayer p, String msg) {
        if (!DF_KEY_DEBUG) return;
        try {
            if (p != null) {
                p.addChatMessage(new ChatComponentText("\u00A78[DFKEY] \u00A77" + msg));
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
        if (!tag.getBoolean(TAG_ROLLED) && !tag.hasKey(TAG_SHARDS) && !tag.hasKey(TAG_LIGHT_TYPE)) return null;

        // Solar?
        if (tag.hasKey(TAG_LIGHT_TYPE)) {
            String lt = tag.getString(TAG_LIGHT_TYPE);
            if (lt == null) lt = "";
            boolean solarLike = lt.equalsIgnoreCase("Solar") || lt.equalsIgnoreCase("Angelic");
            if (!solarLike) return null;
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

    // =========================
    // Action gem selection helpers
    // =========================

    /** Build an ordered list of action-capable gems currently equipped (held/armor + sockets). */
    private static List<ActionOrbCandidate> collectActionOrbs(EntityPlayer p) {
        List<ActionOrbCandidate> out = new ArrayList<ActionOrbCandidate>();
        if (p == null) return out;

        // Main hand
        ItemStack held = null;
        try { held = p.getCurrentEquippedItem(); } catch (Throwable ignored) {}
        if (isActionOrb(held)) out.add(new ActionOrbCandidate(ORB_KIND_FRACTURED_OR_SOLAR, held, orbLabel(held, "Held"), 0, 0, -1));

        // Socketed gems on held host
        addSocketedOrbs(out, held, 0, 0, "Held socket");

        // Armor slots
        try {
            ItemStack[] armor = (p.inventory != null) ? p.inventory.armorInventory : null;
            if (armor != null) {
                for (int i = 0; i < armor.length; i++) {
                    ItemStack a = armor[i];
                    String slot = armorSlotLabel(i);
                    if (isActionOrb(a)) out.add(new ActionOrbCandidate(ORB_KIND_FRACTURED_OR_SOLAR, a, orbLabel(a, slot)));
                    addSocketedOrbs(out, a, 1, i, slot + " socket");
                }
            }
        } catch (Throwable ignored) {}


// Void (one candidate per HUD host that has a Void type mirrored onto it)
        java.util.HashSet<String> seenVoid = new java.util.HashSet<String>();
        addVoidCandidateFromHost(out, held, 0, 0, "Held", seenVoid);

        try {
            ItemStack[] armor2 = (p.inventory != null) ? p.inventory.armorInventory : null;
            if (armor2 != null) {
                for (int i = 0; i < armor2.length; i++) {
                    ItemStack a2 = armor2[i];
                    if (a2 == null) continue;
                    String slot2 = armorSlotLabel(i);
                    addVoidCandidateFromHost(out, a2, 1, i, slot2, seenVoid);
                }
            }
        } catch (Throwable ignored) {}


// Darkfire (one candidate per HUD host that has a Darkfire type mirrored onto it)
        java.util.HashSet<String> seenDF = new java.util.HashSet<String>();
        addDarkfireCandidateFromHost(out, held, 0, 0, "Held", seenDF);

        try {
            ItemStack[] armor3 = (p.inventory != null) ? p.inventory.armorInventory : null;
            if (armor3 != null) {
                for (int i = 0; i < armor3.length; i++) {
                    ItemStack a3 = armor3[i];
                    if (a3 == null) continue;
                    String slot3 = armorSlotLabel(i);
                    addDarkfireCandidateFromHost(out, a3, 1, i, slot3, seenDF);
                }
            }
        } catch (Throwable ignored) {}
        return out;
    }


    // Back-compat overload (older patches called this with just (out, host))
    private static void addSocketedOrbs(List<ActionOrbCandidate> out, ItemStack host) {
        addSocketedOrbs(out, host, 0, 0, "Socket");
    }

    private static void addSocketedOrbs(List<ActionOrbCandidate> out, ItemStack host, int hostKind, int hostSlot, String prefix) {
        if (out == null || host == null) return;
        try {
            int filled = HexSocketAPI.getSocketsFilled(host);
            for (int i = 0; i < filled; i++) {
                ItemStack gem = HexSocketAPI.getGemAt(host, i);
                if (isActionOrb(gem)) {
                    out.add(new ActionOrbCandidate(ORB_KIND_FRACTURED_OR_SOLAR, gem, orbLabel(gem, prefix + " #" + (i + 1), hostKind, hostSlot, i)));
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void addVoidCandidateFromHost(List<ActionOrbCandidate> out, ItemStack host, int hostKind, int hostSlot, String where, java.util.HashSet<String> seen) {
        if (out == null || host == null) return;

        boolean addedSocket = false;

        // Prefer per-socket candidates (so TAB can cycle multiple socketed Void orbs on the same host).
        try {
            int filled = HexSocketAPI.getSocketsFilled(host);
            for (int i = 0; i < filled; i++) {
                ItemStack gem = HexSocketAPI.getGemAt(host, i);
                String vt = getVoidTypeOnOrb(gem);
                if (vt == null || vt.length() == 0) continue;

                // Void: only "active" types belong in the selector/HUD.
                // Passive Void types (Entropy / Gravity Well / Abyss Mark) run in the background from the controller.
                String vtl = vt.toLowerCase();
                if (!(vtl.contains("null shell") || vtl.contains("nullshell"))) {
                    continue;
                }

                // Key includes the socket index so two socketed Void orbs don't collapse into one candidate.
                String key = (where == null ? "" : where) + "|sock" + i + "|" + vt;
                if (seen != null && seen.contains(key)) continue;
                if (seen != null) seen.add(key);

                String label = (where == null ? "Socketed Void" : (where + ": Socketed Void")) + " #" + (i + 1) + " (" + vt + ")";
                out.add(new ActionOrbCandidate(ORB_KIND_VOID, gem, label, vt, hostKind, hostSlot, i));
                addedSocket = true;
            }
        } catch (Throwable ignored) {}

        // If we found socketed candidates, don't add a host fallback (prevents the host item icon showing up).
        if (addedSocket) return;

        // Fallback: only add the host itself if it looks like one of OUR orb stacks (directly held orb case).
        if (!isOurOrb(host)) return;

        String vt = getVoidTypeOnHost(host);
        if (vt == null || vt.length() == 0) return;

        // Void: only "active" types belong in the selector/HUD.
        String vtl = vt.toLowerCase();
        if (!(vtl.contains("null shell") || vtl.contains("nullshell"))) {
            return;
        }

        String key = (where == null ? "" : where) + "|host|" + vt;
        if (seen != null && seen.contains(key)) return;
        if (seen != null) seen.add(key);

        String label = (where == null ? "Void" : (where + ": Void")) + " (" + vt + ")";
        out.add(new ActionOrbCandidate(ORB_KIND_VOID, host, label, vt, hostKind, hostSlot, -1));
    }

    private static void addDarkfireCandidateFromHost(List<ActionOrbCandidate> out, ItemStack host, int hostKind, int hostSlot, String where, java.util.HashSet<String> seen) {
        if (out == null || host == null) return;

        boolean addedSocket = false;

        // Prefer per-socket candidates (so TAB can cycle multiple socketed Darkfire pills on the same host).
        try {
            int filled = HexSocketAPI.getSocketsFilled(host);
            for (int i = 0; i < filled; i++) {
                ItemStack gem = HexSocketAPI.getGemAt(host, i);
                String t = getDarkfireTypeOnOrb(gem);
                if (t == null || t.length() == 0) continue;

                // Darkfire: only "active" types belong in the selector/HUD.
                // Passive Darkfire types (e.g. Ashen Lifesteal) run in the background from the controller.
                if (!isDarkfireActionType(gem)) continue;

                String key = (where == null ? "" : where) + "|sock" + i + "|" + t;
                if (seen != null && seen.contains(key)) continue;
                if (seen != null) seen.add(key);

                String label = (where == null ? "Socketed Darkfire" : (where + ": Socketed Darkfire")) + " #" + (i + 1) + " (" + t + ")";
                out.add(new ActionOrbCandidate(ORB_KIND_DARKFIRE, gem, label, hostKind, hostSlot, i));
                addedSocket = true;
            }
        } catch (Throwable ignored) {}

        // IMPORTANT:
        // If we found any socketed Darkfire pills, do NOT add a "host fallback" candidate.
        // The host is usually a sword/armor piece (and would show as a diamond tool icon), which is confusing.
        if (addedSocket) return;

        // Fallback: only add the host itself if it *is* a Darkfire pill (directly held pill case).
        if (!isDarkfirePillStack(host)) return;

        String t = getDarkfireTypeOnHost(host);
        if (t == null || t.length() == 0) return;

        // Darkfire: only "active" types should be selectable via TAB (passives are background-only).
        if (!isDarkfireActionType(host)) return;

        String key = (where == null ? "" : where) + "|host|" + t;
        if (seen != null && seen.contains(key)) return;
        if (seen != null) seen.add(key);

        String label = (where == null ? "Darkfire" : (where + ": Darkfire")) + " (" + t + ")";
        out.add(new ActionOrbCandidate(ORB_KIND_DARKFIRE, host, label, hostKind, hostSlot, -1));
    }


    private static String safeStr(String s) {
        return (s == null) ? "" : s;
    }



    private static String getVoidTypeOnOrb(ItemStack orb) {
        if (orb == null) return null;
        NBTTagCompound tag = orb.getTagCompound();
        if (tag == null) return null;
        // Prefer rolled key
        if (tag.hasKey("HexVoidType", 8)) {
            String s = safeStr(tag.getString("HexVoidType"));
            if (s.length() > 0) return s;
        }
        if (tag.hasKey("HexVoidOrbType", 8)) {
            String s = safeStr(tag.getString("HexVoidOrbType"));
            if (s.length() > 0) return s;
        }
        // HUD mirror key fallback
        if (tag.hasKey("HexVoidHudType", 8)) {
            String s = safeStr(tag.getString("HexVoidHudType"));
            if (s.length() > 0) return s;
        }
        return null;
    }

    private static String getDarkfireTypeOnOrb(ItemStack orb) {
        if (orb == null) return null;
        NBTTagCompound tag = orb.getTagCompound();
        if (tag == null) return null;
        if (tag.hasKey("HexDarkFireType", 8)) {
            String s = safeStr(tag.getString("HexDarkFireType"));
            if (s.length() > 0) return s;
        }
        if (tag.hasKey("HexDarkFireHudType", 8)) {
            String s = safeStr(tag.getString("HexDarkFireHudType"));
            if (s.length() > 0) return s;
        }
        return null;
    }

    private static String getVoidTypeOnHost(ItemStack host) {
        if (host == null) return null;
        NBTTagCompound tag = host.getTagCompound();
        if (tag == null) return null;

        // Preferred: server-stamped HUD type
        if (tag.hasKey("HexVoidHudType")) {
            String t = safeStr(tag.getString("HexVoidHudType"));
            if (t.length() > 0) return t;
        }

        // Fallbacks: direct type tags or deep scan
        String t = null;
        if (tag.hasKey("HexVoidType")) t = safeStr(tag.getString("HexVoidType"));
        if (t == null || t.length() == 0) t = safeStr(tag.getString("HexVoidOrbType"));
        if (t == null || t.length() == 0) t = findStringDeep(tag, "HexVoidType");
        if (t == null || t.length() == 0) t = findStringDeep(tag, "HexVoidOrbType");
        if (t == null || t.length() == 0) t = findStringDeep(tag, "VoidType");
        return (t != null && t.length() > 0) ? t : null;
    }

    private static String getVoidTypeInSockets(ItemStack host) {
        if (host == null) return null;
        try {
            int filled = HexSocketAPI.getSocketsFilled(host);
            for (int i = 0; i < filled; i++) {
                ItemStack gem = HexSocketAPI.getGemAt(host, i);
                if (gem == null) continue;
                NBTTagCompound tag = gem.getTagCompound();
                if (tag == null) continue;

                String t = null;
                if (tag.hasKey("HexVoidHudType")) t = safeStr(tag.getString("HexVoidHudType"));
                if (t == null || t.length() == 0) {
                    t = findStringDeep(tag, "HexVoidType");
                    if (t == null) t = findStringDeep(tag, "HexVoidOrbType");
                    if (t == null) t = findStringDeep(tag, "VoidType");
                }
                if (t != null && t.length() > 0) return t;
            }
        } catch (Throwable ignored) {}
        return null;
    }


    private static String getDarkfireTypeOnHost(ItemStack host) {
        if (host == null) return null;
        NBTTagCompound tag = host.getTagCompound();
        if (tag == null) return null;

        // Preferred: server-stamped HUD type
        if (tag.hasKey("HexDarkFireHudType")) {
            String t = safeStr(tag.getString("HexDarkFireHudType"));
            if (t.length() > 0) return t;
        }

        // Fallbacks: direct type tags or deep scan
        String t = null;
        if (tag.hasKey("HexDarkFireType")) t = safeStr(tag.getString("HexDarkFireType"));
        if (t == null || t.length() == 0) t = findStringDeep(tag, "HexDarkFireType");
        if (t == null || t.length() == 0) t = findStringDeep(tag, "DarkFireType");
        return (t != null && t.length() > 0) ? t : null;
    }

    private static String getDarkfireTypeInSockets(ItemStack host) {
        if (host == null) return null;
        try {
            int filled = HexSocketAPI.getSocketsFilled(host);
            for (int i = 0; i < filled; i++) {
                ItemStack gem = HexSocketAPI.getGemAt(host, i);
                if (gem == null) continue;
                NBTTagCompound tag = gem.getTagCompound();
                if (tag == null) continue;

                String t = null;
                if (tag.hasKey("HexDarkFireHudType")) t = safeStr(tag.getString("HexDarkFireHudType"));
                if (t == null || t.length() == 0) {
                    if (tag.hasKey("HexDarkFireType")) t = safeStr(tag.getString("HexDarkFireType"));
                }
                if (t == null || t.length() == 0) {
                    t = findStringDeep(tag, "HexDarkFireType");
                    if (t == null) t = findStringDeep(tag, "DarkFireType");
                }
                if (t != null && t.length() > 0) return t;
            }
        } catch (Throwable ignored) {}
        return null;
    }


    /**
     * Resolves which action-capable orb is selected for HUD + active input gating.
     *
     * Selection rules:
     *  - activeActionOrbIndex == -1 => OFF (nothing selected; no action HUDs; actives require explicit TAB cycling)
     *  - If the selection becomes invalid (gear changed), we fall back to OFF.
     */
    private static ActionOrbCandidate resolveSelectedCandidate(EntityPlayer p, List<ActionOrbCandidate> list) {
        if (list == null || list.isEmpty()) {
            activeActionOrbIndex = -1;
            syncOrbSelectionToServer(p, null);
            return null;
        }

        // OFF mode
        if (activeActionOrbIndex < 0) {
            syncOrbSelectionToServer(p, null);
            return null;
        }

        // If selection is out of range due to gear changes, reset to OFF.
        if (activeActionOrbIndex >= list.size()) {
            activeActionOrbIndex = -1;
            syncOrbSelectionToServer(p, null);
            return null;
        }

        ActionOrbCandidate sel = list.get(activeActionOrbIndex);
        syncOrbSelectionToServer(p, sel);
        return sel;
    }


// =========================
// HUD selection access (client-side)
// =========================

    public static final String HUD_KIND_FRACTURED = "fractured";
    public static final String HUD_KIND_LIGHT     = "light";
    public static final String HUD_KIND_VOID      = "void";
    public static final String HUD_KIND_DARKFIRE  = "darkfire";

    /** Returns which HUD family is currently selected by the TAB cycling. */
    public static String getSelectedHudKind(EntityPlayer p) {
        try {
            List<ActionOrbCandidate> list = collectActionOrbs(p);
            ActionOrbCandidate sel = resolveSelectedCandidate(p, list);
            if (sel == null) return "";
            if (sel.kind == ORB_KIND_VOID) return HUD_KIND_VOID;
            if (sel.kind == ORB_KIND_DARKFIRE) return HUD_KIND_DARKFIRE;

            ItemStack s = sel.stack;
            if (s != null && s.hasTagCompound()) {
                NBTTagCompound tag = s.getTagCompound();
                if (tag != null) {
                    if (tag.hasKey(TAG_SHARDS)) return HUD_KIND_FRACTURED;
                    if (tag.hasKey(TAG_LIGHT_TYPE, 8)) return HUD_KIND_LIGHT;
                }
            }
        } catch (Throwable ignored) {}
        return "";
    }

    /**
     * Returns the selected orb stack if the selected HUD kind matches; otherwise null.
     * This is what the HUD overlays should use so only ONE action HUD is visible at a time.
     * (Chaotic is passive/always-on and is intentionally not part of this gating.)
     */
    public static ItemStack getSelectedHudStackOfKind(EntityPlayer p, String kind) {
        if (p == null || kind == null) return null;
        try {
            List<ActionOrbCandidate> list = collectActionOrbs(p);
            ActionOrbCandidate sel = resolveSelectedCandidate(p, list);
            if (sel == null) return null;

            String k = getSelectedHudKind(p);
            if (k == null || k.length() == 0) return null;
            if (!k.equalsIgnoreCase(kind)) return null;

            return sel.stack;
        } catch (Throwable ignored) {}
        return null;
    }

    public static boolean isHudKindSelected(EntityPlayer p, String kind) {
        if (kind == null) return false;
        String k = getSelectedHudKind(p);
        return k != null && k.equalsIgnoreCase(kind);
    }

    /** True if the currently selected candidate is Null Shell. */
    /** True if the currently selected candidate is a Void host whose type is Null Shell. */
    private static boolean isNullShellSelected(EntityPlayer p) {
        List<ActionOrbCandidate> list = collectActionOrbs(p);
        ActionOrbCandidate sel = resolveSelectedCandidate(p, list);
        return sel != null && sel.kind == ORB_KIND_VOID && isNullShellType(sel.voidType);
    }

    /** Returns the selected orb stack for Fractured/Solar tap actions (CTRL double/triple tap). */
    private static ItemStack getSelectedTapOrb(EntityPlayer p) {
        List<ActionOrbCandidate> list = collectActionOrbs(p);
        ActionOrbCandidate sel = resolveSelectedCandidate(p, list);
        if (sel == null) return null;
        if (sel.kind == ORB_KIND_VOID) return null;
        return sel.stack;
    }

    /** Cycle selection and print which gem is now active. Triggered by double-tapping TAB. */
    /** Cycle selection and print which gem is now active. Triggered by double-tapping TAB. */
    private static void cycleActiveActionOrb(EntityPlayer p) {
        List<ActionOrbCandidate> list = collectActionOrbs(p);
        if (list == null || list.isEmpty()) {
            activeActionOrbIndex = -1;
            syncOrbSelectionToServer(p, null);
            if (p != null) {
                p.addChatMessage(new ChatComponentText("§7[Hex] §cNo action gems found."));
            }
            return;
        }

        // OFF -> first candidate
        if (activeActionOrbIndex < 0) {
            activeActionOrbIndex = 0;
            if (p != null) {
                ActionOrbCandidate c = list.get(activeActionOrbIndex);
                syncOrbSelectionToServer(p, c);
                String msg = "§7[Hex] §fActive: §a" + c.label;
                if (c.kind == ORB_KIND_VOID) msg += " §7(CTRL = Void)";
                p.addChatMessage(new ChatComponentText(msg));
            }
            return;
        }

        // Last candidate -> OFF
        if (activeActionOrbIndex >= list.size() - 1) {
            activeActionOrbIndex = -1;
            syncOrbSelectionToServer(p, null);
            if (p != null) {
                p.addChatMessage(new ChatComponentText("§7[Hex] §fActive: §cOFF"));
            }
            return;
        }

        // Next candidate
        activeActionOrbIndex++;
        if (p != null) {
            ActionOrbCandidate c = list.get(activeActionOrbIndex);
            syncOrbSelectionToServer(p, c);
            String msg = "§7[Hex] §fActive: §a" + c.label;
            if (c.kind == ORB_KIND_VOID) msg += " §7(CTRL = Void)";
            p.addChatMessage(new ChatComponentText(msg));
        }
    }

    private static String armorSlotLabel(int i) {
        // armorInventory: 0 boots, 1 legs, 2 chest, 3 helm
        switch (i) {
            case 0: return "Boots";
            case 1: return "Legs";
            case 2: return "Chest";
            case 3: return "Helm";
            default: return "Armor";
        }
    }

    private static String orbLabel(ItemStack orb, String where) {
        if (orb == null) return where;
        NBTTagCompound tag = orb.getTagCompound();
        String name = null;

        try {
            if (tag != null && tag.hasKey(TAG_LIGHT_TYPE)) {
                String t = tag.getString(TAG_LIGHT_TYPE);
                if (t != null && t.length() > 0) name = "Light (" + t + ")";
            }
            if (name == null && tag != null && tag.hasKey(TAG_SHARDS)) {
                name = "Fractured";
            }
            if (name == null && tag != null && tag.hasKey(TAG_PROFILE)) {
                String p = tag.getString(TAG_PROFILE);
                if (p != null && p.length() > 0) name = p;
            }
            if (name == null) name = orb.getDisplayName();
        } catch (Throwable ignored) {}

        return where + ": " + name;
    }

    // Overload used by newer selection/cycling code; keep it delegating to the base label for compatibility.
    private static String orbLabel(ItemStack orb, String where, int hostKind, int hostSlot, int socketIdx) {
        return orbLabel(orb, where);
    }


    private static ItemStack findEquippedOrb(EntityPlayer p) {
        if (p == null) return null;

        // Main hand
        ItemStack held = null;
        try { held = p.getCurrentEquippedItem(); } catch (Throwable ignored) {}
        if (isOurOrb(held)) return held;

        // Socketed gems on held host
        ItemStack sockHeld = findSocketedOrb(held);
        if (sockHeld != null) return sockHeld;

        // Armor slots
        try {
            ItemStack[] armor = (p.inventory != null) ? p.inventory.armorInventory : null;
            if (armor != null) {
                for (int i = 0; i < armor.length; i++) {
                    ItemStack a = armor[i];
                    if (isOurOrb(a)) return a;

                    ItemStack sock = findSocketedOrb(a);
                    if (sock != null) return sock;
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static ItemStack findSocketedOrb(ItemStack host) {
        if (host == null) return null;
        try {
            int filled = HexSocketAPI.getSocketsFilled(host);
            for (int i = 0; i < filled; i++) {
                ItemStack gem = HexSocketAPI.getGemAt(host, i);
                if (isOurOrb(gem)) return gem;
            }
        } catch (Throwable ignored) {}
        return null;
    }

// -------------------------
    // Darkfire (held item only)
    // -------------------------


    private static ItemStack getHeldDarkfirePill(EntityPlayer p) {
        try {
            if (p == null) return null;

            // If the player is directly holding the pill, always treat it as active
            ItemStack held = p.getCurrentEquippedItem();
            if (isDarkfirePillStack(held) && isDarkfireActionType(held)) return held;

            // Otherwise, only treat socketed/armor Darkfire as active when it is the *selected* action orb
            List<ActionOrbCandidate> list = collectActionOrbs(p);
            ActionOrbCandidate sel = resolveSelectedCandidate(p, list);
            if (sel != null && sel.kind == ORB_KIND_DARKFIRE) {
                return sel.stack;
            }
        } catch (Throwable ignored) {}

        return null;
    }



    // -------------------------
    // Orb family helpers (for selection syncing)
    // -------------------------

    /** Returns true if this stack is (or behaves like) a Light orb for selection family purposes. */
    private static boolean isLightOrbStack(ItemStack s) {
        if (s == null) return false;

        // Meta-based (GEM_ICONS)
        try {
            if (s.getItem() == ExampleMod.GEM_ICONS) {
                int m = s.getItemDamage();
                // From ItemGemIcons: Light orb metas are 10/11
                if (m == 10 || m == 11) return true;
            }
        } catch (Throwable ignored) {}

        NBTTagCompound tag = null;
        try { tag = s.getTagCompound(); } catch (Throwable ignored) {}
        if (tag != null) {
            try {
                if (tag.hasKey(TAG_LIGHT_TYPE, 8)) return true;
            } catch (Throwable ignored) {}

            try {
                if (tag.hasKey(TAG_PROFILE, 8)) {
                    String p = safeStr(tag.getString(TAG_PROFILE)).toLowerCase(Locale.ROOT);
                    if (p.contains("light") || p.contains("solar") || p.contains("radiant") || p.contains("beacon") || p.contains("halo") || p.contains("angelic")) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Last resort: gem key heuristic
        try {
            String key = readGemKeyFromTag(tag).toLowerCase(Locale.ROOT);
            if (key.contains("light") || key.contains("radiance") || key.contains("solar")) return true;
        } catch (Throwable ignored) {}

        return false;
    }

    /** Returns true if this stack is (or behaves like) a Fractured orb for selection family purposes. */
    private static boolean isFracturedOrbStack(ItemStack s) {
        if (s == null) return false;

        // Meta-based (GEM_ICONS)
        try {
            if (s.getItem() == ExampleMod.GEM_ICONS) {
                int m = s.getItemDamage();
                // From ItemGemIcons: Fractured orb metas are 4/5
                if (m == 4 || m == 5) return true;
            }
        } catch (Throwable ignored) {}

        NBTTagCompound tag = null;
        try { tag = s.getTagCompound(); } catch (Throwable ignored) {}
        if (tag != null) {
            try {
                if (tag.hasKey(TAG_SHARDS, 3)) return true;
            } catch (Throwable ignored) {}

            try {
                if (tag.hasKey(TAG_PROFILE, 8)) {
                    String p = safeStr(tag.getString(TAG_PROFILE)).toLowerCase(Locale.ROOT);
                    if (p.contains("fractured")) return true;
                }
            } catch (Throwable ignored) {}
        }

        // Last resort: gem key heuristic
        try {
            String key = readGemKeyFromTag(tag).toLowerCase(Locale.ROOT);
            if (key.contains("fractured")) return true;
        } catch (Throwable ignored) {}

        return false;
    }

    private static boolean isDarkfirePillStack(ItemStack stack) {
        if (stack == null) return false;
        // Prefer tagging over metadata/item checks so this survives refactors.
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(DF_TAG_TYPE)) return false;

        String type = tag.getString(DF_TAG_TYPE);
        return type != null && type.trim().length() > 0;
    }

    private static ItemStack findSocketedDarkfirePillInHost(ItemStack host) {
        if (host == null) return null;
        try {
            int filled = HexSocketAPI.getSocketsFilled(host);
            for (int i = 0; i < filled; i++) {
                ItemStack gem = HexSocketAPI.getGemAt(host, i);
                if (isDarkfirePillStack(gem)) return gem;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean canFireDarkfire25(ItemStack held) {
        if (held == null) return false;
        NBTTagCompound tag = held.getTagCompound();
        if (tag == null) return false;

        int max = tag.hasKey(DF_TAG_CHARGE_MAX) ? tag.getInteger(DF_TAG_CHARGE_MAX) : 1000;
        if (max <= 0) max = 1000;

        int cur = tag.hasKey(DF_TAG_CHARGE) ? tag.getInteger(DF_TAG_CHARGE) : 0;
        if (cur < 0) cur = 0;

        int cost = (int) Math.ceil((double) max * 0.25D);
        if (cost < 1) cost = 1;

        return cur >= cost;
    }

    private static boolean canFireDarkfire50(ItemStack held) {
        if (held == null) return false;
        NBTTagCompound tag = held.getTagCompound();
        if (tag == null) return false;

        int max = tag.hasKey(DF_TAG_CHARGE_MAX) ? tag.getInteger(DF_TAG_CHARGE_MAX) : 1000;
        if (max <= 0) max = 1000;

        int cur = tag.hasKey(DF_TAG_CHARGE) ? tag.getInteger(DF_TAG_CHARGE) : 0;
        if (cur < 0) cur = 0;

        int cost = (int) Math.ceil((double) max * 0.50D);
        if (cost < 1) cost = 1;

        return cur >= cost;
    }

// -------------------------
// Darkfire action-type gating
// -------------------------

    /** True if this Darkfire pill type is action-capable (i.e. should be selectable via TAB / actives). */
    private static boolean isDarkfireActionType(ItemStack dfHeld) {
        return isEmberDetonationType(dfHeld) || isBlackflameBurnType(dfHeld) || isShadowflameTrailType(dfHeld);
    }

// -------------------------
// Ember Detonation helpers
// -------------------------

    private static boolean isEmberDetonationType(ItemStack dfHeld) {
        if (dfHeld == null) return false;
        try {
            NBTTagCompound tag = dfHeld.getTagCompound();
            if (tag == null) return false;
            if (!tag.hasKey(DF_TAG_TYPE)) return false;
            String t = tag.getString(DF_TAG_TYPE);
            if (t == null) return false;
            t = t.trim().toLowerCase();
            return t.contains("ember") && t.contains("deton");
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean isBlackflameBurnType(ItemStack dfHeld) {
        if (dfHeld == null) return false;
        try {
            NBTTagCompound tag = dfHeld.getTagCompound();
            if (tag == null || !tag.hasKey(DF_TAG_TYPE)) return false;
            String t = tag.getString(DF_TAG_TYPE);
            if (t == null) return false;
            t = t.trim().toLowerCase();
            return t.contains("blackflame") && t.contains("burn");
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean isShadowflameTrailType(ItemStack dfHeld) {
        if (dfHeld == null) return false;
        try {
            NBTTagCompound tag = dfHeld.getTagCompound();
            if (tag == null || !tag.hasKey(DF_TAG_TYPE)) return false;
            String t = tag.getString(DF_TAG_TYPE);
            if (t == null) return false;
            t = t.trim().toLowerCase();
            return t.contains("shadowflame") && t.contains("trail");
        } catch (Throwable ignored) {}
        return false;
    }


    private static boolean canFireEmber10(ItemStack dfHeld) {
        if (dfHeld == null) return false;
        try {
            NBTTagCompound tag = dfHeld.getTagCompound();
            int max = (tag != null && tag.hasKey(DF_TAG_CHARGE_MAX)) ? tag.getInteger(DF_TAG_CHARGE_MAX) : 1000;
            int cur = (tag != null && tag.hasKey(DF_TAG_CHARGE)) ? tag.getInteger(DF_TAG_CHARGE) : 0;
            if (max <= 0) max = 1000;
            if (cur < 0) cur = 0;
            int need = (int) Math.ceil((double) Math.max(1, max) * 0.10D);
            if (need < 1) need = 1;
            return cur >= need;
        } catch (Throwable ignored) {}
        // Let server validate if the client can't read charge.
        return true;
    }

    private static boolean isEmberDetonateOnCooldown(EntityPlayer p, ItemStack dfHeld) {
        if (p == null || dfHeld == null) return false;
        try {
            NBTTagCompound tag = dfHeld.getTagCompound();
            if (tag == null) return false;
            long end = tag.getLong("HexDF_EmberDetCDEnd");
            if (end <= 0L) return false;
            long now = (p.worldObj != null) ? p.worldObj.getTotalWorldTime() : 0L;
            return now < end;
        } catch (Throwable ignored) {}
        return false;
    }

    // Call optional HUD methods via reflection so this class compiles even if the overlay doesn't have them yet.
    private static void hudSetEmberHoldPct(float pct0to1) {
        try {
            java.lang.reflect.Method m = DarkfirePillHudOverlay.class.getMethod("setEmberHoldPct", float.class);
            m.invoke(null, pct0to1);
        } catch (Throwable ignored) {}
    }

    private static void hudClearEmberHold() {
        try {
            java.lang.reflect.Method m = DarkfirePillHudOverlay.class.getMethod("clearEmberHold");
            m.invoke(null);
        } catch (Throwable ignored) {}
    }

    private void tryHandleEmberDetonationHold(EntityPlayer p, ItemStack dfHeld) {
        if (p == null || dfHeld == null) return;

        // Requirement: player is sneaking + holding the action key (CTRL mapping) for ~5 seconds
        boolean sneak = false;
        try { sneak = p.isSneaking(); } catch (Throwable ignored) {}
        if (!sneak) sneak = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT);

        boolean actDown = isActionOrLeftCtrlDown();

        long nowMs = System.currentTimeMillis();
        long nowTick = (p.worldObj != null) ? p.worldObj.getTotalWorldTime() : 0L;

        // Local anti-spam window after a successful detonation send
        if (emberLocalBlockUntilTick > 0L && nowTick < emberLocalBlockUntilTick) {
            hudClearEmberHold();
            emberDetHoldStartMs = 0L;
            emberDetonateSentThisHold = false;
            return;
        }

        // If cooldown exists on the held stack, don't even show the hold meter.
        if (isEmberDetonateOnCooldown(p, dfHeld)) {
            hudClearEmberHold();
            emberDetHoldStartMs = 0L;
            emberDetonateSentThisHold = false;
            return;
        }

        if (!sneak || !actDown) {
            // released early: clear meter and re-arm
            if (emberDetHoldStartMs != 0L || emberDetonateSentThisHold) {
                hudClearEmberHold();
            }
            emberDetHoldStartMs = 0L;
            emberDetonateSentThisHold = false;
            return;
        }

        if (emberDetHoldStartMs == 0L) {
            emberDetHoldStartMs = nowMs;
            emberDetonateSentThisHold = false;

            // Don't let tap-based marks and hold detonate stack in the same moment.
            emberTapPending = false;
            emberTapAtMs = 0L;
            tapCount = 0;
            clearPending();
        }

        // Drive meter 0..1 where 1.0 == 5 seconds
        final float HOLD_MS = 5000f;
        float pct = (nowMs - emberDetHoldStartMs) / HOLD_MS;
        if (pct < 0f) pct = 0f;
        if (pct > 1f) pct = 1f;

        hudSetEmberHoldPct(pct);

        if (pct < 1f || emberDetonateSentThisHold) return;

        emberDetonateSentThisHold = true;
        emberLocalBlockUntilTick = nowTick + 10L; // tiny local spam guard

        dfDbg(p, "send action 15 (Ember detonate held)");
        fireAction(ACTION_EMBER_DETONATE_HELD);

        // Clear meter after firing
        hudClearEmberHold();
    }

    private static String readGemKeyFromTag(NBTTagCompound tag) {
        if (tag == null) return null;
        if (tag.hasKey("HexGemKey"))  return tag.getString("HexGemKey");
        if (tag.hasKey("HexGemIcon")) return tag.getString("HexGemIcon");
        if (tag.hasKey("HexOrbIcon")) return tag.getString("HexOrbIcon");
        if (tag.hasKey("GemKey"))     return tag.getString("GemKey");
        return null;
    }

    private static String normalizeGemKey(String raw) {
        if (raw == null) return "";
        raw = raw.trim();
        if (raw.startsWith("<ico:")) {
            int end = raw.indexOf('>');
            if (end > 5) raw = raw.substring(5, end);
        }
        raw = raw.replace("\\", "/");
        while (raw.startsWith("/")) raw = raw.substring(1);
        raw = raw.toLowerCase(Locale.ROOT);
        if (raw.endsWith(".png")) raw = raw.substring(0, raw.length() - 4);
        return raw;
    }

    private static boolean keyLooksLikeOurOrb(ItemStack s, NBTTagCompound tag) {
        String key = readGemKeyFromTag(tag);
        if (key == null) return false;
        String n = normalizeGemKey(key);
        // all our orb textures follow the orb_gem_* naming
        return n.contains("orb_gem_");
    }

    private static boolean isOurOrb(ItemStack s) {
        if (s == null) return false;
        NBTTagCompound tag = s.getTagCompound();
        if (tag == null) return false;

        // Normal path: fully-rolled orb payload
        if (tag.getBoolean(TAG_ROLLED) && tag.hasKey(TAG_PROFILE)) return true;

        // Socket path: some stations stripped TAG_ROLLED / TAG_PROFILE, but kept type keys
        if (tag.hasKey(TAG_SHARDS)) return true;      // Fractured
        if (tag.hasKey(TAG_LIGHT_TYPE)) return true;  // Solar

        // Last resort: if it still carries an orb_gem_* key/icon, treat as ours (server validates actions).
        return keyLooksLikeOurOrb(s, tag);
    }

    private static boolean isActionOrb(ItemStack stack) {
        if (!isOurOrb(stack)) return false;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return false;

        // Fractured orb (shards-based)
        if (tag.hasKey(TAG_SHARDS)) return true;

        // Light orbs: any rolled Light type participates in the HUD/effect cycling (Radiant/Halo/Beacon/Solar/Angelic/etc.)
        if (tag.hasKey(TAG_LIGHT_TYPE, 8)) {
            String lt = tag.getString(TAG_LIGHT_TYPE);
            return lt != null && lt.length() > 0;
        }

        return false;
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
            if (hasNullShellInSockets(held)) return true;
        } catch (Throwable ignored) {}

        // 3) Check armor (socketed / worn orbs commonly live here)
        try {
            ItemStack[] armor = p.inventory != null ? p.inventory.armorInventory : null;
            if (armor != null) {
                for (int i = 0; i < armor.length; i++) {
                    ItemStack a = armor[i];
                    if (isNullShellOnStack(a)) return true;
                    if (hasNullShellInSockets(a)) return true;
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    private static boolean hasNullShellInSockets(ItemStack host) {
        if (host == null) return false;
        try {
            int filled = HexSocketAPI.getSocketsFilled(host);
            for (int i = 0; i < filled; i++) {
                ItemStack gem = HexSocketAPI.getGemAt(host, i);
                if (isNullShellOnStack(gem)) return true;
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