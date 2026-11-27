package org.kylepitcock.legacyconnector.loader.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.kylepitcock.legacyconnector.loader.LegacyJar;
import org.kylepitcock.legacyconnector.loader.LegacyModDecl;
import org.kylepitcock.legacyconnector.loader.LegacyVersion;
import org.kylepitcock.legacyconnector.loader.NativeLibraries1201;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

/** The library tables of the 1.20.1 layer ({@code lib-*.tsv}, {@code libraries.tsv}). */
class Library1201TablesTest implements Opcodes {
    @Test
    void libraryRowsAreReadAfterTheMinecraftOnes() {
        Mc1201Tables t = Mc1201Tables.load();
        assertEquals("org/kylepitcock/legacyconnector/v1_20_1/migrate/ArchitecturyEventBuses", t.classes.get("dev/architectury/platform/forge/EventBuses"));
        assertNotNull(t.calls.get("vazkii/patchouli/api/IVariable.asStream()Ljava/util/stream/Stream;"));
        assertTrue(t.bridges.stream().anyMatch(b -> b.owner().equals("top/theillusivec4/curios/api/type/capability/ICurioItem") && b.oldName().equals("getAttributeModifiers")));
        assertNotNull(t.fields.get("top/theillusivec4/curios/api/CuriosCapability.INVENTORY:Llegacyconnector/v1_20_1/net/minecraftforge/common/capabilities/Capability;"));
        assertNotNull(t.calls.get("net/minecraft/world/item/ItemStack.getTag()Lnet/minecraft/nbt/CompoundTag;"), "the Minecraft rows are still there");
    }

    @Test
    void curiosApiCallGoesToTheHelper() {
        ClassNode n = new ClassNode();
        n.version = V17;
        n.access = ACC_PUBLIC;
        n.name = "mod/Uses";
        n.superName = "java/lang/Object";
        MethodNode m = new MethodNode(ACC_PUBLIC | ACC_STATIC, "go", "(Lnet/minecraft/world/entity/LivingEntity;)Ljava/lang/Object;", null, null);
        m.instructions.add(new VarInsnNode(ALOAD, 0));
        m.instructions.add(new MethodInsnNode(INVOKESTATIC, "top/theillusivec4/curios/api/CuriosApi", "getCuriosInventory",
                "(Lnet/minecraft/world/entity/LivingEntity;)Llegacyconnector/v1_20_1/net/minecraftforge/common/util/LazyOptional;", false));
        m.instructions.add(new InsnNode(ARETURN));
        m.maxStack = 1;
        m.maxLocals = 1;
        n.methods.add(m);
        Mc1201Migration.Counts c = Mc1201Migration.apply(n, new Mc1201Hierarchy());
        assertEquals(1, c.calls);
        MethodInsnNode call = (MethodInsnNode) m.instructions.get(1);
        assertEquals("org/kylepitcock/legacyconnector/v1_20_1/migrate/CuriosCompat", call.owner);
    }

