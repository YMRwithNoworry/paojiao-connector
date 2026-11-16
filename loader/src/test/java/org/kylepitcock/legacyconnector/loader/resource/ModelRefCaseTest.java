package org.kylepitcock.legacyconnector.loader.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelRefCaseTest {
    private static JsonObject fix(String json) {
        return JsonParser.parseString(new String(LegacyResourceConverter.lowercaseResourceRefs(json.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void rootParentAndTexturesAreLowerCased() {
        JsonObject o = fix("{\"parent\":\"MyMod:Block/Cube\",\"textures\":{\"all\":\"MyMod:Blocks/Stone\"}}");
        assertEquals("mymod:block/cube", o.get("parent").getAsString());
        assertEquals("mymod:blocks/stone", o.getAsJsonObject("textures").get("all").getAsString());
    }

    @Test
    void legacyVanillaModelParentsMapToCurrentTemplates() {
        JsonObject pane = fix("{\"parent\":\"block/glass_pane_post_ends\"}");
        JsonObject wall = fix("{\"parent\":\"block/wall_side_tall\"}");

        assertEquals("minecraft:block/template_glass_pane_post", pane.get("parent").getAsString());
        assertEquals("minecraft:block/template_wall_side_tall", wall.get("parent").getAsString());
    }

    @Test
    void legacySnowedGrassModelMapsToCurrentVanillaBlockModel() {
        JsonObject state = fix("{\"variants\":{\"snowy=true\":{\"model\":\"minecraft:grass_snowed\"}}}");

        assertEquals("minecraft:block/grass_block_snow", state.getAsJsonObject("variants")
                .getAsJsonObject("snowy=true").get("model").getAsString());
    }

    @Test
    void nonResourceLocationLoaderParentIsRetainedForRuntimeLookup() {
        JsonObject o = fix("{\"parent\":\"mod:item/internal/x$from_item(mod:x)\"}");

        assertEquals("mod:item/internal/x_from_item_mod_x_", o.get("parent").getAsString());
        assertEquals("mod:item/internal/x$from_item(mod:x)",
                o.get(LegacyResourceConverter.LEGACY_PARENT).getAsString());
    }

    @Test
    void boneParentsOfNonMinecraftModelFormatsKeepTheirCase() {
        // HuajiAge's stand models (Bedrock geometry) live under models/ and name a bone as "parent"
        JsonObject o = fix("{\"geometry.model\":{\"bones\":[{\"name\":\"bikeHead\"},{\"name\":\"hb1\",\"parent\":\"bikeHead\"}]}}");
        var bones = o.getAsJsonObject("geometry.model").getAsJsonArray("bones");
        assertEquals("bikeHead", bones.get(1).getAsJsonObject().get("parent").getAsString());
    }
}
