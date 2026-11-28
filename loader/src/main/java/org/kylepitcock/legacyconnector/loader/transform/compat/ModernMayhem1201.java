package org.kylepitcock.legacyconnector.loader.transform.compat;

import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Combines Modern Mayhem's two Forge item-extension registrations for NeoForge. */
public final class ModernMayhem1201 {
    private static final String SERVER_PACKAGE = "net/tkg/ModernMayhem/server/item/curios/";
    private static final String CUSTOM_RENDERER = "()Lnet/minecraft/client/renderer/BlockEntityWithoutLevelRenderer;";
    private static final String ACCEPT = "(Ljava/lang/Object;)V";

    private ModernMayhem1201() {}

    public static boolean accepts(Set<String> modIds) {
        return modIds.contains("mm");
    }

    public static int apply(ClassNode node) {
        String outer = outerFor(node.name);
        if (outer == null) return 0;
        if (node.name.equals(outer)) return combineRegistrations(node, outer);
        if (node.name.equals(outer + "$1")) return addCustomRenderer(node, rendererFor(outer));
        return 0;
    }

    private static String outerFor(String name) {
        for (String simple : new String[] {
                "back/BackpackItem",
                "body/BandoleerItem",
                "body/HexagonRigItem",
                "body/PlateCarrierItem",
                "body/ReconRigItem",
                "facewear/NVGGogglesItem",
                "facewear/TVGGogglesItem",
                "facewear/VisorItem",
                "head/HeadGearItems",
                "knee/KneepadItems"}) {
            String outer = SERVER_PACKAGE + simple;
            if (name.equals(outer) || name.equals(outer + "$1")) return outer;
        }
        return null;
    }

    private static String rendererFor(String outer) {
        return switch (outer.substring(SERVER_PACKAGE.length())) {
            case "back/BackpackItem" -> "net/tkg/ModernMayhem/client/renderer/curios/back/BackpackRenderer$BackpackSlotRenderer";
            case "body/BandoleerItem" -> "net/tkg/ModernMayhem/client/renderer/curios/body/BandoleerRenderer$BandoleerSlotRenderer";
            case "body/HexagonRigItem" -> "net/tkg/ModernMayhem/client/renderer/curios/body/HexagonRigRenderer$HexagonRigSlotRenderer";
            case "body/PlateCarrierItem" -> "net/tkg/ModernMayhem/client/renderer/curios/body/PlateCarrierRenderer$PlateCarrierSlotRenderer";
            case "body/ReconRigItem" -> "net/tkg/ModernMayhem/client/renderer/curios/body/ReconRigRenderer$ReconRigSlotRenderer";
            case "facewear/NVGGogglesItem", "facewear/TVGGogglesItem", "facewear/VisorItem" ->
                    "net/tkg/ModernMayhem/client/renderer/curios/facewear/GenericSpecialGogglesRenderer$GenericNVGGogglesSlotRenderer";
            case "head/HeadGearItems" -> "net/tkg/ModernMayhem/client/renderer/curios/head/HeadGearRenderer$HeadGearItemRenderer";
            case "knee/KneepadItems" -> "net/tkg/ModernMayhem/client/renderer/curios/knee/KneepadRenderer$KneepadItemRenderer";
            default -> null;
        };
    }

    private static int combineRegistrations(ClassNode node, String outer) {
        MethodNode initialize = node.methods.stream()
                .filter(method -> method.name.equals("initializeClient")
                        && method.desc.equals("(Ljava/util/function/Consumer;)V"))
                .findFirst().orElse(null);
        if (initialize == null || initialize.instructions == null) return 0;

        MethodInsnNode first = null;
        MethodInsnNode second = null;
        for (AbstractInsnNode instruction : initialize.instructions) {
            if (!(instruction instanceof MethodInsnNode call)
                    || call.getOpcode() != Opcodes.INVOKEINTERFACE
                    || !call.owner.equals("java/util/function/Consumer")
                    || !call.name.equals("accept")
                    || !call.desc.equals(ACCEPT)) continue;
            if (first == null) first = call;
            else {
                second = call;
                break;
            }
        }
        if (first == null || second == null || second == first) return 0;
        String secondOwner = secondOwner(first, second);
        if (!secondOwner.equals(outer + "$2")) return 0;

        AbstractInsnNode instruction = first.getNext();
        while (instruction != null) {
            AbstractInsnNode next = instruction.getNext();
            initialize.instructions.remove(instruction);
            if (instruction == second) break;
            instruction = next;
        }
        return 1;
    }

    private static String secondOwner(MethodInsnNode first, MethodInsnNode second) {
        AbstractInsnNode instruction = first.getNext();
        while (instruction != null && instruction != second) {
            if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW) return type.desc;
            instruction = instruction.getNext();
        }
        return "";
    }

    private static int addCustomRenderer(ClassNode node, String renderer) {
        if (renderer == null) return 0;
        boolean alreadyPresent = node.methods.stream()
                .anyMatch(method -> method.name.equals("getCustomRenderer") && method.desc.equals(CUSTOM_RENDERER));
        if (alreadyPresent) return 0;

        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "getCustomRenderer", CUSTOM_RENDERER, null, null);
        InsnList instructions = method.instructions;
        instructions.add(new TypeInsnNode(Opcodes.NEW, renderer));
        instructions.add(new InsnNode(Opcodes.DUP));
        instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, renderer, "<init>", "()V", false));
        instructions.add(new InsnNode(Opcodes.ARETURN));
        node.methods.add(method);
        return 1;
    }
}
