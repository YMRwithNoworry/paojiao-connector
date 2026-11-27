package org.kylepitcock.legacyconnector.loader.transform.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

class TaczArcana1201Test implements Opcodes {
    private static final String CONFIG = "group/taczexpands/client/config/PreLoadConfig";
    private static final String PRELOAD = "com/tacz/guns/config/PreLoadModConfig";
    private static final String CONFIG_SPEC = "Lnet/neoforged/neoforge/common/ModConfigSpec;";
    private static final String CONFIG_BRIDGE = "legacyconnector/v1_20_1/compat/TaczArcanaConfig1201";
    private static final String CAMERA = "group/taczexpands/client/mixin/accessor/IAccessorCamera";
    private static final String BULLET_MIXIN = "group/taczexpands/common/mixin/MixinEntityKineticBullet";
    private static final String CALLBACK = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
    private static final String OLD_BUFFER = "Lnet/minecraft/network/FriendlyByteBuf;";
    private static final String NEW_BUFFER = "Lnet/minecraft/network/RegistryFriendlyByteBuf;";
    private static final String GUN_HUD_MIXIN = "group/taczexpands/client/mixin/MixinGunHudOverlay";
    private static final String LEGACY_HUD = "(Llegacyconnector/v1_20_1/net/minecraftforge/client/gui/overlay/ForgeGui;Lnet/minecraft/client/gui/GuiGraphics;FIILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    private static final String NATIVE_HUD = "(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    private static final String NATIVE_RENDER = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V";
    private static final String GAME_RENDERER_MIXIN = "group/taczexpands/client/mixin/MixinGameRenderer";
    private static final String LEGACY_RENDER_LEVEL = "(FJLcom/mojang/blaze3d/vertex/PoseStack;)V";
    private static final String NATIVE_RENDER_LEVEL = "(Lnet/minecraft/client/DeltaTracker;)V";

    @TempDir
    Path tempDir;

    @Test
    void preservesOnlyARecognizedArcanaWrapperForEmbeddedModExtraction() throws Exception {
        Path wrapper = tempDir.resolve("arcana-wrapper.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(wrapper))) {
            put(zip, "META-INF/services/net.minecraftforge.forgespi.locating.IModLocator", "group.taczexpands.locator.ModLocator");
            put(zip, "group/taczexpands/locator/ModLocator.class", "test");
            put(zip, "META-INF/mod.jar", "embedded");
        }
        try (ZipFile zip = new ZipFile(wrapper.toFile())) {
            assertEquals("META-INF/mod.jar", TaczArcana1201.embeddedPayload(zip).getName());
        }

        Path ordinary = tempDir.resolve("ordinary.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(ordinary))) {
            put(zip, "META-INF/mods.toml", "modLoader=\"javafml\"");
            put(zip, "META-INF/services/net.minecraftforge.forgespi.locating.IModLocator", "group.taczexpands.locator.ModLocator");
            put(zip, "group/taczexpands/locator/ModLocator.class", "test");
            put(zip, "META-INF/mod.jar", "embedded");
        }
        try (ZipFile zip = new ZipFile(ordinary.toFile())) {
            assertNull(TaczArcana1201.embeddedPayload(zip), "ordinary Forge mods are not wrapper-extracted");
        }
    }

    @Test
    void keepsTheModSpecAndLoadsTheClientConfigThroughTheNativeBridge() {
        ClassNode node = new ClassNode();
        node.name = CONFIG;
        node.superName = "java/lang/Object";
        node.fields.add(new FieldNode(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "spec", CONFIG_SPEC, null, null));

        MethodNode config = new MethodNode(ACC_PUBLIC, "getModConfig", "()L" + PRELOAD + ";", null, null);
        config.instructions.add(new InsnNode(ACONST_NULL));
        config.instructions.add(new InsnNode(ARETURN));
        node.methods.add(config);

        MethodNode load = new MethodNode(ACC_PUBLIC, "load", "(Ljava/nio/file/Path;)V", null, null);
        load.instructions.add(new VarInsnNode(ALOAD, 0));
        load.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, CONFIG, "getModConfig", "()L" + PRELOAD + ";", false));
        load.instructions.add(new InsnNode(POP));
        load.instructions.add(new InsnNode(RETURN));
        node.methods.add(load);

