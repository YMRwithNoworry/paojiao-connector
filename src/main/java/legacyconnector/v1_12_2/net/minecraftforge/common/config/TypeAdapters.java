package legacyconnector.v1_12_2.net.minecraftforge.common.config;

/** Forge 1.12.2 config value adapters. */
public final class TypeAdapters {
    public static final ITypeAdapter bool = of(boolean.class);
    public static final ITypeAdapter boolA = of(boolean[].class);
    public static final ITypeAdapter Bool = of(Boolean.class);
    public static final ITypeAdapter BoolA = of(Boolean[].class);
    public static final ITypeAdapter flt = of(float.class);
    public static final ITypeAdapter fltA = of(float[].class);
    public static final ITypeAdapter Flt = of(Float.class);
    public static final ITypeAdapter FltA = of(Float[].class);
    public static final ITypeAdapter dbl = of(double.class);
    public static final ITypeAdapter dblA = of(double[].class);
    public static final ITypeAdapter Dbl = of(Double.class);
    public static final ITypeAdapter DblA = of(Double[].class);
    public static final ITypeAdapter byt = of(byte.class);
    public static final ITypeAdapter bytA = of(byte[].class);
    public static final ITypeAdapter Byt = of(Byte.class);
    public static final ITypeAdapter BytA = of(Byte[].class);
    public static final ITypeAdapter chr = of(char.class);
    public static final ITypeAdapter chrA = of(char[].class);
    public static final ITypeAdapter Chr = of(Character.class);
    public static final ITypeAdapter ChrA = of(Character[].class);
    public static final ITypeAdapter shrt = of(short.class);
    public static final ITypeAdapter shrtA = of(short[].class);
    public static final ITypeAdapter Shrt = of(Short.class);
    public static final ITypeAdapter ShrtA = of(Short[].class);
    public static final ITypeAdapter int_ = of(int.class);
    public static final ITypeAdapter intA = of(int[].class);
    public static final ITypeAdapter Int = of(Integer.class);
    public static final ITypeAdapter IntA = of(Integer[].class);
    public static final ITypeAdapter Str = of(String.class);
    public static final ITypeAdapter StrA = of(String[].class);

    private TypeAdapters() {}

    static ITypeAdapter of(Class<?> declaredType) {
        Class<?> valueType = declaredType;
        while (valueType.isArray()) valueType = valueType.getComponentType();
        Property.Type propertyType = valueType == boolean.class || valueType == Boolean.class ? Property.Type.BOOLEAN
                : valueType == float.class || valueType == Float.class || valueType == double.class || valueType == Double.class
                        ? Property.Type.DOUBLE
                : valueType == byte.class || valueType == Byte.class || valueType == char.class || valueType == Character.class
                        || valueType == short.class || valueType == Short.class || valueType == int.class || valueType == Integer.class
                        || valueType == long.class || valueType == Long.class ? Property.Type.INTEGER
                : Property.Type.STRING;
        return new TypedAdapter(declaredType, propertyType);
    }

    private record TypedAdapter(Class<?> declaredType, Property.Type type) implements ITypeAdapter {
        @Override public Property.Type getType() { return type; }
        @Override public boolean isArrayAdapter() { return declaredType.isArray(); }

        @Override
        public void setDefaultValue(Property property, Object value) {
            set(property, value, true);
        }

        @Override
        public void setValue(Property property, Object value) {
            set(property, value, false);
        }

        private void set(Property property, Object value, boolean defaultValue) {
            if (declaredType.isArray()) {
                int length = value == null ? 0 : java.lang.reflect.Array.getLength(value);
                String[] values = new String[length];
                Class<?> component = declaredType.getComponentType();
                for (int i = 0; i < length; i++) values[i] = format(java.lang.reflect.Array.get(value, i), component);
                if (defaultValue) property.setDefaultValues(values);
                else property.setValues(values);
                return;
            }
            String string = format(value, declaredType);
            if (defaultValue) property.setDefaultValue(string);
            else property.setValue(string);
        }

        @Override
        public Object getValue(Property property) {
            if (!declaredType.isArray()) return scalarValue(property, declaredType);
            String[] values = property.getStringList();
            Class<?> component = declaredType.getComponentType();
            Object array = java.lang.reflect.Array.newInstance(component, values.length);
            for (int i = 0; i < values.length; i++) java.lang.reflect.Array.set(array, i, parse(values[i], component));
            return array;
        }
    }

    private static String format(Object value, Class<?> target) {
        if (target == char.class || target == Character.class) {
            return Integer.toString(value == null ? 0 : (Character) value);
        }
        return String.valueOf(value);
    }

    private static Object scalarValue(Property property, Class<?> target) {
        if (target == boolean.class || target == Boolean.class) return property.getBoolean();
        if (target == byte.class || target == Byte.class) return (byte) property.getInt();
        if (target == short.class || target == Short.class) return (short) property.getInt();
        if (target == int.class || target == Integer.class) return property.getInt();
        if (target == long.class || target == Long.class) return property.getLong();
        if (target == char.class || target == Character.class) return (char) property.getInt();
        if (target == float.class || target == Float.class) return (float) property.getDouble();
        if (target == double.class || target == Double.class) return property.getDouble();
        return property.getString();
    }

    private static Object parse(String value, Class<?> target) {
        if (target == boolean.class || target == Boolean.class) return Boolean.parseBoolean(value);
        if (target == byte.class || target == Byte.class) return Byte.parseByte(value);
        if (target == short.class || target == Short.class) return Short.parseShort(value);
        if (target == int.class || target == Integer.class) return Integer.parseInt(value);
        if (target == long.class || target == Long.class) return Long.parseLong(value);
        if (target == char.class || target == Character.class) return (char) Integer.parseInt(value);
        if (target == float.class || target == Float.class) return Float.parseFloat(value);
        if (target == double.class || target == Double.class) return Double.parseDouble(value);
        return value;
    }
}
