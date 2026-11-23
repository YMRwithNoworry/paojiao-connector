package org.kylepitcock.legacyconnector.v1_20_1.migrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.server.Bootstrap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RecipeCompatTest {
    @BeforeAll
    static void bootstrap() throws ReflectiveOperationException {
        var dist = FMLLoader.class.getDeclaredField("dist");
        dist.setAccessible(true);
        dist.set(null, Dist.CLIENT);
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void legacyRecipeFilteringPreservesHolderAndId() {
        var manager = new RecipeManager(RegistryAccess.EMPTY);
        var first = new RecipeHolder<>(ResourceLocation.parse("test:first"), recipe());
        var second = new RecipeHolder<>(ResourceLocation.parse("test:second"), recipe());
        manager.replaceRecipes(List.of(first, second));

        assertEquals(List.of(first.value(), second.value()), RecipeCompat.getAllRecipesFor(manager, RecipeType.CRAFTING));
        assertSame(second.value(), RecipeCompat.byKey(manager, second.id()).orElseThrow());
        RecipeCompat.replaceRecipes(manager, RecipeCompat.getRecipes(manager).stream().filter(r -> r != first.value()).toList());

        assertTrue(manager.byKey(first.id()).isEmpty());
        assertSame(second, manager.byKey(second.id()).orElseThrow());
    }

    private static ShapelessRecipe recipe() {
        return new ShapelessRecipe("", CraftingBookCategory.MISC, ItemStack.EMPTY, NonNullList.of(Ingredient.EMPTY));
    }
}
