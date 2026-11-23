package legacyconnector.v1_20_1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import org.junit.jupiter.api.Test;

class LegacySubscriberRegistration1201Test {
    public static final class InstanceEvent extends Event {
        int calls;
    }

    public static final class StaticEvent extends Event {
        int calls;
    }

    public static final class MixedSubscriber {
        @SubscribeEvent
        public void receive(InstanceEvent event) {
            event.calls++;
        }

        @SubscribeEvent
        public static void receive(StaticEvent event) {
            event.calls++;
        }
    }

    @Test
    void selectsModeDeduplicatesAndRemovesTheRightSubscription() {
        var bus = BusBuilder.builder().build();
        var subscriber = new MixedSubscriber();
        ForgeApi1201.busRegister(bus, subscriber);
        ForgeApi1201.busRegister(bus, subscriber);
        assertEquals(1, bus.post(new InstanceEvent()).calls);
        assertEquals(0, bus.post(new StaticEvent()).calls);

        ForgeApi1201.busRegister(bus, MixedSubscriber.class);
        assertEquals(1, bus.post(new InstanceEvent()).calls);
        assertEquals(1, bus.post(new StaticEvent()).calls);

        ForgeApi1201.unregisterSubscriber(bus, subscriber);
        assertEquals(0, bus.post(new InstanceEvent()).calls);
        assertEquals(1, bus.post(new StaticEvent()).calls);
        ForgeApi1201.unregisterSubscriber(bus, MixedSubscriber.class);
        assertEquals(0, bus.post(new StaticEvent()).calls);
    }
}
