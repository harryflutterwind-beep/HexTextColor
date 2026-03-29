package com.example.examplemod.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class CommandDBCBody extends CommandBase {

    @Override
    public String getCommandName() {
        return "dbcbody";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/dbcbody [player]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    private static final String[] DBC_BODY_CUR_KEYS = {"jrmcBdy", "jrmcBdyCur", "jrmcBdyc"};
    private static final String[] DBC_BODY_MAX_KEYS = {"jrmcBdyF", "jrmcBdyMax", "jrmcBdyM"};
    private static final String TAG_HEX_FRAC_BODY_MAX = "HexFracBodyMax";

    private static boolean JRM_LOOKUP_DONE = false;
    private static Method JRM_GET_INT = null;

    private static void ensureJrmGetInt() {
        if (JRM_LOOKUP_DONE) return;
        JRM_LOOKUP_DONE = true;

        try {
            Class<?> c = Class.forName("JinRyuu.JRMCore.JRMCoreH");
            Method[] methods = c.getMethods();
            for (Method m : methods) {
                if (!Modifier.isStatic(m.getModifiers())) continue;
                if (!"getInt".equals(m.getName())) continue;

                Class<?> rt = m.getReturnType();
                if (!(rt == int.class || rt == Integer.class)) continue;

                Class<?>[] pt = m.getParameterTypes();
                if (pt.length != 2) continue;

                boolean ep0 = EntityPlayer.class.isAssignableFrom(pt[0]) && pt[1] == String.class;
                boolean ep1 = pt[0] == String.class && EntityPlayer.class.isAssignableFrom(pt[1]);
                if (ep0 || ep1) {
                    JRM_GET_INT = m;
                    break;
                }
            }
        } catch (Throwable ignored) {}
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
            if (r instanceof Integer) return ((Integer) r).intValue();
        } catch (Throwable ignored) {}
        return Integer.MIN_VALUE;
    }

    private static float readNbtNumber(NBTTagCompound nbt, String key) {
        if (nbt == null || key == null || !nbt.hasKey(key)) return Float.NaN;
        try {
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

    private static String findFirstPresentKey(NBTTagCompound nbt, String[] keys) {
        if (nbt == null || keys == null) return null;
        for (String k : keys) {
            float v = readNbtNumber(nbt, k);
            if (!Float.isNaN(v)) return k;
        }
        return null;
    }

    private static EntityPlayerMP resolveTarget(ICommandSender sender, String[] args) {
        if (args != null && args.length >= 1 && args[0] != null && args[0].trim().length() > 0) {
            try {
                return getPlayer(sender, args[0]);
            } catch (Throwable ignored) {
                MinecraftServer srv = MinecraftServer.getServer();
                if (srv != null && srv.getConfigurationManager() != null) {
                    EntityPlayerMP p = srv.getConfigurationManager().func_152612_a(args[0]);
                    if (p != null) return p;
                }
                return null;
            }
        }

        try {
            return getCommandSenderAsPlayer(sender);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String fmtFloat(float v) {
        if (Float.isNaN(v)) return "n/a";
        if (Math.abs(v - Math.round(v)) < 0.0001f) return String.valueOf((int) Math.round(v));
        return String.format("%.2f", v);
    }

    private static String fmtPct(float cur, float max) {
        if (!(cur >= 0f) || !(max > 0f)) return "n/a";
        float pct = (cur / max) * 100.0f;
        if (pct < 0f) pct = 0f;
        return String.format("%.2f%%", pct);
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args != null && args.length == 1) {
            return getListOfStringsMatchingLastWord(args, MinecraftServer.getServer().getAllUsernames());
        }
        return null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        EntityPlayerMP target = resolveTarget(sender, args);
        if (target == null) {
            sender.addChatMessage(new ChatComponentText("§cUsage: " + getCommandUsage(sender)));
            if (args != null && args.length >= 1) {
                sender.addChatMessage(new ChatComponentText("§cPlayer not found: §f" + args[0]));
            }
            return;
        }

        NBTTagCompound ed = target.getEntityData();
        NBTTagCompound pp = ed != null && ed.hasKey("PlayerPersisted", 10) ? ed.getCompoundTag("PlayerPersisted") : null;

        List lines = new ArrayList();

        float jrmCur = Float.NaN;
        String jrmCurKey = null;
        for (int i = 0; i < DBC_BODY_CUR_KEYS.length; i++) {
            int v = jrmGetIntSafe(target, DBC_BODY_CUR_KEYS[i]);
            if (v >= 0) {
                jrmCur = (float) v;
                jrmCurKey = DBC_BODY_CUR_KEYS[i];
                break;
            }
        }

        float jrmMax = Float.NaN;
        String jrmMaxKey = null;
        for (int i = 0; i < DBC_BODY_MAX_KEYS.length; i++) {
            int v = jrmGetIntSafe(target, DBC_BODY_MAX_KEYS[i]);
            if (v > 0) {
                jrmMax = (float) v;
                jrmMaxKey = DBC_BODY_MAX_KEYS[i];
                break;
            }
        }

        float rootCur = readFirstNumber(ed, DBC_BODY_CUR_KEYS);
        String rootCurKey = findFirstPresentKey(ed, DBC_BODY_CUR_KEYS);
        float rootMax = readFirstNumber(ed, DBC_BODY_MAX_KEYS);
        String rootMaxKey = findFirstPresentKey(ed, DBC_BODY_MAX_KEYS);

        float ppCur = readFirstNumber(pp, DBC_BODY_CUR_KEYS);
        String ppCurKey = findFirstPresentKey(pp, DBC_BODY_CUR_KEYS);
        float ppMax = readFirstNumber(pp, DBC_BODY_MAX_KEYS);
        String ppMaxKey = findFirstPresentKey(pp, DBC_BODY_MAX_KEYS);

        float cacheMax = readNbtNumber(ed, TAG_HEX_FRAC_BODY_MAX);
        if (Float.isNaN(cacheMax) && pp != null) {
            cacheMax = readNbtNumber(pp, TAG_HEX_FRAC_BODY_MAX);
        }

        float resolvedCur = !Float.isNaN(jrmCur) ? jrmCur : (!Float.isNaN(rootCur) ? rootCur : ppCur);
        float resolvedMax = !Float.isNaN(jrmMax) ? jrmMax : (!Float.isNaN(rootMax) ? rootMax : (!Float.isNaN(ppMax) ? ppMax : cacheMax));

        lines.add(new ChatComponentText("§b[DBC Body] §f" + target.getCommandSenderName()));
        lines.add(new ChatComponentText(
                "§7Resolved: §f" + fmtFloat(resolvedCur) + "§7/§f" + fmtFloat(resolvedMax) +
                        " §8| §b" + fmtPct(resolvedCur, resolvedMax)
        ));
        lines.add(new ChatComponentText(
                "§7JRM: §fcur §8(" + (jrmCurKey == null ? "none" : jrmCurKey) + "§8)=§f" + fmtFloat(jrmCur) +
                        " §7max §8(" + (jrmMaxKey == null ? "none" : jrmMaxKey) + "§8)=§f" + fmtFloat(jrmMax)
        ));
        lines.add(new ChatComponentText(
                "§7Root NBT: §fcur §8(" + (rootCurKey == null ? "none" : rootCurKey) + "§8)=§f" + fmtFloat(rootCur) +
                        " §7max §8(" + (rootMaxKey == null ? "none" : rootMaxKey) + "§8)=§f" + fmtFloat(rootMax)
        ));
        lines.add(new ChatComponentText(
                "§7Persisted: §fcur §8(" + (ppCurKey == null ? "none" : ppCurKey) + "§8)=§f" + fmtFloat(ppCur) +
                        " §7max §8(" + (ppMaxKey == null ? "none" : ppMaxKey) + "§8)=§f" + fmtFloat(ppMax)
        ));
        lines.add(new ChatComponentText(
                "§7Cache: §fHexFracBodyMax=§f" + fmtFloat(cacheMax) +
                        " §8| §7Vanilla HP=§f" + fmtFloat(target.getHealth()) + "§7/§f" + fmtFloat(target.getMaxHealth())
        ));

        for (int i = 0; i < lines.size(); i++) {
            sender.addChatMessage((ChatComponentText) lines.get(i));
        }
    }
}
