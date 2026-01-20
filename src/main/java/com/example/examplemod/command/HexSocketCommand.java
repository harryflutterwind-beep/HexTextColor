package com.example.examplemod.command;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ChatComponentText;
import com.example.examplemod.api.HexSocketAPI;
import com.example.examplemod.ExampleMod;
import com.example.examplemod.gui.HexSocketStationGuiHandler;

public class HexSocketCommand extends CommandBase {

    // ─────────────────────────────────────────────────────────────
    // NBT
    // ─────────────────────────────────────────────────────────────
    private static final String ROOT   = "HexGems";
    private static final String K_OPEN = "SocketsOpen";
    private static final String K_MAX  = "SocketsMax"; // -1 = unlimited
    private static final String K_GEMS = "Gems";       // NBTTagList of strings (icon keys)

    // ─────────────────────────────────────────────────────────────
    // Lore / icons
    // ─────────────────────────────────────────────────────────────
    private static final String SOCKET_PREFIX = "Sockets:";
    // Swap to socket_empty_32 / socket_empty_16 if you want smaller rings
    private static final String ICON_EMPTY    = "<ico:gems/socket_empty_64>";

    // Colorful prefix (works great with your Hex renderer)
    private static final String PFX =
            "§8[<grad #ff4fd8 #36d1ff #ffe66d #7cff6b>HexSocket</grad>§8]§r ";

    // How many icons per lore line (prevents one ultra-wide tooltip line)
    // Set to 1 if you want "each socket on a new line".
    private static final int ICONS_PER_LINE = 6;

    // ─────────────────────────────────────────────────────────────
    // Lore Pages (HexLorePages) — dedicated sockets page per item
    // Stored by LorePagesCommand at: stack.tag.HexLorePages.Pages (NBTTagList<String>)
    // We keep an index pointer in HexGems to update the same page.
    // ─────────────────────────────────────────────────────────────
    private static final String LP_ROOT = "HexLorePages";
    private static final String LP_LIST = "Pages";
    private static final String K_PAGE_IDX = "SocketsPageIndex"; // int (0-based) stored under HexGems


    // Bulk-test list (generated from your gems.zip)
    private static final String[] TEST_ALL_KEYS = new String[] {
            "orb_gem_blue_frost_64.png",
            "orb_gem_blue_frost_64_anim_8f.png",
            "orb_gem_chaoticSphere_64.png",
            "orb_gem_chaoticSphere_anim_8f_64x516.png",
            "orb_gem_fractured_64.png",
            "orb_gem_fractured_anim_8f_64x516.png",
            "orb_gem_gold_solar_64.png",
            "orb_gem_gold_solar_64_anim_8f.png",
            "orb_gem_green_nature_64.png",
            "orb_gem_green_nature_64_anim_8f.png",
            "orb_gem_light_64.png",
            "orb_gem_light_anim_8f_64x516.png",
            "orb_gem_negative_64.png",
            "orb_gem_negative_anim_8f_64x516.png",
            "orb_gem_orange_inferno_64.png",
            "orb_gem_orange_inferno_64_anim_8f.png",
            "orb_gem_rainbow_energized_64.png",
            "orb_gem_rainbow_energized_anim_8f_64x516.png",
            "orb_gem_swirly_64.png",
            "orb_gem_swirly_loop.png",
            "orb_gem_teal_aether_64.png",
            "orb_gem_teal_aether_64_anim_8f.png",
            "orb_gem_violet_void_64.png",
            "orb_gem_violet_void_64_anim_8f.png",
            "pill_dark_fire_face_64.png",
            "pill_dark_fire_face_64_anim.png",
            "pill_fire_animated_64_anim.png",
            "pill_fire_textured_64.png",
            "socket_empty_16.png",
            "socket_empty_32.png",
            "socket_empty_64.png",
            "socket_rim_overlay_64.png"
    };

