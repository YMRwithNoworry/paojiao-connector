package org.kylepitcock.legacyconnector.v1_12_2.bridge;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Constructs 1.12.2 coremod plugins the way FML did before mods loaded: the plugin is instantiated, gets its injectData, and its
 * setup hook runs. Mods check that their coremod was loaded (EnderCore fails with "accessed too early" otherwise). The plugins are
 * driven by reflection, so they work with whatever the legacy plugin interface looks like after relocation.
 */
public final class CoreModPlugins1122 {
    private static final Logger LOG = LoggerFactory.getLogger("LegacyConnector/CoreMods");
    private static final String RESOURCE = "META-INF/legacyconnector-coremod.txt";
    private static boolean ran;

    private CoreModPlugins1122() {}

    public static synchronized void runAll(ClassLoader loader) {
        if (ran) return;
        ran = true;
        Set<String> classes = new LinkedHashSet<>();
        Map<String, URL> where = new HashMap<>(); // plugin class -> the resource URL that named it (the translated jar)
        try {
            var urls = loader.getResources(RESOURCE);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                try (InputStream in = url.openStream()) {
                    String name = coremodClass(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    if (name != null) {
                        classes.add(name);
                        where.putIfAbsent(name, url);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Could not list legacy coremods: {}", e.toString());
        }
        File gameDir = FMLPaths.GAMEDIR.get().toFile();
        System.setProperty("java.awt.headless", "true");
        List<ClassLoader> candidates = classLoaders(loader, classes);
        List<Object[]> constructed = new ArrayList<>();
        for (String name : classes) {
            try {
                Class<?> cls = load(name, candidates, where.get(name));
                var ctor = cls.getDeclaredConstructor();
                ctor.setAccessible(true);
                Object plugin = ctor.newInstance();
                int index = 0;
                for (var a : cls.getAnnotations()) {
                    if (a.annotationType().getSimpleName().equals("SortingIndex")) index = (Integer) a.annotationType().getMethod("value").invoke(a);
                }
                constructed.add(new Object[] {name, plugin, index});
                LOG.info("Constructed legacy coremod {}", name);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                Throwable cause = rootCause(e);
                LOG.warn("Legacy coremod {} could not be constructed: {}", name, cause.toString(), cause);
            }
        }
        constructed.sort(Comparator.comparingInt(o -> (Integer) o[2]));
        for (Object[] o : constructed) {
            Object plugin = o[1];
            setUp((String) o[0], plugin, plugin.getClass().getClassLoader(), gameDir);
        }
    }

    static String coremodClass(String content) {
        String[] lines = content.trim().split("\\R");
        if (lines.length < 2 || !"1.12.2".equals(lines[1].trim())) return null;
        String name = lines[0].trim();
        return name.isEmpty() ? null : name;
    }

    /** Gets the owning Java module from each FML container, then resolves coremod packages in those modules' layers. */
    private static List<ClassLoader> classLoaders(ClassLoader loader, Iterable<String> coremodClasses) {
        Set<ClassLoader> owners = new LinkedHashSet<>();
        Set<ModuleLayer> layers = new LinkedHashSet<>();
        for (var mod : org.kylepitcock.legacyconnector.core.LegacyMods.all()) {
            addModule(owningModule(mod.container), owners, layers);
            for (Class<?> owner : new Class<?>[] {mod.mainClass, mod.instance == null ? null : mod.instance.getClass()}) {
                if (owner != null) addModule(owner.getModule(), owners, layers);
            }
        }
        for (String coremodClass : coremodClasses) {
            ClassLoader pluginLoader = moduleClassLoaderForPackage(coremodClass, layers);
            if (pluginLoader != null) owners.add(pluginLoader);
        }
        return withOwnerLoaders(loader, owners);
    }

    private static void addModule(Module module, Set<ClassLoader> owners, Set<ModuleLayer> layers) {
        if (module == null) return;
        if (module.getClassLoader() != null) owners.add(module.getClassLoader());
        if (module.getLayer() != null) layers.add(module.getLayer());
    }

    static Module owningModule(Object container) {
        if (container == null) return null;
        for (Class<?> type = container.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != Module.class) continue;
                try {
                    Object value = org.kylepitcock.legacyconnector.compat.jdk.ReflectionCompat.get(field, container);
                    if (value instanceof Module module) return module;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Other container implementations may keep their owning module elsewhere.
                }
            }
        }
        return null;
    }

    static ClassLoader moduleClassLoaderForPackage(String className, Iterable<ModuleLayer> layers) {
        int separator = className.lastIndexOf('.');
        if (separator < 1) return null;
        String packageName = className.substring(0, separator);
        for (ModuleLayer layer : layers) {
            for (Module module : layer.modules()) {
                if (module.getPackages().contains(packageName)) return module.getClassLoader();
            }
        }
        return null;
    }

    static List<ClassLoader> withOwnerLoaders(ClassLoader loader, Iterable<ClassLoader> owners) {
        Set<ClassLoader> candidates = new LinkedHashSet<>();
        candidates.add(loader == null ? ClassLoader.getSystemClassLoader() : loader);
        for (ClassLoader owner : owners) if (owner != null) candidates.add(owner);
        return List.copyOf(candidates);
    }

    /** Tries the game loader first, then the module loaders of the legacy jars. */
    static Class<?> loadFrom(String name, Iterable<ClassLoader> candidates) throws ClassNotFoundException {
        ClassNotFoundException first = null;
        for (ClassLoader candidate : candidates) {
            try {
                return Class.forName(name, true, candidate);
            } catch (ClassNotFoundException e) {
                if (first == null) first = e;
            }
        }
        throw first == null ? new ClassNotFoundException(name) : first;
    }

    private static Class<?> load(String name, Iterable<ClassLoader> candidates, URL announcedBy) throws ClassNotFoundException {
        try {
            return loadFrom(name, candidates);
        } catch (ClassNotFoundException e) {
            int tried = candidates instanceof java.util.Collection<?> collection ? collection.size() : -1;
            LOG.warn("Legacy coremod {} is announced by {} but no class loader ({} tried) can load it", name, announcedBy, tried);
            throw e;
        }
    }

    private static void setUp(String name, Object plugin, ClassLoader loader, File gameDir) {
        Map<String, Object> data = new HashMap<>();
        data.put("mcLocation", gameDir);
        data.put("coremodList", new ArrayList<>());
        data.put("coremodLocation", null);
        data.put("runtimeDeobfuscationEnabled", Boolean.FALSE);
        data.put("mcVersion", "1.12.2");
        try {
            Method inject = find(plugin.getClass(), "injectData", Map.class);
            if (inject != null) inject.invoke(plugin, data);
            Method setupClass = find(plugin.getClass(), "getSetupClass");
            String setup = setupClass == null ? null : (String) setupClass.invoke(plugin);
            if (setup != null && !setup.isBlank()) {
                Object hook = Class.forName(setup, true, loader).getDeclaredConstructor().newInstance();
                Method hookInject = find(hook.getClass(), "injectData", Map.class);
                if (hookInject != null) hookInject.invoke(hook, data);
                Method call = find(hook.getClass(), "call");
                if (call != null) call.invoke(hook);
            }
            LOG.info("Legacy coremod {} loaded", name);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            Throwable cause = rootCause(e);
            LOG.warn("Legacy coremod {} failed during setup: {}", name, cause.toString(), cause);
        }
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof java.lang.reflect.InvocationTargetException invocation && invocation.getCause() != null) {
            cause = invocation.getCause();
        }
        return cause;
    }

    private static Method find(Class<?> cls, String name, Class<?>... params) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                // look further up
            }
        }
        for (Class<?> i : cls.getInterfaces()) {
            Method m = find(i, name, params);
            if (m != null) return m;
        }
        return null;
    }
}
