package org.kylepitcock.legacyconnector.loader.transform.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.kylepitcock.legacyconnector.loader.LegacyJar;
import org.kylepitcock.legacyconnector.loader.LegacyModDecl;
import org.kylepitcock.legacyconnector.loader.LegacyVersion;
import org.kylepitcock.legacyconnector.loader.shim.ClassInfo;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

class SrpCotesiaParticleCompatTest {
    private static final String PACKET = SrpCotesiaParticleCompat.SRP_PACKET;
    private static final String EFFECT = SrpCotesiaParticleCompat.SRP_EFFECT_BASE;
    private static final String ENTITY = SrpCotesiaParticleCompat.ENTITY;
    private static final String BYTE_BUF = SrpCotesiaParticleCompat.BYTE_BUF;
    private static final String SIMPLE_NETWORK = "net/minecraftforge/fml/common/network/simpleimpl/SimpleNetworkWrapper";
    private static final String NETWORK_DESC = "(Lnet/minecraftforge/fml/common/network/simpleimpl/IMessage;L" + ENTITY + ";)V";

    @Test
    void extendsParticleProtocolWithTheSendingEntityOnlyWhenCotesiaIsPresent() {
        LegacyJar jar = srpJar();
        Map<String, byte[]> classes = currentSrpClasses();

        assertEquals(0, SrpCotesiaParticleCompat.applyToSrp(jar, classes, Map.of()));
        assertFalse(read(classes.get(PACKET)).fields.stream().anyMatch(field -> field.name.equals("mcc$particleEntityId")));

        Map<String, ClassInfo> peers = Map.of(SrpCotesiaParticleCompat.COTESIA_EFFECT_MIXIN,
                ClassInfo.of(plainClass(SrpCotesiaParticleCompat.COTESIA_EFFECT_MIXIN)));
        assertEquals(1, SrpCotesiaParticleCompat.applyToSrp(jar, classes, peers));

        ClassNode packet = read(classes.get(PACKET));
        assertTrue(packet.fields.stream().anyMatch(field -> field.name.equals("mcc$particleEntityId") && field.desc.equals("I")));
        assertNotNull(findMethod(packet, SrpCotesiaParticleCompat.PARTICLE_ID_GETTER, "()I"));
        assertNotNull(findMethod(packet, SrpCotesiaParticleCompat.PARTICLE_ID_SETTER, "(I)V"));
        assertEquals(1, countCalls(findMethod(packet, "fromBytes", "(L" + BYTE_BUF + ";)V"), BYTE_BUF, "readInt"));
        assertEquals(1, countCalls(findMethod(packet, "toBytes", "(L" + BYTE_BUF + ";)V"), BYTE_BUF, "writeInt"));

        MethodNode sendParticle = findMethod(read(classes.get(EFFECT)), "sendParticle", SrpCotesiaParticleCompat.SEND_PARTICLE_DESC);
        MethodInsnNode setId = findCall(sendParticle, PACKET, SrpCotesiaParticleCompat.PARTICLE_ID_SETTER, "(I)V");
        MethodInsnNode track = findCall(sendParticle, SIMPLE_NETWORK, "sendToAllTracking", NETWORK_DESC);
        assertNotNull(setId);
        assertNotNull(track);
        assertTrue(indexOf(sendParticle, setId) < indexOf(sendParticle, track));
        MethodInsnNode getId = findCall(sendParticle, ENTITY, "func_145782_y", "()I");
        assertNotNull(getId);
        assertEquals(Opcodes.ALOAD, getId.getPrevious().getOpcode());
        assertEquals(0, SrpCotesiaParticleCompat.applyToSrp(jar, classes, peers));
    }