    // Friendly alias -> icon key (NO "<ico: >", just the key)
    private static final Map<String, String> GEM_ALIAS = new HashMap<String, String>();
    static {
        // Static examples (adjust/add as needed)
        GEM_ALIAS.put("swirly",         "gems/orb_gem_swirly_64");
        GEM_ALIAS.put("blue_frost",     "gems/orb_gem_blue_frost_64");
        GEM_ALIAS.put("gold_solar",     "gems/orb_gem_gold_solar_64");
        GEM_ALIAS.put("green_nature",   "gems/orb_gem_green_nature_64");
        GEM_ALIAS.put("orange_inferno", "gems/orb_gem_orange_inferno_64");
        GEM_ALIAS.put("teal_aether",    "gems/orb_gem_teal_aether_64");
        GEM_ALIAS.put("violet_void",    "gems/orb_gem_violet_void_64");
        GEM_ALIAS.put("negative",       "gems/orb_gem_negative_64");
        GEM_ALIAS.put("light",          "gems/orb_gem_light_64");

        // Animated examples
        GEM_ALIAS.put("dark_fire_anim", "gems/pill_dark_fire_face_64_anim@12");
        GEM_ALIAS.put("blue_frost_anim","gems/orb_gem_blue_frost_64_anim_8f@8");
    }

    @Override public String getCommandName() { return "hexsocket"; }

    @Override public String getCommandUsage(ICommandSender s) {
        // Public subcommands:
        //  - gui    : Socket Station GUI
        //  - opener : Socket Opener GUI
        // Legacy aliases (station/anvil/openstation/socket) are intentionally not advertised.
        return "/hexsocket <open|unsocket|setbonus|list|info|setmax|clear|testall|gui|opener> ...";
    }

