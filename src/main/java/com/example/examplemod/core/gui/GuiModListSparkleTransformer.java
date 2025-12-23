// src/main/java/com/example/examplemod/core/gui/GuiModListSparkleTransformer.java
package com.example.examplemod.core.gui;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.util.Iterator;

import static org.objectweb.asm.Opcodes.*;

/**
 * Injects a post-draw hook into FML's mod list screen so we can render sparkles
 * when our mod is selected.
 *
 * Target: cpw.mods.fml.client.GuiModList
 * Method: drawScreen(int mouseX, int mouseY, float partialTicks)  => (IIF)V
 *
 * Injects (near method end):
 *   com.example.examplemod.client.ModListSparkles.renderIfSelected(this, mouseX, mouseY, partialTicks);
 */
public class GuiModListSparkleTransformer implements IClassTransformer {

    private static final String TARGET_CLASS = "cpw.mods.fml.client.GuiModList";

    // SRG/obf names for 1.7.10:
    // drawScreen = func_73863_a
    private static final String M_DRAWSCREEN_MCP = "drawScreen";
    private static final String M_DRAWSCREEN_SRG = "func_73863_a";
    private static final String M_DRAWSCREEN_DESC = "(IIF)V";

    private static final String HOOK_OWNER = "com/example/examplemod/client/ModListSparkles";
    private static final String HOOK_NAME  = "renderIfSelected";
    private static final String HOOK_DESC  = "(Ljava/lang/Object;IIF)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;
        if (!TARGET_CLASS.equals(transformedName)) return basicClass;

        try {
            ClassNode cn = new ClassNode();
            ClassReader cr = new ClassReader(basicClass);
            cr.accept(cn, 0);

            boolean patched = false;

            for (MethodNode mn : cn.methods) {
                if (!M_DRAWSCREEN_DESC.equals(mn.desc)) continue;

                String n = mn.name;
                if (!(M_DRAWSCREEN_MCP.equals(n) || M_DRAWSCREEN_SRG.equals(n))) continue;

                // Avoid double-inject
                if (alreadyHasHook(mn)) {
                    patched = true;
                    break;
                }

                injectBeforeEveryReturn(mn);
                patched = true;
                break;
            }

            if (!patched) {
                System.out.println("[HexTextColor] GuiModListSparkleTransformer: drawScreen not found");
                return basicClass;
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);

            System.out.println("[HexTextColor] GuiModListSparkleTransformer: patched " + transformedName);
            return cw.toByteArray();

        } catch (Throwable t) {
            System.out.println("[HexTextColor] GuiModListSparkleTransformer: failed: " + t);
            return basicClass;
        }
    }

    private static boolean alreadyHasHook(MethodNode mn) {
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == INVOKESTATIC && insn instanceof MethodInsnNode) {
                MethodInsnNode mi = (MethodInsnNode) insn;
                if (HOOK_OWNER.equals(mi.owner) && HOOK_NAME.equals(mi.name) && HOOK_DESC.equals(mi.desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void injectBeforeEveryReturn(MethodNode mn) {
        InsnList call = new InsnList();
        // stack: (none)
        call.add(new VarInsnNode(ALOAD, 0)); // this
        call.add(new VarInsnNode(ILOAD, 1)); // mouseX
        call.add(new VarInsnNode(ILOAD, 2)); // mouseY
        call.add(new VarInsnNode(FLOAD, 3)); // partialTicks
        call.add(new MethodInsnNode(INVOKESTATIC, HOOK_OWNER, HOOK_NAME, HOOK_DESC, false));

        // Insert before each RETURN
        for (Iterator<AbstractInsnNode> it = mn.instructions.iterator(); it.hasNext(); ) {
            AbstractInsnNode insn = it.next();
            if (insn.getOpcode() == RETURN) {
                mn.instructions.insertBefore(insn, cloneInsnList(call));
            }
        }
    }

    // Defensive: same InsnList cannot be inserted multiple times
    private static InsnList cloneInsnList(InsnList src) {
        InsnList out = new InsnList();
        for (AbstractInsnNode n = src.getFirst(); n != null; n = n.getNext()) {
            out.add(n.clone(null));
        }
        return out;
    }
}
