package com.example.examplemod.command;

import com.example.examplemod.beams.RarityDetect;
import com.example.examplemod.beams.RarityTags;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.util.Constants;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RarityLoreCommand extends CommandBase {

    private static final Map<String, String> COLOR_MAP;
    private static final Map<String, String> EVOLVED_MAP;
    private static final Map<Integer, Integer> EVOLVE_TIER_PCT;

    private static final List<String> SUBS_PUBLIC = Arrays.asList("list", "help");
    private static final List<String> SUBS_OP = Arrays.asList(
            "remove", "chaosremove",
            "ring", "amulet", "artifact",
            "pblack", "pb", "pseudoblack",
            "evotest", "evo", "evolved"
    );

    private static final List<String> CHAOS_LEVELS =
            Arrays.asList("chaotic", "volatile", "primordial", "ascended");

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

        if (args.length == 0) { showHelpBox(p, isOp); return; }

        String sub = args[0].toLowerCase(Locale.ROOT);

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
            p.addChatMessage(txt("§7Ops: §f/rarity pblack §8[base] §7• §f/rarity evotest §81|2|3"));
            p.addChatMessage(txt("§7Chaos: "
                    + "<grad #2BE3FF #8AF7FF scroll=0.20>Chaotic</grad>§7, "
                    + "<grad #2BE3FF #8AF7FF scroll=0.20>Volatile</grad>§7, "
                    + "<grad #2BE3FF #8AF7FF scroll=0.20>Primordial</grad>§7, "
                    + "<grad #2BE3FF #8AF7FF scroll=0.20>Ascended</grad>"));
            p.addChatMessage(txt("§8────────────────────────────────"));
            return;
        }

        if ("help".equals(sub)) { showHelpBox(p, isOp); return; }

        if ("evotest".equals(sub) || "evo".equals(sub) || "evolved".equals(sub)) {
            if (!isOp) { p.addChatMessage(txt("§cYou must be op to use §f/rarity " + sub + "§c.")); return; }
            ItemStack held = p.getCurrentEquippedItem();
            if (held == null) { p.addChatMessage(txt("§cHold an item.")); return; }

            int tier = parseTier(args.length >= 2 ? args[1] : "1");
            if (tier < 1 || tier > 3) {
                p.addChatMessage(txt("§cUsage: §f/rarity " + sub + " 1|2|3"));
                return;
            }

            String rarityKey = applyEvolveTestLore(held, tier);
            p.addChatMessage(txt("§aApplied evolve test tier §e" + tier + "§a using §f" + baseDisplayLabel(rarityKey) + "§a colors."));
            return;
        }

        if ("pblack".equals(sub) || "pb".equals(sub) || "pseudoblack".equals(sub)) {
            if (!isOp) { p.addChatMessage(txt("§cYou must be op to use §f/rarity " + sub + "§c.")); return; }
            ItemStack held = p.getCurrentEquippedItem();
            if (held == null) { p.addChatMessage(txt("§cHold an item.")); return; }

            String requestedBase = (args.length >= 2) ? normalizeRarityKey(args[1]) : "";
            String base = resolvePseudoBlackBase(held, requestedBase);
            if ("black".equals(base)) {
                p.addChatMessage(txt("§cBlack items cannot be converted into Pseudo Black."));
                return;
            }
            if (base.isEmpty()) base = "common";

            applyPseudoBlackOverlay(held, base);
            p.addChatMessage(txt("§aApplied pseudo-black overlay using base rarity: §f" + baseDisplayLabel(base)));
            return;
        }

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

        if ("remove".equals(sub)) {
            if (!isOp) { p.addChatMessage(txt("§cYou must be op to use §f/rarity remove§c.")); return; }
            ItemStack held = p.getCurrentEquippedItem();
            if (held == null) { p.addChatMessage(txt("§cHold an item.")); return; }
            if (removeRarityLine(held)) {
                clearRarityTags(held);
                p.addChatMessage(txt("§aRarity line removed."));
            } else p.addChatMessage(txt("§cNo rarity line found."));
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

        if (!COLOR_MAP.containsKey(sub)) {
            p.addChatMessage(txt("§cUnknown tier: §f" + sub));
            return;
        }
        if (!isOp) { p.addChatMessage(txt("§cYou must be op to set rarity/chaos.")); return; }

        ItemStack held = p.getCurrentEquippedItem();
        if (held == null) { p.addChatMessage(txt("§cHold an item.")); return; }

        replaceOrAddRarityLine(held, COLOR_MAP.get(sub));
        writeRarityTag(held, sub);
        clearEvolveFlags(held);

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
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("pblack".equals(sub) || "pb".equals(sub) || "pseudoblack".equals(sub)) {
                ArrayList<String> bases = new ArrayList<String>(COLOR_MAP.keySet());
                bases.remove("black");
                return getListOfStringsMatchingLastWord(args, bases.toArray(new String[bases.size()]));
            }
            if ("evotest".equals(sub) || "evo".equals(sub) || "evolved".equals(sub)) {
                return getListOfStringsMatchingLastWord(args, new String[]{"1", "2", "3"});
            }
            return getListOfStringsMatchingLastWord(args, CHAOS_LEVELS.toArray(new String[CHAOS_LEVELS.size()]));
        }
        return Collections.emptyList();
    }

    private static String applyEvolveTestLore(ItemStack stack, int tier) {
        if (tier < 1) tier = 1;
        if (tier > 3) tier = 3;

        String rarityKey = detectAppliedRarityKey(stack);
        if (rarityKey == null || rarityKey.length() == 0) rarityKey = "common";

        String header = getEvolvedHeader(rarityKey);
        ArrayList<String> lore = getLoreLines(stack);
        lore = stripExistingEvolveLore(lore);

        int rarityIdx = findRarityLineIndex(lore);
        if (rarityIdx >= 0) lore.set(rarityIdx, header);
        else lore.add(header);

        if ("effervescent_".equals(rarityKey)) {
            removeExactCleanLine(lore, "[effervescent+]");
            int insertAt = findRarityLineIndex(lore);
            if (insertAt < 0) insertAt = 0;
            lore.add(Math.min(insertAt + 1, lore.size()), "§8[effervescent+]");
        }

        lore.add("§7Evolved: Tier §e" + tier);
        String starLine = getEvolveStarLine(rarityKey, tier);
        if (starLine != null && starLine.length() > 0) lore.add(starLine);
        lore.add("§7Evolution Boost: §a+" + getEvolveBoostPercent(tier) + "%");

        lore = redirectEvolveInfoToLorePage(stack, lore);
        setLoreLines(stack, lore);

        NBTTagCompound tag = ensureTag(stack);
        if ("pseudo_black".equals(rarityKey)) {
            tag.setString(RarityTags.KEY, "pseudo_black");
            tag.setBoolean(RarityTags.PSEUDO_BLACK, true);
        } else {
            tag.setString(RarityTags.KEY, rarityKey);
        }
        tag.setBoolean("isEvolved", true);
        tag.setInteger("evolveTier", tier);
        stack.setTagCompound(tag);

        return rarityKey;
    }

    private static ArrayList<String> stripExistingEvolveLore(List<String> lore) {
        ArrayList<String> out = new ArrayList<String>();
        if (lore == null) return out;
        for (int i = 0; i < lore.size(); i++) {
            String raw = String.valueOf(lore.get(i));
            if (isEvolveInfoLine(raw)) continue;
            out.add(raw);
        }
        return out;
    }

    private static ArrayList<String> redirectEvolveInfoToLorePage(ItemStack stack, List<String> loreArr) {
        ArrayList<String> keep = new ArrayList<String>();
        ArrayList<String> move = new ArrayList<String>();
        if (loreArr == null) return keep;

        for (int i = 0; i < loreArr.size(); i++) {
            String line = String.valueOf(loreArr.get(i));
            if (shouldMoveToEvolvePage(line, i)) move.add(line);
            else keep.add(line);
        }

        if (move.isEmpty()) return keep;

        try {
            Class<?> api = findLorePagesApi();
            if (api == null) return keep;

            Method read = api.getMethod("readPagesFromNBT", ItemStack.class);
            Method write = api.getMethod("writePagesToNBT", ItemStack.class, List.class);

            Object existing = null;
            try { existing = read.invoke(null, stack); } catch (Throwable ignored) { existing = null; }
            ArrayList<List<String>> pages = clonePages(existing);
            upsertEvolveLorePage(pages, move);
            write.invoke(null, stack, pages);
        } catch (Throwable ignored) {
        }

        return keep;
    }

    private static Class<?> findLorePagesApi() {
        try { return Class.forName("com.example.examplemod.api.HexLorePagesAPI"); }
        catch (Throwable ignored) {
            try { return Class.forName("com.example.examplemod.api.LorePagesAPI"); }
            catch (Throwable ignored2) { return null; }
        }
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static ArrayList<List<String>> clonePages(Object existing) {
        ArrayList<List<String>> pages = new ArrayList<List<String>>();
        if (!(existing instanceof Iterable)) return pages;

        for (Object pageObj : (Iterable) existing) {
            ArrayList<String> page = new ArrayList<String>();
            if (pageObj instanceof Iterable) {
                for (Object lineObj : (Iterable) pageObj) {
                    page.add(String.valueOf(lineObj == null ? "" : lineObj));
                }
            }
            pages.add(page);
        }
        return pages;
    }

    private static void upsertEvolveLorePage(ArrayList<List<String>> pages, List<String> moveLines) {
        int idx = findExistingEvolveLorePageIndex(pages);
        ArrayList<String> newPage = new ArrayList<String>();

        if (idx >= 0) {
            List<String> src = pages.get(idx);
            for (int i = 0; i < src.size(); i++) {
                String s = String.valueOf(src.get(i));
                if (isEvolveInfoLine(s)) continue;
                newPage.add(s);
            }
            for (int j = 0; j < moveLines.size(); j++) newPage.add(String.valueOf(moveLines.get(j)));
            pages.set(idx, newPage);
            return;
        }

        for (int j = 0; j < moveLines.size(); j++) newPage.add(String.valueOf(moveLines.get(j)));
        pages.add(newPage);
    }

    private static int findExistingEvolveLorePageIndex(List<List<String>> pages) {
        if (pages == null) return -1;
        for (int p = 0; p < pages.size(); p++) {
            List<String> page = pages.get(p);
            if (page == null) continue;
            for (int i = 0; i < page.size(); i++) {
                if (isEvolveInfoLine(String.valueOf(page.get(i)))) return p;
            }
        }
        return -1;
    }

    private static boolean shouldMoveToEvolvePage(String line, int index) {
        if (line == null) return false;
        if (index == 0) return false;
        return isMarkerBracketLine(line)
                || isEvolvedTierLine(line)
                || isStarsBracketLine(line)
                || isEvolutionBoostLine(line);
    }

    private static boolean isEvolveInfoLine(String line) {
        if (line == null) return false;
        String clean = stripLoreVisualFormatting(line).toLowerCase(Locale.ROOT);
        if (clean.indexOf("debug evolve tier") >= 0) return true;
        if (clean.indexOf("evolution boost") >= 0) return true;
        if (clean.indexOf("evolved") >= 0 && clean.indexOf("tier") >= 0) return true;
        if ("[evolved]".equals(clean)) return true;
        return isStarsBracketLine(line);
    }

    private static boolean isMarkerBracketLine(String rawLine) {
        String t = stripLoreVisualFormatting(rawLine);
        return t.matches("^\\[[^\\]]+\\]$");
    }

    private static boolean isStarsBracketLine(String rawLine) {
        String t = stripLoreVisualFormatting(rawLine);
        if (!t.matches("^\\[[^\\]]+\\]$")) return false;
        return t.indexOf('✦') >= 0 || t.indexOf('★') >= 0 || t.indexOf('☆') >= 0 || t.indexOf('✧') >= 0 || t.indexOf('✩') >= 0;
    }

    private static boolean isEvolvedTierLine(String rawLine) {
        String t = stripMCColors(rawLine).toLowerCase(Locale.ROOT);
        return t.indexOf("evolved") >= 0 && t.indexOf("tier") >= 0;
    }

    private static boolean isEvolutionBoostLine(String rawLine) {
        String t = stripMCColors(rawLine).toLowerCase(Locale.ROOT);
        return t.indexOf("evolution boost") >= 0;
    }

    private static String stripLoreVisualFormatting(String rawLine) {
        String t = rawLine == null ? "" : rawLine;
        t = stripMCColors(t);
        t = t.replaceAll("<[^>]*>", "");
        t = t.replace('Ｅ', 'e').replace('ｅ', 'e').replace('＋', '+').replace('－', '-');
        return t.trim();
    }

    private static String stripMCColors(String s) {
        if (s == null) return "";
        return s.replaceAll("(?:\\u00A7|§)[0-9A-FK-ORa-fk-or]", "");
    }

    private static int getEvolveBoostPercent(int tier) {
        Integer pct = EVOLVE_TIER_PCT.get(Integer.valueOf(tier));
        return pct == null ? 40 : pct.intValue();
    }

    private static String getEvolveStarLine(String rarityKey, int tier) {
        if (tier <= 0) return null;
        String stars = "";
        for (int i = 0; i < tier; i++) stars += "✦";

        rarityKey = normalizeRarityKey(rarityKey);
        if ("uncommon".equals(rarityKey))
            return "<grad #006600 #00CC00 #33FF33 #00CC00 #006600 scroll=0.30 waveamp=1.4 wavespeed=5>[" + stars + "]</grad>";
        if ("rare".equals(rarityKey))
            return "<grad #A64DFF #D9B3FF #A64DFF scroll=0.22 waveamp=1.4 wavespeed=5>[" + stars + "]</grad>";
        if ("etech".equals(rarityKey))
            return "<grad #FF2BA6 #FF66CF #FF2BA6 scroll=0.22 waveamp=1.4 wavespeed=5>[" + stars + "]</grad>";
        if ("epic".equals(rarityKey))
            return "<grad #3A7BFF #1ED1FF #3A7BFF scroll=0.25 waveamp=1.4 wavespeed=5>[" + stars + "]</grad>";
        if ("legendary".equals(rarityKey))
            return "<grad #FFB300 #FFF176 #FFB300 scroll=0.18 waveamp=1.4 wavespeed=5>[" + stars + "]</grad>";
        if ("pearlescent".equals(rarityKey))
            return "<grad #00CED1 #48D1CC #00BFC6 #00CED1 scroll=0.22 waveamp=1.4 wavespeed=5>[" + stars + "]</grad>";
        if ("seraph".equals(rarityKey))
            return "<grad #FF66C4 #FF7FD2 #FF98DD #FFB3E6 #FF66C4 scroll=0.60 waveamp=1.4 wavespeed=5>[" + stars + "]</grad>";
        if ("glitch".equals(rarityKey))
            return "<grad #FFC1E3 #FFD1EA #FFE2F2 #FFD1EA #FFC1E3 scroll=0.50 waveamp=1.4 wavespeed=5>[" + stars + "]</grad>";
        if ("effervescent".equals(rarityKey) || "effervescent_".equals(rarityKey))
            return "<rainbow cycles=2 speed=0.25 waveamp=2 wavespeed=6 pulse=1 pulsespeed=1.2 amp=0.35>[" + stars + "]</rainbow>";
        if ("black".equals(rarityKey))
            return "<grad #000000 #0a0a0a #141414 #0a0a0a #000000 scroll=0.26 waveamp=1.4 wavespeed=5>[" + stars + "]</grad>";
        if ("pseudo_black".equals(rarityKey))
            return "<glow #000000 fill=nested amp=0.18 speed=0.52><grad #FFFFFF #E8E8E8 #D0D0D0 #E8E8E8 #FFFFFF scroll=0.16 styles=l>[" + stars + "]</grad></glow>";
        return "<grad #505050 #B0B0B0 #E0E0E0 #B0B0B0 #505050 scroll=0.22 waveamp=1.4 wavespeed=5>[" + stars + "]</grad>";
    }

    private static String getEvolvedHeader(String rarityKey) {
        rarityKey = normalizeRarityKey(rarityKey);
        String out = EVOLVED_MAP.get(rarityKey);
        if (out == null || out.length() == 0) out = EVOLVED_MAP.get("common");
        return out;
    }

    private static int parseTier(String s) {
        if (s == null) return 1;
        s = s.toLowerCase(Locale.ROOT).trim();
        if ("t1".equals(s)) return 1;
        if ("t2".equals(s)) return 2;
        if ("t3".equals(s)) return 3;
        try { return Integer.parseInt(s); } catch (Throwable ignored) { return -1; }
    }

    private static String detectAppliedRarityKey(ItemStack stack) {
        if (stack == null) return "common";

        NBTTagCompound tag = ensureTag(stack);
        if (tag.hasKey(RarityTags.PSEUDO_BLACK, Constants.NBT.TAG_BYTE) && tag.getBoolean(RarityTags.PSEUDO_BLACK)) {
            return "pseudo_black";
        }
        if (tag.hasKey(RarityTags.KEY, Constants.NBT.TAG_STRING)) {
            String direct = normalizeRarityKey(tag.getString(RarityTags.KEY));
            if (direct.length() > 0) return direct;
        }

        ArrayList<String> lore = getLoreLines(stack);
        for (int i = 0; i < lore.size(); i++) {
            String clean = stripTags(lore.get(i)).toLowerCase(Locale.ROOT).trim();
            if (clean.indexOf("pseudo black") >= 0) return "pseudo_black";
        }

        try {
            String detected = normalizeRarityKey(RarityDetect.fromStack(stack));
            if (detected.length() > 0) return detected;
        } catch (Throwable ignored) {
        }

        String visible = detectVisibleBaseRarity(stack);
        return visible.length() == 0 ? "common" : visible;
    }

    private static ArrayList<String> getLoreLines(ItemStack stack) {
        ArrayList<String> out = new ArrayList<String>();
        if (stack == null) return out;
        NBTTagCompound tag = ensureTag(stack);
        NBTTagCompound display = ensureDisplay(tag);
        NBTTagList lore = ensureLore(display);
        for (int i = 0; i < lore.tagCount(); ++i) out.add(lore.getStringTagAt(i));
        return out;
    }

    private static void setLoreLines(ItemStack stack, List<String> lines) {
        NBTTagCompound tag = ensureTag(stack);
        NBTTagCompound display = ensureDisplay(tag);
        NBTTagList lore = new NBTTagList();
        if (lines != null) {
            for (int i = 0; i < lines.size(); i++) lore.appendTag(new NBTTagString(String.valueOf(lines.get(i))));
        }
        display.setTag("Lore", lore);
        stack.setTagCompound(tag);
    }

    private static void removeExactCleanLine(List<String> lines, String cleanTarget) {
        if (lines == null || cleanTarget == null) return;
        for (int i = lines.size() - 1; i >= 0; i--) {
            String clean = stripTags(String.valueOf(lines.get(i))).toLowerCase(Locale.ROOT).trim();
            if (cleanTarget.equals(clean)) lines.remove(i);
        }
    }

    private static void writeRarityTag(ItemStack stack, String key) {
        key = normalizeRarityKey(key);
        if (key.length() == 0) return;
        NBTTagCompound tag = ensureTag(stack);
        tag.setString(RarityTags.KEY, key);
        if (!"pseudo_black".equals(key)) {
            tag.removeTag(RarityTags.PSEUDO_BLACK);
            tag.removeTag(RarityTags.PSEUDO_BLACK_BASE);
        }
        stack.setTagCompound(tag);
    }

    private static void clearRarityTags(ItemStack stack) {
        NBTTagCompound tag = ensureTag(stack);
        tag.removeTag(RarityTags.KEY);
        stack.setTagCompound(tag);
    }

    private static void clearEvolveFlags(ItemStack stack) {
        NBTTagCompound tag = ensureTag(stack);
        tag.removeTag("isEvolved");
        tag.removeTag("evolveTier");
        stack.setTagCompound(tag);
    }

    private static boolean replaceOrAddWearableLine(ItemStack stack, String keyLower, String newLine) {
        NBTTagCompound tag = ensureTag(stack);
        NBTTagCompound display = ensureDisplay(tag);
        NBTTagList lore = ensureLore(display);

        int idx = findWearableLineIndex(lore);
        if (idx >= 0) {
            String cur = stripTags(lore.getStringTagAt(idx)).toLowerCase(Locale.ROOT);
            if (cur.contains(keyLower)) {
                stack.setTagCompound(tag);
                return false;
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
                    if (i == rIdx) out.appendTag(new NBTTagString(newLine));
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
        if ("amulet".equals(wordLower)) {
            return "<grad #FF3BD5 #FF66FF #B45CFF #FF3BD5 scroll=0.18 styles=l>Amulet</grad>";
        }
        if ("artifact".equals(wordLower)) {
            return "<grad #FFB300 #FFE066 #FFB300 scroll=0.18 styles=l>Artifact</grad>";
        }
        return "<grad #35E8FF #7FF7FF #35E8FF scroll=0.18 styles=l>Ring</grad>";
    }

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

    private static int findRarityLineIndex(List<String> lore) {
        if (lore == null) return -1;
        for (int i = 0; i < lore.size(); ++i) {
            String s = stripTags(String.valueOf(lore.get(i))).toLowerCase(Locale.ROOT);
            for (String k : COLOR_MAP.keySet()) if (s.contains(k)) return i;
            if (s.contains("legend")) return i;
            if (s.contains("pseudo black")) return i;
        }
        return -1;
    }

    private static int findRarityLineIndex(NBTTagList lore) {
        if (lore == null) return -1;
        for (int i = 0; i < lore.tagCount(); ++i) {
            String s = stripTags(lore.getStringTagAt(i)).toLowerCase(Locale.ROOT);
            for (String k : COLOR_MAP.keySet()) if (s.contains(k)) return i;
            if (s.contains("legend")) return i;
            if (s.contains("pseudo black")) return i;
        }
        return -1;
    }

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

    private static void applyPseudoBlackOverlay(ItemStack stack, String baseKey) {
        baseKey = normalizeRarityKey(baseKey);
        if (baseKey.length() == 0 || "pseudo_black".equals(baseKey)) baseKey = detectVisibleBaseRarity(stack);
        if (baseKey.length() == 0 || "pseudo_black".equals(baseKey)) baseKey = "common";

        replaceOrAddRarityLine(stack, getPseudoBlackHeader(baseKey));
        removePseudoBlackMainLoreMarkers(stack);
        writePseudoBlackTags(stack, baseKey);
        upsertPseudoBlackLorePage(stack, baseKey);
    }

    private static void removePseudoBlackMainLoreMarkers(ItemStack stack) {
        NBTTagCompound tag = ensureTag(stack);
        NBTTagCompound display = ensureDisplay(tag);
        NBTTagList lore = ensureLore(display);
        NBTTagList out = new NBTTagList();
        for (int i = 0; i < lore.tagCount(); ++i) {
            String raw = lore.getStringTagAt(i);
            String s = stripTags(raw).toLowerCase(Locale.ROOT).trim();
            if (s.contains("pseudo black overlay")) continue;
            if (s.contains("[pseudoblackbase:")) continue;
            if ("[pseudo_black]".equals(s) || "[pseudoblack]".equals(s) || "pseudo_black".equals(s) || "pseudoblack".equals(s)) continue;
            out.appendTag(new NBTTagString(raw));
        }
        display.setTag("Lore", out);
        stack.setTagCompound(tag);
    }

    private static String resolvePseudoBlackBase(ItemStack stack, String requested) {
        String key = normalizeRarityKey(requested);
        if (key.length() > 0 && !"pseudo_black".equals(key)) return key;

        NBTTagCompound tag = ensureTag(stack);
        if (tag.hasKey(RarityTags.PSEUDO_BLACK_BASE, Constants.NBT.TAG_STRING)) {
            String stored = normalizeRarityKey(tag.getString(RarityTags.PSEUDO_BLACK_BASE));
            if (stored.length() > 0 && !"pseudo_black".equals(stored)) return stored;
        }
        if (tag.hasKey(RarityTags.KEY, Constants.NBT.TAG_STRING)) {
            String storedRarity = normalizeRarityKey(tag.getString(RarityTags.KEY));
            if (storedRarity.length() > 0 && !"pseudo_black".equals(storedRarity)) return storedRarity;
        }
        return detectVisibleBaseRarity(stack);
    }

    private static String detectVisibleBaseRarity(ItemStack stack) {
        if (stack == null) return "";
        NBTTagCompound tag = ensureTag(stack);
        NBTTagCompound display = ensureDisplay(tag);
        NBTTagList lore = ensureLore(display);

        for (int i = 0; i < lore.tagCount(); ++i) {
            String raw = lore.getStringTagAt(i);
            String clean = stripTags(raw).toLowerCase(Locale.ROOT).trim();

            if (clean.contains("pseudo black")) return "pseudo_black";
            if (clean.contains("effervescent")) {
                if (raw.indexOf("§k") >= 0 || raw.indexOf('☆') >= 0 || raw.indexOf('★') >= 0 || clean.contains("effervescent_")) return "effervescent_";
                return "effervescent";
            }
            if (clean.contains("e-tech") || clean.contains("etech")) return "etech";
            if (clean.contains("pearlescent")) return "pearlescent";
            if (clean.contains("legendary")) return "legendary";
            if (clean.contains("uncommon")) return "uncommon";
            if (clean.contains("seraph")) return "seraph";
            if (clean.contains("glitch")) return "glitch";
            if (clean.contains("epic")) return "epic";
            if (clean.contains("rare")) return "rare";
            if (clean.contains("common")) return "common";
            if ("black".equals(clean) || clean.contains(" black")) return "black";
        }
        return "";
    }

    private static String normalizeRarityKey(String s) {
        if (s == null) return "";
        s = stripTags(s).toLowerCase(Locale.ROOT).trim();
        s = s.replace(' ', '_').replace('-', '_');
        if ("e_tech".equals(s)) return "etech";
        if ("pseudoblack".equals(s) || "pseudo_black".equals(s) || "pseudo".equals(s)) return "pseudo_black";
        if ("effervescent+".equals(s)) return "effervescent_";
        if (COLOR_MAP.containsKey(s) || "black".equals(s) || "pseudo_black".equals(s)) return s;
        return "";
    }

    private static String baseDisplayLabel(String key) {
        key = normalizeRarityKey(key);
        if ("etech".equals(key)) return "E-Tech";
        if ("effervescent_".equals(key)) return "Effervescent+";
        if ("effervescent".equals(key)) return "Effervescent";
        if ("pseudo_black".equals(key)) return "Pseudo Black";
        if (key == null || key.length() == 0) return "Common";
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    private static String getPseudoBlackHeader(String baseKey) {
        return "§r§l<grad #000000 #060606 #111111 #060606 #000000 cycles=2 scroll=0.14 pulse=1 pulsespeed=1.08 amp=0.24 styles=l>"
                + baseDisplayLabel(baseKey)
                + "</grad>§r";
    }

    private static void writePseudoBlackTags(ItemStack stack, String baseKey) {
        NBTTagCompound tag = ensureTag(stack);
        tag.setString(RarityTags.KEY, "pseudo_black");
        tag.setBoolean(RarityTags.PSEUDO_BLACK, true);
        tag.setString(RarityTags.PSEUDO_BLACK_BASE, baseKey);
        stack.setTagCompound(tag);
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static void upsertPseudoBlackLorePage(ItemStack stack, String baseKey) {
        try {
            Class<?> api = findLorePagesApi();
            if (api == null) return;

            Method read = api.getMethod("readPagesFromNBT", ItemStack.class);
            Method write = api.getMethod("writePagesToNBT", ItemStack.class, List.class);

            Object existing = read.invoke(null, stack);
            ArrayList<List<String>> pages = new ArrayList<List<String>>();

            if (existing instanceof Iterable) {
                for (Object pageObj : (Iterable) existing) {
                    ArrayList<String> lines = new ArrayList<String>();
                    boolean isPseudoPage = false;
                    if (pageObj instanceof Iterable) {
                        for (Object lineObj : (Iterable) pageObj) {
                            String raw = String.valueOf(lineObj == null ? "" : lineObj);
                            String clean = stripTags(raw).toLowerCase(Locale.ROOT).trim();
                            if (clean.contains("pseudo black overlay") || clean.startsWith("[pseudoblackbase:")) {
                                isPseudoPage = true;
                                continue;
                            }
                            lines.add(raw);
                        }
                    }
                    if (!isPseudoPage || !lines.isEmpty()) pages.add(lines);
                }
            }

            ArrayList<String> pseudoPage = new ArrayList<String>();
            pseudoPage.add("§7§o──────── Pseudo Black Overlay ────────");
            pseudoPage.add(getPseudoBlackHeader(baseKey));
            pseudoPage.add("§8[PseudoBlackBase: " + baseKey + "]");
            pages.add(pseudoPage);

            write.invoke(null, stack, pages);
        } catch (Throwable ignored) {
        }
    }

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
            p.addChatMessage(txt("§7Pseudo: §f/rarity pblack §8[base]"));
            p.addChatMessage(txt("§7Evolve: §f/rarity evotest §81|2|3 §7(uses evolved color formats + LorePages)"));
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

    private static String stripTags(String s) {
        if (s == null) return "";
        s = s.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
        s = s.replaceAll("<[^>]+>", "");
        return s;
    }

    private static String cap(String s) {
        return (s == null || s.length() == 0) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
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

        Map<String,String> e = new LinkedHashMap<String,String>();
        e.put("common", "§r§l<grad #FFFFFF #DADADA scroll=0.18 pulse=1 pulsespeed=1.6 amp=0.20>Common ✦ Evolved</grad>§r");
        e.put("uncommon", "§r§l<grad #006600 #00CC00 #33FF33 #00CC00 #006600 scroll=0.30>Uncommon ✦ Evolved</grad>§r");
        e.put("rare", "§r<pulse #A64DFF styles=l speed=1.8 amp=0.22>Rare ✦ Evolved</pulse>§r");
        e.put("etech", "§r§l<grad #FF2BA6 #FF66CF #FF2BA6 scroll=0.22>Ｅ-Tech ✦ Evolved</grad>§r");
        e.put("epic", "§r§l<grad #3A7BFF #1ED1FF #3A7BFF scroll=0.25>Epic ✦ Evolved</grad>§r");
        e.put("legendary", "§r§l<grad #FFB300 #FFF176 #FFB300 scroll=0.18>Legendary ✦ Evolved</grad>§r");
        e.put("pearlescent", "§r§l<grad #00CED1 #48D1CC #00BFC6 #00CED1 scroll=0.22>Pearlescent ✦ Evolved</grad>§r");
        e.put("seraph", "§r§l<grad #FF66C4 #FF7FD2 #FF98DD #FFB3E6 #FF66C4 cycles=2 pulse=1 pulsespeed=1.8 amp=0.28 scroll=0.60 styles=l>Seraph ✦ Evolved</grad>§r");
        e.put("glitch", "§r§l<grad #FFC1E3 #FFD1EA #FFE2F2 #FFD1EA #FFC1E3 cycles=2 scroll=0.50 pulse=1 pulsespeed=1.80 amp=0.30 styles=l>Glitch ✦ Evolved</grad>§r");
        e.put("effervescent", "§r§l<rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35 styles=l>Effervescent ✦ Evolved</rainbow>§r");
        e.put("effervescent_", "<rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35>§k☆</rainbow>§r <rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35>☆</rainbow> <rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35 styles=l>Effervescent+ ✦ Evolved</rainbow> <rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35>☆</rainbow> <rainbow cycles=2 speed=0.25 pulse=1 pulsespeed=1.2 amp=0.35>§k☆</rainbow>§r");
        e.put("pseudo_black", "§r§l<glow #000000 fill=nested amp=0.18 speed=0.52><grad #FFFFFF #E8E8E8 #D0D0D0 #E8E8E8 #FFFFFF scroll=0.16 styles=l>Pseudo Black ✦ Evolved</grad></glow>§r");
        e.put("black", "§r§l<grad #000000 #0a0a0a #141414 #0a0a0a #000000 cycles=2 scroll=0.26 pulse=1 pulsespeed=1.7 amp=0.35 styles=l>BLACK ✦ EVOLVED</grad>§r");
        EVOLVED_MAP = Collections.unmodifiableMap(e);

        Map<Integer, Integer> pct = new LinkedHashMap<Integer, Integer>();
        pct.put(Integer.valueOf(1), Integer.valueOf(40));
        pct.put(Integer.valueOf(2), Integer.valueOf(57));
        pct.put(Integer.valueOf(3), Integer.valueOf(87));
        EVOLVE_TIER_PCT = Collections.unmodifiableMap(pct);
    }
}
