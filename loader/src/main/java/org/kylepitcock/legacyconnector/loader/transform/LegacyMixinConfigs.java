package org.kylepitcock.legacyconnector.loader.transform;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.kylepitcock.legacyconnector.loader.LegacyJar;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

/**
 * Mixin configs shipped by a 1.12.2 mod (MixinBooter / Mixin 0.7 style: {@code MixinConfigs} manifest attribute, early and late
 * mixin loaders; all of them leave the config json in the jar). They are registered with NeoForge through {@code [[mixins]]} in the
 * generated {@code neoforge.mods.toml}, so Mixin applies them like any modern mod's.
 *
 * <p>Only mixins targeting translated mod classes or a compatible hand-written legacy API shim survive; shims are structural API views, not host-game classes. The config is rewritten to be harmless: renamed to
 * {@code legacyconnector.legacy.*} (the name the runtime mixin guard recognizes as ours), not required, no refmap (the names in
 * the annotations are the mod's own deobfuscated ones) and no config plugin (it runs legacy launcher code).
 */
final class LegacyMixinConfigs {
    static final String PREFIX = "legacyconnector.legacy.";
    private static final String MIXIN_DESC = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String INJECT_DESC = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String SHADOW_DESC = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final Gson GSON = new Gson();

    private LegacyMixinConfigs() {}

    /**
     * Moves the mixin configs found in {@code other} to their rewritten form, returning the notes to report. The resource names of
     * the rewritten configs are added to {@code jar.mixinConfigResources}.
     */
    static List<String> apply(LegacyJar jar, Map<String, byte[]> other, Map<String, ClassNode> nodes, Hierarchy hierarchy) {
        List<String> notes = new ArrayList<>();
        List<String> candidates = new ArrayList<>();
        for (var e : other.entrySet()) {
            String name = e.getKey();
            if (!name.endsWith(".json") || name.startsWith("assets/") || name.startsWith("data/") || name.startsWith("META-INF/")) continue;
            if (e.getValue().length > 512 * 1024 || !contains(e.getValue(), "\"package\"")) continue;
            candidates.add(name);
        }
        String jarKey = jar.fileName().replaceAll("(?i)\\.jar$", "").replaceAll("[^A-Za-z0-9_-]", "_");
        for (String name : candidates) {
            try {
                JsonElement parsed = GSON.fromJson(new String(other.get(name), StandardCharsets.UTF_8), JsonElement.class);
                if (parsed == null || !parsed.isJsonObject()) continue;
                JsonObject cfg = parsed.getAsJsonObject();
                if (!cfg.has("package") || !cfg.get("package").isJsonPrimitive()) continue;
                String pkg = cfg.get("package").getAsString().replace('.', '/') + "/";
                int total = 0, legacyTargets = 0, kept = 0, omitted = 0, handlersOmitted = 0;
                for (String section : new String[] {"mixins", "client", "server"}) {
                    if (!cfg.has(section) || !cfg.get(section).isJsonArray()) continue;
                    JsonArray in = cfg.getAsJsonArray(section);
                    JsonArray out = new JsonArray();
                    for (JsonElement m : in) {
                        total++;
                        ClassNode node = nodes.get(pkg + m.getAsString().replace('.', '/'));
                        if (node != null && targetsLegacy(node, nodes, hierarchy)) {
                            legacyTargets++;
                            String target = incompatibleSuperTarget(node, nodes, hierarchy);
                            if (target != null) {
                                omitted++;
                                notes.add("Mixin config " + name + ": mixin " + node.name.replace('/', '.') + " extends "
                                        + (node.superName == null ? "<none>" : node.superName.replace('/', '.'))
                                        + ", which is not in target hierarchy " + target.replace('/', '.') + "; omitted");
                                continue;
                            }
                            List<String> strippedShadows = stripUnusedMissingShimShadows(node, hierarchy);
                            for (String shadow : strippedShadows) {
                                notes.add("Mixin config " + name + ": mixin " + node.name.replace('/', '.') + " " + shadow + "; declaration stripped");
                            }
                            String shadowProblem = incompatibleShadowProblem(node, hierarchy);
                            if (shadowProblem != null) {
                                omitted++;
                                notes.add("Mixin config " + name + ": mixin " + node.name.replace('/', '.') + " " + shadowProblem + "; omitted");
                                continue;
                            }
                            List<String> injectionProblems = filterIncompatibleInjectors(node, hierarchy);
                            for (String problem : injectionProblems) {
                                handlersOmitted++;
                                notes.add("Mixin config " + name + ": mixin " + node.name.replace('/', '.') + " " + problem + "; injector handler omitted");
                            }
                            AbsentDependencyStubber.apply(node, hierarchy);
                            String missingDependency = missingDependency(node, hierarchy);
                            if (missingDependency != null) {
                                omitted++;
                                notes.add("Mixin config " + name + ": mixin " + node.name.replace('/', '.')
                                        + " references missing dependency " + missingDependency.replace('/', '.') + "; omitted");
                                continue;
                            }
                            out.add(m);
                            kept++;
                        }
                    }
                    cfg.add(section, out);
                }
                if (total == 0) continue; // not a mixin config after all
                jar.mixinConfigSources.add(name);
                if (kept == 0) {
                    String reason = legacyTargets == 0
                            ? "none of its " + total + " mixin(s) target a translated legacy mod class or supported API shim; not applied"
                            : "none of its " + legacyTargets + " mixin(s) targeting translated legacy classes are compatible; not applied";
                    notes.add("Mixin config " + name + ": " + reason
                            + (omitted == 0 ? "" : " (" + omitted + " incompatible or unavailable)"));
                    continue;
                }
                cfg.addProperty("required", false);
                cfg.remove("refmap");
                cfg.remove("plugin");
                JsonObject injectors = cfg.has("injectors") && cfg.get("injectors").isJsonObject() ? cfg.getAsJsonObject("injectors") : new JsonObject();
                injectors.addProperty("defaultRequire", 0);
                cfg.add("injectors", injectors);
                String base = name.substring(name.lastIndexOf('/') + 1);
                String out = PREFIX + jarKey + "." + base;
                other.remove(name);
                other.put(out, GSON.toJson(cfg).getBytes(StandardCharsets.UTF_8));
                jar.mixinConfigResources.add(out);
                notes.add("Mixin config " + name + ": " + kept + " of " + legacyTargets
                        + " mixin(s) targeting translated legacy classes or supported API shims are applied; " + (total - legacyTargets)
                        + " do not target a translated class or supported shim"
                        + (omitted == 0 ? "" : "; " + omitted + " incompatible or unavailable mixin(s) were omitted")
                        + (handlersOmitted == 0 ? "" : "; " + handlersOmitted + " incompatible injector handler(s) were omitted"));
            } catch (RuntimeException ex) {
                notes.add("Mixin config " + name + " could not be read: " + ex);
            }
        }
        return notes;
    }

