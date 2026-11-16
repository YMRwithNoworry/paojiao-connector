package org.kylepitcock.legacyconnector.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class LegacyAccessTest {
    static class Parent {
        private String inherited = "initial";
    }

    static final class Fixture extends Parent {
        private final String selected;

        private Fixture() {
            selected = "empty";
        }

        private Fixture(Number number) {
            selected = "number:" + number;
        }

        private Fixture(Integer number) {
            selected = "integer:" + number;
        }

        private String select(Number number) {
            return "number:" + number;
        }

        private String select(Integer number) {
            return "integer:" + number;
        }

        private String join(String prefix, int value) {
            return prefix + value;
        }
    }

    @Test
    void resolvesConstructorsMethodsAndInheritedFields() {
        Fixture empty = (Fixture) LegacyAccess.newInstance(Fixture.class);
        Fixture integer = (Fixture) LegacyAccess.newInstance(Fixture.class, 7);

        assertEquals("empty", empty.selected);
        assertEquals("integer:7", integer.selected);
        assertEquals("integer:8", LegacyAccess.invoke(integer, "select", 8));
        assertEquals("number:8.5", LegacyAccess.invoke(integer, "select", 8.5));
        assertEquals("value:3", LegacyAccess.invoke(integer, "join", "value:", 3));

        LegacyAccess.setField(integer, "inherited", "updated");
        assertEquals("updated", LegacyAccess.getField(integer, "inherited"));
    }

    @Test
    void safelySharesResolvedHandlesAcrossThreads() {
        Fixture fixture = new Fixture();
        IntStream.range(0, 256).parallel().forEach(i ->
                assertEquals("integer:" + i, LegacyAccess.invoke(fixture, "select", i)));
    }
}
