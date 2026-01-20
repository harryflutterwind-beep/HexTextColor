package com.example.examplemod.client.gui;

import com.example.examplemod.gui.ContainerHexSocketStation;
import com.example.examplemod.api.HexSocketAPI;
import com.example.examplemod.item.ItemGemIcons;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.lwjgl.input.Keyboard;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Client GUI for Hex Socket Station
 * - Uses vanilla Anvil texture as the base
 * - Adds a "socket ready" visual indicator + gem overlay preview
 * - Adds animated, glowing "orb" decorations (uses the currently inserted gem item icon)
 *
 * Minecraft 1.7.10
 */
public class GuiHexSocketStation extends GuiContainer {

    // ---------------------------------------------------------------------
    // Debug overlay (hold SHIFT) — client-side view of what the GUI/container sees.
    // This helps diagnose: "item has sockets in tooltip, but station says no open socket".
    // ---------------------------------------------------------------------
    private long dbgNextCalcMs = 0L;
    private String dbgTargetId = "-";
    private String dbgGemId = "-";
    private String dbgOutId = "-";
    private boolean dbgHasTag = false;
    private boolean dbgHasHexGems = false;
    private int dbgMax = 0;
    private int dbgOpen = 0;
    private int dbgFilled = 0;
    private int dbgFree = 0;
    private int dbgGemsListCount = 0;
    private int dbgBonusesListCount = 0;

    private String dbgGemVariant = "-";
    private List<String> dbgKeyCandidates = new ArrayList<String>();
    private boolean dbgPreviewOk = false;
    private String dbgPreviewKey = "-";
    private String dbgPreviewNote = "";


    // Clean texture (no '+' and no top-left panel) so nothing clips the slots or overlaps.
    private final ResourceLocation GUI_TEX = new ResourceLocation("hexcolorcodes", "textures/gui/hex_socket_station.png");

    // ---------------------------------------------------------------------
    // Decorative “atom” icons (client-only)
    //
    // These are purely cosmetic and do not affect slot interaction.
    // Each icon is an animated vertical strip (8 frames). We draw one 64x64 frame at a time.
    // If any texture lives in a different folder (ex: textures/pills/...), change the path here.
    // ---------------------------------------------------------------------

    // Nucleus (center) — your swirly animated orb
    private final ResourceLocation TEX_SWIRLY_NUCLEUS = new ResourceLocation("hexcolorcodes", "textures/gems/orb_gem_swirly_loop.png");

    // Orbiters (electrons)
    private final ResourceLocation TEX_NATURE        = new ResourceLocation("hexcolorcodes", "textures/gems/orb_gem_green_nature_64_anim_8f.png");
    private final ResourceLocation TEX_CHAOS         = new ResourceLocation("hexcolorcodes", "textures/gems/orb_gem_chaoticSphere_anim_8f_64x516.png");
    private final ResourceLocation TEX_FRACTURED     = new ResourceLocation("hexcolorcodes", "textures/gems/orb_gem_fractured_anim_8f_64x516.png");
    private final ResourceLocation TEX_LIGHT         = new ResourceLocation("hexcolorcodes", "textures/gems/orb_gem_gold_solar_64_anim_8f.png");
    private final ResourceLocation TEX_DARK_FIRE_PILL= new ResourceLocation("hexcolorcodes", "textures/gems/pill_dark_fire_face_64_anim.png");

    private final int ORB_FRAMES   = 8;
    private final int ORB_FRAME_PX = 64;
    private final int SHEET_H_512  = 512;
    private final int SHEET_H_516  = 516;

    // ---------------------------------------------------------------------
    // Atom motion controls (tweak to taste)
    // ---------------------------------------------------------------------
    private final float ATOM_OMEGA        = 0.42f;   // radians/sec (main orbit speed)
    private final float ATOM_OMEGA_WOBBLE = 0.012f;  // tiny speed wobble (keeps it from feeling robotic)
    private final float ATOM_ORBIT_SCALE  = 1.18f;   // overall orbit size (bigger = wider)
    private final float ATOM_PRECESS_RATE = 0.20f;   // orbit plane precession speed (slow)

    private final int   NUCLEUS_SIZE      = 18;
    private final float NUCLEUS_PULSE     = 0.06f;   // subtle pulse amount

    // Invisible “orb zone” (top-left). Icons are clamped here so they never spill into slots.
    // Since the texture is clean now, we can give this a bit of room.
    private final int ORB_PANEL_X = 2;
    private final int ORB_PANEL_Y = 10;
    private final int ORB_PANEL_W = 96;
    private final int ORB_PANEL_H = 56;

    // Ring planes (3 crossing rings like a classic atom icon).
    private class RingDef {
        final float tiltX;     // radians
        final float rotZ;      // radians
        final float rx;        // base radius (x axis)
        final float rz;        // base radius (depth axis)
        final float precessAmp;// radians
        final float speedMul;  // per-ring speed multiplier

        RingDef(float tiltX, float rotZ, float rx, float rz, float precessAmp, float speedMul) {
            this.tiltX = tiltX;
            this.rotZ = rotZ;
            this.rx = rx;
            this.rz = rz;
            this.precessAmp = precessAmp;
            this.speedMul = speedMul;
        }
    }

    private final RingDef[] RINGS = new RingDef[] {
            // tiltX,  rotZ,   rx,    rz,   precessAmp, speedMul
            new RingDef( 0.00f, 0.00f, 24.0f, 10.0f, 0.22f, 1.00f),  // horizontal
            new RingDef( 1.05f, 0.92f, 21.5f,  9.0f, 0.20f, 1.03f),  // tilted
            new RingDef(-1.05f,-0.78f, 23.0f,  9.5f, 0.20f, 0.97f)   // tilted opposite
    };

    private class ElectronDef {
        final ResourceLocation tex;
        final int sheetH;
        final int baseSize;
        final int ring;
        final float phase;      // radians
        final float wobble;     // subtle motion wobble per-orb
        final float animFps;    // frame advance rate (frames/sec-ish)

        ElectronDef(ResourceLocation tex, int sheetH, int baseSize, int ring, float phase, float wobble, float animFps) {
            this.tex = tex;
            this.sheetH = sheetH;
            this.baseSize = baseSize;
            this.ring = ring;
            this.phase = phase;
            this.wobble = wobble;
            this.animFps = animFps;
        }
    }

    // Orbiting icons. Add more here.
    // - ring: 0..2 chooses which orbit plane they ride on
    // - phase: starting position around the orbit (radians)
    private final ElectronDef[] ELECTRONS = new ElectronDef[] {
            new ElectronDef(TEX_NATURE,         SHEET_H_512, 14, 0, 0.00f, 1.00f, 5.6f),
            new ElectronDef(TEX_CHAOS,          SHEET_H_516, 16, 1, 1.26f, 0.95f, 6.8f),
            new ElectronDef(TEX_FRACTURED,      SHEET_H_516, 14, 2, 2.51f, 1.05f, 6.0f),
            new ElectronDef(TEX_LIGHT,          SHEET_H_516, 13, 1, 3.77f, 0.90f, 6.2f),
            new ElectronDef(TEX_DARK_FIRE_PILL, SHEET_H_512, 15, 0, 5.03f, 0.85f, 5.4f)
    };

    // Slot indices from ContainerHexSocketStation
    private final int SLOT_TARGET = 0;
    private final int SLOT_GEM    = 1;
    private final int SLOT_OUTPUT = 2;

