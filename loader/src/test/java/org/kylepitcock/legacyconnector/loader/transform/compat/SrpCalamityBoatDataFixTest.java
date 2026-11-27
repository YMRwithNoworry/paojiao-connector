package org.kylepitcock.legacyconnector.loader.transform.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

class SrpCalamityBoatDataFixTest implements Opcodes {
    private static final String NORMAL_BOAT = "com/maidnarch/srpcalamity/entity/transportation/boat/EntityCookedFleshBoat";
    private static final String CHEST_BOAT = "com/maidnarch/srpcalamity/entity/transportation/chestboat/EntityCookedFleshChestBoat";

    @Test
    void bindsCookedFleshChestBoatItemToItsChestBoatEntity() {
        ClassNode items = new ClassNode();
        items.name = "com/maidnarch/srpcalamity/init/CCCItems";
        MethodNode initializer = new MethodNode(ACC_STATIC, "<clinit>", "()V", null, null);
        initializer.instructions.add(new LdcInsnNode("cooked_flesh_boat"));
        initializer.instructions.add(new LdcInsnNode(Type.getObjectType(NORMAL_BOAT)));
        initializer.instructions.add(new InsnNode(POP));
        initializer.instructions.add(new LdcInsnNode("cooked_flesh_chest_boat"));
        initializer.instructions.add(new LdcInsnNode(Type.getObjectType(NORMAL_BOAT)));
        initializer.instructions.add(new InsnNode(RETURN));
        items.methods.add(initializer);

        int fixes = SrpCalamityBoatDataFix.apply(items);

        assertEquals(1, fixes);
        List<LdcInsnNode> classes = new ArrayList<>();
        for (var instruction : initializer.instructions) {
            if (instruction instanceof LdcInsnNode constant && constant.cst instanceof Type) classes.add(constant);
        }
        assertEquals(NORMAL_BOAT, ((Type) classes.get(0).cst).getInternalName());
        assertEquals(CHEST_BOAT, ((Type) classes.get(1).cst).getInternalName());
        assertTrue(SrpCalamityBoatDataFix.apply(items) == 0);
    }

    @Test
    void ignoresOtherClassInitializers() {
        ClassNode unrelated = new ClassNode();
        unrelated.name = "com/example/Items";
        MethodNode initializer = new MethodNode(ACC_STATIC, "<clinit>", "()V", null, null);
        initializer.instructions.add(new LdcInsnNode("cooked_flesh_chest_boat"));
        initializer.instructions.add(new LdcInsnNode(Type.getObjectType(NORMAL_BOAT)));
        initializer.instructions.add(new InsnNode(RETURN));
        unrelated.methods.add(initializer);

        assertEquals(0, SrpCalamityBoatDataFix.apply(unrelated));
    }

    @Test
    void fixesStaticRegistrationMethodUsedByCalamity() {
        ClassNode items = new ClassNode();
        items.name = "com/maidnarch/srpcalamity/init/CCCItems";
        MethodNode initializer = new MethodNode(ACC_PUBLIC | ACC_STATIC, "initItems", "()V", null, null);
        initializer.instructions.add(new LdcInsnNode("cooked_flesh_chest_boat"));
        initializer.instructions.add(new LdcInsnNode(Type.getObjectType(NORMAL_BOAT)));
        initializer.instructions.add(new InsnNode(RETURN));
        items.methods.add(initializer);

        assertEquals(1, SrpCalamityBoatDataFix.apply(items));
        LdcInsnNode entity = null;
        for (var instruction : initializer.instructions) {
            if (instruction instanceof LdcInsnNode constant && constant.cst instanceof Type) {
                entity = constant;
            }
        }
        assertTrue(entity != null);
        assertEquals(CHEST_BOAT, ((Type) entity.cst).getInternalName());
    }
}