    private static boolean targetsLegacy(ClassNode mixin, Map<String, ClassNode> nodes, Hierarchy hierarchy) {
        return mixinTargets(mixin).stream().anyMatch(target -> isSupportedTarget(target, nodes, hierarchy));
    }

    private static boolean isSupportedTarget(String target, Map<String, ClassNode> nodes, Hierarchy hierarchy) {
        return nodes.containsKey(target) || hierarchy.isLocal(target) || hierarchy.isPeer(target) || hierarchy.isShim(target);
    }

    private static String incompatibleSuperTarget(ClassNode mixin, Map<String, ClassNode> nodes, Hierarchy hierarchy) {
        for (String target : mixinTargets(mixin)) {
            if (!isSupportedTarget(target, nodes, hierarchy)) continue;
            if (mixin.superName == null || !hierarchy.isSubclassOf(target, mixin.superName)) return target;
        }
        return null;
    }

    private static List<String> filterIncompatibleInjectors(ClassNode mixin, Hierarchy hierarchy) {
        List<String> targets = mixinTargets(mixin);
        List<String> problems = new ArrayList<>();
        for (var handler : new ArrayList<>(mixin.methods)) {
            List<AnnotationNode> invalid = new ArrayList<>();
            for (AnnotationNode annotation : methodAnnotations(handler)) {
                if (!isInjector(annotation.desc)) continue;
                String problem = incompatibleInjectorProblem(handler, annotation, targets, hierarchy);
                if (problem != null) {
                    invalid.add(annotation);
                    problems.add(problem);
                }
            }
            if (invalid.isEmpty()) continue;
            if (handler.visibleAnnotations != null) handler.visibleAnnotations.removeAll(invalid);
            if (handler.invisibleAnnotations != null) handler.invisibleAnnotations.removeAll(invalid);
            if (methodAnnotations(handler).stream().noneMatch(annotation -> isInjector(annotation.desc))) mixin.methods.remove(handler);
        }
        return problems;
    }

