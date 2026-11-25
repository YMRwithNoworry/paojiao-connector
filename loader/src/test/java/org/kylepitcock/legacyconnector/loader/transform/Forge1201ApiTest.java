package org.kylepitcock.legacyconnector.loader.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kylepitcock.legacyconnector.loader.LegacyJar;
import org.kylepitcock.legacyconnector.loader.LegacyVersion;
import org.kylepitcock.legacyconnector.loader.shim.ShimIndex;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** The Forge 1.20.1 facade rewrites, rename tables and metadata conversion. */
class Forge1201ApiTest {
    private static final String CAP = "Llegacyconnector/v1_20_1/net/minecraftforge/common/capabilities/Capability;";
    private static final String LAZY = "Llegacyconnector/v1_20_1/net/minecraftforge/common/util/LazyOptional;";

    private static ClassNode with(MethodInsnNode call) {
        ClassNode node = new ClassNode();
        node.name = "my/Mod";
        node.superName = "java/lang/Object";
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
        m.instructions.add(call);
        m.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(m);
        return node;
    }

    private static MethodInsnNode firstCall(ClassNode node) {
        return (MethodInsnNode) node.methods.get(0).instructions.getFirst();
    }

    @Test
    void mapsKubeJs6ConsoleClassAndNestedTypesToKubeJs7() {
        Forge1201Transformer transformer = new Forge1201Transformer(ShimIndex.empty());

        assertEquals(
                "dev/latvian/mods/kubejs/script/ConsoleJS",
                transformer.mapClass("dev/latvian/mods/kubejs/util/ConsoleJS"));
        assertEquals(
                "dev/latvian/mods/kubejs/script/ConsoleJS$VarFunc",
                transformer.mapClass("dev/latvian/mods/kubejs/util/ConsoleJS$VarFunc"));

        // Only ConsoleJS moved; KubeJS 7 already keeps these types in script.
        assertEquals(
                "dev/latvian/mods/kubejs/script/ScriptType",
                transformer.mapClass("dev/latvian/mods/kubejs/script/ScriptType"));
        assertEquals(
                "dev/latvian/mods/kubejs/script/ConsoleLine",
                transformer.mapClass("dev/latvian/mods/kubejs/script/ConsoleLine"));
        assertEquals(
                "dev/latvian/mods/kubejs/util/LogType",
                transformer.mapClass("dev/latvian/mods/kubejs/util/LogType"));
    }

    @Test
    void mapsLegacyKotlinForForgeContextToNeoForgeContext() {
        Forge1201Transformer transformer = new Forge1201Transformer(ShimIndex.empty());

        assertEquals("thedarkcolour/kotlinforforge/neoforge/KotlinModLoadingContext",
                transformer.mapClass("thedarkcolour/kotlinforforge/KotlinModLoadingContext"));
        assertEquals("thedarkcolour/kotlinforforge/neoforge/KotlinModLoadingContext$Companion",
                transformer.mapClass("thedarkcolour/kotlinforforge/KotlinModLoadingContext$Companion"));
    }

