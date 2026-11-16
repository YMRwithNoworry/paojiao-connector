package org.kylepitcock.legacyconnector.loader.resource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LegacyShaderConverterTest {
    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void convertsSupportedFragmentAndVertexShadersDuringResourceConversion() {
        var converter = new LegacyResourceConverter();
        converter.accept("assets/example/shaders/program/cold.fsh", bytes("#version 120\nuniform sampler2D DiffuseSampler;\nvarying vec2 texCoord;\nvoid main() { gl_FragColor = texture2D(DiffuseSampler, texCoord); }\n"));
        converter.accept("assets/example/shaders/program/quad.vsh", bytes("#version 120\nattribute vec3 Position;\nvarying vec2 texCoord;\nvoid main() { texCoord = Position.xy; }\n"));
        Map<String, byte[]> output = converter.finish();

        String fragment = new String(output.get("assets/example/shaders/program/cold.fsh"), StandardCharsets.UTF_8);
        assertTrue(fragment.startsWith("#version 150\nout vec4 fragColor;\n"), fragment);
        assertTrue(fragment.contains("in vec2 texCoord;"), fragment);
        assertTrue(fragment.contains("fragColor = texture(DiffuseSampler, texCoord)"), fragment);

        String vertex = new String(output.get("assets/example/shaders/program/quad.vsh"), StandardCharsets.UTF_8);
        assertTrue(vertex.startsWith("#version 150\n"), vertex);
        assertTrue(vertex.contains("in vec3 Position;"), vertex);
        assertTrue(vertex.contains("out vec2 texCoord;"), vertex);
    }

    @Test
    void leavesUnsupportedFixedFunctionShadersAndOtherResourcesUntouched() {
        for (String builtin : new String[] {"gl_FragData", "gl_TexCoord", "gl_MultiTexCoord0", "gl_FogFragCoord"}) {
            byte[] unsupported = bytes("#version 120\nvoid main() { gl_FragColor = " + builtin + "[0]; }\n");
            assertArrayEquals(unsupported, LegacyShaderConverter.toCoreProfile("assets/example/shaders/program/old.fsh", unsupported));
        }
        byte[] unsupportedSyntax = bytes("#version 120\n#extension GL_ARB_gpu_shader5 : enable\nvoid main() {}\n");
        assertArrayEquals(unsupportedSyntax, LegacyShaderConverter.toCoreProfile("assets/example/shaders/program/old.fsh", unsupportedSyntax));
        byte[] outsideShaderPath = bytes("#version 120\n");
        assertArrayEquals(outsideShaderPath, LegacyShaderConverter.toCoreProfile("assets/example/shaders/old.fsh", outsideShaderPath));

        var converter = new LegacyResourceConverter();
        byte[] unsupported = bytes("#version 120\nvoid main() { gl_FragColor = gl_FragData[0]; }\n");
        converter.accept("assets/example/shaders/program/old.fsh", unsupported);
        assertArrayEquals(unsupported, converter.finish().get("assets/example/shaders/program/old.fsh"));
    }

    @Test
    void duplicateOrCommentedVersionDirectivesAreNotRewritten() {
        byte[] duplicate = bytes("#version 120\n#version 120\nvoid main() {}\n");
        byte[] commented = bytes("#version 120 // compatibility\nvoid main() {}\n");
        assertArrayEquals(duplicate, LegacyShaderConverter.toCoreProfile("assets/example/shaders/program/old.fsh", duplicate));
        assertArrayEquals(commented, LegacyShaderConverter.toCoreProfile("assets/example/shaders/program/old.fsh", commented));
    }
}
