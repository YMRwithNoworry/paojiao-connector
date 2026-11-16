package legacyconnector.v1_12_2.net.minecraftforge.common.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import legacyconnector.v1_12_2.net.minecraftforge.fml.common.ObfuscationReflectionHelper;

class TypeAdapter1122Test {
    private static final class Values {
        public static int count = 4;
        public static int[] counts = {2, 3};
        public static String[] names = {"alpha"};
        public static Map<String, Integer> weights = new LinkedHashMap<>(Map.of("alpha", 5));
    }

    @Test
    void configAdapterExposesTypeAndArrayShapeThroughForgeReflectionApi() throws Exception {
        Method isArrayAdapter = ObfuscationReflectionHelper.findMethod(ITypeAdapter.class, "isArrayAdapter", boolean.class);
        assertEquals(0, isArrayAdapter.getParameterCount());

        assertAdapter(isArrayAdapter, "count", Property.Type.INTEGER, false);
        assertAdapter(isArrayAdapter, "counts", Property.Type.INTEGER, true);
        assertAdapter(isArrayAdapter, "names", Property.Type.STRING, true);
    }

    @Test
    void typeAdapterStaticFieldsKeepForgeDescriptors() throws Exception {
        for (String name : new String[] {"bool", "boolA", "Bool", "BoolA", "flt", "fltA", "Flt", "FltA",
                "dbl", "dblA", "Dbl", "DblA", "byt", "bytA", "Byt", "BytA", "chr", "chrA", "Chr", "ChrA",
                "shrt", "shrtA", "Shrt", "ShrtA", "int_", "intA", "Int", "IntA", "Str", "StrA"}) {
            assertEquals(ITypeAdapter.class, TypeAdapters.class.getField(name).getType(), name);
        }
    }

    @Test
    void fieldWrappersExposeForgeKeysCategoriesAndLiveValues() throws Exception {
        Values.count = 4;
        Method getCategory = ObfuscationReflectionHelper.findMethod(IFieldWrapper.class, "getCategory", String.class);
        IFieldWrapper scalar = wrapper("count", "general");
        assertEquals("general", getCategory.invoke(scalar));
        assertArrayEquals(new String[] {"general.count"}, scalar.getKeys());
        assertTrue(scalar.hasKey("general.count"));
        assertTrue(scalar.handlesKey("general.count"));
        assertEquals(4, scalar.getValue("general.count"));
        scalar.setValue("general.count", 9);
        assertEquals(9, Values.count);

        IFieldWrapper map = wrapper("weights", "general");
        assertEquals("general.weights", getCategory.invoke(map));
        assertArrayEquals(new String[] {"general.weights.alpha"}, map.getKeys());
        assertTrue(map.hasKey("general.weights.alpha"));
        assertTrue(map.handlesKey("general.weights.beta"));
        map.setValue("general.weights.beta", 7);
        assertEquals(7, Values.weights.get("beta"));
    }

    @Test
    void adaptersSupportTheForgeReflectiveReadWriteContract() throws Exception {
        ITypeAdapter scalar = wrapper("count", "test").getTypeAdapter();
        Method setDefault = ObfuscationReflectionHelper.findMethod(ITypeAdapter.class, "setDefaultValue", void.class,
                Property.class, Object.class);
        Method setValue = ObfuscationReflectionHelper.findMethod(ITypeAdapter.class, "setValue", void.class,
                Property.class, Object.class);
        Method getValue = ObfuscationReflectionHelper.findMethod(ITypeAdapter.class, "getValue", Object.class, Property.class);
        Property scalarProperty = new Property("count", "0", Property.Type.INTEGER);
        setDefault.invoke(scalar, scalarProperty, 4);
        setValue.invoke(scalar, scalarProperty, 9);
        assertEquals(9, getValue.invoke(scalar, scalarProperty));

        ITypeAdapter array = wrapper("counts", "test").getTypeAdapter();
        Property arrayProperty = new Property("counts", new String[0], Property.Type.INTEGER);
        setDefault.invoke(array, arrayProperty, new int[] {2, 3});
        setValue.invoke(array, arrayProperty, new int[] {5, 8});
        assertArrayEquals(new int[] {5, 8}, (int[]) getValue.invoke(array, arrayProperty));
    }

    @Test
    void fieldWrapperAppliesConfigMetadataToTheResolvedCategoryAndProperty() throws Exception {
        Configuration config = new Configuration();
        Property property = config.get("general", "count", 4);
        IFieldWrapper wrapper = wrapper("count", "general");

        wrapper.setupConfiguration(config, "Count comment", "example.count", true, false, true);

        assertEquals("Count comment", property.getComment());
        assertEquals("example.count", property.getLanguageKey());
        assertTrue(property.requiresMcRestart());
        assertFalse(property.requiresWorldRestart());
        assertEquals("general", config.getCategory("general").getName());
    }

    private static void assertAdapter(Method isArrayAdapter, String fieldName, Property.Type expectedType, boolean expectedArray)
            throws Exception {
        ITypeAdapter adapter = wrapper(fieldName, "test").getTypeAdapter();
        assertEquals(expectedType, adapter.getType());
        assertEquals(expectedArray, isArrayAdapter.invoke(adapter));
        assertEquals(expectedArray, adapter.isArrayAdapter());
    }

    private static IFieldWrapper wrapper(String fieldName, String category) throws Exception {
        Field field = Values.class.getDeclaredField(fieldName);
        return FieldWrapper.get(null, field, category);
    }
}
