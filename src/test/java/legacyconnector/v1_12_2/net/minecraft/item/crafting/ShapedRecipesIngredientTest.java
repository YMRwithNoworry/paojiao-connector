package legacyconnector.v1_12_2.net.minecraft.item.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.google.gson.JsonParser;
import legacyconnector.v1_12_2.net.minecraftforge.oredict.OreIngredient;
import org.junit.jupiter.api.Test;

class ShapedRecipesIngredientTest {
    @Test
    void parsesForgeOreDictionaryIngredientJson() {
        var json = JsonParser.parseString("{\"type\":\"forge:ore_dict\",\"ore\":\"testIngredient\"}");

        Ingredient ingredient = ShapedRecipes.deserializeIngredient(json);

        assertInstanceOf(OreIngredient.class, ingredient);
        assertEquals("testIngredient", ((OreIngredient) ingredient).mcc$ore());
    }
}
