package org.kylepitcock.legacyconnector.loader.transform;

import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Prevents coremods from re-registering legacy mixin configs after the translator has filtered them. */
final class CoreModMixinConfigCalls implements Opcodes {
    private static final String MIXINS = "org/spongepowered/asm/mixin/Mixins";
    private static final String ADD_CONFIGURATION = "(Ljava/lang/String;)V";

    private CoreModMixinConfigCalls() {}

    static int apply(ClassNode plugin, Set<String> managedConfigs) {
        if (plugin == null || managedConfigs.isEmpty()) return 0;
        int redirected = 0;
        for (MethodNode method : plugin.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;) {
                AbstractInsnNode next = instruction.getNext();
                if (instruction instanceof MethodInsnNode call && call.getOpcode() == INVOKESTATIC
                        && call.owner.equals(MIXINS) && call.name.equals("addConfiguration") && call.desc.equals(ADD_CONFIGURATION)) {
                    AbstractInsnNode argument = previousInstruction(call);
                    if (argument instanceof LdcInsnNode constant && constant.cst instanceof String name && managedConfigs.contains(name)) {
                        method.instructions.set(call, new InsnNode(POP));
                        redirected++;
                    }
                }
                instruction = next;
            }
        }
        return redirected;
    }

    private static AbstractInsnNode previousInstruction(AbstractInsnNode instruction) {
        AbstractInsnNode previous = instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        return previous;
    }
}