    // Keep OP-only for now while you're testing
    @Override public int getRequiredPermissionLevel() { return 2; }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.addChatMessage(txt(PFX + "§cPlayer only."));
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) sender;

        if (args.length < 1) {
            p.addChatMessage(txt(PFX + "§7" + getCommandUsage(sender)));
            return;
        }

        ItemStack held = p.getHeldItem();
        if (held == null) {
            p.addChatMessage(txt(PFX + "§cHold an item."));
            return;
        }

        String sub = args[0].trim().toLowerCase();

        if ("open".equals(sub)) {
            int add = 1;
            if (args.length >= 2) {
                try { add = Math.max(1, Integer.parseInt(args[1])); }
                catch (NumberFormatException nfe) {
                    p.addChatMessage(txt(PFX + "§cBad number: §f" + args[1]));
                    return;
                }
            }

            int beforeOpen = HexSocketAPI.getSocketsOpen(held);
            int max = HexSocketAPI.getSocketsMax(held);

            // OP testing: if opening beyond cap, mark unlimited so /info isn't confusing
            int targetOpen = beforeOpen + add;
            if (max >= 0 && targetOpen > max) HexSocketAPI.setSocketsMax(held, -1);

            HexSocketAPI.setSocketsOpen(held, targetOpen);
            rebuildSocketLoreLines(held);

            p.inventoryContainer.detectAndSendChanges();

            int nowOpen = HexSocketAPI.getSocketsOpen(held);
            int nowMax  = HexSocketAPI.getSocketsMax(held);
            String maxStr = (nowMax < 0 ? "∞" : String.valueOf(nowMax));

            p.addChatMessage(txt(PFX + "§aOpened §f" + add + "§a socket(s). §7Now: §f" + nowOpen
                    + " §8| §7Max: §f" + maxStr));
            return;
        }
        if ("gui".equals(sub) || "station".equals(sub) || "anvil".equals(sub)) {
            p.openGui(ExampleMod.INSTANCE, HexSocketStationGuiHandler.GUI_ID_SOCKET_STATION, p.worldObj, 0, 0, 0);
            return;
        }

        // Socket opener (enchant-style cost UI)
        if ("opener".equals(sub) || "openstation".equals(sub) || "open_gui".equals(sub)) {
            p.openGui(ExampleMod.INSTANCE, HexSocketStationGuiHandler.GUI_ID_SOCKET_OPENER, p.worldObj, 0, 0, 0);
            return;
        }

        if ("socket".equals(sub)) {
            // Socketing is handled via the Socket Station GUI now.
            // Keep this as a soft-redirect so older muscle-memory does not break.
            p.addChatMessage(txt(PFX + "§eUse §f/hexsocket gui§e to socket gems (Station GUI)."));
            return;
        }

        if ("unsocket".equals(sub)) {
            if (args.length < 2) throw new WrongUsageException("/hexsocket unsocket <index>");

            int idx;
            try { idx = Integer.parseInt(args[1]); }
            catch (NumberFormatException nfe) {
                p.addChatMessage(txt(PFX + "§cInvalid index: §f" + args[1]));
                return;
            }

            int count = HexSocketAPI.getSocketsFilled(held);
            if (idx < 0 || idx >= count) {
                p.addChatMessage(txt(PFX + "§eIndex out of range. §7Filled: §f" + count));
                return;
            }

            String removed = HexSocketAPI.getGemKeyAt(held, idx);
            boolean ok = HexSocketAPI.unsocketGem(held, idx);
            if (!ok) {
                p.addChatMessage(txt(PFX + "§cFailed to remove gem at slot §f" + idx + "§c."));
                return;
            }

            rebuildSocketLoreLines(held);

            p.inventoryContainer.detectAndSendChanges();
            p.addChatMessage(txt(PFX + "§aRemoved §b" + removed + " §afrom slot §f" + idx + "§a."));
            return;
        }

        if ("list".equals(sub)) {
            int open = HexSocketAPI.getSocketsOpen(held);
            int filled = HexSocketAPI.getSocketsFilled(held);

            p.addChatMessage(txt(PFX + "§bSockets §8» §7Open §f" + open + " §8| §7Filled §f" + filled));
            for (int i = 0; i < filled; i++) {
                String k = HexSocketAPI.getGemKeyAt(held, i);
                HexSocketAPI.GemBonus b = HexSocketAPI.getBonusAt(held, i);
                if (b != null && b.attrKey != null && b.attrKey.length() > 0) {
                    String name = (b.displayName != null && b.displayName.length() > 0) ? b.displayName : b.attrKey;
                    p.addChatMessage(txt(PFX + " §8- §f" + i + "§7: §b" + k + " §8| §e" + name + " §6" + b.amount));
                } else {
                    p.addChatMessage(txt(PFX + " §8- §f" + i + "§7: §b" + k));
                }
            }
            return;
        }

        if ("info".equals(sub)) {
            int open = HexSocketAPI.getSocketsOpen(held);
            int max = HexSocketAPI.getSocketsMax(held);
            int filled = HexSocketAPI.getSocketsFilled(held);

            String maxStr = (max < 0 ? "∞" : String.valueOf(max));
            p.addChatMessage(txt(PFX + "§bSocket Info §8» §7Open §f" + open + " §8| §7Filled §f" + filled + " §8| §7Max §f" + maxStr));
            p.addChatMessage(txt(PFX + "§7This mod does not apply attributes. It only stores per-socket NBT for your server scripts."));
            return;
        }

        if ("setmax".equals(sub)) {
            if (args.length < 2) throw new WrongUsageException("/hexsocket setmax <count|-1>");

            int m;
            try { m = Integer.parseInt(args[1]); }
            catch (NumberFormatException nfe) {
                p.addChatMessage(txt(PFX + "§cBad number: §f" + args[1]));
                return;
            }

            HexSocketAPI.setSocketsMax(held, m);
            rebuildSocketLoreLines(held);

            p.inventoryContainer.detectAndSendChanges();

            int maxNow = HexSocketAPI.getSocketsMax(held);
            String maxStr = (maxNow < 0 ? "∞" : String.valueOf(maxNow));
            p.addChatMessage(txt(PFX + "§aSet max sockets to §f" + maxStr + "§a."));
            return;
        }

        if ("clear".equals(sub)) {
            HexSocketAPI.clearSockets(held);
            rebuildSocketLoreLines(held);

            p.inventoryContainer.detectAndSendChanges();
            p.addChatMessage(txt(PFX + "§aCleared sockets + gems + bonuses."));
            return;
        }

        // Bulk test: fills the item with every gem texture key in TEST_ALL_KEYS
        // Usage: /hexsocket testall [limit]
        // Fills the held item with every gem texture key in TEST_ALL_KEYS
        // Usage: /hexsocket testall [limit]
        if ("testall".equals(sub)) {
            int limit = TEST_ALL_KEYS.length;
            if (args.length >= 2) {
                try { limit = Math.max(1, Math.min(TEST_ALL_KEYS.length, Integer.parseInt(args[1]))); }
                catch (NumberFormatException nfe) {
                    p.addChatMessage(txt(PFX + "§cBad number: §f" + args[1]));
                    return;
                }
            }

            // reset
            HexSocketAPI.clearSockets(held);
            HexSocketAPI.setSocketsMax(held, -1);
            HexSocketAPI.setSocketsOpen(held, limit);

            for (int i = 0; i < limit; i++) {
                String k = normalizeGemKey(TEST_ALL_KEYS[i]);
                if (k != null && k.length() > 0) HexSocketAPI.socketGem(held, k);
            }

            rebuildSocketLoreLines(held);

            p.inventoryContainer.detectAndSendChanges();
            p.addChatMessage(txt(PFX + "§aTest-filled §f" + limit + "§a sockets with gem icons."));
            return;
        }

        p.addChatMessage(txt(PFX + "§7" + getCommandUsage(sender)));
    }

    // ─────────────────────────────────────────────────────────────
    // Tab Completion (1.7.10)
    // ─────────────────────────────────────────────────────────────
    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        List<String> out = new ArrayList<String>();

        if (args == null) return out;

        if (args.length == 1) {
            String p = args[0] == null ? "" : args[0].toLowerCase();
            String[] subs = new String[] {
                    "open", "unsocket", "setbonus", "list", "info", "setmax", "clear", "testall",
                    "gui", "opener"
            };
            for (int i = 0; i < subs.length; i++) {
                if (subs[i].startsWith(p)) out.add(subs[i]);
            }
            return out;
        }

        // /hexsocket setbonus <index> <TAB>
        if (args.length == 3 && "setbonus".equalsIgnoreCase(args[0])) {
            String p = args[2] == null ? "" : args[2].toLowerCase();
            String[] attrs = new String[] {
                    "dbc.Strength", "dbc.Strength.Multi",
                    "dbc.Dexterity", "dbc.Dexterity.Multi",
                    "dbc.Constitution", "dbc.Constitution.Multi",
                    "dbc.WillPower", "dbc.WillPower.Multi",
                    "dbc.Spirit", "dbc.Spirit.Multi",
                    "health", "main_attack", "movement_speed"
            };
            for (int i = 0; i < attrs.length; i++) {
                String a = attrs[i];
                if (a.toLowerCase().startsWith(p)) out.add(a);
            }
            return out;
        }

        // /hexsocket unsocket <TAB>  (suggest 0..filled-1)
        if (args.length == 2 && "unsocket".equalsIgnoreCase(args[0]) && (sender instanceof EntityPlayerMP)) {
            EntityPlayerMP p = (EntityPlayerMP) sender;
            ItemStack held = p.getHeldItem();
            if (held != null) {
                int filled = HexSocketAPI.getSocketsFilled(held);
                String pref = args[1] == null ? "" : args[1];
                for (int i = 0; i < filled; i++) {
                    String s = String.valueOf(i);
                    if (s.startsWith(pref)) out.add(s);
                }
            }
            return out;
        }

        return out;
    }

    // ─────────────────────────────────────────────────────────────
    // Key normalization
    // - accepts: alias, raw gems/path, items/path, and pasted "<ico:...>"
    // - forces everything into "gems/..." (your desired folder)
    // - keeps "@8" / "@12" animation suffix if provided
    // ─────────────────────────────────────────────────────────────
    private static String normalizeGemKey(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.length() == 0) return "";

        // Allow paste: <ico:gems/xxx@12>
        if (s.startsWith("<ico:")) s = s.substring(5);
        if (s.endsWith(">")) s = s.substring(0, s.length() - 1);

        // strip ".png" if they include it
        if (s.toLowerCase().endsWith(".png")) s = s.substring(0, s.length() - 4);

        String low = s.toLowerCase();

        // Alias
        if (GEM_ALIAS.containsKey(low)) return forceGemsFolder(GEM_ALIAS.get(low));

        // Already a foldered key
        if (low.startsWith("gems/")) return forceGemsFolder(s);

        // If they typed "items/..." (or "item/...") we still force it to gems/
        if (low.startsWith("items/") || low.startsWith("item/")) return forceGemsFolder(s);

        // If they pasted "textures/gems/..." by accident, strip "textures/"
        if (low.startsWith("textures/")) {
            s = s.substring("textures/".length());
            return forceGemsFolder(s);
        }

        // No folder? assume it's under gems/
        if (low.indexOf('/') < 0) return "gems/" + s;

        // Otherwise accept path, but force it into gems/ per your rule
        return forceGemsFolder(s);
    }

    // Forces icon keys into gems/... while preserving any "@12" anim suffix.
    private static String forceGemsFolder(String key) {
        if (key == null) return "";
        String k = key.trim();
        if (k.length() == 0) return "";

        // preserve "@12" suffix for animations
        int at = k.indexOf('@');
        String base = (at >= 0) ? k.substring(0, at) : k;
        String suf  = (at >= 0) ? k.substring(at) : "";

        String low = base.toLowerCase();

        if (low.startsWith("gems/")) return base + suf;

        if (low.startsWith("items/")) base = "gems/" + base.substring("items/".length());
        else if (low.startsWith("item/")) base = "gems/" + base.substring("item/".length());
        else if (low.startsWith("textures/gems/")) base = "gems/" + base.substring("textures/gems/".length());
        else if (low.indexOf('/') < 0) base = "gems/" + base;
        else {
            // If someone typed some other folder, strip folder name and force under gems/
            int slash = base.indexOf('/');
            base = "gems/" + base.substring(slash + 1);
        }

        return base + suf;
    }

    // ─────────────────────────────────────────────────────────────
    // NBT helpers
    // ─────────────────────────────────────────────────────────────
    private static void ensureDefaultsIfMissing(ItemStack stack) {
        NBTTagCompound t = ensureHex(stack);
        if (!t.hasKey(K_OPEN)) t.setInteger(K_OPEN, 0);
        if (!t.hasKey(K_MAX))  t.setInteger(K_MAX, defaultMaxFor(stack));
        if (!t.hasKey(K_GEMS, 9)) t.setTag(K_GEMS, new NBTTagList()); // 9=list
    }

    private static int defaultMaxFor(ItemStack stack) {
        return (stack != null && stack.getItem() instanceof ItemArmor) ? 2 : 3;
    }

    private static int getOpen(ItemStack stack) {
        return ensureHex(stack).getInteger(K_OPEN);
    }

    private static void setOpen(ItemStack stack, int open) {
        ensureHex(stack).setInteger(K_OPEN, Math.max(0, open));
        clampGemListToOpen(stack);
    }

    private static int getMaxOrDefault(ItemStack stack) {
        NBTTagCompound t = ensureHex(stack);
        return t.hasKey(K_MAX) ? t.getInteger(K_MAX) : defaultMaxFor(stack);
    }

    private static void setMax(ItemStack stack, int max) {
        ensureHex(stack).setInteger(K_MAX, max);
    }

    private static NBTTagCompound ensureHex(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        if (!tag.hasKey(ROOT, 10)) tag.setTag(ROOT, new NBTTagCompound());
        return tag.getCompoundTag(ROOT);
    }

    // ─────────────────────────────────────────────────────────────
    // Gems list (NBT string list)
    // ─────────────────────────────────────────────────────────────
    private static NBTTagList getGemsList(ItemStack stack) {
        NBTTagCompound t = ensureHex(stack);
        if (!t.hasKey(K_GEMS, 9)) t.setTag(K_GEMS, new NBTTagList());
        return t.getTagList(K_GEMS, 8); // 8=string
    }

    private static int getGemsCount(ItemStack stack) {
        return getGemsList(stack).tagCount();
    }

    private static String getGemAt(ItemStack stack, int idx) {
        NBTTagList list = getGemsList(stack);
        if (idx < 0 || idx >= list.tagCount()) return "";
        return list.getStringTagAt(idx);
    }

    private static void addGem(ItemStack stack, String iconKey) {
        NBTTagCompound t = ensureHex(stack);
        NBTTagList list = getGemsList(stack);
        list.appendTag(new NBTTagString(iconKey));
        t.setTag(K_GEMS, list);
        clampGemListToOpen(stack);
    }

    private static String removeGemAt(ItemStack stack, int idx) {
        NBTTagCompound t = ensureHex(stack);
        NBTTagList list = getGemsList(stack);
        String removed = "";
        if (idx >= 0 && idx < list.tagCount()) removed = list.getStringTagAt(idx);

        NBTTagList out = new NBTTagList();
        for (int i = 0; i < list.tagCount(); i++) {
            if (i == idx) continue;
            out.appendTag(new NBTTagString(list.getStringTagAt(i)));
        }
        t.setTag(K_GEMS, out);
        return removed;
    }

    private static void clearGems(ItemStack stack) {
        ensureHex(stack).setTag(K_GEMS, new NBTTagList());
    }

    private static void clampGemListToOpen(ItemStack stack) {
        int open = getOpen(stack);
        if (open <= 0) { clearGems(stack); return; }

        NBTTagList list = getGemsList(stack);
        int n = list.tagCount();
        if (n <= open) return;

        NBTTagList out = new NBTTagList();
        for (int i = 0; i < open; i++) {
            out.appendTag(new NBTTagString(list.getStringTagAt(i)));
        }
        ensureHex(stack).setTag(K_GEMS, out);
    }

    // ─────────────────────────────────────────────────────────────
    // Lore: split sockets across multiple lore lines (prevents ultra-wide tooltips)
    // IMPORTANT: We do NOT redirect to any "socketed/" folder.
    // ─────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────
    // Sockets display
    // - We DO NOT print sockets into normal tooltip lore anymore.
    // - Instead we maintain ONE dedicated Lore Page under HexLorePages.Pages
    //   that contains ONLY the socket icon line(s):
    //     "Sockets: <ico...> <ico...> ..."
    // ─────────────────────────────────────────────────────────────
    private static void rebuildSocketLoreLines(ItemStack stack) {
        if (stack == null) return;

        // IMPORTANT:
        // - Do NOT render socketed gems on the main tooltip (display lore / page 0).
        // - Only render them on the dedicated sockets lore page (via LorePages API).
        //
        // So here we:
        //  1) remove any legacy "Sockets:" lines that may have been written into display lore
        //  2) rebuild the sockets-only lore page from NBT (HexGems + GemBonuses)

        try { replaceLoreBlockByPrefix(stack, SOCKET_PREFIX, null); } catch (Throwable t) { /* ignore */ }

        try { HexSocketAPI.syncSocketsPageOnly(stack); } catch (Throwable t) { /* ignore */ }
    }



    private static void ensureDisplayLore(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        if (!tag.hasKey("display", 10)) tag.setTag("display", new NBTTagCompound());
        NBTTagCompound display = tag.getCompoundTag("display");

        if (!display.hasKey("Lore", 9)) display.setTag("Lore", new NBTTagList());
    }

    // Removes ALL lore lines that match the prefix, then inserts newLines at the first removed position.
    private static void replaceLoreBlockByPrefix(ItemStack stack, String rawPrefix, List<String> newLines) {
        NBTTagCompound display = stack.getTagCompound().getCompoundTag("display");
        NBTTagList lore = display.getTagList("Lore", 8);

        List<String> keep = new ArrayList<String>();
        int insertAt = -1;

        for (int i = 0; i < lore.tagCount(); i++) {
            String s = lore.getStringTagAt(i);
            if (matchesPrefix(s, rawPrefix)) {
                if (insertAt < 0) insertAt = keep.size();
                // skip (remove)
            } else {
                keep.add(s);
            }
        }

        if (insertAt < 0) insertAt = keep.size();

        if (newLines != null && !newLines.isEmpty()) {
            keep.addAll(insertAt, newLines);
        }

        NBTTagList out = new NBTTagList();
        for (int i = 0; i < keep.size(); i++) {
            out.appendTag(new NBTTagString(keep.get(i)));
        }
        display.setTag("Lore", out);
    }

    // ─────────────────────────────────────────────────────────────
    // Lore Pages helpers (HexLorePages.Pages)
    // We use the same storage as LorePagesCommand:
    //   stack.tag.HexLorePages.Pages (NBTTagList<String>)
    // ─────────────────────────────────────────────────────────────

    private static String joinWithBackslashN(List<String> lines) {
        if (lines == null || lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append("\\n");
            sb.append(lines.get(i) == null ? "" : lines.get(i));
        }
        return sb.toString();
    }

    private static NBTTagList getOrCreatePagesList(ItemStack stack) {
        if (stack.getTagCompound() == null) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound tag = stack.getTagCompound();

        if (!tag.hasKey(LP_ROOT, 10)) tag.setTag(LP_ROOT, new NBTTagCompound());
        NBTTagCompound root = tag.getCompoundTag(LP_ROOT);

        if (!root.hasKey(LP_LIST, 9)) root.setTag(LP_LIST, new NBTTagList());
        return root.getTagList(LP_LIST, 8); // 8 = string
    }

    private static List<String> readPages(ItemStack stack) {
        List<String> out = new ArrayList<String>();
        if (stack == null || !stack.hasTagCompound()) return out;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(LP_ROOT, 10)) return out;

        NBTTagCompound root = tag.getCompoundTag(LP_ROOT);
        if (root == null || !root.hasKey(LP_LIST, 9)) return out;

        NBTTagList list = root.getTagList(LP_LIST, 8);
        for (int i = 0; i < list.tagCount(); i++) {
            out.add(list.getStringTagAt(i));
        }
        return out;
    }

    private static void savePages(ItemStack stack, List<String> pages) {
        if (stack == null) return;

        if (pages == null) pages = new ArrayList<String>();

        // Build NBTTagList<String>
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < pages.size(); i++) {
            String s = pages.get(i);
            if (s == null) s = "";
            list.appendTag(new NBTTagString(s));
        }

        if (stack.getTagCompound() == null) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound tag = stack.getTagCompound();

        if (!tag.hasKey(LP_ROOT, 10)) tag.setTag(LP_ROOT, new NBTTagCompound());
        NBTTagCompound root = tag.getCompoundTag(LP_ROOT);

        // If empty, clean up to keep items tidy
        if (list.tagCount() == 0) {
            if (root != null) root.removeTag(LP_LIST);

            if (root == null || root.hasNoTags()) tag.removeTag(LP_ROOT);
            if (tag.hasNoTags()) stack.setTagCompound(null);
            return;
        }

        root.setTag(LP_LIST, list);
    }

    private static int getSocketsPageIndex(ItemStack stack) {
        try {
            NBTTagCompound t = ensureHex(stack);
            if (t.hasKey(K_PAGE_IDX)) return t.getInteger(K_PAGE_IDX);
        } catch (Throwable ignored) {}
        return -1;
    }

    private static void setSocketsPageIndex(ItemStack stack, int idx) {
        try {
            ensureHex(stack).setInteger(K_PAGE_IDX, idx);
        } catch (Throwable ignored) {}
    }

    private static void clearSocketsPageIndex(ItemStack stack) {
        try {
            NBTTagCompound t = ensureHex(stack);
            if (t.hasKey(K_PAGE_IDX)) t.removeTag(K_PAGE_IDX);
        } catch (Throwable ignored) {}
    }

    private static boolean looksLikeSocketsPage(String page) {
        if (page == null) return false;

        // Normalize (strip leading colors, lower-case)
        String norm = stripLeadingColorCodes(page).trim().toLowerCase();

        // Our new page always starts with "Sockets:"
        if (norm.startsWith("sockets:")) return true;

        // Also catch older / accidental versions we previously generated
        // (so we can overwrite them into the minimal page).
        if (norm.contains("socketable") || norm.contains("hexsocket")) return true;
        if (norm.contains("slots") && norm.contains("sockets")) return true;

        // Icon tokens are a strong hint
        if (page.contains("<ico:gems/socket_empty_")) return true;

        return false;
    }

    private static void upsertSocketsLorePage(ItemStack stack, String pageText) {
        List<String> pages = readPages(stack);

        int preferred = -1;
        int stored = getSocketsPageIndex(stack);

        if (stored >= 0 && stored < pages.size() && looksLikeSocketsPage(pages.get(stored))) {
            preferred = stored;
        } else {
            // Find an existing sockets page
            for (int i = 0; i < pages.size(); i++) {
                if (looksLikeSocketsPage(pages.get(i))) {
                    preferred = i;
                    break;
                }
            }
        }

        if (preferred < 0) {
            preferred = pages.size();
            pages.add(pageText);
        } else {
            pages.set(preferred, pageText);
        }

        // Remove duplicates (keep only ONE sockets page)
        for (int i = pages.size() - 1; i >= 0; i--) {
            if (i == preferred) continue;
            if (looksLikeSocketsPage(pages.get(i))) {
                pages.remove(i);
                if (i < preferred) preferred--;
            }
        }

        savePages(stack, pages);
        setSocketsPageIndex(stack, preferred);
    }

    private static void removeSocketsLorePage(ItemStack stack) {
        List<String> pages = readPages(stack);
        if (pages.isEmpty()) {
            clearSocketsPageIndex(stack);
            return;
        }

        int stored = getSocketsPageIndex(stack);
        boolean removedAny = false;

        for (int i = pages.size() - 1; i >= 0; i--) {
            if (looksLikeSocketsPage(pages.get(i))) {
                pages.remove(i);
                removedAny = true;

                // adjust stored index if needed
                if (stored == i) stored = -1;
                else if (stored > i) stored--;
            }
        }

        if (removedAny) savePages(stack, pages);
        clearSocketsPageIndex(stack);
    }


    private static boolean matchesPrefix(String line, String rawPrefix) {
        if (line == null || rawPrefix == null) return false;
        String norm = stripLeadingColorCodes(line).trim();
        if (norm.length() < rawPrefix.length()) return false;
        return norm.regionMatches(true, 0, rawPrefix, 0, rawPrefix.length());
    }

    private static String stripLeadingColorCodes(String s) {
        if (s == null) return "";
        int i = 0;
        while (i + 1 < s.length()) {
            char c = s.charAt(i);
            char n = s.charAt(i + 1);
            if ((c == '§' || c == '&') && isColorCodeChar(n)) { i += 2; continue; }
            if (c == ' ' || c == '\t') { i++; continue; }
            break;
        }
        return s.substring(i);
    }

    private static boolean isColorCodeChar(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'k' && c <= 'o')
                || (c == 'r')
                || (c >= 'A' && c <= 'F')
                || (c >= 'K' && c <= 'O')
                || (c == 'R');
    }

    private static ChatComponentText txt(String s) { return new ChatComponentText(s); }
}
