package org.kylepitcock.legacyconnector.loader.resource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ObjMaterialBridgeTest {
    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, byte[]> relayController(boolean reverseEntries) {
        var converter = new LegacyResourceConverter();
        Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
        entries.put("assets/srparasites/models/block/relaycontroller.json", bytes("{\"loader\":\"forge:obj\",\"model\":\"srparasites:models/block/arraytowerlarge.obj\"}"));
        entries.put("assets/srparasites/models/block/arraytowerlarge.obj", bytes("mtllib arraytowerlarge.mtl\nusemtl pasted\n"));
        entries.put("assets/srparasites/models/block/arraytowerlarge.mtl", bytes("newmtl pasted\nmap_Kd pasted.png\n"));
        entries.put("assets/srparasites/models/block/pasted.png", new byte[] {1, 2, 3, 4});
        var ordered = new java.util.ArrayList<>(entries.entrySet());
        if (reverseEntries) java.util.Collections.reverse(ordered);
        for (var entry : ordered) converter.accept(entry.getKey(), entry.getValue());
        return converter.finish();
    }

    @Test
    void relativeMtlImageResolvesRegardlessOfZipEntryOrderAndKeepsItsResourcePath() {
        Map<String, byte[]> forward = relayController(false);
        Map<String, byte[]> reversed = relayController(true);
        String manifestPath = ObjMaterialBridge.MANIFEST;
        var manifest = JsonParser.parseString(new String(forward.get(manifestPath), StandardCharsets.UTF_8)).getAsJsonObject();
        var texture = manifest.getAsJsonArray("textures").get(0).getAsJsonObject();
        String sprite = texture.get("sprite").getAsString();

        assertEquals("srparasites:models/block/pasted.png", texture.get("resource").getAsString());
        assertTrue(sprite.matches("srparasites:legacyconnector_obj/[0-9a-f]{24}"), sprite);
        String convertedMtl = new String(forward.get("assets/srparasites/models/block/arraytowerlarge.mtl"), StandardCharsets.UTF_8);
        assertTrue(convertedMtl.contains("map_Kd " + sprite), convertedMtl);
        assertArrayEquals(forward.get("assets/srparasites/models/block/arraytowerlarge.mtl"),
                reversed.get("assets/srparasites/models/block/arraytowerlarge.mtl"));
        assertArrayEquals(new byte[] {1, 2, 3, 4}, forward.get("assets/srparasites/models/block/pasted.png"));
    }

    @Test
    void explicitLegacyTextureNamespaceUsesTheTextureFolderAndDiffuseMapWins() {
        var converter = new LegacyResourceConverter();
        converter.accept("assets/example/models/block/model.json", bytes("{\"loader\":\"forge:obj\",\"model\":\"example:models/block/model.obj\"}"));
        converter.accept("assets/example/models/block/model.obj", bytes("mtllib model.mtl\n"));
        converter.accept("assets/example/models/block/model.mtl", bytes("newmtl stone\nmap_Ka example:blocks/old\nmap_Kd example:blocks/stone.png\n"));
        converter.accept("assets/example/textures/blocks/old.png", new byte[] {8});
        converter.accept("assets/example/textures/blocks/stone.png", new byte[] {9});
        Map<String, byte[]> output = converter.finish();

        var entries = JsonParser.parseString(new String(output.get(ObjMaterialBridge.MANIFEST), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("textures");
        assertEquals(1, entries.size());
        assertEquals("example:textures/blocks/stone.png", entries.get(0).getAsJsonObject().get("resource").getAsString());
        String mtl = new String(output.get("assets/example/models/block/model.mtl"), StandardCharsets.UTF_8);
        assertTrue(mtl.contains("map_Kd example:blocks/stone"), mtl);
        assertTrue(mtl.contains("map_Ka example:blocks/old"), mtl);
    }

    @Test
    void oldVanillaTextureFoldersAreMappedToCurrentAtlasPaths() {
        var converter = new LegacyResourceConverter();
        converter.accept("assets/example/models/block/model.json", bytes("{\"loader\":\"forge:obj\",\"model\":\"example:models/block/model.obj\"}"));
        converter.accept("assets/example/models/block/model.obj", bytes("mtllib model.mtl\n"));
        converter.accept("assets/example/models/block/model.mtl", bytes("newmtl stone\nmap_Kd minecraft:blocks/stone\n"));
        Map<String, byte[]> output = converter.finish();
        var textures = JsonParser.parseString(new String(output.get(ObjMaterialBridge.MANIFEST), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("textures");
        assertEquals("minecraft:textures/block/stone.png", textures.get(0).getAsJsonObject().get("resource").getAsString());
        assertEquals("minecraft:block/stone", textures.get(0).getAsJsonObject().get("sprite").getAsString());
    }

    @Test
    void modelTextureOverridesArePreservedInPerModelMaterialLibraries() {
        var converter = new LegacyResourceConverter();
        converter.accept("assets/example/models/block/red.json", bytes("{\"loader\":\"forge:obj\",\"model\":\"example:models/block/common.obj\",\"textures\":{\"stone\":\"example:blocks/red\"}}"));
        converter.accept("assets/example/models/block/blue.json", bytes("{\"loader\":\"forge:obj\",\"model\":\"example:models/block/common.obj\",\"textures\":{\"blocks/stone\":\"example:blocks/blue\"}}"));
        converter.accept("assets/example/models/block/common.obj", bytes("mtllib common.mtl\nusemtl stone\n"));
        converter.accept("assets/example/models/block/common.mtl", bytes("newmtl stone\nmap_Kd example:blocks/stone\n"));
        converter.accept("assets/example/textures/blocks/stone.png", new byte[] {1});
        Map<String, byte[]> output = converter.finish();

        var red = JsonParser.parseString(new String(output.get("assets/example/models/block/red.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        var blue = JsonParser.parseString(new String(output.get("assets/example/models/block/blue.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        String redMtlPath = "assets/" + red.get("mtl_override").getAsString().replace(':', '/');
        String blueMtlPath = "assets/" + blue.get("mtl_override").getAsString().replace(':', '/');
        assertFalse(redMtlPath.equals(blueMtlPath));
        assertTrue(new String(output.get(redMtlPath), StandardCharsets.UTF_8).contains("map_Kd #stone"));
        assertTrue(new String(output.get(blueMtlPath), StandardCharsets.UTF_8).contains("map_Kd #blocks/stone"));
        assertTrue(new String(output.get("assets/example/models/block/common.mtl"), StandardCharsets.UTF_8).contains("map_Kd example:blocks/stone"));
    }

    @Test
    void ignoresNonObjModelLibraries() {
        var converter = new LegacyResourceConverter();
        converter.accept("assets/example/models/block/model.json", bytes("{\"parent\":\"minecraft:block/cube_all\"}"));
        converter.accept("assets/example/models/block/model.mtl", bytes("newmtl stone\nmap_Kd stone.png\n"));
        converter.accept("assets/example/models/block/stone.png", new byte[] {7});
        Map<String, byte[]> output = converter.finish();
        assertFalse(output.containsKey(ObjMaterialBridge.MANIFEST));
        assertEquals("newmtl stone\nmap_Kd stone.png\n", new String(output.get("assets/example/models/block/model.mtl"), StandardCharsets.UTF_8));
    }
}
