package org.kylepitcock.legacyconnector.loader.transform.compat;

import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Keeps Crawl Fix's hold-to-crawl cancellation independent of TaCZ's tick event type. */
public final class CrawlKeyMixin1201 implements Opcodes {
    private static final String MIXIN = "com/ns/crawlfix/mixin/CrawlKeyMixinV12";
    private static final String TARGET = "com/tacz/guns/client/input/CrawlKey";
    private static final String EVENT = "Llegacyconnector/v1_20_1/net/minecraftforge/event/TickEvent$ClientTickEvent;";
    private static final String CALLBACK = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";

    private CrawlKeyMixin1201() {}

    public static int apply(ClassNode node, String target) {
        if (!MIXIN.equals(node.name) || !TARGET.equals(target)) return 0;
        for (MethodNode method : node.methods) {
            if (!"crawlfix$suppressHolding".equals(method.name) || !("(" + EVENT + CALLBACK + ")V").equals(method.desc)
                    || (method.access & (ACC_PRIVATE | ACC_STATIC)) != (ACC_PRIVATE | ACC_STATIC)
                    || method.maxLocals != 2 || method.signature != null || !isHoldInjection(method.visibleAnnotations)
                    || method.visibleParameterAnnotations != null || method.invisibleParameterAnnotations != null
                    || method.visibleTypeAnnotations != null || method.invisibleTypeAnnotations != null
                    || method.visibleLocalVariableAnnotations != null || method.invisibleLocalVariableAnnotations != null
                    || (method.parameters != null && method.parameters.size() != 2)) continue;

            // Mixin accepts a callback-only signature without local capture. Never drop a parameter the handler uses.
            boolean unusedEvent = true;
            for (var insn : method.instructions) {
                if (insn instanceof VarInsnNode var && (var.getOpcode() != ALOAD || var.var != 1)
                        || insn instanceof IincInsnNode || insn instanceof FrameNode frame && frame.type != F_SAME) {
                    unusedEvent = false;
                    break;
                }
            }
            if (!unusedEvent) continue;
            for (var insn : method.instructions) if (insn instanceof VarInsnNode var) var.var = 0;
            if (method.localVariables != null) {
                method.localVariables.removeIf(local -> local.index == 0);
                for (var local : method.localVariables) local.index = 0;
            }
            if (method.parameters != null) method.parameters.removeFirst();
            method.desc = "(" + CALLBACK + ")V";
            method.maxLocals = 1;
            return 1;
        }
        return 0;
    }

    private static boolean isHoldInjection(List<AnnotationNode> annotations) {
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations) {
            if (INJECT.equals(annotation.desc) && List.of("onCrawlHoldingInput").equals(value(annotation, "method"))
                    && value(annotation, "target") == null && value(annotation, "locals") == null
                    && Boolean.TRUE.equals(value(annotation, "cancellable"))) return true;
        }
        return false;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }
}
