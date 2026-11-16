package org.kylepitcock.legacyconnector.loader.transform.compat;

import java.util.List;
import java.util.Map;
import org.kylepitcock.legacyconnector.loader.LegacyJar;
import org.kylepitcock.legacyconnector.loader.LegacyVersion;
import org.kylepitcock.legacyconnector.loader.shim.ClassInfo;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Preserves Cotesia's player-aware SRP particle hooks after SRP moved particle spawning into its client packet handler. */
final class SrpCotesiaParticleCompat {
    static final String SRP_EFFECT_BASE = "com/dhanantry/scapeandrunparasites/potion/SRPEffectBase";
    static final String SRP_PACKET = "com/dhanantry/scapeandrunparasites/network/SRPPacketParticle";
    static final String SRP_PARTICLE_HANDLER = SRP_PACKET + "$Handler";
    static final String COTESIA_EFFECT_MIXIN = "com/srpcotesia/mixin/SRPEffectBaseMixin";
    static final String PARTICLE_ID_GETTER = "mcc$getParticleEntityId";
    static final String PARTICLE_ID_SETTER = "mcc$setParticleEntityId";
    static final String PARTICLE_ID_DESC = "()I";
    static final String PARTICLE_ID_SET_DESC = "(I)V";
    static final String ENTITY_LIVING_BASE = "net/minecraft/entity/EntityLivingBase";
    static final String ENTITY = "net/minecraft/entity/Entity";
    static final String RANDOM = "java/util/Random";
    static final String BYTE_BUF = "io/netty/buffer/ByteBuf";
    static final String PARTICLE_CTOR_DESC = "(DDDFFB)V";
    static final String SEND_PARTICLE_DESC = "(L" + ENTITY_LIVING_BASE + ";BZ)V";
    static final String HANDLER_SPAWN_DESC = "(Lcom/dhanantry/scapeandrunparasites/client/particle/SRPEnumParticle;L"
            + RANDOM + ";L" + SRP_PACKET + ";III)V";
    static final String HANDLER_HANDLE_DESC = "(L" + SRP_PACKET
            + ";Lnet/minecraftforge/fml/common/network/simpleimpl/MessageContext;)V";
    private static final String PARTICLE_ID_FIELD = "mcc$particleEntityId";
    private static final String SIMPLE_NETWORK = "net/minecraftforge/fml/common/network/simpleimpl/SimpleNetworkWrapper";
    private static final String MESSAGE_DESC = "Lnet/minecraftforge/fml/common/network/simpleimpl/IMessage;";

    private SrpCotesiaParticleCompat() {}