    private static String incompatibleInjectorProblem(org.objectweb.asm.tree.MethodNode handler, AnnotationNode annotation,
            List<String> targets, Hierarchy hierarchy) {
        List<String> selectors = stringValues(value(annotation, "method"));
        if (selectors.isEmpty()) return null;
        List<MethodTarget> methods = targetMethods(targets, selectors, hierarchy);
        boolean hasWildcard = selectors.stream().anyMatch(LegacyMixinConfigs::hasWildcard);
        if (methods.isEmpty() && !hasWildcard) {
            return "injector " + handler.name + " selects missing target method " + String.join(", ", selectors);
        }
        if (annotation.desc.equals(INJECT_DESC) && !methods.isEmpty()
                && methods.stream().noneMatch(target -> compatibleInjectHandler(handler.desc, target.desc()))) {
            return "@Inject handler " + handler.name + " has a signature incompatible with "
                    + methods.getFirst().owner().replace('/', '.') + "." + methods.getFirst().name() + methods.getFirst().desc();
        }
        if (required(annotation)) {
            String missingAtTarget = missingAtTarget(annotation, hierarchy);
            if (missingAtTarget != null) return "injector " + handler.name + " selects missing target " + missingAtTarget;
            String missingSite = missingInjectionSite(annotation, methods, hierarchy);
            if (missingSite != null) return "injector " + handler.name + " selects " + missingSite;
        }
        return null;
    }

    private static String missingInjectionSite(AnnotationNode annotation, List<MethodTarget> targets, Hierarchy hierarchy) {
        List<AnnotationNode> atPoints = annotations(value(annotation, "at"));
        if (atPoints.size() != 1) return null;
        AnnotationNode at = atPoints.getFirst();
        Object rawKind = value(at, "value");
        Object rawTarget = value(at, "target");
        if (!(rawKind instanceof String kind) || !(rawTarget instanceof String selector)
                || !(kind.equals("INVOKE") || kind.equals("FIELD"))) return null;
        int required = value(annotation, "require") instanceof Integer count ? count : 0;
        if (required <= 0) return null;
        int ordinal = value(at, "ordinal") instanceof Integer index ? index : -1;
        int opcode = value(at, "opcode") instanceof Integer value ? value : -1;
        int matches = 0;
        boolean known = false;
        for (MethodTarget target : targets) {
            org.kylepitcock.legacyconnector.loader.shim.ClassInfo info = hierarchy.find(target.owner());
            if (info == null || !info.hasMethod(target.name(), target.desc())) return null;
            known = true;
            int methodMatches = info.instructionSiteCount(target.name() + target.desc(), kind, selector, opcode);
            if (ordinal >= 0) matches += methodMatches > ordinal ? 1 : 0;
            else matches += methodMatches;
        }
        return known && matches < required ? kind + " site " + selector + " (requires " + required + ", found " + matches + ")" : null;
    }

    private static List<String> stripUnusedMissingShimShadows(ClassNode mixin, Hierarchy hierarchy) {
        List<String> targets = mixinTargets(mixin);
        if (targets.isEmpty() || targets.stream().anyMatch(target -> !hierarchy.isShim(target))) return List.of();
        List<String> stripped = new ArrayList<>();
        for (var method : new ArrayList<>(mixin.methods)) {
            AnnotationNode shadow = methodAnnotations(method).stream()
                    .filter(annotation -> SHADOW_DESC.equals(annotation.desc)).findFirst().orElse(null);
            if (shadow == null || (method.access & org.objectweb.asm.Opcodes.ACC_ABSTRACT) == 0) continue;
            List<String> names = shadowNames(method.name, shadow);
            boolean absent = targets.stream().allMatch(target -> names.stream()
                    .allMatch(name -> hierarchy.resolveMethod(target, name, method.desc) == Hierarchy.Lookup.MISSING_MEMBER));
            if (!absent || referencesMethod(mixin, names, method.desc)) continue;
            mixin.methods.remove(method);
            stripped.add("unused @Shadow method " + names.getFirst() + method.desc + " has no backing member in the legacy API shim");
        }
        return stripped;
    }

