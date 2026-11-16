package org.kylepitcock.legacyconnector.loader.resource;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Converts the small GLSL 1.20 syntax subset accepted by the modern core-profile shader pipeline. */
public final class LegacyShaderConverter {
    private static final Pattern VERSION_120 = Pattern.compile("(?m)^[\\t ]*#version[\\t ]+120[\\t ]*$");
    private static final Pattern UNSUPPORTED = Pattern.compile("#\\s*extension|\\b(?:texture1D|texture3D|textureCube|texture2DProj|texture2DLod|texture2DGrad)\\b");
    private static final Pattern BUILTIN = Pattern.compile("\\bgl_[A-Za-z0-9_]+\\b");
    private static final Pattern FRAGMENT_OUTPUT = Pattern.compile("\\bgl_FragColor\\b");
    private static final Pattern ATTRIBUTE = Pattern.compile("\\battribute\\b");
    private static final Pattern FRAG_COLOR_NAME = Pattern.compile("\\bfragColor\\b");

    private LegacyShaderConverter() {}

    private static boolean usesOnlySupportedBuiltins(String source, String stage) {
        var matcher = BUILTIN.matcher(source);
        while (matcher.find()) {
            String name = matcher.group();
            boolean supported = stage.equals("vertex")
                    ? name.equals("gl_Position") || name.equals("gl_PointSize")
                    : name.equals("gl_FragColor") || name.equals("gl_FragCoord") || name.equals("gl_FrontFacing") || name.equals("gl_PointCoord");
            if (!supported) return false;
        }
        return true;
    }

    /** Returns unchanged source when it is outside the supported core-profile subset. */
    public static byte[] toCoreProfile(String path, byte[] sourceBytes) {
        String stage;
        if (path.matches("assets/[^/]+/shaders/program/[^/]+\\.vsh")) stage = "vertex";
        else if (path.matches("assets/[^/]+/shaders/program/[^/]+\\.fsh")) stage = "fragment";
        else return sourceBytes;

        String source = new String(sourceBytes, StandardCharsets.UTF_8).replace("\r\n", "\n");
        var version = VERSION_120.matcher(source);
        if (!version.find()) return sourceBytes;
        int versionStart = version.start();
        int versionEnd = version.end();
        if (version.find() || UNSUPPORTED.matcher(source).find()) return sourceBytes;
        boolean writesFragmentColor = FRAGMENT_OUTPUT.matcher(source).find();
        if (!usesOnlySupportedBuiltins(source, stage)) return sourceBytes;
        if (stage.equals("fragment") && ATTRIBUTE.matcher(source).find()) return sourceBytes;
        if (stage.equals("fragment") && writesFragmentColor && FRAG_COLOR_NAME.matcher(source).find()) return sourceBytes;

        String converted = source.substring(0, versionStart) + "#version 150" + source.substring(versionEnd);
        converted = converted.replaceAll("\\btexture2D\\b", "texture");
        converted = converted.replaceAll("\\bvarying\\b", stage.equals("vertex") ? "out" : "in");
        if (stage.equals("vertex")) converted = converted.replaceAll("\\battribute\\b", "in");
        if (stage.equals("fragment") && writesFragmentColor) {
            converted = converted.replaceAll("\\bgl_FragColor\\b", "fragColor");
            int lineEnd = converted.indexOf('\n', versionStart);
            int insertAt = lineEnd < 0 ? converted.length() : lineEnd + 1;
            converted = converted.substring(0, insertAt) + "out vec4 fragColor;\n" + converted.substring(insertAt);
        }
        return converted.getBytes(StandardCharsets.UTF_8);
    }
}
