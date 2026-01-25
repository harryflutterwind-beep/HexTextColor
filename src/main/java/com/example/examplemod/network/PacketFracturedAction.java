package com.example.examplemod.network;

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
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraft.nbt.NBTTagList;

/**
 * Server-bound action packet for orb actions triggered by LCTRL double/triple taps. (MC/Forge 1.7.10)
 *
 * action:
 *  1 = FRACTURED: spend 1 shard (small flying blast)
 *  2 = FRACTURED: spend all shards (big flying blast; requires max shards)
 *  3 = SOLAR: fire Solar Beam (costs 10% radiance; uses beam cooldown bar)
 *  4 = SOLAR: fire Super Solar Beam (costs 80% radiance; uses beam cooldown bar)
 */
public class PacketFracturedAction implements IMessage {

    public int action;

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

    private static final String TAG_FR_SHARDS   = "HexFracShards";      // int 0..5
    private static final String TAG_FR_SNAP     = "HexFracSnapTicks";   // int
    private static final String TAG_FR_SNAP_MAX = "HexFracSnapMax";     // int


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

        // Prefer FRACTURED if equipped; otherwise allow SOLAR (Light type) beams.
        ItemStack stack = findFirstEquippedFractured(p);
        if (stack != null) {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null || !tag.getBoolean(TAG_ROLLED)) return;

            String prof = tag.getString(TAG_PROFILE);
            if (prof == null || !prof.startsWith("FRACTURED_")) return;

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

            p.inventory.markDirty();
            p.inventoryContainer.detectAndSendChanges();
            return;
        }

// Not fractured -> try SOLAR Light orb
        if (action != 3 && action != 4) return;

        ItemStack solar = findFirstEquippedSolarLight(p);
        if (solar == null) return;

        NBTTagCompound tag = solar.getTagCompound();
        if (tag == null || !tag.getBoolean(TAG_ROLLED)) return;

        String prof = tag.getString(TAG_PROFILE);
        if (prof == null || !prof.startsWith("LIGHT_")) return;

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
        p.inventory.markDirty();
        p.inventoryContainer.detectAndSendChanges();

    }



// ---------------------------------------------------------------------
// SOLAR beam (instant ray; DBC-first damage)
// ---------------------------------------------------------------------

    private static ItemStack findFirstEquippedSolarLight(EntityPlayerMP p) {
        if (p == null) return null;

        ItemStack held = p.getCurrentEquippedItem();
        if (isSolarLight(held)) return held;

        if (p.inventory != null && p.inventory.armorInventory != null) {
            for (int i = 0; i < p.inventory.armorInventory.length; i++) {
                ItemStack s = p.inventory.armorInventory[i];
                if (isSolarLight(s)) return s;
            }
        }
        return null;
    }

    private static boolean isSolarLight(ItemStack stack) {
        if (stack == null) return false;
        if (!stack.hasTagCompound()) return false;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return false;
        if (!tag.getBoolean(TAG_ROLLED)) return false;

        String prof = tag.getString(TAG_PROFILE);
        if (prof == null || !prof.startsWith("LIGHT_")) return false;

        String type = tag.getString(TAG_LIGHT_TYPE);
        return (type != null && "Solar".equalsIgnoreCase(type.trim()));
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

    private static float computeSolarBeamDamage(EntityPlayerMP p, boolean superBeam){
        double str = getStrengthEffective(p);
        double base = 32000D + (str * 24.0D);
        if (superBeam) base *= 3.35D;
        if (base < 18000D) base = 18000D;
        if (superBeam) {
            if (base > 26000000D) base = 26000000D;
        } else {
            if (base > 9000000D) base = 9000000D;
        }
        return (float) base;
    }

    private static final String TAG_PLAYER_PERSISTED = "PlayerPersisted";
    private static final String TAG_DBC_BODY = "jrmcBdy";

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
            applyDbcBodyDamageOrFallback(p, hit, dmg);

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
                        applyDbcBodyDamageOrFallback(p, elb, dmg * 0.70F * falloff);

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

        ItemStack cur = p.getCurrentEquippedItem();
        if (isFractured(cur)) return cur;

        for (int i = 0; i < p.inventory.armorInventory.length; i++) {
            ItemStack s = p.inventory.armorInventory[i];
            if (isFractured(s)) return s;
        }
        return null;
    }

    private static boolean isFractured(ItemStack s) {
        if (s == null) return false;
        NBTTagCompound tag = s.getTagCompound();
        if (tag == null) return false;
        if (!tag.getBoolean(TAG_ROLLED)) return false;
        String prof = tag.getString(TAG_PROFILE);
        return prof != null && prof.startsWith("FRACTURED_");
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