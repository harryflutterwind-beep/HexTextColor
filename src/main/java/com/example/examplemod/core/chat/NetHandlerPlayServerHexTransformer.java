// src/main/java/com/example/examplemod/core/chat/NetHandlerPlayServerHexTransformer.java
package com.example.examplemod.core.chat;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;

import static org.objectweb.asm.Opcodes.*;

public class NetHandlerPlayServerHexTransformer implements IClassTransformer {

    private static final String TARGET_CLASS = "net.minecraft.network.NetHandlerPlayServer";
    private static final String TARGET_CLASS_OBF = "nh"; // adjust if your SRG says otherwise

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        if (!TARGET_CLASS.equals(transformedName) && !TARGET_CLASS_OBF.equals(transformedName)) {
            return basicClass;
        }

        System.out.println("[HexChat] Transforming NetHandlerPlayServer for sanitizeIncoming + isAllowedChatChar…");

        ClassReader cr = new ClassReader(basicClass);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        ClassVisitor cv = new ClassVisitor(ASM4, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String mName, String desc, String sig, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, mName, desc, sig, exceptions);

                // processChatMessage(C01PacketChatMessage)
                if (!"processChatMessage".equals(mName) && !"func_147354_a".equals(mName)) {
                    return mv;
                }

                return new MethodVisitor(ASM4, mv) {

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String methodDesc, boolean itf) {

                        // 1) Wrap C01PacketChatMessage.getMessage()
                        if (opcode == INVOKEVIRTUAL &&
                                ("net/minecraft/network/play/client/C01PacketChatMessage".equals(owner)
                                        || "iw".equals(owner)) &&
                                ("getMessage".equals(name) || "func_149439_c".equals(name)) &&
                                "()Ljava/lang/String;".equals(methodDesc)) {

                            // Original call: String s = packet.getMessage()
                            super.visitMethodInsn(opcode, owner, name, methodDesc, itf);
                            // Stack: ..., String

                            // Wrap with ChatHexServerSanitizer.sanitizeIncoming(String)
                            super.visitMethodInsn(INVOKESTATIC,
                                    "com/example/examplemod/server/ChatHexServerSanitizer",
                                    "sanitizeIncoming",
                                    "(Ljava/lang/String;)Ljava/lang/String;",
                                    false);
                            return;
                        }

                        // 2) Redirect ChatAllowedCharacters.isAllowedCharacter(char)
                        if (opcode == INVOKESTATIC &&
                                ("net/minecraft/util/ChatAllowedCharacters".equals(owner)
                                        || "oi".equals(owner)) &&
                                ("isAllowedCharacter".equals(name) || "func_71566_a".equals(name)) &&
                                "(C)Z".equals(methodDesc)) {

                            // Instead of calling the vanilla method, call our relaxed one
                            super.visitMethodInsn(INVOKESTATIC,
                                    "com/example/examplemod/server/ChatHexServerSanitizer",
                                    "isAllowedChatChar",
                                    "(C)Z",
                                    false);
                            return;
                        }

                        super.visitMethodInsn(opcode, owner, name, methodDesc, itf);
                    }
                };
            }
        };

        cr.accept(cv, 0);
        return cw.toByteArray();
    }
}
