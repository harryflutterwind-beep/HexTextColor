package com.example.examplemod.gui;

import com.example.examplemod.api.HexSocketAPI;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Enchant-table style "open socket" station.
 *
 * Slots:
 *  - 0: target item (stackSize must be 1)
 *  - 1: material item (iron/gold/diamond/emerald/nether star)
 *
 * UI shows 3 options (like enchanting table). Clicking an option:
 *  - consumes EXP levels (random, biased toward HIGH costs)
 *  - consumes 1 material item
 *  - increases SocketsOpen by +1/+2/+3 (based on option, clamped by remaining)
 *  - refreshes sockets lore page via HexSocketAPI
 */
public class ContainerHexSocketOpener extends Container {

    // Persisted per-item so players can't "slot 1 in/out" reroll infinitely.
    // Each target item gets a locked base roll stored in its NBT.
    // We allow at most ONE manual "cycle" reroll per item (remove target -> put it back).
    private static final String NBT_BASE_COSTS   = "HexSocketOpenerBaseCosts"; // int[3]
    private static final String NBT_CYCLE_USED   = "HexSocketOpenerCycleUsed"; // boolean

    // Slot indices
    public static final int SLOT_TARGET   = 0;
    public static final int SLOT_CATALYST = 1;

    private static final int COST_MIN = 25;
    private static final int COST_MAX_0 = 250;
    private static final int COST_MAX_1 = 500;
    private static final int COST_MAX_2 = 1000;

    // Higher => more biased toward HIGH costs (cheap rolls become rarer)
    private static final double HIGH_BIAS_POWER = 3.0;

    private final EntityPlayer player;

    private final InventoryBasic input = new InventoryBasic("HexSocketOpener", false, 2) {
        @Override
        public void markDirty() {
            super.markDirty();
            // Mirror vanilla behavior (update offers immediately)
            ContainerHexSocketOpener.this.onCraftMatrixChanged(this);
        }
    };

    /** The three displayed costs, synced to the client via progress-bar updates. */
    public final int[] costs = new int[3];

    /**
     * Base (pre-material-multiplier) costs. These are rolled ONLY when the TARGET slot changes.
     *
     * This prevents an easy "infinite reroll" by pulling the material in/out of the catalyst slot.
     * The displayed costs will still update immediately when the material changes (via the multiplier),
     * but the underlying roll stays the same until the target item is removed and re-inserted.
     */
    private final int[] baseCosts = new int[3];

    /** Tracks the last signature used for *display* costs (target + material tier). */
    private int lastCostSig = 0;
    /** Tracks the last signature used for the *base roll* (target only). */
    private int lastTargetSig = 0;

    /** Whether the target slot was non-empty the last time we evaluated offers. */
    private boolean hadTarget = false;

    /** Becomes true after the target was removed once; used to allow ONE manual cycle reroll. */
    private boolean sawTargetRemoved = false;

    /** Force a base reroll (used after a click attempt). */
    private boolean forceRerollBase = true;

    /** Incremented whenever we roll new base costs (helps avoid same-tick identical seeds). */
    private int offerSerial = 0;

    // Material tiers: rarer material => lower level cost multiplier + higher success chance.
    // User requested ordering: Iron < Gold < Diamond < Emerald < Nether Star (rarest)
    private static final class MaterialInfo {
        final int tier;            // 0..4
        final double costMult;     // <= 1.0 (lower is cheaper)
        final double successChance; // 0..1

        MaterialInfo(int tier, double costMult, double successChance) {
            this.tier = tier;
            this.costMult = costMult;
            this.successChance = successChance;
        }
    }

