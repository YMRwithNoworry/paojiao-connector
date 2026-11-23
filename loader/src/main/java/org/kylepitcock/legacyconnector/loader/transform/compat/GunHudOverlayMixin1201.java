package org.kylepitcock.legacyconnector.loader.transform.compat;

import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Adapts TaCZ Presence's cancellable HUD injection to TaCZ's native NeoForge render signature. */
public final class GunHudOverlayMixin1201 implements Opcodes {
    private static final String MIXIN = "com/tacz/presence/mixin/GunHudOverlayMixin";
    private static final String TARGET = "com/tacz/guns/client/gui/overlay/GunHudOverlay";
    private static final String FORGE_GUI = "legacyconnector/v1_20_1/net/minecraftforge/client/gui/overlay/ForgeGui";
    private static final String GRAPHICS = "net/minecraft/client/gui/GuiGraphics";
    private static final String DELTA = "net/minecraft/client/DeltaTracker";
    private static final String CALLBACK = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String LEGACY_ARGS = "L" + FORGE_GUI + ";L" + GRAPHICS + ";FII";
    private static final String NATIVE_ARGS = "L" + GRAPHICS + ";L" + DELTA + ";";

    private GunHudOverlayMixin1201() {}

    public static int apply(ClassNode node, String target) {
        if (!MIXIN.equals(node.name) || !TARGET.equals(target)) return 0;
        for (MethodNode original : node.methods) {
            if (!"onRender".equals(original.name) || !("(" + LEGACY_ARGS + CALLBACK + ")V").equals(original.desc)
                    || (original.access & (ACC_PUBLIC | ACC_STATIC)) != ACC_PUBLIC || original.visibleAnnotations == null) continue;
            for (AnnotationNode inject : original.visibleAnnotations) {
                if (!INJECT.equals(inject.desc) || !List.of("render").equals(value(inject, "method"))
                        || value(inject, "target") != null || !Boolean.TRUE.equals(value(inject, "cancellable"))) continue;

                AnnotationNode nativeInject = new AnnotationNode(INJECT);
                inject.accept(nativeInject);
                // Either release of TaCZ can be present. The absent overload must not invalidate the other injection.
                selectRender(inject, LEGACY_ARGS);
                selectRender(nativeInject, NATIVE_ARGS);
                MethodNode adapter = new MethodNode(ACC_PRIVATE | ACC_SYNTHETIC, "legacyconnector$nativeGunHud",
                        "(" + NATIVE_ARGS + CALLBACK + ")V", null, null);
                adapter.visibleAnnotations = List.of(nativeInject);
                var code = adapter.instructions;
                code.add(new VarInsnNode(ALOAD, 0));
                code.add(new MethodInsnNode(INVOKESTATIC, FORGE_GUI, "mcc$instance", "()L" + FORGE_GUI + ";", false));
                code.add(new VarInsnNode(ALOAD, 1));
                code.add(new VarInsnNode(ALOAD, 2));
                code.add(new InsnNode(ICONST_0));
                code.add(new MethodInsnNode(INVOKEINTERFACE, DELTA, "getGameTimeDeltaPartialTick", "(Z)F", true));
                code.add(new VarInsnNode(ALOAD, 1));
                code.add(new MethodInsnNode(INVOKEVIRTUAL, GRAPHICS, "guiWidth", "()I", false));
                code.add(new VarInsnNode(ALOAD, 1));
                code.add(new MethodInsnNode(INVOKEVIRTUAL, GRAPHICS, "guiHeight", "()I", false));
                code.add(new VarInsnNode(ALOAD, 3));
                code.add(new MethodInsnNode(INVOKEVIRTUAL, node.name, original.name, original.desc, false));
                code.add(new InsnNode(RETURN));
                adapter.maxLocals = 4;
                adapter.maxStack = 7;
                node.methods.add(adapter);
                return 1;
            }
        }
        return 0;
    }

    private static void selectRender(AnnotationNode annotation, String args) {
        // A structured selector keeps the descriptor even when Mixin's permissive refmap pass is enabled.
        AnnotationNode selector = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Desc;");
        selector.values = List.of("value", "render", "args", List.of(Type.getArgumentTypes("(" + args + ")V")),
                "ret", Type.VOID_TYPE, "min", 0, "max", 1);
        setValue(annotation, "method", List.of());
        setValue(annotation, "target", List.of(selector));
        setValue(annotation, "require", 0);
        setValue(annotation, "expect", 0);
    }

    private static void setValue(AnnotationNode annotation, String key, Object value) {
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) {
                annotation.values.set(i + 1, value);
                return;
            }
        }
        annotation.visit(key, value);
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }
}
