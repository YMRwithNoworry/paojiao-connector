package legacyconnector.v1_12_2.net.minecraftforge.fml.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import legacyconnector.v1_12_2.net.minecraft.client.resources.FileResourcePack;
import legacyconnector.v1_12_2.net.minecraft.client.resources.IResourcePack;
import legacyconnector.v1_12_2.net.minecraft.util.ResourceLocation;
import legacyconnector.v1_12_2.net.minecraftforge.fml.common.ModContainer;
import legacyconnector.v1_12_2.net.minecraftforge.fml.common.ModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FMLClientHandlerResourcePackTest {
    @TempDir
    Path tempDir;

    @Test
    void exposesLegacyJarResourcesThroughTheReflectiveForgePackList() throws Exception {
        String model = "{\"format_version\":\"1.12.0\"}";
        Path source = tempDir.resolve("srpcotesia.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(source))) {
            zip.putNextEntry(new ZipEntry("assets/srpcotesia/geo/factory.geo.json"));
            zip.write(model.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        Field field = FMLClientHandler.class.getDeclaredField("resourcePackList");
        assertEquals(List.class, field.getType());

        List<IResourcePack> packs = FMLClientHandler.mcc$resourcePacks(List.of(new TestModContainer(source.toFile())));
        assertEquals(1, packs.size());
        FileResourcePack pack = assertInstanceOf(FileResourcePack.class, packs.getFirst());
        try (pack) {
            Field zipField = FileResourcePack.class.getDeclaredField("resourcePackZipFile");
            zipField.setAccessible(true);
            assertNotNull(zipField.get(pack), "Forge exposes zip packs with an open archive for reflective enumerators");
            assertTrue(pack.getResourceDomains().contains("srpcotesia"));
            ResourceLocation geoModel = new ResourceLocation("srpcotesia", "geo/factory.geo.json");
            assertTrue(pack.resourceExists(geoModel));
            try (var input = pack.getInputStream(geoModel)) {
                assertEquals(model, new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    private record TestModContainer(File source) implements ModContainer {
        @Override
        public String getModId() {
            return "srpcotesia";
        }

        @Override
        public String getName() {
            return "SRP Cotesia";
        }

        @Override
        public String getVersion() {
            return "test";
        }

        @Override
        public File getSource() {
            return source;
        }

        @Override
        public ModMetadata getMetadata() {
            return null;
        }

        @Override
        public Object getMod() {
            return null;
        }

        @Override
        public String getDisplayVersion() {
            return "test";
        }

        @Override
        public Map<String, String> getCustomModProperties() {
            return Map.of();
        }
    }
}
