package com.example.examplemod.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import java.util.ArrayList;
import java.util.List;

public class HexHelpCommand extends CommandBase {

    // ─────────────────────────────────────────────
    // Paging knobs (tweak these)
    // ─────────────────────────────────────────────
    private static final int MAX_LINES_PER_PAGE    = 12; // hard cap: total lines per page (tips + sections + examples)
    private static final int MAX_EXAMPLES_PER_PAGE = 8;  // soft cap: number of examples per page before auto-splitting

    @Override
    public String getCommandName() {
        return "hexhelp";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/hexhelp [page]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0; // everyone
    }
    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true; // allow everyone (even deopped)
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        int page = 1;
        if (args != null && args.length >= 1) {
            try { page = Integer.parseInt(args[0]); } catch (Throwable ignored) {}
        }

        List<List<IChatComponent>> pages = buildPages();
        int max = pages.size();
        if (page < 1) page = 1;
        if (page > max) page = max;

        sender.addChatMessage(new ChatComponentText(titleBar("HexColorCodes Help")));

        // ✅ keep small gap only for the TOP nav
        sender.addChatMessage(new ChatComponentText(""));
        sender.addChatMessage(navLine(page, max));
        sender.addChatMessage(new ChatComponentText(""));

        for (IChatComponent line : pages.get(page - 1)) {
            sender.addChatMessage(line);
        }

        // ✅ add space ABOVE the bottom nav
        sender.addChatMessage(new ChatComponentText(""));

