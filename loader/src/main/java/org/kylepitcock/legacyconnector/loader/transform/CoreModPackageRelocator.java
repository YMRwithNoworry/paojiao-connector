package org.kylepitcock.legacyconnector.loader.transform;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

/** Moves a coremod loader out of a package claimed by one of its Mixin configs. */
final class CoreModPackageRelocator {
    private CoreModPackageRelocator() {}

    static String relocateIfInMixinPackage(Map<String, ClassNode> classes, String coremodClass, String jarKey,
            List<String> configResources, Map<String, byte[]> resources) {
        ClassNode plugin = classes.get(coremodClass);
        if (plugin == null || !isInMixinPackage(coremodClass, configResources, resources)) return coremodClass;

        String packageSegment = jarKey.replaceAll("[^A-Za-z0-9_]", "_");
        if (packageSegment.isBlank() || !Character.isJavaIdentifierStart(packageSegment.charAt(0))) packageSegment = "_" + packageSegment;
        String relocatedName = "legacyconnector/legacycoremod/" + packageSegment + "/" + coremodClass;
        Remapper remapper = new Remapper() {
            @Override
            public String map(String internalName) {
                return internalName.equals(coremodClass) ? relocatedName : internalName;
            }
        };
        Map<String, ClassNode> relocated = new LinkedHashMap<>();
        for (ClassNode node : classes.values()) {
            ClassNode rewritten = new ClassNode();
            node.accept(new ClassRemapper(rewritten, remapper));
            relocated.put(rewritten.name, rewritten);
        }
        classes.clear();
        classes.putAll(relocated);
        return relocatedName;
    }

    private static boolean isInMixinPackage(String className, List<String> configResources, Map<String, byte[]> resources) {
        for (String resource : configResources) {
            byte[] contents = resources.get(resource);
            if (contents == null) continue;
            try {
                JsonElement parsed = JsonParser.parseString(new String(contents, StandardCharsets.UTF_8));
                if (!parsed.isJsonObject()) continue;
                JsonObject config = parsed.getAsJsonObject();
                if (!config.has("package") || !config.get("package").isJsonPrimitive()) continue;
                String mixinPackage = config.get("package").getAsString().replace('.', '/');
                if (!mixinPackage.isBlank() && className.startsWith(mixinPackage + "/")) return true;
            } catch (RuntimeException ignored) {
                // Invalid configs are handled and reported by LegacyMixinConfigs.
            }
        }
        return false;
    }
}
