package org.kylepitcock.legacyconnector.loader.transform;

import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Keeps the original chat injection code callable under the 1.21.1 target signatures. */
final class ChatMixin1201 implements Opcodes {
    static final String TARGET = "net/minecraft/client/gui/components/ChatComponent";
    static final String SCREEN = "net/minecraft/client/gui/screens/ChatScreen";
    private static final String CALLBACK = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
    private static final String RETURNABLE = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String RENDER = "Lnet/minecraft/client/gui/GuiGraphics;III";
    private static final String COMPONENT = "Lnet/minecraft/network/chat/Component;";
    private static final String SIGNATURE = "Lnet/minecraft/network/chat/MessageSignature;";
    private static final String TAG = "Lnet/minecraft/client/GuiMessageTag;";
    private static final String OLD_MESSAGE = COMPONENT + SIGNATURE + "I" + TAG + "Z";
    private static final String NEW_MESSAGE = COMPONENT + SIGNATURE + TAG;
    private static final String MC = "net/minecraft/client/Minecraft";
    private static final String GUI = "net/minecraft/client/gui/Gui";

    private ChatMixin1201() {}

    static int apply(ClassNode node, String target) {
        int changed = 0;
        for (MethodNode old : List.copyOf(node.methods)) {
            if ((old.access & (ACC_PRIVATE | ACC_STATIC)) != ACC_PRIVATE) continue;
            if (wrap(node, old, old.visibleAnnotations, target) || wrap(node, old, old.invisibleAnnotations, target)) changed++;
        }
        return changed;
    }

    private static boolean wrap(ClassNode node, MethodNode old, List<AnnotationNode> annotations, String owner) {
        if (annotations == null) return false;
        for (AnnotationNode inject : annotations) {
            if (!INJECT.equals(inject.desc) || inject.values == null) continue;
            if (!(value(inject, "method") instanceof List<?> selectors) || selectors.size() != 1) continue;
            if (!(value(inject, "at") instanceof List<?> at) || at.size() != 1 || !(at.getFirst() instanceof AnnotationNode point)) continue;
            if (!(value(point, "value") instanceof String kind) || !List.of("HEAD", "TAIL", "RETURN").contains(kind)
                    || value(inject, "slice") != null) continue;
            Object selector = selectors.getFirst();
            boolean render = TARGET.equals(owner) && old.desc.equals(handler(RENDER))
                    && ("render".equals(selector) || ("render(" + RENDER + ")V").equals(selector));
            boolean message = TARGET.equals(owner) && old.desc.equals(handler(OLD_MESSAGE)) && ("addMessage(" + OLD_MESSAGE + ")V").equals(selector);
            boolean scroll = SCREEN.equals(owner) && old.desc.equals("(DDD" + RETURNABLE + ")V")
                    && ("mouseScrolled".equals(selector) || "mouseScrolled(DDD)Z".equals(selector));
            if (!render && !message && !scroll) continue;
            String args = render ? RENDER + "Z" : NEW_MESSAGE;
            String target = scroll ? "mouseScrolled(DDDD)Z" : (render ? "render" : "addMessage") + "(" + args + ")V";
            String name = "legacyconnector$chat$" + old.name;
            String desc = scroll ? "(DDDD" + RETURNABLE + ")V" : handler(args);
            if (node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(desc))) continue;
            MethodNode adapter = new MethodNode(ACC_PRIVATE | ACC_SYNTHETIC, name, desc, null, null);
            for (int i = 0; i < inject.values.size(); i += 2) {
                if ("method".equals(inject.values.get(i))) inject.values.set(i + 1, List.of(target));
            }
            annotations.remove(inject);
            if (annotations == old.visibleAnnotations) adapter.visibleAnnotations = List.of(inject);
            else adapter.invisibleAnnotations = List.of(inject);
            adapter.instructions.add(new VarInsnNode(ALOAD, 0));
            if (scroll) {
                for (int slot : new int[] {1, 3, 7}) adapter.instructions.add(new VarInsnNode(DLOAD, slot));
                adapter.instructions.add(new VarInsnNode(ALOAD, 9));
            } else if (render) {
                adapter.instructions.add(new VarInsnNode(ALOAD, 1));
                for (int i = 2; i <= 4; i++) adapter.instructions.add(new VarInsnNode(ILOAD, i));
                adapter.instructions.add(new VarInsnNode(ALOAD, 6));
            } else {
                adapter.instructions.add(new VarInsnNode(ALOAD, 1));
                adapter.instructions.add(new VarInsnNode(ALOAD, 2));
                // The 1.21.1 public method only adds new messages, not chat-history refreshes.
                adapter.instructions.add(new MethodInsnNode(INVOKESTATIC, MC, "getInstance", "()L" + MC + ";", false));
                adapter.instructions.add(new FieldInsnNode(GETFIELD, MC, "gui", "L" + GUI + ";"));
                adapter.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, GUI, "getGuiTicks", "()I", false));
                adapter.instructions.add(new VarInsnNode(ALOAD, 3));
                adapter.instructions.add(new InsnNode(ICONST_0));
                adapter.instructions.add(new VarInsnNode(ALOAD, 4));
            }
            adapter.instructions.add(new MethodInsnNode(INVOKESPECIAL, node.name, old.name, old.desc, false));
            adapter.instructions.add(new InsnNode(RETURN));
            adapter.maxLocals = 1 + slots(desc);
            adapter.maxStack = 1 + slots(old.desc);
            node.methods.add(adapter);
            return true;
        }
        return false;
    }

    private static int slots(String desc) {
        return (Type.getArgumentsAndReturnSizes(desc) >> 2) - 1;
    }

    private static String handler(String args) {
        return "(" + args + CALLBACK + ")V";
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }
}