    public ContainerHexSocketOpener(InventoryPlayer inv, EntityPlayer player) {
        this.player = player;

        // Match enchanting-table-ish positions
        this.addSlotToContainer(new Slot(input, SLOT_TARGET, 15, 47) {
            @Override public boolean isItemValid(ItemStack stack) {
                return stack != null && stack.stackSize == 1;
            }

            @Override public int getSlotStackLimit() { return 1; }
        });

        this.addSlotToContainer(new Slot(input, SLOT_CATALYST, 37, 47) {
            @Override public boolean isItemValid(ItemStack stack) {
                return isCatalyst(stack);
            }
        });

        // Player inventory
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                this.addSlotToContainer(new Slot(inv, x + y * 9 + 9, 8 + x * 18, 84 + y * 18));
            }
        }
        for (int x = 0; x < 9; x++) {
            this.addSlotToContainer(new Slot(inv, x, 8 + x * 18, 142));
        }

        // Initialize offers
        this.onCraftMatrixChanged(input);
    }

    /**
     * Compatibility ctor (enchant-table / gui handler pattern).
     * Some openGui calls pass (InventoryPlayer, World, x, y, z).
     * We ignore coords and delegate.
     */
    public ContainerHexSocketOpener(InventoryPlayer inv, World world, int x, int y, int z) {
        this(inv, inv != null ? inv.player : null);
    }

    /**
     * Compatibility ctor (EntityPlayer, World, x, y, z).
     */
    public ContainerHexSocketOpener(EntityPlayer player, World world, int x, int y, int z) {
        this(player != null ? player.inventory : null, player);
    }

    public IInventory getInputInventory() {
        return input;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }

    @Override
    public void onCraftMatrixChanged(IInventory inv) {
        super.onCraftMatrixChanged(inv);
        updateOffersIfNeeded();
    }

    private void updateOffersIfNeeded() {
        ItemStack target = input.getStackInSlot(SLOT_TARGET);
        ItemStack cat = input.getStackInSlot(SLOT_CATALYST);

        // We recompute display costs when either:
        //  - the target changes (including being removed/re-added)
        //  - the material tier changes (or material removed/added)
        //  - we force a reroll (after a click attempt)
        int costSig = computeCostSig(target, cat);
        if (costSig == lastCostSig && !forceRerollBase) {
            return;
        }
        lastCostSig = costSig;

        // If target is missing/invalid, show no offers.
        // If the target was present and is now gone, mark that we've seen a removal.
        if (!canOpenAnotherSocket(target)) {
            costs[0] = costs[1] = costs[2] = 0;
            baseCosts[0] = baseCosts[1] = baseCosts[2] = 0;

            if (hadTarget && target == null) {
                // This enables ONE manual cycle reroll on the next re-insert (if the item hasn't used it yet).
                sawTargetRemoved = true;
            }

            hadTarget = (target != null);
            if (target == null) {
                hadTarget = false;
                lastTargetSig = 0;
            }
            forceRerollBase = false;
            return;
        }

        // Target is valid. Handle base roll locking:
        //  - Base costs are stored on the item NBT.
        //  - You may do exactly ONE manual reroll by removing the target then putting it back.
        //  - Attempts (clicking an option) still reroll (because they cost XP/material).
        int targetSig = computeTargetSig(target);
        boolean targetChanged = (!hadTarget) || (targetSig != lastTargetSig);

        if (targetChanged) {
            hadTarget = true;
            lastTargetSig = targetSig;

            boolean loaded = loadBaseCostsFromTarget(target);
            boolean cycleUsed = isCycleUsed(target);

            if (!loaded) {
                // First time this item is used here: generate & persist.
                rollAndPersistBaseCosts(target);
                setCycleUsed(target, false);
            } else if (forceRerollBase) {
                // Paid attempt reroll must win even if socketsOpen changed (which changes targetSig).
                rollAndPersistBaseCosts(target);
            } else if (sawTargetRemoved && !cycleUsed) {
                // Allow exactly ONE manual cycle reroll per item.
                rollAndPersistBaseCosts(target);
                setCycleUsed(target, true);
            }

            sawTargetRemoved = false;
            forceRerollBase = false;
        } else if (forceRerollBase) {
            // Paid attempt reroll (allowed).
            rollAndPersistBaseCosts(target);
            forceRerollBase = false;
        }

        // If material is missing/invalid, show no offers (but keep baseCosts intact).
        MaterialInfo mi = getMaterialInfo(cat);
        if (mi == null) {
            costs[0] = costs[1] = costs[2] = 0;
            return;
        }

        // Apply the current material multiplier to the locked-in baseCosts.
        costs[0] = applyMaterialCostMult(baseCosts[0], mi);
        costs[1] = applyMaterialCostMult(baseCosts[1], mi);
        costs[2] = applyMaterialCostMult(baseCosts[2], mi);
    }

    private int computeTargetSig(ItemStack target) {
        if (target == null) return 0;
        int a = Item.getIdFromItem(target.getItem());
        a = a * 31 + target.getItemDamage();
        a = a * 31 + HexSocketAPI.getSocketsOpen(target);
        a = a * 31 + HexSocketAPI.getSocketsMax(target);
        return a;
    }

    /** Includes target sig + material tier so display costs can update without re-rolling baseCosts. */
    private int computeCostSig(ItemStack target, ItemStack cat) {
        int a = computeTargetSig(target);
        int tier = getMaterialTier(cat); // -1 if missing/invalid
        return a * 131 + tier;
    }

    /** Seed for baseCost rolls (target-only). */
    private long makeOfferSeedTarget(ItemStack target, int serial) {
        long t = (player.worldObj != null) ? player.worldObj.getTotalWorldTime() : 0L;
        long base = ((long) player.getEntityId() * 341873128712L) ^ (t * 132897987541L);
        base ^= (long) serial * 91815541L;
        if (target != null) {
            base ^= (long) Item.getIdFromItem(target.getItem()) * 42317861L;
            base ^= (long) target.getItemDamage() * 137L;
            base ^= (long) HexSocketAPI.getSocketsOpen(target) * 91138233L;
            base ^= (long) HexSocketAPI.getSocketsMax(target) * 127L;
        }
        return base;
    }

    private static int rollCost(Random r, int min, int max) {
        if (max <= min) return min;
        double u = r.nextDouble();
        // Bias toward HIGH values: x in [0,1], heavily weighted near 1.
        double x = 1.0 - Math.pow(u, HIGH_BIAS_POWER);
        int v = min + (int) Math.round(x * (max - min));
        if (v < min) v = min;
        if (v > max) v = max;
        return v;
    }

    // ------------------------------------------------------------
    // Per-item base cost persistence (anti-cheese)
    // ------------------------------------------------------------

    private boolean loadBaseCostsFromTarget(ItemStack target) {
        if (target == null) return false;
        NBTTagCompound tag = target.getTagCompound();
        if (tag == null) return false;

        int[] arr = tag.getIntArray(NBT_BASE_COSTS);
        if (arr == null || arr.length != 3) return false;
        if (arr[0] <= 0 || arr[1] <= 0 || arr[2] <= 0) return false;

        baseCosts[0] = arr[0];
        baseCosts[1] = arr[1];
        baseCosts[2] = arr[2];
        return true;
    }

    private void rollAndPersistBaseCosts(ItemStack target) {
        offerSerial++;
        Random r = new Random(makeOfferSeedTarget(target, offerSerial));
        baseCosts[0] = rollCost(r, COST_MIN, COST_MAX_0);
        baseCosts[1] = rollCost(r, COST_MIN, COST_MAX_1);
        baseCosts[2] = rollCost(r, COST_MIN, COST_MAX_2);

        if (target != null) {
            NBTTagCompound tag = getOrCreateTag(target);
            tag.setIntArray(NBT_BASE_COSTS, new int[] { baseCosts[0], baseCosts[1], baseCosts[2] });
        }
    }

    private static boolean isCycleUsed(ItemStack target) {
        if (target == null) return false;
        NBTTagCompound tag = target.getTagCompound();
        if (tag == null) return false;
        return tag.getBoolean(NBT_CYCLE_USED);
    }

    private static void setCycleUsed(ItemStack target, boolean used) {
        if (target == null) return;
        NBTTagCompound tag = getOrCreateTag(target);
        tag.setBoolean(NBT_CYCLE_USED, used);
    }

    private static NBTTagCompound getOrCreateTag(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        return tag;
    }

    /** "Catalyst" slot accepts multiple materials (not lapis): iron/gold/diamond/emerald/nether star. */
    public static boolean isCatalyst(ItemStack stack) {
        return getMaterialInfo(stack) != null;
    }

    /** Exposed for GUI/help text. Returns 0..4, or -1 if not a valid material. */
    public static int getMaterialTier(ItemStack stack) {
        MaterialInfo mi = getMaterialInfo(stack);
        return mi == null ? -1 : mi.tier;
    }

    /** Exposed for GUI/help text. Returns 0..1 success chance, or 0 if invalid. */
    public static double getMaterialSuccessChance(ItemStack stack) {
        MaterialInfo mi = getMaterialInfo(stack);
        return mi == null ? 0.0 : mi.successChance;
    }

    /** Exposed for GUI/help text. Returns <=1 cost multiplier, or 1 if invalid. */
    public static double getMaterialCostMult(ItemStack stack) {
        MaterialInfo mi = getMaterialInfo(stack);
        return mi == null ? 1.0 : mi.costMult;
    }

    private static MaterialInfo getMaterialInfo(ItemStack stack) {
        if (stack == null) return null;
        Item item = stack.getItem();
        if (item == null) return null;

        // tier: 0 iron, 1 gold, 2 diamond, 3 emerald, 4 nether star
        // costMult: rarer => cheaper
        // successChance: rarer => higher
        if (item == Items.iron_ingot)  return new MaterialInfo(0, 1.00, 0.70);
        if (item == Items.gold_ingot)  return new MaterialInfo(1, 0.90, 0.78);
        if (item == Items.diamond)     return new MaterialInfo(2, 0.80, 0.86);
        if (item == Items.emerald)     return new MaterialInfo(3, 0.75, 0.90);
        if (item == Items.nether_star) return new MaterialInfo(4, 0.60, 0.97);
        return null;
    }

    private static int applyMaterialCostMult(int baseCost, MaterialInfo mi) {
        if (mi == null) return baseCost;
        int v = (int) Math.ceil(baseCost * mi.costMult);
        if (v < 1) v = 1;
        return v;
    }

    private static boolean canOpenAnotherSocket(ItemStack target) {
        if (target == null || target.stackSize != 1) return false;
        int open = HexSocketAPI.getSocketsOpen(target);
        int max = HexSocketAPI.getSocketsMax(target);
        return (max < 0) || (open < max);
    }

    /**
     * Called by mc.playerController.sendEnchantPacket(windowId, buttonId).
     * We use buttonId 0..2 like the enchanting table.
     */
    @Override
    public boolean enchantItem(EntityPlayer player, int buttonId) {
        if (buttonId < 0 || buttonId > 2) return false;

        ItemStack target = input.getStackInSlot(SLOT_TARGET);
        ItemStack cat = input.getStackInSlot(SLOT_CATALYST);
        if (target == null || cat == null) return false;
        if (!isCatalyst(cat)) return false;

        if (!canOpenAnotherSocket(target)) return false;

        int cost = costs[buttonId];
        if (cost <= 0) return false;

        boolean creative = player.capabilities != null && player.capabilities.isCreativeMode;
        if (!creative && player.experienceLevel < cost) return false;

        // Compute how many sockets this option would open.
        int open = HexSocketAPI.getSocketsOpen(target);
        int max = HexSocketAPI.getSocketsMax(target);
        int remaining = (max < 0) ? 999 : Math.max(0, (max - open));
        int add = Math.min(buttonId + 1, remaining);
        if (add <= 0) return false;

        // Consume material + XP first (attempt cost), unless creative.
        if (!creative) {
            player.addExperienceLevel(-cost);
            cat.stackSize--;
            if (cat.stackSize <= 0) input.setInventorySlotContents(SLOT_CATALYST, null);
        }

        // Chance roll (rarer material => higher chance)
        MaterialInfo mi = getMaterialInfo(cat);
        if (mi == null) return false;

        Random rr = new Random(makeAttemptSeed(target, cat));
        boolean success = rr.nextDouble() < mi.successChance;

        if (success) {
            HexSocketAPI.setSocketsOpen(target, open + add);
            try { HexSocketAPI.syncSocketsPageOnly(target); } catch (Throwable ignored) {}
        } else {
            // Minimal feedback; only on failure to avoid chat spam.
            try {
                player.addChatMessage(new net.minecraft.util.ChatComponentText("\u00a7c[HexSocket] Failed to open a socket."));
            } catch (Throwable ignored) {}
        }

        // Re-roll offers for the next open (even if the target didn't change).
        forceRerollBase = true;
        updateOffersIfNeeded();
        detectAndSendChanges();
        return true;
    }

    private long makeAttemptSeed(ItemStack target, ItemStack cat) {
        long t = (player.worldObj != null) ? player.worldObj.getTotalWorldTime() : 0L;
        long base = (t * 912367421L) ^ ((long) player.getEntityId() * 73471L);
        if (target != null) {
            base ^= (long) Item.getIdFromItem(target.getItem()) * 3418731287L;
            base ^= (long) target.getItemDamage() * 132897987L;
            base ^= (long) HexSocketAPI.getSocketsOpen(target) * 7771L;
            base ^= (long) HexSocketAPI.getSocketsMax(target) * 1777L;
        }
        if (cat != null) {
            base ^= (long) Item.getIdFromItem(cat.getItem()) * 99194853094755497L;
            base ^= (long) cat.getItemDamage() * 31337L;
        }
        return base;
    }

    // ------------------------------------------------------------
    // Client sync (progress bars)
    // ------------------------------------------------------------

    @Override
    public void addCraftingToCrafters(ICrafting crafting) {
        super.addCraftingToCrafters(crafting);
        // Initial push
        crafting.sendProgressBarUpdate(this, 0, costs[0]);
        crafting.sendProgressBarUpdate(this, 1, costs[1]);
        crafting.sendProgressBarUpdate(this, 2, costs[2]);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        for (Object o : this.crafters) {
            ICrafting c = (ICrafting) o;
            c.sendProgressBarUpdate(this, 0, costs[0]);
            c.sendProgressBarUpdate(this, 1, costs[1]);
            c.sendProgressBarUpdate(this, 2, costs[2]);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void updateProgressBar(int id, int value) {
        if (id >= 0 && id < 3) costs[id] = value;
    }

    // ------------------------------------------------------------
    // Shift-click handling
    // ------------------------------------------------------------

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        ItemStack out = null;
        Slot slot = (Slot) this.inventorySlots.get(index);
        if (slot != null && slot.getHasStack()) {
            ItemStack stack = slot.getStack();
            out = stack.copy();

            // Container slots: 0..1
            if (index == SLOT_TARGET || index == SLOT_CATALYST) {
                // Move from container -> player
                if (!this.mergeItemStack(stack, 2, 38, true)) return null;
            } else {
                // Move from player -> container
                if (isCatalyst(stack)) {
                    if (!this.mergeItemStack(stack, SLOT_CATALYST, SLOT_CATALYST + 1, false)) return null;
                } else {
                    if (!this.mergeItemStack(stack, SLOT_TARGET, SLOT_TARGET + 1, false)) return null;
                }
            }

            if (stack.stackSize <= 0) slot.putStack(null);
            else slot.onSlotChanged();

            if (stack.stackSize == out.stackSize) return null;
            slot.onPickupFromSlot(player, stack);
        }
        return out;
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        // Drop leftovers like vanilla
        if (!player.worldObj.isRemote) {
            ItemStack a = input.getStackInSlot(SLOT_TARGET);
            ItemStack b = input.getStackInSlot(SLOT_CATALYST);
            if (a != null) player.dropPlayerItemWithRandomChoice(a, false);
            if (b != null) player.dropPlayerItemWithRandomChoice(b, false);
            input.setInventorySlotContents(SLOT_TARGET, null);
            input.setInventorySlotContents(SLOT_CATALYST, null);
        }
    }
}
