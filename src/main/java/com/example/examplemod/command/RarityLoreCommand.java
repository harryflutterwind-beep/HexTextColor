// src/main/java/com/example/examplemod/command/RarityLoreCommand.java
package com.example.examplemod.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.util.Constants;

import java.util.*;

public class RarityLoreCommand extends CommandBase {

    private static final Map<String, String> COLOR_MAP;
    private static final List<String> SUBS_PUBLIC = Arrays.asList("list", "help");

    // ✅ Added: ring / amulet / artifact (now LORE tags, not name)
    private static final List<String> SUBS_OP = Arrays.asList(
            "remove", "chaosremove",
            "ring", "amulet", "artifact"
    );

    private static final List<String> CHAOS_LEVELS =
            Arrays.asList("chaotic","volatile","primordial","ascended");

    @Override public String getCommandName() { return "rarity"; }
    @Override public int getRequiredPermissionLevel() { return 0; }

    @Override
    public String getCommandUsage(ICommandSender s) {
        return "/rarity <" + join(mergedKeys(), "|") + "> [chaotic|volatile|primordial|ascended]";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayer)) {
            sender.addChatMessage(txt("§cPlayers only."));
            return;
        }
        EntityPlayer p = (EntityPlayer) sender;
        boolean isOp = sender.canCommandSenderUseCommand(2, getCommandName());

        // show pretty help box on bare /rarity
        if (args.length == 0) { showHelpBox(p, isOp); return; }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // public helpers
        if ("list".equals(sub)) {
            p.addChatMessage(txt("§8───────── <grad #3A7BFF #1ED1FF #3A7BFF scroll=0.14>§lRarity Tiers</grad> §8─────────"));
            p.addChatMessage(txt("§7Tiers: "
                    + "§fCommon§7, "
                    + "<grad #006600 #00CC00 #33FF33 #00CC00 #006600 scroll=0.16>Uncommon</grad>§7, "
                    + "<pulse #A64DFF styles=l speed=1.05 amp=0.22>Rare</pulse>§7, "
                    + "<grad #FF2BA6 #FF66CF #FF2BA6 scroll=0.14>E-Tech</grad>§7, "
                    + "<grad #3A7BFF #1ED1FF #3A7BFF scroll=0.14>Epic</grad>§7, "
                    + "<grad #FFB300 #FFF176 #FFB300 scroll=0.10>Legendary</grad>§7, "
                    + "<grad #40E0D0 #7FFFD4 #40E0D0 scroll=0.12>Pearlescent</grad>§7, "
                    + "<grad #FF66C4 #FF7FD2 #FF98DD #FFB3E6 #FF66C4 cycles=2 speed=0.25 pulse=1 pulsespeed=1.15 amp=0.28 scroll=0.34 styles=l>Seraph</grad>§7, "
                    + "<grad #FFB6DF #FFC6E6 #FFD6ED #FFC6E6 #FFB6DF cycles=2 scroll=0.22 pulse=1 pulsespeed=1.15 amp=0.26 styles=l>Glitch</grad>§7, "
                    + "<rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35><#FFFFFFl>Effervescent</#></rainbow>§7, "
                    + "<rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35>§k☆</rainbow>§r "
                    + "<rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35 styles=l>Effervescent</rainbow> "
                    + "<rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35>§k☆</rainbow>§7, "
                    + "<grad #000000 #0a0a0a #141414 #0a0a0a #000000 cycles=2 scroll=0.14 pulse=1 pulsespeed=1.10 amp=0.35 styles=l>BLACK</grad>"));
            p.addChatMessage(txt("§7Chaos: "
                    + "<grad #2BE3FF #8AF7FF scroll=0.20>Chaotic</grad>§7, "
                    + "<grad #2BE3FF #8AF7FF scroll=0.20>Volatile</grad>§7, "
                    + "<grad #2BE3FF #8AF7FF scroll=0.20>Primordial</grad>§7, "
                    + "<grad #2BE3FF #8AF7FF scroll=0.20>Ascended</grad>"));
            p.addChatMessage(txt("§8────────────────────────────────"));
            return;
        }

        if ("help".equals(sub)) { showHelpBox(p, isOp); return; }

        // ✅ NEW: wearable lore tags (Ring / Amulet / Artifact)
        if ("ring".equals(sub) || "amulet".equals(sub) || "artifact".equals(sub)) {
            if (!isOp) { p.addChatMessage(txt("§cYou must be op to use §f/rarity " + sub + "§c.")); return; }
            ItemStack held = p.getCurrentEquippedItem();
            if (held == null) { p.addChatMessage(txt("§cHold an item.")); return; }

            String styled = getWearableLoreTag(sub);
            boolean changed = replaceOrAddWearableLine(held, sub, styled);

            if (changed) p.addChatMessage(txt("§aApplied wearable lore: §f" + cap(sub)));
            else p.addChatMessage(txt("§eWearable lore already present."));

            return;
        }

        // removers
        if ("remove".equals(sub)) {
            if (!isOp) { p.addChatMessage(txt("§cYou must be op to use §f/rarity remove§c.")); return; }
            ItemStack held = p.getCurrentEquippedItem();
            if (held == null) { p.addChatMessage(txt("§cHold an item.")); return; }
            if (removeRarityLine(held)) p.addChatMessage(txt("§aRarity line removed."));
            else p.addChatMessage(txt("§cNo rarity line found."));
            return;
        }
        if ("chaosremove".equals(sub)) {
            if (!isOp) { p.addChatMessage(txt("§cYou must be op to use §f/rarity chaosremove§c.")); return; }
            ItemStack held = p.getCurrentEquippedItem();
            if (held == null) { p.addChatMessage(txt("§cHold an item.")); return; }
            if (removeChaosLine(held)) p.addChatMessage(txt("§aChaos line removed."));
            else p.addChatMessage(txt("§cNo chaos line found."));
            return;
        }

        // set rarity (+ optional chaos)
        if (!COLOR_MAP.containsKey(sub)) {
            p.addChatMessage(txt("§cUnknown tier: §f" + sub));
            return;
        }
        if (!isOp) { p.addChatMessage(txt("§cYou must be op to set rarity/chaos.")); return; }

        ItemStack held = p.getCurrentEquippedItem();
        if (held == null) { p.addChatMessage(txt("§cHold an item.")); return; }

        replaceOrAddRarityLine(held, COLOR_MAP.get(sub));

        boolean wroteChaos = false;
        String chaosArg = (args.length >= 2) ? args[1].toLowerCase(Locale.ROOT) : "";
        if (CHAOS_LEVELS.contains(chaosArg)) {
            replaceOrAddChaosLine(held, chaosArg);
            wroteChaos = true;
        }

        if (wroteChaos) {
            p.addChatMessage(txt("§aApplied rarity: §f" + sub + " §7| §bChaos §7→ §f" + cap(chaosArg)));
        } else {
            p.addChatMessage(txt("§aApplied rarity: §f" + sub));
        }
    }

    @Override
    @SuppressWarnings({"rawtypes","unchecked"})
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        boolean isOp = sender.canCommandSenderUseCommand(2, getCommandName());
        if (args.length == 1) {
            ArrayList<String> opts = new ArrayList<String>();
            opts.addAll(SUBS_PUBLIC);
            if (isOp) {
                opts.addAll(SUBS_OP);
                opts.addAll(COLOR_MAP.keySet());
            }
            return getListOfStringsMatchingLastWord(args, opts.toArray(new String[opts.size()]));
        } else if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, CHAOS_LEVELS.toArray(new String[CHAOS_LEVELS.size()]));
        }
        return Collections.emptyList();
    }

    /* ---------------- wearable lore helpers ---------------- */

    private static boolean replaceOrAddWearableLine(ItemStack stack, String keyLower, String newLine) {
        NBTTagCompound tag = ensureTag(stack);
        NBTTagCompound display = ensureDisplay(tag);
        NBTTagList lore = ensureLore(display);

        int idx = findWearableLineIndex(lore);
        if (idx >= 0) {
            String cur = stripTags(lore.getStringTagAt(idx)).toLowerCase(Locale.ROOT);
            if (cur.contains(keyLower)) {
                stack.setTagCompound(tag);
                return false; // already same type
            }

            NBTTagList out = new NBTTagList();
            for (int i = 0; i < lore.tagCount(); ++i) {
                out.appendTag(new NBTTagString(i == idx ? newLine : lore.getStringTagAt(i)));
            }
            lore = out;
        } else {
            int rIdx = findRarityLineIndex(lore);
            if (rIdx >= 0) {
                NBTTagList out = new NBTTagList();
                for (int i = 0; i < lore.tagCount(); ++i) {
                    if (i == rIdx) out.appendTag(new NBTTagString(newLine)); // insert BEFORE rarity
                    out.appendTag(new NBTTagString(lore.getStringTagAt(i)));
                }
                lore = out;
            } else {
                lore.appendTag(new NBTTagString(newLine));
            }
        }

        display.setTag("Lore", lore);
        stack.setTagCompound(tag);
        return true;
    }

    private static int findWearableLineIndex(NBTTagList lore) {
        if (lore == null) return -1;
        for (int i = 0; i < lore.tagCount(); ++i) {
            String s = stripTags(lore.getStringTagAt(i)).toLowerCase(Locale.ROOT);
            if (s.contains("ring") || s.contains("amulet") || s.contains("artifact")) return i;
        }
        return -1;
    }

    private static String getWearableLoreTag(String wordLower) {
        // Match your screenshot vibe (tweak freely)
        if ("amulet".equals(wordLower)) {
            return "<grad #FF3BD5 #FF66FF #B45CFF #FF3BD5 scroll=0.18 styles=l>Amulet</grad>";
        }
        if ("artifact".equals(wordLower)) {
            return "<grad #FFB300 #FFE066 #FFB300 scroll=0.18 styles=l>Artifact</grad>";
        }
        return "<grad #35E8FF #7FF7FF #35E8FF scroll=0.18 styles=l>Ring</grad>";
    }

    /* ---------------- rarity line ---------------- */

    private static boolean removeRarityLine(ItemStack stack) {
        NBTTagCompound tag = ensureTag(stack);
        NBTTagCompound display = ensureDisplay(tag);
        NBTTagList lore = ensureLore(display);
        int idx = findRarityLineIndex(lore);
        if (idx >= 0) {
            NBTTagList out = new NBTTagList();
            for (int i = 0; i < lore.tagCount(); ++i) {
                if (i != idx) out.appendTag(new NBTTagString(lore.getStringTagAt(i)));
            }
            display.setTag("Lore", out);
            stack.setTagCompound(tag);
            return true;
        }
        return false;
    }

    private static void replaceOrAddRarityLine(ItemStack stack, String newLine) {
        NBTTagCompound tag = ensureTag(stack);
        NBTTagCompound display = ensureDisplay(tag);
        NBTTagList lore = ensureLore(display);
        int idx = findRarityLineIndex(lore);

        if (idx >= 0) {
            NBTTagList out = new NBTTagList();
            for (int i = 0; i < lore.tagCount(); ++i) {
                out.appendTag(new NBTTagString(i == idx ? newLine : lore.getStringTagAt(i)));
            }
            lore = out;
        } else {
            lore.appendTag(new NBTTagString(newLine));
        }
        display.setTag("Lore", lore);
        stack.setTagCompound(tag);
    }

    private static int findRarityLineIndex(NBTTagList lore) {
        if (lore == null) return -1;
        for (int i = 0; i < lore.tagCount(); ++i) {
            String s = stripTags(lore.getStringTagAt(i)).toLowerCase(Locale.ROOT);
            for (String k : COLOR_MAP.keySet()) if (s.contains(k)) return i;
            if (s.contains("legend")) return i; // legacy catch
        }
        return -1;
    }

    /* ---------------- chaos line ---------------- */

    private static boolean removeChaosLine(ItemStack stack) {
        NBTTagCompound tag = ensureTag(stack);
        NBTTagCompound display = ensureDisplay(tag);
        NBTTagList lore = ensureLore(display);
        int idx = findChaosLineIndex(lore);
        if (idx >= 0) {
            NBTTagList out = new NBTTagList();
            for (int i = 0; i < lore.tagCount(); ++i) {
                if (i != idx) out.appendTag(new NBTTagString(lore.getStringTagAt(i)));
            }
            display.setTag("Lore", out);
            stack.setTagCompound(tag);
            return true;
        }
        return false;
    }

    private static void replaceOrAddChaosLine(ItemStack stack, String chaosLower) {
        String line = "§b§l" + cap(chaosLower);
        NBTTagCompound tag = ensureTag(stack);
        NBTTagCompound display = ensureDisplay(tag);
        NBTTagList lore = ensureLore(display);

        int cIdx = findChaosLineIndex(lore);
        if (cIdx >= 0) {
            NBTTagList out = new NBTTagList();
            for (int i = 0; i < lore.tagCount(); ++i) {
                out.appendTag(new NBTTagString(i == cIdx ? line : lore.getStringTagAt(i)));
            }
            lore = out;
        } else {
            int rIdx = findRarityLineIndex(lore);
            if (rIdx >= 0) {
                NBTTagList out = new NBTTagList();
                for (int i = 0; i < lore.tagCount(); ++i) {
                    out.appendTag(new NBTTagString(lore.getStringTagAt(i)));
                    if (i == rIdx) out.appendTag(new NBTTagString(line));
                }
                lore = out;
            } else {
                lore.appendTag(new NBTTagString(line));
            }
        }

        display.setTag("Lore", lore);
        stack.setTagCompound(tag);
    }

    private static int findChaosLineIndex(NBTTagList lore) {
        if (lore == null) return -1;
        for (int i = 0; i < lore.tagCount(); ++i) {
            String s = stripTags(lore.getStringTagAt(i)).toLowerCase(Locale.ROOT).trim();
            if (s.startsWith("chaos:")) s = s.substring("chaos:".length()).trim();
            if (CHAOS_LEVELS.contains(s)) return i;
            String raw = stripTags(lore.getStringTagAt(i)).toLowerCase(Locale.ROOT);
            if (raw.startsWith("chaos:")) return i;
        }
        return -1;
    }

    /* ---------------- NBT helpers ---------------- */

    private static NBTTagCompound ensureTag(ItemStack s) {
        if (!s.hasTagCompound()) s.setTagCompound(new NBTTagCompound());
        return s.getTagCompound();
    }

    private static NBTTagCompound ensureDisplay(NBTTagCompound tag) {
        if (!tag.hasKey("display", Constants.NBT.TAG_COMPOUND))
            tag.setTag("display", new NBTTagCompound());
        return tag.getCompoundTag("display");
    }

    private static NBTTagList ensureLore(NBTTagCompound display) {
        if (!display.hasKey("Lore", Constants.NBT.TAG_LIST))
            display.setTag("Lore", new NBTTagList());
        return display.getTagList("Lore", Constants.NBT.TAG_STRING);
    }

    /* ---------------- UI helpers ---------------- */

    private static void showHelpBox(EntityPlayer p, boolean isOp) {
        p.addChatMessage(txt("§8────────────────────────────────────────────"));
        p.addChatMessage(txt("<grad #3A7BFF #1ED1FF #3A7BFF scroll=0.14>§l/rarity</grad> §7— apply a rarity header (§8+§7 optional Chaos)"));
        p.addChatMessage(txt("§8────────────────────────────────────────────"));

        p.addChatMessage(txt("§7Usage: §f/rarity <tier> §8["
                + "<grad #2BE3FF #8AF7FF scroll=0.20>Chaotic</grad>§7|"
                + "<grad #2BE3FF #8AF7FF scroll=0.20>Volatile</grad>§7|"
                + "<grad #2BE3FF #8AF7FF scroll=0.20>Primordial</grad>§7|"
                + "<grad #2BE3FF #8AF7FF scroll=0.20>Ascended</grad>§8]"));

        if (isOp) {
            p.addChatMessage(txt("§7Also:  §f/rarity remove §8• §f/rarity chaosremove"));
            p.addChatMessage(txt("§7Lore:  §f/rarity ring §8• §f/rarity amulet §8• §f/rarity artifact"));
        }

        p.addChatMessage(txt("§7Tiers: "
                + "§fCommon§7, "
                + "<grad #006600 #00CC00 #33FF33 #00CC00 #006600 scroll=0.16>Uncommon</grad>§7, "
                + "<pulse #A64DFF styles=l speed=1.05 amp=0.22>Rare</pulse>§7, "
                + "<grad #FF2BA6 #FF66CF #FF2BA6 scroll=0.14>E-Tech</grad>§7, "
                + "<grad #3A7BFF #1ED1FF #3A7BFF scroll=0.14>Epic</grad>§7, "
                + "<grad #FFB300 #FFF176 #FFB300 scroll=0.10>Legendary</grad>§7"));

        p.addChatMessage(txt("§7       "
                + "<grad #40E0D0 #7FFFD4 #40E0D0 scroll=0.12>Pearlescent</grad>§7, "
                + "<grad #FF66C4 #FF7FD2 #FF98DD #FFB3E6 #FF66C4 cycles=2 speed=0.25 pulse=1 pulsespeed=1.15 amp=0.28 scroll=0.34 styles=l>Seraph</grad>§7, "
                + "<grad #FFB6DF #FFC6E6 #FFD6ED #FFC6E6 #FFB6DF cycles=2 scroll=0.22 pulse=1 pulsespeed=1.15 amp=0.26 styles=l>Glitch</grad>§7"));

        p.addChatMessage(txt("§7       "
                + "<rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35><#FFFFFFl>Effervescent</#></rainbow>§7, "
                + "<rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35>§k☆</rainbow>§r "
                + "<rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35 styles=l>Effervescent</rainbow> "
                + "<rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35>§k☆</rainbow>§7, "
                + "<grad #000000 #0a0a0a #141414 #0a0a0a #000000 cycles=2 scroll=0.14 pulse=1 pulsespeed=1.10 amp=0.35 styles=l>BLACK</grad>"));

        p.addChatMessage(txt("§7Chaos: "
                + "<grad #2BE3FF #8AF7FF scroll=0.20>Chaotic</grad>§7, "
                + "<grad #2BE3FF #8AF7FF scroll=0.20>Volatile</grad>§7, "
                + "<grad #2BE3FF #8AF7FF scroll=0.20>Primordial</grad>§7, "
                + "<grad #2BE3FF #8AF7FF scroll=0.20>Ascended</grad>"));

        p.addChatMessage(txt("§8────────────────────────────────────────────"));
    }

    /* ---------------- tiny utils ---------------- */

    private static String stripTags(String s) {
        if (s == null) return "";
        s = s.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
        s = s.replaceAll("<[^>]+>", "");
        return s;
    }

    private static String cap(String s) {
        return (s == null || s.isEmpty()) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String join(List<String> list, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private static List<String> mergedKeys() {
        ArrayList<String> out = new ArrayList<String>();
        out.addAll(SUBS_PUBLIC);
        out.addAll(SUBS_OP);
        out.addAll(COLOR_MAP.keySet());
        return out;
    }

    private static ChatComponentText txt(String s) { return new ChatComponentText(s); }

    static {
        Map<String,String> m = new LinkedHashMap<String,String>();
        m.put("common",        "§r§l<grad #FFFFFF #DADADA scroll=0.10 pulse=1 pulsespeed=1.0 amp=0.20>Common</grad>§r");
        m.put("uncommon",      "§r§l<grad #006600 #00CC00 #33FF33 #00CC00 #006600 scroll=0.16>Uncommon</grad>§r");
        m.put("rare",          "§r<pulse #A64DFF styles=l speed=1.05 amp=0.22>Rare</pulse>§r");
        m.put("etech",         "§r§l<grad #FF2BA6 #FF66CF #FF2BA6 scroll=0.14>E-Tech</grad>§r");
        m.put("epic",          "§r§l<grad #3A7BFF #1ED1FF #3A7BFF scroll=0.14>Epic</grad>§r");
        m.put("legendary",     "§r§l<grad #FFB300 #FFF176 #FFB300 scroll=0.10>Legendary</grad>§r");
        m.put("pearlescent",   "§r§l<grad #40E0D0 #7FFFD4 #40E0D0 scroll=0.12>Pearlescent</grad>§r");
        m.put("seraph",        "§r§l<grad #FF66C4 #FF7FD2 #FF98DD #FFB3E6 #FF66C4 cycles=2 speed=0.25 pulse=1 pulsespeed=1.15 amp=0.28 scroll=0.34 styles=l>Seraph</grad>§r");
        m.put("glitch",        "§r§l<grad #FFB6DF #FFC6E6 #FFD6ED #FFC6E6 #FFB6DF cycles=2 scroll=0.22 pulse=1 pulsespeed=1.15 amp=0.26 styles=l>Glitch</grad>§r");
        m.put("effervescent",  "<rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35><#FFFFFFl>Effervescent</#></rainbow>");
        m.put("effervescent_", "<rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35>§k☆</rainbow>§r <rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35>☆</rainbow> <rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35 styles=l>Effervescent</rainbow> <rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35>☆</rainbow> <rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35>§k☆</rainbow>§r");
        m.put("black",         "§r§l<grad #000000 #0a0a0a #141414 #0a0a0a #000000 cycles=2 scroll=0.14 pulse=1 pulsespeed=1.10 amp=0.35 styles=l>BLACK</grad>§r");
        COLOR_MAP = Collections.unmodifiableMap(m);
    }
}
