package legacyconnector.v1_20_1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cpw.mods.jarhandling.SecureJar;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import legacyconnector.v1_20_1.net.minecraftforge.resource.DelegatingPackResources;
import legacyconnector.v1_20_1.net.minecraftforge.resource.PathPackResources;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the Forge 1.20.1 PathPackResources facade against the public 1.21.1 secure-jar API. */
class ForgeResourcePackCompat1201Test {
    @Test
    void modFactoryUsesTheSecureJarRootAndLegacyPackMetadata() throws Exception {
        Path root = Files.createTempDirectory("legacyconnector-secure-jar-root-");
        Path resource = root.resolve("assets/symbiote/lang/en_us.json");
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, "{\"hello\":\"world\"}");

        SecureJar secureJar = (SecureJar) Proxy.newProxyInstance(SecureJar.class.getClassLoader(),
                new Class<?>[] {SecureJar.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getRootPath" -> root;
                    default -> defaultValue(proxy, method, args);
                });

        IModInfo mod = (IModInfo) Proxy.newProxyInstance(IModInfo.class.getClassLoader(),
                new Class<?>[] {IModInfo.class},
                (proxy, method, args) -> method.getName().equals("getModId")
                        ? "symbiote" : defaultValue(proxy, method, args));

        IModFile file = (IModFile) Proxy.newProxyInstance(IModFile.class.getClassLoader(),
                new Class<?>[] {IModFile.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getSecureJar" -> secureJar;
                    case "getFileName" -> "symbiote-1.1.3.jar";
                    default -> defaultValue(proxy, method, args);
                });

        IModFileInfo info = (IModFileInfo) Proxy.newProxyInstance(IModFileInfo.class.getClassLoader(),
                new Class<?>[] {IModFileInfo.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getFile" -> file;
                    case "getMods" -> List.of(mod);
                    default -> defaultValue(proxy, method, args);
                });

        PathPackResources pack = ForgeApi1201.createPackForMod(info);
        try {
            assertEquals("symbiote", pack.packId());
            assertEquals(root, pack.getSource());
            assertTrue(pack.isBuiltin());
            assertEquals(root.resolve("assets").resolve("symbiote").resolve("lang").resolve("en_us.json"),
                    invokeLegacyResolve(pack, "assets", "symbiote", "lang", "en_us.json"));
            assertEquals("{\"hello\":\"world\"}",
                    Files.readString(pack.getSource().resolve("assets/symbiote/lang/en_us.json")));
        } finally {
            pack.close();
            deleteTree(root);
        }
    }

    @Test
    void delegatingPackFindsTheFirstResourceAndKeepsAssetAndDataNamespacesSeparate(@TempDir Path root) throws Exception {
        Path firstRoot = Files.createDirectories(root.resolve("first/assets/sample/texts"));
        Files.writeString(firstRoot.resolve("shared.txt"), "first");
        Path secondRoot = Files.createDirectories(root.resolve("second/assets/sample/texts"));
        Files.writeString(secondRoot.resolve("shared.txt"), "second");
        Files.writeString(secondRoot.resolve("fallback.txt"), "fallback");
        Files.createDirectories(root.resolve("second/data/other"));
        var metadata = new PackMetadataSection(Component.literal("resources"), 34);
        var first = new PathPackResources("first", root.resolve("first"));
        var second = new PathPackResources("second", root.resolve("second"));
        try (var pack = new DelegatingPackResources("aggregate", true, metadata, List.of(first, second))) {
            assertEquals("first", readAsset(pack, "texts/shared.txt"));
            assertEquals("fallback", readAsset(pack, "texts/fallback.txt"));
            assertEquals(Set.of("sample"), pack.getNamespaces(PackType.CLIENT_RESOURCES));
            assertEquals(Set.of("other"), pack.getNamespaces(PackType.SERVER_DATA));
            assertEquals(metadata, pack.getMetadataSection(PackMetadataSection.TYPE));
        }
    }

    private static String readAsset(PackResources pack, String path) throws Exception {
        try (var stream = pack.getResource(PackType.CLIENT_RESOURCES,
                ResourceLocation.fromNamespaceAndPath("sample", path)).get()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Path invokeLegacyResolve(PathPackResources pack, String... paths) throws Exception {
        Method resolve = PathPackResources.class.getDeclaredMethod("resolve", String[].class);
        resolve.setAccessible(true);
        return (Path) resolve.invoke(pack, (Object) paths);
    }

    private static Object defaultValue(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> method.getDeclaringClass().getSimpleName() + " test proxy";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> {
                Class<?> type = method.getReturnType();
                if (!type.isPrimitive()) yield null;
                if (type == boolean.class) yield false;
                if (type == char.class) yield '\0';
                if (type == byte.class) yield (byte) 0;
                if (type == short.class) yield (short) 0;
                if (type == int.class) yield 0;
                if (type == long.class) yield 0L;
                if (type == float.class) yield 0.0F;
                if (type == double.class) yield 0.0D;
                yield null;
            }
        };
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
