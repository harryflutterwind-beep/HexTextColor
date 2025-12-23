package com.example.examplemod.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;

import static org.objectweb.asm.Opcodes.*;

/**
 * Fully patches chat length across BOTH CLIENT + SERVER.
 *
 * Affects:
 *  - GuiChat                   (typing limit)
 *  - C01PacketChatMessage      (packet clamp)
 *  - NetHandlerPlayServer      (server clamp)
 *  - ServerConfigurationManager (server broadcast clamp)
 */
    public class ChatLengthTransformer implements IClassTransformer {

    private static final int NEW_LIMIT = 512;

    private static final String GUICHAT_DEOBF = "net.minecraft.client.gui.GuiChat";
    private static final String GUICHAT_OBF = "bcy";

    private static final String PACKET_DEOBF = "net.minecraft.network.play.client.C01PacketChatMessage";
    private static final String PACKET_OBF = "cw";

    private static final String NETHANDLER_DEOBF = "net.minecraft.network.NetHandlerPlayServer";
    private static final String NETHANDLER_OBF = "nh"; // common obf name

    private static final String SCM_DEOBF = "net.minecraft.server.management.ServerConfigurationManager";
    private static final String SCM_OBF = "oi"; // common obf name

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        // ─────────────────────────────────────────────
        // CLIENT: GuiChat
        // ─────────────────────────────────────────────
        if (GUICHAT_DEOBF.equals(transformedName) || GUICHAT_OBF.equals(name)) {
            System.out.println("[HexChat] Patching GuiChat…");
            return patchGuiChat(basicClass);
        }

        // ─────────────────────────────────────────────
        // CLIENT → SERVER PACKET
        // ─────────────────────────────────────────────
        if (PACKET_DEOBF.equals(transformedName) || PACKET_OBF.equals(name)) {
            System.out.println("[HexChat] Patching C01PacketChatMessage…");
            return patchChatPacket(basicClass);
        }

        // ─────────────────────────────────────────────
        // SERVER: NetHandlerPlayServer
        // Handles incoming chat message validation
        // ─────────────────────────────────────────────
        if (NETHANDLER_DEOBF.equals(transformedName) || NETHANDLER_OBF.equals(name)) {
            System.out.println("[HexChat] Patching NetHandlerPlayServer…");
            return patchNetHandler(basicClass);
        }

        // ─────────────────────────────────────────────
        // SERVER: ServerConfigurationManager
        // Handles broadcast / formatting
        // ─────────────────────────────────────────────
        if (SCM_DEOBF.equals(transformedName) || SCM_OBF.equals(name)) {
            System.out.println("[HexChat] Patching ServerConfigurationManager…");
            return patchSCM(basicClass);
        }

        return basicClass;
    }

    // =====================================================================
    //  CLIENT PATCH: GuiChat max char
    // =====================================================================
    private byte[] patchGuiChat(byte[] bytes) {
        return patchAllIntConstants(bytes, 100, NEW_LIMIT);
    }

    // =====================================================================
    //  CLIENT PACKET PATCH: C01PacketChatMessage
    // =====================================================================
    private byte[] patchChatPacket(byte[] bytes) {
        return patchAllIntConstants(bytes, 100, NEW_LIMIT);
    }

    // =====================================================================
    //  SERVER PATCH 1: NetHandlerPlayServer (incoming chat limit)
    // =====================================================================
    private byte[] patchNetHandler(byte[] bytes) {
        return patchAllIntConstants(bytes, 100, NEW_LIMIT);
    }

    // =====================================================================
    //  SERVER PATCH 2: ServerConfigurationManager (broadcast limit)
    // =====================================================================
    private byte[] patchSCM(byte[] bytes) {
        return patchAllIntConstants(bytes, 100, NEW_LIMIT);
    }

    // =====================================================================
//  UNIVERSAL INT-PATCHER (Java 6–safe, finals added)
// =====================================================================
    private byte[] patchAllIntConstants(byte[] bytes, final int oldValue, final int newValue) {
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        ClassVisitor cv = new ClassVisitor(ASM4, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String sig, String[] exceptions) {

                MethodVisitor mv = super.visitMethod(access, name, desc, sig, exceptions);

                return new MethodVisitor(ASM4, mv) {

                    @Override
                    public void visitIntInsn(int opcode, int operand) {
                        if (opcode == BIPUSH && operand == oldValue) {
                            super.visitLdcInsn(newValue);
                            return;
                        }
                        super.visitIntInsn(opcode, operand);
                    }

                    @Override
                    public void visitLdcInsn(Object cst) {
                        if (cst instanceof Integer && ((Integer) cst) == oldValue) {
                            super.visitLdcInsn(newValue);
                            return;
                        }
                        super.visitLdcInsn(cst);
                    }
                };
            }
        };

        cr.accept(cv, 0);
        return cw.toByteArray();
    }
}
