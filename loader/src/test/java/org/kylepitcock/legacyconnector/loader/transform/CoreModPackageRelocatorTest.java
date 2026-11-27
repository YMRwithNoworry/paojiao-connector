package org.kylepitcock.legacyconnector.loader.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

class CoreModPackageRelocatorTest implements Opcodes {
    @Test
    void relocatesCoremodAndReferencesWhenItsClassSharesTheMixinPackage() {
        String coremod = "com/example/mixin/MixinLoader";
        String configResource = "legacyconnector.legacy.example.mixins.json";
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        ClassNode plugin = new ClassNode();
        plugin.name = coremod;
        plugin.superName = "java/lang/Object";
        classes.put(plugin.name, plugin);
        ClassNode caller = new ClassNode();
        caller.name = "com/example/Mod";
        caller.superName = "java/lang/Object";
        caller.fields.add(new FieldNode(ACC_PRIVATE, "loader", "L" + coremod + ";", null, null));
        MethodNode createLoader = new MethodNode(ACC_PUBLIC, "createLoader", "()L" + coremod + ";", null, null);
        createLoader.instructions.add(new TypeInsnNode(NEW, coremod));
        createLoader.instructions.add(new InsnNode(DUP));
        createLoader.instructions.add(new MethodInsnNode(INVOKESPECIAL, coremod, "<init>", "()V", false));
        createLoader.instructions.add(new InsnNode(ARETURN));
        caller.methods.add(createLoader);
        classes.put(caller.name, caller);

        Map<String, byte[]> resources = Map.of(configResource,
                "{\"package\":\"com.example.mixin\",\"mixins\":[\"EntityMixin\"]}".getBytes(StandardCharsets.UTF_8));

        String relocated = CoreModPackageRelocator.relocateIfInMixinPackage(
                classes, coremod, "SpartanWeaponry-1_12_2-1_6_1", List.of(configResource), resources);

        assertEquals("legacyconnector/legacycoremod/SpartanWeaponry_1_12_2_1_6_1/com/example/mixin/MixinLoader", relocated);
        assertFalse(classes.containsKey(coremod));
        assertTrue(classes.containsKey(relocated));
        ClassNode rewrittenCaller = classes.get("com/example/Mod");
        assertEquals("L" + relocated + ";", rewrittenCaller.fields.getFirst().desc);
        assertEquals("()L" + relocated + ";", rewrittenCaller.methods.getFirst().desc);
        assertTrue(rewrittenCaller.methods.getFirst().instructions.getFirst() instanceof TypeInsnNode create
                && create.desc.equals(relocated));
        assertTrue(rewrittenCaller.methods.getFirst().instructions.get(2) instanceof MethodInsnNode constructor
                && constructor.owner.equals(relocated));
        assertTrue(new String(resources.get(configResource), StandardCharsets.UTF_8).contains("com.example.mixin"));
    }

    @Test
    void leavesCoremodsOutsideMixinPackagesUntouched() {
        String coremod = "com/example/MixinLoader";
        ClassNode plugin = new ClassNode();
        plugin.name = coremod;
        plugin.superName = "java/lang/Object";
        Map<String, ClassNode> classes = Map.of(coremod, plugin);
        String configResource = "legacyconnector.legacy.example.mixins.json";
        Map<String, byte[]> resources = Map.of(configResource,
                "{\"package\":\"com.example.mixin\",\"mixins\":[\"EntityMixin\"]}".getBytes(StandardCharsets.UTF_8));

        String relocated = CoreModPackageRelocator.relocateIfInMixinPackage(
                classes, coremod, "example_mod", List.of(configResource), resources);

        assertEquals(coremod, relocated);
        assertTrue(classes.containsKey(coremod));
    }
}
