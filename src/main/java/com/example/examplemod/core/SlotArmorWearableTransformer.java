package com.example.examplemod.core;

import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;

import static org.objectweb.asm.Opcodes.*;

public class SlotArmorWearableTransformer implements IClassTransformer {

    private static final String HOOK_OWNER = "com/example/examplemod/core/WearableArmorHooks";

    private static final String TARGET_DEOBF = "net.minecraft.inventory.SlotArmor";
    private static final String TARGET_OBF   = "yc"; // common 1.7.10

    private static final String OWNER_DEOBF  = "net/minecraft/inventory/SlotArmor";
    private static final String OWNER_OBF    = "yc";

    private static final String IS_VALID_DESC = "(Lnet/minecraft/item/ItemStack;)Z";
    private static final String HOOK_DESC     = "(Lnet/minecraft/item/ItemStack;I)Z";

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

            String isValidDescRuntime = remap.mapMethodDesc(IS_VALID_DESC);
            String hookDescRuntime    = remap.mapMethodDesc(HOOK_DESC);

            String armorTypeField = remap.mapFieldName(owner, "armorType", "I");

            boolean patched = false;

            for (MethodNode mn : cn.methods) {
                boolean nameOk =
                        "isItemValid".equals(mn.name) ||
                                "func_75214_a".equals(mn.name) ||
                                "a".equals(mn.name);

                boolean descOk = IS_VALID_DESC.equals(mn.desc) || isValidDescRuntime.equals(mn.desc);
                if (!nameOk || !descOk) continue;

                InsnList insn = new InsnList();
                LabelNode L_continue = new LabelNode();

                insn.add(new VarInsnNode(ALOAD, 1)); // stack
                insn.add(new VarInsnNode(ALOAD, 0)); // this
                insn.add(new FieldInsnNode(GETFIELD, owner, armorTypeField, "I")); // armorType

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
