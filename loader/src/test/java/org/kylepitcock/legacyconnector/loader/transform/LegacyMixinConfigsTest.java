package org.kylepitcock.legacyconnector.loader.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.kylepitcock.legacyconnector.loader.LegacyJar;
import org.kylepitcock.legacyconnector.loader.shim.ClassInfo;
import org.kylepitcock.legacyconnector.loader.shim.ShimIndex;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

class LegacyMixinConfigsTest implements Opcodes {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";

    @Test
    void preservesMixinsWhenMissingDependenciesAreOnlyInMethodBodies() {
        String target = "sample/Target";
        ClassNode targetNode = new ClassNode();
        targetNode.name = target;
        targetNode.superName = "java/lang/Object";
        Map<String, ClassNode> nodes = new LinkedHashMap<>();
        nodes.put(target, targetNode);

        ClassNode fieldMixin = mixin("sample/FieldMixin", "java/lang/Object", target);
        fieldMixin.fields.add(new org.objectweb.asm.tree.FieldNode(ACC_PRIVATE, "optional", "Loptional/api/Capability;", null, null));
        nodes.put(fieldMixin.name, fieldMixin);
        ClassNode bytecodeMixin = mixin("sample/BytecodeMixin", "java/lang/Object", target);
        org.objectweb.asm.tree.MethodNode integration = new org.objectweb.asm.tree.MethodNode(ACC_PRIVATE, "integration", "()V", null, null);
        integration.instructions.add(new org.objectweb.asm.tree.TypeInsnNode(NEW, "optional/api/Behavior"));
        bytecodeMixin.methods.add(integration);
        nodes.put(bytecodeMixin.name, bytecodeMixin);
        nodes.put("sample/GoodMixin", mixin("sample/GoodMixin", "java/lang/Object", target));

        Hierarchy hierarchy = new Hierarchy(ShimIndex.empty(), getClass().getClassLoader(), Map.of(), null);
        hierarchy.addLocal(new ClassInfo(target, "java/lang/Object", List.of(), ACC_PUBLIC));
        Map<String, byte[]> resources = new LinkedHashMap<>();
        resources.put("mixins.json", "{\"package\":\"sample\",\"mixins\":[\"FieldMixin\",\"BytecodeMixin\",\"GoodMixin\"]}"
                .getBytes(StandardCharsets.UTF_8));
        LegacyJar jar = new LegacyJar(Path.of("optional-mixins.jar"));

        List<String> notes = LegacyMixinConfigs.apply(jar, resources, nodes, hierarchy);

        assertEquals(List.of("mixins.json"), jar.mixinConfigSources);
        JsonObject rewritten = JsonParser.parseString(new String(
                resources.get(jar.mixinConfigResources.getFirst()), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(2, rewritten.getAsJsonArray("mixins").size());
        assertEquals("BytecodeMixin", rewritten.getAsJsonArray("mixins").get(0).getAsString());
        assertEquals("GoodMixin", rewritten.getAsJsonArray("mixins").get(1).getAsString());
        assertTrue(bytecodeMixin.methods.getFirst().instructions.getFirst() instanceof org.objectweb.asm.tree.TypeInsnNode type
                && type.getOpcode() == NEW && type.desc.equals("java/lang/NoClassDefFoundError"));
        assertTrue(notes.stream().anyMatch(note -> note.contains("FieldMixin") && note.contains("optional.api.Capability")));
    }

    @Test
    void omitsMixinsWithMissingShadowFieldsAndMethods() {
        String target = "sample/Target";
        ClassNode targetNode = new ClassNode();
        targetNode.name = target;
        targetNode.superName = "java/lang/Object";
        Map<String, ClassNode> nodes = new LinkedHashMap<>();
        nodes.put(target, targetNode);

        ClassNode missingField = mixin("sample/MissingShadowFieldMixin", "java/lang/Object", target);
        org.objectweb.asm.tree.FieldNode shadowField = new org.objectweb.asm.tree.FieldNode(ACC_PRIVATE, "canmelt", "Z", null, null);
        shadowField.visibleAnnotations = new ArrayList<>(List.of(shadow()));
        missingField.fields.add(shadowField);
        nodes.put(missingField.name, missingField);
        ClassNode missingMethod = mixin("sample/MissingShadowMethodMixin", "java/lang/Object", target);
        missingMethod.methods.add(handler("legacyMethod", "()V", shadow()));
        nodes.put(missingMethod.name, missingMethod);
        nodes.put("sample/GoodMixin", mixin("sample/GoodMixin", "java/lang/Object", target));

        Hierarchy hierarchy = new Hierarchy(ShimIndex.empty(), getClass().getClassLoader(), Map.of(), null);
        hierarchy.addLocal(new ClassInfo(target, "java/lang/Object", List.of(), ACC_PUBLIC));
        Map<String, byte[]> resources = new LinkedHashMap<>();
        resources.put("mixins.json", "{\"package\":\"sample\",\"mixins\":[\"MissingShadowFieldMixin\",\"MissingShadowMethodMixin\",\"GoodMixin\"]}"
                .getBytes(StandardCharsets.UTF_8));
        LegacyJar jar = new LegacyJar(Path.of("shadow-mixins.jar"));

        List<String> notes = LegacyMixinConfigs.apply(jar, resources, nodes, hierarchy);

        JsonObject rewritten = JsonParser.parseString(new String(
                resources.get(jar.mixinConfigResources.getFirst()), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, rewritten.getAsJsonArray("mixins").size());
        assertEquals("GoodMixin", rewritten.getAsJsonArray("mixins").get(0).getAsString());
        assertTrue(notes.stream().anyMatch(note -> note.contains("MissingShadowFieldMixin") && note.contains("@Shadow field canmelt")));
        assertTrue(notes.stream().anyMatch(note -> note.contains("MissingShadowMethodMixin") && note.contains("@Shadow method legacyMethod")));
    }

    @Test
    void omitsMixinsWhoseSuperclassIsOutsideTheTargetHierarchy() {
        String target = "sample/Target";
        ClassNode targetNode = new ClassNode();
        targetNode.name = target;
        targetNode.superName = "legacyconnector/v1_12_2/net/minecraft/entity/ai/EntityAIBase";

        Map<String, ClassNode> nodes = new LinkedHashMap<>();
        nodes.put(target, targetNode);
        nodes.put("sample/BadMixin", mixin("sample/BadMixin",
                "legacyconnector/v1_12_2/net/minecraft/entity/ai/EntityAITarget", target));
        nodes.put("sample/GoodMixin", mixin("sample/GoodMixin", "java/lang/Object", target));

        Hierarchy hierarchy = new Hierarchy(ShimIndex.empty(), getClass().getClassLoader(), Map.of(), null);
        hierarchy.addLocal(new ClassInfo(target, targetNode.superName, List.of(), ACC_PUBLIC));
        hierarchy.addLocal(new ClassInfo(targetNode.superName, "java/lang/Object", List.of(), ACC_PUBLIC));
        hierarchy.addLocal(new ClassInfo(
                "legacyconnector/v1_12_2/net/minecraft/entity/ai/EntityAITarget", targetNode.superName, List.of(), ACC_PUBLIC));

        Map<String, byte[]> resources = new LinkedHashMap<>();
        resources.put("mixins.json", "{\"package\":\"sample\",\"mixins\":[\"BadMixin\",\"GoodMixin\"]}"
                .getBytes(StandardCharsets.UTF_8));
        LegacyJar jar = new LegacyJar(Path.of("cotesia.jar"));

        List<String> notes = LegacyMixinConfigs.apply(jar, resources, nodes, hierarchy);

        assertEquals(1, jar.mixinConfigResources.size());
        JsonObject rewritten = JsonParser.parseString(new String(
                resources.get(jar.mixinConfigResources.getFirst()), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, rewritten.getAsJsonArray("mixins").size());
        assertEquals("GoodMixin", rewritten.getAsJsonArray("mixins").get(0).getAsString());
        assertTrue(notes.stream().anyMatch(note -> note.contains("BadMixin") && note.contains("target hierarchy")));
    }

    @Test
    void removesIncompatibleInjectorsWithoutDiscardingTheirMixinClasses() {
        String target = "sample/Target";
        ClassNode targetNode = new ClassNode();
        targetNode.name = target;
        targetNode.superName = "java/lang/Object";
        org.objectweb.asm.tree.MethodNode despawn = new org.objectweb.asm.tree.MethodNode(ACC_PUBLIC, "despawnEntity", "()V", null, null);
        despawn.instructions.add(new org.objectweb.asm.tree.InsnNode(RETURN));
        targetNode.methods.add(despawn);

        ClassInfo targetInfo = new ClassInfo(target, "java/lang/Object", List.of(), ACC_PUBLIC);
        targetInfo.methods.put("existing()V", ACC_PUBLIC);
        targetInfo.methods.put("applyBonuses(Lsample/SaveData;Lsample/World;)V", ACC_PUBLIC);
        targetInfo.methods.put("returnsBoolean()Z", ACC_PUBLIC);
        targetInfo.methods.put("despawnEntity()V", ACC_PUBLIC);
        ClassInfo saveData = new ClassInfo("sample/SaveData", "java/lang/Object", List.of(), ACC_PUBLIC);
        saveData.methods.put("setTotalKills(IIZLsample/World;)Z", ACC_PUBLIC);

        Map<String, ClassNode> nodes = new LinkedHashMap<>();
        nodes.put(target, targetNode);
        ClassNode missingMethod = mixin("sample/MissingMethodMixin", "java/lang/Object", target);
        missingMethod.methods.add(handler("onMissing", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",
                inject("notInCurrentVersion")));
        nodes.put(missingMethod.name, missingMethod);
        ClassNode badSignature = mixin("sample/BadSignatureMixin", "java/lang/Object", target);
        badSignature.methods.add(handler("applyBonuses", "(IIBLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",
                inject("applyBonuses")));
        nodes.put(badSignature.name, badSignature);
        ClassNode badCallback = mixin("sample/BadCallbackMixin", "java/lang/Object", target);
        badCallback.methods.add(handler("wrongCallback", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",
                inject("returnsBoolean()Z")));
        nodes.put(badCallback.name, badCallback);
        ClassNode badAtTarget = mixin("sample/BadAtTargetMixin", "java/lang/Object", target);
        AnnotationNode at = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/At;");
        at.values = new ArrayList<>(List.of("value", "INVOKE", "target",
                "Lsample/SaveData;setTotalKills(IIZLsample/World;)Z"));
        AnnotationNode redirect = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Redirect;");
        redirect.values = new ArrayList<>(List.of("method", List.of("despawnEntity()V"), "at", List.of(at), "require", 1));
        badAtTarget.methods.add(handler("redirect", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", redirect));
        nodes.put(badAtTarget.name, badAtTarget);
        ClassNode good = mixin("sample/GoodMixin", "java/lang/Object", target);
        good.methods.add(handler("onExisting", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", inject("existing()V")));
        nodes.put(good.name, good);
        ClassNode goodReturn = mixin("sample/GoodReturnMixin", "java/lang/Object", target);
        goodReturn.methods.add(handler("onBoolean", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V",
                inject("returnsBoolean()Z")));
        nodes.put(goodReturn.name, goodReturn);

        Hierarchy hierarchy = new Hierarchy(ShimIndex.empty(), getClass().getClassLoader(), Map.of(), null);
        hierarchy.addLocal(targetInfo);
        hierarchy.addLocal(saveData);
        Map<String, byte[]> resources = new LinkedHashMap<>();
        resources.put("mixins.json", "{\"package\":\"sample\",\"mixins\":[\"MissingMethodMixin\",\"BadSignatureMixin\",\"BadCallbackMixin\",\"BadAtTargetMixin\",\"GoodMixin\",\"GoodReturnMixin\"]}"
                .getBytes(StandardCharsets.UTF_8));
        LegacyJar jar = new LegacyJar(Path.of("cotesia-injectors.jar"));

        List<String> notes = LegacyMixinConfigs.apply(jar, resources, nodes, hierarchy);

        JsonObject rewritten = JsonParser.parseString(new String(
                resources.get(jar.mixinConfigResources.getFirst()), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(6, rewritten.getAsJsonArray("mixins").size());
        assertEquals(List.of("MissingMethodMixin", "BadSignatureMixin", "BadCallbackMixin", "BadAtTargetMixin", "GoodMixin", "GoodReturnMixin"),
                java.util.stream.IntStream.range(0, rewritten.getAsJsonArray("mixins").size())
                        .mapToObj(i -> rewritten.getAsJsonArray("mixins").get(i).getAsString()).toList());
        assertTrue(missingMethod.methods.isEmpty());
        assertTrue(badSignature.methods.isEmpty());
        assertTrue(badCallback.methods.isEmpty());
        assertTrue(badAtTarget.methods.isEmpty());
        assertTrue(notes.stream().anyMatch(note -> note.contains("MissingMethodMixin") && note.contains("notInCurrentVersion")));
        assertTrue(notes.stream().anyMatch(note -> note.contains("BadSignatureMixin") && note.contains("signature incompatible")));
        assertTrue(notes.stream().anyMatch(note -> note.contains("BadCallbackMixin") && note.contains("signature incompatible")));
        assertTrue(notes.stream().anyMatch(note -> note.contains("BadAtTargetMixin") && note.contains("INVOKE site")));
        assertEquals(4, notes.stream().filter(note -> note.contains("injector handler omitted")).count());
    }

    @Test
    void checksRequiredInjectionSitesInPeerJars() {
        String target = "sample/PeerTarget";
        ClassNode targetNode = new ClassNode();
        targetNode.name = target;
        targetNode.superName = "java/lang/Object";
        org.objectweb.asm.tree.MethodNode targetMethod = new org.objectweb.asm.tree.MethodNode(ACC_PUBLIC, "spawnWaves", "()Lsample/Entity;", null, null);
        targetMethod.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(INVOKESTATIC,
                "sample/EntityList", "create", "()Lsample/Entity;", false));
        targetMethod.instructions.add(new org.objectweb.asm.tree.InsnNode(ARETURN));
        targetNode.methods.add(targetMethod);
        ClassInfo peer = ClassInfo.of(targetNode);

        ClassNode mixin = mixin("sample/PeerMixin", "java/lang/Object", target);
        AnnotationNode at = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/At;");
        at.values = new ArrayList<>(List.of("value", "INVOKE", "target", "Lsample/EntityList;create()Lsample/Entity;"));
        AnnotationNode modify = new AnnotationNode("Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;");
        modify.values = new ArrayList<>(List.of("method", List.of("spawnWaves"), "at", List.of(at), "require", 1));
        mixin.methods.add(handler("modifySpawn", "(Lsample/Entity;)Lsample/Entity;", modify));
        Map<String, ClassNode> nodes = new LinkedHashMap<>();
        nodes.put(mixin.name, mixin);
        Hierarchy hierarchy = new Hierarchy(ShimIndex.empty(), getClass().getClassLoader(), Map.of(target, peer), null);
        Map<String, byte[]> resources = new LinkedHashMap<>();
        resources.put("mixins.json", "{\"package\":\"sample\",\"mixins\":[\"PeerMixin\"]}"
                .getBytes(StandardCharsets.UTF_8));
        LegacyJar jar = new LegacyJar(Path.of("peer-mixins.jar"));

        List<String> notes = LegacyMixinConfigs.apply(jar, resources, nodes, hierarchy);

        assertEquals(1, mixin.methods.size());
        assertTrue(notes.stream().noneMatch(note -> note.contains("injector handler omitted")));
    }

    @Test
    void appliesCompatibleMixinsToLegacyApiShims() {
        String target = "legacyconnector/v1_12_2/net/minecraft/entity/player/EntityPlayer";
        String parent = "legacyconnector/v1_12_2/net/minecraft/entity/EntityLivingBase";
        String fearInterface = "sample/IFearRandom";
        String randomClass = "sample/SyncedRandom";
        ClassInfo player = new ClassInfo(target, parent, List.of(), ACC_PUBLIC | ACC_ABSTRACT);
        player.methods.put("canEat(Z)Z", ACC_PUBLIC);
        player.methods.put("shouldHeal()Z", ACC_PUBLIC);
        ClassInfo living = new ClassInfo(parent, "java/lang/Object", List.of(), ACC_PUBLIC | ACC_ABSTRACT);
        ShimIndex shim = new ShimIndex(Map.of(target, player, parent, living));
        Hierarchy hierarchy = new Hierarchy(shim, getClass().getClassLoader(), Map.of(), null);
        hierarchy.addLocal(new ClassInfo(fearInterface, "java/lang/Object", List.of(), ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT));
        hierarchy.addLocal(new ClassInfo(randomClass, "java/util/Random", List.of(), ACC_PUBLIC));

        ClassNode playerMixin = mixin("sample/PlayerMixin", parent, target);
        playerMixin.interfaces.add(fearInterface);
        playerMixin.fields.add(new org.objectweb.asm.tree.FieldNode(ACC_PRIVATE | ACC_FINAL, "fearRandom", "L" + randomClass + ";", null, null));
        org.objectweb.asm.tree.MethodNode unusedShadow = new org.objectweb.asm.tree.MethodNode(ACC_PUBLIC | ACC_ABSTRACT,
                "addMovementStat", "(DDD)V", null, null);
        unusedShadow.visibleAnnotations = new ArrayList<>(List.of(shadow()));
        playerMixin.methods.add(unusedShadow);
        playerMixin.methods.add(handler("onCanEat", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V",
                inject("canEat")));
        playerMixin.methods.add(handler("onShouldHeal", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V",
                inject("shouldHeal")));
        playerMixin.methods.add(new org.objectweb.asm.tree.MethodNode(ACC_PUBLIC, "srpcotesia$getFearRNG", "()L" + randomClass + ";", null, null));
        Map<String, ClassNode> nodes = new LinkedHashMap<>();
        nodes.put(playerMixin.name, playerMixin);
        Map<String, byte[]> resources = new LinkedHashMap<>();
        resources.put("mixins.early.json", "{\"package\":\"sample\",\"mixins\":[\"PlayerMixin\"]}"
                .getBytes(StandardCharsets.UTF_8));
        LegacyJar jar = new LegacyJar(Path.of("cotesia-player-mixin.jar"));

        List<String> notes = LegacyMixinConfigs.apply(jar, resources, nodes, hierarchy);

        assertEquals(1, jar.mixinConfigResources.size());
        JsonObject rewritten = JsonParser.parseString(new String(
                resources.get(jar.mixinConfigResources.getFirst()), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("PlayerMixin", rewritten.getAsJsonArray("mixins").get(0).getAsString());
        assertTrue(playerMixin.interfaces.contains(fearInterface));
        assertTrue(playerMixin.methods.stream().anyMatch(method -> method.name.equals("srpcotesia$getFearRNG")));
        assertTrue(playerMixin.methods.stream().anyMatch(method -> method.name.equals("onCanEat")));
        assertTrue(playerMixin.methods.stream().anyMatch(method -> method.name.equals("onShouldHeal")));
        assertTrue(playerMixin.methods.stream().noneMatch(method -> method.name.equals("addMovementStat")));
        assertTrue(notes.stream().anyMatch(note -> note.contains("unused @Shadow method addMovementStat")));
        assertTrue(notes.stream().noneMatch(note -> note.contains("incompatible") || note.contains("omitted")));
    }

    private static AnnotationNode shadow() {
        return new AnnotationNode("Lorg/spongepowered/asm/mixin/Shadow;");
    }

    private static AnnotationNode inject(String targetMethod) {
        AnnotationNode annotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;");
        annotation.values = new ArrayList<>(List.of("method", List.of(targetMethod)));
        return annotation;
    }

    private static org.objectweb.asm.tree.MethodNode handler(String name, String descriptor, AnnotationNode annotation) {
        org.objectweb.asm.tree.MethodNode method = new org.objectweb.asm.tree.MethodNode(ACC_PRIVATE, name, descriptor, null, null);
        method.visibleAnnotations = new ArrayList<>(List.of(annotation));
        return method;
    }

    private static ClassNode mixin(String name, String superName, String target) {
        ClassNode mixin = new ClassNode();
        mixin.version = V1_8;
        mixin.access = ACC_PUBLIC | ACC_ABSTRACT;
        mixin.name = name;
        mixin.superName = superName;
        AnnotationNode annotation = new AnnotationNode(MIXIN);
        annotation.values = new ArrayList<>(List.of("value", List.of(Type.getObjectType(target))));
        mixin.visibleAnnotations = new ArrayList<>(List.of(annotation));
        return mixin;
    }
}
