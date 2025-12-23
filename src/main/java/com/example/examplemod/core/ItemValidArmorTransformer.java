package com.example.examplemod.core;

import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;

import static org.objectweb.asm.Opcodes.*;

public class ItemValidArmorTransformer implements IClassTransformer {

    private static final String HOOK_OWNER = "com/example/examplemod/core/WearableArmorHooks";

    private static final String TARGET_DEOBF = "net.minecraft.item.Item";
    private static final String TARGET_OBF   = "adb"; // common 1.7.10

    private static final String OWNER_DEOBF  = "net/minecraft/item/Item";
    private static final String OWNER_OBF    = "adb";

    // boolean isValidArmor(ItemStack stack, int armorType, Entity entity)
    private static final String DESC_DEOBF =
            "(Lnet/minecraft/item/ItemStack;ILnet/minecraft/entity/Entity;)Z";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        boolean isTarget = TARGET_DEOBF.equals(transformedName) || TARGET_OBF.equals(name);
        if (!isTarget) return basicClass;

        final boolean obf = TARGET_OBF.equals(name);
        final String owner = obf ? OWNER_OBF : OWNER_DEOBF;

        try {
            ClassNode cn = new ClassNode();
            new ClassReader(basicClass).accept(cn, 0);

            FMLDeobfuscatingRemapper remap = FMLDeobfuscatingRemapper.INSTANCE;
            String descRuntime = remap.mapMethodDesc(DESC_DEOBF);
            String hookDescRuntime = remap.mapMethodDesc("(Lnet/minecraft/item/ItemStack;I)Z");

            boolean patched = false;

            for (MethodNode mn : cn.methods) {
                boolean nameOk =
                        "isValidArmor".equals(mn.name) ||
                                "func_82789_a".equals(mn.name) ||
                                "a".equals(mn.name);

                boolean descOk = DESC_DEOBF.equals(mn.desc) || descRuntime.equals(mn.desc);
                if (!nameOk || !descOk) continue;

                InsnList insn = new InsnList();
                LabelNode L_continue = new LabelNode();

                // stack, armorType
                insn.add(new VarInsnNode(ALOAD, 1));
                insn.add(new VarInsnNode(ILOAD, 2));

                insn.add(new MethodInsnNode(INVOKESTATIC,
                        HOOK_OWNER,
                        "allowExtraArmorItems",
                        hookDescRuntime,
                        false));

                insn.add(new JumpInsnNode(IFEQ, L_continue));
                insn.add(new InsnNode(ICONST_1));
                insn.add(new InsnNode(IRETURN));
                insn.add(L_continue);

                mn.instructions.insert(insn);
                patched = true;
                break;
            }

            if (!patched) return basicClass;

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            return cw.toByteArray();

        } catch (Throwable t) {
            return basicClass;
        }
    }
}
