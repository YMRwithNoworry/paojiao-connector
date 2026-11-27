package org.kylepitcock.legacyconnector.loader.transform.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

class SrpMidgeShotServerFixTest implements Opcodes {
    private static final String TARGET = "com/maidnarch/srpcalamity/entity/item/EntityMidgeShot";
    private static final String ENUM = "com/dhanantry/scapeandrunparasites/client/particle/SRPEnumParticle";
    private static final String PARTICLE_SPAWNER = "com/dhanantry/scapeandrunparasites/client/particle/ParticleSpawner";

    @Test
    void removesClientParticleLoopFromImpactHandler() {
        ClassNode node = new ClassNode();
        node.name = TARGET;
        MethodNode impact = new MethodNode(ACC_PROTECTED, "func_70184_a", "(Lnet/minecraft/util/math/RayTraceResult;)V", null, null);
        LabelNode condition = new LabelNode();
        LabelNode exit = new LabelNode();
        impact.instructions.add(new InsnNode(ICONST_0));
        impact.instructions.add(new VarInsnNode(ISTORE, 2));
        impact.instructions.add(condition);
        impact.instructions.add(new VarInsnNode(ILOAD, 2));
        impact.instructions.add(new IntInsnNode(BIPUSH, 32));
        impact.instructions.add(new JumpInsnNode(IF_ICMPGE, exit));
        impact.instructions.add(new FieldInsnNode(GETSTATIC, ENUM, "RHAPPY", "L" + ENUM + ";"));
        impact.instructions.add(new InsnNode(ACONST_NULL));
        impact.instructions.add(new MethodInsnNode(INVOKESTATIC, PARTICLE_SPAWNER, "spawnParticle",
                "(L" + ENUM + ";DDDDDDIII)Lnet/minecraft/client/particle/Particle;", false));
        impact.instructions.add(new InsnNode(POP));
        impact.instructions.add(new IincInsnNode(2, 1));
        impact.instructions.add(new JumpInsnNode(GOTO, condition));
        impact.instructions.add(exit);
        impact.instructions.add(new InsnNode(RETURN));
        node.methods.add(impact);

        assertEquals(1, SrpMidgeShotServerFix.apply(node));
        boolean hasParticleCall = false;
        boolean hasParticleEnumRead = false;
        boolean hasReturn = false;
        for (var instruction : impact.instructions) {
            if (instruction instanceof MethodInsnNode call && PARTICLE_SPAWNER.equals(call.owner)) hasParticleCall = true;
            if (instruction instanceof FieldInsnNode field && field.owner.equals(ENUM)) hasParticleEnumRead = true;
            if (instruction.getOpcode() == RETURN) hasReturn = true;
        }
        assertFalse(hasParticleCall);
        assertFalse(hasParticleEnumRead);
        assertTrue(hasReturn);
        assertEquals(0, SrpMidgeShotServerFix.apply(node));
    }

    @Test
    void ignoresOtherClassesAndMethods() {
        ClassNode node = new ClassNode();
        node.name = "com/example/Other";
        MethodNode method = new MethodNode(ACC_PUBLIC, "func_70184_a", "(Lnet/minecraft/util/math/RayTraceResult;)V", null, null);
        method.instructions.add(new InsnNode(RETURN));
        node.methods.add(method);

        assertEquals(0, SrpMidgeShotServerFix.apply(node));
    }
}