    private static boolean referencesMethod(ClassNode mixin, List<String> names, String descriptor) {
        for (var method : mixin.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                        && names.contains(call.name) && descriptor.equals(call.desc)) return true;
                if (instruction instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && referencesMethodHandle(ldc.cst, names, descriptor)) return true;
                if (instruction instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode dynamic) {
                    if (referencesMethodHandle(dynamic.bsm, names, descriptor)) return true;
                    for (Object argument : dynamic.bsmArgs) {
                        if (referencesMethodHandle(argument, names, descriptor)) return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean referencesMethodHandle(Object value, List<String> names, String descriptor) {
        if (value instanceof org.objectweb.asm.Handle handle) {
            return names.contains(handle.getName()) && descriptor.equals(handle.getDesc());
        }
        if (value instanceof org.objectweb.asm.ConstantDynamic dynamic) {
            if (referencesMethodHandle(dynamic.getBootstrapMethod(), names, descriptor)) return true;
            for (int i = 0; i < dynamic.getBootstrapMethodArgumentCount(); i++) {
                if (referencesMethodHandle(dynamic.getBootstrapMethodArgument(i), names, descriptor)) return true;
            }
        }
        return false;
    }

    private static String incompatibleShadowProblem(ClassNode mixin, Hierarchy hierarchy) {
        List<String> targets = mixinTargets(mixin);
        for (var field : mixin.fields) {
            AnnotationNode shadow = fieldShadow(field);
            if (shadow == null) continue;
            List<String> names = shadowNames(field.name, shadow);
            if (!shadowFieldExists(targets, names, field.desc, hierarchy)) {
                return "@Shadow field " + names.getFirst() + ":" + field.desc + " is missing from target hierarchy "
                        + targets.stream().map(name -> name.replace('/', '.')).toList();
            }
        }
        for (var method : mixin.methods) {
            AnnotationNode shadow = methodAnnotations(method).stream().filter(annotation -> SHADOW_DESC.equals(annotation.desc)).findFirst().orElse(null);
            if (shadow == null) continue;
            List<String> names = shadowNames(method.name, shadow);
            if (!shadowMethodExists(targets, names, method.desc, hierarchy)) {
                return "@Shadow method " + names.getFirst() + method.desc + " is missing from target hierarchy "
                        + targets.stream().map(name -> name.replace('/', '.')).toList();
            }
        }
        return null;
    }

    private static AnnotationNode fieldShadow(org.objectweb.asm.tree.FieldNode field) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (field.visibleAnnotations != null) annotations.addAll(field.visibleAnnotations);
        if (field.invisibleAnnotations != null) annotations.addAll(field.invisibleAnnotations);
        return annotations.stream().filter(annotation -> SHADOW_DESC.equals(annotation.desc)).findFirst().orElse(null);
    }

    private static List<String> shadowNames(String name, AnnotationNode annotation) {
        List<String> names = new ArrayList<>();
        String prefix = value(annotation, "prefix") instanceof String configured ? configured : "shadow$";
        names.add(name);
        if (!prefix.isEmpty() && name.startsWith(prefix)) names.add(name.substring(prefix.length()));
        names.addAll(stringValues(value(annotation, "aliases")));
        return names.stream().distinct().toList();
    }

    private static boolean shadowFieldExists(List<String> targets, List<String> names, String descriptor, Hierarchy hierarchy) {
        boolean unknown = false;
        for (String target : targets) {
            for (String name : names) {
                Hierarchy.Lookup lookup = hierarchy.resolveField(target, name, descriptor);
                if (lookup == Hierarchy.Lookup.FOUND) return true;
                if (lookup != Hierarchy.Lookup.MISSING_MEMBER) unknown = true;
            }
        }
        return unknown;
    }

    private static boolean shadowMethodExists(List<String> targets, List<String> names, String descriptor, Hierarchy hierarchy) {
        boolean unknown = false;
        for (String target : targets) {
            for (String name : names) {
                Hierarchy.Lookup lookup = hierarchy.resolveMethod(target, name, descriptor);
                if (lookup == Hierarchy.Lookup.FOUND) return true;
                if (lookup != Hierarchy.Lookup.MISSING_MEMBER) unknown = true;
            }
        }
        return unknown;
    }

    private static String missingDependency(ClassNode mixin, Hierarchy hierarchy) {
        String missing = missingName(mixin.superName, hierarchy);
        if (missing != null) return missing;
        for (String iface : mixin.interfaces) {
            missing = missingName(iface, hierarchy);
            if (missing != null) return missing;
        }
        for (var field : mixin.fields) {
            missing = missingType(Type.getType(field.desc), hierarchy);
            if (missing != null) return missing;
        }
        for (var method : mixin.methods) {
            missing = missingType(Type.getMethodType(method.desc), hierarchy);
            if (missing != null) return missing;
            if (method.exceptions != null) {
                for (String exception : method.exceptions) {
                    missing = missingName(exception, hierarchy);
                    if (missing != null) return missing;
                }
            }
            for (var block : method.tryCatchBlocks) {
                missing = missingName(block.type, hierarchy);
                if (missing != null) return missing;
            }
        }
        return null;
    }

    private static String missingType(Type type, Hierarchy hierarchy) {
        return switch (type.getSort()) {
            case Type.OBJECT -> missingName(type.getInternalName(), hierarchy);
            case Type.ARRAY -> missingType(type.getElementType(), hierarchy);
            case Type.METHOD -> {
                String missing = null;
                for (Type argument : type.getArgumentTypes()) {
                    missing = missingType(argument, hierarchy);
                    if (missing != null) break;
                }
                yield missing == null ? missingType(type.getReturnType(), hierarchy) : missing;
            }
            default -> null;
        };
    }

    private static String missingName(String name, Hierarchy hierarchy) {
        return name != null && hierarchy.isAbsent(name) ? name : null;
    }

    private static boolean isInjector(String descriptor) {
        return descriptor.startsWith("Lorg/spongepowered/asm/mixin/injection/")
                || descriptor.startsWith("Lcom/llamalad7/mixinextras/injector/");
    }

    private static List<AnnotationNode> methodAnnotations(org.objectweb.asm.tree.MethodNode method) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
        return annotations;
    }

    private static List<String> stringValues(Object value) {
        List<String> strings = new ArrayList<>();
        if (value instanceof String string) strings.add(string);
        else if (value instanceof List<?> list) {
            for (Object element : list) if (element instanceof String string) strings.add(string);
        }
        return strings;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values == null) return null;
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }

