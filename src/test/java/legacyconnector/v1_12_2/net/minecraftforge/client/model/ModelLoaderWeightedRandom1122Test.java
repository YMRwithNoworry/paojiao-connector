package legacyconnector.v1_12_2.net.minecraftforge.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.google.common.collect.ImmutableMap;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import legacyconnector.v1_12_2.net.minecraft.client.renderer.block.model.IBakedModel;
import legacyconnector.v1_12_2.net.minecraft.client.renderer.block.model.ModelRotation;
import legacyconnector.v1_12_2.net.minecraft.client.renderer.block.model.Variant;
import legacyconnector.v1_12_2.net.minecraft.client.renderer.block.model.WeightedBakedModel;
import legacyconnector.v1_12_2.net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import legacyconnector.v1_12_2.net.minecraft.util.ResourceLocation;
import legacyconnector.v1_12_2.net.minecraftforge.common.model.IModelState;
import legacyconnector.v1_12_2.net.minecraftforge.common.model.TRSRTransformation;
import org.junit.jupiter.api.Test;

class ModelLoaderWeightedRandom1122Test {
    @Test
    void exposesForgeReflectiveConstructorAndRetexturesWeightedModels() throws Exception {
        ResourceLocation lowLocation = new ResourceLocation("test:low");
        ResourceLocation highLocation = new ResourceLocation("test:high");
        ResourceLocation oldTexture = new ResourceLocation("test:old");
        ResourceLocation newTexture = new ResourceLocation("test:new");
        IBakedModel lowBaked = bakedModel();
        IBakedModel highBaked = bakedModel();
        FakeModel lowModel = new FakeModel(oldTexture, lowBaked, newTexture);
        FakeModel highModel = new FakeModel(oldTexture, highBaked, newTexture);
        List<Variant> variants = List.of(
                new Variant(lowLocation, ModelRotation.X0_Y0, false, 1),
                new Variant(highLocation, ModelRotation.X0_Y0, false, 7));
        List<ResourceLocation> locations = List.of(lowLocation, highLocation);
        IModelState state = TRSRTransformation.identity();

        IModel weighted = construct(variants, locations, Set.of(oldTexture), List.of(lowModel, highModel), state);
        assertEquals(locations, weighted.getDependencies());
        assertEquals(Set.of(oldTexture), Set.copyOf(weighted.getTextures()));
        assertSame(state, weighted.getDefaultState());

        IModel retextured = weighted.retexture(ImmutableMap.of("layer0", newTexture.toString()));
        assertNotSame(weighted, retextured);
        assertEquals(Set.of(newTexture), Set.copyOf(retextured.getTextures()));
        assertEquals(locations, retextured.getDependencies());

        IBakedModel baked = retextured.bake(retextured.getDefaultState(), DefaultVertexFormats.ITEM, ignored -> null);
        WeightedBakedModel combined = assertInstanceOf(WeightedBakedModel.class, baked);
        assertSame(highBaked, combined.baseModel);
    }

    private static IModel construct(List<Variant> variants, List<ResourceLocation> locations, Set<ResourceLocation> textures,
            List<IModel> models, IModelState state) throws Exception {
        Class<?> type = Class.forName(ModelLoader.class.getName() + "$WeightedRandomModel");
        Constructor<?> constructor = type.getDeclaredConstructor(List.class, List.class, Set.class, List.class, IModelState.class);
        constructor.setAccessible(true);
        return (IModel) constructor.newInstance(variants, locations, textures, models, state);
    }

    private static IBakedModel bakedModel() {
        return (IBakedModel) Proxy.newProxyInstance(IBakedModel.class.getClassLoader(), new Class<?>[] {IBakedModel.class},
                (proxy, method, args) -> null);
    }

    private static final class FakeModel implements IModel {
        private final ResourceLocation texture;
        private final IBakedModel baked;
        private final ResourceLocation replacement;

        FakeModel(ResourceLocation texture, IBakedModel baked, ResourceLocation replacement) {
            this.texture = texture;
            this.baked = baked;
            this.replacement = replacement;
        }

        @Override
        public java.util.Collection<ResourceLocation> getTextures() {
            return List.of(texture);
        }

        @Override
        public IModel retexture(ImmutableMap<String, String> textures) {
            String path = textures.get("layer0");
            if (path == null) return this;
            return new FakeModel(new ResourceLocation(path), baked, replacement);
        }

        @Override
        public IBakedModel bake(IModelState state, legacyconnector.v1_12_2.net.minecraft.client.renderer.vertex.VertexFormat format,
                java.util.function.Function<ResourceLocation, legacyconnector.v1_12_2.net.minecraft.client.renderer.texture.TextureAtlasSprite> textureGetter) {
            return baked;
        }
    }
}