    @Test
    void nativeItemFieldLinksWithItsDeclaredSubtypeAndStillReturnsTheRegisteredValue() {
        String owner = "com/tacz/guns/init/ModItems";
        String holder = "net/neoforged/neoforge/registries/DeferredHolder";
        ClassNode n = new ClassNode();
        n.name = "mod/Uses";
        n.superName = "java/lang/Object";
        MethodNode m = new MethodNode(ACC_PUBLIC | ACC_STATIC, "go", "()Ljava/lang/Object;", null, null);
        m.instructions.add(new FieldInsnNode(GETSTATIC, owner, "MODERN_KINETIC_GUN", "L" + holder + ";"));
        m.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, holder, "value", "()Ljava/lang/Object;", false));
        m.instructions.add(new InsnNode(ARETURN));
        m.maxStack = 1;
        n.methods.add(m);

        Mc1201Migration.apply(n, new Mc1201Hierarchy().add(n));

        FieldInsnNode field = (FieldInsnNode) m.instructions.getFirst();
        assertEquals("Lnet/neoforged/neoforge/registries/DeferredItem;", field.desc);
        MethodInsnNode get = (MethodInsnNode) field.getNext();
        assertEquals(holder, get.owner);
        assertEquals("value", get.name);
    }

    @Test
    void nativeAttachmentInstallKeepsGunAndAttachmentInOrder() throws Exception {
        String gun = "com/tacz/guns/api/item/IGun";
        String stack = "Lnet/minecraft/world/item/ItemStack;";
        String provider = "Lnet/minecraft/core/HolderLookup$Provider;";
        var caller = new ClassNode();
        caller.name = "test/AttachmentMenu";
        caller.superName = "java/lang/Object";
        var method = new MethodNode(ACC_STATIC, "install", "(L" + gun + ";" + stack + stack + ")V", null, null);
        method.maxLocals = 3;
        method.maxStack = 3;
        for (int i = 0; i < 3; i++) method.instructions.add(new VarInsnNode(ALOAD, i));
        var install = new MethodInsnNode(INVOKEINTERFACE, gun, "installAttachment", "(" + stack + stack + ")V", true);
        method.instructions.add(install);
        method.instructions.add(new InsnNode(RETURN));
        caller.methods.add(method);

        assertEquals(1, Mc1201Migration.apply(caller, new Mc1201Hierarchy().add(caller)).calls);
        assertEquals("(" + provider + stack + stack + ")V", install.desc);
        var attachment = (VarInsnNode) install.getPrevious();
        var gunStack = (VarInsnNode) attachment.getPrevious();
        var context = (MethodInsnNode) gunStack.getPrevious();
        assertEquals("()" + provider, context.desc);
        assertEquals(3, gunStack.var);
        assertEquals(4, attachment.var);
        new Analyzer<>(new BasicVerifier()).analyze(caller.name, method);
    }

    @Test
    void geckoLibColourFloatsArePackedIntoOneInt() {
        ClassNode n = new ClassNode();
        n.version = V17;
        n.access = ACC_PUBLIC;
        n.name = "mod/Render";
        n.superName = "java/lang/Object";
        String oldDesc = "(Lcom/mojang/blaze3d/vertex/PoseStack;Lsoftware/bernie/geckolib/cache/object/GeoBone;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V";
        MethodNode m = new MethodNode(ACC_PUBLIC | ACC_STATIC, "go", "(Lsoftware/bernie/geckolib/renderer/GeoEntityRenderer;)V", null, null);
        m.instructions.add(new VarInsnNode(ALOAD, 0));
        m.instructions.add(new InsnNode(ACONST_NULL));
        m.instructions.add(new InsnNode(ACONST_NULL));
        m.instructions.add(new InsnNode(ACONST_NULL));
        m.instructions.add(new InsnNode(ICONST_0));
        m.instructions.add(new InsnNode(ICONST_0));
        for (int i = 0; i < 4; i++) m.instructions.add(new InsnNode(FCONST_1));
        m.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "software/bernie/geckolib/renderer/GeoEntityRenderer", "renderCubesOfBone", oldDesc, false));
        m.instructions.add(new InsnNode(RETURN));
        m.maxStack = 10;
        m.maxLocals = 1;
        n.methods.add(m);
        Mc1201Migration.apply(n, new Mc1201Hierarchy());
        MethodInsnNode pack = null;
        MethodInsnNode call = null;
        for (var in : m.instructions) {
            if (in instanceof MethodInsnNode c) {
                if (c.name.equals("pack")) pack = c;
                else call = c;
            }
        }
        assertNotNull(pack, "the four floats are packed");
        assertEquals("(Lcom/mojang/blaze3d/vertex/PoseStack;Lsoftware/bernie/geckolib/cache/object/GeoBone;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", call.desc);
    }

    @Test
    void nativeLibraryJarIsSkippedOnlyWhenTheModernBuildIsInstalled() {
        LegacyJar jar = new LegacyJar(Path.of("architectury-9.2.14-forge.jar"));
        jar.version = LegacyVersion.V1_20_1;
        LegacyModDecl mod = new LegacyModDecl();
        mod.legacyId = "architectury";
        jar.mods.add(mod);
        assertNull(NativeLibraries1201.replacing(jar, Set.of()));
        assertEquals("architectury", NativeLibraries1201.replacing(jar, Set.of("architectury")).modernModId());
        mod.legacyId = "somecontentmod";
        assertNull(NativeLibraries1201.replacing(jar, Set.of("somecontentmod")), "only libraries of the table");
        assertTrue(NativeLibraries1201.table().containsKey("curios"));
        assertTrue(Files.exists(Path.of(".")));
    }
}