    private static boolean hasWildcard(String selector) {
        return selector.indexOf('*') >= 0 || selector.indexOf('?') >= 0;
    }

    private static List<MethodTarget> targetMethods(List<String> targets, List<String> selectors, Hierarchy hierarchy) {
        List<MethodSelector> parsed = selectors.stream().map(LegacyMixinConfigs::parseMethodSelector).filter(java.util.Objects::nonNull).toList();
        List<MethodTarget> methods = new ArrayList<>();
        for (String target : targets) {
            org.kylepitcock.legacyconnector.loader.shim.ClassInfo info = hierarchy.find(target);
            if (info == null) continue;
            for (String key : info.methods.keySet()) {
                int descriptorStart = key.indexOf('(');
                if (descriptorStart < 0) continue;
                String name = key.substring(0, descriptorStart);
                String descriptor = key.substring(descriptorStart);
                for (MethodSelector selector : parsed) {
                    if (selector.name().equals(name) && (selector.desc() == null || selector.desc().equals(descriptor))) {
                        methods.add(new MethodTarget(target, name, descriptor));
                        break;
                    }
                }
            }
        }
        return methods.stream().distinct().toList();
    }

    private static MethodSelector parseMethodSelector(String raw) {
        String selector = raw.trim();
        int ownerEnd = selector.indexOf(';');
        if (selector.startsWith("L") && ownerEnd >= 0) selector = selector.substring(ownerEnd + 1);
        int descriptorStart = selector.indexOf('(');
        String name = descriptorStart < 0 ? selector : selector.substring(0, descriptorStart);
        if (name.isBlank() || hasWildcard(name)) return null;
        return new MethodSelector(name, descriptorStart < 0 ? null : selector.substring(descriptorStart));
    }

