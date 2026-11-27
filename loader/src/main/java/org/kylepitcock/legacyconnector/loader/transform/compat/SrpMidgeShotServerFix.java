package org.kylepitcock.legacyconnector.loader.transform.compat;

import java.util.HashMap;
import java.util.Map;
import org.kylepitcock.legacyconnector.loader.LegacyJar;
import org.kylepitcock.legacyconnector.loader.LegacyVersion;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Removes SRP Calamity's client-only particle loop from the server-side midge-shot impact handler. */
public final class SrpMidgeShotServerFix implements Opcodes {
    private static final String TARGET = "com/maidnarch/srpcalamity/entity/item/EntityMidgeShot";
    private static final String IMPACT_DESC = "(Lnet/minecraft/util/math/RayTraceResult;)V";
    private static final String PARTICLE_SPAWNER = "com/dhanantry/scapeandrunparasites/client/particle/ParticleSpawner";
    private static final String PARTICLE_METHOD = "spawnParticle";

    private SrpMidgeShotServerFix() {}

    /** Applies the fix to SRP Calamity's raw 1.12.2 class map. */
    public static int apply(LegacyJar jar, Map<String, byte[]> classes) {
        if (jar.version != LegacyVersion.V1_12_2) return 0;
        byte[] bytes = classes.get(TARGET);
        if (bytes == null) return 0;

        ClassNode node = new ClassNode(ASM9);
        new ClassReader(bytes).accept(node, 0);
        int changes = apply(node);
        if (changes > 0) {
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            classes.put(TARGET, writer.toByteArray());
        }
        return changes;
    }

    static int apply(ClassNode node) {
        if (!TARGET.equals(node.name)) return 0;
        int changes = 0;
        for (MethodNode method : node.methods) {
            if (!IMPACT_DESC.equals(method.desc)) continue;
            changes += removeParticleLoops(method);
        }
        return changes;
    }

    private static int removeParticleLoops(MethodNode method) {
        int changes = 0;
        while (true) {
            AbstractInsnNode particleCall = findParticleCall(method);
            if (particleCall == null) return changes;
            AbstractInsnNode[] range = findLoopRange(method, particleCall);
            if (range == null) return changes;
            removeRange(method, range[0], range[1]);
            changes++;
        }
    }

    private static AbstractInsnNode findParticleCall(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.getOpcode() == INVOKESTATIC
                    && PARTICLE_SPAWNER.equals(call.owner) && PARTICLE_METHOD.equals(call.name)) return call;
        }
        return null;
    }

    /** Finds the zero-to-32 counted loop that surrounds the particle call. */
    private static AbstractInsnNode[] findLoopRange(MethodNode method, AbstractInsnNode particleCall) {
        AbstractInsnNode[] instructions = method.instructions.toArray();
        Map<AbstractInsnNode, Integer> indexes = new HashMap<>();
        for (int i = 0; i < instructions.length; i++) indexes.put(instructions[i], i);
        int callIndex = indexes.get(particleCall);

        JumpInsnNode backJump = null;
        for (int i = callIndex + 1; i < instructions.length; i++) {
            AbstractInsnNode instruction = instructions[i];
            if (!(instruction instanceof JumpInsnNode jump) || jump.getOpcode() != GOTO) continue;
            Integer target = indexes.get(jump.label);
            if (target != null && target < callIndex) {
                backJump = jump;
                break;
            }
        }
        if (backJump == null) return null;

        JumpInsnNode exitJump = null;
        for (int i = callIndex - 1; i >= 0; i--) {
            AbstractInsnNode instruction = instructions[i];
            if (!(instruction instanceof JumpInsnNode jump) || jump.getOpcode() != IF_ICMPGE) continue;
            Integer target = indexes.get(jump.label);
            if (target != null && target > indexes.get(backJump)) {
                exitJump = jump;
                break;
            }
        }
        if (exitJump == null) return null;

        int exitIndex = indexes.get(exitJump);
        for (int i = exitIndex - 1; i >= 1; i--) {
            AbstractInsnNode store = instructions[i];
            if (!(store instanceof VarInsnNode variable) || variable.getOpcode() != ISTORE) continue;
            AbstractInsnNode initializer = previousExecutable(instructions, i - 1);
            if (initializer == null || initializer.getOpcode() != ICONST_0) continue;
            AbstractInsnNode condition = nextExecutable(instructions, i + 1);
            if (!(condition instanceof VarInsnNode load) || load.getOpcode() != ILOAD || load.var != variable.var) continue;
            return new AbstractInsnNode[] {initializer, backJump};
        }
        return null;
    }

    private static void removeRange(MethodNode method, AbstractInsnNode first, AbstractInsnNode last) {
        for (AbstractInsnNode instruction = first; instruction != null; ) {
            AbstractInsnNode next = instruction.getNext();
            method.instructions.remove(instruction);
            if (instruction == last) return;
            instruction = next;
        }
    }

    private static AbstractInsnNode previousExecutable(AbstractInsnNode[] instructions, int index) {
        while (index >= 0 && instructions[index].getOpcode() < 0) index--;
        return index < 0 ? null : instructions[index];
    }

    private static AbstractInsnNode nextExecutable(AbstractInsnNode[] instructions, int index) {
        while (index < instructions.length && instructions[index].getOpcode() < 0) index++;
        return index >= instructions.length ? null : instructions[index];
    }
}