    @Test
    void preservesDynamicConstantsInInvokeDynamicBootstrapArguments(@TempDir Path temp) throws Exception {
        String owner = getClass().getPackageName().replace('.', '/') + "/DynamicConstantFixture";
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "message", "()Ljava/lang/String;", null, null);
        var constant = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/ConstantBootstraps", "explicitCast",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;)Ljava/lang/Object;", false);
        var concat = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/StringConcatFactory", "makeConcatWithConstants",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false);
        method.visitCode();
        method.visitLdcInsn("Legacy");
        method.visitInvokeDynamicInsn("makeConcatWithConstants", "(Ljava/lang/String;)Ljava/lang/String;", concat,
                new ConstantDynamic("recipe", "Ljava/lang/String;", constant, "\u0001 Connector"));
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        Path input = temp.resolve("fixture.jar");
        Path output = temp.resolve("translated.jar");
        String entryName = owner + ".class";
        try (var jar = new JarOutputStream(Files.newOutputStream(input))) {
            jar.putNextEntry(new JarEntry(entryName));
            jar.write(writer.toByteArray());
        }
        LegacyJar legacy = new LegacyJar(input);
        legacy.version = LegacyVersion.V1_20_1;
        var result = new Forge1201Transformer(ShimIndex.empty()).transform(legacy, output);
        try (var jar = new JarFile(output.toFile())) {
            var entry = jar.getJarEntry(entryName);
            assertTrue(entry != null, result.notes.toString());
            try (var stream = jar.getInputStream(entry)) {
                Class<?> translated = MethodHandles.lookup().defineClass(stream.readAllBytes());
                assertEquals("Legacy Connector", translated.getMethod("message").invoke(null));
            }
        }
    }

    @Test
    void tableLoadsAndLinesAreClean() {
        Map<String, String> table = ForgeApiRewrites1201.table();
        assertFalse(table.isEmpty());
        for (var e : table.entrySet()) {
            assertFalse(e.getKey().contains("\r") || e.getValue().contains("\r"), "carriage return in " + e.getKey());
            assertTrue(e.getKey().indexOf('(') > 0, e.getKey());
        }
    }

    @Test
    void instanceCallGetsItsReceiverFirst() {
        ClassNode node = with(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/neoforged/neoforge/registries/DeferredHolder", "isPresent", "()Z", false));
        assertEquals(1, ForgeApiRewrites1201.apply(node));
        MethodInsnNode call = firstCall(node);
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("legacyconnector/v1_20_1/ForgeApi1201", call.owner);
        assertEquals("holderIsPresent", call.name);
        assertEquals("(Lnet/neoforged/neoforge/registries/DeferredHolder;)Z", call.desc);
    }

    @Test
    void getCapabilityOnAnyReceiverGoesToTheCapabilityHelper() {
        String desc = "(" + CAP + "Lnet/minecraft/core/Direction;)" + LAZY;
        ClassNode node = with(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/block/entity/BlockEntity", "getCapability", desc, false));
        assertEquals(1, ForgeApiRewrites1201.apply(node));
        MethodInsnNode call = firstCall(node);
        assertEquals("legacyconnector/v1_20_1/Caps1201", call.owner);
        assertEquals("getCapability", call.name);
        assertEquals("(Ljava/lang/Object;" + CAP + "Lnet/minecraft/core/Direction;)" + LAZY, call.desc);
    }

    @Test
    void superCallOfAMinecraftParentUsesTheSuperHelper() {
        String desc = "(" + CAP + "Lnet/minecraft/core/Direction;)" + LAZY;
        ClassNode node = with(new MethodInsnNode(Opcodes.INVOKESPECIAL, "net/minecraft/world/level/block/entity/BlockEntity", "getCapability", desc, false));
        assertEquals(1, ForgeApiRewrites1201.apply(node));
        assertEquals("superGetCapability", firstCall(node).name);
    }

    @Test
    void superCallOfAModParentIsLeftAlone() {
        String desc = "(" + CAP + "Lnet/minecraft/core/Direction;)" + LAZY;
        ClassNode node = with(new MethodInsnNode(Opcodes.INVOKESPECIAL, "my/BaseBlockEntity", "getCapability", desc, false));
        assertEquals(0, ForgeApiRewrites1201.apply(node));
    }

    @Test
    void aClassThatDeclaresGetCapabilityBecomesAProvider() {
        ClassNode node = new ClassNode();
        node.name = "my/Be";
        node.superName = "net/minecraft/world/level/block/entity/BlockEntity";
        node.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "getCapability", ForgeApiRewrites1201.GET_CAPABILITY_DESC, null, null));
        assertTrue(ForgeApiRewrites1201.markProviders(node));
        assertTrue(node.interfaces.contains(ForgeApiRewrites1201.PROVIDER));
        assertFalse(ForgeApiRewrites1201.markProviders(node), "only once");
    }

    @Test
    void cancellationCallsOnNeoForgeEventsGoThroughTheHelper() {
        ClassNode node = with(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/neoforged/neoforge/event/entity/player/PlayerInteractEvent$EntityInteract", "setCanceled", "(Z)V", false));
        assertEquals(1, ForgeApiRewrites1201.apply(node));
        MethodInsnNode call = firstCall(node);
        assertEquals("eventSetCanceled", call.name);
        assertEquals("(Ljava/lang/Object;Z)V", call.desc);
        // a mod's own event keeps the call: its class implements ICancellableEvent
        ClassNode own = with(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "my/MyEvent", "setCanceled", "(Z)V", false));
        assertEquals(0, ForgeApiRewrites1201.apply(own));
    }

    @Test
    void configValueReadsUseTheForgeDefaultBeforeConfigLoad() {
        ClassNode node = with(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "net/neoforged/neoforge/common/ModConfigSpec$BooleanValue", "get", "()Ljava/lang/Object;", false));

        assertEquals(1, ForgeApiRewrites1201.apply(node));
        MethodInsnNode rewritten = firstCall(node);
        assertEquals(Opcodes.INVOKESTATIC, rewritten.getOpcode());
        assertEquals(ForgeApiRewrites1201.HELPERS, rewritten.owner);
        assertEquals("configValueGet", rewritten.name);
        assertEquals("(Lnet/neoforged/neoforge/common/ModConfigSpec$ConfigValue;)Ljava/lang/Object;", rewritten.desc);
    }

    @Test
    void anItemThatDeclaresInitCapabilitiesGetsTheInitInterface() {
        ClassNode node = new ClassNode();
        node.name = "my/Item";
        node.superName = "net/minecraft/world/item/Item";
        node.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "initCapabilities", ForgeApiRewrites1201.INIT_CAPABILITIES_DESC, null, null));
        assertTrue(ForgeApiRewrites1201.markProviders(node));
        assertTrue(node.interfaces.contains(ForgeApiRewrites1201.INIT_CAPABILITIES));
        assertFalse(node.interfaces.contains(ForgeApiRewrites1201.PROVIDER));
    }

    @Test
    void attachCapabilitiesHandlerWithATypeArgumentGetsAGuard() {
        ClassNode node = new ClassNode();
        node.name = "my/Events";
        node.superName = "java/lang/Object";
        String event = AttachGuards1201.EVENT;
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "attach", "(L" + event + ";)V", "(L" + event + "<Lnet/minecraft/world/entity/Entity;>;)V", null);
        m.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(m);
        MethodNode plain = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "other", "(L" + event + ";)V", null, null);
        plain.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(plain);
        assertEquals(1, AttachGuards1201.apply(node, java.util.Set.of()));
        assertEquals(7, m.instructions.size(), "aload, getObject, instanceof, ifne, return, label, then the original return");
        assertEquals(1, plain.instructions.size());
        assertEquals("net/minecraft/world/entity/Entity", AttachGuards1201.typeArgument("(L" + event + "<Lnet/minecraft/world/entity/Entity;>;)V", 0));
        assertEquals(null, AttachGuards1201.typeArgument("(Ljava/lang/String;L" + event + "<Lnet/minecraft/world/entity/Entity;>;)V", 0));
        assertEquals("net/minecraft/world/item/ItemStack", AttachGuards1201.typeArgument("(Ljava/lang/String;L" + event + "<Lnet/minecraft/world/item/ItemStack;>;)V", 1));
    }

    @Test
    void modsTomlGetsTheMixinConfigsOfTheManifest() {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("MixinConfigs", "a.mixins.json, b.mixins.json");
        assertEquals(List.of("a.mixins.json", "b.mixins.json"), Forge1201Transformer.mixinConfigs(manifest));
        String toml = Forge1201Transformer.modsToml("modLoader=\"javafml\"\nloaderVersion=\"[47,)\"\n[[mods]]\nmodId=\"x\"\n", List.of("a.mixins.json"));
        assertTrue(toml.contains("loaderVersion=\"[1,)\""), toml);
        assertTrue(toml.contains("[[mixins]]\nconfig=\"a.mixins.json\""), toml);
    }

    @Test
    void stripRefmapKeepsTheJsonValidWhateverTheKeyOrder() {
        assertEquals("{\"package\":\"p\"}", Forge1201Transformer.stripRefmap("{\"package\":\"p\",\"refmap\":\"r.json\"}"));
        assertEquals("{\"package\":\"p\"}", Forge1201Transformer.stripRefmap("{\"refmap\":\"r.json\",\"package\":\"p\"}"));
    }

    @Test
    void nestedMixinExtrasIsDroppedFromTheJarJarMetadata() {
        String json = "{\"jars\":[{\"identifier\":{\"group\":\"g\",\"artifact\":\"mixinextras-forge\"},\"path\":\"META-INF/jarjar/mixinextras-forge-0.3.6.jar\"},"
                + "{\"identifier\":{\"group\":\"g\",\"artifact\":\"spectrelib-forge\"},\"path\":\"META-INF/jarjar/spectrelib-forge-1.jar\"}]}";
        String out = Forge1201Transformer.jarJarMetadata(json);
        assertFalse(out.contains("mixinextras"), out);
        assertTrue(out.contains("spectrelib"), out);
    }

    @Test
    void renameTablesHaveNoCarriageReturnsAndSensibleTargets() throws IOException {
        for (String f : List.of("forge-event-renames.tsv", "forge-fml-renames.tsv", "forge-curated-renames.tsv", "forge-call-rewrites.tsv", "forge-members.tsv")) {
            try (InputStream in = Forge1201ApiTest.class.getClassLoader().getResourceAsStream("legacyconnector/forward/1.20.1/" + f)) {
                assertTrue(in != null, f);
                for (String l : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                    if (l.isBlank() || l.startsWith("#")) continue;
                    String[] a = l.split("\t");
                    assertEquals(f.equals("forge-members.tsv") ? 3 : 2, a.length, f + ": " + l);
                    if (!f.equals("forge-call-rewrites.tsv") && !f.equals("forge-members.tsv")) {
                        if (!f.equals("forge-curated-renames.tsv") || a[0].startsWith("net/minecraftforge/")) {
                            assertTrue(a[1].startsWith("net/neoforged/"), f + ": " + l);
                        }
                    }
                }
            }
        }
    }

    @Test
    void forgeModResourcePackFactoryCallUsesTheConcreteLegacyFacade() {
        String descriptor = "(Lnet/neoforged/neoforgespi/language/IModFileInfo;)"
                + "Llegacyconnector/v1_20_1/net/minecraftforge/resource/PathPackResources;";
        ClassNode node = with(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "net/neoforged/neoforge/resource/ResourcePackLoader", "createPackForMod", descriptor, false));

        assertEquals(1, ForgeApiRewrites1201.apply(node));
        MethodInsnNode rewritten = firstCall(node);
        assertEquals(Opcodes.INVOKESTATIC, rewritten.getOpcode());
        assertEquals("legacyconnector/v1_20_1/ForgeApi1201", rewritten.owner);
        assertEquals("createPackForMod", rewritten.name);
        assertEquals(descriptor, rewritten.desc);
    }

    @Test
    void mavenArtifactVersionStringUsesTheForgeCompatibilityHelper() {
        String descriptor = "(Lorg/apache/maven/artifact/versioning/ArtifactVersion;)Ljava/lang/String;";
        ClassNode node = with(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "net/neoforged/neoforge/common/util/MavenVersionStringHelper", "artifactVersionToString", descriptor, false));

        assertEquals(1, ForgeApiRewrites1201.apply(node));
        MethodInsnNode rewritten = firstCall(node);
        assertEquals(Opcodes.INVOKESTATIC, rewritten.getOpcode());
        assertEquals("legacyconnector/v1_20_1/ForgeApi1201", rewritten.owner);
        assertEquals("artifactVersionToString", rewritten.name);
        assertEquals(descriptor, rewritten.desc);
    }

}