        // ✅ bottom nav stays last, so it hugs the bottom
        sender.addChatMessage(navLine(page, max));
    }

    // ─────────────────────────────────────────────
    // Fancy UI helpers (header + nav)
    // ─────────────────────────────────────────────

    private String titleBar(String title) {
        // ✅ wave the entire bar line (keeps your same gradient + styles)
        return "<wave amp=0.55 speed=4.5>"
                + "<grad #55FFFF #FFFFFF #FF55FF scroll=0.18 styles=l>"
                + "========== " + title + " =========="
                + "</grad>"
                + "</wave>";
    }

    private String navSep() {
        // ✅ wider spacing around divider
        return "  <#444444>|</#>  ";
    }

    private String pageChip(int page, int max) {
        return "<grad #9CA3AF #FFFFFF #9CA3AF scroll=0.10>"
                + "Page <#FFFFFFl>" + page + "</#>/"
                + "<#FFFFFFl>" + max + "</#>"
                + "</grad>";
    }

    private IChatComponent navLine(int page, int max) {
        ChatComponentText line = new ChatComponentText("");

        line.appendSibling(btnFancy("« Prev",
                page > 1 ? ("/hexhelp " + (page - 1)) : null,
                page > 1 ? ("Go to page " + (page - 1)) : "Already on page 1"
        ));

        line.appendText(navSep());
        line.appendSibling(new ChatComponentText(pageChip(page, max)));
        line.appendText(navSep());

        line.appendSibling(btnFancy("Next »",
                page < max ? ("/hexhelp " + (page + 1)) : null,
                page < max ? ("Go to page " + (page + 1)) : "Already on last page"
        ));

        return line;
    }

    private IChatComponent btnFancy(String text, String runCmdOrNull, String hover) {
        String shown = (runCmdOrNull != null)
                ? ("<grad #55FFFF #FFFFFF scroll=0.14>" + text + "</grad>")
                : ("<#666666>" + text + "</#>");

        ChatComponentText c = new ChatComponentText(shown);

        ChatStyle st = new ChatStyle();
        if (runCmdOrNull != null) {
            st.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, runCmdOrNull));
        }
        st.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ChatComponentText(EnumChatFormatting.YELLOW + hover)));
        c.setChatStyle(st);

        return c;
    }

    // ─────────────────────────────────────────────
    // Existing helpers (examples + layout)
    // ─────────────────────────────────────────────

    private IChatComponent exLine(String label, String example) {
        return exLine2(label, example, example);
    }

    private IChatComponent exLine2(String label, String display, String insert) {
        ChatComponentText line = new ChatComponentText(EnumChatFormatting.DARK_GRAY + "• "
                + EnumChatFormatting.GRAY + label + ": ");

        ChatComponentText ex = new ChatComponentText(display);

        ChatStyle st = new ChatStyle();
        st.setColor(EnumChatFormatting.WHITE);
        st.setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, insert));
        st.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ChatComponentText(EnumChatFormatting.AQUA + "Click to insert into chat")));
        ex.setChatStyle(st);

        line.appendSibling(ex);
        return line;
    }

    private IChatComponent tip(String s) {
        return new ChatComponentText(EnumChatFormatting.DARK_GRAY + "» "
                + EnumChatFormatting.GRAY + s);
    }

    private IChatComponent section(String s) {
        return new ChatComponentText(EnumChatFormatting.DARK_GRAY + "— "
                + EnumChatFormatting.AQUA + s
                + EnumChatFormatting.DARK_GRAY + " —");
    }

    // ─────────────────────────────────────────────
    // Auto page splitting builder
    // ─────────────────────────────────────────────

    private static final class PageBuilder {
        private final List<List<IChatComponent>> pages = new ArrayList<List<IChatComponent>>();
        private List<IChatComponent> cur = new ArrayList<IChatComponent>();

        private int lineCount = 0;
        private int exampleCount = 0;

        void addLine(IChatComponent c) {
            if (c == null) return;
            if (lineCount >= MAX_LINES_PER_PAGE) newPage(false);
            cur.add(c);
            lineCount++;
        }

        void blank() {
            addLine(new ChatComponentText(""));
        }

        void tip(IChatComponent c) {
            addLine(c);
        }

        void section(IChatComponent c) {
            if (lineCount >= MAX_LINES_PER_PAGE - 1) newPage(false);
            addLine(c);
        }

        void example(IChatComponent c) {
            if (exampleCount >= MAX_EXAMPLES_PER_PAGE || lineCount >= MAX_LINES_PER_PAGE) {
                newPage(true);
            }
            addLine(c);
            exampleCount++;
        }

        void newPage(boolean keepHeaderSpace) {
            if (!cur.isEmpty()) pages.add(cur);
            cur = new ArrayList<IChatComponent>();
            lineCount = 0;
            exampleCount = 0;

            if (keepHeaderSpace) {
                // (no blank line by default)
            }
        }

        void finish() {
            if (!cur.isEmpty()) pages.add(cur);
        }
    }

    private List<List<IChatComponent>> buildPages() {
        PageBuilder pb = new PageBuilder();

        // ─────────────────────────────────────────────
        // GROUP 1 — Quick Start
        // ─────────────────────────────────────────────
        pb.tip(tip("Click any example to auto-insert it into your chat input."));
        pb.tip(tip("Most tags can be nested: <snow><grad ...>Text</grad></snow>"));
        pb.tip(tip("Vanilla preview uses § colors, inserts use safe tags (no § in chat)."));
        pb.blank();

        pb.section(section("Hex basics"));
        pb.example(exLine2("Inline Hex",
                EnumChatFormatting.GOLD + "Hello " + EnumChatFormatting.AQUA + "world" + EnumChatFormatting.RESET,
                "<#FFCC00>Hello </#><#00E5FF>world</#>"));
        pb.example(exLine2("Short + long",
                EnumChatFormatting.GREEN + "ShortHex" + EnumChatFormatting.GRAY + " + " + EnumChatFormatting.AQUA + "LongHex" + EnumChatFormatting.RESET,
                "<#0F8>ShortHex</#> + <#00FF88>LongHex</#>"));
        pb.example(exLine2("Style suffix",
                EnumChatFormatting.YELLOW.toString() + EnumChatFormatting.BOLD + "Bold" + EnumChatFormatting.RESET,
                "<#FFFF55l>Bold</#>"));

        pb.blank();
        pb.section(section("Vanilla-look builds (exactly what you see)"));
        pb.example(exLine2("Quick gradient (2 colors)",
                EnumChatFormatting.RED + "Sun" + EnumChatFormatting.GOLD + "set" + EnumChatFormatting.RESET,
                "<#FF5555>Sun</#><#FFAA00>set</#>"));
        pb.example(exLine2("Quick rainbow (per-letter)",
                EnumChatFormatting.RED + "R" +
                        EnumChatFormatting.GOLD + "a" +
                        EnumChatFormatting.YELLOW + "i" +
                        EnumChatFormatting.GREEN + "n" +
                        EnumChatFormatting.AQUA + "b" +
                        EnumChatFormatting.BLUE + "o" +
                        EnumChatFormatting.LIGHT_PURPLE + "w" +
                        EnumChatFormatting.RESET,
                "<#FF5555>R</#><#FFAA00>a</#><#FFFF55>i</#><#55FF55>n</#><#55FFFF>b</#><#5555FF>o</#><#FF55FF>w</#>"));

        pb.newPage(true);

        // ─────────────────────────────────────────────
        // GROUP 2 — Vanilla styles + palette (kept as-is)
        // ─────────────────────────────────────────────
        pb.tip(tip("These use the supported hex-style suffix letters: l/o/n/m/k"));
        pb.tip(tip("You can stack them: <#FFAA00lonm>Text</#>"));
        pb.blank();

        pb.section(section("Vanilla styles"));
        pb.example(exLine2("Bold",
                EnumChatFormatting.YELLOW.toString() + EnumChatFormatting.BOLD + "Bold" + EnumChatFormatting.RESET,
                "<#FFFF55l>Bold</#>"));
        pb.example(exLine2("Italic",
                EnumChatFormatting.AQUA.toString() + EnumChatFormatting.ITALIC + "Italic" + EnumChatFormatting.RESET,
                "<#55FFFFo>Italic</#>"));
        pb.example(exLine2("Underline",
                EnumChatFormatting.LIGHT_PURPLE.toString() + EnumChatFormatting.UNDERLINE + "Underline" + EnumChatFormatting.RESET,
                "<#FF55FFn>Underline</#>"));
        pb.example(exLine2("Strikethrough",
                EnumChatFormatting.RED.toString() + EnumChatFormatting.STRIKETHROUGH + "Strike" + EnumChatFormatting.RESET,
                "<#FF5555m>Strike</#>"));
        pb.example(exLine2("Obfuscated",
                EnumChatFormatting.DARK_PURPLE.toString() + EnumChatFormatting.OBFUSCATED + "Obfuscate" + EnumChatFormatting.RESET,
                "<#AA00AAk>Obfuscate</#>"));
        pb.example(exLine2("Mix (bold+italic)",
                EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + "" + EnumChatFormatting.ITALIC + "Combo" + EnumChatFormatting.RESET,
                "<#FFAA00lo>Combo</#>"));

        pb.blank();
        pb.section(section("Vanilla palette (safe inserts)"));
        pb.example(exLine2("c Red", EnumChatFormatting.RED + "Red" + EnumChatFormatting.RESET, "<#FF5555>Red</#>"));
        pb.example(exLine2("6 Gold", EnumChatFormatting.GOLD + "Gold" + EnumChatFormatting.RESET, "<#FFAA00>Gold</#>"));
        pb.example(exLine2("e Yellow", EnumChatFormatting.YELLOW + "Yellow" + EnumChatFormatting.RESET, "<#FFFF55>Yellow</#>"));
        pb.example(exLine2("a Green", EnumChatFormatting.GREEN + "Green" + EnumChatFormatting.RESET, "<#55FF55>Green</#>"));
        pb.example(exLine2("b Aqua", EnumChatFormatting.AQUA + "Aqua" + EnumChatFormatting.RESET, "<#55FFFF>Aqua</#>"));
        pb.example(exLine2("9 Blue", EnumChatFormatting.BLUE + "Blue" + EnumChatFormatting.RESET, "<#5555FF>Blue</#>"));
        pb.example(exLine2("d Light Purple", EnumChatFormatting.LIGHT_PURPLE + "LightPurple" + EnumChatFormatting.RESET, "<#FF55FF>LightPurple</#>"));
        pb.example(exLine2("f White", EnumChatFormatting.WHITE + "White" + EnumChatFormatting.RESET, "<#FFFFFF>White</#>"));

        pb.newPage(true);

        // ─────────────────────────────────────────────
        // GROUP 3 — Gradients
        // (default examples -> color examples -> combo/nested examples)
        // ─────────────────────────────────────────────
        pb.tip(tip("Gradient: <grad ...>TEXT</grad> (supports multi-stops)"));
        pb.tip(tip("Common args: scroll=, waveamp=, wavespeed=, styles="));
        pb.blank();

        pb.section(section("Gradients (default)"));
        pb.example(exLine("2-stop sunset", "<grad #FF6A00 #FFDD55>Sunset Shine</grad>"));
        pb.example(exLine("2-stop ocean", "<grad #00C6FF #0072FF>Deep Ocean</grad>"));
        pb.example(exLine("3-stop neon", "<grad #00FF88 #FFFFFF #FF00AA>Neon Pop</grad>"));
        pb.example(exLine("3-stop aurora", "<grad #55FFFF #FFFFFF #AA55FF>Aurora</grad>"));

        pb.blank();
        pb.section(section("Gradients (color / styled)"));
        pb.example(exLine("Scrolling shine", "<grad #FF6A00 #FFDD55 scroll=0.24>Scrolling Shine</grad>"));
        pb.example(exLine("Scroll + bold", "<grad #00C6FF #0072FF scroll=0.20 styles=l>Bold Ocean</grad>"));
        pb.example(exLine("Scroll + underline", "<grad #FF00AA #FFFFFF scroll=0.18 styles=n>Underline Glow</grad>"));
        pb.example(exLine("Scroll + wave", "<grad #FF00AA #00E5FF scroll=0.22 waveamp=2.2 wavespeed=6>Wave Shine</grad>"));

        pb.blank();
        pb.section(section("Gradients (combo / nested)"));
        pb.example(exLine("Inside outline", "<outline><grad #FFDD55 #FF6A00 scroll=0.18>Outlined Gradient</grad></outline>"));
        pb.example(exLine("Inside snow",
                "<snow dens=0.35 fall=14 speed=0.85 drift=1.1><grad #55FFFF #FFFFFF #AA55FF scroll=0.22>[Aurora Alert]</grad></snow>"));

        pb.newPage(true);

        // ─────────────────────────────────────────────
        // GROUP 4 — Rainbow (ALL rainbow stuff on one page)
        // ─────────────────────────────────────────────
        pb.tip(tip("Rainbow aliases: <rainbow>, <rb>, <rbw> ... </rbw> (try nesting with pulse/outline)."));
        pb.blank();

        pb.section(section("Rainbow (examples)"));
        pb.example(exLine("Rainbow simple", "<rbw>R A I N B O W</rbw>"));
        pb.example(exLine("Rainbow tuned", "<rbw sat=1 val=1 speed=1.2>Rainbow Speed</rbw>"));
        pb.example(exLine("Rainbow slow", "<rbw speed=0.55 cycles=1.1>Slow Rainbow</rbw>"));
        pb.example(exLine("Rainbow + pulse (soft)", "<pulse amp=0.18 speed=2.4><rbw speed=0.85>Soft Pulse</rbw></pulse>"));
        pb.example(exLine("Rainbow + pulse (strong)", "<pulse amp=0.65 speed=0.9><rbw cycles=1.2 speed=0.55>Pulse Rainbow</rbw></pulse>"));
        pb.example(exLine("Rainbow + wave", "<wave amp=2.0 speed=6><rbw speed=0.9>Wavy Rainbow</rbw></wave>"));
        pb.example(exLine("Rainbow + outline", "<outline><rbw sat=1 val=1 speed=1.1>Outlined RBW</rbw></outline>"));

        pb.newPage(true);

        // ─────────────────────────────────────────────
        // GROUP 5 — Motion tags
        // ─────────────────────────────────────────────
        pb.tip(tip("Motion: wave / wobble / jitter / shake / zoom / scroll"));
        pb.tip(tip("Best on short phrases (chat lines + tooltips)."));
        pb.blank();

        pb.section(section("Motion (examples)"));
        pb.example(exLine("Wave", "<wave amp=2.2 speed=6><grad #FF6A00 #FFDD55>Wavy Text</grad></wave>"));
        pb.example(exLine("Shake", "<shake amp=1.2 speed=10><grad #FF3B3B #FFD1D1>Shaky!</grad></shake>"));
        pb.example(exLine("Jitter", "<jitter amp=1.0 speed=14><grad #00FF88 #00C6FF>Jitter</grad></jitter>"));
        pb.example(exLine("Wobble", "<wobble amp=1.6 speed=4><grad #A855F7 #FDE047>Wobble</grad></wobble>"));
        pb.example(exLine("Zoom", "<zoom amp=1.35 speed=3.5><grad #7CFF00 #00FFD5>Zoom Pop</grad></zoom>"));
        pb.example(exLine("Scroll", "<scroll speed=1.0><grad #FF00AA #00E5FF>Scroll Motion</grad></scroll>"));

        pb.newPage(true);

        // ─────────────────────────────────────────────
        // GROUP 6 — NEW: Loop + ShootingStar
        // ─────────────────────────────────────────────
        pb.tip(tip("NEW tags: <loop ...> and <shootingstar ...> (both layer nicely over gradients/rainbow)."));
        pb.tip(tip("Loop often looks best wrapping a long divider or header line."));
        pb.blank();

        pb.section(section("Loop"));
        pb.example(exLine("Loop (simple color)",
                "<loop #FFD45A>======================</loop>"));
        pb.example(exLine("Loop + gradient (animated)",
                "<loop #FFD45A><grad #FFD45A #FF8A2A #FF3B3B #FFD45A scroll=0.22>──────── LOOP BAR ────────</grad></loop>"));
        pb.example(exLine("Loop + wave header",
                "<loop #FF4FD8><wave amp=0.55 speed=4.5><grad #00C6FF #0072FF #FF4FD8 #00E5FF scroll=0.22 styles=l>──────── Header ────────</grad></wave></loop>"));

        pb.blank();
        pb.section(section("ShootingStar"));
        pb.example(exLine("ShootingStar (simple)",
                "<shootingstar><grad #55FFFF #FFFFFF #FF55FF scroll=0.18>Wish granted!</grad></shootingstar>"));
        pb.example(exLine("ShootingStar + rainbow",
                "<shootingstar><rbw speed=0.85>STAR TRAIL</rbw></shootingstar>"));
// replace the broken one with outline-first
        pb.example(exLine("ShootingStar + outline",
                "<outline><shootingstar><grad #FFDD55 #FF6A00 scroll=0.18 styles=l>CRIT!</grad></shootingstar></outline>"));
// add a solid-color variant (solid red)
        pb.example(exLine("ShootingStar (solid red)",
                "<outline><shootingstar><#FF3B3Bl>CRIT!</#></shootingstar></outline>"));


        pb.newPage(true);

        // ─────────────────────────────────────────────
        // GROUP 7 — FX (outline/shadow/flicker/glitch/sparkle/rain)
        // ─────────────────────────────────────────────
        pb.tip(tip("FX tags: outline / shadow / sparkle / flicker / glitch / rain"));
        pb.tip(tip("These can wrap other styles for layered looks."));
        pb.tip(tip("Rain: drop=#RRGGBB or drop=#A,#B or drop=rbw"));
        pb.tip(tip("Sparkle: star=#RRGGBB or star=#A,#B or star=rbw"));
        pb.blank();

        pb.section(section("Core FX"));
        pb.example(exLine("Outline", "<outline><grad #FFDD55 #FF6A00>Outlined</grad></outline>"));
        pb.example(exLine("Shadow", "<shadow><grad #00C6FF #0072FF>Shadowed</grad></shadow>"));
        pb.example(exLine("Flicker", "<flicker><grad #7CFF00 #00FFD5>Flicker</grad></flicker>"));
        pb.example(exLine("Glitch", "<glitch><grad #FF3B3B #FFFFFF>GL1TCH</grad></glitch>"));

        pb.blank();
        pb.section(section("Sparkle"));
        pb.example(exLine("Stars inherit",
                "<sparkle><grad #FF00AA #FFFFFF>✨ Sparkle ✨</grad></sparkle>"));
        pb.example(exLine("Stars solid",
                "<sparkle star=#FFFFFF><grad #FF00AA #00E5FF>Star Solid</grad></sparkle>"));
        pb.example(exLine("Stars gradient",
                "<sparkle star=#00FFD5,#7CFF00><grad #A855F7 #FDE047>Star Gradient</grad></sparkle>"));
        pb.example(exLine("Stars rainbow",
                "<sparkle star=rbw><grad #FF6A00 #FFDD55>Star Rainbow</grad></sparkle>"));

        pb.blank();
        pb.section(section("Rain"));
        pb.example(exLine("Rain (simple)", "<rain>Rain</rain>"));
        pb.example(exLine("Drops solid",
                "<rain drop=#33A1FF dens=0.22 fall=10 speed=0.75 drift=0.9><grad #FF6A00 #FFDD55>Rainy</grad></rain>"));
        pb.example(exLine("Drops gradient",
                "<rain drop=#00FFD5,#7CFF00 dens=0.22 fall=10 speed=0.75 drift=0.9><grad #A855F7 #FDE047>Rainy</grad></rain>"));
        pb.example(exLine("Drops rbw",
                "<rain drop=rbw dens=0.22 fall=10 speed=0.75 drift=0.9><grad #FF3B3B #FFD1D1>Rainy</grad></rain>"));

        pb.newPage(true);

        // ─────────────────────────────────────────────
        // GROUP 8 — Snow Deep Dive
        // ─────────────────────────────────────────────
        pb.tip(tip("Snow is an overlay: dens/fall/speed/drift/start change the vibe a lot."));
        pb.tip(tip("Snow flakes can be colored: flake=#RRGGBB | flake=#A,#B | flake=rbw"));
        pb.tip(tip("Use flakeMix=0..1 to keep rainbow flakes looking icy (mixes toward white)."));
        pb.blank();

        pb.section(section("Snow aura (basic)"));
        pb.example(exLine("Aura basic",
                "<snow dens=0.35 fall=14 speed=0.85 drift=1.1><grad #55FFFF #FFFFFF #AA55FF>[Aurora]</grad></snow>"));

        pb.blank();
        pb.section(section("Snow presets"));
        pb.example(exLine("Snow (simple)", "<snow>Snow</snow>"));
        pb.example(exLine("Soft & slow",
                "<snow dens=0.18 fall=10 speed=0.55 drift=0.55><grad #A8E6FF #FFFFFF>Soft Snow</grad></snow>"));
        pb.example(exLine("Heavy blizzard",
                "<snow dens=0.70 fall=18 speed=1.15 drift=1.35><grad #CFE9FF #FFFFFF>Blizzard</grad></snow>"));
        pb.example(exLine("Windy drift",
                "<snow dens=0.30 fall=12 speed=0.80 drift=1.85><grad #BFE6FF #EAF7FF>Windy Snow</grad></snow>"));
        pb.example(exLine("High flakes",
                "<snow dens=0.28 fall=22 start=18 speed=0.90 drift=1.10><grad #D7F1FF #FFFFFF>High Flakes</grad></snow>"));

        pb.blank();
        pb.section(section("Flake color modes"));
        pb.example(exLine("Inherit",
                "<snow dens=0.35 fall=14 speed=0.85 drift=1.1><grad #FF00AA #FFFFFF #00E5FF scroll=0.18>[Inherit]</grad></snow>"));
        pb.example(exLine("Solid",
                "<snow flake=#55FFFF dens=0.35 fall=14 speed=0.85 drift=1.1><grad #FF00AA #FFFFFF #00E5FF scroll=0.18>[Solid]</grad></snow>"));
        pb.example(exLine("Gradient",
                "<snow flake=#00FFD5,#7CFF00 dens=0.35 fall=14 speed=0.85 drift=1.1><grad #FF00AA #FFFFFF #00E5FF scroll=0.18>[Grad]</grad></snow>"));
        pb.example(exLine("RBW",
                "<snow flake=rbw dens=0.35 fall=14 speed=0.85 drift=1.1><grad #FF00AA #FFFFFF #00E5FF scroll=0.18>[RBW]</grad></snow>"));
        pb.example(exLine("RBW icy",
                "<snow flake=rbw flakeMix=0.35 dens=0.35 fall=14 speed=0.85 drift=1.1><grad #FF00AA #FFFFFF #00E5FF scroll=0.18>[RBW Icy]</grad></snow>"));

        pb.blank();
        pb.section(section("Readability"));
        pb.example(exLine("Snow + outline",
                "<snow dens=0.30 fall=14 speed=0.85 drift=1.1><outline><grad #55FFFF #FFFFFF #AA55FF>[Aurora Alert]</grad></outline></snow>"));
        pb.example(exLine("Snow + shadow",
                "<snow dens=0.28 fall=13 speed=0.80 drift=1.0><shadow><grad #55FFFF #FFFFFF>Winter Glow</grad></shadow></snow>"));

        pb.newPage(true);

        // ─────────────────────────────────────────────
        // GROUP 9 — Recipes / Layer stacks
        // ─────────────────────────────────────────────
        pb.tip(tip("Recipes kept short to reduce wrap/carry weirdness."));
        pb.tip(tip("Rule: outer effect wraps inner style."));
        pb.blank();

        pb.section(section("Holiday / flashy"));
        pb.example(exLine("Snow + gradient (Christmas)",
                "<snow dens=0.35 fall=14 speed=0.85 drift=1.1>"
                        + "<grad #0B4D2A #FFFFFF #B31217 scroll=0.22 waveamp=2.2 wavespeed=6 styles=l>"
                        + "[Christmas Bonus]"
                        + "</grad></snow>"));
        pb.example(exLine("Snow + rbw",
                "<snow dens=0.28 fall=12 speed=0.8 drift=1.0><rbw sat=1 val=1 speed=1.1>Winter Glow</rbw></snow>"));

        pb.blank();
        pb.section(section("Layer stacks"));
        pb.example(exLine("Outline + rbw",
                "<outline><rbw sat=1 val=1 speed=1.1>Outlined</rbw></outline>"));
        pb.example(exLine("Shadow + flicker + grad",
                "<shadow><flicker><grad #00C6FF #0072FF>Neon</grad></flicker></shadow>"));
        pb.example(exLine("Glitch + pulse + grad",
                "<glitch><pulse amp=0.25 speed=3.2><grad #FF3B3B #FFD1D1>Pulse</grad></pulse></glitch>"));

        pb.blank();
        pb.section(section("Sparkle + snow"));
        pb.example(exLine("Stars inherit",
                "<snow dens=0.30 fall=14 speed=0.85 drift=1.1>"
                        + "<sparkle><grad #FF00AA #FFFFFF>Snow Sparkle</grad></sparkle>"
                        + "</snow>"));
        pb.example(exLine("Stars colored",
                "<sparkle star=#00FFD5,#7CFF00>"
                        + "<snow dens=0.30 fall=14 speed=0.85 drift=1.1>"
                        + "<grad #55FFFF #FFFFFF #AA55FF>[Aurora Bonus]</grad>"
                        + "</snow></sparkle>"));

        pb.blank();
        pb.section(section("Storm mixes"));
        pb.example(exLine("Rain + pulse + grad",
                "<rain drop=#00FFD5,#7CFF00 dens=0.22 fall=10 speed=0.75 drift=0.9>"
                        + "<pulse amp=0.22 speed=2.4><grad #FF6A00 #FFDD55>Wet</grad></pulse></rain>"));
        pb.example(exLine("Snow + rain",
                "<rain drop=#55FFFF dens=0.18 fall=10 speed=0.75 drift=0.9>"
                        + "<snow dens=0.22 fall=16 speed=0.95 drift=1.2>"
                        + "<grad #BFE6FF #FFFFFF>Storm Mix</grad>"
                        + "</snow></rain>"));
        pb.example(exLine("Snow flakes + aurora",
                "<snow flake=#55FFFF,#AA55FF dens=0.30 fall=14 speed=0.85 drift=1.1>"
                        + "<grad #55FFFF #FFFFFF #AA55FF scroll=0.22>[Aurora Bonus]</grad>"
                        + "</snow>"));

        pb.finish();
        return pb.pages;
    }
}
