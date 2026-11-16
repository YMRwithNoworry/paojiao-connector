package legacyconnector.v1_12_2.net.minecraftforge.common.config;

/** Forge's per-type conversion between config properties and field values; mods read the property type (First Aid, by reflection). */
public interface ITypeAdapter {
    void setDefaultValue(Property property, Object value);
    void setValue(Property property, Object value);
    Object getValue(Property property);
    Property.Type getType();

    default boolean isArrayAdapter() {
        return false;
    }
}
