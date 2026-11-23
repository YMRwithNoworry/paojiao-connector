package legacyconnector.v1_20_1;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;

/** Forge selected the static or instance methods before registering a subscriber, rather than rejecting a mixed class. */
final class LegacySubscriberRegistration1201 {
    private static final Map<IEventBus, Map<Object, List<Consumer<?>>>> registrations = new WeakHashMap<>();

    private LegacySubscriberRegistration1201() {}

    static boolean registerIfMixed(IEventBus bus, Object target) {
        Class<?> type = target instanceof Class<?> clazz ? clazz : target.getClass();
        boolean staticOnly = target instanceof Class<?>;
        // Check before handing the target to NeoForge: its register may attach some methods before throwing.
        for (Method method : type.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) == staticOnly || !method.isAnnotationPresent(SubscribeEvent.class)) continue;
            registerSelected(bus, target, type, staticOnly);
            return true;
        }
        return false;
    }

    private static void registerSelected(IEventBus bus, Object target, Class<?> type, boolean staticOnly) {
        synchronized (registrations) {
            var targets = registrations.get(bus);
            if (targets != null && targets.containsKey(target)) return;
            var attached = new ArrayList<Consumer<?>>();
            Object receiver = staticOnly ? null : target;
            try {
                for (Method method : type.getMethods()) {
                    if (Modifier.isStatic(method.getModifiers()) != staticOnly) continue;
                    var annotation = method.getAnnotation(SubscribeEvent.class);
                    if (annotation == null) continue;
                    if (method.getParameterCount() != 1 || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                        throw new IllegalArgumentException("Expected one event parameter for " + method);
                    }
                    method.setAccessible(true);
                    var event = method.getParameterTypes()[0].asSubclass(Event.class);
                    if (Modifier.isAbstract(event.getModifiers())) {
                        for (var concrete : ForgeApi1201.concreteEvents(event)) attach(bus, concrete, annotation, method, receiver, attached);
                    } else {
                        attach(bus, event, annotation, method, receiver, attached);
                    }
                }
            } catch (RuntimeException | Error failure) {
                attached.forEach(bus::unregister);
                throw failure;
            }
            if (!attached.isEmpty()) registrations.computeIfAbsent(bus, unused -> new HashMap<>()).put(target, attached);
        }
    }

    private static <E extends Event> void attach(IEventBus bus, Class<E> event, SubscribeEvent annotation, Method method,
            Object receiver, List<Consumer<?>> attached) {
        Consumer<E> listener = value -> {
            try {
                method.invoke(receiver, value);
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException runtime) throw runtime;
                if (e.getCause() instanceof Error error) throw error;
                throw new IllegalStateException(e.getCause());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        };
        try {
            bus.addListener(annotation.priority(), annotation.receiveCanceled(), event, listener);
            attached.add(listener);
        } catch (IllegalArgumentException e) {
            if (!ForgeApi1201.wrongBus(e)) throw e;
        }
    }

    static void unregister(IEventBus bus, Object target) {
        synchronized (registrations) {
            var targets = registrations.get(bus);
            if (targets != null) {
                var attached = targets.remove(target);
                if (attached != null) attached.forEach(bus::unregister);
                if (targets.isEmpty()) registrations.remove(bus);
            }
            bus.unregister(target);
        }
    }
}
