package org.kylepitcock.legacyconnector.loader.transform.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

class ModernMayhem1201Test implements Opcodes {
    private static final String OUTER = "net/tkg/ModernMayhem/server/item/curios/facewear/NVGGogglesItem";
    private static final String INNER_ONE = OUTER + "$1";
    private static final String INNER_TWO = OUTER + "$2";

    @Test
    void acceptsOnlyModernMayhem() {
        assertTrue(ModernMayhem1201.accepts(Set.of("mm")));
        assertTrue(!ModernMayhem1201.accepts(Set.of("other")));
    }

    @Test
    void mergesTheTwoClientExtensionsAndAddsTheItemRenderer() {
        ClassNode outer = new ClassNode();
        outer.name = OUTER;
        MethodNode initialize = new MethodNode(ACC_PUBLIC, "initializeClient", "(Ljava/util/function/Consumer;)V", null, null);
        addExtension( initialize, INNER_ONE);
        addExtension(initialize, INNER_TWO);
        initialize.instructions.add(new InsnNode(RETURN));
        outer.methods.add(initialize);

        ClassNode first = new ClassNode();
        first.name = INNER_ONE;
        ClassNode second = new ClassNode();
        second.name = INNER_TWO;

        assertEquals(1, ModernMayhem1201.apply(outer));
        assertEquals(1, countAccepts(initialize));
        assertEquals(1, ModernMayhem1201.apply(first));
        assertTrue(first.methods.stream().anyMatch(method -> method.name.equals("getCustomRenderer")));
        assertEquals(0, ModernMayhem1201.apply(second));
        assertEquals(0, ModernMayhem1201.apply(outer));
        assertEquals(0, ModernMayhem1201.apply(first));
    }

    @Test
    void coversCurioBackpackExtensionsWithTheirDedicatedRenderer() {
        String outerName = "net/tkg/ModernMayhem/server/item/curios/back/BackpackItem";
        ClassNode outer = new ClassNode();
        outer.name = outerName;
        MethodNode initialize = new MethodNode(ACC_PUBLIC, "initializeClient", "(Ljava/util/function/Consumer;)V", null, null);
        addExtension(initialize, outerName + "$1");
        addExtension(initialize, outerName + "$2");
        initialize.instructions.add(new InsnNode(RETURN));
        outer.methods.add(initialize);
        ClassNode first = new ClassNode();
        first.name = outerName + "$1";

        assertEquals(1, ModernMayhem1201.apply(outer));
        assertEquals(1, ModernMayhem1201.apply(first));
        assertTrue(first.methods.stream().anyMatch(method -> method.name.equals("getCustomRenderer")));
    }

    private static void addExtension(MethodNode method, String owner) {
        method.instructions.add(new VarInsnNode(ALOAD, 1));
        method.instructions.add(new TypeInsnNode(NEW, owner));
        method.instructions.add(new InsnNode(DUP));
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        method.instructions.add(new MethodInsnNode(INVOKESPECIAL, owner, "<init>", "()V", false));
        method.instructions.add(new MethodInsnNode(INVOKEINTERFACE, "java/util/function/Consumer", "accept", "(Ljava/lang/Object;)V", true));
    }

    private static int countAccepts(MethodNode method) {
        int count = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("java/util/function/Consumer") && call.name.equals("accept")) count++;
        }
        return count;
    }
}
