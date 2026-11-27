package org.kylepitcock.legacyconnector.loader.transform.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
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
import org.objectweb.asm.tree.VarInsnNode;

/** Bridges Arcana 1.4.2's Forge launcher and pre-1.21 TaCZ and Minecraft APIs. */
public final class TaczArcana1201 implements Opcodes {
    static final String MOD_ID = "taczexpands";
    private static final String CONFIG_CLASS = "group/taczexpands/client/config/PreLoadConfig";
    private static final String OBFUSCATED_CONFIG_CLASS = "group/taczexpands/dist/Ma2czh1U";
    private static final String PRELOAD_CONFIG = "com/tacz/guns/config/PreLoadModConfig";
    private static final String CONFIG_SPEC = "Lnet/neoforged/neoforge/common/ModConfigSpec;";
    private static final String CONFIG_BRIDGE = "legacyconnector/v1_20_1/compat/TaczArcanaConfig1201";
    private static final String CAMERA_ACCESSOR = "group/taczexpands/client/mixin/accessor/IAccessorCamera";
    private static final Set<String> CAMERA_INVOKERS = Set.of("move", "n8b5mhI6");
    private static final String OLD_MOVE = "(DDD)V";
    private static final String NEW_MOVE = "(FFF)V";
    private static final String BULLET_MIXIN = "group/taczexpands/common/mixin/MixinEntityKineticBullet";
    private static final Set<String> SPAWN_DATA_HANDLERS = Set.of("postWriteSpawnData", "postReadSpawnData");
    private static final String GUN_HUD_MIXIN = "group/taczexpands/client/mixin/MixinGunHudOverlay";
    private static final String HEAT_HUD_MIXIN = "group/taczexpands/client/mixin/MixinHeatBarOverlay";
    private static final String CALLBACK = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String FORGE_GUI = "legacyconnector/v1_20_1/net/minecraftforge/client/gui/overlay/ForgeGui";
    private static final String GUI_GRAPHICS = "net/minecraft/client/gui/GuiGraphics";
    private static final String DELTA_TRACKER = "net/minecraft/client/DeltaTracker";
    private static final String LEGACY_HUD_DESC = "(L" + FORGE_GUI + ";L" + GUI_GRAPHICS + ";FII" + CALLBACK + ")V";
    private static final String NATIVE_HUD_DESC = "(L" + GUI_GRAPHICS + ";L" + DELTA_TRACKER + ";" + CALLBACK + ")V";
    private static final String FRIENDLY_BUFFER = "Lnet/minecraft/network/FriendlyByteBuf;";
    private static final String REGISTRY_BUFFER = "Lnet/minecraft/network/RegistryFriendlyByteBuf;";
    private static final String GAME_RENDERER_MIXIN = "group/taczexpands/client/mixin/MixinGameRenderer";
    private static final String OLD_RENDER_LEVEL_DESC = "(FJLcom/mojang/blaze3d/vertex/PoseStack;)V";
    private static final String NEW_RENDER_LEVEL_DESC = "(L" + DELTA_TRACKER + ";)V";
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String SBM_PLUGIN = "group/taczexpands/client/coremod/TACZExpandsClientMixinPlugin";
    private static final String SBM_MODERN_INVOKER = "group/taczexpands/client/mixin/accessor/sbm/ModernFirstPersonRenderHandlerInvoker";
    private static final String OLD_CLIENT_TICK = "legacyconnector/v1_20_1/net/minecraftforge/event/TickEvent$ClientTickEvent";
    private static final String LEGACY_CLIENT_TICK_DESC = "(Lnet/minecraftforge/event/TickEvent$ClientTickEvent;)V";
    private static final String OLD_CLIENT_TICK_DESC = "(L" + OLD_CLIENT_TICK + ";)V";
    private static final String OLD_CLIENT_TICK_PHASE = "legacyconnector/v1_20_1/net/minecraftforge/event/TickEvent$Phase";
    private static final String OLD_CLIENT_TICK_PHASE_DESC = "L" + OLD_CLIENT_TICK_PHASE + ";";
    private static final String OLD_CLIENT_TICK_CTOR_DESC = "(" + OLD_CLIENT_TICK_PHASE_DESC + ")V";
    private static final String NATIVE_CLIENT_TICK = "net/neoforged/neoforge/client/event/ClientTickEvent$Pre";
    private static final String NATIVE_CLIENT_TICK_DESC = "(L" + NATIVE_CLIENT_TICK + ";)V";
    private static final String CONNECTION = "net/minecraft/network/Connection";
    private static final String CONNECTION_PROTOCOL = "net/minecraft/network/ConnectionProtocol";
    private static final String ATTRIBUTE_KEY_DESC = "Lio/netty/util/AttributeKey;";
    private static final String MINECRAFT = "net/minecraft/client/Minecraft";
    private static final String TEXTURE_MANAGER_DESC = "Lnet/minecraft/client/renderer/texture/TextureManager;";
    private static final String CLIENT_COMPAT = "legacyconnector/v1_20_1/compat/TaczArcanaClientCompat1201";

