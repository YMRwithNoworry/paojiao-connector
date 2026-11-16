package legacyconnector.v1_12_2.net.minecraftforge.common.config;

/** Forge's adapter between one config field and its fully-qualified config properties. */
public interface IFieldWrapper {
    ITypeAdapter getTypeAdapter();
    String[] getKeys();
    Object getValue(String key);
    void setValue(String key, Object value);
    boolean hasKey(String key);
    boolean handlesKey(String key);
    void setupConfiguration(Configuration cfg, String description, String languageKey, boolean requiresMcRestart, boolean requiresWorldRestart);
    String getCategory();

    default void setupConfiguration(Configuration cfg, String description, String languageKey, boolean requiresMcRestart,
            boolean requiresWorldRestart, boolean hasSlidingControl) {
        setupConfiguration(cfg, description, languageKey, requiresMcRestart, requiresWorldRestart);
    }
}
