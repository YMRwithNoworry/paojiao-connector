package legacyconnector.v1_12_2.net.minecraft.client.renderer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import legacyconnector.v1_12_2.net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.resources.model.BakedModel;
import org.kylepitcock.legacyconnector.v1_12_2.client.Models1122;

class BlockModelShapes1122Test {
    @Test
    void usesLegacyMissingModelWhenTheModernStateLookupReturnsNull() {
        IBakedModel missing = (IBakedModel) Proxy.newProxyInstance(
                IBakedModel.class.getClassLoader(), new Class<?>[] {IBakedModel.class}, (proxy, method, args) -> null);

        IBakedModel result = BlockModelShapes.legacyOrMissing(null, () -> missing);

        assertSame(missing, result);
    }

    @Test
    void adaptsLoadedModernModelWithoutConsultingTheMissingModel() {
        BakedModel modern = (BakedModel) Proxy.newProxyInstance(
                BakedModel.class.getClassLoader(), new Class<?>[] {BakedModel.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "test-baked-model";
                    default -> null;
                });

        IBakedModel result = BlockModelShapes.legacyOrMissing(modern, () -> fail("unexpected missing-model lookup"));

        assertNotNull(result);
        assertInstanceOf(Models1122.LegacyView.class, result);
    }
}
