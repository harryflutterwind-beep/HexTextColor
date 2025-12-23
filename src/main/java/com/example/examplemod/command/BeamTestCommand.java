package com.example.examplemod.command;

import com.example.examplemod.beams.RarityDetect;
import com.example.examplemod.beams.RarityTags;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ChatComponentText;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class BeamTestCommand extends CommandBase {

    // ── Evolved header map (same as your JS evolvedHeaderMap) ──
    private static final Map<String, String> EVOLVED_HEADERS = new HashMap<String, String>();
    static {
        EVOLVED_HEADERS.put("common",
                "§r§l<grad #FFFFFF #DADADA scroll=0.18 pulse=1 pulsespeed=1.6 amp=0.20>Common ✦ Evolved</grad>§r");
        EVOLVED_HEADERS.put("uncommon",
                "§r§l<grad #006600 #00CC00 #33FF33 #00CC00 #006600 scroll=0.30>Uncommon ✦ Evolved</grad>§r");
        EVOLVED_HEADERS.put("rare",
                "§r<pulse #A64DFF styles=l speed=1.8 amp=0.22>Rare ✦ Evolved</pulse>§r");
        EVOLVED_HEADERS.put("etech",
                // special full-width Ｅ only for the evolved header
                "§r§l<grad #FF2BA6 #FF66CF #FF2BA6 scroll=0.22>Ｅ-Tech ✦ Evolved</grad>§r");
        EVOLVED_HEADERS.put("epic",
                "§r§l<grad #3A7BFF #1ED1FF #3A7BFF scroll=0.25>Epic ✦ Evolved</grad>§r");
        EVOLVED_HEADERS.put("legendary",
                "§r§l<grad #FFB300 #FFF176 #FFB300 scroll=0.18>Legendary ✦ Evolved</grad>§r");
        EVOLVED_HEADERS.put("pearlescent",
                "§r§l<grad #00CED1 #48D1CC #00BFC6 #00CED1 scroll=0.22>Pearlescent ✦ Evolved</grad>§r");
        EVOLVED_HEADERS.put("seraph",
                "§r§l<grad #FF66C4 #FF7FD2 #FF98DD #FFB3E6 #FF66C4 cycles=2 pulse=1 pulsespeed=1.8 amp=0.28 scroll=0.60 styles=l>Seraph ✦ Evolved</grad>§r");
        EVOLVED_HEADERS.put("glitch",
                "§r§l<grad #FFC1E3 #FFD1EA #FFE2F2 #FFD1EA #FFC1E3 cycles=2 scroll=0.50 pulse=1 pulsespeed=1.80 amp=0.30 styles=l>Glitch ✦ Evolved</grad>§r");
        EVOLVED_HEADERS.put("effervescent",
                "§r§l<rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35 styles=l>Effervescent ✦ Evolved</rainbow>§r");
        EVOLVED_HEADERS.put("effervescent_",
                "<rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35>§k☆</rainbow>§r " +
                        "<rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35>☆</rainbow> " +
                        "<rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35 styles=l>Effervescent ✦ Evolved</rainbow> " +
                        "<rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35>☆</rainbow> " +
                        "<rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35>§k☆</rainbow>§r");
        EVOLVED_HEADERS.put("black",
                "§r§l<grad #000000 #0a0a0a #141414 #0a0a0a #000000 cycles=2 scroll=0.26 pulse=1 pulsespeed=1.7 amp=0.35 styles=l>BLACK ✦ EVOLVED</grad>§r");
    }

    // order to display when listing
    private static final String[] ORDERED_KEYS = new String[] {
            "common","uncommon","rare","epic","legendary",
            "pearlescent","seraph","etech","glitch","effervescent","effervescent_","black"
    };

    // words that identify the *base* rarity header (from /rarity or scripts)
    private static final String[] BASE_RARITY_WORDS = new String[] {
            "common","uncommon","rare","e-tech","etech","epic",
            "legendary","pearlescent","seraph","glitch","effervescent","black"
    };

    // strip helper (for removing old evolved / rarity lines)
    private static final Pattern MC_CODES  = Pattern.compile("§[0-9A-FK-ORa-fk-or]");
    private static final Pattern ANGLE_TAG = Pattern.compile("<[^>]+>");

    private static String stripFormatting(String s) {
        if (s == null) return "";
        String out = MC_CODES.matcher(s).replaceAll("");
        out = ANGLE_TAG.matcher(out).replaceAll("");
        return out.trim().toLowerCase(Locale.ROOT);
    }

    // does this plain, lowercased line look like a base rarity header?
    private static boolean isBaseRarityPlain(String plainLower) {
        if (plainLower == null || plainLower.isEmpty()) return false;

        for (String w : BASE_RARITY_WORDS) {
            if (plainLower.equals(w)) return true;

            // allow short decorative lines that contain the rarity name
            if (plainLower.contains(w) && plainLower.length() <= 40) {
                return true;
            }
        }
        return false;
    }

    // normalize aliases → canonical key
    private static String normalizeKey(String in) {
        if (in == null) return "";
        String k = in.trim().toLowerCase(Locale.ROOT);

        if (k.equals("eff") || k.equals("effervescent"))   return "effervescent";
        if (k.equals("eff_") || k.equals("eff2")
                || k.equals("effervescent_"))              return "effervescent_";
        if (k.equals("leg") || k.equals("lego"))           return "legendary";
        if (k.equals("pearls") || k.equals("pearl"))       return "pearlescent";
        if (k.equals("srp"))                               return "seraph";
        if (k.equals("gl") || k.equals("glitchy"))         return "glitch";
        if (k.equals("blk") || k.equals("black_"))         return "black";
        if (k.equals("et") || k.equals("e-tech")
                || k.equals("etech"))                      return "etech";

        if (EVOLVED_HEADERS.containsKey(k)) return k;
        return k;
    }

    // ─────────────────────────────────────

    @Override
    public String getCommandName() {
        return "evolve";          // /evolve ...
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/evolve <rarity>  - set rarity tag + add evolved header to held item\n" +
                "/evolve rarity    - list all valid rarities (colored)";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.addChatMessage(new ChatComponentText("Players only."));
            return;
        }

        EntityPlayerMP p = (EntityPlayerMP) sender;

        if (args.length < 1) {
            p.addChatMessage(new ChatComponentText("§cUsage: §f/evolve <rarity>"));
            return;
        }

        String sub = args[0];

        // ── /evolve rarity | rarities | list → show all with colors ──
        if ("rarity".equalsIgnoreCase(sub)
                || "rarities".equalsIgnoreCase(sub)
                || "list".equalsIgnoreCase(sub)) {

            p.addChatMessage(new ChatComponentText("§b[Evolve] §fValid rarities:"));
            for (String key : ORDERED_KEYS) {
                String header = EVOLVED_HEADERS.get(key);
                if (header == null) continue;
                p.addChatMessage(new ChatComponentText(
                        header + " §7(§f" + key + "§7)"
                ));
            }
            p.addChatMessage(new ChatComponentText("§7Use §f/evolve <rarity> §7with one of the keys above."));
            return;
        }

        // ── /evolve <rarity> → apply rarity + evolved header ──
        ItemStack held = p.getHeldItem();
        if (held == null) {
            p.addChatMessage(new ChatComponentText("§c[Evolve] Hold an item first."));
            return;
        }

        String key = normalizeKey(sub);
        if (!EVOLVED_HEADERS.containsKey(key)) {
            p.addChatMessage(new ChatComponentText(
                    "§c[Evolve] Unknown rarity: §f" + sub +
                            "§c. Try §f/evolve rarity §cfor a list."
            ));
            return;
        }

        // Ensure NBT + set rarity + height for beams
        NBTTagCompound tag = held.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            held.setTagCompound(tag);
        }

        tag.setString(RarityTags.KEY, key);                         // <- beam key: "etech"
        tag.setInteger(RarityTags.HKEY, RarityDetect.beamHeight(key));

        // Rewrite lore: evolved header + [key] marker + old lines (minus old headers/markers)
        NBTTagCompound display = tag.getCompoundTag("display");
        if (!tag.hasKey("display", 10)) {
            tag.setTag("display", display);
        }

        NBTTagList lore    = display.getTagList("Lore", 8);
        NBTTagList newLore = new NBTTagList();

        String header     = EVOLVED_HEADERS.get(key);
        String markerText = "§8[" + key + "]";   // e.g. "§8[etech]"

        newLore.appendTag(new NBTTagString(header));      // line 1: fancy evolved header
        newLore.appendTag(new NBTTagString(markerText));  // line 2: plain marker

        String markerPlain = "[" + key + "]";

        for (int i = 0; i < lore.tagCount(); i++) {
            String line  = lore.getStringTagAt(i);
            String plain = stripFormatting(line);   // already lowercased

            // 1) strip any old evolved header lines
            if (plain.contains("evolved")) continue;

            // 2) strip any old [key] marker lines
            if (plain.equals(markerPlain.toLowerCase(Locale.ROOT))) continue;

            // 3) strip base rarity headers (from /rarity etc.)
            if (isBaseRarityPlain(plain)) continue;

            // anything else stays
            newLore.appendTag(new NBTTagString(line));
        }

        display.setTag("Lore", newLore);

        p.addChatMessage(new ChatComponentText(
                "§a[Evolve] Set rarity to §e" + key +
                        "§a and applied evolved header to §f" + held.getDisplayName()
        ));
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }
}
