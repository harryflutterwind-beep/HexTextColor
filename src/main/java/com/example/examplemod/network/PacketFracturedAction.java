package com.example.examplemod.network;

import com.example.examplemod.server.HexOrbEffectsController;
import com.example.examplemod.api.HexSocketAPI;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import java.util.concurrent.Callable;
import java.lang.reflect.Method;
import cpw.mods.fml.common.FMLCommonHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Vec3;
import net.minecraft.network.Packet;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import com.example.examplemod.server.HexDBCProcDamageProvider;
import com.example.examplemod.server.HexDBCBridgeDamageApplier;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraft.nbt.NBTTagList;
import com.example.examplemod.ExampleMod;

/**
 * Server-bound action packet for orb actions triggered by LCTRL double/triple taps. (MC/Forge 1.7.10)
 *
 * action:
 *  1 = FRACTURED: spend 1 shard (small flying blast)
 *  2 = FRACTURED: spend all shards (big flying blast; requires max shards)
 *  3 = SOLAR: fire Solar Beam (costs 10% radiance; uses beam cooldown bar)
 *  4 = SOLAR: fire Super Solar Beam (costs 80% radiance; uses beam cooldown bar)
 *  9  = DARKFIRE: Blackfire Burn (costs 25% Darkfire charge; applies 10s DoT)
 *  10 = DARKFIRE: Big Blackfire Burn (costs 50% Darkfire charge; bigger flame + more damage)
 *  16 = DARKFIRE: Shadowflame Trail dash (costs 25% Darkfire charge; dash + void trail + black flames)
 */
public class PacketFracturedAction implements IMessage {

    public int action;

    // ---------------------------
    // Dark Fire Pill: Ember Detonation actions
    // (Keep explicit IDs so HUD/keybinds can reference them)
    // ---------------------------
    public static final int ACTION_EMBER_MARK_SINGLE   = 13;  // costs 10% charge, marks 1 target
    public static final int ACTION_EMBER_MARK_MULTI    = 14;  // costs 10% charge, marks nearby targets
    public static final int ACTION_EMBER_DETONATE_HELD = 15;  // sneak + hold ~5s -> detonate marked targets
    public static final int ACTION_DARKFIRE_SHADOWFLAME_TRAIL = 16;  // costs 25% charge, dash + void trail + black flames

    /**
     * Client helper: send an action packet to the server.
     * Safe to call only on client (HUD/key handler). No-ops on server side.
     */
    public static void sendActionToServer(int actionId) {
        try {
            // Guard: never attempt to send-to-server from dedicated server thread
            if (FMLCommonHandler.instance().getEffectiveSide().isServer()) return;

            SimpleNetworkWrapper net = resolveNetWrapper();
            if (net != null) {
                net.sendToServer(new PacketFracturedAction(actionId));
            }
        } catch (Throwable ignored) {}
    }


    // Required by Forge SimpleNetworkWrapper (reflective instantiation)
    public PacketFracturedAction() {
    }

