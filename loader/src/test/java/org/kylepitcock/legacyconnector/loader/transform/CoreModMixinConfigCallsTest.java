package org.kylepitcock.legacyconnector.loader.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class CoreModMixinConfigCallsTest implements Opcodes {
    private static final String MIXINS = "org/spongepowered/asm/mixin/Mixins";

    @Test
    void suppressesRegistrationOfAConfigManagedByTheTranslator() {
        ClassNode plugin = new ClassNode();
        MethodNode registration = new MethodNode(ACC_PUBLIC | ACC_STATIC, "register", "()V", null, null);
        registration.instructions.add(new LdcInsnNode("mixins.srparasites.json"));
        registration.instructions.add(addConfiguration());
        registration.instructions.add(new InsnNode(RETURN));
        plugin.methods.add(registration);

        int redirected = CoreModMixinConfigCalls.apply(plugin, Set.of("mixins.srparasites.json"));

        assertEquals(1, redirected);
        assertTrue(registration.instructions.get(1) instanceof InsnNode instruction && instruction.getOpcode() == POP);
        assertFalse(hasAddConfiguration(registration));
    }

    @Test
    void preservesUnmanagedAndDynamicallyNamedConfigurations() {
        ClassNode plugin = new ClassNode();
        plugin.name = "sample/Plugin";
        plugin.superName = "java/lang/Object";
        plugin.fields.add(new org.objectweb.asm.tree.FieldNode(ACC_PRIVATE | ACC_STATIC, "CONFIG", "Ljava/lang/String;", null, null));
        MethodNode registration = new MethodNode(ACC_PUBLIC | ACC_STATIC, "register", "()V", null, null);
        registration.instructions.add(new LdcInsnNode("mixins.other.json"));
        registration.instructions.add(addConfiguration());
        registration.instructions.add(new org.objectweb.asm.tree.FieldInsnNode(GETSTATIC, plugin.name, "CONFIG", "Ljava/lang/String;"));
        registration.instructions.add(addConfiguration());
        registration.instructions.add(new InsnNode(RETURN));
        plugin.methods.add(registration);

        assertEquals(0, CoreModMixinConfigCalls.apply(plugin, Set.of("mixins.srparasites.json")));
        assertTrue(hasAddConfiguration(registration));
    }

    private static MethodInsnNode addConfiguration() {
        return new MethodInsnNode(INVOKESTATIC, MIXINS, "addConfiguration", "(Ljava/lang/String;)V", false);
    }

    private static boolean hasAddConfiguration(MethodNode method) {
        for (org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(MIXINS) && call.name.equals("addConfiguration")) return true;
        }
        return false;
    }
}