    private TaczArcana1201() {}

    public static boolean accepts(Set<String> modIds) {
        return modIds.contains(MOD_ID);
    }

    public static ZipEntry embeddedPayload(ZipFile zip) {
        if (zip.getEntry("META-INF/mods.toml") != null || zip.getEntry("META-INF/neoforge.mods.toml") != null
                || zip.getEntry("META-INF/services/net.minecraftforge.forgespi.locating.IModLocator") == null
                || zip.getEntry("group/taczexpands/locator/ModLocator.class") == null) return null;
        ZipEntry embedded = zip.getEntry("META-INF/mod.jar");
        return embedded != null && !embedded.isDirectory() ? embedded : null;
    }


    /** Returns the number of changed methods/calls for the translated-class report. */
    public static int apply(ClassNode node) {
        int changed = bridgePreloadConfig(node);
        changed += adaptCameraAccessor(node);
        changed += adaptCameraInvokerCalls(node);
        changed += adaptSpawnDataHandlers(node);
        changed += adaptHudMixins(node);
        changed += adaptGameRendererShadow(node);
        changed += adaptSimpleBedrockFirstPerson(node);
        changed += adaptConnectionPlayState(node);
        changed += adaptMinecraftTextureManagerAccess(node);
        return changed;
    }

    private static int adaptMinecraftTextureManagerAccess(ClassNode node) {
        int changed = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (!(instruction instanceof FieldInsnNode field) || field.getOpcode() != GETFIELD
                        || !MINECRAFT.equals(field.owner) || !"textureManager".equals(field.name)
                        || !TEXTURE_MANAGER_DESC.equals(field.desc)) continue;
                method.instructions.set(field, new MethodInsnNode(INVOKEVIRTUAL, MINECRAFT, "getTextureManager",
                        "()" + TEXTURE_MANAGER_DESC, false));
                changed++;
            }
        }
        return changed;
    }

    private static int adaptConnectionPlayState(ClassNode node) {
        int changed = 0;
        for (MethodNode method : node.methods) {
            if (!"()Z".equals(method.desc) || !usesLegacyPlayProtocolAttribute(method)) continue;
            method.instructions.clear();
            method.instructions.add(new MethodInsnNode(INVOKESTATIC, CLIENT_COMPAT, "isPlayClientConnection", "()Z", false));
            method.instructions.add(new InsnNode(IRETURN));
            if (method.tryCatchBlocks != null) method.tryCatchBlocks.clear();
            method.localVariables = null;
            method.maxStack = 1;
            method.maxLocals = (method.access & ACC_STATIC) == 0 ? 1 : 0;
            changed++;
        }
        return changed;
    }

    private static boolean usesLegacyPlayProtocolAttribute(MethodNode method) {
        boolean readsProtocolAttribute = false;
        boolean comparesPlayProtocol = false;
        for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof FieldInsnNode field) || field.getOpcode() != GETSTATIC) continue;
            if (CONNECTION.equals(field.owner) && ATTRIBUTE_KEY_DESC.equals(field.desc)) readsProtocolAttribute = true;
            if (CONNECTION_PROTOCOL.equals(field.owner) && "PLAY".equals(field.name)) comparesPlayProtocol = true;
        }
        return readsProtocolAttribute && comparesPlayProtocol;
    }

    private static int adaptSimpleBedrockFirstPerson(ClassNode node) {
        if (SBM_PLUGIN.equals(node.name)) {
            int changed = 0;
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof LdcInsnNode constant && LEGACY_CLIENT_TICK_DESC.equals(constant.cst)) {
                        constant.cst = NATIVE_CLIENT_TICK_DESC;
                        changed++;
                    }
                }
            }
            return changed;
        }
        if (SBM_MODERN_INVOKER.equals(node.name)) {
            int changed = 0;
            for (MethodNode method : node.methods) {
                if (OLD_CLIENT_TICK_DESC.equals(method.desc) && hasInvoker(method, "onClientTick")) {
                    method.desc = NATIVE_CLIENT_TICK_DESC;
                    changed++;
                }
            }
            return changed;
        }

        int changed = 0;
        for (MethodNode method : node.methods) {
            // The released caller is obfuscated; match the event construction at the invoker call.
            for (AbstractInsnNode instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call) || call.getOpcode() != INVOKESTATIC
                        || !SBM_MODERN_INVOKER.equals(call.owner) || !OLD_CLIENT_TICK_DESC.equals(call.desc)) continue;
                if (!(call.getPrevious() instanceof MethodInsnNode ctor) || ctor.getOpcode() != INVOKESPECIAL
                        || !OLD_CLIENT_TICK.equals(ctor.owner) || !"<init>".equals(ctor.name)
                        || !OLD_CLIENT_TICK_CTOR_DESC.equals(ctor.desc)) continue;
                if (!(ctor.getPrevious() instanceof FieldInsnNode phase) || phase.getOpcode() != GETSTATIC
                        || !OLD_CLIENT_TICK_PHASE.equals(phase.owner) || !"START".equals(phase.name)
                        || !OLD_CLIENT_TICK_PHASE_DESC.equals(phase.desc)) continue;
                AbstractInsnNode dup = phase.getPrevious();
                if (dup == null || dup.getOpcode() != DUP || !(dup.getPrevious() instanceof TypeInsnNode type)
                        || type.getOpcode() != NEW || !OLD_CLIENT_TICK.equals(type.desc)) continue;

                type.desc = NATIVE_CLIENT_TICK;
                method.instructions.remove(phase);
                ctor.owner = NATIVE_CLIENT_TICK;
                ctor.desc = "()V";
                call.desc = NATIVE_CLIENT_TICK_DESC;
                changed += 4;
            }
        }
        return changed;
    }

    private static int adaptGameRendererShadow(ClassNode node) {
        if (!GAME_RENDERER_MIXIN.equals(node.name)) return 0;
        for (MethodNode method : node.methods) {
            if (!"renderLevel".equals(method.name) || !OLD_RENDER_LEVEL_DESC.equals(method.desc)
                    || !hasAnnotation(method.visibleAnnotations, SHADOW) && !hasAnnotation(method.invisibleAnnotations, SHADOW)) continue;
            method.desc = NEW_RENDER_LEVEL_DESC;
            method.signature = null;
            return 1;
        }
        return 0;
    }

    private static int bridgePreloadConfig(ClassNode node) {
        if (!CONFIG_CLASS.equals(node.name) && !OBFUSCATED_CONFIG_CLASS.equals(node.name)) return 0;
        FieldNode spec = null;
        for (FieldNode field : node.fields) {
            if ((field.access & ACC_STATIC) != 0 && CONFIG_SPEC.equals(field.desc)) {
                if (spec != null) return 0;
                spec = field;
            }
        }
        if (spec == null) return 0;

        MethodNode load = null;
        List<MethodNode> removed = new ArrayList<>();
        for (MethodNode method : node.methods) {
            if (method.desc.equals("()L" + PRELOAD_CONFIG + ";")) {
                removed.add(method);
                continue;
            }
            if (!"(Ljava/nio/file/Path;)V".equals(method.desc)) continue;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && node.name.equals(call.owner)
                        && ("()L" + PRELOAD_CONFIG + ";").equals(call.desc)) {
                    load = method;
                    break;
                }
            }
        }
        if (load == null) return 0;

        load.instructions.clear();
        load.instructions.add(new FieldInsnNode(GETSTATIC, node.name, spec.name, CONFIG_SPEC));
        load.instructions.add(new MethodInsnNode(INVOKESTATIC, CONFIG_BRIDGE, "load", "(" + CONFIG_SPEC + ")V", false));
        load.instructions.add(new InsnNode(RETURN));
        if (load.tryCatchBlocks != null) load.tryCatchBlocks.clear();
        load.localVariables = null;
        load.visibleLocalVariableAnnotations = null;
        load.invisibleLocalVariableAnnotations = null;
        load.maxStack = 1;
        load.maxLocals = 2;
        node.methods.removeAll(removed);
        return 1 + removed.size();
    }

    private static int adaptCameraAccessor(ClassNode node) {
        if (!CAMERA_ACCESSOR.equals(node.name)) return 0;
        int changed = 0;
        for (MethodNode method : node.methods) {
            if (CAMERA_INVOKERS.contains(method.name) && OLD_MOVE.equals(method.desc)) {
                method.desc = NEW_MOVE;
                changed++;
            }
        }
        return changed;
    }

    private static int adaptCameraInvokerCalls(ClassNode node) {
        int changed = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)
                        || !CAMERA_ACCESSOR.equals(call.owner)
                        || !CAMERA_INVOKERS.contains(call.name)
                        || !OLD_MOVE.equals(call.desc)) continue;

                int z = method.maxLocals;
                int y = z + 1;
                InsnList conversions = new InsnList();
                conversions.add(new InsnNode(D2F));
                conversions.add(new VarInsnNode(FSTORE, z));
                conversions.add(new InsnNode(D2F));
                conversions.add(new VarInsnNode(FSTORE, y));
                conversions.add(new InsnNode(D2F));
                conversions.add(new VarInsnNode(FLOAD, y));
                conversions.add(new VarInsnNode(FLOAD, z));
                method.instructions.insertBefore(call, conversions);
                method.maxLocals = y + 1;
                call.desc = NEW_MOVE;
                changed++;
            }
        }
        return changed;
    }

    private static int adaptSpawnDataHandlers(ClassNode node) {
        if (!BULLET_MIXIN.equals(node.name)) return 0;
        int changed = 0;
        String oldDescriptor = "(" + FRIENDLY_BUFFER + CALLBACK + ")V";
        for (MethodNode method : node.methods) {
            if (SPAWN_DATA_HANDLERS.contains(method.name) && oldDescriptor.equals(method.desc)) {
                method.desc = "(" + REGISTRY_BUFFER + CALLBACK + ")V";
                changed++;
            }
        }
        return changed;
    }

    private static int adaptHudMixins(ClassNode node) {
        if (!GUN_HUD_MIXIN.equals(node.name) && !HEAT_HUD_MIXIN.equals(node.name)) return 0;
        int changed = 0;
        for (MethodNode original : new ArrayList<>(node.methods)) {
            if (!LEGACY_HUD_DESC.equals(original.desc)) continue;
            AnnotationNode inject = findInject(original);
            if (inject == null) continue;
            removeInject(original);
            AnnotationNode nativeInject = new AnnotationNode(INJECT);
            inject.accept(nativeInject);
            selectNativeRender(nativeInject);

            MethodNode adapter = new MethodNode(ACC_PRIVATE | ACC_SYNTHETIC,
                    "legacyconnector$" + original.name + "$native", NATIVE_HUD_DESC, null, null);
            adapter.visibleAnnotations = new ArrayList<>(List.of(nativeInject));
            InsnList code = adapter.instructions;
            code.add(new VarInsnNode(ALOAD, 0));
            code.add(new MethodInsnNode(INVOKESTATIC, FORGE_GUI, "mcc$instance", "()L" + FORGE_GUI + ";", false));
            code.add(new VarInsnNode(ALOAD, 1));
            code.add(new VarInsnNode(ALOAD, 2));
            code.add(new InsnNode(ICONST_0));
            code.add(new MethodInsnNode(INVOKEINTERFACE, DELTA_TRACKER, "getGameTimeDeltaPartialTick", "(Z)F", true));
            code.add(new VarInsnNode(ALOAD, 1));
            code.add(new MethodInsnNode(INVOKEVIRTUAL, GUI_GRAPHICS, "guiWidth", "()I", false));
            code.add(new VarInsnNode(ALOAD, 1));
            code.add(new MethodInsnNode(INVOKEVIRTUAL, GUI_GRAPHICS, "guiHeight", "()I", false));
            code.add(new VarInsnNode(ALOAD, 3));
            code.add(new MethodInsnNode(INVOKEVIRTUAL, node.name, original.name, original.desc, false));
            code.add(new InsnNode(RETURN));
            adapter.maxLocals = 4;
            adapter.maxStack = 7;
            node.methods.add(adapter);
            changed++;
        }
        return changed;
    }

    private static boolean hasAnnotation(List<AnnotationNode> annotations, String descriptor) {
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations) if (descriptor.equals(annotation.desc)) return true;
        return false;
    }

    private static boolean hasInvoker(MethodNode method, String target) {
        return hasInvoker(method.visibleAnnotations, target) || hasInvoker(method.invisibleAnnotations, target);
    }

    private static boolean hasInvoker(List<AnnotationNode> annotations, String target) {
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations) {
            if (!"Lorg/spongepowered/asm/mixin/gen/Invoker;".equals(annotation.desc)) continue;
            if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2) {
                if ("value".equals(annotation.values.get(i)) && target.equals(annotation.values.get(i + 1))) return true;
            }
        }
        return false;
    }

    private static AnnotationNode findInject(MethodNode method) {
        if (method.visibleAnnotations != null) for (AnnotationNode annotation : method.visibleAnnotations) {
            if (INJECT.equals(annotation.desc)) return annotation;
        }
        if (method.invisibleAnnotations != null) for (AnnotationNode annotation : method.invisibleAnnotations) {
            if (INJECT.equals(annotation.desc)) return annotation;
        }
        return null;
    }

    private static void removeInject(MethodNode method) {
        if (method.visibleAnnotations != null) method.visibleAnnotations.removeIf(a -> INJECT.equals(a.desc));
        if (method.invisibleAnnotations != null) method.invisibleAnnotations.removeIf(a -> INJECT.equals(a.desc));
    }

    private static void selectNativeRender(AnnotationNode annotation) {
        setValue(annotation, "method", List.of("render(L" + GUI_GRAPHICS + ";L" + DELTA_TRACKER + ";)V"));
    }

    private static void setValue(AnnotationNode annotation, String key, Object value) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) {
                annotation.values.set(i + 1, value);
                return;
            }
        }
        annotation.visit(key, value);
    }
}
