// src/main/java/com/example/examplemod/core/chat/GuiChatHexTransformer.java
package com.example.examplemod.core.chat;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;

import static org.objectweb.asm.Opcodes.*;

public class GuiChatHexTransformer implements IClassTransformer {

    private static final String TARGET_CLASS = "net.minecraft.client.gui.GuiChat";
    private static final String TARGET_CLASS_OBF = "bcy"; // same as your ChatLengthTransformer

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        if (!TARGET_CLASS.equals(transformedName) && !TARGET_CLASS_OBF.equals(transformedName)) {
            return basicClass;
        }

        System.out.println("[HexChat] Transforming GuiChat for preSendChat…");

        ClassReader cr = new ClassReader(basicClass);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        ClassVisitor cv = new ClassVisitor(ASM4, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String mName, String desc, String sig, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, mName, desc, sig, exceptions);

                // keyTyped(CI)V is where GuiChat sends the message
                if (!"keyTyped".equals(mName) && !"func_73869_a".equals(mName)) {
                    return mv;
                }

                return new MethodVisitor(ASM4, mv) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String methodDesc, boolean itf) {
                        // look for: this.mc.thePlayer.sendChatMessage(String)
                        if (opcode == INVOKEVIRTUAL &&
                                ("net/minecraft/client/entity/EntityClientPlayerMP".equals(owner)
                                        || "bjk".equals(owner)) &&
                                ("sendChatMessage".equals(name) || "func_71165_d".equals(name)) &&
                                "(Ljava/lang/String;)V".equals(methodDesc)) {

                            // Stack before call: ..., String
                            // We want to wrap param with ChatHexClientSanitizer.preSendChat(String)

                            // call our static helper
                            super.visitMethodInsn(INVOKESTATIC,
                                    "com/example/examplemod/client/ChatHexClientSanitizer",
                                    "preSendChat",
                                    "(Ljava/lang/String;)Ljava/lang/String;",
                                    false);
                            // result (String) stays on stack

                            // now call original sendChatMessage
                            super.visitMethodInsn(opcode, owner, name, methodDesc, itf);
                        } else {
                            super.visitMethodInsn(opcode, owner, name, methodDesc, itf);
                        }
                    }
                };
            }
        };

        cr.accept(cv, 0);
        return cw.toByteArray();
    }
}
