package org.kylepitcock.legacyconnector.loader.transform.compat;

import java.util.List;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/** Ports Extra Gore 5.4's vanilla terrain wrapper, including the warmup that closes its loading screen. */
public final class ExtraGoreRenderMixin1201 implements Opcodes {
    private static final String MIXIN = "com/eruannie_9/extragore/mixin/render/VanillaTranslucentMixin";
    private static final String TARGET = "net/minecraft/client/renderer/LevelRenderer";
    private static final String PARTICLES = "com/eruannie_9/extragore/particle/BloodParticleHandler";
    private static final String LAYER = "com/eruannie_9/extragore/particle/render/layer/BloodTranslucentLayer";
    private static final String POSE = "Lcom/mojang/blaze3d/vertex/PoseStack;";
    private static final String MATRIX = "Lorg/joml/Matrix4f;";
    private static final String RENDER_TYPE = "Lnet/minecraft/client/renderer/RenderType;";
    private static final String CAMERA = "Lnet/minecraft/client/Camera;";
    private static final String DELTA = "Lnet/minecraft/client/DeltaTracker;";
    private static final String CALLBACK = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
    private static final String CLIENT_COMPAT = "org/kylepitcock/legacyconnector/v1_20_1/migrate/ClientCompat";
    private static final String OLD_LAYER = "(" + RENDER_TYPE + POSE + "DDD" + MATRIX + ")V";
    private static final String SECTION_ARGS = RENDER_TYPE + "DDD" + MATRIX + MATRIX;
    private static final String SECTION_LAYER = "(" + SECTION_ARGS + ")V";
    private static final String OLD_REDIRECT = "(L" + TARGET + ";" + OLD_LAYER.substring(1);
    private static final String NATIVE_REDIRECT = "(L" + TARGET + ";" + SECTION_ARGS + ")V";
    private static final String RENDER_TAIL = "Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;";
    private static final String OLD_INJECT = "(" + POSE + "FJZ" + CAMERA + RENDER_TAIL + MATRIX + CALLBACK + ")V";
    private static final String NATIVE_INJECT = "(" + DELTA + "Z" + CAMERA + RENDER_TAIL + MATRIX + MATRIX + CALLBACK + ")V";
    private static final Handle LAMBDA = new Handle(H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                    + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
    private static final Type VOID_METHOD = Type.getMethodType("()V");

    private ExtraGoreRenderMixin1201() {}

    public static int apply(ClassNode node, String target) {
        if (!MIXIN.equals(node.name) || !TARGET.equals(target)) return 0;
        MethodNode shadow = find(node, "renderChunkLayer", OLD_LAYER);
        MethodNode begin = find(node, "beginTranslucentFrame", OLD_INJECT);
        MethodNode finish = find(node, "finishNativePreparation", OLD_INJECT);
        MethodNode fabulous = find(node, "fabulousBlood", OLD_INJECT);
        MethodNode fabulousTerrain = find(node, "fabulousTerrain", OLD_REDIRECT);
        MethodNode translucentTerrain = find(node, "translucentTerrain", OLD_REDIRECT);
        MethodNode fabulousLambda = find(node, "lambda$fabulousTerrain$0", OLD_LAYER);
        MethodNode translucentLambda = find(node, "lambda$translucentTerrain$1", OLD_LAYER);
        if (shadow == null || begin == null || finish == null || fabulous == null || fabulousTerrain == null
                || translucentTerrain == null || fabulousLambda == null || translucentLambda == null) return 0;
        AnnotationNode fabulousAt = redirectAt(fabulousTerrain);
        AnnotationNode translucentAt = redirectAt(translucentTerrain);
        if (fabulousAt == null || translucentAt == null) return 0;

        var invoke = new MethodNode(ACC_PUBLIC | ACC_ABSTRACT, "legacyconnector$renderSectionLayer", SECTION_LAYER, null, null);
        var invoker = new AnnotationNode("Lorg/spongepowered/asm/mixin/gen/Invoker;");
        invoker.visit("value", "renderSectionLayer");
        invoke.visibleAnnotations = List.of(invoker);
        node.methods.removeAll(List.of(shadow, fabulousLambda, translucentLambda));
        node.methods.add(invoke);
        node.methods.add(terrainInvoker(node.name, invoke.name));
        adaptTerrain(fabulousTerrain, fabulousAt, node.name);
        adaptTerrain(translucentTerrain, translucentAt, node.name);
        adaptFrameCallback(node, begin, false);
        adaptFrameCallback(node, fabulous, true);
        // This callback doesn't read any arguments, so Mixin can pass only CallbackInfo.
        finish.desc = "(" + CALLBACK + ")V";
        finish.localVariables = null;
        return 6;
    }

    private static MethodNode find(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) if (method.name.equals(name) && method.desc.equals(desc)) return method;
        return null;
    }