    // NBT keys (must match HexSocketCommand / HexSocketAPI)
    private final String NBT_ROOT = "HexGems";
    private final String K_MAX    = "SocketsMax";
    private final String K_OPEN   = "SocketsOpen";
    private final String K_GEMS   = "Gems";

    // Slot pixel coords (match ContainerHexSocketStation)
    private final int TARGET_X = 27,  TARGET_Y = 47;
    private final int GEM_X    = 76,  GEM_Y    = 47;
    private final int OUT_X    = 134, OUT_Y    = 47;

    // Shift-revealed tab button (top-right)
    private static final int BTN_TAB_OPEN = 4101;
    private static final int TAB_W = 46;
    private static final int TAB_H = 14;
    private static final String TAB_LABEL = "Open";
    // Where the tab navigates (handled server-side by your command)
    private static final String TAB_CMD = "/hexsocket opener";
    private GuiButton btnTabOpen;


    public GuiHexSocketStation(net.minecraft.entity.player.EntityPlayer player) {
        super(new ContainerHexSocketStation(player));
        this.xSize = 176;
        this.ySize = 166;
    }

    @Override
    public void initGui() {
        super.initGui();

        // Top-right tab button (protrudes above the GUI)
        int x = this.guiLeft + this.xSize - TAB_W - 4;
        int y = this.guiTop - TAB_H + 4;

        // Custom-drawn button so it looks like a tab, not a vanilla button
        btnTabOpen = new GuiButton(BTN_TAB_OPEN, x, y, TAB_W, TAB_H, TAB_LABEL) {
            @Override
            public void drawButton(Minecraft mc, int mouseX, int mouseY) {
                if (!this.visible) return;

                boolean hover = mouseX >= this.xPosition && mouseY >= this.yPosition
                        && mouseX < this.xPosition + this.width
                        && mouseY < this.yPosition + this.height;

                int bg = hover ? 0xFF3A3A3A : 0xFF2A2A2A;
                int br = 0xFF6B2FB6; // purple border

                // Background
                GuiHexSocketStation.this.drawRect(this.xPosition, this.yPosition, this.xPosition + this.width, this.yPosition + this.height, bg);
                // Border
                GuiHexSocketStation.this.drawRect(this.xPosition, this.yPosition, this.xPosition + this.width, this.yPosition + 1, br);
                GuiHexSocketStation.this.drawRect(this.xPosition, this.yPosition + this.height - 1, this.xPosition + this.width, this.yPosition + this.height, br);
                GuiHexSocketStation.this.drawRect(this.xPosition, this.yPosition, this.xPosition + 1, this.yPosition + this.height, br);
                GuiHexSocketStation.this.drawRect(this.xPosition + this.width - 1, this.yPosition, this.xPosition + this.width, this.yPosition + this.height, br);

                int col = hover ? 0xFFFFE08A : 0xFFE0E0E0;
                int lw = GuiHexSocketStation.this.fontRendererObj.getStringWidth(this.displayString);
                int tx = this.xPosition + (this.width - lw) / 2;
                int ty = this.yPosition + (this.height - 8) / 2;
                GuiHexSocketStation.this.fontRendererObj.drawString(this.displayString, tx, ty, col, false);
            }
        };

        boolean show = isLeftShiftDown() && !isDebugHeld();
        btnTabOpen.visible = show;
        btnTabOpen.enabled = show;
        this.buttonList.add(btnTabOpen);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (btnTabOpen != null) {
            boolean show = isLeftShiftDown() && !isDebugHeld();
            btnTabOpen.visible = show;
            btnTabOpen.enabled = show;

            // keep it anchored to top-right even if GUI recenters
            btnTabOpen.xPosition = this.guiLeft + this.xSize - TAB_W - 4;
            btnTabOpen.yPosition = this.guiTop - TAB_H + 4;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button != null && button.id == BTN_TAB_OPEN) {
            if (this.mc != null && this.mc.thePlayer != null) {
                this.mc.thePlayer.sendChatMessage(TAB_CMD);
            }
            return;
        }
        super.actionPerformed(button);
    }


    private ItemStack getSlotStack(int idx) {
        try {
            Slot s = (Slot) this.inventorySlots.inventorySlots.get(idx);
            return s != null ? s.getStack() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private float timeSeconds() {
        // stable animation timer
        return (Minecraft.getSystemTime() % 1000000L) / 1000.0f;
    }

    private boolean isLeftShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT);
    }

