package com.example.examplemod.core.chat;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;

import static org.objectweb.asm.Opcodes.*;

public class GuiChatSendTransformer implements IClassTransformer {

    private static final String TARGET = "net.minecraft.client.gui.GuiChat";
    private static final String TARGET_OBF = "bcz"; // GuiChat in 1.7.10 obf

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {

        if (basicClass == null) return null;

        if (!TARGET.equals(transformedName) && !TARGET_OBF.equals(name))
            return basicClass;

        System.out.println("[HexChat] Transforming GuiChat#sendChatMessage");

        ClassReader cr = new ClassReader(basicClass);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        ClassVisitor cv = new ClassVisitor(ASM4, cw) {

            @Override
            public MethodVisitor visitMethod(int access, String mName, String desc,
                                             String sig, String[] exceptions) {

                // MCP: sendChatMessage(String)
                // SRG: func_146403_a(Ljava/lang/String;)V
                boolean match =
                        ("sendChatMessage".equals(mName) && "(Ljava/lang/String;)V".equals(desc)) ||
                                ("func_146403_a".equals(mName) && "(Ljava/lang/String;)V".equals(desc));

                if (!match)
                    return super.visitMethod(access, mName, desc, sig, exceptions);

                System.out.println("[HexChat] Patching sendChatMessage...");

                MethodVisitor mv = super.visitMethod(access, mName, desc, sig, exceptions);

                return new MethodVisitor(ASM4, mv) {

                    @Override
                    public void visitCode() {
                        super.visitCode();

                        // Load 'msg' onto the stack
                        mv.visitVarInsn(ALOAD, 1);

                        // Call ChatSendHook.onClientSendChat(msg)
                        mv.visitMethodInsn(INVOKESTATIC,
                                "com/example/examplemod/client/ChatSendHook",
                                "onClientSendChat",
                                "(Ljava/lang/String;)Ljava/lang/String;",
                                false);

                        // Store modified msg back into local var 1
                        mv.visitVarInsn(ASTORE, 1);
                    }
                };
            }
        };

        cr.accept(cv, 0);
        return cw.toByteArray();
    }
}
