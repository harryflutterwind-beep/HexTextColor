// src/main/java/com/example/examplemod/core/chat/GuiNewChatTransformer.java
package com.example.examplemod.core.chat;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class GuiNewChatTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "net.minecraft.client.gui.GuiNewChat";

    private static final boolean DEBUG_TRANSFORM = Boolean.getBoolean("hexchat.debugTransformer");

    private static void dbg(String s) {
        if (DEBUG_TRANSFORM) System.out.println(s);
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        if (!TARGET.equals(transformedName)) return bytes;

        System.out.println("[HexChat] Transforming GuiNewChat (1.7.10 wrap hook)…");

        try {
            ClassNode cn = new ClassNode();
            ClassReader cr = new ClassReader(bytes);
            cr.accept(cn, 0);

            boolean patched = false;

            for (int i = 0; i < cn.methods.size(); i++) {
                MethodNode mn = (MethodNode) cn.methods.get(i);

                // func_146237_a(IChatComponent;IIZ)V
                if (!"func_146237_a".equals(mn.name)) continue;
                if (!"(Lnet/minecraft/util/IChatComponent;IIZ)V".equals(mn.desc)) continue;

                System.out.println("[HexChat]   → Found func_146237_a, patching remainder carry…");
                patched = patchFunc146237a(mn);

                break;
            }

            if (!patched) {
                System.out.println("[HexChat]   ! WARNING: remainder carry hook NOT applied (pattern not found)");
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            return cw.toByteArray();

        } catch (Throwable t) {
            System.out.println("[HexChat]   ! ERROR patching GuiNewChat: " + t);
            t.printStackTrace();
            return bytes;
        }
    }

    /**
     * Vanilla 1.7.10 creates a remainder ChatComponentText(var17).
     * We inject right before that NEW:
     *
     *   var17 = HexChatWrapFix.prependCustomCarry(var16, var17);
     *
     * In Mojang’s compiled local layout, these are almost always:
     *   var16 = local 16 (trimmed visible line)
     *   var17 = local 17 (remainder)
     *
     * We patch specifically the constructor call that loads ALOAD 17.
     */
    private boolean patchFunc146237a(MethodNode mn) {
        InsnList insns = mn.instructions;
        boolean did = false;

        for (AbstractInsnNode n = insns.getFirst(); n != null; n = n.getNext()) {

            // Look for: NEW ChatComponentText; DUP; ALOAD 17; INVOKESPECIAL <init>(String)
            if (n.getOpcode() != NEW) continue;
            if (!(n instanceof TypeInsnNode)) continue;

            TypeInsnNode tin = (TypeInsnNode) n;
            if (!"net/minecraft/util/ChatComponentText".equals(tin.desc)) continue;

            AbstractInsnNode a = n.getNext();
            if (a == null || a.getOpcode() != DUP) continue;

            AbstractInsnNode b = a.getNext();
            if (!(b instanceof VarInsnNode) || b.getOpcode() != ALOAD) continue;

            VarInsnNode vLoad = (VarInsnNode) b;
            int argVar = vLoad.var;

            AbstractInsnNode c = b.getNext();
            if (!(c instanceof MethodInsnNode)) continue;

            MethodInsnNode mi = (MethodInsnNode) c;
            if (mi.getOpcode() != INVOKESPECIAL) continue;
            if (!"net/minecraft/util/ChatComponentText".equals(mi.owner)) continue;
            if (!"<init>".equals(mi.name)) continue;
            if (!"(Ljava/lang/String;)V".equals(mi.desc)) continue;

            // We only want the remainder constructor (almost always ALOAD 17)
            if (argVar != 17) {
                dbg("[HexChat]   skip ChatComponentText ctor using ALOAD " + argVar);
                continue;
            }

            // Inject before NEW:
            //   ALOAD 16
            //   ALOAD 17
            //   INVOKESTATIC HexChatWrapFix.prependCustomCarry (String,String)String
            //   ASTORE 17
            InsnList inject = new InsnList();
            inject.add(new VarInsnNode(ALOAD, 16));
            inject.add(new VarInsnNode(ALOAD, 17));
            inject.add(new MethodInsnNode(INVOKESTATIC,
                    "com/example/examplemod/client/HexChatWrapFix",
                    "prependCustomCarry",
                    "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                    false));
            inject.add(new VarInsnNode(ASTORE, 17));

            insns.insertBefore(n, inject);

            System.out.println("[HexChat]   ✓ Applied remainder carry hook (var16=16, var17=17)");
            did = true;
            break;
        }

        return did;
    }
}