    private boolean isDebugAllowed() {
        try {
            if (this.inventorySlots instanceof ContainerHexSocketStation) {
                return ((ContainerHexSocketStation) this.inventorySlots).isDebugAllowed();
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean isDebugHeld() {
        // Debug overlay: only show for OPs/admins, and only when BOTH Left SHIFT and Left CTRL are held.
        if (!isDebugAllowed()) return false;
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && Keyboard.isKeyDown(Keyboard.KEY_LCONTROL);
    }

    private boolean isShiftHeld() {
        // kept for any future use; includes either shift
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    /** Recompute debug snapshot at most ~5x/sec while SHIFT is held. */
    private void refreshDebugCache(ItemStack target, ItemStack gem, ItemStack out) {
        long now = Minecraft.getSystemTime();
        if (now < dbgNextCalcMs) return;
        dbgNextCalcMs = now + 200L;

        // IDs
        dbgTargetId = (target == null) ? "-" : String.valueOf(Item.itemRegistry.getNameForObject(target.getItem()));
        dbgGemId    = (gem == null) ? "-" : String.valueOf(Item.itemRegistry.getNameForObject(gem.getItem()));
        dbgOutId    = (out == null) ? "-" : String.valueOf(Item.itemRegistry.getNameForObject(out.getItem()));

        // NBT + socket counts (client-side call to your API)
        dbgHasTag = (target != null && target.hasTagCompound());
        dbgHasHexGems = false;
        dbgGemsListCount = 0;
        dbgBonusesListCount = 0;

        dbgMax = 0;
        dbgOpen = 0;
        dbgFilled = 0;
        dbgFree = 0;

        if (target != null) {
            try {
                dbgMax = HexSocketAPI.getSocketsMax(target);
            } catch (Throwable t) { dbgMax = 0; }

            try {
                dbgOpen = HexSocketAPI.getSocketsOpen(target);
            } catch (Throwable t) { dbgOpen = 0; }

            try {
                dbgFilled = HexSocketAPI.getSocketsFilled(target);
            } catch (Throwable t) { dbgFilled = 0; }

            dbgFree = dbgOpen - dbgFilled;

            if (target.hasTagCompound()) {
                NBTTagCompound tag = target.getTagCompound();
                if (tag != null && tag.hasKey("HexGems", 10)) {
                    dbgHasHexGems = true;
                    NBTTagCompound hg = tag.getCompoundTag("HexGems");
                    if (hg != null) {
                        if (hg.hasKey("Gems", 9)) {
                            NBTTagList l = hg.getTagList("Gems", 8);
                            dbgGemsListCount = (l != null) ? l.tagCount() : 0;
                        }
                        if (hg.hasKey("GemBonuses", 9)) {
                            NBTTagList l = hg.getTagList("GemBonuses", 10);
                            dbgBonusesListCount = (l != null) ? l.tagCount() : 0;
                        }
                    }
                }
            }
        }

        // Gem variant + key candidates
        dbgGemVariant = "-";
        dbgKeyCandidates.clear();
        dbgPreviewOk = false;
        dbgPreviewKey = "-";
        dbgPreviewNote = "";

        if (gem != null && gem.getItem() instanceof ItemGemIcons) {
            int meta = gem.getItemDamage();
            if (meta < 0) meta = 0;
            if (meta >= ItemGemIcons.VARIANTS.length) meta = ItemGemIcons.VARIANTS.length - 1;
            String raw = ItemGemIcons.VARIANTS[meta];
            if (raw == null) raw = "";
            raw = raw.trim();
            dbgGemVariant = raw;

            dbgKeyCandidates.addAll(gemKeyCandidatesFromStackDebug(gem));

            // Try a client-side preview using the same API call the container uses.
            if (target != null && !dbgKeyCandidates.isEmpty()) {
                for (int i = 0; i < dbgKeyCandidates.size(); i++) {
                    String k = dbgKeyCandidates.get(i);
                    try {
                        ItemStack c = target.copy();
                        boolean ok = HexSocketAPI.socketGem(c, k);
                        if (ok) {
                            dbgPreviewOk = true;
                            dbgPreviewKey = k;
                            break;
                        }
                    } catch (Throwable t) {
                        // ignore and keep trying
                    }
                }
                if (!dbgPreviewOk) {
                    dbgPreviewNote = "socketGem(copy,key)=false for all candidates";
                }
            } else if (target == null) {
                dbgPreviewNote = "no target in slot 0";
            }
        } else if (gem != null) {
            dbgPreviewNote = "slot 1 item is not ItemGemIcons";
        }
    }

    /** Draw a small debug panel when SHIFT is held.
     *  Auto-places to the LEFT of the GUI if there is room, otherwise to the RIGHT.
     *  (Coordinates here are relative to guiLeft/guiTop, so negative X draws outside the panel.)
     */
    private void drawDebugPanel(int mouseX, int mouseY, ItemStack target, ItemStack gem, ItemStack out) {
        int w = 128;
        int h = 92;
        int pad = 6;
        int y = 18;

        // Prefer left, but if it would go off-screen, push to the right side.
        boolean canLeft  = (this.guiLeft - w - pad) >= 0;
        boolean canRight = (this.guiLeft + this.xSize + w + pad) <= this.width;

        int x;
        if (canLeft) {
            x = -w - pad;
        } else if (canRight) {
            x = this.xSize + pad;
        } else {
            // fallback: keep it inside if the screen is too small (rare)
            x = 6;
        }

        // Background + border (debug styling)
        int bg = 0xAA000000;
        int br = 0xFFFF5A2A; // orange border
        int br2 = 0xFF8A1F00; // inner accent

        drawRect(x, y, x + w, y + h, bg);

        // Outer border
        drawRect(x, y, x + w, y + 1, br);
        drawRect(x, y + h - 1, x + w, y + h, br);
        drawRect(x, y, x + 1, y + h, br);
        drawRect(x + w - 1, y, x + w, y + h, br);

        // Subtle inner border
        drawRect(x + 1, y + 1, x + w - 1, y + 2, br2);
        drawRect(x + 1, y + h - 2, x + w - 1, y + h - 1, br2);
        drawRect(x + 1, y + 1, x + 2, y + h - 1, br2);
        drawRect(x + w - 2, y + 1, x + w - 1, y + h - 1, br2);

        int yy = y + 4;
        int col = 0xE0E0E0;

        this.fontRendererObj.drawStringWithShadow("DBG (LSHIFT+LCTRL)", x + 4, yy, 0xFFF2D38A);
        yy += 10;

        this.fontRendererObj.drawStringWithShadow("slot0: " + ellipsize(dbgTargetId, 20), x + 4, yy, col); yy += 9;
        this.fontRendererObj.drawStringWithShadow("tag=" + (dbgHasTag ? "Y" : "N") + " HexGems=" + (dbgHasHexGems ? "Y" : "N"), x + 4, yy, col); yy += 9;

        String maxStr = (dbgMax < 0 ? "∞" : String.valueOf(dbgMax));
        this.fontRendererObj.drawStringWithShadow("max=" + maxStr + " open=" + dbgOpen, x + 4, yy, col); yy += 9;
        this.fontRendererObj.drawStringWithShadow("filled=" + dbgFilled + " free=" + dbgFree, x + 4, yy, col); yy += 9;

        this.fontRendererObj.drawStringWithShadow("Gems=" + dbgGemsListCount + " Bon=" + dbgBonusesListCount, x + 4, yy, col); yy += 9;

        this.fontRendererObj.drawStringWithShadow("slot1: " + ellipsize(dbgGemId, 20), x + 4, yy, col); yy += 9;
        if (gem != null) {
            this.fontRendererObj.drawStringWithShadow("var: " + ellipsize(dbgGemVariant, 18), x + 4, yy, col); yy += 9;
        } else {
            this.fontRendererObj.drawStringWithShadow("var: -", x + 4, yy, col); yy += 9;
        }

        // Preview status
        String outStr = (out == null ? "null" : "ok");
        String prevStr = (dbgPreviewOk ? "ok" : "fail");
        this.fontRendererObj.drawStringWithShadow("out=" + outStr + " prev=" + prevStr, x + 4, yy, col); yy += 9;

        if (dbgPreviewOk) {
            this.fontRendererObj.drawStringWithShadow("key: " + ellipsize(dbgPreviewKey, 18), x + 4, yy, 0xB8FFB8);
        } else {
            this.fontRendererObj.drawStringWithShadow(ellipsize(dbgPreviewNote, 22), x + 4, yy, 0xFFAAAA);
        }
    }


    /**
     * Draw a side panel for ORB preview (default), in the same spot as the debug panel.
     * Shows the inserted orb/pill icon + a couple useful tooltip lines.
     * Hold LEFT SHIFT to swap this panel into DEBUG.
     */
    private void drawOrbPanel(int mouseX, int mouseY, ItemStack target, ItemStack gem, ItemStack out) {
        int w = 128;
        int h = 92;
        int pad = 6;
        int y = 18;

        // Prefer left, but if it would go off-screen, push to the right side.
        boolean canLeft  = (this.guiLeft - w - pad) >= 0;
        boolean canRight = (this.guiLeft + this.xSize + w + pad) <= this.width;

        int x;
        if (canLeft) {
            x = -w - pad;
        } else if (canRight) {
            x = this.xSize + pad;
        } else {
            x = 6;
        }

        // Background + border (match opener help styling: dark glass + cyan rim)
        int bg = 0xAA000000;
        int br = 0xFF2FD6FF; // cyan border
        int br2 = 0xFF0B6CA8; // inner accent

        drawRect(x, y, x + w, y + h, bg);

        // Outer border
        drawRect(x, y, x + w, y + 1, br);
        drawRect(x, y + h - 1, x + w, y + h, br);
        drawRect(x, y, x + 1, y + h, br);
        drawRect(x + w - 1, y, x + w, y + h, br);

        // Subtle inner border
        drawRect(x + 1, y + 1, x + w - 1, y + 2, br2);
        drawRect(x + 1, y + h - 2, x + w - 1, y + h - 1, br2);
        drawRect(x + 1, y + 1, x + 2, y + h - 1, br2);
        drawRect(x + w - 2, y + 1, x + w - 1, y + h - 1, br2);

        int yy = y + 4;
        // Header (avoid overlap by wrapping if needed)
        boolean dbgAllowed = isDebugAllowed();

        String hdrLeftPlain = "ORB";
        String hdrLeftStyled = "<grad #00C6FF #00FFE5 scroll=0.12>ORB</grad>";

        String hdrRightPlain = "Hold LSHIFT+LCTRL for DBG";
        String hdrRightShortPlain = "LSHIFT+LCTRL: DBG";

        String hdrRightStyled = "<grad #B0B0B0 #FFFFFF scroll=0.05>Hold</grad> <grad #FFFFFF #B0B0B0 scroll=0.06>LSHIFT+LCTRL</grad> <grad #B0B0B0 #FFFFFF scroll=0.05>for</grad> <grad #FF4D4D #FFAA00 scroll=0.22>DBG</grad>";
        String hdrRightShortStyled = "<grad #FFFFFF #B0B0B0 scroll=0.06>LSHIFT+LCTRL:</grad> <grad #FF4D4D #FFAA00 scroll=0.22>DBG</grad>";

        this.fontRendererObj.drawStringWithShadow(hdrLeftStyled, x + 4, yy, 0xFFFFFF);

        if (dbgAllowed) {
            int leftW = this.fontRendererObj.getStringWidth(hdrLeftPlain);
            int rightW = this.fontRendererObj.getStringWidth(hdrRightPlain);
            int rightX = x + w - 4 - rightW;

            // If it would collide with the left header, push to next line
            if (rightX <= (x + 4 + leftW + 8)) {
                yy += 10;
                this.fontRendererObj.drawStringWithShadow(hdrRightShortStyled, x + 4, yy, 0xFFFFFF);
                yy += 12;
            } else {
                this.fontRendererObj.drawStringWithShadow(hdrRightStyled, rightX, yy, 0xFFFFFF);
                yy += 12;
            }
        } else {
            yy += 12;
        }

        if (gem == null) {
            this.fontRendererObj.drawStringWithShadow("<grad #00C6FF #00FFE5 scroll=0.18>Insert an orb/pill</grad>", x + 4, yy, 0xFFFFFF);
            yy += 10;

            // Light guidance
            if (target == null) {
                this.fontRendererObj.drawStringWithShadow("<grad #FFD000 #FF7A00 scroll=0.12>Tip:</grad> <grad #B0B0B0 #FFFFFF scroll=0.06>Place item first</grad>", x + 4, yy, 0xFFFFFF);
            } else {
                int open = getSocketsOpenClient(target);
                int filled = getSocketsFilledClient(target);
                int free = open - filled;
                if (free < 0) free = 0;
                this.fontRendererObj.drawStringWithShadow("<grad #B0B0B0 #FFFFFF scroll=0.06>Free sockets:</grad> <grad #33FF77 #00FFCC scroll=0.12>" + free + "</grad>", x + 4, yy, 0xFFFFFF);
            }
            return;
        }

        // Render the orb icon larger (32x32-ish)
        drawItemScaled(gem, x + 6, y + 20, 2.0f);

        // Name + a couple lines
        String orbName = getOrbNamePlain(gem);

        // Preview name should reflect the actual item name (output if present, otherwise the target).
        String previewName = null;
        if (out != null) previewName = out.getDisplayName();
        else if (target != null) previewName = target.getDisplayName();

        int textW = w - 52;

        if (previewName != null && previewName.length() > 0) {
            this.fontRendererObj.drawStringWithShadow(trimPx(previewName, textW), x + 44, y + 18, 0xFFFFFF);
        }
        if (orbName != null && orbName.length() > 0) {
            this.fontRendererObj.drawStringWithShadow(trimPx(orbName, textW), x + 44, y + 30, 0xFFFFFF);
        }

        String vHint = getOrbVariantHint(gem);
        String vHintPlain = plainForCompare(vHint);
        if (vHintPlain.length() > 0) {
            String previewPlain = plainForCompare(previewName);
            String orbPlain = plainForCompare(orbName);
            if (!vHintPlain.equalsIgnoreCase(previewPlain) && !vHintPlain.equalsIgnoreCase(orbPlain)) {
                this.fontRendererObj.drawStringWithShadow(trimPx(vHint, textW), x + 44, y + 40, 0x6EC6FF);
            }
        }

        // Always pull bonus lines from the GEM itself (not from the output/target),
        // otherwise weapons override the orb's rolled line with their own "Attack Damage" etc.
        ItemStack src = gem;
        List<String> lines = pickUsefulLines(src, 6);

        int ly = y + 52;
        int shown = 0;
        for (int i = 0; i < lines.size(); i++) {
            String li = lines.get(i);
            String liPlain = plainForCompare(li);
            if (liPlain.length() == 0) continue;
            if (liPlain.equalsIgnoreCase(plainForCompare(previewName))) continue;
            if (liPlain.equalsIgnoreCase(plainForCompare(orbName))) continue;
            if (liPlain.equalsIgnoreCase(vHintPlain)) continue;

            int col;
            if (shown == 0) col = 0x2F65FF;
            else if (shown == 1) col = 0x33FFAA;
            else col = 0xE066FF;
            this.fontRendererObj.drawStringWithShadow(trimPx(li, textW), x + 44, ly, col);
            ly += 10;
            shown++;
            if (shown >= 3) break;
            if (ly > y + h - 18) break;
        }

        // Small status line at bottom
        int open = (target != null ? getSocketsOpenClient(target) : 0);
        int filled = (target != null ? getSocketsFilledClient(target) : 0);
        int free = open - filled;
        if (free < 0) free = 0;

        String status;
        int scol;
        if (target == null) {
            status = "Place an item";
            scol = 0xB0B0B0;
        } else if (out != null) {
            status = "Preview ready (" + free + " free)";
            scol = 0xB8FFB8;
        } else {
            status = (free <= 0) ? "No open socket" : "Preview pending...";
            scol = (free <= 0) ? 0xFFAAAA : 0xB0B0B0;
        }
        this.fontRendererObj.drawStringWithShadow(ellipsize(status, 22), x + 4, y + h - 12, scol);
    }

    /** Render an ItemStack at GUI-local coords, scaled (for the side panel). */
    private void drawItemScaled(ItemStack stack, int guiLocalX, int guiLocalY, float scale) {
        if (stack == null) return;

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        GL11.glTranslatef(guiLocalX, guiLocalY, 300.0f);
        GL11.glScalef(scale, scale, 1.0f);

        RenderHelper.enableGUIStandardItemLighting();
        this.itemRender.renderItemAndEffectIntoGUI(this.fontRendererObj, this.mc.getTextureManager(), stack, 0, 0);
        this.itemRender.renderItemOverlayIntoGUI(this.fontRendererObj, this.mc.getTextureManager(), stack, 0, 0);
        RenderHelper.disableStandardItemLighting();

        GL11.glPopMatrix();
    }


    /** Same idea as the container: normalize ItemGemIcons.VARIANTS[meta] into key candidates. */
    private List<String> gemKeyCandidatesFromStackDebug(ItemStack gem) {
        ArrayList<String> out = new ArrayList<String>();
        if (gem == null || !(gem.getItem() instanceof ItemGemIcons)) return out;

        int meta = gem.getItemDamage();
        if (meta < 0) meta = 0;
        if (meta >= ItemGemIcons.VARIANTS.length) meta = ItemGemIcons.VARIANTS.length - 1;

        String rawOrig = ItemGemIcons.VARIANTS[meta];
        if (rawOrig == null) rawOrig = "";
        rawOrig = rawOrig.trim();

        String rawNoExt = rawOrig;
        if (rawNoExt.endsWith(".png")) rawNoExt = rawNoExt.substring(0, rawNoExt.length() - 4);

        // Use a set to avoid duplicates while preserving insertion order-ish.
        Set<String> set = new HashSet<String>();

        // 1) raw as-is
        if (!rawNoExt.isEmpty()) set.add(rawNoExt);
        if (!rawOrig.isEmpty()) set.add(rawOrig);

        // 2) force folder prefixes
        if (!rawNoExt.isEmpty()) {
            set.add("gems/" + rawNoExt);
            set.add("pills/" + rawNoExt);
        }
        if (!rawOrig.isEmpty()) {
            set.add("gems/" + rawOrig);
            set.add("pills/" + rawOrig);
        }

        // 3) strip folder prefix (in case API expects bare)
        if (rawNoExt.startsWith("gems/")) set.add(rawNoExt.substring("gems/".length()));
        if (rawNoExt.startsWith("pills/")) set.add(rawNoExt.substring("pills/".length()));
        if (rawOrig.startsWith("gems/")) set.add(rawOrig.substring("gems/".length()));
        if (rawOrig.startsWith("pills/")) set.add(rawOrig.substring("pills/".length()));

        for (String s : set) {
            if (s != null) {
                String t = s.trim();
                if (!t.isEmpty()) out.add(t);
            }
        }
        return out;
    }




    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // Title
        this.fontRendererObj.drawString("Hex Socket Station", 8, 6, 0x404040);

        ItemStack target = getSlotStack(SLOT_TARGET);
        ItemStack gem    = getSlotStack(SLOT_GEM);
        ItemStack out    = getSlotStack(SLOT_OUTPUT);


        // Orb name + primary bonus line inside the top "Orb:" bar (anvil-style)
        if (gem != null) {
            String orbName = getOrbNamePlain(gem);
            // Name bar coordinates tuned for the anvil texture used by this GUI.
            int barNameX = 52;
            int barNameY = 20;
            int barRightPad = 8;
            int barMaxW = (this.xSize - barRightPad) - barNameX;
            this.fontRendererObj.drawString(trimPx(orbName, barMaxW), barNameX, barNameY, 0x404040);

            // Top bar: show the output/target line (if any) AND the orb's rolled bonus line.
            int barLineX = 52;
            int barLineY = 30;
            int barRightPad2 = 8;
            int barMaxW2 = (this.xSize - barRightPad2) - barLineX;

            String line1 = "";
            if (out != null) {
                List<String> outLines = pickUsefulLines(out, 1);
                if (!outLines.isEmpty()) line1 = outLines.get(0);
            } else if (gem != null) {
                List<String> gemLines = pickUsefulLines(gem, 1);
                if (!gemLines.isEmpty()) line1 = gemLines.get(0);
            }

            String line2 = pickGemValueLine(gem);

            if (line1 != null && line1.length() > 0) {
                this.fontRendererObj.drawStringWithShadow(trimPx(line1, barMaxW2), barLineX, barLineY, 0x2F65FF);
            }
            if (line2 != null && line2.length() > 0) {
                // Avoid duplicating the same text.
                if (!plainForCompare(line2).equalsIgnoreCase(plainForCompare(line1))) {
                    this.fontRendererObj.drawStringWithShadow(trimPx(line2, barMaxW2), barLineX, barLineY + 10, 0x2F65FF);
                }
            }
        }
        boolean ready = (target != null && gem != null && out != null);

        if (ready) {
            // Small status text
            this.fontRendererObj.drawString("Ready", 118, 6, 0x2E7D32);

            // Draw a pulsing glow around the OUTPUT slot (above items)
            float t = timeSeconds();
            float pulse = 0.35f + 0.25f * (float) Math.sin(t * 4.0f);

            GL11.glPushMatrix();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);

            // Soft glow pass (additive)
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glColor4f(1f, 1f, 1f, pulse);

            // Draw a simple glowing frame (4 thin rects)
            int a = (int) (pulse * 255.0f);
            if (a < 0) a = 0;
            if (a > 255) a = 255;
            int glowColor = (a << 24) | 0xFFFFFF;
            drawGlowFrame(OUT_X, OUT_Y, 16, 16, glowColor);

            // Restore
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glPopMatrix();

            // Overlay: draw the GEM icon small on top of the TARGET and OUTPUT slots
            drawMiniOverlayIcon(gem, TARGET_X + 10, TARGET_Y + 10, 0.60f);
            drawMiniOverlayIcon(gem, OUT_X + 10, OUT_Y + 10, 0.60f);

            // Optional: a subtle "socket" hint between slots (text-only, low-key)
            this.fontRendererObj.drawString("→", 106, 50, 0x808080);
        } else {
            // Small hints so the player knows what to do.
            int hintY = TARGET_Y + 22;
            if (target == null) {
                this.fontRendererObj.drawString("Place item", TARGET_X - 8, hintY, 0x707070);
            } else if (gem == null) {
                this.fontRendererObj.drawString("Add orb", GEM_X - 4, hintY, 0x707070);
            } else {
                // "No open socket" is based on the TARGET's sockets, not whether the server preview built an output.
                int open = getSocketsOpenClient(target);
                int filled = getSocketsFilledClient(target);
                int free = open - filled;
                if (free < 0) free = 0;

                if (free <= 0) {
                    this.fontRendererObj.drawString("No open socket", 92, hintY, 0xAA3333);
                } else {
                    this.fontRendererObj.drawString("Open socket (" + free + ")", 82, hintY, 0x33AA33);
                    if (out == null) {
                        this.fontRendererObj.drawString("Preview unavailable", 78, hintY + 10, 0x707070);
                    }
                }
            }
        }

        // Left-side panel: ORB preview by default, DEBUG when holding LEFT SHIFT
        if (isDebugHeld()) {
            refreshDebugCache(target, gem, out);
            drawDebugPanel(mouseX, mouseY, target, gem, out);
        } else {
            drawOrbPanel(mouseX, mouseY, target, gem, out);
        }
    }



    private void drawGlowFrame(int x, int y, int w, int h, int color) {
        // Called from the foreground layer (already translated to guiLeft/guiTop),
        // so x/y are GUI-local.
        int thickness = 2;

        // Top
        drawRect(x - thickness, y - thickness, x + w + thickness, y, color);
        // Bottom
        drawRect(x - thickness, y + h, x + w + thickness, y + h + thickness, color);
        // Left
        drawRect(x - thickness, y, x, y + h, color);
        // Right
        drawRect(x + w, y, x + w + thickness, y + h, color);
    }

    private void drawMiniOverlayIcon(ItemStack stack, int slotX, int slotY, float scale) {
        if (stack == null) return;

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(1f, 1f, 1f, 0.95f);

        // Render small and slightly above the slot item (GUI-local coords)
        GL11.glTranslatef(slotX, slotY, 300.0f);
        GL11.glScalef(scale, scale, 1.0f);

        RenderHelper.enableGUIStandardItemLighting();
        this.itemRender.renderItemAndEffectIntoGUI(this.fontRendererObj, this.mc.getTextureManager(), stack, 0, 0);
        this.itemRender.renderItemOverlayIntoGUI(this.fontRendererObj, this.mc.getTextureManager(), stack, 0, 0);
        RenderHelper.disableStandardItemLighting();

        GL11.glPopMatrix();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GL11.glColor4f(1f, 1f, 1f, 1f);

        // Base anvil texture
        this.mc.getTextureManager().bindTexture(GUI_TEX);
        int x = (this.width - this.xSize) / 2;
        int y = (this.height - this.ySize) / 2;
        this.drawTexturedModalRect(x, y, 0, 0, this.xSize, this.ySize);

        // Animated orb decorations (purely cosmetic)
        drawOrbDecorations(x, y, partialTicks);

    }

    private void drawOrbDecorations(int guiX, int guiY, float partialTicks) {
        // Decorative (non-interactive) atom-style icons in the top-left area.
        // Uses real-time seconds so it stays smooth regardless of FPS/TPS.
        final float t = timeSeconds();

        // Main orbit speed (radians/sec) with a tiny wobble so it feels alive.
        final float omega = ATOM_OMEGA + ATOM_OMEGA_WOBBLE * (float) Math.sin(t * 0.85f);

        final int panelX = guiX + ORB_PANEL_X;
        final int panelY = guiY + ORB_PANEL_Y;

        // Atom center. Lower X pushes everything further left.
        // Keep this safely below the title and left of the rename bar.
        final float cx = panelX + 26.0f;
        final float cy = panelY + 22.0f;

        // ------------------------
        // Orbiting “electrons”
        // ------------------------
        final int n = ELECTRONS.length;
        float[] px = new float[n];
        float[] py = new float[n];
        float[] zz = new float[n];
        float[] sc = new float[n];
        float[] al = new float[n];
        int[] idx = new int[n];

        for (int i = 0; i < n; i++) {
            ElectronDef e = ELECTRONS[i];
            RingDef r = RINGS[Math.abs(e.ring) % RINGS.length];

            // Per-ring slow precession (orbit plane gently rotates over time)
            final float precess = r.precessAmp * (float) Math.sin(t * ATOM_PRECESS_RATE + (i * 1.73f));
            final float rotZ = r.rotZ + precess;

            // Tiny tilt wobble (keeps rings from feeling perfectly static)
            final float tilt = r.tiltX + 0.06f * (float) Math.sin(t * 0.42f + (i * 0.90f));

            // Base orbit angle for this orb
            final float wob = 0.12f * e.wobble * (float) Math.sin(t * 0.65f + (i * 2.07f));
            final float a = (t * omega * r.speedMul) + e.phase + wob;

            // Orbit radii (scaled)
            final float rx = r.rx * ATOM_ORBIT_SCALE;
            final float rz = r.rz * ATOM_ORBIT_SCALE;

            // Start in XZ plane: x = cos(a)*rx, z = sin(a)*rz
            float x = (float) Math.cos(a) * rx;
            float y = 0.0f;
            float z = (float) Math.sin(a) * rz;

            // Rotate orbit plane around X axis (tilt)
            final float cX = (float) Math.cos(tilt);
            final float sX = (float) Math.sin(tilt);
            float y1 = (y * cX) - (z * sX);
            float z1 = (y * sX) + (z * cX);
            float x1 = x;

            // Rotate around Z for ring orientation
            final float cZ = (float) Math.cos(rotZ);
            final float sZ = (float) Math.sin(rotZ);
            float x2 = (x1 * cZ) - (y1 * sZ);
            float y2 = (x1 * sZ) + (y1 * cZ);

            // Depth is z1 (front/back). Normalize for scale/alpha.
            float zn = z1 / (rz * 1.05f);
            if (zn < -1f) zn = -1f;
            if (zn >  1f) zn =  1f;

            float depth = 0.5f + 0.5f * zn; // 0..1
            float breathe = 1.0f + 0.04f * (float) Math.sin(t * 0.70f + i);

            // Scale/alpha based on depth (front orbs larger/brighter, back orbs smaller/dimmer)
            sc[i] = (0.74f + 0.32f * depth) * breathe;
            al[i] = 0.55f + 0.45f * depth;

            px[i] = x2;
            py[i] = y2;
            zz[i] = z1;
            idx[i] = i;
        }

        // Sort electrons back-to-front so overlap looks “3D”.
        for (int i = 0; i < n - 1; i++) {
            for (int j = i + 1; j < n; j++) {
                if (zz[idx[i]] > zz[idx[j]]) {
                    int tmp = idx[i];
                    idx[i] = idx[j];
                    idx[j] = tmp;
                }
            }
        }

        // Draw electrons.
        for (int k = 0; k < n; k++) {
            int i = idx[k];
            ElectronDef e = ELECTRONS[i];

            int frame = ((int) (t * e.animFps + (i * 1.3f))) % ORB_FRAMES;
            if (frame < 0) frame += ORB_FRAMES;

            drawOrbTransformed(e.tex, cx + px[i], cy + py[i], e.baseSize,
                    0, frame * ORB_FRAME_PX, ORB_FRAME_PX, ORB_FRAME_PX,
                    ORB_FRAME_PX, e.sheetH,
                    sc[i], al[i]);
        }

        // ------------------------
        // Center “nucleus” (swirly orb)
        // Draw last so it stays the focal point.
        // ------------------------
        int nucFrame = ((int) (t * 5.2f)) % ORB_FRAMES;
        if (nucFrame < 0) nucFrame += ORB_FRAMES;

        float nucPulse = 1.0f + (NUCLEUS_PULSE * (float) Math.sin(t * 1.8f));
        drawOrbTransformed(TEX_SWIRLY_NUCLEUS, cx, cy, NUCLEUS_SIZE,
                0, nucFrame * ORB_FRAME_PX, ORB_FRAME_PX, ORB_FRAME_PX,
                ORB_FRAME_PX, SHEET_H_512,
                nucPulse, 1.0f);
    }

    /**
     * Returns indices 0..2 ordered from back to front (lowest z first).
     */
    private int[] depthOrder(float z0, float z1, float z2) {
        int a = 0, b = 1, c = 2;
        float za = z0, zb = z1, zc = z2;

        // simple sorting network
        if (za > zb) { int t = a; a = b; b = t; float tz = za; za = zb; zb = tz; }
        if (zb > zc) { int t = b; b = c; c = t; float tz = zb; zb = zc; zc = tz; }
        if (za > zb) { int t = a; a = b; b = t; float tz = za; za = zb; zb = tz; }

        return new int[]{a, b, c};
    }

    /**
     * Draw an orb centered at (cx, cy) with optional scale/alpha.
     * Uses the existing clamped panel bounds so it never bleeds into slots.
     */
    private void drawOrbTransformed(ResourceLocation tex, float cx, float cy, int baseSize,
                                    int u, int v, int uW, int vH, int tileW, int tileH,
                                    float scale, float alpha) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getTextureManager() == null) return;

        // Size after scale (used for clamping).
        float size = baseSize * scale;
        float x = cx - (size * 0.5f);
        float y = cy - (size * 0.5f);

        // Clamp to the orb zone (safe area).
        float minX = (guiLeft + ORB_PANEL_X) + 2;
        float minY = (guiTop  + ORB_PANEL_Y) + 2;
        float maxX = (guiLeft + ORB_PANEL_X + ORB_PANEL_W) - size - 2;
        float maxY = (guiTop  + ORB_PANEL_Y + ORB_PANEL_H) - size - 2;

        if (x < minX) x = minX;
        if (y < minY) y = minY;
        if (x > maxX) x = maxX;
        if (y > maxY) y = maxY;

        mc.getTextureManager().bindTexture(tex);

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, alpha);

