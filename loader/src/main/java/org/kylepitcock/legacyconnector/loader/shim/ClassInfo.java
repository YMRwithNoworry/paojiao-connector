package org.kylepitcock.legacyconnector.loader.shim;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal structural view of a class: hierarchy plus member signatures. Used for link checking and for
 * resolving which class actually declares a member referenced through a subclass.
 */
public final class ClassInfo {
    public final String name;
    public final String superName;
    public final List<String> interfaces;
    public final int access;
    /** Keyed by {@code name + descriptor}. Value is the member access flags. */
    public final Map<String, Integer> methods = new HashMap<>();
    /** Keyed by {@code name + ":" + descriptor}. */
    public final Map<String, Integer> fields = new HashMap<>();
    /** Invocation and field-access site counts keyed by method signature and injector selector. */
    public final Map<String, Integer> instructionSites = new HashMap<>();
    /**
     * The supertypes as the class's own jar names them before translation ({@code [super, interfaces...]}; notch names in a reobfuscated jar), set for the
     * classes {@code LegacyJarTransformer.scan} returns: another jar's subclass of such a class finds the vanilla members it inherits through them.
     */
    public String[] originalSupers;
    /** The name the class has in its own jar before translation (a default-package class is renamed into a package), set like {@link #originalSupers}. */
    public String originalName;

    public ClassInfo(String name, String superName, List<String> interfaces, int access) {
        this.name = name;
        this.superName = superName;
        this.interfaces = interfaces;
        this.access = access;
    }

    public static ClassInfo of(org.objectweb.asm.tree.ClassNode node) {
        ClassInfo info = new ClassInfo(node.name, node.superName, List.copyOf(node.interfaces), node.access);
        for (var f : node.fields) info.fields.put(f.name + ":" + f.desc, f.access);
        for (var m : node.methods) {
            String methodKey = m.name + m.desc;
            info.methods.put(methodKey, m.access);
            for (var instruction : m.instructions) {
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call) {
                    info.addInstructionSite(methodKey, "INVOKE", call.getOpcode(), "L" + call.owner + ";" + call.name + call.desc);
                } else if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode field) {
                    info.addInstructionSite(methodKey, "FIELD", field.getOpcode(), "L" + field.owner + ";" + field.name + ":" + field.desc);
                }
            }
        }
        return info;
    }

    public void addInstructionSite(String methodKey, String kind, int opcode, String selector) {
        String key = methodKey + '\0' + kind + '\0' + opcode + '\0' + selector;
        instructionSites.merge(key, 1, Integer::sum);
    }

    public int instructionSiteCount(String methodKey, String kind, String selector, int opcode) {
        String prefix = methodKey + '\0' + kind + '\0';
        if (opcode >= 0) return instructionSites.getOrDefault(prefix + opcode + '\0' + selector, 0);
        int count = 0;
        for (var entry : instructionSites.entrySet()) {
            if (entry.getKey().startsWith(prefix) && entry.getKey().endsWith('\0' + selector)) count += entry.getValue();
        }
        return count;
    }

    public boolean hasMethod(String name, String desc) {
        return methods.containsKey(name + desc);
    }

    public boolean hasField(String name, String desc) {
        return fields.containsKey(name + ":" + desc);
    }

    /** Field names only; virtual-field accessors are matched by name and owner type. */
    public boolean hasFieldNamed(String name) {
        String prefix = name + ":";
        for (String key : fields.keySet()) {
            if (key.startsWith(prefix)) return true;
        }
        return false;
    }
}
