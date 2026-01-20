package com.example.examplemod.client.gui;

import com.example.examplemod.api.HexSocketAPI;
import com.example.examplemod.gui.ContainerHexSocketOpener;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Method;

/**
 * Hex Socket Opener GUI (client).
 *
 * - 2 input slots: target + material
 * - 3 options: Open +1 / +2 / +3 (XP levels + 1 material)
 * - Help is a hover widget that pops a panel on the LEFT when possible.
 */
@SideOnly(Side.CLIENT)
public class GuiHexSocketOpener extends GuiContainer {

    // Put this texture at:
    //   src/main/resources/assets/hexcolorcodes/textures/gui/hexsocket_opener.png
    private static final ResourceLocation TEX_BG = new ResourceLocation("hexcolorcodes", "textures/gui/hexsocket_opener.png");

    // 8-frame glow overlay for the material slot (256x256 atlas, frames stacked vertically at x=0)
    private static final ResourceLocation TEX_GLOW = new ResourceLocation("hexcolorcodes", "textures/gui/hexsocket_catalyst_glow_atlas.png");
    private static final int GLOW_FRAMES = 8;

    private static final int NAV_BTN_ID = 1001;

    // "tab" size
    private static final int TAB_W = 48;
    private static final int TAB_H = 12;

    // Help button rect (GUI-local)
    private static final int HELP_X = 8;
    private static final int HELP_Y = 17;
    // Keep this compact so it doesn't overlap the 3 option rows on the right.
    private static final int HELP_W = 46;
    private static final int HELP_H = 16;

    // Hint panel (GUI-local coords, can be negative to render left of GUI)
    private static final int HINT_W = 272;
    private static final int HINT_H = 140;
    private static final int HINT_PAD = 6;
    private static final int HINT_GAP = 8;
    private static final int LINE_H = 12;

    // If your HexFontRenderer/format-style system is active in GUIs, leave this TRUE.
    // If you ever see raw tags like <grad ...> in the panel, set this to FALSE to use the safe fallback gradients.
    private static final boolean USE_STYLE_TAGS = true;

    private final EntityPlayer player;
    private final ContainerHexSocketOpener opener;

    private GuiButton btnGoSocketStation;
    private final OptionButton[] optionBtns = new OptionButton[3];

    private int lastMouseX;
    private int lastMouseY;
    private boolean hintOpen = false;

    public GuiHexSocketOpener(EntityPlayer player) {
        this(new ContainerHexSocketOpener(player.inventory, player), player);
    }

    public GuiHexSocketOpener(Container container, EntityPlayer player) {
        super(container);
        this.player = player;
        this.opener = (ContainerHexSocketOpener) container;
        this.xSize = 176;
        this.ySize = 166;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();

        int x = (this.width - this.xSize) / 2;
        int y = (this.height - this.ySize) / 2;

        // 3 option buttons (custom-drawn)
        for (int i = 0; i < 3; i++) {
            // Nudge 1px to better align with the texture (and avoid crowding the Help button).
            optionBtns[i] = new OptionButton(i, x + 59, y + 14 + i * 19, 108, 19);
            this.buttonList.add(optionBtns[i]);
        }

        // Top-right tab (hidden unless SHIFT)
        btnGoSocketStation = new GuiButton(NAV_BTN_ID,
                x + this.xSize - TAB_W - 4,
                y - (TAB_H - 4),
                TAB_W, TAB_H,
                "Socket");
        this.buttonList.add(btnGoSocketStation);
    }