        MethodNode setting = new MethodNode(ACC_PUBLIC, "getEnableDefaultStateMachineReplacement", "()Z", null, null);
        setting.instructions.add(new InsnNode(ICONST_0));
        setting.instructions.add(new InsnNode(IRETURN));
        node.methods.add(setting);

        assertEquals(2, TaczArcana1201.apply(node));
        assertEquals(List.of("load", "getEnableDefaultStateMachineReplacement"), node.methods.stream().map(m -> m.name).toList());
        assertEquals(3, load.instructions.size());
        assertEquals(GETSTATIC, load.instructions.getFirst().getOpcode());
        assertEquals("spec", ((FieldInsnNode) load.instructions.getFirst()).name);
        MethodInsnNode bridge = (MethodInsnNode) load.instructions.getFirst().getNext();
        assertEquals(CONFIG_BRIDGE, bridge.owner);
        assertEquals("(" + CONFIG_SPEC + ")V", bridge.desc);
        assertFalse(node.methods.stream().anyMatch(m -> m.desc.contains(PRELOAD)));
        assertEquals(0, TaczArcana1201.apply(node), "transformation is idempotent");
    }

    @Test
    void narrowsAllCameraArgumentsWithoutCorruptingTheOperandStack() throws Exception {
        ClassNode accessor = new ClassNode();
        accessor.name = CAMERA;
        accessor.superName = "java/lang/Object";
        accessor.methods.add(new MethodNode(ACC_PUBLIC | ACC_ABSTRACT, "move", "(DDD)V", null, null));
        accessor.methods.add(new MethodNode(ACC_PUBLIC | ACC_ABSTRACT, "n8b5mhI6", "(DDD)V", null, null));
        assertEquals(2, TaczArcana1201.apply(accessor));
        assertEquals(List.of("(FFF)V", "(FFF)V"), accessor.methods.stream().map(m -> m.desc).toList());

        ClassNode caller = new ClassNode();
        caller.name = "group/taczexpands/dist/CameraCaller";
        caller.superName = "java/lang/Object";
        MethodNode invoke = new MethodNode(ACC_PUBLIC | ACC_STATIC, "move", "(L" + CAMERA + ";DDDDDD)V", null, null);
        invoke.maxLocals = 13;
        invoke.maxStack = 7;
        for (String name : List.of("move", "n8b5mhI6")) {
            invoke.instructions.add(new VarInsnNode(ALOAD, 0));
            invoke.instructions.add(new InsnNode(DCONST_0));
            invoke.instructions.add(new InsnNode(DCONST_0));
            invoke.instructions.add(new InsnNode(DCONST_0));
            invoke.instructions.add(new MethodInsnNode(INVOKEINTERFACE, CAMERA, name, "(DDD)V", true));
        }
        invoke.instructions.add(new InsnNode(RETURN));
        caller.methods.add(invoke);

        assertEquals(2, TaczArcana1201.apply(caller));
        assertEquals(6, countOpcode(invoke, D2F));
        assertEquals(0, TaczArcana1201.apply(caller), "transformation is idempotent");
        new Analyzer<>(new BasicVerifier()).analyze(caller.name, invoke);
    }

    @Test
    void moduleIsVisibleToTheEnumLookupAndToTaCzsStartupSnapshot() throws Exception {
        ClassNode source = new ClassNode();
        try (var stream = AttachmentFixture.class.getResourceAsStream("TaczArcana1201Test$AttachmentFixture.class")) {
            new ClassReader(stream).accept(source, 0);
        }
        ClassNode node = new ClassNode();
        source.accept(new ClassRemapper(node, new SimpleRemapper(source.name, "com/tacz/guns/api/item/attachment/AttachmentType")));

        String previousMods = System.getProperty("legacyconnector.forge1201.mods");
        try {
            System.setProperty("legacyconnector.forge1201.mods", TaczArcana1201.MOD_ID);
            TaczArcanaCoreMod transformer = new TaczArcanaCoreMod();
            transformer.transform(node, null);
            transformer.transform(node, null);
        } finally {
            if (previousMods == null) System.clearProperty("legacyconnector.forge1201.mods");
            else System.setProperty("legacyconnector.forge1201.mods", previousMods);
        }

        var writer = new ClassWriter(0);
        node.accept(writer);
        byte[] bytes = writer.toByteArray();
        Class<?> type = new ClassLoader() {
            Class<?> define() { return defineClass(null, bytes, 0, bytes.length); }
        }.define();

        Object module = type.getMethod("valueOf", String.class).invoke(null, "MODULE");
        assertEquals(List.of("SCOPE", "NONE", "MODULE"), java.util.Arrays.stream(type.getEnumConstants()).map(Object::toString).toList());
        assertEquals(module, type.getMethod("fromId", int.class).invoke(null, 2));
        assertEquals("module", type.getField("MODULE").getAnnotation(com.google.gson.annotations.SerializedName.class).value());
    }
    @Test
    void addsAForgeChannelAbiFieldToTheNativeTaczHandler() {
        ClassNode node = new ClassNode();
        node.name = "com/tacz/guns/network/NetworkHandler";
        node.superName = "java/lang/Object";
        MethodNode clinit = new MethodNode(ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.instructions.add(new InsnNode(RETURN));
        node.methods.add(clinit);

        TaczArcanaCoreMod.addLegacyChannel(node);
        TaczArcanaCoreMod.addLegacyChannel(node);

        FieldNode channel = node.fields.stream().filter(field -> "CHANNEL".equals(field.name)).findFirst().orElseThrow();
        assertEquals(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, channel.access);
        assertEquals("Lnet/minecraftforge/network/simple/SimpleChannel;", channel.desc);
        assertTrue(hasCall(clinit, "net/minecraftforge/network/simple/SimpleChannel", "legacy"));
        assertEquals(1, node.fields.stream().filter(field -> "CHANNEL".equals(field.name)).count());
    }

    @Test
    void coremodIsDiscoverableBeforeLegacyModDiscoveryCompletes() {
        assertTrue(new TaczArcanaCoreMod().getTransformers().iterator().hasNext());
    }


    public enum AttachmentFixture {
        SCOPE, NONE;

        private static final AttachmentFixture[] BY_ID = values();

        public static AttachmentFixture fromId(int id) { return BY_ID[id]; }
    }

    @Test
    void updatesOnlyTaCzSpawnDataInjectionDescriptors() {
        ClassNode node = new ClassNode();
        node.name = BULLET_MIXIN;
        node.superName = "java/lang/Object";
        MethodNode write = callback("postWriteSpawnData");
        MethodNode read = callback("postReadSpawnData");
        MethodNode unrelated = new MethodNode(ACC_PUBLIC, "other", "(" + OLD_BUFFER + ")V", null, null);
        unrelated.instructions.add(new InsnNode(RETURN));
        node.methods.addAll(List.of(write, read, unrelated));

        assertEquals(2, TaczArcana1201.apply(node));
        assertEquals("(" + NEW_BUFFER + CALLBACK + ")V", write.desc);
        assertEquals("(" + NEW_BUFFER + CALLBACK + ")V", read.desc);
        assertEquals("(" + OLD_BUFFER + ")V", unrelated.desc);
        assertEquals(0, TaczArcana1201.apply(node), "transformation is idempotent");
    }

    @Test
    void preservesTheProtectedReleaseProbe() {
        ClassNode node = new ClassNode();
        node.name = "group/taczexpands/dist/tK3DCEqh";
        node.superName = "java/lang/Object";
        MethodNode setup = new MethodNode(ACC_PUBLIC, "k6XMAhhm", "()V", null, null);
        setup.instructions.add(new FieldInsnNode(GETSTATIC, "group/taczexpands/dist/Un202Fce", "rDuY3DUX", "Lgroup/taczexpands/dist/Un202Fce;"));
        setup.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "group/taczexpands/dist/Un202Fce", "H6IGCe7c", "()Z", false));
        setup.instructions.add(new InsnNode(POP));
        setup.instructions.add(new InsnNode(RETURN));
        node.methods.add(setup);

        assertEquals(0, TaczArcana1201.apply(node));
        assertTrue(hasCall(setup, "group/taczexpands/dist/Un202Fce", "H6IGCe7c"));
    }

    @Test
    void adaptsHudHandlersAndKeepsTheirHeadAndTailInjectionPoints() {
        ClassNode node = new ClassNode();
        node.name = GUN_HUD_MIXIN;
        node.superName = "java/lang/Object";
        MethodNode head = annotatedHud("preRender", "HEAD");
        MethodNode tail = annotatedHud("hookRender", "TAIL");
        node.methods.addAll(List.of(head, tail));

        assertEquals(2, TaczArcana1201.apply(node));
        for (String handler : List.of("preRender", "hookRender")) {
            MethodNode adapter = node.methods.stream().filter(m -> m.name.equals("legacyconnector$" + handler + "$native")).findFirst().orElseThrow();
            assertEquals(NATIVE_HUD, adapter.desc);
            AnnotationNode inject = findInject(adapter);
            assertNotNull(inject);
            assertEquals(List.of(NATIVE_RENDER), annotationValue(inject, "method"));
            AnnotationNode at = (AnnotationNode) annotationValue(inject, "at").getFirst();
            assertEquals(handler.equals("preRender") ? "HEAD" : "TAIL", annotationValue(at, "value").getFirst());
        }
        assertFalse(hasInject(head));
        assertFalse(hasInject(tail));
        assertEquals(0, TaczArcana1201.apply(node), "transformation is idempotent");
    }
    @Test
    void mapsArcanaRenderLevelShadowToNativeDeltaTracker() {
        ClassNode node = new ClassNode();
        node.name = GAME_RENDERER_MIXIN;
        node.superName = "java/lang/Object";
        MethodNode shadow = new MethodNode(ACC_PUBLIC | ACC_ABSTRACT, "renderLevel", LEGACY_RENDER_LEVEL, null, null);
        shadow.visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lorg/spongepowered/asm/mixin/Shadow;")));
        node.methods.add(shadow);

        assertEquals(1, TaczArcana1201.apply(node));
        assertEquals(NATIVE_RENDER_LEVEL, shadow.desc);
        assertNotNull(shadow.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Shadow;")).findFirst().orElse(null));
        assertEquals(0, TaczArcana1201.apply(node), "transformation is idempotent");
    }

    @Test
    void adaptsSimpleBedrockFirstPersonApisToNeoForgePre() throws Exception {
        String oldTick = "legacyconnector/v1_20_1/net/minecraftforge/event/TickEvent$ClientTickEvent";
        String oldTickDesc = "(L" + oldTick + ";)V";
        String oldPhase = "legacyconnector/v1_20_1/net/minecraftforge/event/TickEvent$Phase";
        String nativeTick = "net/neoforged/neoforge/client/event/ClientTickEvent$Pre";
        String nativeTickDesc = "(L" + nativeTick + ";)V";

        ClassNode plugin = new ClassNode();
        plugin.name = "group/taczexpands/client/coremod/TACZExpandsClientMixinPlugin";
        MethodNode detect = new MethodNode(ACC_PRIVATE, "detect", "()V", null, null);
        detect.instructions.add(new LdcInsnNode("(Lnet/minecraftforge/event/TickEvent$ClientTickEvent;)V"));
        detect.instructions.add(new InsnNode(POP));
        detect.instructions.add(new InsnNode(RETURN));
        plugin.methods.add(detect);
        assertEquals(1, TaczArcana1201.apply(plugin));
        assertEquals(nativeTickDesc, ((LdcInsnNode) detect.instructions.getFirst()).cst);

        ClassNode invoker = new ClassNode();
        invoker.name = "group/taczexpands/client/mixin/accessor/sbm/ModernFirstPersonRenderHandlerInvoker";
        MethodNode handUpdate = new MethodNode(ACC_PUBLIC | ACC_STATIC, "mOjU0RrB", oldTickDesc, null, null);
        AnnotationNode invokerAnnotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/gen/Invoker;");
        invokerAnnotation.values = new ArrayList<>(List.of("value", "onClientTick"));
        handUpdate.visibleAnnotations = new ArrayList<>(List.of(invokerAnnotation));
        invoker.methods.add(handUpdate);
        assertEquals(1, TaczArcana1201.apply(invoker));
        assertEquals(nativeTickDesc, handUpdate.desc);

        ClassNode compat = new ClassNode();
        compat.name = "group/taczexpands/dist/L8e6fKLw";
        MethodNode refresh = new MethodNode(ACC_PUBLIC, "RO9a0Mwh", "(Lnet/minecraft/world/item/ItemStack;)V", null, null);
        refresh.instructions.add(new TypeInsnNode(NEW, oldTick));
        refresh.instructions.add(new InsnNode(DUP));
        refresh.instructions.add(new FieldInsnNode(GETSTATIC, oldPhase, "START", "L" + oldPhase + ";"));
        refresh.instructions.add(new MethodInsnNode(INVOKESPECIAL, oldTick, "<init>", "(L" + oldPhase + ";)V", false));
        refresh.instructions.add(new MethodInsnNode(INVOKESTATIC,
                "group/taczexpands/client/mixin/accessor/sbm/ModernFirstPersonRenderHandlerInvoker",
                "mOjU0RrB", oldTickDesc, true));
        refresh.instructions.add(new InsnNode(RETURN));
        compat.methods.add(refresh);
        refresh.maxStack = 3;
        refresh.maxLocals = 2;
        MethodNode unrelated = new MethodNode(ACC_PRIVATE, "ordinaryTick", "()L" + oldTick + ";", null, null);
        unrelated.instructions.add(new TypeInsnNode(NEW, oldTick));
        unrelated.instructions.add(new InsnNode(DUP));
        unrelated.instructions.add(new FieldInsnNode(GETSTATIC, oldPhase, "START", "L" + oldPhase + ";"));
        unrelated.instructions.add(new MethodInsnNode(INVOKESPECIAL, oldTick, "<init>", "(L" + oldPhase + ";)V", false));
        unrelated.instructions.add(new InsnNode(ARETURN));
        compat.methods.add(unrelated);
        assertEquals(4, TaczArcana1201.apply(compat));
        assertEquals(nativeTick, ((TypeInsnNode) refresh.instructions.get(0)).desc);
        MethodInsnNode constructor = (MethodInsnNode) refresh.instructions.get(2);
        assertEquals(nativeTick, constructor.owner);
        assertEquals("()V", constructor.desc);
        MethodInsnNode update = (MethodInsnNode) refresh.instructions.get(3);
        assertEquals(nativeTickDesc, update.desc);
        assertFalse(hasField(refresh, oldPhase, "START"));
        assertTrue(hasField(unrelated, oldPhase, "START"), "ordinary tick creation keeps its Forge ABI");
        new Analyzer<>(new BasicVerifier()).analyze(compat.name, refresh);
        assertEquals(0, TaczArcana1201.apply(plugin), "plugin transformation is idempotent");
        assertEquals(0, TaczArcana1201.apply(invoker), "invoker transformation is idempotent");
        assertEquals(0, TaczArcana1201.apply(compat), "compat transformation is idempotent");
    }

    @Test
    void mapsLegacyPlayProtocolAttributeCheckToNativeConnectionApi() throws Exception {
        ClassNode node = new ClassNode();
        node.name = "group/taczexpands/dist/iCpEa2a5";
        node.superName = "java/lang/Object";
        MethodNode check = new MethodNode(ACC_PUBLIC, "TcSCKdm4", "()Z", null, null);
        check.instructions.add(new FieldInsnNode(GETSTATIC, "net/minecraft/network/Connection", "ATTRIBUTE_PROTOCOL",
                "Lio/netty/util/AttributeKey;"));
        check.instructions.add(new FieldInsnNode(GETSTATIC, "net/minecraft/network/ConnectionProtocol", "PLAY",
                "Lnet/minecraft/network/ConnectionProtocol;"));
        check.instructions.add(new InsnNode(ICONST_1));
        check.instructions.add(new InsnNode(IRETURN));
        check.maxStack = 2;
        node.methods.add(check);

        assertEquals(1, TaczArcana1201.apply(node));
        assertEquals(2, check.instructions.size());
        MethodInsnNode bridge = (MethodInsnNode) check.instructions.getFirst();
        assertEquals(INVOKESTATIC, bridge.getOpcode());
        assertEquals("legacyconnector/v1_20_1/compat/TaczArcanaClientCompat1201", bridge.owner);
        assertEquals("isPlayClientConnection", bridge.name);
        assertEquals("()Z", bridge.desc);
        new Analyzer<>(new BasicVerifier()).analyze(node.name, check);
        assertEquals(0, TaczArcana1201.apply(node), "connection check transformation is idempotent");
    }

    @Test
    void mapsMinecraftTextureManagerFieldReadToPublicGetter() throws Exception {
        ClassNode node = new ClassNode();
        node.name = "group/taczexpands/dist/VitqCY60";
        node.superName = "java/lang/Object";
        MethodNode textureManager = new MethodNode(ACC_PUBLIC | ACC_STATIC, "readTextureManager",
                "(Lnet/minecraft/client/Minecraft;)Lnet/minecraft/client/renderer/texture/TextureManager;", null, null);
        textureManager.instructions.add(new VarInsnNode(ALOAD, 0));
        textureManager.instructions.add(new FieldInsnNode(GETFIELD, "net/minecraft/client/Minecraft", "textureManager",
                "Lnet/minecraft/client/renderer/texture/TextureManager;"));
        textureManager.instructions.add(new InsnNode(ARETURN));
        textureManager.maxStack = 1;
        textureManager.maxLocals = 1;
        node.methods.add(textureManager);

        assertEquals(1, TaczArcana1201.apply(node));
        assertFalse(hasField(textureManager, "net/minecraft/client/Minecraft", "textureManager"));
        assertTrue(hasCall(textureManager, "net/minecraft/client/Minecraft", "getTextureManager"));
        new Analyzer<>(new BasicVerifier()).analyze(node.name, textureManager);
        assertEquals(0, TaczArcana1201.apply(node), "texture manager transformation is idempotent");
    }

    private static boolean hasField(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && owner.equals(field.owner) && name.equals(field.name)) return true;
        }
        return false;
    }

    private static MethodNode annotatedHud(String name, String at) {
        MethodNode method = new MethodNode(ACC_PUBLIC, name, LEGACY_HUD, null, null);
        AnnotationNode atAnnotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/At;");
        atAnnotation.values = new ArrayList<>(List.of("value", at));
        AnnotationNode inject = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;");
        inject.values = new ArrayList<>(List.of("method", List.of("render"), "at", atAnnotation, "cancellable", true));
        method.visibleAnnotations = new ArrayList<>(List.of(inject));
        method.instructions.add(new InsnNode(RETURN));
        return method;
    }

    private static AnnotationNode findInject(MethodNode method) {
        if (method.visibleAnnotations == null) return null;
        return method.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElse(null);
    }

    private static List<Object> annotationValue(AnnotationNode annotation, String name) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2) {
            if (name.equals(annotation.values.get(i))) {
                Object value = annotation.values.get(i + 1);
                return value instanceof List<?> list ? new ArrayList<>(list) : List.of(value);
            }
        }
        return List.of();
    }

    private static boolean hasInject(MethodNode method) {
        return (method.visibleAnnotations != null && method.visibleAnnotations.stream().anyMatch(a -> a.desc.endsWith("/Inject;")))
                || (method.invisibleAnnotations != null && method.invisibleAnnotations.stream().anyMatch(a -> a.desc.endsWith("/Inject;")));
    }

    private static boolean hasCall(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return true;
        }
        return false;
    }

    private static MethodNode callback(String name) {
        MethodNode method = new MethodNode(ACC_PUBLIC, name, "(" + OLD_BUFFER + CALLBACK + ")V", null, null);
        method.instructions.add(new InsnNode(RETURN));
        return method;
    }

    private static int countOpcode(MethodNode method, int opcode) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() == opcode) count++;
        return count;
    }

    private static void put(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