    private static AnnotationNode redirectAt(MethodNode method) {
        if (method.visibleAnnotations == null) return null;
        for (AnnotationNode annotation : method.visibleAnnotations) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;") || annotation.values == null) continue;
            for (int i = 0; i < annotation.values.size(); i += 2) {
                if (annotation.values.get(i).equals("at") && annotation.values.get(i + 1) instanceof AnnotationNode at) return at;
            }
        }
        return null;
    }

    private static void adaptTerrain(MethodNode method, AnnotationNode at, String owner) {
        for (int i = 0; i < at.values.size(); i += 2) {
            if (at.values.get(i).equals("target")) at.values.set(i + 1, "L" + TARGET + ";renderSectionLayer" + SECTION_LAYER);
        }
        method.desc = NATIVE_REDIRECT;
        method.instructions.clear();
        method.localVariables = null;
        var code = new GeneratorAdapter(method, method.access, method.name, method.desc);
        code.loadArg(1);
        code.loadArg(5);
        callStatic(code, CLIENT_COMPAT, "poseFromModelView", "(" + MATRIX + ")" + POSE);
        code.loadArg(6);
        code.loadThis();
        for (int i = 1; i <= 6; i++) code.loadArg(i);
        code.invokeDynamic("run", "(L" + owner + ";" + SECTION_ARGS + ")Ljava/lang/Runnable;", LAMBDA,
                VOID_METHOD, new Handle(H_INVOKEVIRTUAL, owner, "legacyconnector$drawSection", SECTION_LAYER, false), VOID_METHOD);
        callStatic(code, LAYER, "renderTerrain", "(" + RENDER_TYPE + POSE + MATRIX + "Ljava/lang/Runnable;)V");
        code.returnValue();
        code.endMethod();
    }

    private static MethodNode terrainInvoker(String owner, String invoker) {
        var method = new MethodNode(ACC_PRIVATE | ACC_SYNTHETIC, "legacyconnector$drawSection", SECTION_LAYER, null, null);
        var code = new GeneratorAdapter(method, method.access, method.name, method.desc);
        code.loadThis();
        code.loadArgs();
        code.invokeVirtual(Type.getObjectType(owner), new Method(invoker, SECTION_LAYER));
        code.returnValue();
        code.endMethod();
        return method;
    }

    private static void adaptFrameCallback(ClassNode node, MethodNode old, boolean fabulous) {
        var callback = new MethodNode(ACC_PRIVATE, "legacyconnector$" + old.name, NATIVE_INJECT, null, null);
        callback.visibleAnnotations = old.visibleAnnotations;
        callback.invisibleAnnotations = old.invisibleAnnotations;
        old.visibleAnnotations = null;
        old.invisibleAnnotations = null;
        var code = new GeneratorAdapter(callback, callback.access, callback.name, callback.desc);
        code.loadThis();
        if (fabulous) {
            code.loadArg(5);
            callStatic(code, CLIENT_COMPAT, "poseFromModelView", "(" + MATRIX + ")" + POSE);
        } else {
            // The original HEAD callback only uses the partial tick argument.
            code.visitInsn(ACONST_NULL);
        }
        code.loadArg(0);
        code.push(false);
        code.invokeInterface(Type.getType(DELTA), new Method("getGameTimeDeltaPartialTick", "(Z)F"));
        // The original callbacks do not read the old frame deadline.
        code.push(0L);
        for (int i = 1; i <= 4; i++) code.loadArg(i);
        code.loadArg(6);
        code.loadArg(7);
        code.invokeVirtual(Type.getObjectType(node.name), new Method(old.name, old.desc));
        code.returnValue();
        code.endMethod();
        node.methods.add(callback);
    }

    private static void callStatic(GeneratorAdapter code, String owner, String name, String desc) {
        code.invokeStatic(Type.getObjectType(owner), new Method(name, desc));
    }
}
