package legacyconnector.v1_12_2.net.minecraftforge.common.config;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Forge's field adapters for primitive values, enums and string-keyed maps. */
public abstract class FieldWrapper implements IFieldWrapper {
    private static final Set<Class<?>> SIMPLE = Set.of(
            boolean.class, Boolean.class, int.class, Integer.class, float.class, Float.class, double.class, Double.class,
            byte.class, Byte.class, char.class, Character.class, short.class, Short.class, long.class, Long.class, String.class,
            boolean[].class, Boolean[].class, int[].class, Integer[].class, float[].class, Float[].class, double[].class,
            Double[].class, byte[].class, Byte[].class, char[].class, Character[].class, short[].class, Short[].class,
            long[].class, Long[].class, String[].class);

    protected final String category;
    protected final String name;
    protected final Field field;
    protected final Object instance;

    public FieldWrapper(String category, Field field, Object instance) {
        this.category = category;
        this.field = field;
        this.instance = instance;
        Config.Name annotation = field.getAnnotation(Config.Name.class);
        this.name = annotation == null ? field.getName() : annotation.value();
        field.setAccessible(true);
    }

    public static boolean hasWrapperFor(Field field) {
        Class<?> type = field.getType();
        return SIMPLE.contains(type) || Enum.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type);
    }

    public static IFieldWrapper get(Object instance, Field field, String category) {
        if (!hasWrapperFor(field)) return null;
        return new ValueWrapper(instance, field, category);
    }

    private static ITypeAdapter adapterFor(Class<?> type) {
        return TypeAdapters.of(type);
    }

    private static Class<?> mapValueType(Field field) {
        Type generic = field.getGenericType();
        if (!(generic instanceof ParameterizedType parameterized)) {
            throw new IllegalArgumentException("Map config field must declare its value type: " + field);
        }
        Type value = parameterized.getActualTypeArguments()[1];
        if (!(value instanceof Class<?> valueClass)) {
            throw new IllegalArgumentException("Map config values must have a concrete type: " + field);
        }
        return valueClass;
    }

    private static ITypeAdapter adapterForMap(Field field) {
        return adapterFor(mapValueType(field));
    }

    private static final class ValueWrapper extends FieldWrapper {
        private final boolean mapField;
        private final ITypeAdapter adapter;
        private final String mapCategory;
        private final String mapPrefix;
        private final Class<?> mapValueType;

        private ValueWrapper(Object instance, Field field, String category) {
            super(category, field, instance);
            mapField = Map.class.isAssignableFrom(field.getType());
            mapValueType = mapField ? FieldWrapper.mapValueType(field) : null;
            adapter = mapField ? adapterForMap(field) : adapterFor(field.getType());
            mapCategory = mapField ? join(category, name.toLowerCase(Locale.ENGLISH)) : category;
            mapPrefix = mapField ? mapCategory + "." : null;
        }

        @Override public ITypeAdapter getTypeAdapter() { return adapter; }

        @Override
        public String[] getKeys() {
            if (!mapField) return new String[] {join(category, name)};
            Map<?, ?> values = values();
            return values.keySet().stream().map(key -> mapPrefix + key).toArray(String[]::new);
        }

        @Override
        public Object getValue(String key) {
            if (!handlesKey(key)) throw new IllegalArgumentException("Unsupported config key: " + key);
            Object value = mapField ? values().get(key.substring(mapPrefix.length())) : readField();
            return value instanceof Enum<?> e ? e.name() : value;
        }

        @Override
        public void setValue(String key, Object value) {
            if (!handlesKey(key)) throw new IllegalArgumentException("Unsupported config key: " + key);
            if (mapField) {
                Object mapValue = mapValueType.isEnum() && value instanceof String string
                        ? Enum.valueOf(mapValueType.asSubclass(Enum.class), string) : value;
                values().put(key.substring(mapPrefix.length()), mapValue);
            } else if (field.getType().isEnum() && value instanceof String string) {
                writeField(Enum.valueOf(field.getType().asSubclass(Enum.class), string));
            } else {
                writeField(value);
            }
        }

        @Override
        public boolean hasKey(String key) {
            return mapField ? handlesKey(key) && values().containsKey(key.substring(mapPrefix.length())) : handlesKey(key);
        }

        @Override
        public boolean handlesKey(String key) {
            return mapField ? key != null && key.startsWith(mapPrefix) : join(category, name).equals(key);
        }

        @Override
        public String getCategory() {
            return mapField ? mapCategory : category == null ? "" : category;
        }

        @Override
        public void setupConfiguration(Configuration cfg, String description, String languageKey,
                boolean requiresMcRestart, boolean requiresWorldRestart) {
            ConfigCategory configCategory = cfg.getCategory(getCategory());
            configCategory.setComment(description);
            configCategory.setLanguageKey(languageKey);
            configCategory.setRequiresMcRestart(requiresMcRestart);
            configCategory.setRequiresWorldRestart(requiresWorldRestart);

            if (mapField) return;
            Property property = configCategory.get(name);
            if (property == null) return;
            property.setComment(description);
            property.setLanguageKey(languageKey);
            property.setRequiresMcRestart(requiresMcRestart);
            property.setRequiresWorldRestart(requiresWorldRestart);
            if (field.getType().isEnum()) {
                Object[] constants = field.getType().getEnumConstants();
                String[] names = Arrays.stream(constants).map(value -> ((Enum<?>) value).name()).toArray(String[]::new);
                String[] display = Arrays.stream(constants).map(Object::toString).toArray(String[]::new);
                property.setValidationPattern(Pattern.compile(String.join("|", names)));
                property.setValidValues(names);
                property.setValidValuesDisplay(display);
            }
            Config.RangeInt integerRange = field.getAnnotation(Config.RangeInt.class);
            if (integerRange != null) {
                property.setMinValue(integerRange.min());
                property.setMaxValue(integerRange.max());
            }
            Config.RangeDouble doubleRange = field.getAnnotation(Config.RangeDouble.class);
            if (doubleRange != null) {
                property.setMinValue(doubleRange.min());
                property.setMaxValue(doubleRange.max());
            }
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> values() {
            Object map = readField();
            if (map == null) {
                map = new java.util.LinkedHashMap<String, Object>();
                writeField(map);
            }
            return (Map<String, Object>) map;
        }

        private Object readField() {
            try {
                return field.get(instance);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        private void writeField(Object value) {
            try {
                field.set(instance, value);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static String join(String parent, String child) {
        return parent == null || parent.isEmpty() ? child : parent + "." + child;
    }
}