    public PacketFracturedAction(int action) {
        this.action = action;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.action);
    }

    public static class Handler implements IMessageHandler<PacketFracturedAction, IMessage> {
        @Override
        public IMessage onMessage(PacketFracturedAction msg, MessageContext ctx) {
            final EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            if (p == null) return null;

            final int act = msg.action;

            // IMPORTANT (1.7.10): SimpleImpl handlers run on the Netty network thread.
            // World/entity access must be executed on the main server thread to avoid world crashes.
            try {
                Object server = FMLCommonHandler.instance().getMinecraftServerInstance();
                if (server != null) {
                    // Try common scheduled-task method names/signatures across 1.7.10 forks.
                    Method m = null;

                    // Runnable: addScheduledTask(Runnable) or func_152344_a(Runnable)
                    try { m = server.getClass().getMethod("addScheduledTask", Runnable.class); } catch (Throwable ignored) {}
                    if (m == null) {
                        try { m = server.getClass().getMethod("func_152344_a", Runnable.class); } catch (Throwable ignored) {}
                    }
                    if (m != null) {
                        final Method mm = m;
                        mm.invoke(server, new Runnable() {
                            @Override public void run() {
                                handleOrbAction(p, act);
                            }
                        });
                        return null;
                    }

                    // Callable: addScheduledTask(Callable)
                    try { m = server.getClass().getMethod("addScheduledTask", Callable.class); } catch (Throwable ignored) {}
                    if (m != null) {
                        final Method mm = m;
                        mm.invoke(server, new Callable<Object>() {
                            @Override public Object call() {
                                handleOrbAction(p, act);
                                return null;
                            }
                        });
                        return null;
                    }
                }
            } catch (Throwable ignored) {}

            // Fallback: execute directly (should be rare, but keeps compatibility if scheduling method isn't found)
            handleOrbAction(p, act);
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // Fractured-only logic
    // ---------------------------------------------------------------------

    private static final String TAG_ROLLED  = "HexOrbRolled";
    private static final String TAG_PROFILE = "HexOrbProfile";


    private static final String FRACTURED_PREFIX = "FRACTURED_";
    private static final String TAG_FR_SHARDS   = "HexFracShards";      // int 0..5
    private static final String TAG_FR_SNAP     = "HexFracSnapTicks";   // int
    private static final String TAG_FR_SNAP_MAX = "HexFracSnapMax";     // int

    // Fractured snap cooldown (ticks). Client HUD reads TAG_FR_SNAP/TAG_FR_SNAP_MAX.
    private static final int FRB_COOLDOWN_TICKS = 120; // 6s @ 20t/s


    // Light/Solar tags (stored on LIGHT_* orbs)
    private static final String TAG_LIGHT_TYPE = "HexLightType";           // String (e.g. "Solar")
    private static final String TAG_LIGHT_RAD  = "HexLightRadiance";       // int 0..100
    private static final String TAG_L_BEAM_CD     = "HexLightBeamCd";      // int ticks remaining
    private static final String TAG_L_BEAM_CD_MAX = "HexLightBeamCdMax";   // int max for HUD

    private static final int L_COST_SOLAR_BEAM       = 10;
    private static final int L_COST_SUPER_SOLAR_BEAM = 80;
    private static final int L_BEAM_CD_TICKS       = 70;   // 3.5s
    private static final int L_SUPER_BEAM_CD_TICKS = 240;  // 12s


    // Player entity-data keys for the "flying blast" sim (ticked in HexOrbEffectsController)
    public static final String FRB_KEY_ACTIVE = "HexFRB_Active";
    public static final String FRB_KEY_X      = "HexFRB_X";
    public static final String FRB_KEY_Y      = "HexFRB_Y";
    public static final String FRB_KEY_Z      = "HexFRB_Z";
    public static final String FRB_KEY_DX     = "HexFRB_DX";
    public static final String FRB_KEY_DY     = "HexFRB_DY";
    public static final String FRB_KEY_DZ     = "HexFRB_DZ";
    public static final String FRB_KEY_STEP   = "HexFRB_Step";
    public static final String FRB_KEY_TICKS  = "HexFRB_Ticks";
    public static final String FRB_KEY_DMG    = "HexFRB_Dmg";
    public static final String FRB_KEY_KB     = "HexFRB_KB";
    public static final String FRB_KEY_RAD    = "HexFRB_Rad";
    public static final String FRB_KEY_AOE_RAD = "HexFRB_AoeRad";
    public static final String FRB_KEY_AOE_DMG = "HexFRB_AoeDmg";
    public static final String FRB_KEY_WIL_SNAP = "HexFRB_WilSnap";
    public static final String FRB_KEY_END_BOOM = "HexFRB_EndBoom"; // explode when it reaches end-of-path (no hit)

    private static final int MAX_SHARDS = 5;

    private static void handleOrbAction(EntityPlayerMP p, int action) {
        if (p == null || p.worldObj == null || p.worldObj.isRemote) return;

        // ------------------------------------------------------------------------
        // Void Orb (Null Shell) actions (5-8)
        // ------------------------------------------------------------------------
        if (action >= 5 && action <= 8) {
            boolean ok = false;
            try {
                if (action == 5) ok = HexOrbEffectsController.tryNullShellActiveDash(p);
                else if (action == 6) ok = HexOrbEffectsController.tryNullShellDefenseBuff(p);
                else if (action == 7) ok = HexOrbEffectsController.tryNullShellPushStart(p);
                else if (action == 8) ok = HexOrbEffectsController.tryNullShellPushRelease(p);
            } catch (Throwable t) {
                ok = false;
            }

            // Quick packet debugger: if we get here but the action is denied, print once.
            if (!ok && HexOrbEffectsController.VOID_NULL_SHELL_DEBUG) {
                p.addChatMessage(new net.minecraft.util.ChatComponentText("§8[NSDBG] §7Action " + action + " received (denied)."));
            }
            return;
        }

        // ------------------------------------------------------------------------
        // Darkfire Pill action (9): Blackflame Burn
        // ------------------------------------------------------------------------
        if (action == 9) {
            // Blackflame Spray (Action 9)
            // Use the SAME hitscan sweep pattern as our Solar Beam so point-blank hits register
            // reliably (Thermos included) instead of passing through entities.
            if (!HexOrbEffectsController.tryConsumeDarkfireBlackfireBurn(p)) return;

            fireBlackflameSpray(p);
            return;
        }

        // Darkfire Pill action (10): Big Blackflame Burn (50%)
        if (action == 10) {
            if (!HexOrbEffectsController.tryConsumeDarkfireBlackfireBurnBig(p)) return;
            fireBlackflameSprayBig(p);
            return;
        }

        // Darkfire Pill action (11): Rapid Fire shot (100% hold CTRL)
        if (action == 11) {
            if (!HexOrbEffectsController.tryConsumeDarkfireRapidFireShot(p)) return;
            fireDarkfireRapidFireShot(p);
            return;
        }

        // Darkfire Pill action (12): Rapid Fire stop (release / cancel)
        if (action == 12) {
            HexOrbEffectsController.stopDarkfireRapidFire(p);
            return;
        }



        // Darkfire Pill action (13): Ember Detonation mark shot (single) (10% charge)
        if (action == 13) {
            if (HexOrbEffectsController.tryConsumeDarkfireEmberMarkShot(p)) {
                fireEmberMarkShot(p, false);
            }
            return;
        }

        // Darkfire Pill action (14): Ember Detonation mark shot (multi) (10% charge)
        if (action == 14) {
            if (HexOrbEffectsController.tryConsumeDarkfireEmberMarkShot(p)) {
                fireEmberMarkShot(p, true);
            }
            return;
        }

        // Darkfire Pill action (15): Ember Detonation detonate (client holds action key ~5s, then sends this)
        if (action == 15) {
            HexOrbEffectsController.detonateDarkfireEmberMarks(p);
            return;
        }

        // Darkfire Pill action (16): Shadowflame Trail dash (25% charge)
        // NOTE: implemented in HexOrbEffectsController; this packet just routes the action.
        // Action 16: Darkfire Shadowflame Trail
        if (action == ACTION_DARKFIRE_SHADOWFLAME_TRAIL) {
            try { HexOrbEffectsController.tryDarkfireShadowflameTrailDash(p); } catch (Throwable ignored) {}
            return;
        }

// ------------------------------------------------------------------------
// FRACTURED actions (1-2): flying blasts
// NOTE: Do NOT let a Fractured orb "steal" Solar actions (3-4) when both are equipped.
// ------------------------------------------------------------------------
        if (action == 1 || action == 2) {
            OrbRef fr = findFirstEquippedFracturedRef(p);
            if (fr == null) return;
            ItemStack stack = fr.stack;

            // Socketed fractured gems sometimes lose HexOrbRolled/HexOrbProfile;
            // restamp the identity tags if the shard markers are present.
            if (!isFractured(stack)) return;

            boolean fixed = ensureFracturedTags(stack);
            NBTTagCompound tag = stack.getTagCompound();
            if (fixed && fr.socketed) {
                try { HexSocketAPI.setGemAt(fr.host, fr.socketIndex, stack); } catch (Throwable ignored) {}
            }
            if (tag == null) return;

            int shards = tag.getInteger(TAG_FR_SHARDS);

            // Cooldown gate (shown by HUD bar)
            int cd = tag.getInteger(TAG_FR_SNAP);
            if (cd > 0) return;

            // If a blast is already active, ignore (prevents spam)
            if (p.getEntityData().getBoolean(FRB_KEY_ACTIVE)) return;

            if (action == 1) {
                // Double-tap (1 shard) -> longer-range flying blast (no AOE)
                if (shards < 1) return;
                shards -= 1;

                // A bit farther + snappier
                double rangeBlocks = 32.0D;
                double stepBlocks  = 1.35D;

                float  dmg = computeFrbDamageFromWill(p, false);
                double kb  = 0.55D;

                // Collision size (visual size is handled by particles in HexOrbEffectsController)
                double rad = 0.60D;

                // Add a small AOE burst on impact, and also use it for end-of-path explosion if nothing is hit.
                double aoeRad = 3.65D;
                float  aoeDmg = dmg * 0.60F;

                startFlyingBlast(p, rangeBlocks, stepBlocks, dmg, kb, rad, aoeRad, aoeDmg);
                startSnap(tag, 120);

            } else if (action == 2) {
                // Triple-tap (MAX shards) -> bigger flying blast + AOE explosion
                if (shards < MAX_SHARDS) return;

                shards = 0;

                double rangeBlocks = 40.0D;
                double stepBlocks  = 1.45D;

                float  dmg = computeFrbDamageFromWill(p, true);
                double kb  = 0.75D;

                double rad = 0.85D;

                double aoeRad = 5.25D;
                float aoeDmg  = dmg * 0.80F;

                startFlyingBlast(p, rangeBlocks, stepBlocks, dmg, kb, rad, aoeRad, aoeDmg);
                startSnap(tag, 240);

            } else {
                return;
            }

            // Save shards back to the orb
            if (shards < 0) shards = 0;
            if (shards > MAX_SHARDS) shards = MAX_SHARDS;

            tag.setInteger(TAG_FR_SHARDS, shards);
            stack.setTagCompound(tag);

            // If socketed: persist the modified orb back into the host socket
            if (fr.socketed) {
                try { HexSocketAPI.setGemAt(fr.host, fr.socketIndex, stack); } catch (Throwable ignored) {}
            }

            p.inventory.markDirty();
            p.inventoryContainer.detectAndSendChanges();
            return;
        }

// Not fractured -> try SOLAR Light orb
        if (action != 3 && action != 4) return;

        OrbRef sr = findFirstEquippedSolarLightRef(p);
        ItemStack solar = (sr != null) ? sr.stack : null;
        if (solar == null) return;

        NBTTagCompound tag = solar.getTagCompound();
        if (tag == null) return;

        // Socket stations can strip TAG_ROLLED / TAG_PROFILE while keeping Light keys.
        // Restamp so both client HUD and server validators keep behaving.
        ensureSolarTags(solar);
        tag = solar.getTagCompound();
        if (tag == null) return;

        String type = tag.getString(TAG_LIGHT_TYPE);
        if (type == null || !"Solar".equalsIgnoreCase(type.trim())) return;

        int rad = tag.getInteger(TAG_LIGHT_RAD);
        if (rad < 0) rad = 0;
        if (rad > 100) rad = 100;

        int cd = tag.getInteger(TAG_L_BEAM_CD);
        if (cd > 0) return;

        int cost = (action == 4) ? L_COST_SUPER_SOLAR_BEAM : L_COST_SOLAR_BEAM;
        if (rad < cost) return;

        // Spend radiance and start cooldown
        rad -= cost;
        if (rad < 0) rad = 0;
        tag.setInteger(TAG_LIGHT_RAD, rad);

        int cdTicks = (action == 4) ? L_SUPER_BEAM_CD_TICKS : L_BEAM_CD_TICKS;
        tag.setInteger(TAG_L_BEAM_CD, cdTicks);
        tag.setInteger(TAG_L_BEAM_CD_MAX, cdTicks);

        // Fire beam (instant ray + DBC-scaled damage)
        fireSolarBeam(p, action == 4);

        solar.setTagCompound(tag);

        // If socketed: persist back into host socket
        if (sr != null && sr.socketed) {
            try { HexSocketAPI.setGemAt(sr.host, sr.socketIndex, solar); } catch (Throwable ignored) {}
        }

        p.inventory.markDirty();
        p.inventoryContainer.detectAndSendChanges();
    }



// ---------------------------------------------------------------------
// SOLAR beam (instant ray; DBC-first damage)
// ---------------------------------------------------------------------


    // ---------------------------------------------------------------------
    // Socket-aware orb lookup (so actions work when orbs are stored in sockets)
    // ---------------------------------------------------------------------

    private static final class OrbRef {
        public final ItemStack stack;   // the orb stack (stackSize expected 1)
        public final ItemStack host;    // host item containing the socket (null if directly held/worn)
        public final int socketIndex;   // socket index in host (only valid if socketed)
        public final boolean socketed;

        public OrbRef(ItemStack stack, ItemStack host, int socketIndex, boolean socketed) {
            this.stack = stack;
            this.host = host;
            this.socketIndex = socketIndex;
            this.socketed = socketed;
        }
    }

    private static OrbRef findFirstEquippedSolarLightRef(EntityPlayerMP p) {
        if (p == null) return null;

        // Held
        ItemStack held = p.getCurrentEquippedItem();
        if (isSolarLight(held)) return new OrbRef(held, null, -1, false);

        OrbRef r = findSolarInSockets(held);
        if (r != null) return r;

        // Armor
        if (p.inventory != null && p.inventory.armorInventory != null) {
            for (int i = 0; i < p.inventory.armorInventory.length; i++) {
                ItemStack s = p.inventory.armorInventory[i];
                if (isSolarLight(s)) return new OrbRef(s, null, -1, false);

                r = findSolarInSockets(s);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static OrbRef findSolarInSockets(ItemStack host) {
        if (host == null) return null;
        try {
            int filled = HexSocketAPI.getSocketsFilled(host);
            for (int i = 0; i < filled; i++) {
                ItemStack gem = HexSocketAPI.getGemAt(host, i);
                if (isSolarLight(gem)) return new OrbRef(gem, host, i, true);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static OrbRef findFirstEquippedFracturedRef(EntityPlayerMP p) {
        if (p == null) return null;

        // Held
        ItemStack cur = p.getCurrentEquippedItem();
        if (isFractured(cur)) return new OrbRef(cur, null, -1, false);

        OrbRef r = findFracturedInSockets(cur);
        if (r != null) return r;

        // Armor
        if (p.inventory != null && p.inventory.armorInventory != null) {
            for (int i = 0; i < p.inventory.armorInventory.length; i++) {
                ItemStack s = p.inventory.armorInventory[i];
                if (isFractured(s)) return new OrbRef(s, null, -1, false);

                r = findFracturedInSockets(s);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static OrbRef findFracturedInSockets(ItemStack host) {
        if (host == null) return null;
        try {
            int filled = HexSocketAPI.getSocketsFilled(host);
            for (int i = 0; i < filled; i++) {
                ItemStack gem = HexSocketAPI.getGemAt(host, i);
                if (isFractured(gem)) return new OrbRef(gem, host, i, true);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static ItemStack findFirstEquippedSolarLight(EntityPlayerMP p) {
        if (p == null) return null;

        // Held
        ItemStack held = p.getCurrentEquippedItem();
        if (isSolarLight(held)) return held;

        // Socketed on held host
        try {
            int filled = HexSocketAPI.getSocketsFilled(held);
            for (int i = 0; i < filled; i++) {
                ItemStack gem = HexSocketAPI.getGemAt(held, i);
                if (isSolarLight(gem)) return gem;
            }
        } catch (Throwable ignored) {}

        // Armor (+ sockets)
        if (p.inventory != null && p.inventory.armorInventory != null) {
            for (int i = 0; i < p.inventory.armorInventory.length; i++) {
                ItemStack s = p.inventory.armorInventory[i];
                if (isSolarLight(s)) return s;

                try {
                    int filled = HexSocketAPI.getSocketsFilled(s);
                    for (int j = 0; j < filled; j++) {
                        ItemStack gem = HexSocketAPI.getGemAt(s, j);
                        if (isSolarLight(gem)) return gem;
                    }
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private static boolean isSolarLight(ItemStack stack) {
        if (stack == null) return false;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return false;

        // Minimal identity check: it must claim to be a Solar Light orb.
        String type = null;
        try { type = tag.getString(TAG_LIGHT_TYPE); } catch (Throwable ignored) {}
        if (type == null || !"Solar".equalsIgnoreCase(type.trim())) return false;

        // Normal path: fully rolled light orb.
        if (tag.getBoolean(TAG_ROLLED)) {
            String prof = tag.getString(TAG_PROFILE);
            if (prof != null && prof.startsWith("LIGHT_")) return true;
        }

        // Socket-stripped path: stations sometimes keep Light keys but drop TAG_ROLLED / TAG_PROFILE.
        // If it has any Light payload key, accept it (server will still validate radiance/cooldown).
        if (tag.hasKey(TAG_LIGHT_RAD) || tag.hasKey(TAG_L_BEAM_CD) || tag.hasKey(TAG_L_BEAM_CD_MAX)) return true;

        // Last resort: if it at least has the type key, treat as Solar (server logic is safe).
        return tag.hasKey(TAG_LIGHT_TYPE);
    }



    private static boolean hasEvolvedLore(ItemStack stack) {
        if (stack == null) return false;
        try {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null) return false;

            // Common boolean NBT flags (if you decide to add one later)
            if (tag.hasKey("HexEvolved")) {
                try { if (tag.getBoolean("HexEvolved")) return true; } catch (Throwable ignored) {}
            }
            if (tag.hasKey("Evolved")) {
                try { if (tag.getBoolean("Evolved")) return true; } catch (Throwable ignored) {}
            }

            // Lore scan (works with most "§" formatted lore lines)
            if (tag.hasKey("display", 10)) {
                NBTTagCompound disp = tag.getCompoundTag("display");
                if (disp != null && disp.hasKey("Lore", 9)) {
                    NBTTagList lore = disp.getTagList("Lore", 8); // 8 = string
                    for (int i = 0; i < lore.tagCount(); i++) {
                        String s = lore.getStringTagAt(i);
                        if (s == null) continue;
                        String low = s.toLowerCase();
                        if (low.contains("evolved") || low.contains("ascended") || low.contains("evolution")) return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static double getStrengthEffective(EntityPlayerMP p){
        double v = 0D;
        try { v = HexDBCProcDamageProvider.getStrengthEffective(p); } catch (Throwable ignored) {}
        if (v < 0D) v = 0D;
        return v;
    }

    private static double getWillPowerEffective(EntityPlayerMP p){
        double v = 0D;
        try { v = HexDBCProcDamageProvider.getWillPowerEffective(p); } catch (Throwable ignored) {}
        if (v < 0D) v = 0D;
        return v;
    }

    private static float computeSolarBeamDamage(EntityPlayerMP p, boolean superBeam){
        // Match Entropy's scaling philosophy: compute a REAL DBC "Body" damage number
        // from an effective stat (includes form multies when available), then feed that
        // through the same DBC bridge damage applier.
        //
        // We use WillPower here (requested), not event damage.
        double wil = Math.floor(getWillPowerEffective(p));

        // Tuned to land in the "few thousand" range for mid/high WIL,
        // without exploding into millions like the old raw proc-scale numbers.
        double base = 1100D + (wil * 0.14D);

        if (superBeam) base *= 3.35D;

        // sane clamps in DBC Body units
        if (base < 1500D) base = 1500D;
        if (superBeam) {
            if (base > 450000D) base = 450000D;
        } else {
            if (base > 160000D) base = 160000D;
        }
        return (float) base;
    }


    // Darkfire (Blackflame Burn) tick damage.
    // This is intentionally a "small but real" DBC Body hit so action 9 always hurts even if the
    // burn/DoT system is temporarily disabled. Your main DoT can still be handled inside HexOrbEffectsController.
    private static float computeBlackfireBurnTickDamage(EntityPlayerMP p){
        double str = 0D;
        try { str = HexDBCProcDamageProvider.getStrengthEffective(p); } catch (Throwable ignored) {}
        if (str < 0D) str = 0D;

        // Per-second-ish body damage (moderate). Tune here if you want hotter/cooler burn.
        double base = 4000D + (str * 0.90D);

        // clamps in DBC Body units
        if (base < 6000D) base = 6000D;
        if (base > 180000D) base = 180000D;

        return (float) base;
    }


    private static final String TAG_PLAYER_PERSISTED = "PlayerPersisted";
    private static final String TAG_DBC_BODY = "jrmcBdy";

    // Same DBC bridge applier used by Entropy proc damage (via HexOrbEffectsController.DAMAGE_APPLIER)
    private static final HexDBCBridgeDamageApplier SOLAR_DAMAGE_APPLIER = new HexDBCBridgeDamageApplier();

    // Reuse the same bridge applier for Darkfire burn hits (DBC body first; vanilla fallback).
    private static final HexDBCBridgeDamageApplier DARKFIRE_DAMAGE_APPLIER = new HexDBCBridgeDamageApplier();

    /**
     * Apply Solar Beam damage using the same DBC bridge path that Entropy uses.
     * (Body damage for DBC targets; vanilla fallback for non-DBC.)
     */
    private static void applySolarBeamDamageOrFallback(EntityPlayerMP src, EntityLivingBase target, float bodyDamage){
        if (src == null || target == null || bodyDamage <= 0f) return;

        // Entropy's proc path bypasses i-frames so effects always land; we do the same here.
        try {
            target.hurtResistantTime = 0;
            target.hurtTime = 0;
        } catch (Throwable ignored) {}

        try {
            SOLAR_DAMAGE_APPLIER.deal(src, target, bodyDamage);
            return;
        } catch (Throwable ignored) {}

        // Last resort fallback (direct body subtraction / vanilla hearts)
        applyDbcBodyDamageOrFallback(src, target, bodyDamage);
    }

    /**
     * Apply the guaranteed "Blackflame Burn" hit damage (DBC body first; vanilla fallback).
     * This is a single tick-sized hit; your actual DoT can live in HexOrbEffectsController.
     */
    private static void applyDarkfireBurnDamageOrFallback(EntityPlayerMP src, EntityLivingBase target, float bodyDamage){
        if (src == null || target == null || bodyDamage <= 0f) return;

        try {
            target.hurtResistantTime = 0;
            target.hurtTime = 0;
        } catch (Throwable ignored) {}

        try {
            DARKFIRE_DAMAGE_APPLIER.deal(src, target, bodyDamage);
            return;
        } catch (Throwable ignored) {}

        applyDbcBodyDamageOrFallback(src, target, bodyDamage);
    }

    // DBC-first body damage; vanilla fallback
    private static void applyDbcBodyDamageOrFallback(EntityPlayerMP src, EntityLivingBase target, float dbcDamage){
        if (src == null || target == null || dbcDamage <= 0f) return;

        try {
            NBTTagCompound ed = target.getEntityData();
            if (ed != null) {
                boolean hasPersisted = ed.hasKey(TAG_PLAYER_PERSISTED, 10);
                NBTTagCompound persisted = hasPersisted ? ed.getCompoundTag(TAG_PLAYER_PERSISTED) : null;

                NBTTagCompound store = null;
                boolean storeIsPersisted = false;

                if (persisted != null && persisted.hasKey(TAG_DBC_BODY)) {
                    store = persisted;
                    storeIsPersisted = true;
                } else if (ed.hasKey(TAG_DBC_BODY)) {
                    store = ed;
                }

                if (store != null) {
                    int cur = store.getInteger(TAG_DBC_BODY);
                    int next = (int) Math.max(0, (double)cur - (double)dbcDamage);
                    store.setInteger(TAG_DBC_BODY, next);

                    if (storeIsPersisted && hasPersisted) {
                        ed.setTag(TAG_PLAYER_PERSISTED, store);
                    }

                    // Ensure kills are respected
                    if (next <= 0) {
                        try { target.setHealth(0.0F); } catch (Throwable ignored) {}
                        try { target.attackEntityFrom(DamageSource.causePlayerDamage(src), 1000000.0F); } catch (Throwable ignored) {}
                    }
                    return;
                }
            }
        } catch (Throwable ignored) {}

        // Vanilla fallback (convert big numbers into hearts)
        float vanilla = dbcDamage / 6500.0F;
        if (vanilla < 2.0F) vanilla = 2.0F;
        if (vanilla > 22.0F) vanilla = 22.0F;
        try { target.attackEntityFrom(DamageSource.causePlayerDamage(src), vanilla); } catch (Throwable ignored) {}
    }

    // ---------------------------------------------------------------------
    // Entropy-style proc damage (uses your shared provider if present)
    // ---------------------------------------------------------------------

    private static volatile Method CACHED_PROC_DAMAGE;

    /**
     * Tries to apply damage using HexDBCProcDamageProvider's "proc" damage method (the same path your Entropy uses).
     * If we can't find/invoke it, we fall back to direct DBC body subtraction (and then vanilla hearts).
     */
    private static void applyEntropyStyleDamageOrFallback(EntityPlayerMP src, EntityLivingBase target, float rawDamage){
        if (src == null || target == null || rawDamage <= 0f) return;
        if (tryProcDamageProvider(src, target, rawDamage)) return;

        // Fallback safety: the proc path usually interprets "rawDamage" in your orb-proc scale.
        // If that method can't be resolved for some reason, convert to a reasonable BODY hit.
        float fallbackBody = rawDamage * 0.0060F; // ~4.9k from ~820k raw
        if (fallbackBody < 800.0F) fallbackBody = 800.0F;
        if (fallbackBody > 45000.0F) fallbackBody = 45000.0F;
        applyDbcBodyDamageOrFallback(src, target, fallbackBody);
    }

    private static boolean tryProcDamageProvider(EntityPlayerMP src, EntityLivingBase target, float rawDamage){
        try {
            Method m = CACHED_PROC_DAMAGE;
            if (m == null) {
                m = resolveProcDamageProviderMethod();
                CACHED_PROC_DAMAGE = m;
            }
            if (m == null) return false;

            Class<?>[] pt = m.getParameterTypes();
            Object[] args = new Object[pt.length];

            // We only resolve methods that match (src, target, amount[, extra])
            args[0] = src;
            args[1] = target;

            // amount
            if (pt[2] == float.class || pt[2] == Float.class) {
                args[2] = rawDamage;
            } else if (pt[2] == double.class || pt[2] == Double.class) {
                args[2] = (double) rawDamage;
            } else if (pt[2] == int.class || pt[2] == Integer.class) {
                args[2] = (int) Math.round(rawDamage);
            } else {
                return false;
            }

            // optional 4th param (best-effort)
            if (pt.length >= 4) {
                Class<?> extra = pt[3];
                if (extra == boolean.class || extra == Boolean.class) {
                    args[3] = Boolean.TRUE;
                } else if (DamageSource.class.isAssignableFrom(extra)) {
                    args[3] = DamageSource.causePlayerDamage(src);
                } else if (extra == int.class || extra == Integer.class) {
                    args[3] = 0;
                } else if (extra == float.class || extra == Float.class) {
                    args[3] = 0.0F;
                } else if (extra == double.class || extra == Double.class) {
                    args[3] = 0.0D;
                } else {
                    // Unknown extra param type
                    return false;
                }
            }

            Object ret = m.invoke(null, args);
            if (ret instanceof Boolean) return ((Boolean) ret).booleanValue();
            return true; // void / numeric / other => assume it succeeded
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Resolve a static "proc damage" style method on HexDBCProcDamageProvider.
     * We do this reflectively so your provider can rename it without breaking compilation.
     */
    private static Method resolveProcDamageProviderMethod(){
        try {
            Method[] ms = HexDBCProcDamageProvider.class.getMethods();
            Method best = null;
            int bestScore = -1;

            for (int i = 0; i < ms.length; i++) {
                Method m = ms[i];
                if (m == null) continue;
                if (!Modifier.isStatic(m.getModifiers())) continue;

                Class<?>[] pt = m.getParameterTypes();
                if (pt == null || pt.length < 3 || pt.length > 4) continue;

                // (EntityPlayerMP src, EntityLivingBase target, number amount[, extra])
                if (!EntityPlayerMP.class.isAssignableFrom(pt[0])) continue;
                if (!EntityLivingBase.class.isAssignableFrom(pt[1])) continue;

                Class<?> amt = pt[2];
                boolean okAmt = (amt == float.class || amt == Float.class || amt == double.class || amt == Double.class || amt == int.class || amt == Integer.class);
                if (!okAmt) continue;

                int score = 0;
                String n = m.getName();
                if (n != null) {
                    String ln = n.toLowerCase();
                    if (ln.equals("applyowneddamage")) score += 1200;
                    if (ln.contains("entropy")) score += 1100;
                    if (ln.contains("owned")) score += 900;
                    if (ln.contains("proc")) score += 800;
                    if (ln.contains("damage")) score += 700;
                    if (ln.contains("apply")) score += 200;
                    if (ln.contains("do")) score += 80;
                }

                if (pt.length == 3) score += 60;
                if (amt == float.class || amt == Float.class) score += 30;
                if (amt == double.class || amt == Double.class) score += 20;

                // If there's an extra param, only accept common safe types
                if (pt.length == 4) {
                    Class<?> extra = pt[3];
                    boolean okExtra = (extra == boolean.class || extra == Boolean.class || DamageSource.class.isAssignableFrom(extra)
                            || extra == int.class || extra == Integer.class || extra == float.class || extra == Float.class
                            || extra == double.class || extra == Double.class);
                    if (!okExtra) continue;
                }

                if (score > bestScore) {
                    bestScore = score;
                    best = m;
                }
            }
            if (best != null) {
                try { best.setAccessible(true); } catch (Throwable ignored) {}
            }
            return best;
        } catch (Throwable ignored) {}
        return null;
    }

    private static void fireSolarBeam(EntityPlayerMP p, boolean superBeam) {
        if (p == null || p.worldObj == null) return;

        final ItemStack solar = findFirstEquippedSolarLight(p);

        double range = superBeam ? 56.0D : 40.0D;
        double step  = 1.0D;
        double rad   = superBeam ? 1.35D : 0.85D;

        Vec3 look = p.getLookVec();
        if (look == null) return;

        double dx = look.xCoord;
        double dy = look.yCoord;
        double dz = look.zCoord;

        double norm = Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (norm < 1.0E-4D) return;
        dx /= norm; dy /= norm; dz /= norm;

        double x = p.posX;
        double y = p.posY + p.getEyeHeight() - 0.10D;
        double z = p.posZ;

        // start slightly forward
        x += dx * 0.55D;
        y += dy * 0.55D;
        z += dz * 0.55D;

        final double sx = x;
        final double sy = y;
        final double sz = z;

        EntityLivingBase hit = null;
        double hitX = x, hitY = y, hitZ = z;

        int steps = (int) Math.ceil(range / step);
        for (int i = 0; i < steps; i++) {
            x += dx * step;
            y += dy * step;
            z += dz * step;

            AxisAlignedBB box = AxisAlignedBB.getBoundingBox(
                    x - rad, y - rad, z - rad,
                    x + rad, y + rad, z + rad
            );

            @SuppressWarnings("unchecked")
            List<Entity> list = p.worldObj.getEntitiesWithinAABBExcludingEntity(p, box);
            if (list != null && !list.isEmpty()) {
                for (int k = 0; k < list.size(); k++) {
                    Entity e = list.get(k);
                    if (!(e instanceof EntityLivingBase)) continue;
                    if (e == p) continue;
                    EntityLivingBase elb = (EntityLivingBase) e;
                    if (elb.isEntityAlive()) {
                        hit = elb;
                        hitX = x; hitY = y; hitZ = z;
                        break;
                    }
                }
            }

            if (hit != null) break;
        }

        // Beam endpoint for FX
        final double ex = (hit != null) ? hitX : x;
        final double ey = (hit != null) ? hitY : y;
        final double ez = (hit != null) ? hitZ : z;

        // Broadcast simple beam FX to nearby players (no extra packet registration)
        broadcastSolarBeamFx(p, sx, sy, sz, ex, ey, ez, superBeam);

        // Also send a textured beam render packet so everyone sees the rarity-style beam.
        broadcastSolarBeamRarityBeam(p, sx, sy, sz, ex, ey, ez, superBeam, solar);


        float dmg = computeSolarBeamDamage(p, superBeam);

        if (hit != null) {
            applySolarBeamDamageOrFallback(p, hit, dmg);

            // knockback
            double kb = superBeam ? 0.95D : 0.55D;
            hit.motionX += dx * kb;
            hit.motionY += dy * (kb * 0.25D);
            hit.motionZ += dz * kb;
            hit.velocityChanged = true;

            // Super beam: small AOE burst around impact
            if (superBeam) {
                double aoe = 4.25D;
                AxisAlignedBB aoeBox = AxisAlignedBB.getBoundingBox(
                        hitX - aoe, hitY - aoe, hitZ - aoe,
                        hitX + aoe, hitY + aoe, hitZ + aoe
                );
                @SuppressWarnings("unchecked")
                List<Entity> list = p.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, aoeBox);
                if (list != null) {
                    for (int k = 0; k < list.size(); k++) {
                        Entity e = list.get(k);
                        if (!(e instanceof EntityLivingBase)) continue;
                        EntityLivingBase elb = (EntityLivingBase) e;
                        if (elb == p || elb == hit) continue;
                        if (!elb.isEntityAlive()) continue;

                        double ddx = elb.posX - hitX;
                        double ddy = (elb.posY + elb.height * 0.5D) - hitY;
                        double ddz = elb.posZ - hitZ;
                        double dist = Math.sqrt(ddx*ddx + ddy*ddy + ddz*ddz);
                        if (dist > aoe || dist < 0.001D) continue;

                        float falloff = (float) Math.max(0.15D, 1.0D - (dist / aoe));
                        applySolarBeamDamageOrFallback(p, elb, dmg * 0.70F * falloff);

                        double kbo = 0.60D * falloff;
                        elb.motionX += (ddx / dist) * kbo;
                        elb.motionY += (ddy / dist) * (kbo * 0.20D);
                        elb.motionZ += (ddz / dist) * kbo;
                        elb.velocityChanged = true;
                    }
                }
            }
        }
    }
    private static void fireBlackflameSpray(EntityPlayerMP p) {
        if (p == null || p.worldObj == null) return;
        if (p.worldObj.isRemote) return;

        final double maxRange = (double) HexOrbEffectsController.DARKFIRE_BURN_RANGE_BLOCKS;
        final double stepSize = 0.65D;     // sweep resolution (smaller = more reliable point-blank hits)
        final double hitRadius = 0.90D;    // sphere radius used to detect entities along the ray

        Vec3 look = null;
        try { look = p.getLookVec(); } catch (Throwable ignored) {}
        if (look == null) look = Vec3.createVectorHelper(0, 0, 1);

        double lx = look.xCoord, ly = look.yCoord, lz = look.zCoord;
        double llen = Math.sqrt(lx*lx + ly*ly + lz*lz);
        if (llen < 1.0E-6D) { lx = 0; ly = 0; lz = 1; llen = 1; }
        lx /= llen; ly /= llen; lz /= llen;

        // Eye position + a small forward offset so we don't start inside the player.
        Vec3 eye = Vec3.createVectorHelper(p.posX, p.posY + (double) p.getEyeHeight(), p.posZ);
        Vec3 start = Vec3.createVectorHelper(
                eye.xCoord + lx * 0.55D,
                eye.yCoord + ly * 0.55D,
                eye.zCoord + lz * 0.55D
        );

        Vec3 intendedEnd = Vec3.createVectorHelper(
                start.xCoord + lx * maxRange,
                start.yCoord + ly * maxRange,
                start.zCoord + lz * maxRange
        );

        // Clamp end to first solid block (ignore blocks without bounding boxes like tall grass).
        net.minecraft.util.MovingObjectPosition mopBlock = null;
        Vec3 end = intendedEnd;
        try {
            mopBlock = p.worldObj.rayTraceBlocks(eye, intendedEnd);
            if (mopBlock != null && mopBlock.hitVec != null) {
                end = mopBlock.hitVec;
            }
        } catch (Throwable ignored) {}

        // Sweep for the first living entity along the segment start->end (Solar Beam style).
        EntityLivingBase hitEnt = null;
        Vec3 hitVec = null;

        double dx = end.xCoord - start.xCoord;
        double dy = end.yCoord - start.yCoord;
        double dz = end.zCoord - start.zCoord;
        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (dist < 1.0E-4D) dist = 1.0E-4D;

        int steps = (int) Math.ceil(dist / stepSize);
        if (steps < 1) steps = 1;

        // Pre-check at the start sphere (helps true point-blank).
        try {
            AxisAlignedBB bb0 = AxisAlignedBB.getBoundingBox(
                    start.xCoord - hitRadius, start.yCoord - hitRadius, start.zCoord - hitRadius,
                    start.xCoord + hitRadius, start.yCoord + hitRadius, start.zCoord + hitRadius
            );
            List l0 = p.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, bb0);
            if (l0 != null && !l0.isEmpty()) {
                double best = 9.9E9D;
                for (int i = 0; i < l0.size(); i++) {
                    Object o = l0.get(i);
                    if (!(o instanceof EntityLivingBase)) continue;
                    EntityLivingBase e = (EntityLivingBase) o;
                    if (e == p || e.isDead || e.getHealth() <= 0.0F) continue;
                    double d = e.getDistanceSqToEntity(p);
                    if (d < best) { best = d; hitEnt = e; hitVec = start; }
                }
            }
        } catch (Throwable ignored) {}

        // Sweep forward if not already found.
        if (hitEnt == null) {
            for (int s = 0; s <= steps; s++) {
                double t = (double) s / (double) steps;
                double px = start.xCoord + dx * t;
                double py = start.yCoord + dy * t;
                double pz = start.zCoord + dz * t;

                AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(
                        px - hitRadius, py - hitRadius, pz - hitRadius,
                        px + hitRadius, py + hitRadius, pz + hitRadius
                );

                List list = null;
                try { list = p.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, bb); } catch (Throwable ignored) {}
                if (list == null || list.isEmpty()) continue;

                double best = 9.9E9D;
                EntityLivingBase bestEnt = null;
                for (int i = 0; i < list.size(); i++) {
                    Object o = list.get(i);
                    if (!(o instanceof EntityLivingBase)) continue;
                    EntityLivingBase e = (EntityLivingBase) o;
                    if (e == p || e.isDead || e.getHealth() <= 0.0F) continue;
                    double d = e.getDistanceSq(px, py, pz);
                    if (d < best) { best = d; bestEnt = e; }
                }

                if (bestEnt != null) {
                    hitEnt = bestEnt;
                    hitVec = Vec3.createVectorHelper(px, py, pz);
                    // Stop the visual at the hit point.
                    end = hitVec;
                    break;
                }
            }
        }

        // Broadcast FX (line + a "burst" at the hit point).
        try {
            SimpleNetworkWrapper net = resolveNetWrapper();
            if (net != null) {
                net.sendToAllAround(
                        new PacketBlackflameSprayFX(
                                (float) start.xCoord, (float) start.yCoord, (float) start.zCoord,
                                (float) end.xCoord,   (float) end.yCoord,   (float) end.zCoord
                        ),
                        new NetworkRegistry.TargetPoint(p.dimension, p.posX, p.posY, p.posZ, 64.0D)
                );

                if (hitVec != null) {
                    net.sendToAllAround(
                            new PacketBlackflameSprayFX(
                                    (float) hitVec.xCoord, (float) hitVec.yCoord, (float) hitVec.zCoord,
                                    (float) hitVec.xCoord, (float) hitVec.yCoord, (float) hitVec.zCoord
                            ),
                            new NetworkRegistry.TargetPoint(p.dimension, p.posX, p.posY, p.posZ, 64.0D)
                    );
                }
            }
        } catch (Throwable ignored) {}

        // Apply hit / burn
        if (hitEnt != null) {
            try { HexOrbEffectsController.applyDarkfireBlackfireBurnHit(p, hitEnt); } catch (Throwable ignored) {}

            // "Fire explode": small AoE burst that tags nearby living entities too (optional, non-griefing).
            if (hitVec != null) {
                try {
                    final double r = 2.75D;
                    AxisAlignedBB aoe = AxisAlignedBB.getBoundingBox(
                            hitVec.xCoord - r, hitVec.yCoord - r, hitVec.zCoord - r,
                            hitVec.xCoord + r, hitVec.yCoord + r, hitVec.zCoord + r
                    );
                    List near = p.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, aoe);
                    if (near != null && !near.isEmpty()) {
                        for (int i = 0; i < near.size(); i++) {
                            Object o = near.get(i);
                            if (!(o instanceof EntityLivingBase)) continue;
                            EntityLivingBase e = (EntityLivingBase) o;
                            if (e == p || e == hitEnt || e.isDead || e.getHealth() <= 0.0F) continue;
                            try { HexOrbEffectsController.applyDarkfireBlackfireBurnHit(p, e); } catch (Throwable ignored2) {}
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } else {
            // Missed entity: place shadow fire where we hit a block (if any).
            if (mopBlock != null) {
                tryPlaceShadowFireAtImpact(p, mopBlock);
            }
        }
    }
    private static void fireBlackflameSprayBig(EntityPlayerMP p) {
        if (p == null || p.worldObj == null) return;
        if (p.worldObj.isRemote) return;

        // Slightly more range and a much fatter hit tube than action 9.
        final double maxRange = (double) HexOrbEffectsController.DARKFIRE_BURN_RANGE_BLOCKS * 1.10D;
        final double stepSize = 0.55D;    // sweep resolution (more reliable close range)
        final double hitRadius = 1.35D;   // larger sphere radius used to detect entities along the ray

        Vec3 look = null;
        try { look = p.getLookVec(); } catch (Throwable ignored) {}
        if (look == null) look = Vec3.createVectorHelper(0, 0, 1);

        double lx = look.xCoord, ly = look.yCoord, lz = look.zCoord;
        double llen = Math.sqrt(lx*lx + ly*ly + lz*lz);
        if (llen < 1.0E-6D) { lx = 0; ly = 0; lz = 1; llen = 1; }
        lx /= llen; ly /= llen; lz /= llen;

        Vec3 eye = Vec3.createVectorHelper(p.posX, p.posY + (double) p.getEyeHeight(), p.posZ);
        Vec3 start = Vec3.createVectorHelper(
                eye.xCoord + lx * 0.55D,
                eye.yCoord + ly * 0.55D,
                eye.zCoord + lz * 0.55D
        );

        Vec3 intendedEnd = Vec3.createVectorHelper(
                start.xCoord + lx * maxRange,
                start.yCoord + ly * maxRange,
                start.zCoord + lz * maxRange
        );

        net.minecraft.util.MovingObjectPosition mopBlock = null;
        Vec3 end = intendedEnd;
        try {
            mopBlock = p.worldObj.rayTraceBlocks(eye, intendedEnd);
            if (mopBlock != null && mopBlock.hitVec != null) {
                end = mopBlock.hitVec;
            }


        } catch (Throwable ignored) {}

        EntityLivingBase hitEnt = null;
        Vec3 hitVec = null;

        double dx = end.xCoord - start.xCoord;
        double dy = end.yCoord - start.yCoord;
        double dz = end.zCoord - start.zCoord;
        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (dist < 1.0E-4D) dist = 1.0E-4D;

        int steps = (int) Math.ceil(dist / stepSize);
        if (steps < 1) steps = 1;

        // Pre-check at the start sphere (helps true point-blank).
        try {
            AxisAlignedBB bb0 = AxisAlignedBB.getBoundingBox(
                    start.xCoord - hitRadius, start.yCoord - hitRadius, start.zCoord - hitRadius,
                    start.xCoord + hitRadius, start.yCoord + hitRadius, start.zCoord + hitRadius
            );
            List l0 = p.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, bb0);
            if (l0 != null && !l0.isEmpty()) {
                double best = 9.9E9D;
                for (int i = 0; i < l0.size(); i++) {
                    Object o = l0.get(i);
                    if (!(o instanceof EntityLivingBase)) continue;
                    EntityLivingBase e = (EntityLivingBase) o;
                    if (e == p || e.isDead || e.getHealth() <= 0.0F) continue;
                    double d = e.getDistanceSqToEntity(p);
                    if (d < best) { best = d; hitEnt = e; hitVec = start; }
                }
            }
        } catch (Throwable ignored) {}

        if (hitEnt == null) {
            for (int s = 0; s <= steps; s++) {
                double t = (double) s / (double) steps;
                double px = start.xCoord + dx * t;
                double py = start.yCoord + dy * t;
                double pz = start.zCoord + dz * t;

                AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(
                        px - hitRadius, py - hitRadius, pz - hitRadius,
                        px + hitRadius, py + hitRadius, pz + hitRadius
                );

                List list = null;
                try { list = p.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, bb); } catch (Throwable ignored) {}
                if (list == null || list.isEmpty()) continue;

                double best = 9.9E9D;
                EntityLivingBase bestEnt = null;
                for (int i = 0; i < list.size(); i++) {
                    Object o = list.get(i);
                    if (!(o instanceof EntityLivingBase)) continue;
                    EntityLivingBase e = (EntityLivingBase) o;
                    if (e == p || e.isDead || e.getHealth() <= 0.0F) continue;
                    double d = e.getDistanceSq(px, py, pz);
                    if (d < best) { best = d; bestEnt = e; }
                }

                if (bestEnt != null) {
                    hitEnt = bestEnt;
                    hitVec = Vec3.createVectorHelper(px, py, pz);
                    end = hitVec;
                    break;
                }
            }
        }

        // FX: multiple offset streaks so it looks like a thicker flame.
        try {
            SimpleNetworkWrapper net = resolveNetWrapper();
            if (net != null) {
                double ux = 0D, uy = 1D, uz = 0D;

                double rx = ly*uz - lz*uy;
                double ry = lz*ux - lx*uz;
                double rz = lx*uy - ly*ux;
                double rlen = Math.sqrt(rx*rx + ry*ry + rz*rz);
                if (rlen < 1.0E-6D) { rx = 1; ry = 0; rz = 0; rlen = 1; }
                rx /= rlen; ry /= rlen; rz /= rlen;

                double vx = ry*lz - rz*ly;
                double vy = rz*lx - rx*lz;
                double vz = rx*ly - ry*lx;
                double vlen = Math.sqrt(vx*vx + vy*vy + vz*vz);
                if (vlen < 1.0E-6D) { vx = 0; vy = 1; vz = 0; vlen = 1; }
                vx /= vlen; vy /= vlen; vz /= vlen;

                double offR = 0.22D;
                double offU = 0.18D;

                sendBlackflameSprayFX(net, p, start, end, 0D, 0D, 0D);
                sendBlackflameSprayFX(net, p, start, end, rx*offR, ry*offR, rz*offR);
                sendBlackflameSprayFX(net, p, start, end, -rx*offR, -ry*offR, -rz*offR);
                sendBlackflameSprayFX(net, p, start, end, vx*offU, vy*offU, vz*offU);
                sendBlackflameSprayFX(net, p, start, end, -vx*offU, -vy*offU, -vz*offU);

                if (hitVec != null) {
                    net.sendToAllAround(
                            new PacketBlackflameSprayFX(
                                    (float) hitVec.xCoord, (float) hitVec.yCoord, (float) hitVec.zCoord,
                                    (float) hitVec.xCoord, (float) hitVec.yCoord, (float) hitVec.zCoord
                            ),
                            new NetworkRegistry.TargetPoint(p.dimension, p.posX, p.posY, p.posZ, 64.0D)
                    );
                }
            }
        } catch (Throwable ignored) {}

        if (hitEnt != null) {
            try { HexOrbEffectsController.applyDarkfireBlackfireBurnHitBig(p, hitEnt); } catch (Throwable ignored) {}

            if (hitVec != null) {
                try {
                    final double r = 4.25D;
                    AxisAlignedBB aoe = AxisAlignedBB.getBoundingBox(
                            hitVec.xCoord - r, hitVec.yCoord - r, hitVec.zCoord - r,
                            hitVec.xCoord + r, hitVec.yCoord + r, hitVec.zCoord + r
                    );
                    List near = p.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, aoe);
                    if (near != null && !near.isEmpty()) {
                        for (int i = 0; i < near.size(); i++) {
                            Object o = near.get(i);
                            if (!(o instanceof EntityLivingBase)) continue;
                            EntityLivingBase e = (EntityLivingBase) o;
                            if (e == p || e == hitEnt || e.isDead || e.getHealth() <= 0.0F) continue;
                            try { HexOrbEffectsController.applyDarkfireBlackfireBurnHitBig(p, e); } catch (Throwable ignored2) {}
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } else {
            if (mopBlock != null) {
                try { tryPlaceShadowFireAtImpact(p, mopBlock); } catch (Throwable ignored) {}
            }
        }
    }
    // -------------------------------------------------------------------------
    // Ember Detonation: 10% mark shot (single / multi)
    // - Raycasts to the first living entity along the player's look vector
    // - Always spawns a visible shot trail
    // - On hit, applies "Ember Mark" to the impact target (+ nearby targets if multi)
    //   Mark ticking / persistent VFX is handled in HexOrbEffectsController
    // -------------------------------------------------------------------------
    private static void fireEmberMarkShot(EntityPlayerMP p, boolean multi) {
        if (p == null || p.worldObj == null) return;
        if (p.worldObj.isRemote) return;

        net.minecraft.world.World w = p.worldObj;
        if (!(w instanceof net.minecraft.world.WorldServer)) return;
        net.minecraft.world.WorldServer ws = (net.minecraft.world.WorldServer) w;

        // Prefer the controller constant if present.
        double range = 16.0D;
        try {
            java.lang.reflect.Field f = HexOrbEffectsController.class.getDeclaredField("DARKFIRE_EMBER_MARK_RANGE_BLOCKS");
            f.setAccessible(true);
            Object v = f.get(null);
            if (v instanceof Number) range = ((Number) v).doubleValue();
        } catch (Throwable ignored) {}

        Vec3 start = Vec3.createVectorHelper(p.posX, p.posY + (double) p.getEyeHeight(), p.posZ);

        Vec3 look = null;
        try { look = p.getLookVec(); } catch (Throwable ignored) {}
        if (look == null) {
            try { look = p.getLook(1.0F); } catch (Throwable ignored) {}
        }
        if (look == null) look = Vec3.createVectorHelper(0, 0, 1);

        Vec3 end = start.addVector(look.xCoord * range, look.yCoord * range, look.zCoord * range);

        // Broad-phase query along the look ray
        AxisAlignedBB scan = p.boundingBox
                .addCoord(look.xCoord * range, look.yCoord * range, look.zCoord * range)
                .expand(1.5D, 1.5D, 1.5D);

        List ents = null;
        try { ents = w.getEntitiesWithinAABBExcludingEntity(p, scan); } catch (Throwable ignored) {}
        if (ents == null) ents = java.util.Collections.emptyList();

        EntityLivingBase best = null;
        Vec3 bestHit = null;
        double bestDist = range * range;

        for (int i = 0; i < ents.size(); i++) {
            Object o = ents.get(i);
            if (!(o instanceof EntityLivingBase)) continue;

            EntityLivingBase e = (EntityLivingBase) o;
            if (e == p) continue;
            if (e.isDead) continue;
            try { if (e.getHealth() <= 0.0F) continue; } catch (Throwable ignored) {}

            AxisAlignedBB bb = e.boundingBox.expand(0.30D, 0.30D, 0.30D);
            net.minecraft.util.MovingObjectPosition mop = null;
            try { mop = bb.calculateIntercept(start, end); } catch (Throwable ignored) {}
            if (mop == null || mop.hitVec == null) continue;

            double d = start.squareDistanceTo(mop.hitVec);
            if (d < bestDist) {
                bestDist = d;
                best = e;
                bestHit = mop.hitVec;
            }
        }

        // Visual shot trail (always show; feels like "launching" a particle)
        Vec3 trailEnd = (bestHit != null ? bestHit : end);
        spawnEmberShotTrail(ws, start, trailEnd, multi);

        if (best == null) return;

        // Apply marks (AoE "grab") on impact
        try { HexOrbEffectsController.applyDarkfireEmberMarkImpact(p, best, multi); } catch (Throwable ignored) {}

        // Extra impact burst at the actual hit vec (if we have it)
        if (bestHit != null) {
            try {
                ws.func_147487_a("flame", bestHit.xCoord, bestHit.yCoord, bestHit.zCoord, multi ? 10 : 6, 0.25, 0.20, 0.25, 0.01);
                ws.func_147487_a("smoke", bestHit.xCoord, bestHit.yCoord, bestHit.zCoord, multi ? 8 : 5, 0.25, 0.20, 0.25, 0.01);
                ws.func_147487_a("mobSpell", bestHit.xCoord, bestHit.yCoord, bestHit.zCoord, multi ? 8 : 5, 1.0, 0.30, 0.05, 0.75);
            } catch (Throwable ignored) {}
        }
    }

    private static void spawnEmberShotTrail(net.minecraft.world.WorldServer ws, Vec3 start, Vec3 end, boolean multi) {
        if (ws == null || start == null || end == null) return;

        int steps = multi ? 16 : 12;

        double dx = (end.xCoord - start.xCoord) / (double) steps;
        double dy = (end.yCoord - start.yCoord) / (double) steps;
        double dz = (end.zCoord - start.zCoord) / (double) steps;

        for (int i = 0; i <= steps; i++) {
            double px = start.xCoord + dx * (double) i;
            double py = start.yCoord + dy * (double) i;
            double pz = start.zCoord + dz * (double) i;

            try {
                ws.func_147487_a("mobSpell", px, py, pz, 1, 1.0, 0.28, 0.05, 0.45);
                if ((i & 1) == 0) ws.func_147487_a("flame", px, py, pz, 1, 0.02, 0.02, 0.02, 0.0);
            } catch (Throwable ignored) {}
        }
    }


    /**
     * Darkfire 100%: Rapid-fire shot. Chooses small/medium/big flame sizes and applies stronger
     * impact damage + a moderate DoT. Overcharge (up to 2x) is handled server-side.
     */
    static void fireDarkfireRapidFireShot(EntityPlayerMP p) {
        if (p == null || p.worldObj == null) return;
        if (p.worldObj.isRemote) return;

        // Vary size: mostly small/medium, occasional big shots.
        // 0 = small, 1 = medium, 2 = big
        int roll = p.worldObj.rand.nextInt(100);
        int size;
        if (roll < 45) size = 0;
        else if (roll < 85) size = 1;
        else size = 2;

        double range = (size == 2 ? 18.0D : (size == 1 ? 16.0D : 14.0D));
        double hitRadius = (size == 2 ? 1.45D : (size == 1 ? 1.05D : 0.75D));
        double step = 0.65D;

        Vec3 look = p.getLookVec();
        if (look == null) return;

        Vec3 start = Vec3.createVectorHelper(p.posX, p.posY + 1.52D, p.posZ);
        Vec3 end = start.addVector(look.xCoord * range, look.yCoord * range, look.zCoord * range);

        // Find nearest hit along the ray (same approach as the 25%/50% sprays)
        EntityLivingBase hitEnt = null;
        Vec3 hitVec = null;

        int steps = (int) Math.ceil(range / step);
        double bestDistSq = Double.MAX_VALUE;

        for (int i = 1; i <= steps; i++) {
            double t = (i * step);
            Vec3 pt = start.addVector(look.xCoord * t, look.yCoord * t, look.zCoord * t);
            AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(
                    pt.xCoord - hitRadius, pt.yCoord - hitRadius, pt.zCoord - hitRadius,
                    pt.xCoord + hitRadius, pt.yCoord + hitRadius, pt.zCoord + hitRadius
            );

            List list = p.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, bb);
            if (list == null || list.isEmpty()) continue;

            for (Object o : list) {
                if (!(o instanceof EntityLivingBase)) continue;
                EntityLivingBase e = (EntityLivingBase) o;
                if (e == p) continue;
                if (e.isDead) continue;

                // Quick LoS-ish: prioritize nearest
                double dx = e.posX - p.posX;
                double dy = (e.posY + e.getEyeHeight()) - (p.posY + 1.52D);
                double dz = e.posZ - p.posZ;
                double dsq = dx*dx + dy*dy + dz*dz;
                if (dsq < bestDistSq) {
                    bestDistSq = dsq;
                    hitEnt = e;
                    hitVec = pt;
                }
            }

            if (hitEnt != null) break;
        }

        if (hitEnt != null) {
            HexOrbEffectsController.applyDarkfireRapidFireHit(p, hitEnt, size);
        }

        // Small AoE splash to let it feel like a "blast" instead of a single poke
        if (hitVec != null) {
            double aoe = (size == 2 ? 2.6D : (size == 1 ? 2.1D : 1.6D));
            List around = p.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, AxisAlignedBB.getBoundingBox(
                    hitVec.xCoord - aoe, hitVec.yCoord - aoe, hitVec.zCoord - aoe,
                    hitVec.xCoord + aoe, hitVec.yCoord + aoe, hitVec.zCoord + aoe
            ));
            if (around != null && !around.isEmpty()) {
                for (Object o : around) {
                    if (!(o instanceof EntityLivingBase)) continue;
                    EntityLivingBase e = (EntityLivingBase) o;
                    if (e == p) continue;
                    if (e.isDead) continue;
                    if (hitEnt != null && e == hitEnt) continue; // already applied

                    // Don't double-apply to a huge crowd: cap soft
                    HexOrbEffectsController.applyDarkfireRapidFireHit(p, e, Math.min(1, size)); // AoE never uses "big"
                }
            }
        }
    }



    private static void sendBlackflameSprayFX(SimpleNetworkWrapper net, EntityPlayerMP p, Vec3 start, Vec3 end, double ox, double oy, double oz) {
        if (net == null || p == null || start == null || end == null) return;
        net.sendToAllAround(
                new PacketBlackflameSprayFX(
                        (float) (start.xCoord + ox), (float) (start.yCoord + oy), (float) (start.zCoord + oz),
                        (float) (end.xCoord + ox),   (float) (end.yCoord + oy),   (float) (end.zCoord + oz)
                ),
                new NetworkRegistry.TargetPoint(p.dimension, p.posX, p.posY, p.posZ, 64.0D)
        );
    }




    // Damage scaling for Fractured flying blast.
    // Uses effective WillPower (includes form multipliers when available).
    private static float computeFrbDamageFromWill(EntityPlayerMP p, boolean big){
        double wil = 0D;
        try { wil = HexDBCProcDamageProvider.getWillPowerEffective(p); } catch (Throwable ignored) {}
        if (wil <= 0D) wil = 0D;

        // Tuned to hit ~240k at ~7.3k WIL, and scale into millions for high form-multiplied WIL.
        double base = 35000D + (wil * 28.0D);

        // Big blast is a heavier hit (consumes all shards).
        if (big) base *= 2.60D;

        // Sanity caps (prevents absurd values if stats get extreme)
        if (base < 15000D) base = 15000D;
        if (big) {
            if (base > 20000000D) base = 20000000D;
        } else {
            if (base > 8000000D) base = 8000000D;
        }
        return (float) base;
    }

    private static int computeWillSnapshot(EntityPlayerMP p){
        double wil = 0D;
        try { wil = HexDBCProcDamageProvider.getWillPowerEffective(p); } catch (Throwable ignored) {}
        if (wil <= 0D) wil = 0D;
        if (wil > 2000000000D) wil = 2000000000D;
        return (int) Math.round(wil);
    }


    /**
     * Server-side AOE damage + knockback for Fractured blast explosion.
     * Call this at impact OR when the blast reaches end-of-path without hitting anything.
     */
    public static void doFrbAoeExplosion(EntityPlayerMP shooter, double x, double y, double z,
                                         double aoeRadius, float aoeDamage, double kb) {
        if (shooter == null || shooter.worldObj == null) return;
        if (aoeRadius <= 0D || aoeDamage <= 0f) return;

        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(
                x - aoeRadius, y - aoeRadius, z - aoeRadius,
                x + aoeRadius, y + aoeRadius, z + aoeRadius
        );

        @SuppressWarnings("unchecked")
        List<Entity> list = shooter.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, box);
        if (list == null) return;

        for (int i = 0; i < list.size(); i++) {
            Entity e = list.get(i);
            if (!(e instanceof EntityLivingBase)) continue;
            EntityLivingBase elb = (EntityLivingBase) e;
            if (elb == shooter) continue;
            if (!elb.isEntityAlive()) continue;

            double dx = elb.posX - x;
            double dy = (elb.posY + elb.height * 0.5D) - y;
            double dz = elb.posZ - z;
            double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (dist > aoeRadius || dist < 0.001D) continue;

            float falloff = (float) Math.max(0.15D, 1.0D - (dist / aoeRadius));
            float dmg = aoeDamage * falloff;

            applyDbcBodyDamageOrFallback(shooter, elb, dmg);

            double kbo = kb * falloff;
            elb.motionX += (dx / dist) * kbo;
            elb.motionY += (dy / dist) * (kbo * 0.20D);
            elb.motionZ += (dz / dist) * kbo;
            elb.velocityChanged = true;
        }
    }

    private static void startSnap(NBTTagCompound tag, int ticks) {
        if (tag == null) return;
        tag.setInteger(TAG_FR_SNAP, ticks);
        tag.setInteger(TAG_FR_SNAP_MAX, ticks);
    }

    /**
     * Writes a "virtual projectile" into the player's entity NBT.
     * HexOrbEffectsController.onLivingUpdate advances it each tick.
     *
     * @param rangeBlocks how far it can travel before dissipating
     * @param stepBlocks  how far it moves per tick
     * @param dmg         damage to apply on hit
     * @param kb          knockback strength (scaled by look vec)
     * @param rad         hit radius around the projectile segment
     */
    private static void startFlyingBlast(EntityPlayerMP p, double rangeBlocks, double stepBlocks, float dmg, double kb, double rad, double aoeRad, float aoeDmg) {
        if (p == null) return;

        // Eye position and look dir
        double x = p.posX;
        double y = p.posY + p.getEyeHeight() - 0.10D;
        double z = p.posZ;

        // Start slightly in front of face to avoid self-collide
        double dx = p.getLookVec().xCoord;
        double dy = p.getLookVec().yCoord;
        double dz = p.getLookVec().zCoord;

        double norm = Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (norm < 1.0E-4D) return;
        dx /= norm; dy /= norm; dz /= norm;

        x += dx * 0.55D;
        y += dy * 0.55D;
        z += dz * 0.55D;

        int ticks = (int) Math.ceil(rangeBlocks / Math.max(0.05D, stepBlocks));

        NBTTagCompound data = p.getEntityData();
        data.setBoolean(FRB_KEY_ACTIVE, true);
        data.setDouble(FRB_KEY_X, x);
        data.setDouble(FRB_KEY_Y, y);
        data.setDouble(FRB_KEY_Z, z);
        data.setDouble(FRB_KEY_DX, dx);
        data.setDouble(FRB_KEY_DY, dy);
        data.setDouble(FRB_KEY_DZ, dz);
        data.setDouble(FRB_KEY_STEP, stepBlocks);
        data.setInteger(FRB_KEY_TICKS, ticks);
        data.setFloat(FRB_KEY_DMG, dmg);
        data.setInteger(FRB_KEY_WIL_SNAP, computeWillSnapshot(p));
        data.setDouble(FRB_KEY_KB, kb);
        data.setDouble(FRB_KEY_RAD, rad);
        // Optional AOE (used for triple-tap / max-shard blast)
        if (aoeRad > 0D && aoeDmg > 0f) {
            data.setDouble(FRB_KEY_AOE_RAD, aoeRad);
            data.setFloat(FRB_KEY_AOE_DMG, aoeDmg);
            data.setBoolean(FRB_KEY_END_BOOM, true);
        } else {
            data.removeTag(FRB_KEY_AOE_RAD);
            data.removeTag(FRB_KEY_AOE_DMG);
            data.setBoolean(FRB_KEY_END_BOOM, false);
        }
    }

    private static ItemStack findFirstEquippedFractured(EntityPlayerMP p) {
        if (p == null) return null;

        // Held
        ItemStack held = p.getCurrentEquippedItem();
        if (isFractured(held)) return held;

        // Socketed on held host
        try {
            int filled = HexSocketAPI.getSocketsFilled(held);
            for (int i = 0; i < filled; i++) {
                ItemStack gem = HexSocketAPI.getGemAt(held, i);
                if (isFractured(gem)) return gem;
            }
        } catch (Throwable ignored) {}

        // Armor (+ sockets)
        if (p.inventory != null && p.inventory.armorInventory != null) {
            for (int i = 0; i < p.inventory.armorInventory.length; i++) {
                ItemStack s = p.inventory.armorInventory[i];
                if (isFractured(s)) return s;

                try {
                    int filled = HexSocketAPI.getSocketsFilled(s);
                    for (int j = 0; j < filled; j++) {
                        ItemStack gem = HexSocketAPI.getGemAt(s, j);
                        if (isFractured(gem)) return gem;
                    }
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private static boolean isFractured(ItemStack s) {
        if (s == null) return false;
        NBTTagCompound tag = s.getTagCompound();
        if (tag == null) return false;

        // Normal identity path
        String prof = tag.getString(TAG_PROFILE);
        if (prof != null && prof.startsWith(FRACTURED_PREFIX)) return true;

        // Socketed/stripped fallback: fractured marker tags survive socketing
        if (tag.hasKey(TAG_FR_SHARDS) || tag.hasKey(TAG_FR_SNAP) || tag.hasKey(TAG_FR_SNAP_MAX)) return true;

        return false;
    }

    /**
     * Socket stations sometimes strip TAG_ROLLED / TAG_PROFILE while leaving Light payload keys.
     * We restamp a minimal valid identity so:
     *  - server validators keep working
     *  - client HUD / key handler can keep finding the orb
     *
     * Only applies to Solar Light orbs.
     */
    private static boolean ensureSolarTags(ItemStack stack) {
        if (stack == null) return false;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return false; // don't create tags for random stacks

        if (!tag.hasKey(TAG_LIGHT_TYPE)) return false;
        String type = tag.getString(TAG_LIGHT_TYPE);
        if (type == null || !"Solar".equalsIgnoreCase(type.trim())) return false;

        boolean changed = false;

        if (!tag.getBoolean(TAG_ROLLED)) {
            tag.setBoolean(TAG_ROLLED, true);
            changed = true;
        }

        String prof = tag.getString(TAG_PROFILE);
        if (prof == null || prof.length() == 0 || !prof.startsWith("LIGHT_")) {
            tag.setString(TAG_PROFILE, "LIGHT_SOCKETED");
            changed = true;
        }

        if (!tag.hasKey(TAG_LIGHT_RAD)) {
            tag.setInteger(TAG_LIGHT_RAD, 0);
            changed = true;
        }

        if (changed) stack.setTagCompound(tag);
        return changed;
    }

    private static boolean ensureFracturedTags(ItemStack stack) {
        if (stack == null) return false;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return false; // do NOT create tags for non-fractured stacks

        // Only fix up if it already looks fractured
        boolean looksFr = false;
        String prof = tag.getString(TAG_PROFILE);
        if (prof != null && prof.startsWith(FRACTURED_PREFIX)) looksFr = true;
        if (tag.hasKey(TAG_FR_SHARDS) || tag.hasKey(TAG_FR_SNAP) || tag.hasKey(TAG_FR_SNAP_MAX)) looksFr = true;
        if (!looksFr) return false;

        boolean changed = false;

        if (!tag.getBoolean(TAG_ROLLED)) {
            tag.setBoolean(TAG_ROLLED, true);
            changed = true;
        }

        if (prof == null || prof.length() == 0 || !prof.startsWith(FRACTURED_PREFIX)) {
            tag.setString(TAG_PROFILE, "FRACTURED_SOCKETED");
            changed = true;
        }

        if (!tag.hasKey(TAG_FR_SHARDS)) {
            tag.setInteger(TAG_FR_SHARDS, 0);
            changed = true;
        }
        if (!tag.hasKey(TAG_FR_SNAP)) {
            tag.setInteger(TAG_FR_SNAP, 0);
            changed = true;
        }
        if (!tag.hasKey(TAG_FR_SNAP_MAX)) {
            tag.setInteger(TAG_FR_SNAP_MAX, 0);
            changed = true;
        }

        if (changed) stack.setTagCompound(tag);
        return changed;
    }


    // ---------------------------------------------------------------------
    // Beam FX (visible to everyone nearby via vanilla particle packets)
    // ---------------------------------------------------------------------

// ---------------------------------------------------------------------
// Fractured Blast FX (yellow/golden particles)
// ---------------------------------------------------------------------

    /**
     * Trail particles for the Fractured flying blast. Call this from the blast tick (server) each step.
     */
    public static void spawnFrbTrailFx(EntityPlayerMP shooter, double x, double y, double z, boolean big) {
        if (shooter == null) return;

        // Warm yellow dust + a tiny sparkle occasionally.
        final float r = 1.00F;
        final float g = big ? 0.98F : 0.92F;
        final float b = big ? 0.22F : 0.12F;

        // Slightly larger radius so nearby players see it.
        final float radius = big ? 64.0F : 48.0F;

        // A couple of dust puffs per tick
        sendParticleToNearby(shooter, "reddust", (float) x, (float) y, (float) z, r, g, b, 0.0F, 0, radius);
        sendParticleToNearby(shooter, "reddust", (float) x, (float) y, (float) z, r, g, b, 0.0F, 0, radius);

        // Occasional sparkle
        if ((shooter.ticksExisted % (big ? 2 : 3)) == 0) {
            sendParticleToNearby(shooter, "fireworksSpark", (float) x, (float) y, (float) z,
                    0.05F, 0.05F, 0.05F,
                    0.03F, 1, radius);
        }
    }

    /**
     * Explosion particles for the Fractured blast (impact OR end-of-path miss).
     */
    public static void spawnFrbExplosionFx(EntityPlayerMP shooter, double x, double y, double z, boolean big) {
        if (shooter == null) return;

        final float radius = big ? 96.0F : 72.0F;

        // Big golden burst
        final int dust = big ? 22 : 14;
        final float r = 1.00F;
        final float g = big ? 0.98F : 0.90F;
        final float b = big ? 0.20F : 0.10F;

        for (int i = 0; i < dust; i++) {
            sendParticleToNearby(shooter, "reddust", (float) x, (float) y, (float) z,
                    r, g, b,
                    0.0F, 0, radius);
        }

        // Spark ring-ish feel
        final int sparks = big ? 16 : 10;
        for (int i = 0; i < sparks; i++) {
            sendParticleToNearby(shooter, "fireworksSpark", (float) x, (float) y, (float) z,
                    0.25F, 0.25F, 0.25F,
                    0.10F, 2, radius);
        }

        // A little flame pop for "explosion" readability
        final int flame = big ? 10 : 6;
        for (int i = 0; i < flame; i++) {
            sendParticleToNearby(shooter, "flame", (float) x, (float) y, (float) z,
                    0.25F, 0.20F, 0.25F,
                    0.02F, 3, radius);
        }
    }


    // ---------------------------------------------------------------------
    // Network wrapper resolution (avoids hard dependency on a specific field name like HexFracNet.NET)
    // ---------------------------------------------------------------------

    private static volatile SimpleNetworkWrapper CACHED_NET;

    /**
     * Exposes the internal net-wrapper resolver for other client->server packets.
     * (Used by PacketOrbSelect / other small sync packets.)
     */
    public static SimpleNetworkWrapper resolveNetWrapperForExternal() {
        return resolveNetWrapper();
    }

    private static SimpleNetworkWrapper resolveNetWrapper() {
        SimpleNetworkWrapper cached = CACHED_NET;
        if (cached != null) return cached;

        try {
            // Use reflection so your HexFracNet can name its wrapper however it wants (NET/CHANNEL/WRAPPER/etc).
            Class<?> netCls = Class.forName("com.example.examplemod.network.HexFracNet");

            // 1) Known static field names
            final String[] fieldNames = new String[] {
                    "NET", "CHANNEL", "WRAPPER", "NETWORK", "NETWORK_WRAPPER", "INSTANCE"
            };

            for (int i = 0; i < fieldNames.length; i++) {
                String fn = fieldNames[i];
                try {
                    Field f = null;
                    try { f = netCls.getField(fn); } catch (Throwable ignored) {}
                    if (f == null) {
                        try { f = netCls.getDeclaredField(fn); } catch (Throwable ignored) {}
                    }
                    if (f != null) {
                        f.setAccessible(true);
                        Object v = Modifier.isStatic(f.getModifiers()) ? f.get(null) : null;
                        if (v instanceof SimpleNetworkWrapper) {
                            CACHED_NET = (SimpleNetworkWrapper) v;
                            return CACHED_NET;
                        }
                        // Some codebases keep an INSTANCE singleton; if so, search inside it.
                        if (v != null) {
                            SimpleNetworkWrapper inner = findWrapperInInstance(v);
                            if (inner != null) {
                                CACHED_NET = inner;
                                return CACHED_NET;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // 2) Any static field of type SimpleNetworkWrapper
            try {
                Field[] fs = netCls.getDeclaredFields();
                for (int i = 0; i < fs.length; i++) {
                    Field f = fs[i];
                    if (!Modifier.isStatic(f.getModifiers())) continue;
                    if (!SimpleNetworkWrapper.class.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    Object v = f.get(null);
                    if (v instanceof SimpleNetworkWrapper) {
                        CACHED_NET = (SimpleNetworkWrapper) v;
                        return CACHED_NET;
                    }
                }
            } catch (Throwable ignored) {}

            // 3) Known static no-arg methods
            final String[] methodNames = new String[] {
                    "getNetwork", "getWrapper", "net", "wrapper", "instance", "getInstance", "get"
            };
            for (int i = 0; i < methodNames.length; i++) {
                try {
                    Method m = null;
                    try { m = netCls.getMethod(methodNames[i]); } catch (Throwable ignored) {}
                    if (m == null) {
                        try { m = netCls.getDeclaredMethod(methodNames[i]); } catch (Throwable ignored) {}
                    }
                    if (m == null) continue;
                    if (!Modifier.isStatic(m.getModifiers())) continue;
                    if (m.getParameterTypes() != null && m.getParameterTypes().length != 0) continue;

                    m.setAccessible(true);
                    Object v = m.invoke(null);
                    if (v instanceof SimpleNetworkWrapper) {
                        CACHED_NET = (SimpleNetworkWrapper) v;
                        return CACHED_NET;
                    }
                    if (v != null) {
                        SimpleNetworkWrapper inner = findWrapperInInstance(v);
                        if (inner != null) {
                            CACHED_NET = inner;
                            return CACHED_NET;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static SimpleNetworkWrapper findWrapperInInstance(Object instance) {
        if (instance == null) return null;
        try {
            if (instance instanceof SimpleNetworkWrapper) return (SimpleNetworkWrapper) instance;

            Field[] fs = instance.getClass().getDeclaredFields();
            for (int i = 0; i < fs.length; i++) {
                Field f = fs[i];
                if (SimpleNetworkWrapper.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object v = f.get(instance);
                    if (v instanceof SimpleNetworkWrapper) return (SimpleNetworkWrapper) v;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }


    // ---------------------------------------------------------------------
    // Darkfire helpers
    // ---------------------------------------------------------------------

    private static net.minecraft.block.Block resolveShadowFireBlock() {
        try {
            Object o = net.minecraft.block.Block.blockRegistry.getObject("hexcolorcodes:shadow_fire");
            if (o instanceof net.minecraft.block.Block) return (net.minecraft.block.Block) o;
        } catch (Throwable ignored) {}
        try {
            Object o = net.minecraft.block.Block.blockRegistry.getObject("shadow_fire");
            if (o instanceof net.minecraft.block.Block) return (net.minecraft.block.Block) o;
        } catch (Throwable ignored) {}
        try {
            Object o = net.minecraft.block.Block.blockRegistry.getObject("examplemod:shadow_fire");
            if (o instanceof net.minecraft.block.Block) return (net.minecraft.block.Block) o;
        } catch (Throwable ignored) {}
        return null;
    }

    private static void tryPlaceShadowFireAtImpact(EntityPlayerMP p, net.minecraft.util.MovingObjectPosition mopBlock) {
        if (p == null || mopBlock == null || p.worldObj == null) return;

        net.minecraft.world.World w = p.worldObj;
        if (w.isRemote) return;

        net.minecraft.block.Block shadowFire = resolveShadowFireBlock();
        if (shadowFire == null) return;

        int x = mopBlock.blockX;
        int y = mopBlock.blockY;
        int z = mopBlock.blockZ;

        // Place on the adjacent face we hit (like fire placement).
        int tx = x, ty = y, tz = z;
        switch (mopBlock.sideHit) {
            case 0: ty -= 1; break; // bottom
            case 1: ty += 1; break; // top
            case 2: tz -= 1; break; // north
            case 3: tz += 1; break; // south
            case 4: tx -= 1; break; // west
            case 5: tx += 1; break; // east
            default: break;
        }

        try {
            // Respect edit permissions
            if (!p.canPlayerEdit(tx, ty, tz, mopBlock.sideHit, p.getCurrentEquippedItem())) return;
        } catch (Throwable ignored) {}

        // Only place into air/replaceable
        try {
            if (!w.isAirBlock(tx, ty, tz)) return;
        } catch (Throwable ignored) {
            return;
        }

        try {
            if (!shadowFire.canPlaceBlockAt(w, tx, ty, tz)) return;
        } catch (Throwable ignored) {}

        try {
            w.setBlock(tx, ty, tz, shadowFire, 0, 3);
        } catch (Throwable ignored) {}
    }
    private static void sendToAllAroundSafe(IMessage msg, TargetPoint pt) {
        if (msg == null || pt == null) return;
        try {
            SimpleNetworkWrapper net = resolveNetWrapper();
            if (net != null) {
                net.sendToAllAround(msg, pt);
            }
        } catch (Throwable ignored) {}
    }


    private static void broadcastSolarBeamRarityBeam(EntityPlayerMP p,
                                                     double sx, double sy, double sz,
                                                     double ex, double ey, double ez,
                                                     boolean superBeam, ItemStack solar) {
        try {
            // Colors are intentionally "solar gold" and reuse the same beam texture pipeline.
            // (We avoid depending on rarity NBT keys here so it always works for Solar Light.)
            final int botRGB = superBeam ? 0xFFD48A : 0xFFB84A;
            final int topRGB = superBeam ? 0xFFFFF2 : 0xFFF6C8;

            // If you later want chaos/evolved parity with item beams, populate these from the orb's lore/NBT.
            final byte chaosOrdinal = 0; // ItemBeamRenderer.Chaos.NONE
            final boolean evolved = (solar != null) && hasEvolvedLore(solar);

            final float radiusScale = superBeam ? 2.15F : 1.15F;
            final int lifeTicks = superBeam ? 14 : 10;

            sendToAllAroundSafe(
                    new PacketSolarBeamFX(
                            sx, sy, sz,
                            ex, ey, ez,
                            botRGB, topRGB,
                            chaosOrdinal, evolved,
                            radiusScale, lifeTicks
                    ),
                    new TargetPoint(p.dimension, sx, sy, sz, 64.0D)
            );
        } catch (Throwable ignored) {}
    }

    private static void broadcastSolarBeamFx(EntityPlayerMP shooter,
                                             double sx, double sy, double sz,
                                             double ex, double ey, double ez,
                                             boolean superBeam) {
        if (shooter == null || shooter.worldObj == null) return;

        // Who sees it (radius around shooter)
        final double radius = 64.0D;

        // Beam density
        final double distX = (ex - sx);
        final double distY = (ey - sy);
        final double distZ = (ez - sz);
        final double dist = Math.sqrt(distX*distX + distY*distY + distZ*distZ);
        if (dist < 0.01D) return;

        final double step = superBeam ? 0.45D : 0.65D;
        final int points = Math.max(6, (int) Math.ceil(dist / step));

        // Golden-ish redstone dust. In 1.7.10, reddust uses the "offset" fields as color.
        final float r = 1.00F;
        final float g = superBeam ? 0.92F : 0.88F;
        final float b = superBeam ? 0.18F : 0.10F;

        for (int i = 0; i <= points; i++) {
            double t = (double) i / (double) points;
            double px = sx + distX * t;
            double py = sy + distY * t;
            double pz = sz + distZ * t;

            // Core beam
            sendParticleToNearby(shooter, "reddust", (float) px, (float) py, (float) pz,
                    r, g, b,
                    0.0F, 0, radius);

            // Occasional sparkle for texture
            if ((i % (superBeam ? 3 : 4)) == 0) {
                sendParticleToNearby(shooter, "fireworksSpark", (float) px, (float) py, (float) pz,
                        0.0F, 0.0F, 0.0F,
                        0.02F, 1, radius);
            }
        }

        // Impact burst
        int burst = superBeam ? 10 : 6;
        for (int i = 0; i < burst; i++) {
            sendParticleToNearby(shooter, "fireworksSpark", (float) ex, (float) ey, (float) ez,
                    0.20F, 0.20F, 0.20F,
                    0.08F, 2, radius);
        }
    }

    @SuppressWarnings("unchecked")
    private static void sendParticleToNearby(EntityPlayerMP shooter,
                                             String particle,
                                             float x, float y, float z,
                                             float ox, float oy, float oz,
                                             float speed,
                                             int count,
                                             double radius) {
        if (shooter == null || shooter.worldObj == null || particle == null) return;
        // Try server-side particle broadcast first (works even if Packet63WorldParticles constructor differs on forks).
        if (tryWorldServerSpawnParticle(shooter, particle, x, y, z, ox, oy, oz, speed, count)) {
            return;
        }

        Object pkt = createParticlePacket(particle, x, y, z, ox, oy, oz, speed, count);
        if (!(pkt instanceof Packet)) return;

        List<?> players = shooter.worldObj.playerEntities;
        if (players == null) return;

        double r2 = radius * radius;
        for (int i = 0; i < players.size(); i++) {
            Object o = players.get(i);
            if (!(o instanceof EntityPlayerMP)) continue;
            EntityPlayerMP pl = (EntityPlayerMP) o;

            double dx = pl.posX - shooter.posX;
            double dy = (pl.posY + pl.getEyeHeight()) - (shooter.posY + shooter.getEyeHeight());
            double dz = pl.posZ - shooter.posZ;
            double d2 = dx*dx + dy*dy + dz*dz;
            if (d2 > r2) continue;

            try {
                pl.playerNetServerHandler.sendPacket((Packet) pkt);
            } catch (Throwable ignored) {
            }
        }
    }



    /**
     * Attempts to broadcast particles using the server world's particle method.
     * Supports both deobf name (spawnParticle) and SRG/obf name (func_147487_a) in 1.7.10 forks.
     */
    private static boolean tryWorldServerSpawnParticle(EntityPlayerMP shooter,
                                                       String particle,
                                                       float x, float y, float z,
                                                       float ox, float oy, float oz,
                                                       float speed,
                                                       int count) {
        if (shooter == null || shooter.worldObj == null || particle == null) return false;

        try {
            Object ws = shooter.worldObj;

            // Only WorldServer can broadcast to tracking clients
            if (!(ws instanceof net.minecraft.world.WorldServer)) return false;

            Method m = null;

            // Deobf / dev-mapped name
            try {
                m = ws.getClass().getMethod("spawnParticle",
                        String.class,
                        double.class, double.class, double.class,
                        int.class,
                        double.class, double.class, double.class,
                        double.class);
            } catch (Throwable ignored) {}

            // SRG / obf name used by vanilla 1.7.10
            if (m == null) {
                try {
                    m = ws.getClass().getMethod("func_147487_a",
                            String.class,
                            double.class, double.class, double.class,
                            int.class,
                            double.class, double.class, double.class,
                            double.class);
                } catch (Throwable ignored) {}
            }

            if (m == null) return false;

            m.setAccessible(true);
            m.invoke(ws,
                    particle,
                    (double) x, (double) y, (double) z,
                    count,
                    (double) ox, (double) oy, (double) oz,
                    (double) speed);
            return true;
        } catch (Throwable ignored) {}

        return false;
    }

    private static Object createParticlePacket(String particle,
                                               float x, float y, float z,
                                               float ox, float oy, float oz,
                                               float speed,
                                               int count) {
        // 1.7.10: Packet63WorldParticles
        try {
            Class<?> c = Class.forName("net.minecraft.network.play.server.Packet63WorldParticles");
            java.lang.reflect.Constructor<?>[] ctors = c.getConstructors();
            for (int i = 0; i < ctors.length; i++) {
                java.lang.reflect.Constructor<?> k = ctors[i];
                Class<?>[] p = k.getParameterTypes();
                if (p == null) continue;

                // (String, float,float,float, float,float,float, float, int)
                if (p.length == 9 && p[0] == String.class) {
                    return k.newInstance(particle, x, y, z, ox, oy, oz, speed, count);
                }

                // Some forks include boolean longDistance as 2nd param
                if (p.length == 10 && p[0] == String.class && p[1] == boolean.class) {
                    return k.newInstance(particle, false, x, y, z, ox, oy, oz, speed, count);
                }
            }
        } catch (Throwable ignored) {
        }

        // 1.8+ fallback (dev env sometimes mixes mappings): S2APacketParticles
        try {
            Class<?> c = Class.forName("net.minecraft.network.play.server.S2APacketParticles");
            java.lang.reflect.Constructor<?>[] ctors = c.getConstructors();
            for (int i = 0; i < ctors.length; i++) {
                java.lang.reflect.Constructor<?> k = ctors[i];
                Class<?>[] p = k.getParameterTypes();
                if (p == null) continue;

                // (String, boolean, float,float,float, float,float,float, float, int)
                if (p.length == 10 && p[0] == String.class && p[1] == boolean.class) {
                    return k.newInstance(particle, false, x, y, z, ox, oy, oz, speed, count);
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

}