    @Override
    protected void actionPerformed(GuiButton btn) {
        if (btn == null || !btn.enabled) return;

        if (btn.id >= 0 && btn.id <= 2) {
            // This calls Container.enchantItem on the server for this window.
            Minecraft.getMinecraft().playerController.sendEnchantPacket(this.inventorySlots.windowId, btn.id);
            return;
        }

        if (btn.id == NAV_BTN_ID) {
            // Switch back to the gem socketing station
            Minecraft.getMinecraft().thePlayer.sendChatMessage("/hexsocket gui");
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();

        // Show nav tab only while holding SHIFT
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        btnGoSocketStation.visible = shift;

        // Update option labels + enabled state
        ItemStack target = opener.getInputInventory().getStackInSlot(ContainerHexSocketOpener.SLOT_TARGET);
        ItemStack mat    = opener.getInputInventory().getStackInSlot(ContainerHexSocketOpener.SLOT_CATALYST);

        int open = 0;
        int max  = 0;
        boolean canOpen = false;
        if (target != null) {
            open = HexSocketAPI.getSocketsOpen(target);
            max  = HexSocketAPI.getSocketsMax(target);
            canOpen = (max < 0) || (open < max);
        }

        int remaining = 0;
        if (target != null) {
            remaining = (max < 0) ? 999 : Math.max(0, max - open);
        }

        boolean hasMat = isValidMaterial(mat);

        for (int i = 0; i < 3; i++) {
            OptionButton b = optionBtns[i];
            int baseWant = i + 1;
            int add = Math.min(baseWant, Math.max(0, remaining));
            int cost = opener.costs[i];

            if (target == null || !canOpen || add <= 0 || cost <= 0) {
                b.displayString = "-";
                b.enabled = false;
                continue;
            }

            b.displayString = "Open +" + add + " (" + cost + " lv)";

            boolean enoughLevels = player.capabilities.isCreativeMode || player.experienceLevel >= cost;
            b.enabled = enoughLevels && hasMat;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GL11.glColor4f(1f, 1f, 1f, 1f);
        this.mc.getTextureManager().bindTexture(TEX_BG);
        int x = this.guiLeft;
        int y = this.guiTop;
        this.drawTexturedModalRect(x, y, 0, 0, this.xSize, this.ySize);

        // Material slot glow (subtle + animated)
        drawCatalystGlow(partialTicks);
    }

    private void drawCatalystGlow(float partialTicks) {
        Slot s = findInputSlot(ContainerHexSocketOpener.SLOT_CATALYST);
        if (s == null) return;

        // Always glow a bit to hint "material slot" (stronger when empty)
        ItemStack mat = opener.getInputInventory().getStackInSlot(ContainerHexSocketOpener.SLOT_CATALYST);
        float alpha = (mat == null) ? 0.85f : 0.35f;

        long time = Minecraft.getSystemTime();
        int frame = (int) ((time / 110L) % GLOW_FRAMES);

        GL11.glColor4f(1f, 1f, 1f, alpha);
        this.mc.getTextureManager().bindTexture(TEX_GLOW);

        int u = 0;
        int v = frame * 16;
        int x = this.guiLeft + s.xDisplayPosition;
        int y = this.guiTop + s.yDisplayPosition;

        this.drawTexturedModalRect(x, y, u, v, 16, 16);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // Title
        this.fontRendererObj.drawString("Hex Socket Opener", 8, 6, 0x404040);

        // Help button
        int mx = this.lastMouseX - this.guiLeft;
        int my = this.lastMouseY - this.guiTop;
        boolean helpHover = isMouseOver(mx, my, HELP_X, HELP_Y, HELP_W, HELP_H);

        drawHelpButton(helpHover);

        // Update hint open state (only when hovering the Help button)
        int[] hr = getHintPanelRect();
        hintOpen = helpHover;
        if (hintOpen) {
            drawHintPanel(hr[0], hr[1]);
        }

        super.drawGuiContainerForegroundLayer(mouseX, mouseY);
    }

    private void drawHelpButton(boolean hover) {
        int bg = hover ? 0xFF2D2D2D : 0xFF4B4B4B;
        int border = hover ? rainbowRGB(0) : 0xFF2A2A2A;

        // background
        Gui.drawRect(HELP_X, HELP_Y, HELP_X + HELP_W, HELP_Y + HELP_H, bg);
        // border
        Gui.drawRect(HELP_X, HELP_Y, HELP_X + HELP_W, HELP_Y + 1, border);
        Gui.drawRect(HELP_X, HELP_Y + HELP_H - 1, HELP_X + HELP_W, HELP_Y + HELP_H, border);
        Gui.drawRect(HELP_X, HELP_Y, HELP_X + 1, HELP_Y + HELP_H, border);
        Gui.drawRect(HELP_X + HELP_W - 1, HELP_Y, HELP_X + HELP_W, HELP_Y + HELP_H, border);

        String label = "?Help";
        int textX = HELP_X + (HELP_W - this.fontRendererObj.getStringWidth(label)) / 2;
        int textY = HELP_Y + (HELP_H - 8) / 2;
        // draw with colored '?' like the screenshots
        this.fontRendererObj.drawString("\u00a7c?\u00a7fHelp", textX, textY, 0xFFFFFF);
    }

    private void drawHintPanel(int px, int py) {
        // background
        Gui.drawRect(px, py, px + HINT_W, py + HINT_H, 0xCC000000);

        // animated border (subtle)
        int border = rainbowRGB(0);
        Gui.drawRect(px, py, px + HINT_W, py + 1, border);
        Gui.drawRect(px, py + HINT_H - 1, px + HINT_W, py + HINT_H, border);
        Gui.drawRect(px, py, px + 1, py + HINT_H, border);
        Gui.drawRect(px + HINT_W - 1, py, px + HINT_W, py + HINT_H, border);

        int x = px + HINT_PAD;
        int y = py + HINT_PAD;

        ItemStack target = opener.getInputInventory().getStackInSlot(ContainerHexSocketOpener.SLOT_TARGET);
        ItemStack mat = opener.getInputInventory().getStackInSlot(ContainerHexSocketOpener.SLOT_CATALYST);


        // Try to use your custom format-style tags ("<grad>", "<rbw>", etc.) if supported by your renderer.
        if (USE_STYLE_TAGS) {
            drawHintPanelTextTags(x, y, target, mat);
            return;
        }

        // Safe fallback (vanilla FontRenderer)

        // Header: "Socket" gradient + "Help"
        int wSocket = drawGradientString(this.fontRendererObj, "Socket", x, y, 0x2E80FF, 0x00E5FF, true);
        this.fontRendererObj.drawStringWithShadow("Help", x + wSocket + 2, y, 0xFFFFFF);
        y += LINE_H;

        // Place item to open sockets (open sockets gradient)
        String pre = "Place item to ";
        this.fontRendererObj.drawStringWithShadow(pre, x, y, 0xC8C8C8);
        drawGradientString(this.fontRendererObj, "open sockets", x + this.fontRendererObj.getStringWidth(pre), y, 0x00C6FF, 0x00FFB3, true);
        y += LINE_H;

        if (target == null) {
            this.fontRendererObj.drawStringWithShadow("No target item", x, y, 0xFF5555);
            y += LINE_H;
        } else {
            int open = HexSocketAPI.getSocketsOpen(target);
            int max = HexSocketAPI.getSocketsMax(target);
            String sockets = "Sockets: " + open + " / " + ((max < 0) ? "\u221e" : Integer.toString(max));
            this.fontRendererObj.drawStringWithShadow(sockets, x, y, 0xDDDDDD);
            y += LINE_H;
        }

        // Cost line
        this.fontRendererObj.drawStringWithShadow("Cost: ", x, y, 0xAFAFAF);
        int off = this.fontRendererObj.getStringWidth("Cost: ");
        int w = drawGradientString(this.fontRendererObj, "XP levels", x + off, y, 0x00C6FF, 0x0072FF, true);
        this.fontRendererObj.drawStringWithShadow(" + ", x + off + w, y, 0xAFAFAF);
        this.fontRendererObj.drawStringWithShadow("1 material", x + off + w + this.fontRendererObj.getStringWidth(" + "), y, 0xFF55FF);
        y += LINE_H;

        // Rarity hint
        this.fontRendererObj.drawStringWithShadow("Rarer material: ", x, y, 0x777777);
        int off2 = this.fontRendererObj.getStringWidth("Rarer material: ");
        this.fontRendererObj.drawStringWithShadow("+Chance", x + off2, y, 0x55FF55);
        this.fontRendererObj.drawStringWithShadow(", ", x + off2 + this.fontRendererObj.getStringWidth("+Chance"), y, 0x777777);
        this.fontRendererObj.drawStringWithShadow("-Cost", x + off2 + this.fontRendererObj.getStringWidth("+Chance, "), y, 0x55FFFF);
        y += LINE_H;

        // Current material + numbers (if present)
        CatalystInfo info = getMaterialInfo(mat);
        String curName = info.displayName;
        int curCol = info.color;
        float chance = info.successChance;
        float costMult = info.costMult;

        this.fontRendererObj.drawStringWithShadow("Current: ", x, y, 0xBBBBBB);
        int off3 = this.fontRendererObj.getStringWidth("Current: ");
        if (mat == null) {
            this.fontRendererObj.drawStringWithShadow("None", x + off3, y, 0xFF5555);
        } else {
            this.fontRendererObj.drawStringWithShadow(curName, x + off3, y, curCol);
        }
        y += LINE_H;

        // Chance / Cost only if a material is in the slot
        if (mat != null) {
            int pctChance = Math.round(chance * 100f);
            int pctCost = Math.round(costMult * 100f);
            this.fontRendererObj.drawStringWithShadow("Chance: ", x, y, 0x777777);
            this.fontRendererObj.drawStringWithShadow(pctChance + "%", x + this.fontRendererObj.getStringWidth("Chance: "), y, 0x55FF55);

            int ox = x + 110;
            this.fontRendererObj.drawStringWithShadow("Cost: ", ox, y, 0x777777);
            this.fontRendererObj.drawStringWithShadow(pctCost + "%", ox + this.fontRendererObj.getStringWidth("Cost: "), y, 0x55FFFF);
            y += LINE_H;
        }

        // Materials list (two lines)
        this.fontRendererObj.drawStringWithShadow("Materials:", x, y, 0xBBBBBB);
        y += LINE_H;

        // Line 1
        int xx = x;
        xx += drawGradientString(this.fontRendererObj, "Iron", xx, y, 0xB5B5B5, 0xFFFFFF, true);
        this.fontRendererObj.drawStringWithShadow(" / ", xx, y, 0x777777);
        xx += this.fontRendererObj.getStringWidth(" / ");
        xx += drawGradientString(this.fontRendererObj, "Gold", xx, y, 0xFFAA00, 0xFFFF55, true);
        this.fontRendererObj.drawStringWithShadow(" / ", xx, y, 0x777777);
        xx += this.fontRendererObj.getStringWidth(" / ");
        xx += drawGradientString(this.fontRendererObj, "Diamond", xx, y, 0x55FFFF, 0x00C6FF, true);
        y += LINE_H;

        // Line 2
        xx = x;
        xx += drawGradientString(this.fontRendererObj, "Emerald", xx, y, 0x55FF55, 0x00FFB3, true);
        this.fontRendererObj.drawStringWithShadow(" / ", xx, y, 0x777777);
        xx += this.fontRendererObj.getStringWidth(" / ");
        xx += drawRainbowString(this.fontRendererObj, "Nether Star", xx, y, 0xFFFFFFFF, true);
        y += LINE_H;

        // Tip
        this.fontRendererObj.drawStringWithShadow("Tip: Hold ", x, y, 0x777777);
        int tipOff = this.fontRendererObj.getStringWidth("Tip: Hold ");
        this.fontRendererObj.drawStringWithShadow("Shift", x + tipOff, y, 0xFFFF55);
        this.fontRendererObj.drawStringWithShadow(" to show the ", x + tipOff + this.fontRendererObj.getStringWidth("Shift"), y, 0x777777);
        this.fontRendererObj.drawStringWithShadow("Socket", x + tipOff + this.fontRendererObj.getStringWidth("Shift to show the "), y, 0xFF55FF);
        this.fontRendererObj.drawStringWithShadow(" tab", x + tipOff + this.fontRendererObj.getStringWidth("Shift to show the Socket"), y, 0x777777);
        y += LINE_H;

        this.fontRendererObj.drawStringWithShadow("Glowing slot = material slot", x, y, 0x666666);
    }



    /**
     * Help panel text rendered using your format-style tags.
     * If your renderer doesn't support tags in GUIs, set USE_STYLE_TAGS=false to fall back.
     */
    private void drawHintPanelTextTags(int x, int yStart, ItemStack target, ItemStack mat) {
        int y = yStart;

        // Header (gradient + pop)
        // Animated header (scrolling gradient)
        this.fontRendererObj.drawStringWithShadow("<grad #2E80FF #00E5FF scroll=0.25>§lSocket</grad> §f§lHelp", x, y, 0xFFFFFF);
        y += LINE_H;

        // Instruction line
        // Animated highlight for the key phrase
        this.fontRendererObj.drawStringWithShadow("§7Place item to <grad #00C6FF #00FFB3 scroll=0.22>open sockets</grad>", x, y, 0xFFFFFF);
        y += LINE_H;

        // Target status
        if (target == null) {
            this.fontRendererObj.drawStringWithShadow("§cNo target item", x, y, 0xFFFFFF);
            y += LINE_H;
        } else {
            int open = HexSocketAPI.getSocketsOpen(target);
            int max  = HexSocketAPI.getSocketsMax(target);
            String sockets = "§fSockets: §7" + open + " / " + ((max < 0) ? "∞" : Integer.toString(max));
            this.fontRendererObj.drawStringWithShadow(sockets, x, y, 0xFFFFFF);
            y += LINE_H;
        }

        // Cost line (styled)
        this.fontRendererObj.drawStringWithShadow(
                "§7Cost: <grad #00C6FF #0072FF scroll=0.20>XP levels</grad> §7+ <grad #FF55FF #FF99FF scroll=0.18>1 material</grad>",
                x, y, 0xFFFFFF);
        y += LINE_H;

        // Rarity hint
        this.fontRendererObj.drawStringWithShadow("§8Rarer material: §a+Chance§8, §b-Cost", x, y, 0xFFFFFF);
        y += LINE_H;

        // Current material
        this.fontRendererObj.drawStringWithShadow("§7Current: ", x, y, 0xFFFFFF);
        int off = this.fontRendererObj.getStringWidth("§7Current: ");
        if (mat == null) {
            this.fontRendererObj.drawStringWithShadow("§cNone", x + off, y, 0xFFFFFF);
        } else {
            CatalystInfo info = getMaterialInfo(mat);
            this.fontRendererObj.drawStringWithShadow(info.displayName, x + off, y, info.color);
        }
        y += LINE_H;

        // Chance/Cost line when material present
        if (mat != null) {
            CatalystInfo info2 = getMaterialInfo(mat);
            float chance = info2.successChance;
            float costMult = info2.costMult;
            int pctChance = Math.round(chance * 100f);
            int pctCost = Math.round(costMult * 100f);

            String line = "§8Chance: §a" + pctChance + "%   §8Cost: §b" + pctCost + "%";
            this.fontRendererObj.drawStringWithShadow(line, x, y, 0xFFFFFF);
            y += LINE_H;
        }

        // Materials label + 2 lines
        this.fontRendererObj.drawStringWithShadow("§7Materials:", x, y, 0xFFFFFF);
        y += LINE_H;

        // Animated materials (scrolling gradients)
        this.fontRendererObj.drawStringWithShadow(
                "<grad #B5B5B5 #FFFFFF scroll=0.12>Iron</grad> §8/ <grad #FFAA00 #FFFF55 scroll=0.14>Gold</grad> §8/ <grad #55FFFF #00C6FF scroll=0.16>Diamond</grad>",
                x, y, 0xFFFFFF);
        y += LINE_H;

        this.fontRendererObj.drawStringWithShadow(
                "<grad #55FF55 #00FFB3 scroll=0.16>Emerald</grad> §8/ <pulse amp=0.65 speed=0.9><rbw cycles=1.2 speed=0.55>Nether Star</rbw></pulse>",
                x, y, 0xFFFFFF);
        y += LINE_H;

        // Tip
        this.fontRendererObj.drawStringWithShadow("§8Tip: Hold §eShift§8 to show the <grad #FF55FF #FF99FF scroll=0.16>Socket</grad>§8 tab", x, y, 0xFFFFFF);
        y += LINE_H;

        this.fontRendererObj.drawStringWithShadow("§8Glowing slot = material slot", x, y, 0xFFFFFF);
    }

    private int[] getHintPanelRect() {
        int py = HELP_Y - 6;

        // Always place the help panel on the LEFT side of the GUI.
        // If it would go off-screen, clamp it to the screen edge (still left-side anchored).
        int screenX = this.guiLeft - HINT_W - HINT_GAP;
        if (screenX < 2) screenX = 2;
        int px = screenX - this.guiLeft;

        // Clamp vertically within screen.
        int screenY = this.guiTop + py;
        if (screenY < 2) {
            py += (2 - screenY);
        } else if (screenY + HINT_H > this.height - 2) {
            py -= (screenY + HINT_H) - (this.height - 2);
        }

        return new int[]{px, py, px + HINT_W, py + HINT_H};
    }

    private static boolean isMouseOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private static int rainbowRGB(long offsetMs) {
        return rainbowRGB(Minecraft.getSystemTime() + offsetMs, 3500L);
    }

    /** Returns 0xAARRGGBB */
    private static int rainbowRGB(long timeMs, long periodMs) {
        float hue = (timeMs % periodMs) / (float) periodMs;
        int rgb = java.awt.Color.HSBtoRGB(hue, 0.95f, 1.0f);
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    /** Draws a per-character gradient string. Returns drawn width. */
    private static int drawGradientString(FontRenderer fr, String text, int x, int y, int c1, int c2, boolean shadow) {
        if (text == null || text.isEmpty()) return 0;
        int len = text.length();
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;

        int dx = 0;
        for (int i = 0; i < len; i++) {
            char ch = text.charAt(i);
            float t = (len <= 1) ? 0f : (i / (float) (len - 1));
            int r = (int) (r1 + (r2 - r1) * t);
            int g = (int) (g1 + (g2 - g1) * t);
            int b = (int) (b1 + (b2 - b1) * t);
            int col = 0xFF000000 | (r << 16) | (g << 8) | b;

            String s = String.valueOf(ch);
            if (shadow) fr.drawStringWithShadow(s, x + dx, y, col);
            else fr.drawString(s, x + dx, y, col);

            dx += fr.getCharWidth(ch);
        }
        return dx;
    }

    /** Animated rainbow string. Returns drawn width. */
    private static int drawRainbowString(FontRenderer fr, String text, int x, int y, int fallback, boolean shadow) {
        if (text == null || text.isEmpty()) return 0;
        long now = Minecraft.getSystemTime();
        int dx = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int col = rainbowRGB(now + (i * 120L), 2400L);
            if (shadow) fr.drawStringWithShadow(String.valueOf(ch), x + dx, y, col);
            else fr.drawString(String.valueOf(ch), x + dx, y, col);
            dx += fr.getCharWidth(ch);
        }
        return dx;
    }

    // ─────────────────────────────────────────────────────────────
    // Slot lookup + Material (Catalyst) info helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * In 1.7.10 containers, the "slot index" constants are often the *IInventory* indices (0..n)
     * and do NOT always match the Container slotList indices once player inventory slots are added.
     *
     * This scans for the slot that belongs to the opener input inventory.
     */
    private Slot findInputSlot(int inputInvIndex) {
        for (Object o : this.inventorySlots.inventorySlots) {
            if (!(o instanceof Slot)) continue;
            Slot s = (Slot) o;
            if (s.inventory == opener.getInputInventory() && s.getSlotIndex() == inputInvIndex) {
                return s;
            }
        }
        return null;
    }

    /** Vanilla material set (what you described: iron/gold/diamond/emerald/nether star). */
    private static boolean isVanillaMaterial(ItemStack st) {
        if (st == null) return false;
        if (st.getItem() == Items.iron_ingot) return true;
        if (st.getItem() == Items.gold_ingot) return true;
        if (st.getItem() == Items.diamond) return true;
        if (st.getItem() == Items.emerald) return true;
        if (st.getItem() == Items.nether_star) return true;
        return false;
    }

    /**
     * "Valid material" = whatever the server container accepts *plus* the vanilla material set.
     * This is purely for client-side button enabling + help display.
     */
    private static boolean isValidMaterial(ItemStack st) {
        if (st == null) return false;
        try {
            // If your server container exposes the check, prefer it.
            if (ContainerHexSocketOpener.isCatalyst(st)) return true;
        } catch (Throwable ignored) {
        }
        return isVanillaMaterial(st);
    }

    private static final class CatalystInfo {
        final String displayName;
        final int color;          // 0xRRGGBB
        final float successChance; // 0..1
        final float costMult;      // 0..?

        CatalystInfo(String displayName, int color, float successChance, float costMult) {
            this.displayName = displayName;
            this.color = color;
            this.successChance = successChance;
            this.costMult = costMult;
        }
    }

    /**
     * These GUI-only values used to be provided by ContainerHexSocketOpener helper methods.
     * Your current ContainerHexSocketOpener source doesn't have them, so we provide a safe fallback.
     *
     * If you later re-add those container helpers, this will auto-use them via reflection.
     */
    private static CatalystInfo getMaterialInfo(ItemStack mat) {
        if (mat == null) {
            return new CatalystInfo("None", 0xFF5555, 0f, 1f);
        }

        // ── Try to read the original helper methods if they exist in your workspace/jar ──
        String dn = reflectString(ContainerHexSocketOpener.class, "getCatalystDisplayName", mat);
        Integer col = reflectInt(ContainerHexSocketOpener.class, "getCatalystColor", mat);
        Float ch = reflectFloat(ContainerHexSocketOpener.class, "getCatalystSuccessChance", mat);
        Float cm = reflectFloat(ContainerHexSocketOpener.class, "getCatalystCostMult", mat);
        if (dn != null && col != null && ch != null && cm != null) {
            return new CatalystInfo(dn, col.intValue(), ch.floatValue(), cm.floatValue());
        }

        // ── Fallback mapping (kept intentionally simple + readable) ──
        if (mat.getItem() == Items.iron_ingot) {
            return new CatalystInfo("Iron", 0xC8C8C8, 0.55f, 1.00f);
        }
        if (mat.getItem() == Items.gold_ingot) {
            return new CatalystInfo("Gold", 0xFFAA00, 0.65f, 0.90f);
        }
        if (mat.getItem() == Items.diamond) {
            return new CatalystInfo("Diamond", 0x55FFFF, 0.75f, 0.80f);
        }
        if (mat.getItem() == Items.emerald) {
            return new CatalystInfo("Emerald", 0x55FF55, 0.85f, 0.70f);
        }
        if (mat.getItem() == Items.nether_star) {
            // best-in-slot by default
            return new CatalystInfo("Nether Star", 0xFFFFFF, 0.95f, 0.60f);
        }

        // Unknown material (custom item, etc.)
        String name = mat.getDisplayName();
        if (name == null || name.isEmpty()) name = "Material";
        return new CatalystInfo(name, 0xFFFFFF, 0.75f, 1.00f);
    }

    private static String reflectString(Class<?> cls, String method, ItemStack arg) {
        try {
            Method m = cls.getDeclaredMethod(method, ItemStack.class);
            m.setAccessible(true);
            Object v = m.invoke(null, arg);
            return (v instanceof String) ? (String) v : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Integer reflectInt(Class<?> cls, String method, ItemStack arg) {
        try {
            Method m = cls.getDeclaredMethod(method, ItemStack.class);
            m.setAccessible(true);
            Object v = m.invoke(null, arg);
            return (v instanceof Integer) ? (Integer) v : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Float reflectFloat(Class<?> cls, String method, ItemStack arg) {
        try {
            Method m = cls.getDeclaredMethod(method, ItemStack.class);
            m.setAccessible(true);
            Object v = m.invoke(null, arg);
            if (v instanceof Float) return (Float) v;
            if (v instanceof Double) return ((Double) v).floatValue();
            return null;
        } catch (Throwable t) {
            return null;
        }
    }


    /** Button styled like an enchanting option bar. */
    @SideOnly(Side.CLIENT)
    private static class OptionButton extends GuiButton {
        public OptionButton(int id, int x, int y, int w, int h) {
            super(id, x, y, w, h, "");
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            if (!this.visible) return;

            FontRenderer fr = mc.fontRenderer;
            boolean hover = mouseX >= this.xPosition && mouseY >= this.yPosition && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;

            int bg;
            int border;
            int textCol;
            if (!this.enabled) {
                bg = 0xFF1A1A1A;
                border = 0xFF2A2A2A;
                textCol = 0xFF666666;
            } else if (hover) {
                bg = 0xFF2A2A2A;
                border = 0xFF00C6FF;
                textCol = 0xFFFFFFFF;
            } else {
                bg = 0xFF202020;
                border = 0xFF2A2A2A;
                textCol = 0xFFDDDDDD;
            }

            Gui.drawRect(this.xPosition, this.yPosition, this.xPosition + this.width, this.yPosition + this.height, bg);
            Gui.drawRect(this.xPosition, this.yPosition, this.xPosition + this.width, this.yPosition + 1, border);
            Gui.drawRect(this.xPosition, this.yPosition + this.height - 1, this.xPosition + this.width, this.yPosition + this.height, border);
            Gui.drawRect(this.xPosition, this.yPosition, this.xPosition + 1, this.yPosition + this.height, border);
            Gui.drawRect(this.xPosition + this.width - 1, this.yPosition, this.xPosition + this.width, this.yPosition + this.height, border);

            int tx = this.xPosition + 8;
            int ty = this.yPosition + (this.height - 8) / 2;
            fr.drawString(this.displayString, tx, ty, textCol);
        }
    }
}