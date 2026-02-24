package com.example.examplemod.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CommandScanHealth extends CommandBase {

    @Override
    public String getCommandName() {
        return "scanhealth";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/scanhealth [radius] [limit]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    // Best-effort DBC/JRM keys (varies by pack/config; reflection covers most player cases)
    private static final String[] DBC_BODY_CUR_KEYS = {"jrmcBdy", "jrmcBdyCur", "jrmcBdyc"};
    private static final String[] DBC_BODY_MAX_KEYS = {"jrmcBdyF", "jrmcBdyMax", "jrmcBdyM"};

    private static boolean JRM_LOOKUP_DONE = false;
    private static Method JRM_GET_INT = null; // JinRyuu.JRMCore.JRMCoreH.getInt(...)

    private static void ensureJrmGetInt() {
        if (JRM_LOOKUP_DONE) return;
        JRM_LOOKUP_DONE = true;

        try {
            Class<?> c = Class.forName("JinRyuu.JRMCore.JRMCoreH");

            for (Method m : c.getMethods()) {
                if (!Modifier.isStatic(m.getModifiers())) continue;
                if (!"getInt".equals(m.getName())) continue;

                Class<?> rt = m.getReturnType();
                if (!(rt == int.class || rt == Integer.class)) continue;

                Class<?>[] pt = m.getParameterTypes();
                if (pt.length != 2) continue;

                // Either getInt(EntityPlayer, String) or getInt(String, EntityPlayer)
                boolean ep0 = EntityPlayer.class.isAssignableFrom(pt[0]) && pt[1] == String.class;
                boolean ep1 = pt[0] == String.class && EntityPlayer.class.isAssignableFrom(pt[1]);

                if (ep0 || ep1) {
                    JRM_GET_INT = m;
                    break;
                }
            }
        } catch (Throwable ignored) {
            // DBC/JRM not installed or class name changed
        }
    }

    private static int jrmGetIntSafe(EntityPlayer p, String key) {
        if (p == null || key == null) return Integer.MIN_VALUE;
        ensureJrmGetInt();
        if (JRM_GET_INT == null) return Integer.MIN_VALUE;

        try {
            Class<?>[] pt = JRM_GET_INT.getParameterTypes();
            Object r;
            if (EntityPlayer.class.isAssignableFrom(pt[0])) {
                r = JRM_GET_INT.invoke(null, p, key);
            } else {
                r = JRM_GET_INT.invoke(null, key, p);
            }
            if (r instanceof Integer) return (Integer) r;
        } catch (Throwable ignored) {}
        return Integer.MIN_VALUE;
    }

    private static float readNbtNumber(NBTTagCompound nbt, String key) {
        if (nbt == null || key == null) return Float.NaN;
        try {
            if (!nbt.hasKey(key)) return Float.NaN;

            NBTBase b = nbt.getTag(key);
            if (b instanceof NBTTagInt) return ((NBTTagInt) b).func_150287_d();
            if (b instanceof NBTTagFloat) return ((NBTTagFloat) b).func_150288_h();
            if (b instanceof NBTTagDouble) return (float) ((NBTTagDouble) b).func_150286_g();
            if (b instanceof NBTTagLong) return (float) ((NBTTagLong) b).func_150291_c();
            if (b instanceof NBTTagShort) return ((NBTTagShort) b).func_150289_e();
            if (b instanceof NBTTagByte) return ((NBTTagByte) b).func_150290_f();
        } catch (Throwable ignored) {}
        return Float.NaN;
    }

    private static float readFirstNumber(NBTTagCompound nbt, String[] keys) {
        if (nbt == null || keys == null) return Float.NaN;
        for (String k : keys) {
            float v = readNbtNumber(nbt, k);
            if (!Float.isNaN(v)) return v;
        }
        return Float.NaN;
    }

    private static float dbcBodyCur(Entity e) {
        if (e == null) return -1f;

        // Best: live JRM lookup for PLAYERS
        if (e instanceof EntityPlayer) {
            EntityPlayer p = (EntityPlayer) e;
            for (String k : DBC_BODY_CUR_KEYS) {
                int v = jrmGetIntSafe(p, k);
                if (v >= 0) return (float) v;
            }
        }

        // Fallback: entity NBT (works if those keys exist on the entity)
        try {
            NBTTagCompound ed = e.getEntityData();
            float cur = readFirstNumber(ed, DBC_BODY_CUR_KEYS);
            if (!Float.isNaN(cur) && cur >= 0f) return cur;

            if (ed != null && ed.hasKey("PlayerPersisted", 10)) {
                NBTTagCompound pp = ed.getCompoundTag("PlayerPersisted");
                cur = readFirstNumber(pp, DBC_BODY_CUR_KEYS);
                if (!Float.isNaN(cur) && cur >= 0f) return cur;
            }
        } catch (Throwable ignored) {}

        return -1f;
    }

    private static float dbcBodyMax(Entity e) {
        if (e == null) return -1f;

        if (e instanceof EntityPlayer) {
            EntityPlayer p = (EntityPlayer) e;
            for (String k : DBC_BODY_MAX_KEYS) {
                int v = jrmGetIntSafe(p, k);
                if (v > 0) return (float) v;
            }
        }

        try {
            NBTTagCompound ed = e.getEntityData();
            float max = readFirstNumber(ed, DBC_BODY_MAX_KEYS);
            if (!Float.isNaN(max) && max > 0f) return max;

            if (ed != null && ed.hasKey("PlayerPersisted", 10)) {
                NBTTagCompound pp = ed.getCompoundTag("PlayerPersisted");
                max = readFirstNumber(pp, DBC_BODY_MAX_KEYS);
                if (!Float.isNaN(max) && max > 0f) return max;
            }
        } catch (Throwable ignored) {}

        return -1f;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void processCommand(ICommandSender sender, String[] args) {
        EntityPlayerMP p = getCommandSenderAsPlayer(sender);
        final EntityPlayerMP fp = p; // Java 7: used inside inner class

        double r = 8.0;
        int limit = 20;

        if (args != null && args.length >= 1) {
            try {
                double rr = Double.parseDouble(args[0]);
                if (rr > 0) r = rr;
            } catch (Throwable ignored) {}
        }
        if (args != null && args.length >= 2) {
            try {
                int ll = Integer.parseInt(args[1]);
                if (ll > 0) limit = Math.min(ll, 80);
            } catch (Throwable ignored) {}
        }

        AxisAlignedBB box = fp.boundingBox.expand(r, r, r);
        List<EntityLivingBase> list = fp.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, box);

        if (list == null || list.isEmpty()) {
            fp.addChatMessage(new ChatComponentText("§7[Scan] §fNo living entities within §b" + r + "§f blocks."));
            return;
        }

        // nearest first
        Collections.sort(list, new Comparator<EntityLivingBase>() {
            @Override
            public int compare(EntityLivingBase a, EntityLivingBase b) {
                double da = a.getDistanceSqToEntity(fp);
                double db = b.getDistanceSqToEntity(fp);
                return Double.compare(da, db);
            }
        });

        fp.addChatMessage(new ChatComponentText("§7[Scan] §fEntities within §b" + r + "§f blocks:"));

        int shown = 0;
        for (EntityLivingBase e : list) {
            if (shown >= limit) break;
            if (e == null || e.isDead) continue;
            if (e == fp) continue;

            String name = e.getCommandSenderName();
            String cls = e.getClass().getSimpleName();

            float hp = e.getHealth();
            float mh = e.getMaxHealth();

            String line = "§8- §f" + name + " §8(" + cls + ") §8| §7HP §f"
                    + String.format("%.1f", hp) + "§7/§f" + String.format("%.1f", mh);

            float b = dbcBodyCur(e);
            float bm = dbcBodyMax(e);
            if (bm > 0f && b >= 0f) {
                line += " §8| §bBody §f" + (int) b + "§7/§f" + (int) bm;
            }

            fp.addChatMessage(new ChatComponentText(line));
            shown++;
        }

        if (list.size() > shown) {
            fp.addChatMessage(new ChatComponentText(
                    "§7[Scan] §8(+ " + (list.size() - shown) + " more; use /scanhealth " + r + " <limit>)"
            ));
        }
    }
}