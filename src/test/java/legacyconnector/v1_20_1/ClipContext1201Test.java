package legacyconnector.v1_20_1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClipContext1201Test {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        var dist = net.neoforged.fml.loading.FMLLoader.class.getDeclaredField("dist");
        dist.setAccessible(true);
        dist.set(null, net.neoforged.api.distmarker.Dist.CLIENT);
        net.neoforged.fml.loading.LoadingModList.of(
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void raycastsWithoutAnEntity() throws ReflectiveOperationException {
        var from = new Vec3(0.5, 2.0, 0.5);
        var to = new Vec3(0.5, -1.0, 0.5);
        var ray = Compat1201.clipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
        var collisionContext = ClipContext.class.getDeclaredField("collisionContext");
        collisionContext.setAccessible(true);

        assertEquals(from, ray.getFrom());
        assertEquals(to, ray.getTo());
        assertSame(CollisionContext.empty(), collisionContext.get(ray));
    }
}
