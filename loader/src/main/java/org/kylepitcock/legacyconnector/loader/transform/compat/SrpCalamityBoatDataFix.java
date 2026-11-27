package org.kylepitcock.legacyconnector.loader.transform.compat;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Corrects SRP Calamity's cooked-flesh chest-boat item registration. */
public final class SrpCalamityBoatDataFix implements Opcodes {
    private static final String ITEMS = "com/maidnarch/srpcalamity/init/CCCItems";
    private static final String CHEST_BOAT_ITEM = "cooked_flesh_chest_boat";
    private static final String BOAT = "com/maidnarch/srpcalamity/entity/transportation/boat/EntityCookedFleshBoat";
    private static final String CHEST_BOAT = "com/maidnarch/srpcalamity/entity/transportation/chestboat/EntityCookedFleshChestBoat";

    private SrpCalamityBoatDataFix() {}

    public static int apply(ClassNode node) {
        if (!ITEMS.equals(node.name)) return 0;
        for (MethodNode method : node.methods) {
            if (!"()V".equals(method.desc) || (method.access & ACC_STATIC) == 0) continue;
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof LdcInsnNode itemName) || !CHEST_BOAT_ITEM.equals(itemName.cst)) continue;
                AbstractInsnNode next = nextExecutable(instruction);
                if (next instanceof LdcInsnNode entityType && entityType.cst instanceof Type type
                        && type.getSort() == Type.OBJECT && BOAT.equals(type.getInternalName())) {
                    entityType.cst = Type.getObjectType(CHEST_BOAT);
                    return 1;
                }
            }
        }
        return 0;
    }

    private static AbstractInsnNode nextExecutable(AbstractInsnNode instruction) {
        AbstractInsnNode next = instruction.getNext();
        while (next != null && next.getOpcode() < 0) next = next.getNext();
        return next;
    }
}
