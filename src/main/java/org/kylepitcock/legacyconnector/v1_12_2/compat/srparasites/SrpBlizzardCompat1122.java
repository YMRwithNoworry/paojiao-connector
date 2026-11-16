package org.kylepitcock.legacyconnector.v1_12_2.compat.srparasites;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.material.FogType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.kylepitcock.legacyconnector.core.LegacyMods;
import org.kylepitcock.legacyconnector.v1_7_10.client.RenderContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Restores SRP 1.12.2's Blizzard weather hooks on the 1.21.1 renderer. */
public final class SrpBlizzardCompat1122 {
    private static final Logger LOG = LoggerFactory.getLogger("LegacyConnector/SRPBlizzard");
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private static final Set<String> FOG_REPORTED = ConcurrentHashMap.newKeySet();
    private static volatile Calls calls;
    private static volatile boolean activationLogged;
    private static ClassLoader attemptedLoader;

    private SrpBlizzardCompat1122() {}

    private record Calls(Method intensity, Method fogRenderer, Method weatherRenderer) {}
    static record FogPlanes(float near, float far) {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SrpBlizzardCompat1122::onWeatherStage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, SrpBlizzardCompat1122::onFog);
    }

    public static boolean isActive(float partialTick) {
        return shouldRender(intensity(partialTick));
    }

    static boolean shouldRender(float intensity) {
        return intensity > 0.001F;
    }

    public static boolean shouldSkipVanillaWeather(boolean customWeatherRendered, boolean blizzardActive) {
        return customWeatherRendered || blizzardActive;
    }

    public static float[] sunriseColor(net.minecraft.client.renderer.DimensionSpecialEffects effects, float celestialAngle, float partialTick) {
        return isActive(partialTick) ? null : effects.getSunriseColor(celestialAngle, partialTick);
    }

    static FogPlanes fogPlanes(float intensity, boolean inWaterOrLava) {
        if (!shouldRender(intensity) || inWaterOrLava) return null;
        float far = 72.0F - intensity * 56.0F;
        return new FogPlanes(far * 0.08F, far);
    }

    private static Calls calls() {
        Calls found = calls;
        if (found != null) return found;
        var mod = LegacyMods.find("srparasites");
        if (mod == null || mod.mainClass == null) return null;
        ClassLoader loader = mod.mainClass.getClassLoader();
        synchronized (SrpBlizzardCompat1122.class) {
            if (calls != null) return calls;
            if (attemptedLoader == loader) return null;
            attemptedLoader = loader;
            try {
                Class<?> client = Class.forName("com.dhanantry.scapeandrunparasites.client.weather.SRPBlizzardClient", false, loader);
                Class<?> fog = Class.forName("com.dhanantry.scapeandrunparasites.client.weather.SRPBlizzardFogRenderer", false, loader);
                Class<?> weather = Class.forName("com.dhanantry.scapeandrunparasites.client.weather.SRPBlizzardRenderer", false, loader);
                Class<?> legacyMinecraft = legacyconnector.v1_12_2.net.minecraft.client.Minecraft.class;
                calls = new Calls(client.getMethod("getIntensity", float.class),
                        fog.getMethod("render", legacyMinecraft, float.class, float.class),
                        weather.getMethod("render", legacyMinecraft, float.class, float.class));
                return calls;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                report("resolving SRP Blizzard renderers", e);
                return null;
            }
        }
    }

    private static float intensity(float partialTick) {
        Calls methods = calls();
        if (methods == null) return 0.0F;
        try {
            return ((Number) methods.intensity().invoke(null, partialTick)).floatValue();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            report("reading SRP Blizzard intensity", unwrap(e));
            return 0.0F;
        }
    }

    private static void onWeatherStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float intensity = intensity(partialTick);
        if (!shouldRender(intensity)) return;
        if (!activationLogged) {
            activationLogged = true;
            LOG.info("SRP Blizzard renderer active at intensity {}", intensity);
        }
        Calls methods = calls();
        if (methods == null) return;

        var buffers = mc.renderBuffers().bufferSource();
        var pose = new com.mojang.blaze3d.vertex.PoseStack();
        pose.last().pose().set(event.getModelViewMatrix());
        pose.last().normal().set(new org.joml.Matrix3f(event.getModelViewMatrix()));
        RenderContext.beginWorld(pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, RenderContext.KIND_WORLD);
        try {
            var legacyMinecraft = legacyconnector.v1_12_2.net.minecraft.client.Minecraft.getMinecraft();
            invokeRenderer("drawing the SRP Blizzard fog shell", methods.fogRenderer(), legacyMinecraft, partialTick, intensity);
            invokeRenderer("drawing SRP Blizzard snow", methods.weatherRenderer(), legacyMinecraft, partialTick, intensity);
        } finally {
            RenderContext.end();
            buffers.endBatch();
        }
    }

    private static void onFog(ViewportEvent.RenderFog event) {
        FogType type = event.getType();
        boolean inWaterOrLava = type == FogType.WATER || type == FogType.LAVA;
        float sampledIntensity = intensity((float) event.getPartialTick());
        FogPlanes planes = fogPlanes(sampledIntensity, inWaterOrLava);
        if (planes == null) return;
        boolean alreadyCanceled = event.isCanceled();
        float previousNear = event.getNearPlaneDistance();
        float previousFar = event.getFarPlaneDistance();
        event.setNearPlaneDistance(planes.near());
        event.setFarPlaneDistance(planes.far());
        event.setCanceled(true);
        String key = event.getMode() + "/" + type;
        if (Boolean.getBoolean("legacyconnector.debugBlizzardFog") && FOG_REPORTED.add(key)) {
            LOG.info("SRP Blizzard fog event {}: canceledBefore={}, intensity={}, near {} -> {}, far {} -> {}",
                    key, alreadyCanceled, sampledIntensity, previousNear, planes.near(), previousFar, planes.far());
        }
    }

    private static void invokeRenderer(String operation, Method method, Object... args) {
        try {
            method.invoke(null, args);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            report(operation, unwrap(e));
        }
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof InvocationTargetException invocation && invocation.getCause() != null ? invocation.getCause() : error;
    }

    private static void report(String operation, Throwable error) {
        if (REPORTED.add(operation)) LOG.warn("Could not {}: {}", operation, error.toString(), error);
    }
}
