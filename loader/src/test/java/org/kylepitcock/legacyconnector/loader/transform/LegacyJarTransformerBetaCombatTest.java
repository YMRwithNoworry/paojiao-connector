package org.kylepitcock.legacyconnector.loader.transform;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kylepitcock.legacyconnector.loader.LegacyJar;
import org.kylepitcock.legacyconnector.loader.LegacyVersion;
import org.kylepitcock.legacyconnector.loader.shim.ClassInfo;
import org.kylepitcock.legacyconnector.loader.shim.ShimIndex;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.InsnNode;

class LegacyJarTransformerBetaCombatTest implements Opcodes {
    private static final String PREFIX = "legacyconnector/vb1_7_3/";
    private static final String ENTITY = PREFIX + "net/minecraft/entity/Entity";
    private static final String LIVING = PREFIX + "net/minecraft/entity/LivingEntity";
    private static final String MOB = PREFIX + "net/minecraft/entity/MobEntity";
    private static final String WORLD = PREFIX + "net/minecraft/world/World";
    private static final String PLAYER = PREFIX + "net/minecraft/entity/player/PlayerEntity";
    private static final String HELPER = "org/kylepitcock/legacyconnector/vb1_7_3/CombatTargetsB173";

    @Test
    void betaCombatAnalysisRunsInsideJarTransform(@TempDir Path temp) throws IOException {
        Path input = temp.resolve("combat.jar");
        Path output = temp.resolve("combat.translated.jar");
        try (var jar = new JarOutputStream(java.nio.file.Files.newOutputStream(input))) {
            var entry = new JarEntry("fixture/Predator.class");
            jar.putNextEntry(entry);
            jar.write(predatorClass());
            jar.closeEntry();
        }

        LegacyJar legacy = new LegacyJar(input);
        legacy.version = LegacyVersion.VB1_7_3;
        var result = new LegacyJarTransformer(shim(), getClass().getClassLoader()).transform(legacy, output);

        assertTrue(result.notes.stream().anyMatch(note -> note.startsWith("Beta combat provenance:")), result.notes.toString());
        try (var jar = new JarFile(output.toFile())) {
            var entry = jar.getJarEntry("fixture/Predator.class");
            assertNotNull(entry);
            var transformed = new ClassNode();
            try (var stream = jar.getInputStream(entry)) {
                new ClassReader(stream.readAllBytes()).accept(transformed, 0);
            }
            assertTrue(transformed.methods.stream().anyMatch(method -> method.name.equals("mcc$combatRoles")));
            var tick = transformed.methods.stream().filter(method -> method.name.equals("tickLiving")).findFirst().orElseThrow();
            assertTrue(java.util.stream.StreamSupport.stream(tick.instructions.spliterator(), false)
                    .anyMatch(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals(HELPER) && call.name.equals("closest")));
        }
    }

    private static byte[] predatorClass() {
        var node = new ClassNode();
        node.version = V17;
        node.access = ACC_PUBLIC;
        node.name = "fixture/Predator";
        node.superName = "net/minecraft/entity/MobEntity";
        node.fields.add(new FieldNode(ACC_PRIVATE, "prey", "Lnet/minecraft/entity/Entity;", null, null));

        var tick = new MethodNode(ACC_PUBLIC, "tickLiving", "()V", null, null);
        var code = tick.instructions;
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new FieldInsnNode(GETFIELD, "net/minecraft/entity/Entity", "world", "Lnet/minecraft/world/World;"));
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new LdcInsnNode(16.0D));
        code.add(new MethodInsnNode(INVOKEVIRTUAL, "net/minecraft/world/World", "getClosestPlayer",
                "(Lnet/minecraft/entity/Entity;D)Lnet/minecraft/entity/player/PlayerEntity;", false));
        code.add(new FieldInsnNode(PUTFIELD, "fixture/Predator", "prey", "Lnet/minecraft/entity/Entity;"));
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new FieldInsnNode(GETFIELD, "fixture/Predator", "prey", "Lnet/minecraft/entity/Entity;"));
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new InsnNode(ICONST_1));
        code.add(new MethodInsnNode(INVOKEVIRTUAL, "net/minecraft/entity/Entity", "damage",
                "(Lnet/minecraft/entity/Entity;I)Z", false));
        code.add(new InsnNode(POP));
        code.add(new InsnNode(RETURN));
        tick.maxStack = 6;
        tick.maxLocals = 1;
        node.methods.add(tick);

        var writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static ShimIndex shim() {
        var entity = info(ENTITY, "java/lang/Object");
        entity.fields.put("world:L" + WORLD + ";", ACC_PUBLIC);
        entity.fields.put("x:D", ACC_PUBLIC);
        entity.methods.put("damage(L" + ENTITY + ";I)Z", ACC_PUBLIC);
        var living = info(LIVING, ENTITY);
        var mob = info(MOB, LIVING);
        mob.fields.put("target:L" + ENTITY + ";", ACC_PROTECTED);
        var world = info(WORLD, "java/lang/Object");
        world.methods.put("getClosestPlayer(L" + ENTITY + ";D)L" + PLAYER + ";", ACC_PUBLIC);
        var player = info(PLAYER, LIVING);
        var helper = info(HELPER, "java/lang/Object");
        helper.methods.put("closest(L" + WORLD + ";L" + ENTITY + ";DLjava/lang/String;)L" + PLAYER + ";", ACC_PUBLIC | ACC_STATIC);
        helper.methods.put("stored(L" + ENTITY + ";L" + ENTITY + ";Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)L" + ENTITY + ";",
                ACC_PUBLIC | ACC_STATIC);
        return new ShimIndex(Map.of(ENTITY, entity, LIVING, living, MOB, mob, WORLD, world, PLAYER, player, HELPER, helper));
    }

    private static ClassInfo info(String name, String parent) {
        return new ClassInfo(name, parent, List.of(), ACC_PUBLIC);
    }
}
