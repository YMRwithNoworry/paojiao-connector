package org.kylepitcock.legacyconnector.loader.transform;

import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;

/** Migrates legacy Pose enum factories used by a pre-codec $VALUES appender. */
final class PoseEnumMixin1201 implements Opcodes {
    private static final String POSE = "net/minecraft/world/entity/Pose";
    private static final String VALUES = "[L" + POSE + ";";
    private static final String OLD_FACTORY = "(Ljava/lang/String;I)L" + POSE + ";";
    private static final String NEW_FACTORY = "(Ljava/lang/String;II)L" + POSE + ";";
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/";
    private static final String CALLBACK = "(" + MIXIN + "injection/callback/CallbackInfo;)V";

    private PoseEnumMixin1201() {}

    static int apply(ClassNode node, String target) {
        if (!POSE.equals(target)) return 0;
        McMembers1201.Members host = McMembers1201.get(POSE);
        if (host == null || !host.methods().contains("<init>(Ljava/lang/String;II)V")
                || host.methods().contains("<init>(Ljava/lang/String;I)V") || !host.methods().contains("id()I")
                || !host.fields().contains("$VALUES:" + VALUES) || !host.fields().contains("BY_ID:Ljava/util/function/IntFunction;")
                || !host.fields().contains("STREAM_CODEC:Lnet/minecraft/network/codec/StreamCodec;")) return 0;
        boolean mutableValues = node.fields.stream().anyMatch(field -> "$VALUES".equals(field.name) && VALUES.equals(field.desc)
                && (field.access & ACC_STATIC) != 0 && annotation(field.visibleAnnotations, MIXIN + "Shadow;") != null
                && annotation(field.visibleAnnotations, MIXIN + "Final;") != null
                && annotation(field.visibleAnnotations, MIXIN + "Mutable;") != null);
        if (!mutableValues) return 0;

        MethodNode factory = null;
        for (MethodNode method : node.methods) {
            if (!OLD_FACTORY.equals(method.desc) || (method.access & (ACC_PUBLIC | ACC_STATIC)) != (ACC_PUBLIC | ACC_STATIC)) continue;
            AnnotationNode invoker = annotation(method.visibleAnnotations, MIXIN + "gen/Invoker;");
            if (!"<init>".equals(value(invoker, "value"))) continue;
            if (factory != null || method.signature != null || method.maxLocals != 2
                    || method.visibleParameterAnnotations != null || method.invisibleParameterAnnotations != null
                    || method.visibleTypeAnnotations != null || method.invisibleTypeAnnotations != null
                    || (method.parameters != null && method.parameters.size() != 2)) return 0;
            factory = method;
        }
        if (factory == null) return 0;

        MethodNode append = null;
        MethodInsnNode construct = null;
        for (MethodNode method : node.methods) {
            if (method.name.equals(factory.name) && NEW_FACTORY.equals(method.desc)) return 0;
            for (var insn : method.instructions) {
                if (!(insn instanceof MethodInsnNode call) || !node.name.equals(call.owner)
                        || !factory.name.equals(call.name) || !OLD_FACTORY.equals(call.desc)) continue;
                if (construct != null || call.getOpcode() != INVOKESTATIC || !appendsValues(node, method, call)) return 0;
                append = method;
                construct = call;
            }
        }
        if (append == null) return 0;

        // 1.21.1 publishes $VALUES before Pose.values() builds BY_ID and then STREAM_CODEC.
        // All existing IDs equal their ordinals; the original appender already assigns the next ordinals.
        at(append).visit("opcode", PUTSTATIC);
        factory.desc = NEW_FACTORY;
        factory.maxLocals = 3;
        if (factory.parameters != null) factory.parameters.add(new ParameterNode("networkId", 0));
        append.instructions.insertBefore(construct, new InsnNode(DUP));
        construct.desc = NEW_FACTORY;
        append.maxStack++;
        return 1;
    }

    private static boolean appendsValues(ClassNode node, MethodNode method, MethodInsnNode call) {
        if (!CALLBACK.equals(method.desc) || (method.access & ACC_STATIC) == 0) return false;
        AnnotationNode inject = annotation(method.visibleAnnotations, MIXIN + "injection/Inject;");
        AnnotationNode at = at(method);
        if (!List.of("<clinit>").equals(value(inject, "method")) || value(inject, "target") != null
                || value(inject, "slice") != null || value(inject, "locals") != null
                || Boolean.TRUE.equals(value(inject, "cancellable")) || at == null
                || !"FIELD".equals(value(at, "value")) || !("L" + POSE + ";$VALUES:" + VALUES).equals(value(at, "target"))
                || value(at, "opcode") != null || value(at, "ordinal") != null || value(at, "slice") != null
                || value(at, "args") != null || !(value(at, "shift") instanceof String[] shift)
                || shift.length != 2 || !(MIXIN + "injection/At$Shift;").equals(shift[0]) || !"AFTER".equals(shift[1])) return false;

        // Recognize an enum appender, not an arbitrary call to the old constructor: last.ordinal() + offset,
        // then List.add, and the updated array assigned once to the shadow before returning.
        AbstractInsnNode sum = previousCode(call);
        AbstractInsnNode offset = previousCode(sum);
        AbstractInsnNode ordinal = previousCode(offset);
        if (sum == null || sum.getOpcode() != IADD || offset == null || offset.getOpcode() != ILOAD
                || !(ordinal instanceof MethodInsnNode ordinalCall) || ordinalCall.getOpcode() != INVOKEVIRTUAL
                || !(POSE.equals(ordinalCall.owner) || "java/lang/Enum".equals(ordinalCall.owner))
                || !"ordinal".equals(ordinalCall.name) || !"()I".equals(ordinalCall.desc)
                || !(nextCode(call) instanceof MethodInsnNode add) || add.getOpcode() != INVOKEINTERFACE
                || !"java/util/List".equals(add.owner) || !"add".equals(add.name) || !"(Ljava/lang/Object;)Z".equals(add.desc)) return false;
        int reads = 0;
        int writes = 0;
        for (var insn : method.instructions) {
            if (!(insn instanceof FieldInsnNode field) || !node.name.equals(field.owner)
                    || !"$VALUES".equals(field.name) || !VALUES.equals(field.desc)) continue;
            if (field.getOpcode() == GETSTATIC) reads++;
            else if (field.getOpcode() == PUTSTATIC) {
                AbstractInsnNode after = nextCode(field);
                if (after == null || after.getOpcode() != RETURN) return false;
                writes++;
            }
        }
        return reads == 1 && writes == 1;
    }

    private static AbstractInsnNode previousCode(AbstractInsnNode insn) {
        if (insn != null) do { insn = insn.getPrevious(); } while (insn != null && insn.getOpcode() < 0);
        return insn;
    }

    private static AbstractInsnNode nextCode(AbstractInsnNode insn) {
        do { insn = insn.getNext(); } while (insn != null && insn.getOpcode() < 0);
        return insn;
    }

    private static AnnotationNode at(MethodNode method) {
        Object points = value(annotation(method.visibleAnnotations, MIXIN + "injection/Inject;"), "at");
        return points instanceof List<?> list && list.size() == 1 && list.getFirst() instanceof AnnotationNode at ? at : null;
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String desc) {
        if (annotations != null) for (AnnotationNode annotation : annotations) if (desc.equals(annotation.desc)) return annotation;
        return null;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation != null && annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }
}
