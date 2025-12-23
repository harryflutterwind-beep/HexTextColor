// src/main/java/com/example/examplemod/core/chat/ServerChatLimitTransformer.java
package com.example.examplemod.core.chat;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;

/**
 * ServerChatLimitTransformer
 *  - Runs on the *server*.
 *  - Patches net.minecraft.network.play.client.C01PacketChatMessage
 *    so that readPacketData() calls PacketBuffer.readStringFromBuffer(512)
 *    instead of 100.
 *
 *  This removes the vanilla 100-char hard cap for player chat packets.
 */
public class ServerChatLimitTransformer implements IClassTransformer {

    // Deobf name on Forge 1.7.10; packet classes are not obfuscated.
    private static final String TARGET_CLASS = "net.minecraft.network.play.client.C01PacketChatMessage";

    // Keep this in sync with your client ChatLengthTransformer (512)
    private static final int NEW_LIMIT = 1024;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        // Only patch the chat packet class
        if (!TARGET_CLASS.equals(transformedName)) {
            return basicClass;
        }

        System.out.println("[HexChat] Patching C01PacketChatMessage chat limit to " + NEW_LIMIT + "...");

        ClassReader cr = new ClassReader(basicClass);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        ClassVisitor cv = new ClassVisitor(ASM4, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String mName, String desc,
                                             String signature, String[] exceptions) {

                MethodVisitor mv = super.visitMethod(access, mName, desc, signature, exceptions);

                // Look for: void readPacketData(PacketBuffer buf)
                if (!"readPacketData".equals(mName)
                        || !"(Lnet/minecraft/network/PacketBuffer;)V".equals(desc)) {
                    return mv;
                }

                return new MethodVisitor(ASM4, mv) {

                    private boolean waitingForReadString = false;

                    @Override
                    public void visitIntInsn(int opcode, int operand) {
                        if (opcode == BIPUSH && operand == 100) {
                            waitingForReadString = true; // mark that we want to replace THIS one
                        } else {
                            super.visitIntInsn(opcode, operand);
                        }
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String desc) {
                        if (waitingForReadString
                                && opcode == INVOKEVIRTUAL
                                && "net/minecraft/network/PacketBuffer".equals(owner)
                                && "readStringFromBuffer".equals(name)
                                && "(I)Ljava/lang/String;".equals(desc)) {

                            // Replace BIPUSH 100 → SIPUSH 512
                            super.visitIntInsn(SIPUSH, NEW_LIMIT);
                            waitingForReadString = false;
                        }

                        super.visitMethodInsn(opcode, owner, name, desc);
                    }
                };
            }
        };

        cr.accept(cv, 0);
        return cw.toByteArray();
    }
}