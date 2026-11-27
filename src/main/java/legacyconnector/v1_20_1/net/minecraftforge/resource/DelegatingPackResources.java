package legacyconnector.v1_20_1.net.minecraftforge.resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import org.kylepitcock.legacyconnector.v1_20_1.migrate.RoundCompat;

/** Forge's ordered pack aggregate. Unlike vanilla overlays, the first delegate has lookup priority. */
public class DelegatingPackResources extends AbstractPackResources {
    private final PackMetadataSection metadata;
    private final List<PackResources> delegates;
    private final Map<String, List<PackResources>> assets;
    private final Map<String, List<PackResources>> data;

    public DelegatingPackResources(String id, boolean builtin, PackMetadataSection metadata,
            List<? extends PackResources> delegates) {
        super(RoundCompat.packLocation(id, builtin));
        this.metadata = metadata;
        this.delegates = List.copyOf(delegates);
        assets = index(PackType.CLIENT_RESOURCES);
        data = index(PackType.SERVER_DATA);
    }

    private Map<String, List<PackResources>> index(PackType type) {
        Map<String, List<PackResources>> index = new HashMap<>();
        for (PackResources delegate : delegates) {
            for (String namespace : delegate.getNamespaces(type)) {
                index.computeIfAbsent(namespace, key -> new ArrayList<>()).add(delegate);
            }
        }
        return Collections.unmodifiableMap(index);
    }

    private Map<String, List<PackResources>> namespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? assets : data;
    }

    public Collection<PackResources> getChildren() {
        return delegates;
    }

    public boolean isBuiltin() {
        return location().source() == PackSource.BUILT_IN;
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        List<PackResources> candidates = namespaces(type).get(location.getNamespace());
        if (candidates != null) {
            for (PackResources candidate : candidates) {
                IoSupplier<InputStream> resource = candidate.getResource(type, location);
                if (resource != null) return resource;
            }
        }
        return null;
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        for (PackResources delegate : delegates) delegate.listResources(type, namespace, path, output);
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return namespaces(type).keySet();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) throws IOException {
        return PackMetadataSection.TYPE.getMetadataSectionName().equals(serializer.getMetadataSectionName())
                ? (T) metadata : null;
    }

    @Override
    public void close() {
        for (PackResources delegate : delegates) delegate.close();
    }
}