    @Test
    void exposesSyntheticPeerAccessorsOnlyForTheCurrentPacketAndHandlerShape() {
        LegacyJar jar = srpJar();
        String prefix = LegacyVersion.V1_12_2.linkPrefix();
        ClassNode packetNode = new ClassNode();
        packetNode.name = PACKET;
        packetNode.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "<init>", SrpCotesiaParticleCompat.PARTICLE_CTOR_DESC, null, null));
        packetNode.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "fromBytes", "(L" + BYTE_BUF + ";)V", null, null));
        packetNode.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "toBytes", "(L" + BYTE_BUF + ";)V", null, null));

        ClassNode handlerNode = new ClassNode();
        handlerNode.name = SrpCotesiaParticleCompat.SRP_PARTICLE_HANDLER;
        handlerNode.methods.add(new MethodNode(Opcodes.ACC_PRIVATE, "spawnParticles", SrpCotesiaParticleCompat.HANDLER_SPAWN_DESC, null, null));
        String handleDesc = "(L" + PACKET + ";L" + prefix + "net/minecraftforge/fml/common/network/simpleimpl/MessageContext;)V";
        handlerNode.methods.add(new MethodNode(Opcodes.ACC_PRIVATE, "handle", handleDesc, null, null));

        ClassNode effectNode = new ClassNode();
        effectNode.name = EFFECT;
        effectNode.methods.add(new MethodNode(Opcodes.ACC_PRIVATE, "sendParticle",
                "(L" + prefix + SrpCotesiaParticleCompat.ENTITY_LIVING_BASE + ";BZ)V", null, null));

        List<ClassInfo> infos = new ArrayList<>(List.of(ClassInfo.of(packetNode), ClassInfo.of(handlerNode), ClassInfo.of(effectNode)));
        SrpApiCompat.addLegacyPeerSignatures(jar, infos);
        ClassInfo packet = infos.stream().filter(info -> info.name.equals(PACKET)).findFirst().orElseThrow();
        assertTrue(packet.hasMethod(SrpCotesiaParticleCompat.PARTICLE_ID_GETTER, "()I"));
        assertTrue(packet.hasMethod(SrpCotesiaParticleCompat.PARTICLE_ID_SETTER, "(I)V"));

        handlerNode.methods.removeIf(method -> method.name.equals("spawnParticles"));
        infos = new ArrayList<>(List.of(ClassInfo.of(packetNode), ClassInfo.of(handlerNode), ClassInfo.of(effectNode)));
        SrpApiCompat.addLegacyPeerSignatures(jar, infos);
        packet = infos.stream().filter(info -> info.name.equals(PACKET)).findFirst().orElseThrow();
        assertFalse(packet.hasMethod(SrpCotesiaParticleCompat.PARTICLE_ID_GETTER, "()I"));
    }

    private static LegacyJar srpJar() {
        LegacyJar jar = new LegacyJar(Path.of("srparasites.jar"));
        jar.version = LegacyVersion.V1_12_2;
        LegacyModDecl mod = new LegacyModDecl();
        mod.legacyId = "srparasites";
        jar.mods.add(mod);
        return jar;
    }

    private static Map<String, byte[]> currentSrpClasses() {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(PACKET, packetClass());
        classes.put(EFFECT, effectClass());
        return classes;
    }

    private static byte[] packetClass() {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = PACKET;
        node.superName = "java/lang/Object";
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "x", "D", null, null));
        MethodNode constructor = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "<init>", SrpCotesiaParticleCompat.PARTICLE_CTOR_DESC, null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        constructor.maxStack = 1;
        constructor.maxLocals = 10;
        node.methods.add(constructor);
        node.methods.add(emptyMethod("fromBytes", "(L" + BYTE_BUF + ";)V", 2));
        node.methods.add(emptyMethod("toBytes", "(L" + BYTE_BUF + ";)V", 2));
        return write(node);
    }

    private static byte[] effectClass() {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = EFFECT;
        node.superName = "java/lang/Object";
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE, "sendParticle",
                SrpCotesiaParticleCompat.SEND_PARTICLE_DESC, null, null);
        method.instructions.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, PACKET));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new InsnNode(Opcodes.DCONST_0));
        method.instructions.add(new InsnNode(Opcodes.DCONST_0));
        method.instructions.add(new InsnNode(Opcodes.DCONST_0));
        method.instructions.add(new InsnNode(Opcodes.FCONST_0));
        method.instructions.add(new InsnNode(Opcodes.FCONST_0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, PACKET, "<init>", SrpCotesiaParticleCompat.PARTICLE_CTOR_DESC, false));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 4));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/dhanantry/scapeandrunparasites/SRPMain", "network",
                "L" + SIMPLE_NETWORK + ";"));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SIMPLE_NETWORK, "sendToAllTracking", NETWORK_DESC, false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 11;
        method.maxLocals = 5;
        node.methods.add(method);
        return write(node);
    }

    private static MethodNode emptyMethod(String name, String descriptor, int locals) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, name, descriptor, null, null);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 0;
        method.maxLocals = locals;
        return method;
    }

    private static ClassNode plainClass(String name) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = name;
        node.superName = "java/lang/Object";
        return node;
    }

    private static int countCalls(MethodNode method, String owner, String name) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) count++;
        }
        return count;
    }

    private static MethodInsnNode findCall(MethodNode method, String owner, String name, String descriptor) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor)) return call;
        }
        return null;
    }

    private static int indexOf(MethodNode method, AbstractInsnNode target) {
        int index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction == target) return index;
            index++;
        }
        return -1;
    }

    private static MethodNode findMethod(ClassNode node, String name, String descriptor) {
        return node.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst().orElse(null);
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] write(ClassNode node) {
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }
}
