package org.kylepitcock.legacyconnector.loader.transform;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** The settings formerly passed to ServerPlayer in a packet are now passed as the settings record itself. */
final class ClientSettingsMixin1201 {
    private static final String PLAYER = "net/minecraft/server/level/ServerPlayer";
    private static final String PACKET = "net/minecraft/network/protocol/game/ServerboundClientInformationPacket";
    private static final String SETTINGS = "net/minecraft/server/level/ClientInformation";
    private static final String SETTING_ARGUMENT = "L" + PACKET + ";";
    private static final String CALLBACK = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
    private static final String OLD_HANDLER = "(" + SETTING_ARGUMENT + CALLBACK + ")V";
    private static final String OLD_SELECTOR = "updateOptions(" + SETTING_ARGUMENT + ")V";
    private static final String NEW_SELECTOR = "updateOptions(L" + SETTINGS + ";)V";
    private static final Set<String> RECORD_ACCESSORS = Set.of("language()Ljava/lang/String;", "viewDistance()I",
            "chatVisibility()Lnet/minecraft/world/entity/player/ChatVisiblity;", "chatColors()Z", "modelCustomisation()I",
            "mainHand()Lnet/minecraft/world/entity/HumanoidArm;", "textFilteringEnabled()Z", "allowsListing()Z");
    private static final SimpleRemapper RECORD_TYPE = new SimpleRemapper(PACKET, SETTINGS);

    private ClientSettingsMixin1201() {}

    static int apply(ClassNode node) {
        if (!targetsPlayer(node.invisibleAnnotations) && !targetsPlayer(node.visibleAnnotations)) return 0;
        int changed = 0;
        for (int i = 0; i < node.methods.size(); i++) {
            MethodNode method = node.methods.get(i);
            if (!OLD_HANDLER.equals(method.desc)) continue;
            List<?> selectors = optionsSelectors(method.visibleAnnotations);
            if (selectors == null) selectors = optionsSelectors(method.invisibleAnnotations);
            if (selectors == null) continue;
            boolean compatible = true;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && (call.desc.contains(SETTING_ARGUMENT)
                        || call.owner.equals(PACKET) && !RECORD_ACCESSORS.contains(call.name + call.desc))) {
                    compatible = false;
                    break;
                }
            }
            if (!compatible) continue;
            // Keep the callback at the same slot and remap its descriptors, local types and frame entries together.
            @SuppressWarnings("unchecked")
            List<Object> methods = (List<Object>) selectors;
            methods.set(0, NEW_SELECTOR);
            MethodNode updated = new MethodNode(method.access, method.name, RECORD_TYPE.mapMethodDesc(method.desc),
                    RECORD_TYPE.mapSignature(method.signature, false), method.exceptions.toArray(String[]::new));
            method.accept(new MethodRemapper(updated, RECORD_TYPE));
            node.methods.set(i, updated);
            changed++;
        }
        return changed;
    }

    private static boolean targetsPlayer(List<AnnotationNode> annotations) {
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;") || annotation.values == null) continue;
            int targets = 0;
            boolean player = false;
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                Object key = annotation.values.get(i);
                if (!(annotation.values.get(i + 1) instanceof List<?> list) || !("value".equals(key) || "targets".equals(key))) continue;
                targets += list.size();
                for (Object value : list) {
                    if (value instanceof Type type && type.getInternalName().equals(PLAYER)
                            || value instanceof String name && name.replace('.', '/').equals(PLAYER)) player = true;
                }
            }
            return player && targets == 1;
        }
        return false;
    }

    private static List<?> optionsSelectors(List<AnnotationNode> annotations) {
        if (annotations == null) return null;
        for (AnnotationNode annotation : annotations) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;") || annotation.values == null) continue;
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                if (!"method".equals(annotation.values.get(i)) || !(annotation.values.get(i + 1) instanceof List<?> list) || list.size() != 1) continue;
                Object selector = list.getFirst();
                if ("updateOptions".equals(selector) || OLD_SELECTOR.equals(selector)) return list;
            }
        }
        return null;
    }
}
