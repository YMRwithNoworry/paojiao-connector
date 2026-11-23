package org.kylepitcock.legacyconnector.loader.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;

class Mc1201FrameHierarchyTest {
    @Test
    void mergesNativeAndLegacySubclassesWithoutLoadingDependencies() {
        String base = "nativeapi/Engine";
        String wheel = "nativeapi/Wheel";
        String track = "nativeapi/Track";
        var hierarchy = new Mc1201Hierarchy().frameDependencies(Map.of(
                base, new Mc1201Hierarchy.ClassHeader("java/lang/Object", List.of()),
                wheel, new Mc1201Hierarchy.ClassHeader(base, List.of()),
                track, new Mc1201Hierarchy.ClassHeader(base, List.of())));
        ClassNode local = new ClassNode();
        local.name = "legacy/CustomWheel";
        local.superName = wheel;
        hierarchy.add(local);

        assertEquals(base, hierarchy.commonSuperClass(wheel, track, () -> fail("native types are indexed")));
        assertEquals(base, hierarchy.commonSuperClass(local.name, track, () -> fail("mixed types are indexed")));
        assertEquals(wheel, hierarchy.commonSuperClass(wheel, local.name, () -> fail("native parent is indexed")));
        assertEquals(Mc1201Hierarchy.Origin.UNKNOWN, hierarchy.declaredIn(wheel, "unmapped", "()V"));
    }
}
