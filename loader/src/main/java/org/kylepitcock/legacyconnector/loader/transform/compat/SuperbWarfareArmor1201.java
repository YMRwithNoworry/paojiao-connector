package org.kylepitcock.legacyconnector.loader.transform.compat;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** DragonRise's old Superb Warfare material constants are registered holders in the native port. */
public final class SuperbWarfareArmor1201 {
    public static final String MATERIALS = "com/atsuishio/superbwarfare/tiers/ModArmorMaterial";
    private static final String HOLDER = "Lnet/neoforged/neoforge/registries/DeferredHolder;";
    private static final String MIGRATE = "org/kylepitcock/legacyconnector/v1_20_1/migrate/";

    private SuperbWarfareArmor1201() {}

    public static boolean accepts(java.util.Set<String> modIds, java.util.Set<String> nativeClasses) {
        return modIds.contains("dragonrise_reforge") && nativeClasses.contains(MATERIALS);
    }

    public static int apply(ClassNode node) {
        int changes = 0;
        for (var method : node.methods) {
            for (var instruction : method.instructions.toArray()) {
                if (!(instruction instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETSTATIC
                        || !field.owner.equals(MATERIALS) || !field.desc.equals("L" + MATERIALS + ";")) continue;
                int durability = switch (field.name) {
                    case "CEMENTED_CARBIDE" -> 50;
                    case "STEEL" -> 35;
                    default -> 0;
                };
                if (durability == 0) continue;
                field.desc = HOLDER;
                var multiplier = new LdcInsnNode(durability);
                method.instructions.insert(field, multiplier);
                method.instructions.insert(multiplier, new MethodInsnNode(Opcodes.INVOKESTATIC, MIGRATE + "ArmorCompat",
                        "registeredMaterial", "(Lnet/minecraft/core/Holder;I)L" + MIGRATE + "LegacyArmorMaterial;", false));
                changes++;
            }
        }
        return changes;
    }
}
