package org.kylepitcock.legacyconnector.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class LegacyJarInspectorTest {
    @Test
    void recognizesFmlLibraryJarsFromTheirVersionFolder(@TempDir Path dir) throws IOException {
        Path jarPath = dir.resolve("mods/1.12.2/geckolib-forge.jar");
        writeLibraryJar(jarPath);

        LegacyJar jar = LegacyJarInspector.inspect(jarPath);

        assertNotNull(jar);
        assertEquals(LegacyVersion.V1_12_2, jar.version);
        assertEquals("LIBRARY", jar.fmlModType);
        assertFalse(jar.hasMods());
    }

    @Test
    void doesNotInferLibraryVersionOutsideAVersionFolder(@TempDir Path dir) throws IOException {
        Path jarPath = dir.resolve("mods/geckolib-forge.jar");
        writeLibraryJar(jarPath);

        assertNull(LegacyJarInspector.inspect(jarPath));
    }

    @Test
    void recognizesGeckoLib3LibraryFromItsArtifactNameInModsRoot(@TempDir Path dir) throws IOException {
        Path jarPath = dir.resolve("mods/geckolib-forge-1.12.2-3.0.31.jar");
        writeGeckoLib3LibraryJar(jarPath);

        LegacyJar jar = LegacyJarInspector.inspect(jarPath);

        assertNotNull(jar);
        assertEquals(LegacyVersion.V1_12_2, jar.version);
        assertEquals("LIBRARY", jar.fmlModType);
        assertFalse(jar.hasMods());
    }

    @Test
    void recognizesForge1122BytecodeWhenMcreatorMetadataStillHasAForge1144Template(@TempDir Path dir) throws IOException {
        Path jarPath = dir.resolve("nocubes-srp-addon.jar");
        writeConflictingMcreatorJar(jarPath, "nocubessrparmory", true);

        LegacyJar jar = LegacyJarInspector.inspect(jarPath);

        assertNotNull(jar);
        assertEquals(LegacyVersion.V1_12_2, jar.version);
        assertEquals("nocubessrparmory", jar.mods.get(0).legacyId);
        assertTrue(jar.notes.stream().anyMatch(note -> note.contains("FMLPreInitializationEvent")));
    }

    @Test
    void doesNotOverrideForge1144MetadataWithoutTheLegacyPreInitializationApi(@TempDir Path dir) throws IOException {
        Path jarPath = dir.resolve("modern-mcreator-addon.jar");
        writeConflictingMcreatorJar(jarPath, "nocubessrparmory", false);

        assertNull(LegacyJarInspector.inspect(jarPath));
    }

    private static void writeLibraryJar(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("FMLModType", "LIBRARY");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            manifest.write(zip);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("mcmod.info"));
            zip.write("[{\"modid\":\"geckolib3\",\"mcversion\":\"${mcversion}\"}]".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("META-INF/mods.toml"));
            zip.write(("modLoader=\"javafml\"\nloaderVersion=\"[31,)\"\n[[mods]]\nmodId=\"geckolib3\"\nversion=\"${file.jarVersion}\"\n"
                    + "[[dependencies.geckolib3]]\nmodId=\"minecraft\"\nversionRange=\"[1.15,1.16)\"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("sample/Library.class"));
            zip.write(libraryClass());
            zip.closeEntry();
        }
    }

    private static void writeGeckoLib3LibraryJar(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("FMLModType", "LIBRARY");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            manifest.write(zip);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("mcmod.info"));
            zip.write("[{\"modid\":\"geckolib3\",\"mcversion\":\"${mcversion}\"}]"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("META-INF/mods.toml"));
            zip.write(("modLoader=\"javafml\"\nloaderVersion=\"[31,)\"\n[[mods]]\nmodId=\"geckolib3\"\n"
                    + "version=\"${file.jarVersion}\"\n[[dependencies.geckolib3]]\nmodId=\"minecraft\"\n"
                    + "versionRange=\"[1.15,1.16)\"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("software/bernie/geckolib3/GeckoLib.class"));
            zip.write(libraryClass("software/bernie/geckolib3/GeckoLib"));
            zip.closeEntry();
        }
    }

    private static void writeConflictingMcreatorJar(Path path, String modId, boolean legacyPreInitialization) throws IOException {
        Files.createDirectories(path.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("META-INF/mods.toml"));
            zip.write(("modLoader=\"javafml\"\nloaderVersion=\"[28,)\"\n"
                    + "[[mods]]\nmodId=\"" + modId + "\"\nversion=\"1.0\"\n"
                    + "[[dependencies." + modId + "]]\nmodId=\"minecraft\"\nmandatory=true\nversionRange=\"[1.14.4]\"\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("mcmod.info"));
            zip.write(("[{\"modid\":\"" + modId + "\",\"name\":\"Fixture\",\"version\":\"1.0\",\"mcversion\":\"1.12.2\"}]")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            String className = "net/mcreator/" + modId + "/ExampleMod";
            zip.putNextEntry(new ZipEntry(className + ".class"));
            zip.write(mcreatorModClass(className, modId, legacyPreInitialization));
            zip.closeEntry();
        }
    }

    private static byte[] mcreatorModClass(String className, String modId, boolean legacyPreInitialization) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);
        var mod = writer.visitAnnotation(LegacyJarInspector.MOD_1_12, true);
        mod.visit("modid", modId);
        mod.visit("name", "Fixture");
        mod.visit("version", "1.0");
        mod.visitEnd();
        if (legacyPreInitialization) {
            var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                    "(Lnet/minecraftforge/fml/common/event/FMLPreInitializationEvent;)V", null, null);
            method.visitCode();
            method.visitInsn(Opcodes.RETURN);
            method.visitMaxs(0, 2);
            method.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] libraryClass() {
        return libraryClass("sample/Library");
    }

    private static byte[] libraryClass(String className) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }
}
