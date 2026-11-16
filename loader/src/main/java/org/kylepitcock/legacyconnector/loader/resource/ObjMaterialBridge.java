package org.kylepitcock.legacyconnector.loader.resource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts Forge OBJ material image paths into atlas sprites without moving their resource-pack paths. */
final class ObjMaterialBridge {
    static final String MANIFEST = "META-INF/legacyconnector/obj-atlas.json";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Pattern MODEL = Pattern.compile("assets/([^/]+)/models/(.+)\\.json");
    private static final Set<String> TEXTURE_FOLDERS = Set.of("block/", "blocks/", "item/", "items/");

    private record AtlasTexture(String resource, String sprite) {}
    private record ResourcePath(String namespace, String path) {}
    private record SelectedMap(String material, String texture) {}
    private record MaterialRewrite(String content, boolean usesModelOverrides) {}

    private ObjMaterialBridge() {}

    static void apply(Map<String, byte[]> sourceAssets, Map<String, byte[]> output) {
        Map<String, AtlasTexture> textures = new LinkedHashMap<>();
        Set<String> processedLibraries = new LinkedHashSet<>();

        for (var entry : sourceAssets.entrySet()) {
            Matcher modelPath = MODEL.matcher(entry.getKey());
            if (!modelPath.matches()) continue;
            String namespace = normalizeNamespace(modelPath.group(1));
            JsonObject model = object(entry.getValue());
            if (model == null || !model.has("loader") || !model.get("loader").isJsonPrimitive()
                    || !"forge:obj".equalsIgnoreCase(model.get("loader").getAsString())) continue;
            if (!model.has("model") || !model.get("model").isJsonPrimitive()) continue;

            String objAsset = findObj(sourceAssets, namespace, model.get("model").getAsString());
            if (objAsset == null) continue;
            byte[] obj = sourceAssets.get(objAsset);
            JsonObject textureOverrides = model.has("textures") && model.get("textures").isJsonObject() ? model.getAsJsonObject("textures") : null;
            for (String library : materialLibraries(obj)) {
                String mtlAsset = findLibrary(sourceAssets, namespace, objAsset, library);
                if (mtlAsset == null) continue;
                byte[] originalMtl = sourceAssets.get(mtlAsset);
                if (processedLibraries.add(mtlAsset)) {
                    MaterialRewrite base = rewriteMaterial(originalMtl, mtlAsset, textures, sourceAssets, null);
                    if (base != null) output.put(mtlAsset, base.content().getBytes(StandardCharsets.UTF_8));
                }
                if (textureOverrides == null || textureOverrides.isEmpty()) continue;
                MaterialRewrite variant = rewriteMaterial(originalMtl, mtlAsset, textures, sourceAssets, textureOverrides);
                if (variant == null || !variant.usesModelOverrides()) continue;
                String overridePath = modelMtlPath(entry.getKey());
                output.put(overridePath, variant.content().getBytes(StandardCharsets.UTF_8));
                JsonObject convertedModel = object(output.get(entry.getKey()));
                if (convertedModel != null) {
                    convertedModel.addProperty("mtl_override", namespaceWithinAssets(overridePath) + ":" + pathWithinAssets(overridePath));
                    output.put(entry.getKey(), GSON.toJson(convertedModel).getBytes(StandardCharsets.UTF_8));
                }
            }
        }

        if (!textures.isEmpty()) {
            JsonObject manifest = new JsonObject();
            JsonArray entries = new JsonArray();
            for (AtlasTexture texture : textures.values()) {
                JsonObject item = new JsonObject();
                item.addProperty("resource", texture.resource());
                item.addProperty("sprite", texture.sprite());
                entries.add(item);
            }
            manifest.add("textures", entries);
            output.put(MANIFEST, GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static JsonObject object(byte[] data) {
        try {
            JsonElement parsed = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String findObj(Map<String, byte[]> assets, String defaultNamespace, String ref) {
        ResourcePath parsed = resourcePath(ref, defaultNamespace);
        if (parsed == null) return null;
        String path = normalizePath(parsed.path());
        String exact = assetPath(parsed.namespace(), path);
        if (assets.containsKey(exact)) return exact;
        if (!path.startsWith("models/")) {
            String underModels = assetPath(parsed.namespace(), "models/" + path);
            if (assets.containsKey(underModels)) return underModels;
        }
        return null;
    }

    private static List<String> materialLibraries(byte[] obj) {
        List<String> out = new ArrayList<>();
        for (String line : new String(obj, StandardCharsets.UTF_8).split("\\R")) {
            String trimmed = line.trim();
            int split = firstWhitespace(trimmed);
            if (split < 0 || !trimmed.substring(0, split).equalsIgnoreCase("mtllib")) continue;
            String value = trimmed.substring(split).trim();
            if (!value.isEmpty()) out.add(value);
        }
        return out;
    }

    private static String findLibrary(Map<String, byte[]> assets, String defaultNamespace, String objAsset, String ref) {
        ResourcePath parsed = resourcePath(ref, defaultNamespace);
        if (parsed == null) return null;
        String path = normalizePath(parsed.path());
        List<String> candidates = new ArrayList<>();
        if (ref.indexOf(':') >= 0) {
            candidates.add(assetPath(parsed.namespace(), path));
        } else if (path.indexOf('/') >= 0) {
            candidates.add(assetPath(parsed.namespace(), path));
        } else {
            String objPath = pathWithinAssets(objAsset);
            int slash = objPath.lastIndexOf('/');
            candidates.add(assetPath(parsed.namespace(), (slash < 0 ? "" : objPath.substring(0, slash + 1)) + path));
        }
        for (String candidate : candidates) if (assets.containsKey(candidate)) return candidate;
        return null;
    }

    private static MaterialRewrite rewriteMaterial(byte[] data, String mtlAsset, Map<String, AtlasTexture> textures,
            Map<String, byte[]> sourceAssets, JsonObject textureOverrides) {
        String[] lines = new String(data, StandardCharsets.UTF_8).split("\\R", -1);
        Map<Integer, SelectedMap> selectedMaps = new LinkedHashMap<>();
        Map<String, Integer> selectedIndex = new LinkedHashMap<>();
        String material = "default";
        int materialNumber = 0;
        boolean hasTexture = false;

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int split = firstWhitespace(trimmed);
            if (split < 0) continue;
            String command = trimmed.substring(0, split);
            String value = trimmed.substring(split).trim();
            if (command.equalsIgnoreCase("newmtl")) {
                material = lastToken(value);
                if (material.isBlank()) material = "material-" + (++materialNumber);
                hasTexture = false;
            } else if (command.equalsIgnoreCase("map_Kd")) {
                selectMap(selectedMaps, selectedIndex, material, i, new SelectedMap(material, lastToken(value)));
                hasTexture = true;
            } else if ((command.equalsIgnoreCase("map_Ka") || command.equalsIgnoreCase("map_Ks")) && !hasTexture) {
                selectMap(selectedMaps, selectedIndex, material, i, new SelectedMap(material, lastToken(value)));
                hasTexture = true;
            }
        }

        boolean changed = false;
        boolean usesModelOverrides = false;
        for (var selected : selectedMaps.entrySet()) {
            SelectedMap map = selected.getValue();
            String overrideKey = textureOverrides == null ? null : matchingOverride(textureOverrides, map.material(), map.texture());
            String sprite;
            if (overrideKey != null) {
                sprite = "#" + overrideKey;
                usesModelOverrides = true;
            } else {
                ResourcePath resource = resolveTexture(map.texture(), mtlAsset, sourceAssets);
                if (resource == null) continue;
                AtlasTexture texture = textures.computeIfAbsent(resource.namespace() + ":" + resource.path(), ignored -> atlasTexture(resource));
                sprite = texture.sprite();
            }
            int lineIndex = selected.getKey();
            String line = lines[lineIndex];
            int indent = 0;
            while (indent < line.length() && Character.isWhitespace(line.charAt(indent))) indent++;
            lines[lineIndex] = line.substring(0, indent) + "map_Kd " + sprite;
            changed = true;
        }
        if (!changed) return null;
        return new MaterialRewrite(String.join("\n", lines), usesModelOverrides);
    }

    private static String matchingOverride(JsonObject overrides, String material, String texture) {
        String textureKey = texture;
        int colon = textureKey.indexOf(':');
        if (colon >= 0) textureKey = textureKey.substring(colon + 1);
        if (textureKey.endsWith(".png")) textureKey = textureKey.substring(0, textureKey.length() - 4);
        for (var entry : overrides.entrySet()) {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) continue;
            String key = entry.getKey().startsWith("#") ? entry.getKey().substring(1) : entry.getKey();
            if (key.equals(material) || key.equals(textureKey)) return key;
        }
        return null;
    }

    private static String modelMtlPath(String modelAsset) {
        String namespace = namespaceWithinAssets(modelAsset);
        String key = modelAsset.substring("assets/".length());
        byte[] digest;
        try {
            digest = java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
        String id = java.util.HexFormat.of().formatHex(digest, 0, 12);
        return "assets/" + namespace + "/models/legacyconnector_obj/" + id + ".mtl";
    }

    private static void selectMap(Map<Integer, SelectedMap> selectedMaps, Map<String, Integer> selectedIndex, String material, int line, SelectedMap ref) {
        Integer previous = selectedIndex.put(material, line);
        if (previous != null) selectedMaps.remove(previous);
        selectedMaps.put(line, ref);
    }

    private static String lastToken(String value) {
        String[] tokens = value.trim().split("\\s+");
        if (tokens.length == 0) return "";
        String last = tokens[tokens.length - 1];
        if (last.length() >= 2 && last.startsWith("\"") && last.endsWith("\"")) return last.substring(1, last.length() - 1);
        return last;
    }

    private static ResourcePath resolveTexture(String ref, String mtlAsset, Map<String, byte[]> sourceAssets) {
        if (ref == null || ref.isBlank() || ref.startsWith("#")) return null;
        String normalized = ref.replace('\\', '/');
        int embedded = normalized.toLowerCase(java.util.Locale.ROOT).lastIndexOf("/assets/");
        if (embedded >= 0) normalized = normalized.substring(embedded + 1);
        if (normalized.startsWith("assets/")) {
            String rest = normalized.substring("assets/".length());
            int slash = rest.indexOf('/');
            if (slash <= 0) return null;
            ResourcePath absolute = new ResourcePath(normalizeNamespace(rest.substring(0, slash)), normalizePath(rest.substring(slash + 1)));
            return modernVanillaTexture(withExtension(absolute));
        }

        ResourcePath explicit = null;
        int colon = normalized.indexOf(':');
        if (colon > 0 && !normalized.matches("^[a-zA-Z]:/.*")) {
            explicit = new ResourcePath(normalizeNamespace(normalized.substring(0, colon)), normalizePath(normalized.substring(colon + 1)));
            normalized = normalized.substring(colon + 1);
        }

        String defaultNamespace = namespaceWithinAssets(mtlAsset);
        String path = normalizePath(normalized);
        if (path.startsWith("/")) path = path.substring(1);
        List<ResourcePath> candidates = new ArrayList<>();
        if (explicit != null) {
            if (path.startsWith("textures/") || path.startsWith("models/")) candidates.add(new ResourcePath(explicit.namespace(), path));
            else {
                candidates.add(new ResourcePath(explicit.namespace(), "textures/" + path));
                candidates.add(new ResourcePath(explicit.namespace(), relativeToMtl(mtlAsset, path)));
            }
        } else if (path.startsWith("textures/") || path.startsWith("models/")) {
            candidates.add(new ResourcePath(defaultNamespace, path));
        } else if (TEXTURE_FOLDERS.stream().anyMatch(path::startsWith)) {
            candidates.add(new ResourcePath(defaultNamespace, "textures/" + path));
        } else {
            candidates.add(new ResourcePath(defaultNamespace, relativeToMtl(mtlAsset, path)));
            candidates.add(new ResourcePath(defaultNamespace, "textures/" + path));
        }

        List<ResourcePath> withExtensions = candidates.stream().map(ObjMaterialBridge::withExtension)
                .map(ObjMaterialBridge::modernVanillaTexture).distinct().toList();
        for (ResourcePath candidate : withExtensions) {
            if (sourceAssets.containsKey(assetPath(candidate.namespace(), candidate.path()))) return candidate;
        }
        return withExtensions.isEmpty() ? null : withExtensions.get(0);
    }

    private static ResourcePath modernVanillaTexture(ResourcePath path) {
        if (!path.namespace().equals("minecraft") || !path.path().startsWith("textures/")) return path;
        String texture = path.path().substring("textures/".length());
        if (!texture.startsWith("blocks/") && !texture.startsWith("items/")) return path;
        String extension = texture.endsWith(".png") ? ".png" : "";
        String basename = extension.isEmpty() ? texture : texture.substring(0, texture.length() - extension.length());
        return new ResourcePath("minecraft", "textures/" + VanillaTextureRenames.modern(basename) + extension);
    }

    private static String relativeToMtl(String mtlAsset, String path) {
        String mtlPath = pathWithinAssets(mtlAsset);
        int slash = mtlPath.lastIndexOf('/');
        return normalizePath((slash < 0 ? "" : mtlPath.substring(0, slash + 1)) + path);
    }

    private static ResourcePath withExtension(ResourcePath path) {
        String value = path.path();
        int slash = value.lastIndexOf('/');
        int dot = value.lastIndexOf('.');
        if (dot <= slash) value += ".png";
        return new ResourcePath(path.namespace(), normalizePath(value));
    }

    private static AtlasTexture atlasTexture(ResourcePath resource) {
        String path = resource.path();
        if (path.startsWith("textures/")) {
            String sprite = path.substring("textures/".length());
            if (sprite.endsWith(".png")) sprite = sprite.substring(0, sprite.length() - 4);
            return new AtlasTexture(resource.namespace() + ":" + path, resource.namespace() + ":" + sprite);
        }
        String key = resource.namespace() + ":" + path;
        byte[] digest;
        try {
            digest = java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
        String id = java.util.HexFormat.of().formatHex(digest, 0, 12);
        return new AtlasTexture(key, resource.namespace() + ":legacyconnector_obj/" + id);
    }

    private static ResourcePath resourcePath(String ref, String defaultNamespace) {
        if (ref == null) return null;
        String value = ref.replace('\\', '/');
        int colon = value.indexOf(':');
        if (colon > 0 && !value.matches("^[a-zA-Z]:/.*")) {
            return new ResourcePath(normalizeNamespace(value.substring(0, colon)), normalizePath(value.substring(colon + 1)));
        }
        return new ResourcePath(defaultNamespace, normalizePath(value));
    }

    private static String assetPath(String namespace, String path) {
        return "assets/" + normalizeNamespace(namespace) + "/" + normalizePath(path);
    }

    private static String namespaceWithinAssets(String asset) {
        String rest = asset.substring("assets/".length());
        int slash = rest.indexOf('/');
        return normalizeNamespace(slash < 0 ? rest : rest.substring(0, slash));
    }

    private static String pathWithinAssets(String asset) {
        String rest = asset.substring("assets/".length());
        int slash = rest.indexOf('/');
        return slash < 0 ? "" : rest.substring(slash + 1);
    }

    private static String normalizePath(String path) {
        String replaced = path.replace('\\', '/');
        List<String> parts = new ArrayList<>();
        for (String part : replaced.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (!parts.isEmpty()) parts.remove(parts.size() - 1);
                continue;
            }
            parts.add(part);
        }
        return LegacyResourceConverter.normalizePath(String.join("/", parts));
    }

    private static String normalizeNamespace(String namespace) {
        StringBuilder normalized = new StringBuilder(namespace.length());
        for (char c : namespace.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            normalized.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' ? c : '_');
        }
        if (normalized.isEmpty() || normalized.charAt(0) < 'a' || normalized.charAt(0) > 'z') normalized.insert(0, "mod_");
        if (normalized.length() > 64) normalized.setLength(64);
        return normalized.toString();
    }

    private static int firstWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) if (Character.isWhitespace(text.charAt(i))) return i;
        return -1;
    }
}
