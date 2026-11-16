package org.kylepitcock.legacyconnector.v1_12_2.client;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Adds an arbitrary resource-manager image path to the block atlas under a stable sprite id. */
public final class LegacyObjSpriteSource implements SpriteSource {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("legacyconnector", "legacy_obj_texture");
    public static final MapCodec<LegacyObjSpriteSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("resource").forGetter(source -> source.resource),
            ResourceLocation.CODEC.fieldOf("sprite").forGetter(source -> source.sprite))
            .apply(instance, LegacyObjSpriteSource::new));
    public static final SpriteSourceType TYPE = new SpriteSourceType(CODEC);

    private static final Logger LOG = LoggerFactory.getLogger("LegacyConnector/OBJ");

    private final ResourceLocation resource;
    private final ResourceLocation sprite;

    public LegacyObjSpriteSource(ResourceLocation resource, ResourceLocation sprite) {
        this.resource = resource;
        this.sprite = sprite;
    }

    @Override
    public void run(ResourceManager resourceManager, SpriteSource.Output output) {
        Optional<Resource> found = resourceManager.getResource(resource);
        if (found.isPresent()) {
            output.add(sprite, found.get());
        } else {
            LOG.warn("Missing legacy OBJ material image {} (atlas sprite {})", resource, sprite);
        }
    }

    @Override
    public SpriteSourceType type() {
        return TYPE;
    }
}
