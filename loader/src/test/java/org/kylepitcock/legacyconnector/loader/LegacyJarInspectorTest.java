package org.kylepitcock.legacyconnector.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    private static byte[] libraryClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "sample/Library", null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }
}
