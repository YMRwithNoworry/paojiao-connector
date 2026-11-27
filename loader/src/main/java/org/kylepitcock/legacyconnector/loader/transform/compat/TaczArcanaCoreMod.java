package org.kylepitcock.legacyconnector.loader.transform.compat;

import java.util.List;
import java.util.Set;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import net.neoforged.neoforgespi.coremod.ICoreMod;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Replaces Arcana's Forge-only launcher transform on the native TaCZ dependency. */
public final class TaczArcanaCoreMod implements ICoreMod, ITransformer<ClassNode>, Opcodes {
    private static final String ATTACHMENT_TYPE = "com/tacz/guns/api/item/attachment/AttachmentType";
    private static final String ATTACHMENT_TYPE_TARGET = "com.tacz.guns.api.item.attachment.AttachmentType";
    private static final String MOD_ID = "taczexpands";
    private static final String NETWORK_HANDLER = "com/tacz/guns/network/NetworkHandler";
    private static final String LEGACY_CHANNEL = "net/minecraftforge/network/simple/SimpleChannel";
    private static final String MODULE = "MODULE";
    private static final String CHANNEL = "CHANNEL";
    private static final String CONSTRUCTOR = "(Ljava/lang/String;I)V";

    @Override
    public Iterable<? extends ITransformer<?>> getTransformers() {
        return List.of(this);
    }

    @Override
    public Set<Target<ClassNode>> targets() {
        return Set.of(Target.targetPreClass(ATTACHMENT_TYPE_TARGET), Target.targetPreClass(NETWORK_HANDLER));
    }

    @Override
    public TargetType<ClassNode> getTargetType() { return TargetType.PRE_CLASS; }

    @Override
    public TransformerVoteResult castVote(ITransformerVotingContext context) { return TransformerVoteResult.YES; }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        if (!isEnabled()) return input;
        if (ATTACHMENT_TYPE.equals(input.name)) addModule(input);
        else if (NETWORK_HANDLER.equals(input.name)) addLegacyChannel(input);
        return input;
    }

    static void addLegacyChannel(ClassNode node) {
        String descriptor = "L" + LEGACY_CHANNEL + ";";
        for (FieldNode field : node.fields) {
            if (CHANNEL.equals(field.name) && descriptor.equals(field.desc)) return;
        }

        node.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, CHANNEL, descriptor, null, null));
        MethodNode clinit = null;
        for (MethodNode method : node.methods) {
            if ("<clinit>".equals(method.name) && "()V".equals(method.desc)) {
                clinit = method;
                break;
            }
        }
        InsnList init = new InsnList();
        init.add(new LdcInsnNode("tacz"));
        init.add(new LdcInsnNode("legacy"));
        init.add(new MethodInsnNode(INVOKESTATIC, "net/minecraft/resources/ResourceLocation", "fromNamespaceAndPath",
                "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;", false));
        init.add(new MethodInsnNode(INVOKESTATIC, LEGACY_CHANNEL, "legacy",
                "(Lnet/minecraft/resources/ResourceLocation;)L" + LEGACY_CHANNEL + ";", false));
        init.add(new FieldInsnNode(PUTSTATIC, node.name, CHANNEL, descriptor));
        if (clinit == null) {
            clinit = new MethodNode(ACC_STATIC, "<clinit>", "()V", null, null);
            clinit.instructions.add(init);
            clinit.instructions.add(new InsnNode(RETURN));
            clinit.maxStack = 2;
            node.methods.add(clinit);
            return;
        }
        AbstractInsnNode returnInsn = clinit.instructions.getLast();
        while (returnInsn != null && returnInsn.getOpcode() != RETURN) returnInsn = returnInsn.getPrevious();
        if (returnInsn == null) throw unsupportedLayout(node);
        clinit.instructions.insertBefore(returnInsn, init);
        clinit.maxStack = Math.max(clinit.maxStack, 2);
    }


    static void addModule(ClassNode node) {
        int ordinal = 0;
        for (FieldNode field : node.fields) {
            if ((field.access & ACC_ENUM) == 0) continue;
            if (MODULE.equals(field.name)) return;
            ordinal++;
        }
        String desc = "L" + node.name + ";";
        String valuesDesc = "()[" + desc;
        MethodNode clinit = null;
        MethodNode values = null;
        boolean hasConstructor = false;
        for (MethodNode method : node.methods) {
            if ("<clinit>".equals(method.name)) clinit = method;
            else if ("$values".equals(method.name) && valuesDesc.equals(method.desc)) values = method;
            else if ("<init>".equals(method.name) && CONSTRUCTOR.equals(method.desc)) hasConstructor = true;
        }
        if (!hasConstructor || clinit == null || values == null) throw unsupportedLayout(node);

        MethodInsnNode buildValues = null;
        for (AbstractInsnNode insn : clinit.instructions) {
            if (insn instanceof MethodInsnNode call && call.getOpcode() == INVOKESTATIC
                    && node.name.equals(call.owner) && "$values".equals(call.name) && valuesDesc.equals(call.desc)) {
                buildValues = call;
                break;
            }
        }
        AbstractInsnNode size = values.instructions.getFirst();
        while (size != null && size.getOpcode() < 0) size = size.getNext();
        AbstractInsnNode end = values.instructions.getLast();
        while (end != null && end.getOpcode() < 0) end = end.getPrevious();
        if (buildValues == null || size == null || end == null || end.getOpcode() != ARETURN
                || !(size.getNext() instanceof TypeInsnNode array) || array.getOpcode() != ANEWARRAY
                || !node.name.equals(array.desc)) throw unsupportedLayout(node);

        FieldNode module = new FieldNode(ACC_PUBLIC | ACC_STATIC | ACC_FINAL | ACC_ENUM, MODULE, desc, null, null);
        AnnotationNode serializedName = new AnnotationNode("Lcom/google/gson/annotations/SerializedName;");
        serializedName.visit("value", "module");
        module.visibleAnnotations = List.of(serializedName);
        node.fields.add(module);

        // Both the codec and TaCZ's fromId table are built from values() later in <clinit>.
        InsnList init = new InsnList();
        init.add(new TypeInsnNode(NEW, node.name));
        init.add(new InsnNode(DUP));
        init.add(new LdcInsnNode(MODULE));
        init.add(new LdcInsnNode(ordinal));
        init.add(new MethodInsnNode(INVOKESPECIAL, node.name, "<init>", CONSTRUCTOR, false));
        init.add(new FieldInsnNode(PUTSTATIC, node.name, MODULE, desc));
        clinit.instructions.insertBefore(buildValues, init);
        clinit.maxStack = Math.max(clinit.maxStack, 4);

        values.instructions.set(size, new LdcInsnNode(ordinal + 1));
        InsnList append = new InsnList();
        append.add(new InsnNode(DUP));
        append.add(new LdcInsnNode(ordinal));
        append.add(new FieldInsnNode(GETSTATIC, node.name, MODULE, desc));
        append.add(new InsnNode(AASTORE));
        values.instructions.insertBefore(end, append);
        values.maxStack = Math.max(values.maxStack, 4);
    }

    private static boolean isEnabled() {
        String modIds = System.getProperty("legacyconnector.forge1201.mods", "");
        for (String modId : modIds.split(",")) if (MOD_ID.equals(modId)) return true;
        return false;
    }
    private static IllegalStateException unsupportedLayout(ClassNode node) {
        return new IllegalStateException("Cannot extend " + node.name + " for Arcana: unsupported attachment enum layout");
    }
}