    private static boolean compatibleInjectHandler(String handlerDescriptor, String targetDescriptor) {
        Type[] handlerArgs = Type.getArgumentTypes(handlerDescriptor);
        Type[] targetArgs = Type.getArgumentTypes(targetDescriptor);
        Type expectedCallback = Type.getReturnType(targetDescriptor).getSort() == Type.VOID
                ? Type.getObjectType("org/spongepowered/asm/mixin/injection/callback/CallbackInfo")
                : Type.getObjectType("org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable");
        if (handlerArgs.length == 1 && handlerArgs[0].equals(expectedCallback)) return true;
        if (handlerArgs.length <= targetArgs.length) return false;
        for (int i = 0; i < targetArgs.length; i++) if (!handlerArgs[i].equals(targetArgs[i])) return false;
        return handlerArgs[targetArgs.length].equals(expectedCallback);
    }

    private static boolean required(AnnotationNode annotation) {
        Object require = value(annotation, "require");
        return require instanceof Integer count && count > 0;
    }

    private static String missingAtTarget(AnnotationNode annotation, Hierarchy hierarchy) {
        for (AnnotationNode at : annotations(value(annotation, "at"))) {
            Object raw = value(at, "target");
            if (!(raw instanceof String selector) || !selector.startsWith("L")) continue;
            int ownerEnd = selector.indexOf(';');
            if (ownerEnd < 2) continue;
            String owner = selector.substring(1, ownerEnd);
            if (hierarchy.find(owner) == null) continue;
            String member = selector.substring(ownerEnd + 1);
            int methodStart = member.indexOf('(');
            if (methodStart >= 0) {
                String name = member.substring(0, methodStart);
                String desc = member.substring(methodStart);
                if (hierarchy.resolveMethod(owner, name, desc) == Hierarchy.Lookup.MISSING_MEMBER) return owner.replace('/', '.') + "." + name + desc;
            } else {
                int fieldStart = member.indexOf(':');
                if (fieldStart >= 0) {
                    String name = member.substring(0, fieldStart);
                    String desc = member.substring(fieldStart + 1);
                    if (hierarchy.resolveField(owner, name, desc) == Hierarchy.Lookup.MISSING_MEMBER) return owner.replace('/', '.') + "." + name + ":" + desc;
                }
            }
        }
        return null;
    }

    private static List<AnnotationNode> annotations(Object value) {
        if (value instanceof AnnotationNode annotation) return List.of(annotation);
        if (value instanceof List<?> list) return list.stream().filter(AnnotationNode.class::isInstance).map(AnnotationNode.class::cast).toList();
        return List.of();
    }

    private record MethodSelector(String name, String desc) {}
    private record MethodTarget(String owner, String name, String desc) {}

    private static List<String> mixinTargets(ClassNode mixin) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (mixin.visibleAnnotations != null) annotations.addAll(mixin.visibleAnnotations);
        if (mixin.invisibleAnnotations != null) annotations.addAll(mixin.invisibleAnnotations);
        List<String> targets = new ArrayList<>();
        for (AnnotationNode a : annotations) {
            if (!MIXIN_DESC.equals(a.desc) || a.values == null) continue;
            for (int i = 0; i + 1 < a.values.size(); i += 2) {
                String key = (String) a.values.get(i);
                if (key.equals("value") || key.equals("targets")) addTargets(targets, a.values.get(i + 1));
            }
        }
        return targets.stream().distinct().toList();
    }

    private static void addTargets(List<String> targets, Object value) {
        if (value instanceof Type type && type.getSort() == Type.OBJECT) {
            targets.add(type.getInternalName());
        } else if (value instanceof String name) {
            String internal = name.replace('.', '/');
            if (internal.startsWith("L") && internal.endsWith(";")) internal = internal.substring(1, internal.length() - 1);
            targets.add(internal);
        } else if (value instanceof List<?> list) {
            for (Object element : list) addTargets(targets, element);
        }
    }
    private static boolean contains(byte[] data, String needle) {
        return new String(data, StandardCharsets.ISO_8859_1).contains(needle);
    }
}
