package org.kylepitcock.legacyconnector.loader.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.kylepitcock.legacyconnector.loader.LegacyVersion;
import org.kylepitcock.legacyconnector.loader.mapping.McpMappings;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

class LegacyMixinSelectorRemappingTest {
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
    private static final String SHIM = "legacyconnector/v1_12_2/";

    @Test
    void mixinMethodAndAtTargetStringsRelocateDescriptorsAndSrgNames() {
        AnnotationNode at = new AnnotationNode(AT);
        at.values = new ArrayList<>(List.of(
                "value", "INVOKE",
                "target", "Lnet/minecraft/entity/Entity;func_123456_a(Lnet/minecraft/nbt/NBTTagCompound;)V"));
        AnnotationNode inject = new AnnotationNode(INJECT);
        inject.values = new ArrayList<>(List.of(
                "method", "writeEntityToNBT(Lnet/minecraft/nbt/NBTTagCompound;)V",
                "at", at));

        ClassNode input = new ClassNode();
        input.version = Opcodes.V1_8;
        input.access = Opcodes.ACC_PUBLIC;
        input.name = "fixture/ParasiteMixin";
        input.superName = "java/lang/Object";
        MethodNode handler = new MethodNode(Opcodes.ACC_PUBLIC, "inject", "()V", null, null);
        handler.invisibleAnnotations = new ArrayList<>(List.of(inject));
        input.methods.add(handler);

        McpMappings mappings = new McpMappings(Map.of("func_123456_a", "writeCustomNbt"), Map.of());
        LegacyRemapper remapper = new LegacyRemapper(LegacyVersion.V1_12_2, mappings, null, null);
        ClassNode output = new ClassNode();
        input.accept(new ClassRemapper(output, remapper));

        AnnotationNode mappedInject = output.methods.getFirst().invisibleAnnotations.getFirst();
        assertEquals("writeEntityToNBT(L" + SHIM + "net/minecraft/nbt/NBTTagCompound;)V", value(mappedInject, "method"));
        AnnotationNode mappedAt = (AnnotationNode) value(mappedInject, "at");
        assertEquals("L" + SHIM + "net/minecraft/entity/Entity;writeCustomNbt(L" + SHIM
                + "net/minecraft/nbt/NBTTagCompound;)V", value(mappedAt, "target"));
    }

    @Test
    void aMethodSelectorWithoutAnOwnerStillMapsItsSrgNameAndDescriptor() {
        McpMappings mappings = new McpMappings(Map.of("func_123456_a", "writeCustomNbt"), Map.of());
        LegacyRemapper remapper = new LegacyRemapper(LegacyVersion.V1_12_2, mappings, null, null);

        assertEquals("writeCustomNbt(L" + SHIM + "net/minecraft/nbt/NBTTagCompound;)V",
                remapper.mapString("func_123456_a(Lnet/minecraft/nbt/NBTTagCompound;)V"));
        assertEquals("unrelated", remapper.mapString("unrelated"));
    }

    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        throw new AssertionError("Missing annotation value: " + key);
    }
}
