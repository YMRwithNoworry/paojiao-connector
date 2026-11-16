package legacyconnector.v1_12_2.net.minecraftforge.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import legacyconnector.v1_12_2.net.minecraft.entity.item.EntityItem;
import legacyconnector.v1_12_2.net.minecraft.util.DamageSource;
import legacyconnector.v1_12_2.net.minecraft.world.World;
import legacyconnector.v1_12_2.net.minecraftforge.common.MinecraftForge;
import legacyconnector.v1_12_2.net.minecraftforge.event.entity.player.PlayerDropsEvent;
import legacyconnector.v1_12_2.net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.junit.jupiter.api.Test;

class ForgeEventFactoryPlayerDrops1122Test {
    @Test
    void postsCancelableEventAndPreservesMutableDropsList() {
        var drops = new ArrayList<EntityItem>();
        var addedDrop = new EntityItem((World) null);
        var received = new AtomicReference<PlayerDropsEvent>();
        Object listener = new Object() {
            @SubscribeEvent
            public void onPlayerDrops(PlayerDropsEvent event) {
                received.set(event);
                event.getDrops().add(addedDrop);
                event.setCanceled(true);
            }
        };
        var bus = MinecraftForge.EVENT_BUS;
        bus.register(listener);
        try {
            ForgeEventFactory.onPlayerDrops(null, DamageSource.GENERIC, drops, true);
        } finally {
            bus.unregister(listener);
        }

        PlayerDropsEvent event = received.get();
        assertNotNull(event);
        assertSame(drops, event.getDrops());
        assertSame(DamageSource.GENERIC, event.getSource());
        assertTrue(event.isRecentlyHit());
        assertTrue(event.isCanceled());
        assertEquals(1, drops.size());
        assertSame(addedDrop, drops.getFirst());
    }
}