        // sub-pixel + scale
        GL11.glTranslatef(x, y, 0.0f);
        GL11.glScalef(scale, scale, 1.0f);

        // Draw at (0,0) with baseSize; scaling handles final pixel size.
        drawScaledCustomSizeModalRect(0, 0, (float) u, (float) v,
                uW, vH, baseSize, baseSize, (float) tileW, (float) tileH);

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

// ---------------------------------------------------------------------
// Decorative-orb helpers

// ---------------------------------------------------------------------

    private float getClientTicks(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.theWorld != null) {
            return (float) mc.theWorld.getTotalWorldTime() + partialTicks;
        }
        // Fallback if world is null (menus, etc.)
        return (Minecraft.getSystemTime() / 50.0f) + partialTicks;
    }


    /**
     * 1.7.10 does NOT have Gui#drawScaledCustomSizeModalRect.
     * This is a local copy (Tessellator-based) that supports source region size != dest size.
     */
    private void drawScaledCustomSizeModalRect(int x, int y, float u, float v,
                                               int uWidth, int vHeight,
                                               int width, int height,
                                               float tileWidth, float tileHeight) {
        float f  = 1.0F / tileWidth;
        float f1 = 1.0F / tileHeight;

        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV((double) x,         (double) (y + height), (double) this.zLevel, (double) (u * f),              (double) ((v + (float) vHeight) * f1));
        tess.addVertexWithUV((double) (x + width), (double) (y + height), (double) this.zLevel, (double) ((u + (float) uWidth) * f), (double) ((v + (float) vHeight) * f1));
        tess.addVertexWithUV((double) (x + width), (double) y,          (double) this.zLevel, (double) ((u + (float) uWidth) * f), (double) (v * f1));
        tess.addVertexWithUV((double) x,         (double) y,           (double) this.zLevel, (double) (u * f),              (double) (v * f1));
        tess.draw();
    }

    /**
     * Draw a decorative orb with simple motion.
     *
     * @param tex   Texture to bind
     * @param baseX Base x (GUI space)
     * @param baseY Base y (GUI space)
     * @param size  Render size in GUI px
     * @param u     Source u (px)
     * @param v     Source v (px)
     * @param uW    Source width (px)
     * @param vH    Source height (px)
     * @param tileW Full texture width (px)
     * @param tileH Full texture height (px)
     */
    private void drawOrbWithMotion(ResourceLocation tex, int baseX, int baseY, int size,
                                   int u, int v, int uW, int vH,
                                   int tileW, int tileH,
                                   float dx, float dy) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getTextureManager() == null) return;

        // Don't round the motion into whole pixels (that creates the "static" / stepping feel).
        // Instead, keep the fractional part via a GL translate.
        float xf = (float)baseX + dx;
        float yf = (float)baseY + dy;

        // Clamp to the panel bounds (keeps icons from escaping / clipping edges).
        int minX = (guiLeft + ORB_PANEL_X) + 2;
        int minY = (guiTop  + ORB_PANEL_Y) + 2;
        int maxX = (guiLeft + ORB_PANEL_X + ORB_PANEL_W) - size - 2;
        int maxY = (guiTop  + ORB_PANEL_Y + ORB_PANEL_H) - size - 2;
        if (xf < minX) xf = minX;
        if (yf < minY) yf = minY;
        if (xf > maxX) xf = maxX;
        if (yf > maxY) yf = maxY;

        int x = (int)Math.floor(xf);
        int y = (int)Math.floor(yf);
        float fx = xf - (float)x;
        float fy = yf - (float)y;

        mc.getTextureManager().bindTexture(tex);

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        // Uses correct UVs for non-256 textures.
        // Keep fractional motion smooth.
        GL11.glTranslatef(fx, fy, 0.0f);
        drawScaledCustomSizeModalRect(x, y, (float) u, (float) v, uW, vH, size, size, (float) tileW, (float) tileH);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private float motionCircleX(float t, float speed, float amp) {
        return (float) (Math.sin(t * speed) * amp);
    }

    private float motionCircleY(float t, float speed, float amp) {
        return (float) (Math.cos(t * speed) * amp);
    }

    private float motionJuggleX(float t, float speed, float amp) {
        return (float) (Math.sin(t * speed) * amp);
    }

    private float motionJuggleY(float t, float speed, float amp) {
        // bounce: always positive-ish
        return (float) (Math.abs(Math.sin(t * speed)) * amp);
    }

    private float motionTriangleX(float t, float speed, float amp) {
        // triangle-ish via piecewise lerp
        float p = (t * speed) % 3.0f; // 0..3
        if (p < 1.0f) return lerp(-amp, amp, p);
        if (p < 2.0f) return lerp(amp, 0f, p - 1.0f);
        return lerp(0f, -amp, p - 2.0f);
    }

    private float motionTriangleY(float t, float speed, float amp) {
        float p = (t * speed) % 3.0f;
        if (p < 1.0f) return lerp(0f, -amp, p);
        if (p < 2.0f) return lerp(-amp, amp, p - 1.0f);
        return lerp(amp, 0f, p - 2.0f);
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private String ellipsize(String s, int maxChars) {
        if (s == null) return "";
        if (s.length() <= maxChars) return s;
        if (maxChars <= 3) return s.substring(0, maxChars);
        return s.substring(0, maxChars - 3) + "...";
    }

    /**
     * Pixel-width trimming that works with HexFontRenderer tags because it uses
     * FontRenderer.trimStringToWidth + getStringWidth (tag-aware in HexFontRenderer).
     */
    private String trimPx(String s, int maxW) {
        if (s == null) return "";
        if (maxW <= 0) return "";
        // fast path
        if (this.fontRendererObj.getStringWidth(s) <= maxW) return s;
        final String ell = "…";
        final int ellW = this.fontRendererObj.getStringWidth(ell);
        String cut = this.fontRendererObj.trimStringToWidth(s, Math.max(0, maxW - ellW));
        return cut + ell;
    }

    // For comparisons only: strips <...> tags and § formatting codes.
    private String plainForCompare(String s) {
        if (s == null) return "";
        s = stripAngleTags(s);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) {
                i++; // skip formatting code
                continue;
            }
            sb.append(c);
        }
        return sb.toString().trim();
    }


    /** Strips <tag ...> wrappers used by HexFont styles, leaving inner text. */
    private String stripAngleTags(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        boolean in = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') { in = true; continue; }
            if (c == '>') { in = false; continue; }
            if (!in) out.append(c);
        }
        return out.toString();
    }

    private String niceTitleFromVariant(String variant) {
        if (variant == null) return "";
        // drop common suffixes
        String v = variant;
        v = v.replace("_64_anim_8f", "");
        v = v.replace("_64_anim", "");
        v = v.replace("_64", "");
        v = v.replace("_anim_8f_64x516", "");
        v = v.replace("_anim_8f", "");
        v = v.replace("_anim", "");

        // remove leading families
        if (v.startsWith("orb_gem_")) v = v.substring("orb_gem_".length());
        if (v.startsWith("orb_"))     v = v.substring("orb_".length());
        if (v.startsWith("pill_"))    v = v.substring("pill_".length());

        // turn underscores into Title Case words
        String[] parts = v.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.length() == 0) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1));
        }
        return sb.toString();
    }

    /** Best-effort plain orb name for GUIs (works even when the item name is tag-heavy). */
    /**
     * Best-effort orb name for GUIs.
     * Keeps Hex tags (e.g. <grad ...>) so HexFontRenderer can style it.
     */
    private String getOrbNamePlain(ItemStack gem) {
        if (gem == null) return "";
        String raw = gem.getDisplayName();
        if (raw == null) raw = "";

        // "check" version without tags, used only to detect missing translations.
        String check = stripAngleTags(raw);
        check = check.replace('\n', ' ').replace('\r', ' ');
        check = check.trim().replaceAll("\\s+", " ");

        // Render version keeps tags / § formatting, but still removes newlines and collapses spacing.
        String render = raw.replace('\n', ' ').replace('\r', ' ');
        render = render.trim().replaceAll("\\s+", " ");

        if (check.length() == 0 || check.startsWith("item.") || check.endsWith(".name")) {
            Item it = gem.getItem();
            if (it instanceof ItemGemIcons) {
                int meta = gem.getItemDamage();
                if (meta >= 0 && meta < ItemGemIcons.VARIANTS.length) {
                    String v = ItemGemIcons.VARIANTS[meta];
                    String titled = niceTitleFromVariant(v);
                    if (titled.length() > 0) return titled;
                }
            }
            return "Orb";
        }

        return (render.length() > 0 ? render : check);
    }


    /** Optional hint derived from the gem icon variant (helps when display name doesn't clearly say what it is). */
    private String getOrbVariantHint(ItemStack gem) {
        if (gem == null) return "";
        if (!(gem.getItem() instanceof ItemGemIcons)) return "";
        try {
            int meta = gem.getItemDamage();
            if (meta < 0) meta = 0;
            if (meta >= ItemGemIcons.VARIANTS.length) meta = ItemGemIcons.VARIANTS.length - 1;
            String raw = ItemGemIcons.VARIANTS[meta];
            if (raw == null) raw = "";
            raw = raw.trim();
            if (raw.length() == 0) return "";

            String titled = niceTitleFromVariant(raw);
            if (titled == null) titled = "";
            titled = titled.trim();
            if (titled.length() == 0) return "";

            // Avoid repeating what's already in the orb's display name.
            String namePlain = plainForCompare(gem.getDisplayName());
            String titledPlain = plainForCompare(titled);
            if (titledPlain.length() > 0 && namePlain.toLowerCase().contains(titledPlain.toLowerCase())) {
                return "";
            }

            // Subtle, gray hint line.
            return "§8" + titled;
        } catch (Throwable t) {
            return "";
        }
    }

    private List<String> pickUsefulLines(ItemStack stack, int maxLines) {
        List<String> out = new ArrayList<String>();
        if (stack == null || maxLines <= 0) return out;

        try {
            @SuppressWarnings("unchecked")
            List<String> tip = stack.getTooltip(this.mc.thePlayer, this.mc.gameSettings.advancedItemTooltips);
            if (tip == null || tip.isEmpty()) return out;

            // tip[0] is usually the display name; start after that
            for (int i = 1; i < tip.size() && out.size() < maxLines; i++) {
                String line = tip.get(i);
                if (line == null) continue;

                String plain = stripColor(line).toLowerCase();

                boolean useful =
                        plain.contains("socket") ||
                                plain.contains("dbc.") ||
                                plain.contains("%") ||
                                plain.contains("strength") ||
                                plain.contains("constitution") ||
                                plain.contains("dexterity") ||
                                plain.contains("spirit") ||
                                plain.contains("will") ||
                                plain.contains("multi") ||
                                plain.contains("+") ||
                                plain.contains("bonus");

                if (useful) out.add(line);
            }

            // Fallback: first non-empty tooltip line
            if (out.isEmpty()) {
                for (int i = 1; i < tip.size() && out.size() < maxLines; i++) {
                    String line = tip.get(i);
                    if (line != null && stripColor(line).trim().length() > 0) {
                        out.add(line);
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
            // best-effort only
        }

        return out;
    }

    /**
     * Picks the "rolled value" line from an orb tooltip (e.g. "Spirit +48" or "12% Spirit Multi").
     * We intentionally skip descriptive lines like "Bonus: ...".
     */
    private String pickGemValueLine(ItemStack gem) {
        if (gem == null) return "";
        List<String> lines = pickUsefulLines(gem, 8);
        String best = "";
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null) continue;
            String p = plainForCompare(line).toLowerCase();
            if (p.startsWith("bonus:")) continue;
            // Prefer numeric-looking lines.
            if (p.contains("+") || p.contains("%")) {
                best = line;
            }
        }
        return best;
    }

    private String stripColor(String s) {
        if (s == null) return "";
        return s.replaceAll("\u00A7[0-9A-FK-ORa-fk-or]", "");
    }

    private int getSocketsOpenClient(ItemStack stack) {
        if (stack == null) return 0;
        try {
            return HexSocketAPI.getSocketsOpen(stack);
        } catch (Throwable ignored) {
        }
        // Fallback: raw NBT read (matches HexSocketCommand keys)
        try {
            if (stack.getTagCompound() == null) return 0;
            if (!stack.getTagCompound().hasKey(NBT_ROOT)) return 0;
            NBTTagCompound root = stack.getTagCompound().getCompoundTag(NBT_ROOT);
            if (root == null) return 0;
            return root.getInteger(K_OPEN);
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private int getSocketsFilledClient(ItemStack stack) {
        if (stack == null) return 0;
        try {
            return HexSocketAPI.getSocketsFilled(stack);
        } catch (Throwable ignored) {
        }
        // Fallback: filled == Gems list size (HexSocketCommand does NOT store a SocketsFilled int)
        try {
            if (stack.getTagCompound() == null) return 0;
            if (!stack.getTagCompound().hasKey(NBT_ROOT)) return 0;
            NBTTagCompound root = stack.getTagCompound().getCompoundTag(NBT_ROOT);
            if (root == null) return 0;

            if (root.hasKey(K_GEMS, 9)) { // list
                NBTTagList list = root.getTagList(K_GEMS, 8); // strings
                return list == null ? 0 : list.tagCount();
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }


    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawNameBarHoverTooltip(mouseX, mouseY);
    }

    /**
     * Hovering the anvil-style name bar shows the full orb/preview tooltip (useful when the text is trimmed).
     */
    private void drawNameBarHoverTooltip(int mouseX, int mouseY) {
        // Don't fight with the slot tooltips while debugging.
        if (isDebugHeld()) return;

        int relX = mouseX - this.guiLeft;
        int relY = mouseY - this.guiTop;

        // Covers the whole name bar area (both the name + the blue stat line).
        int hoverX = 48;
        int hoverY = 16;
        int hoverW = this.xSize - 48 - 8;
        int hoverH = 26;

        if (relX < hoverX || relX >= hoverX + hoverW || relY < hoverY || relY >= hoverY + hoverH) {
            return;
        }

        ItemStack gem = getSlotStack(SLOT_GEM);
        if (gem == null) return;

        ItemStack out = getSlotStack(SLOT_OUTPUT);
        ItemStack target = getSlotStack(SLOT_TARGET);
        ItemStack src = (out != null ? out : gem);

        java.util.List<String> tip = new java.util.ArrayList<String>();

        // Always show the full orb name (this is what gets clipped in the bar).
        String orbName = getOrbNamePlain(gem);
        if (orbName != null && orbName.length() > 0) tip.add(orbName);

        // Also show the preview/target item name so the hover reflects the actual output.
        String previewName = null;
        if (out != null) previewName = out.getDisplayName();
        else if (target != null) previewName = target.getDisplayName();
        if (previewName != null && previewName.length() > 0) tip.add("§7→ §f" + previewName);

        java.util.List<String> lines = pickUsefulLines(src, 10);
        String orbPlain = plainForCompare(orbName);
        String prevPlain = plainForCompare(previewName);

        if (lines != null) {
            for (int i = 0; i < lines.size(); i++) {
                String li = lines.get(i);
                String liPlain = plainForCompare(li);
                if (liPlain.length() == 0) continue;
                if (liPlain.equalsIgnoreCase(orbPlain)) continue;
                if (liPlain.equalsIgnoreCase(prevPlain)) continue;
                tip.add(li);
                if (tip.size() >= 10) break;
            }
        }

        if (tip.isEmpty()) tip.add("§7(No details)");


        this.drawHoveringText(tip, mouseX, mouseY, this.fontRendererObj);
        RenderHelper.disableStandardItemLighting();
    }
}