    static int applyToSrp(LegacyJar jar, Map<String, byte[]> classes, Map<String, ClassInfo> peers) {
        if (!isSrparasites(jar) || !peers.containsKey(COTESIA_EFFECT_MIXIN)) return 0;

        byte[] packetBytes = classes.get(SRP_PACKET);
        byte[] effectBytes = classes.get(SRP_EFFECT_BASE);
        if (packetBytes == null || effectBytes == null) return 0;

        ClassNode packet = read(packetBytes);
        ClassNode effect = read(effectBytes);
        MethodNode fromBytes = findMethod(packet, "fromBytes", "(L" + BYTE_BUF + ";)V");
        MethodNode toBytes = findMethod(packet, "toBytes", "(L" + BYTE_BUF + ";)V");
        MethodNode sendParticle = findMethod(effect, "sendParticle", SEND_PARTICLE_DESC);
        MethodInsnNode sendTracking = sendParticle == null ? null : findCall(sendParticle, SIMPLE_NETWORK, "sendToAllTracking",
                "(" + MESSAGE_DESC + "L" + ENTITY + ";)V");
        VarInsnNode packetStore = sendParticle == null ? null : packetConstructorStore(sendParticle);
        if (fromBytes == null || toBytes == null || sendParticle == null || sendTracking == null || packetStore == null
                || packet.fields.stream().anyMatch(field -> field.name.equals(PARTICLE_ID_FIELD))
                || findMethod(packet, PARTICLE_ID_GETTER, PARTICLE_ID_DESC) != null
                || findMethod(packet, PARTICLE_ID_SETTER, PARTICLE_ID_SET_DESC) != null) return 0;

        packet.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC, PARTICLE_ID_FIELD, "I", null, null));
        packet.methods.add(particleIdGetter());
        packet.methods.add(particleIdSetter());
        appendReadEntityId(fromBytes);
        appendWriteEntityId(toBytes);
        assignEntityId(sendParticle, packetStore);

        classes.put(SRP_PACKET, write(packet));
        classes.put(SRP_EFFECT_BASE, write(effect));
        return 1;
    }

    static void addPeerSignatures(LegacyJar jar, List<ClassInfo> classes) {
        if (!isSrparasites(jar)) return;
        ClassInfo packet = null;
        ClassInfo handler = null;
        ClassInfo effect = null;
        for (ClassInfo info : classes) {
            if (info.name.equals(SRP_PACKET)) packet = info;
            else if (info.name.equals(SRP_PARTICLE_HANDLER)) handler = info;
            else if (info.name.equals(SRP_EFFECT_BASE)) effect = info;
        }
        if (!hasCurrentParticleApi(packet, handler, effect)) return;
        packet.methods.putIfAbsent(PARTICLE_ID_GETTER + PARTICLE_ID_DESC, Opcodes.ACC_PUBLIC);
        packet.methods.putIfAbsent(PARTICLE_ID_SETTER + PARTICLE_ID_SET_DESC, Opcodes.ACC_PUBLIC);
    }

    static boolean hasCurrentParticleApi(Map<String, ClassInfo> peers) {
        return hasCurrentParticleApi(peers.get(SRP_PACKET), peers.get(SRP_PARTICLE_HANDLER), peers.get(SRP_EFFECT_BASE));
    }

    private static boolean hasCurrentParticleApi(ClassInfo packet, ClassInfo handler, ClassInfo effect) {
        String prefix = LegacyVersion.V1_12_2.linkPrefix();
        String byteBuf = "(Lio/netty/buffer/ByteBuf;)V";
        String entityLivingBase = "(L" + prefix + ENTITY_LIVING_BASE + ";BZ)V";
        String handlerSpawn = "(Lcom/dhanantry/scapeandrunparasites/client/particle/SRPEnumParticle;Ljava/util/Random;L"
                + SRP_PACKET + ";III)V";
        String handlerHandle = "(L" + SRP_PACKET + ";L" + prefix
                + "net/minecraftforge/fml/common/network/simpleimpl/MessageContext;)V";
        return packet != null && handler != null && effect != null
                && packet.hasMethod("<init>", PARTICLE_CTOR_DESC)
                && packet.hasMethod("fromBytes", byteBuf)
                && packet.hasMethod("toBytes", byteBuf)
                && handler.hasMethod("spawnParticles", handlerSpawn)
                && handler.hasMethod("handle", handlerHandle)
                && effect.hasMethod("sendParticle", entityLivingBase);
    }

    private static boolean isSrparasites(LegacyJar jar) {
        return jar.version == LegacyVersion.V1_12_2 && jar.mods.stream().anyMatch(mod -> mod.legacyId != null
                && mod.legacyId.equalsIgnoreCase("srparasites"));
    }

    private static void appendReadEntityId(MethodNode method) {
        InsnList code = new InsnList();
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BYTE_BUF, "readInt", "()I", false));
        code.add(new FieldInsnNode(Opcodes.PUTFIELD, SRP_PACKET, PARTICLE_ID_FIELD, "I"));
        insertBeforeReturn(method, code);
        method.maxStack = Math.max(method.maxStack, 2);
    }

    private static void appendWriteEntityId(MethodNode method) {
        InsnList code = new InsnList();
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, SRP_PACKET, PARTICLE_ID_FIELD, "I"));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BYTE_BUF, "writeInt", "(I)L" + BYTE_BUF + ";", false));
        code.add(new InsnNode(Opcodes.POP));
        insertBeforeReturn(method, code);
        method.maxStack = Math.max(method.maxStack, 3);
    }

    private static void insertBeforeReturn(MethodNode method, InsnList code) {
        AbstractInsnNode last = method.instructions.getLast();
        while (last != null && last.getOpcode() < 0) last = last.getPrevious();
        if (last == null || last.getOpcode() != Opcodes.RETURN) throw new IllegalArgumentException(method.name + " has no RETURN");
        method.instructions.insertBefore(last, code);
    }

    private static MethodNode particleIdGetter() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                PARTICLE_ID_GETTER, PARTICLE_ID_DESC, null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, SRP_PACKET, PARTICLE_ID_FIELD, "I"));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 1;
        method.maxLocals = 1;
        return method;
    }

    private static MethodNode particleIdSetter() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                PARTICLE_ID_SETTER, PARTICLE_ID_SET_DESC, null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, SRP_PACKET, PARTICLE_ID_FIELD, "I"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 2;
        method.maxLocals = 2;
        return method;
    }

    private static void assignEntityId(MethodNode method, VarInsnNode packetStore) {
        InsnList code = new InsnList();
        code.add(new VarInsnNode(Opcodes.ALOAD, packetStore.var));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY, "func_145782_y", "()I", false));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SRP_PACKET, PARTICLE_ID_SETTER, PARTICLE_ID_SET_DESC, false));
        method.instructions.insert(packetStore, code);
        method.maxStack = Math.max(method.maxStack, 2);
    }

    private static VarInsnNode packetConstructorStore(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals(SRP_PACKET)
                    || !call.name.equals("<init>") || !call.desc.equals(PARTICLE_CTOR_DESC)) continue;
            AbstractInsnNode next = instruction.getNext();
            while (next != null && next.getOpcode() < 0) next = next.getNext();
            if (next instanceof VarInsnNode store && store.getOpcode() == Opcodes.ASTORE) return store;
        }
        return null;
    }

    private static MethodInsnNode findCall(MethodNode method, String owner, String name, String descriptor) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor)) return call;
        }
        return null;
    }

    private static MethodNode findMethod(ClassNode node, String name, String descriptor) {
        for (MethodNode method : node.methods) if (method.name.equals(name) && method.desc.equals(descriptor)) return method;
        return null;
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
