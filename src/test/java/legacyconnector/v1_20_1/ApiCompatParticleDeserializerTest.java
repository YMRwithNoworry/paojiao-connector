package legacyconnector.v1_20_1;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.mojang.brigadier.StringReader;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kylepitcock.legacyconnector.v1_20_1.migrate.ApiCompat;
import org.kylepitcock.legacyconnector.v1_20_1.migrate.MigrateContext;

class ApiCompatParticleDeserializerTest {
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
    void readsSimpleParticleTypesWithoutOptions() throws Exception {
        var flash = ParticleTypes.FLASH;
        var reader = new StringReader("");

        assertSame(flash, ApiCompat.noDeserializer(flash).fromCommand(flash, reader));
    }

    @Test
    void readsStructuredParticlesFromAPlainFriendlyBuffer() {
        var type = ParticleTypes.DUST;
        var expected = new DustParticleOptions(new Vector3f(0.25f, 0.5f, 0.75f), 1.0f);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        type.streamCodec().encode(new RegistryFriendlyByteBuf(buffer, registries), expected);
        byte[] expectedBytes = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), expectedBytes);
        buffer.readerIndex(0);
        MigrateContext.remember(registries);

        var decoded = ApiCompat.noDeserializer(type).fromNetwork(type, buffer);
        var roundTrip = new FriendlyByteBuf(Unpooled.buffer());
        type.streamCodec().encode(new RegistryFriendlyByteBuf(roundTrip, registries), decoded);
        byte[] actualBytes = new byte[roundTrip.readableBytes()];
        roundTrip.getBytes(roundTrip.readerIndex(), actualBytes);

        assertInstanceOf(DustParticleOptions.class, decoded);
        assertArrayEquals(expectedBytes, actualBytes);
    }
}