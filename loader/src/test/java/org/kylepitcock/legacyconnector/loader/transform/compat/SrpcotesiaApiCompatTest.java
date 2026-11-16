package org.kylepitcock.legacyconnector.loader.transform.compat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.kylepitcock.legacyconnector.loader.LegacyJar;
import org.kylepitcock.legacyconnector.loader.LegacyModDecl;
import org.kylepitcock.legacyconnector.loader.LegacyVersion;
import org.kylepitcock.legacyconnector.loader.mapping.McpMappings;
import org.kylepitcock.legacyconnector.loader.shim.ClassInfo;
import org.kylepitcock.legacyconnector.loader.transform.LegacyRemapper;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;

class SrpcotesiaApiCompatTest {
    private static final String OLD_BIOME = "com/dhanantry/scapeandrunparasites/world/biome/BiomeParasite";
    private static final String BIOME_BASE = "com/dhanantry/scapeandrunparasites/world/biome/BiomeParasiteBase";
    private static final String BIOME_DECORATOR = "com/dhanantry/scapeandrunparasites/world/biome/BiomeParasiteDecorator";
    private static final String PARASITE_EVENT_WORLD = "com/dhanantry/scapeandrunparasites/util/ParasiteEventWorld";
    private static final String PARASITE_EVENT_WORLD_MIXIN = "com/srpcotesia/mixin/ParasiteEventWorldMixin";
    private static final String BLOCK_PARASITE_SPREADING = "com/dhanantry/scapeandrunparasites/block/BlockParasiteSpreading";
    private static final String BECKON_BLOCK_INFESTATION = "com/dhanantry/scapeandrunparasites/util/convert/BeckonBlockInfestation";
    private static final String SPREADING_MIXIN = "com/srpcotesia/mixin/compat/LegacyParasiteSpreadingMixin";
    private static final String BECKON_INFESTATION_MIXIN = "com/srpcotesia/mixin/compat/LegacyBeckonInfestationMixin";
    private static final String ENTITY_AI_BLOCK_INFEST = "com/dhanantry/scapeandrunparasites/entity/ai/EntityAIBlockInfest";
    private static final String ENTITY_AI_BLOCK_INFEST_MIXIN = "com/srpcotesia/mixin/entity/ai/EntityAIBlockInfestMixin";
    private static final String CAN_INFEST_DESC = "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Ljava/util/Random;IZ)V";
    private static final String SPREAD_METHOD_DESC = "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Ljava/util/Random;)V";
    private static final String SALT_DECORATOR = "com/srpcotesia/biomes/BiomeSaltDecorator";
    private static final String BASE_CTOR = "(L" + BIOME_BASE + ";)V";
    private static final String SRP_SAVE_DATA = "com/dhanantry/scapeandrunparasites/world/SRPSaveData";
    private static final String ENTITY_PARASITE = "com/dhanantry/scapeandrunparasites/entity/ai/misc/EntityParasiteBase";
    private static final String PARASITE_EVENT_ENTITY = "com/dhanantry/scapeandrunparasites/util/ParasiteEventEntity";
    private static final String PARASITE_SUMMON = "com/dhanantry/scapeandrunparasites/util/spawn/ParasiteSummon";
    private static final String ENTITY_P_INFECTED = "com/dhanantry/scapeandrunparasites/entity/ai/misc/EntityPInfected";
    private static final String ENTITY_P_FERAL = "com/dhanantry/scapeandrunparasites/entity/ai/misc/EntityPFeral";
    private static final String ENTITY_PMALLEABLE = "com/dhanantry/scapeandrunparasites/entity/ai/misc/EntityPMalleable";
    private static final String OLD_SCARY_ORB = "com/dhanantry/scapeandrunparasites/entity/EntityScaryOrb";
    private static final String CURRENT_SCARY_ORB = "com/dhanantry/scapeandrunparasites/entity/EntityOrbScary";
    private static final String ENTITY_NADE = "com/dhanantry/scapeandrunparasites/entity/projectile/EntityNade";
    private static final String ENTITY_NADE_MIXIN = "com/srpcotesia/mixin/entity/EntityNadeMixin";
    private static final String ENTITY_ROF = "com/dhanantry/scapeandrunparasites/entity/monster/deterrent/EntityRof";
    private static final String ENTITY_ROF_MIXIN = "com/srpcotesia/mixin/entity/EntityRofMixin";
    private static final String ENTITY_P_INFECTED_MIXIN = "com/srpcotesia/mixin/entity/EntityPInfectedMixin";
    private static final String ENTITY_AI_INFECTED_SEARCH = "com/dhanantry/scapeandrunparasites/entity/ai/EntityAIInfectedSearch";
    private static final String ENTITY_AI_INFECTED_SEARCH_MIXIN = "com/srpcotesia/mixin/entity/ai/EntityAIInfectedSearchMixin";
    private static final String ENTITY_AI_BASE = "net/minecraft/entity/ai/EntityAIBase";
    private static final String ENTITY_AI_TARGET = "net/minecraft/entity/ai/EntityAITarget";
    private static final String ENTITY_AI_NEAREST_TARGET = "com/dhanantry/scapeandrunparasites/entity/ai/EntityAINearestAttackableTargetStatus";
    private static final String ENTITY_AI_NEAREST_TARGET_MIXIN = "com/srpcotesia/mixin/entity/ai/EntityAINearestAttackableTargetStatusMixin";
    private static final String ENTITY_CREATURE = "net/minecraft/entity/EntityCreature";
    private static final String NEAREST_TARGET_HANDLER = "srpcotesia$restrictedSight";
    private static final String ENTITY_ELVIA = "com/dhanantry/scapeandrunparasites/entity/monster/pure/preeminent/EntityElvia";
    private static final String I_PARASITE = "com/srpcotesia/entity/parasites/IParasite";
    private static final String POTION_ANTIBODIES = "com/srpcotesia/potion/PotionAntibodies";
    private static final String ENTITY_LIST = "net/minecraft/entity/EntityList";
    private static final String SUMMON_MIXIN = "com/srpcotesia/mixin/compat/LegacyParasiteSummonMixin";
    private static final String SUMMON_FACTORY_DESC = "(Lnet/minecraft/util/ResourceLocation;Lnet/minecraft/world/World;)Lnet/minecraft/entity/Entity;";
    private static final String OLD_SUMMON_M_DESC = "(L" + ENTITY_PARASITE + ";[Ljava/lang/String;ILnet/minecraft/entity/EntityLivingBase;)Z";
    private static final String NEW_SUMMON_M_DESC = "(Lnet/minecraft/entity/EntityLivingBase;[Ljava/lang/String;IILnet/minecraft/entity/EntityLivingBase;)Z";
    private static final String SUMMON_M_TWO_DESC = "(L" + ENTITY_PARASITE + ";[Ljava/lang/String;IDDDLnet/minecraft/entity/EntityLivingBase;Z)Z";
    private static final String OLD_SUMMON_M_HANDLER_DESC = "(Lnet/minecraft/entity/Entity;L" + ENTITY_PARASITE + ";[Ljava/lang/String;ILnet/minecraft/entity/EntityLivingBase;)Lnet/minecraft/entity/Entity;";
    private static final String SUMMON_M_TWO_HANDLER_DESC = "(Lnet/minecraft/entity/Entity;L" + ENTITY_PARASITE + ";[Ljava/lang/String;IDDDLnet/minecraft/entity/EntityLivingBase;Z)Lnet/minecraft/entity/Entity;";
    private static final String OLD_GET_FERAL_DESC = "()L" + ENTITY_P_FERAL + ";";
    private static final String NEW_GET_FERAL_DESC = "(Lnet/minecraft/world/World;)L" + ENTITY_P_FERAL + ";";
    private static final String CONVERT_ENTITY_FERAL_DESC = "(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/nbt/NBTTagCompound;Z[Ljava/lang/String;)Z";
    private static final String GET_FERAL_HANDLER = "srpcotesia$convertEntityFeral$getFeral";
    private static final String PARASITE_BASE_MIXIN = "com/srpcotesia/mixin/entity/EntityParasiteBaseMixin";
    private static final String APPLY_BONUSES_HANDLER = "srpcotesia$applyBonuses";
    private static final String BLOCK_MINI_DAMAGE_HANDLER = "srpcotesia$blockMiniDamage";
    private static final String MIN_DAMAGE_HANDLER = "srpcotesia$minDamageMixin";
    private static final String CALLBACK_INFO = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private static final String CALLBACK_INFO_RETURNABLE = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
    private static final String LOCAL_FLOAT_REF = "com/llamalad7/mixinextras/sugar/ref/LocalFloatRef";
    private static final String LOCAL_ANNOTATION = "Lcom/llamalad7/mixinextras/sugar/Local;";
    private static final String OLD_APPLY_BONUSES_HANDLER_DESC = "(IIBL" + CALLBACK_INFO + ";)V";
    private static final String OLD_BLOCK_MINI_DAMAGE_HANDLER_DESC = "(Lnet/minecraft/entity/EntityLivingBase;L" + CALLBACK_INFO_RETURNABLE + ";L" + LOCAL_FLOAT_REF + ";)V";
    private static final String OLD_MIN_DAMAGE_HANDLER_DESC = "(Lnet/minecraft/entity/EntityLivingBase;L" + CALLBACK_INFO_RETURNABLE + ";)V";
    private static final String WORLD = "net/minecraft/world/World";
    private static final String SRP_BLOCKS = "com/dhanantry/scapeandrunparasites/init/SRPBlocks";
    private static final String BLOCK_GORE = "com/dhanantry/scapeandrunparasites/block/BlockGore";
    private static final String GORE_ENUM = BLOCK_GORE + "$EnumType";
    private static final String GORE_DESC = "L" + BLOCK_GORE + ";";
    private static final String GORE_ENUM_DESC = "L" + GORE_ENUM + ";";
    private static final String PARASITE_PLAYER = "com/srpcotesia/capability/ParasitePlayer";
    private static final String GORE_BLOCK_HELPER = "mcc$legacyGoreBlock";
    private static final String[] GORE_FAMILIES = {"SIM", "PRI", "ADA", "PURE"};
    private static final String OLD_GET = "(L" + WORLD + ";)L" + SRP_SAVE_DATA + ";";
    private static final String NEW_GET = "(L" + WORLD + ";I)L" + SRP_SAVE_DATA + ";";
    private static final String OLD_SET_COOLDOWN = "(IL" + WORLD + ";I)V";
    private static final String NEW_SET_COOLDOWN = "(IL" + WORLD + ";IZ)V";
    private static final String OLD_SET_PHASE = "(IBZL" + WORLD + ";Z)Z";
    private static final String NEW_SET_PHASE = "(IBZL" + WORLD + ";)Z";
    private static final String OLD_SET_TOTAL_KILLS = "(IIZL" + WORLD + ";Z)Z";
    private static final String NEW_SET_TOTAL_KILLS = "(IIZL" + WORLD + ";ZI)Z";
    private static final String OLD_DESPAWN_REDIRECT = "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";Z)Z";
    private static final String NEW_DESPAWN_REDIRECT = "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";ZI)Z";
    private static final String OLD_DESPAWN_TARGET = "L" + SRP_SAVE_DATA + ";setTotalKills" + OLD_SET_TOTAL_KILLS;
    private static final String NEW_DESPAWN_TARGET = "L" + SRP_SAVE_DATA + ";setTotalKills" + NEW_SET_TOTAL_KILLS;
    private static final String DESPAWN_MIXIN = "com/srpcotesia/mixin/entity/EntityPAdaptedMixin";
    private static final String DESPAWN_HANDLER = "srpcotesia$onDespawnMixin";
    private static final String MERGE_MIXIN = "com/srpcotesia/mixin/ParasiteEventEntityMixin";
    private static final String MERGE_HANDLER = "srpcotesia$merge";
    private static final String OLD_MERGE_HANDLER_DESC = "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";ZLcom/llamalad7/mixinextras/injector/wrapoperation/Operation;L" + ENTITY_PARASITE + ";ILjava/lang/String;)Z";
    private static final String NEW_MERGE_HANDLER_DESC = "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";ZILcom/llamalad7/mixinextras/injector/wrapoperation/Operation;L" + ENTITY_PARASITE + ";ILjava/lang/String;)Z";
    private static final String OLD_MERGE_HANDLER_SIGNATURE = "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";ZLcom/llamalad7/mixinextras/injector/wrapoperation/Operation"
            + "<Ljava/lang/Boolean;>;L" + ENTITY_PARASITE + ";ILjava/lang/String;)Z";
    private static final String NEW_MERGE_HANDLER_SIGNATURE = "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";ZILcom/llamalad7/mixinextras/injector/wrapoperation/Operation"
            + "<Ljava/lang/Boolean;>;L" + ENTITY_PARASITE + ";ILjava/lang/String;)Z";
    private static final String EFFECT_BASE = "com/dhanantry/scapeandrunparasites/potion/SRPEffectBase";
    private static final String EFFECT_MIXIN = "com/srpcotesia/mixin/SRPEffectBaseMixin";
    private static final String OPERATION = "com/llamalad7/mixinextras/injector/wrapoperation/Operation";
    private static final String WRAP_METHOD = "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;";
    private static final String NEAREST_TARGET_METHOD_DESC = "(Lnet/minecraft/entity/EntityLivingBase;Z)Z";
    private static final String NEAREST_TARGET_WRAP_DESC = "(Lnet/minecraft/entity/EntityLivingBase;ZL" + OPERATION + ";)Z";
    private static final String PARTICLE_MIXIN = "com/srpcotesia/mixin/compat/LegacySrpParticleMixin";
    private static final String PARTICLE_ENUM = "com/dhanantry/scapeandrunparasites/client/particle/SRPEnumParticle";
    private static final String PARTICLE_PACKET = "com/dhanantry/scapeandrunparasites/network/SRPPacketParticle";
    private static final String PARTICLE_HANDLER = PARTICLE_PACKET + "$Handler";
    private static final String PARTICLE_INJECT_HANDLER = "srpcotesia$spawnParticles";
    private static final String PARTICLE_INJECT_OLD_DESC = "(L" + PARTICLE_ENUM + ";Ljava/util/Random;Lnet/minecraft/entity/EntityLivingBase;L" + CALLBACK_INFO + ";)V";
    private static final String PARTICLE_INJECT_NEW_DESC = "(L" + PARTICLE_ENUM + ";Ljava/util/Random;L" + PARTICLE_PACKET + ";IIIL" + CALLBACK_INFO + ";)V";
    private static final String PARTICLE_WRAP_OLD_DESC = "(Ljava/util/Random;L" + OPERATION + ";L" + PARTICLE_ENUM + ";Ljava/util/Random;Lnet/minecraft/entity/EntityLivingBase;)F";
    private static final String PARTICLE_WRAP_NEW_DESC = "(Ljava/util/Random;L" + OPERATION + ";L" + PARTICLE_ENUM + ";Ljava/util/Random;L" + PARTICLE_PACKET + ";III)F";
    private static final String POTION_COTH = "com/dhanantry/scapeandrunparasites/potion/PotionCOTH";
    private static final String POTION_NEEDLER = "com/dhanantry/scapeandrunparasites/potion/PotionNeedler";
    private static final String COTH_MIXIN = "com/srpcotesia/mixin/compat/LegacyCothEffectsMixin";
    private static final String NEEDLER_MIXIN = "com/srpcotesia/mixin/compat/LegacyNeedlerEffectMixin";
    private static final String SOURCE_ID_OPERATION = "com/srpcotesia/compat/SourceIdOperation";
    private static final String SPAWN_BECKON_DESC = "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;I)V";
    private static final String ENTITY_VENKROL = "com/dhanantry/scapeandrunparasites/entity/monster/deterrent/nexus/EntityVenkrol";
    private static final String OLD_VENKROL_INJECT_DESC = "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Ljava/util/Random;IZL" + CALLBACK_INFO + ";L" + ENTITY_VENKROL + ";)V";
    private static final String NEW_VENKROL_INJECT_DESC = "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;IL" + CALLBACK_INFO + ";L" + ENTITY_VENKROL + ";)V";
    private static final String OLD_MOVED_KILLS_HANDLER_DESC = "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";ZL" + OPERATION + ";L" + WORLD + ";Lnet/minecraft/util/math/BlockPos;Ljava/util/Random;IZ)Z";
    private static final String NEW_MOVED_KILLS_HANDLER_DESC = "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";ZIL" + OPERATION + ";L" + WORLD + ";Lnet/minecraft/util/math/BlockPos;Ljava/util/Random;IZ)Z";
    private static final String OLD_MOVED_KILLS_HANDLER_SIGNATURE = "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";ZL" + OPERATION
            + "<Ljava/lang/Boolean;>;L" + WORLD + ";Lnet/minecraft/util/math/BlockPos;Ljava/util/Random;IZ)Z";
    private static final String NEW_MOVED_KILLS_HANDLER_SIGNATURE = "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";ZIL" + OPERATION
            + "<Ljava/lang/Boolean;>;L" + WORLD + ";Lnet/minecraft/util/math/BlockPos;Ljava/util/Random;IZ)Z";
    private static final String COTH_SPAWN_HANDLER = "srpcotesia$effectCOTH$spawnInsider";
    private static final String COTH_KILLS_HANDLER = "srpcotesia$effectCOTH$setTotalKills";
    private static final String INFECT_HANDLER = "srpcotesia$InfectNearby";
    private static final String NEEDLER_HANDLER = "srpcotesia$effectNeedler";
    private static final String COTH_TRANSFORM_DESC = "(Lnet/minecraft/entity/EntityLivingBase;IZZZ)Z";
    private static final String COTH_EXTEND_DESC = "(IILnet/minecraft/entity/EntityLivingBase;IZZZ)V";
    private static final String OLD_COTH_SPAWN_DESC = "(Lnet/minecraft/entity/EntityLivingBase;ILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    private static final String NEW_COTH_SPAWN_DESC = "(Lnet/minecraft/entity/EntityLivingBase;IZZZLorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V";
    private static final String OLD_COTH_KILLS_DESC = "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";ZLcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lnet/minecraft/entity/EntityLivingBase;I)Z";
    private static final String NEW_COTH_KILLS_DESC = "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";ZILcom/llamalad7/mixinextras/injector/wrapoperation/Operation;IILnet/minecraft/entity/EntityLivingBase;IZZZ)Z";
    private static final String NEEDLER_HANDLER_DESC = "(Lnet/minecraft/entity/EntityLivingBase;ILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    private static final String INFECT_TARGET = "Lnet/minecraft/entity/EntityLivingBase;addPotionEffect(Lnet/minecraft/potion/PotionEffect;)V";
    private static final String SPAWN_INSIDER_TARGET = "Lcom/dhanantry/scapeandrunparasites/util/ParasiteEventEntity;spawnInsider(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/world/World;Lnet/minecraft/nbt/NBTTagCompound;)V";

    @Test
    void mapsLegacyCotesiaGoreVariantsToSplitFamilyBlocks() throws Exception {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(PARASITE_PLAYER, oldParasitePlayerGore());
        LegacyRemapper remapper = newRemapper();

        assertEquals(11, SrpcotesiaApiCompat.apply(cotesiaJar(), classes, currentGoreApiPeers(), remapper));

        ClassNode adapted = readNode(classes.get(PARASITE_PLAYER));
        MethodNode spawn = findMethod(adapted, "spawnGore",
                "(Lnet/minecraft/entity/player/EntityPlayer;L" + WORLD + ";)V");
        assertNotNull(spawn);
        assertEquals(0, countFieldReads(spawn, SRP_BLOCKS, "Gore"));
        assertEquals(2, countCalls(spawn, PARASITE_PLAYER, GORE_BLOCK_HELPER));
        assertEquals(List.of("FLAT", "FLAT", "FLAT", "FLAT"),
                enumFieldNames(findMethod(adapted, "getGoreFlat", "()" + GORE_ENUM_DESC)));
        assertEquals(List.of("BIG", "BIG", "BIG", "BIG"),
                enumFieldNames(findMethod(adapted, "getGoreBig", "()" + GORE_ENUM_DESC)));

        MethodNode helper = findMethod(adapted, GORE_BLOCK_HELPER, "()" + GORE_DESC);
        assertNotNull(helper);
        List<String> selectedBlocks = new ArrayList<>();
        for (var instruction : helper.instructions) {
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                    && field.owner.equals(SRP_BLOCKS)) selectedBlocks.add(field.name);
        }
        assertEquals(List.of("goreSim", "gorePri", "goreAda", "gorePur"), selectedBlocks);
        for (MethodNode method : adapted.methods) {
            new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, method);
        }
    }

    @Test
    void adaptsRemovedBiomeTypeAndSaltDecoratorToTheCurrentPeerApi() {
        LegacyJar jar = cotesiaJar();
        Map<String, ClassInfo> peers = currentApiPeers();
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(SALT_DECORATOR, oldSaltDecorator());
        LegacyRemapper remapper = newRemapper();

        assertEquals(2, SrpcotesiaApiCompat.apply(jar, classes, peers, remapper));
        assertEquals(BIOME_BASE, remapper.map(OLD_BIOME));

        ClassNode adapted = new ClassNode();
        new ClassReader(classes.get(SALT_DECORATOR)).accept(adapted, 0);
        var constructor = adapted.methods.stream().filter(method -> method.name.equals("<init>")).findFirst().orElseThrow();
        MethodInsnNode superCall = null;
        for (var instruction : constructor.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(BIOME_DECORATOR)) superCall = call;
        }
        assertNotNull(superCall);
        assertEquals(BASE_CTOR, superCall.desc);
        assertEquals(Opcodes.ACONST_NULL, superCall.getPrevious().getOpcode());
        assertEquals(2, constructor.maxStack);
    }

    @Test
    void mapsCotesiaScaryOrbNameWhenTheCurrentSrpPeerMatchesItsMixinApi() {
        LegacyRemapper remapper = newRemapper();
        Map<String, ClassInfo> peers = Map.of(CURRENT_SCARY_ORB, ClassInfo.of(currentScaryOrbClass()));

        assertEquals(1, SrpcotesiaApiCompat.apply(cotesiaJar(), new LinkedHashMap<>(), peers, remapper));
        assertEquals(CURRENT_SCARY_ORB, remapper.map(OLD_SCARY_ORB));
    }

    @Test
    void leavesScaryOrbNameUnmappedWhenThePeerIsIncompleteOrLegacyClassExists() {
        ClassNode incomplete = currentScaryOrbClass();
        incomplete.fields.removeIf(field -> field.name.equals("poosZ"));
        LegacyRemapper remapper = newRemapper();
        assertEquals(0, SrpcotesiaApiCompat.apply(cotesiaJar(), new LinkedHashMap<>(),
                Map.of(CURRENT_SCARY_ORB, ClassInfo.of(incomplete)), remapper));
        assertEquals(OLD_SCARY_ORB, remapper.map(OLD_SCARY_ORB));

        ClassNode oldOrb = currentScaryOrbClass();
        oldOrb.name = OLD_SCARY_ORB;
        remapper = newRemapper();
        assertEquals(0, SrpcotesiaApiCompat.apply(cotesiaJar(), new LinkedHashMap<>(),
                Map.of(OLD_SCARY_ORB, ClassInfo.of(oldOrb), CURRENT_SCARY_ORB, ClassInfo.of(currentScaryOrbClass())), remapper));
        assertEquals(OLD_SCARY_ORB, remapper.map(OLD_SCARY_ORB));
    }

    @Test
    void repairsCotesiaAssetReferencesForTheCurrentSrpPeer() {
        Map<String, byte[]> resources = new LinkedHashMap<>();
        byte[] inactiveSaltine = new byte[] {1, 2, 3};
        resources.put("assets/srpcotesia/textures/blocks/saltine_inactive.png", inactiveSaltine);
        String model = "{\"textures\":{\"particle\":\"srparasites:blocks/parasitetrunk_tree_side\","
                + "\"down\":\"srparasites:blocks/parasitetrunk_tree_down\",\"up\":\"srpcotesia:blocks/inf_crafting_table_up\"}}";
        resources.put("assets/srpcotesia/models/block/inf_crafting_table.json", model.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Map<String, ClassInfo> peers = new LinkedHashMap<>(currentApiPeers());
        ClassNode oldBiome = new ClassNode();
        oldBiome.name = OLD_BIOME;
        peers.put(OLD_BIOME, ClassInfo.of(oldBiome));

        assertEquals(2, SrpcotesiaApiCompat.apply(cotesiaJar(), new LinkedHashMap<>(), resources, peers, newRemapper()));
        assertArrayEquals(inactiveSaltine, resources.get("assets/srpcotesia/textures/blocks/saltine.png"));
        var adapted = com.google.gson.JsonParser.parseString(new String(resources.get("assets/srpcotesia/models/block/inf_crafting_table.json"),
                java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("srparasites:blocks/parasitetrunk_tree", adapted.getAsJsonObject("textures").get("down").getAsString());
        assertEquals("srparasites:blocks/parasitetrunk_tree_side", adapted.getAsJsonObject("textures").get("particle").getAsString());
    }

    @Test
    void leavesCotesiaAssetsAloneForOtherModsAndDoesNotGuessOldSrpAssets() {
        Map<String, byte[]> resources = new LinkedHashMap<>();
        byte[] modelBytes = "{\"textures\":{\"down\":\"srparasites:blocks/parasitetrunk_tree_down\"}}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        resources.put("assets/srpcotesia/textures/blocks/saltine_inactive.png", new byte[] {4, 5, 6});
        resources.put("assets/srpcotesia/models/block/inf_crafting_table.json", modelBytes);

        LegacyJar oldPeerJar = cotesiaJar();
        assertEquals(1, SrpcotesiaApiCompat.apply(oldPeerJar, new LinkedHashMap<>(), resources, Map.of(), newRemapper()));
        assertFalse(resources.containsKey("assets/srparasites/textures/blocks/parasitetrunk_tree_down.png"));
        var unchanged = com.google.gson.JsonParser.parseString(new String(resources.get("assets/srpcotesia/models/block/inf_crafting_table.json"),
                java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("srparasites:blocks/parasitetrunk_tree_down", unchanged.getAsJsonObject("textures").get("down").getAsString());

        LegacyJar otherJar = new LegacyJar(Path.of("other.jar"));
        otherJar.version = LegacyVersion.V1_12_2;
        LegacyModDecl otherMod = new LegacyModDecl();
        otherMod.legacyId = "othermod";
        otherJar.mods.add(otherMod);
        resources.remove("assets/srpcotesia/textures/blocks/saltine.png");
        assertEquals(0, SrpcotesiaApiCompat.apply(otherJar, new LinkedHashMap<>(), resources, currentApiPeers(), newRemapper()));
        assertFalse(resources.containsKey("assets/srpcotesia/textures/blocks/saltine.png"));
    }

    @Test
    void leavesOtherModsAndTheOldPeerApiUntouched() {
        LegacyJar jar = new LegacyJar(Path.of("other.jar"));
        jar.version = LegacyVersion.V1_12_2;
        LegacyModDecl mod = new LegacyModDecl();
        mod.legacyId = "othermod";
        jar.mods.add(mod);
        Map<String, byte[]> classes = new LinkedHashMap<>();
        byte[] original = oldSaltDecorator();
        classes.put(SALT_DECORATOR, original);
        LegacyRemapper remapper = newRemapper();

        assertEquals(0, SrpcotesiaApiCompat.apply(jar, classes, currentApiPeers(), remapper));
        assertEquals(OLD_BIOME, remapper.map(OLD_BIOME));
        assertEquals(original, classes.get(SALT_DECORATOR));

        jar = cotesiaJar();
        Map<String, ClassInfo> oldPeers = new LinkedHashMap<>(currentApiPeers());
        ClassNode oldBiome = new ClassNode();
        oldBiome.name = OLD_BIOME;
        oldPeers.put(OLD_BIOME, ClassInfo.of(oldBiome));
        classes = new LinkedHashMap<>();
        classes.put(SALT_DECORATOR, original);
        remapper = newRemapper();
        assertEquals(1, SrpcotesiaApiCompat.apply(jar, classes, oldPeers, remapper));
        assertEquals(OLD_BIOME, remapper.map(OLD_BIOME));
        assertEquals(BASE_CTOR, superCall(classes.get(SALT_DECORATOR)).desc);
    }

    @Test
    void adaptsCotesiaParasiteHandlersToTheCurrentSrpMethodArguments() throws Exception {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(PARASITE_BASE_MIXIN, oldParasiteBaseMixin());
        LegacyRemapper remapper = newRemapper();
        ClassNode parasiteBase = new ClassNode(Opcodes.ASM9);
        parasiteBase.name = ENTITY_PARASITE;
        parasiteBase.superName = "java/lang/Object";
        parasiteBase.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "applyBonuses",
                remapper.mapMethodDesc("(L" + SRP_SAVE_DATA + ";L" + WORLD + ";)V"), null, null));
        parasiteBase.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "attackEntityAsMobMinimum",
                remapper.mapMethodDesc("(Lnet/minecraft/entity/EntityLivingBase;F)Z"), null, null));
        Map<String, ClassInfo> peers = Map.of(ENTITY_PARASITE, ClassInfo.of(parasiteBase));

        assertEquals(3, SrpcotesiaApiCompat.apply(cotesiaJar(), classes, peers, remapper));

        ClassNode adapted = readNode(classes.get(PARASITE_BASE_MIXIN));
        MethodNode apply = findMethod(adapted, APPLY_BONUSES_HANDLER, "(L" + CALLBACK_INFO + ";)V");
        MethodNode blockDamage = findMethod(adapted, BLOCK_MINI_DAMAGE_HANDLER,
                "(Lnet/minecraft/entity/EntityLivingBase;FL" + CALLBACK_INFO_RETURNABLE + ";L" + LOCAL_FLOAT_REF + ";)V");
        MethodNode minimumDamage = findMethod(adapted, MIN_DAMAGE_HANDLER,
                "(Lnet/minecraft/entity/EntityLivingBase;FL" + CALLBACK_INFO_RETURNABLE + ";)V");
        assertNotNull(apply);
        assertNotNull(blockDamage);
        assertNotNull(minimumDamage);
        assertEquals(3, apply.maxLocals);
        assertEquals(6, blockDamage.maxLocals);
        assertEquals(4, minimumDamage.maxLocals);
        assertEquals("(Lnet/minecraft/entity/EntityLivingBase;FL" + CALLBACK_INFO_RETURNABLE
                + "<Ljava/lang/Boolean;>;L" + LOCAL_FLOAT_REF + ";)V", blockDamage.signature);
        assertEquals("(Lnet/minecraft/entity/EntityLivingBase;FL" + CALLBACK_INFO_RETURNABLE
                + "<Ljava/lang/Boolean;>;)V", minimumDamage.signature);
        assertEquals(2, localOrdinal(blockDamage));
        assertEquals(3, parameterIndex(blockDamage, LOCAL_ANNOTATION));
        assertEquals(List.of(2), localIndexes(apply, "dendr"));
        assertEquals(List.of(1, 3, 4, 5), localIndexes(blockDamage, "target", "cir", "damage", "after"));
        assertEquals(List.of(3), variableIndexes(minimumDamage, Opcodes.ALOAD));
        for (MethodNode method : List.of(apply, blockDamage, minimumDamage)) {
            new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, method);
        }
    }

    @Test
    void movesParasiteSpreadingInjectorsToTheCurrentPeerOwners() {
        LegacyRemapper remapper = newRemapper();
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(PARASITE_EVENT_WORLD_MIXIN, oldParasiteEventWorldMixin());
        classes.put(ENTITY_AI_BLOCK_INFEST_MIXIN, oldEntityAiBlockInfestMixin());
        Map<String, byte[]> resources = new LinkedHashMap<>();
        resources.put("mixins.srpcotesia.json", "{\"package\":\"com.srpcotesia.mixin\",\"mixins\":[\"ParasiteEventWorldMixin\",\"SRPEffectBaseMixin\"]}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertEquals(7, SrpcotesiaApiCompat.apply(cotesiaJar(), classes, resources, movedParasitePeers(remapper), remapper));

        ClassNode worldMixin = readNode(classes.get(PARASITE_EVENT_WORLD_MIXIN));
        assertFalse(worldMixin.methods.stream().anyMatch(method -> method.name.equals("srpcotesia$canInfestBlock")
                && methodAnnotation(method, "Lorg/spongepowered/asm/mixin/injection/Inject;") != null));
        assertTrue(worldMixin.methods.stream().anyMatch(method -> method.name.equals("srpcotesia$blockException")));
        assertTrue(worldMixin.methods.stream().anyMatch(method -> method.name.equals("srpcotesia$SpreadBiome")));

        ClassNode spreadingMixin = readNode(classes.get(SPREADING_MIXIN));
        assertMixinTarget(spreadingMixin, BLOCK_PARASITE_SPREADING);
        assertEquals(2, spreadingMixin.methods.size());
        assertTrue(spreadingMixin.methods.stream().anyMatch(method -> method.name.equals("srpcotesia$spreadBiomeBlockStain")));
        assertTrue(spreadingMixin.methods.stream().anyMatch(method -> method.name.equals("srpcotesia$spreadBiomeBlockTrunk")));

        ClassNode beckonMixin = readNode(classes.get(BECKON_INFESTATION_MIXIN));
        assertMixinTarget(beckonMixin, BECKON_BLOCK_INFESTATION);
        assertEquals(2, beckonMixin.methods.size());
        MethodNode movedInjector = beckonMixin.methods.stream().filter(method -> method.desc.equals(NEW_VENKROL_INJECT_DESC))
                .findFirst().orElseThrow();
        AnnotationNode movedInject = methodAnnotation(movedInjector, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        assertEquals(List.of("spawnBeckonFromInfestation"), annotationValue(movedInject, "method"));
        assertTrue(annotationHasTarget(movedInject, "L" + ENTITY_VENKROL + ";cannotDespawn(Z)V"));
        assertEquals(4, parameterIndex(movedInjector, LOCAL_ANNOTATION));
        assertEquals(List.of(4, 5), variableIndexes(movedInjector, Opcodes.ALOAD));
        assertTrue(variableIndexes(movedInjector, Opcodes.ILOAD).isEmpty());
        assertEquals(List.of(2, 3, 4, 5), localIndexes(movedInjector, "stage", "ci", "out", "possible"));

        MethodNode killsWrapper = beckonMixin.methods.stream().filter(method -> method.desc.equals(NEW_MOVED_KILLS_HANDLER_DESC))
                .findFirst().orElseThrow();
        assertEquals("srpcotesia$canInfestBlock", killsWrapper.name);
        assertEquals(NEW_MOVED_KILLS_HANDLER_DESC, killsWrapper.desc);
        assertEquals(NEW_MOVED_KILLS_HANDLER_SIGNATURE, killsWrapper.signature);
        AnnotationNode wrapper = methodAnnotation(killsWrapper,
                "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");
        assertEquals(List.of("beckonInfestation"), annotationValue(wrapper, "method"));
        assertTrue(annotationHasTarget(wrapper, "L" + SRP_SAVE_DATA + ";setTotalKills" + NEW_SET_TOTAL_KILLS));
        assertTrue(hasMethodCall(killsWrapper, SOURCE_ID_OPERATION, "<init>"));
        assertTrue(classes.containsKey(SOURCE_ID_OPERATION));

        ClassNode aiMixin = readNode(classes.get(ENTITY_AI_BLOCK_INFEST_MIXIN));
        MethodNode aiHandler = aiMixin.methods.get(0);
        AnnotationNode inject = methodAnnotation(aiHandler, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        Object atValue = annotationValue(inject, "at");
        assertTrue(atValue instanceof List<?> list && list.get(0) instanceof AnnotationNode at
                && ("L" + BLOCK_PARASITE_SPREADING + ";canInfestBlock" + remapper.mapMethodDesc(CAN_INFEST_DESC))
                        .equals(annotationValue(at, "target")));
        String config = new String(resources.get("mixins.srpcotesia.json"), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(config.contains("compat.LegacyParasiteSpreadingMixin"));
        assertTrue(config.contains("compat.LegacyBeckonInfestationMixin"));
    }

    @Test
    void movesSummonMixinsToTheCurrentPeerOwnerAndAdaptsTheAddedArgument() {
        LegacyRemapper remapper = newRemapper();
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(MERGE_MIXIN, oldSummonMixin());
        Map<String, byte[]> resources = new LinkedHashMap<>();
        resources.put("mixins.srpcotesia.json", "{\"package\":\"com.srpcotesia.mixin\",\"mixins\":[\"ParasiteEventEntityMixin\"]}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertEquals(2, SrpcotesiaApiCompat.apply(cotesiaJar(), classes, resources, movedSummonPeers(remapper), remapper));

        ClassNode original = readNode(classes.get(MERGE_MIXIN));
        assertFalse(original.methods.stream().anyMatch(method -> method.name.equals("srpcotesia$SummonM")
                || method.name.equals("srpcotesia$SummonMTwo")));
        ClassNode moved = readNode(classes.get(SUMMON_MIXIN));
        assertMixinTarget(moved, PARASITE_SUMMON);
        MethodNode first = moved.methods.stream().filter(method -> method.name.equals("srpcotesia$SummonM")).findFirst().orElseThrow();
        MethodNode second = moved.methods.stream().filter(method -> method.name.equals("srpcotesia$SummonMTwo")).findFirst().orElseThrow();
        assertEquals("(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/EntityLivingBase;[Ljava/lang/String;IILnet/minecraft/entity/EntityLivingBase;)Lnet/minecraft/entity/Entity;",
                first.desc);
        assertEquals(6, first.maxLocals);
        assertEquals(SUMMON_M_TWO_HANDLER_DESC, second.desc);
        assertEquals(List.of("SummonM" + NEW_SUMMON_M_DESC), annotationValue(methodAnnotation(first,
                "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;"), "method"));
        String config = new String(resources.get("mixins.srpcotesia.json"), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(config.contains("compat.LegacyParasiteSummonMixin"));
    }

    @Test
    void updatesFeralGetterSelectorForTheWorldAwarePeerMethod() {
        LegacyRemapper remapper = newRemapper();
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(MERGE_MIXIN, oldFeralGetterMixin());

        assertEquals(1, SrpcotesiaApiCompat.apply(cotesiaJar(), classes, movedFeralPeers(remapper), remapper));

        ClassNode adapted = readNode(classes.get(MERGE_MIXIN));
        MethodNode handler = adapted.methods.stream().filter(method -> method.name.equals(GET_FERAL_HANDLER)).findFirst().orElseThrow();
        AnnotationNode injector = methodAnnotation(handler, "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;");
        Object atValue = annotationValue(injector, "at");
        assertTrue(atValue instanceof List<?>);
        assertEquals("L" + ENTITY_P_INFECTED + ";getFeral" + NEW_GET_FERAL_DESC,
                annotationValue((AnnotationNode) ((List<?>) atValue).get(0), "target"));
        assertEquals("convertEntityFeral", ((List<?>) annotationValue(injector, "method")).get(0));
    }

    @Test
    void adaptsRemainingCotesiaEntityMixinsToTheCurrentSrpPeerShapes() throws Exception {
        LegacyRemapper remapper = newRemapper();
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(ENTITY_P_INFECTED_MIXIN, oldInfectedMixinForCurrentApi());
        classes.put(ENTITY_AI_INFECTED_SEARCH_MIXIN, oldInfectedSearchMixin());
        classes.put(ENTITY_AI_NEAREST_TARGET_MIXIN, oldNearestTargetMixin());
        classes.put(ENTITY_ROF_MIXIN, oldEntityRofMixin());
        classes.put(ENTITY_NADE_MIXIN, oldEntityNadeMixin());
        classes.put(POTION_ANTIBODIES, oldPotionAntibodies());
        Map<String, ClassInfo> peers = currentEntityMixinPeers(remapper);
        ClassInfo nadePeer = peers.get(ENTITY_NADE);
        assertTrue(nadePeer.hasField("father", remapper.mapDesc("L" + ENTITY_PARASITE + ";")));
        assertTrue(nadePeer.hasMethod("setFatherS", remapper.mapMethodDesc("(L" + ENTITY_PARASITE + ";)V")));
        String targetMinimum = "L" + remapper.map(ENTITY_PARASITE) + ";attackEntityAsMobMinimum"
                + remapper.mapMethodDesc("(Lnet/minecraft/entity/EntityLivingBase;F)Z");
        assertEquals(1, nadePeer.instructionSiteCount("selfExplode()V", "INVOKE", targetMinimum, Opcodes.INVOKEVIRTUAL));
        ClassNode oldNade = readNode(classes.get(ENTITY_NADE_MIXIN));
        MethodNode oldMinimum = oldNade.methods.stream().filter(method -> method.name.equals("srpcotesia$selfExplodeMinimum")).findFirst().orElseThrow();
        assertTrue(annotationHasTarget(methodAnnotation(oldMinimum, "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;"),
                "L" + ENTITY_PMALLEABLE + ";attackEntityAsMobMinimum(Lnet/minecraft/entity/EntityLivingBase;)Z"));
        assertEquals(1, methodCallCount(oldNade.methods.stream().filter(method -> method.name.equals("srpcotesia$selfExplode")).findFirst().orElseThrow(),
                POTION_ANTIBODIES, "getDurationForMob"));
        assertEquals(1, methodCallCount(oldNade.methods.stream().filter(method -> method.name.equals("srpcotesia$selfExplode")).findFirst().orElseThrow(),
                POTION_ANTIBODIES, "getAmpForMob"));

        ClassInfo nearestPeerBefore = peers.get(ENTITY_AI_NEAREST_TARGET);
        assertEquals(remapper.map(ENTITY_AI_BASE), nearestPeerBefore.superName);
        assertTrue(nearestPeerBefore.hasField("taskOwner", remapper.mapDesc("L" + ENTITY_PARASITE + ";")));
        assertTrue(nearestPeerBefore.hasField("shouldCheckSight", "Z"));
        assertTrue(nearestPeerBefore.hasField("targetClass", "Ljava/lang/Class;"));
        assertTrue(nearestPeerBefore.hasField("targetEntitySelector", "Lcom/google/common/base/Predicate;"));
        assertTrue(nearestPeerBefore.hasMethod("isSuitableTarget", remapper.mapMethodDesc(NEAREST_TARGET_METHOD_DESC)));
        assertEquals("isSuitableTarget" + remapper.mapMethodDesc(NEAREST_TARGET_METHOD_DESC),
                remapper.mapString("isSuitableTarget" + NEAREST_TARGET_METHOD_DESC));
        ClassNode oldNearest = readNode(classes.get(ENTITY_AI_NEAREST_TARGET_MIXIN));
        assertMixinTarget(oldNearest, ENTITY_AI_NEAREST_TARGET);
        assertEquals(ENTITY_AI_TARGET, oldNearest.superName);
        assertTrue(oldNearest.signature.endsWith("L" + ENTITY_AI_TARGET + ";"));
        MethodNode oldSight = findMethod(oldNearest, "func_75296_a", NEAREST_TARGET_METHOD_DESC);
        assertNotNull(oldSight);
        assertTrue(hasFieldAnnotation(oldNearest.fields.stream().filter(field -> field.name.equals("targetClass")).findFirst().orElseThrow(),
                "Lorg/spongepowered/asm/mixin/Shadow;"));
        assertTrue(hasFieldAnnotation(oldNearest.fields.stream().filter(field -> field.name.equals("targetEntitySelector")).findFirst().orElseThrow(),
                "Lorg/spongepowered/asm/mixin/Shadow;"));
        assertEquals("(L" + ENTITY_CREATURE + ";Z)V", findMethod(oldNearest, "<init>", "(L" + ENTITY_CREATURE + ";Z)V").desc);
        assertEquals(1, methodCallCount(oldSight, ENTITY_AI_TARGET, "func_75296_a"));
        assertEquals(1, fieldReadCount(oldSight, ENTITY_AI_NEAREST_TARGET_MIXIN, "field_75297_f", "Z"));
        assertEquals(2, fieldReadCount(oldSight, ENTITY_AI_NEAREST_TARGET_MIXIN, "field_75299_d", "L" + ENTITY_CREATURE + ";"));
        ClassNode rewriteProbe = readNode(oldNearestTargetMixin());
        assertTrue(SrpcotesiaApiCompat.rewriteNearestTargetSightHandler(
                findMethod(rewriteProbe, "func_75296_a", NEAREST_TARGET_METHOD_DESC)));

        int adaptations = SrpcotesiaApiCompat.apply(cotesiaJar(), classes, peers, remapper);
        assertEquals(ENTITY_AI_BASE, readNode(classes.get(ENTITY_AI_NEAREST_TARGET_MIXIN)).superName);
        assertEquals(12, adaptations);
        for (String name : List.of(ENTITY_P_INFECTED_MIXIN, ENTITY_AI_INFECTED_SEARCH_MIXIN,
                ENTITY_AI_NEAREST_TARGET_MIXIN, ENTITY_ROF_MIXIN, ENTITY_NADE_MIXIN, POTION_ANTIBODIES)) {
            assertRemappable(classes.get(name));
        }

        ClassNode nearestTarget = readNode(classes.get(ENTITY_AI_NEAREST_TARGET_MIXIN));
        MethodNode nearestSight = findMethod(nearestTarget, NEAREST_TARGET_HANDLER, NEAREST_TARGET_WRAP_DESC);
        assertNotNull(nearestSight);
        assertEquals(ENTITY_AI_BASE, nearestTarget.superName);
        assertTrue(nearestTarget.signature.endsWith("L" + ENTITY_AI_BASE + ";"));
        assertEquals(List.of("isSuitableTarget" + NEAREST_TARGET_METHOD_DESC), annotationValue(methodAnnotation(nearestSight, WRAP_METHOD), "method"));
        assertEquals(1, methodCallCount(nearestSight, OPERATION, "call"));
        assertFalse(hasMethodCall(nearestSight, ENTITY_AI_TARGET, "func_75296_a"));
        assertEquals(1, fieldReadCount(nearestSight, ENTITY_AI_NEAREST_TARGET, "shouldCheckSight", "Z"));
        assertEquals(2, fieldReadCount(nearestSight, ENTITY_AI_NEAREST_TARGET, "taskOwner", "L" + ENTITY_PARASITE + ";"));
        assertEquals(0, fieldReadCount(nearestSight, ENTITY_AI_NEAREST_TARGET_MIXIN, "field_75297_f", "Z"));
        assertEquals(0, fieldReadCount(nearestSight, ENTITY_AI_NEAREST_TARGET_MIXIN, "field_75299_d", "L" + ENTITY_CREATURE + ";"));
        assertFalse(fieldReadCount(nearestSight, ENTITY_AI_TARGET, "field_75297_f", "Z") > 0);
        FieldNode targetClass = nearestTarget.fields.stream().filter(field -> field.name.equals("targetClass")).findFirst().orElseThrow();
        FieldNode selector = nearestTarget.fields.stream().filter(field -> field.name.equals("targetEntitySelector")).findFirst().orElseThrow();
        assertFalse(hasFieldAnnotation(targetClass, "Lorg/spongepowered/asm/mixin/Final;"));
        assertTrue(hasFieldAnnotation(selector, "Lorg/spongepowered/asm/mixin/Mutable;"));
        MethodNode dummyConstructor = findMethod(nearestTarget, "<init>", "(L" + ENTITY_CREATURE + ";Z)V");
        assertNotNull(dummyConstructor);
        assertNotNull(findCall(dummyConstructor, ENTITY_AI_BASE, "<init>", "()V"));
        assertTrue(findMethod(nearestTarget, "func_75296_a", NEAREST_TARGET_METHOD_DESC) == null);

        for (MethodNode method : nearestTarget.methods) {
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
                new Analyzer<BasicValue>(new BasicVerifier()).analyze(nearestTarget.name, method);
            }
        }

        ClassNode infected = readNode(classes.get(ENTITY_P_INFECTED_MIXIN));
        assertNotNull(findMethod(infected, "srpcotesia$transform", "(L" + CALLBACK_INFO + ";)V"));
        assertTrue(infected.methods.stream().noneMatch(method -> method.name.equals("srpcotesia$melt")));

        ClassNode infectedSearch = readNode(classes.get(ENTITY_AI_INFECTED_SEARCH_MIXIN));
        FieldNode canmelt = infectedSearch.fields.stream().filter(field -> field.name.equals("canmelt")).findFirst().orElseThrow();
        assertTrue(canmelt.visibleAnnotations.stream().anyMatch(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/Unique;")));
        assertTrue(canmelt.visibleAnnotations.stream().noneMatch(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;")));

        ClassNode rof = readNode(classes.get(ENTITY_ROF_MIXIN));
        MethodNode getMob = rof.methods.stream().filter(method -> method.name.equals("srpcotesia$getMob")).findFirst().orElseThrow();
        AnnotationNode getMobInject = methodAnnotation(getMob, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        assertEquals(List.of("getMobFromList"), annotationValue(getMobInject, "method"));
        assertTrue(getMob.signature.contains("Lnet/minecraft/entity/EntityLiving;"));
        assertTrue(getMob.instructions.iterator().hasNext());
        assertTrue(hasMethodCall(getMob, ENTITY_ROF_MIXIN, "srpcotesia$spawnWaves"));
        MethodNode spawnWaves = rof.methods.stream().filter(method -> method.name.equals("srpcotesia$spawnWaves")).findFirst().orElseThrow();
        assertEquals(List.of("getMobFromList"), annotationValue(methodAnnotation(spawnWaves,
                "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;"), "method"));

        ClassNode nade = readNode(classes.get(ENTITY_NADE_MIXIN));
        String baseDesc = "L" + ENTITY_PARASITE + ";";
        FieldNode father = nade.fields.stream().filter(field -> field.name.equals("father")).findFirst().orElseThrow();
        assertEquals(baseDesc, father.desc);
        MethodNode setFather = nade.methods.stream().filter(method -> method.name.equals("srpcotesia$setFatherS")).findFirst().orElseThrow();
        assertEquals("(" + baseDesc + "L" + CALLBACK_INFO + ";)V", setFather.desc);
        MethodNode minimum = nade.methods.stream().filter(method -> method.name.equals("srpcotesia$selfExplodeMinimum")).findFirst().orElseThrow();
        assertEquals("(" + baseDesc + "Lnet/minecraft/entity/EntityLivingBase;FL" + OPERATION + ";)Z", minimum.desc);
        assertEquals(2, methodCallCount(minimum, OPERATION, "call"));
        assertTrue(annotationHasTarget(methodAnnotation(minimum, "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;"),
                "L" + ENTITY_PARASITE + ";attackEntityAsMobMinimum(Lnet/minecraft/entity/EntityLivingBase;F)Z"));
        ClassNode antibodies = readNode(classes.get(POTION_ANTIBODIES));
        assertNotNull(findMethod(antibodies, "getDurationForMob", "(" + baseDesc + ")I"));
        assertNotNull(findMethod(antibodies, "getAmpForMob", "(" + baseDesc + ")I"));

        for (MethodNode method : List.of(getMob, minimum)) {
            new Analyzer<BasicValue>(new BasicVerifier()).analyze(method == getMob ? rof.name : nade.name, method);
        }
    }

    private static Map<String, ClassInfo> currentEntityMixinPeers(LegacyRemapper remapper) {
        Map<String, ClassInfo> peers = new LinkedHashMap<>();
        ClassNode parasite = new ClassNode();
        parasite.name = ENTITY_PARASITE;
        parasite.superName = "java/lang/Object";
        peers.put(ENTITY_PARASITE, ClassInfo.of(parasite));

        ClassNode infected = new ClassNode();
        infected.name = ENTITY_P_INFECTED;
        infected.superName = ENTITY_PARASITE;
        infected.methods.add(new MethodNode(Opcodes.ACC_PRIVATE, "transform", "()V", null, null));
        peers.put(ENTITY_P_INFECTED, ClassInfo.of(infected));

        ClassNode search = new ClassNode();
        search.name = ENTITY_AI_INFECTED_SEARCH;
        search.superName = "java/lang/Object";
        search.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "parent",
                remapper.mapDesc("L" + ENTITY_PARASITE + ";"), null, null));
        search.methods.add(new MethodNode(Opcodes.ACC_PROTECTED, "LeshCount", "()Z", null, null));
        peers.put(ENTITY_AI_INFECTED_SEARCH, ClassInfo.of(search));

        ClassNode nearestPeer = new ClassNode();
        nearestPeer.name = ENTITY_AI_NEAREST_TARGET;
        nearestPeer.superName = remapper.map(ENTITY_AI_BASE);
        nearestPeer.fields.add(new FieldNode(Opcodes.ACC_PROTECTED, "taskOwner",
                remapper.mapDesc("L" + ENTITY_PARASITE + ";"), null, null));
        nearestPeer.fields.add(new FieldNode(Opcodes.ACC_PROTECTED, "shouldCheckSight", "Z", null, null));
        nearestPeer.fields.add(new FieldNode(Opcodes.ACC_PROTECTED, "targetClass", "Ljava/lang/Class;", null, null));
        nearestPeer.fields.add(new FieldNode(Opcodes.ACC_PROTECTED, "targetEntitySelector",
                "Lcom/google/common/base/Predicate;", null, null));
        nearestPeer.methods.add(new MethodNode(Opcodes.ACC_PROTECTED, "isSuitableTarget",
                remapper.mapMethodDesc(NEAREST_TARGET_METHOD_DESC), null, null));
        peers.put(ENTITY_AI_NEAREST_TARGET, ClassInfo.of(nearestPeer));

        String livingDesc = remapper.mapDesc("Lnet/minecraft/entity/EntityLiving;");
        String factoryDesc = remapper.mapMethodDesc(SUMMON_FACTORY_DESC);
        ClassNode rof = new ClassNode();
        rof.name = ENTITY_ROF;
        rof.superName = "java/lang/Object";
        MethodNode getMob = new MethodNode(Opcodes.ACC_PRIVATE, "getMobFromList", "()" + livingDesc, null, null);
        getMob.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, remapper.map(ENTITY_LIST),
                "createEntityByIDFromName", factoryDesc, false));
        rof.methods.add(getMob);
        peers.put(ENTITY_ROF, ClassInfo.of(rof));

        String minimumDesc = remapper.mapMethodDesc("(Lnet/minecraft/entity/EntityLivingBase;F)Z");
        ClassNode nade = new ClassNode();
        nade.name = ENTITY_NADE;
        nade.superName = "java/lang/Object";
        nade.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "father", remapper.mapDesc("L" + ENTITY_PARASITE + ";"), null, null));
        nade.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "setFatherS",
                remapper.mapMethodDesc("(L" + ENTITY_PARASITE + ";)V"), null, null));
        MethodNode selfExplode = new MethodNode(Opcodes.ACC_PROTECTED, "selfExplode", "()V", null, null);
        selfExplode.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, remapper.map(ENTITY_PARASITE),
                "attackEntityAsMobMinimum", minimumDesc, false));
        nade.methods.add(selfExplode);
        peers.put(ENTITY_NADE, ClassInfo.of(nade));
        return peers;
    }

    private static byte[] oldInfectedMixinForCurrentApi() {
        ClassNode node = simpleMixin(ENTITY_P_INFECTED_MIXIN, ENTITY_P_INFECTED, ENTITY_PARASITE);
        MethodNode transform = new MethodNode(Opcodes.ACC_PRIVATE, "srpcotesia$transform", "(L" + CALLBACK_INFO + ";)V", null, null);
        transform.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        transform.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/srpcotesia/util/ParasiteInteractions",
                "isSaddled", "(L" + ENTITY_PARASITE + ";)Z", false));
        transform.instructions.add(new InsnNode(Opcodes.POP));
        transform.instructions.add(new InsnNode(Opcodes.RETURN));
        transform.visibleAnnotations = new ArrayList<>(List.of(injectHead("transform")));
        node.methods.add(transform);
        MethodNode melt = new MethodNode(Opcodes.ACC_PRIVATE, "srpcotesia$melt", "(L" + CALLBACK_INFO + ";)V", null, null);
        melt.instructions.add(new InsnNode(Opcodes.RETURN));
        melt.visibleAnnotations = new ArrayList<>(List.of(injectHead("melt")));
        node.methods.add(melt);
        return writeNode(node);
    }

    private static byte[] oldInfectedSearchMixin() {
        ClassNode node = simpleMixin(ENTITY_AI_INFECTED_SEARCH_MIXIN, ENTITY_AI_INFECTED_SEARCH, "net/minecraft/entity/ai/EntityAIBase");
        FieldNode parent = new FieldNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "parent",
                "L" + ENTITY_PARASITE + ";", null, null);
        parent.visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lorg/spongepowered/asm/mixin/Shadow;"),
                new AnnotationNode("Lorg/spongepowered/asm/mixin/Final;")));
        node.fields.add(parent);
        FieldNode canmelt = new FieldNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE, "canmelt", "Z", null, null);
        canmelt.visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lorg/spongepowered/asm/mixin/Shadow;")));
        node.fields.add(canmelt);
        MethodNode update = new MethodNode(Opcodes.ACC_PRIVATE, "srpcotesia$updateTask", "(L" + CALLBACK_INFO + ";)V", null, null);
        update.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        update.instructions.add(new InsnNode(Opcodes.ICONST_0));
        update.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, node.name, "canmelt", "Z"));
        update.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(update);
        MethodNode count = new MethodNode(Opcodes.ACC_PRIVATE, "srpcotesia$leshCount", "(L" + CALLBACK_INFO_RETURNABLE + ";)V", null, null);
        count.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        count.instructions.add(new InsnNode(Opcodes.ICONST_0));
        count.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, node.name, "canmelt", "Z"));
        count.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(count);
        return writeNode(node);
    }

    private static byte[] oldEntityRofMixin() {
        ClassNode node = simpleMixin(ENTITY_ROF_MIXIN, ENTITY_ROF, "com/dhanantry/scapeandrunparasites/entity/ai/misc/EntityPStationary");
        MethodNode extra = new MethodNode(Opcodes.ACC_PRIVATE, "srpcotesia$getExtraParasite", "()Ljava/lang/String;", null, null);
        extra.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        extra.instructions.add(new InsnNode(Opcodes.ARETURN));
        node.methods.add(extra);
        MethodNode getMob = new MethodNode(Opcodes.ACC_PRIVATE, "srpcotesia$getMob",
                "(L" + CALLBACK_INFO_RETURNABLE + ";)V", null, null);
        getMob.instructions.add(new InsnNode(Opcodes.RETURN));
        getMob.visibleAnnotations = new ArrayList<>(List.of(injectHead("getMob")));
        node.methods.add(getMob);
        MethodNode spawnWaves = new MethodNode(Opcodes.ACC_PRIVATE, "srpcotesia$spawnWaves",
                "(Lnet/minecraft/entity/Entity;)Lnet/minecraft/entity/Entity;", null, null);
        spawnWaves.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        spawnWaves.instructions.add(new InsnNode(Opcodes.ARETURN));
        spawnWaves.visibleAnnotations = new ArrayList<>(List.of(modifyExpressionValue("spawnWaves",
                "L" + ENTITY_LIST + ";createEntityByIDFromName" + SUMMON_FACTORY_DESC)));
        node.methods.add(spawnWaves);
        return writeNode(node);
    }

    private static byte[] oldNearestTargetMixin() {
        ClassNode node = simpleMixin(ENTITY_AI_NEAREST_TARGET_MIXIN, ENTITY_AI_NEAREST_TARGET, ENTITY_AI_TARGET);
        node.signature = "<T:Lnet/minecraft/entity/EntityLivingBase;>L" + ENTITY_AI_TARGET + ";";
        FieldNode targetClass = new FieldNode(Opcodes.ASM9, Opcodes.ACC_PROTECTED, "targetClass", "Ljava/lang/Class;",
                "Ljava/lang/Class<TT;>;", null);
        targetClass.visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lorg/spongepowered/asm/mixin/Shadow;"),
                new AnnotationNode("Lorg/spongepowered/asm/mixin/Final;")));
        node.fields.add(targetClass);
        FieldNode selector = new FieldNode(Opcodes.ASM9, Opcodes.ACC_PROTECTED, "targetEntitySelector",
                "Lcom/google/common/base/Predicate;", "Lcom/google/common/base/Predicate<-TT;>;", null);
        selector.visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lorg/spongepowered/asm/mixin/Shadow;"),
                new AnnotationNode("Lorg/spongepowered/asm/mixin/Final;"),
                new AnnotationNode("Lorg/spongepowered/asm/mixin/Mutable;")));
        node.fields.add(selector);

        MethodNode constructor = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "<init>",
                "(L" + ENTITY_CREATURE + ";Z)V", null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        constructor.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, ENTITY_AI_TARGET, "<init>",
                "(L" + ENTITY_CREATURE + ";Z)V", false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        constructor.maxStack = 3;
        constructor.maxLocals = 3;
        node.methods.add(constructor);

        MethodNode sight = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PROTECTED, "func_75296_a", NEAREST_TARGET_METHOD_DESC, null, null);
        LabelNode vanillaFailure = new LabelNode();
        LabelNode trueResult = new LabelNode();
        LabelNode loyal = new LabelNode();
        sight.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        sight.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        sight.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        sight.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, ENTITY_AI_TARGET, "func_75296_a",
                NEAREST_TARGET_METHOD_DESC, false));
        sight.instructions.add(new JumpInsnNode(Opcodes.IFEQ, vanillaFailure));
        sight.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/srpcotesia/config/ConfigMain", "restrictedSight", "Z"));
        sight.instructions.add(new JumpInsnNode(Opcodes.IFEQ, trueResult));
        sight.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        sight.instructions.add(new JumpInsnNode(Opcodes.IFNULL, trueResult));
        sight.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        sight.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_AI_NEAREST_TARGET_MIXIN, "field_75297_f", "Z"));
        sight.instructions.add(new JumpInsnNode(Opcodes.IFNE, trueResult));
        sight.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        sight.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_AI_NEAREST_TARGET_MIXIN, "field_75299_d", "L" + ENTITY_CREATURE + ";"));
        sight.instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, ENTITY_PARASITE));
        sight.instructions.add(new JumpInsnNode(Opcodes.IFEQ, trueResult));
        sight.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        sight.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/srpcotesia/util/ParasiteInteractions", "isParasite",
                "(Lnet/minecraft/entity/EntityLivingBase;)Z", false));
        sight.instructions.add(new JumpInsnNode(Opcodes.IFNE, trueResult));
        sight.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        sight.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_AI_NEAREST_TARGET_MIXIN, "field_75299_d", "L" + ENTITY_CREATURE + ";"));
        sight.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, ENTITY_PARASITE));
        sight.instructions.add(new VarInsnNode(Opcodes.ASTORE, 3));
        sight.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        sight.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/srpcotesia/util/ParasiteInteractions", "isLoyal",
                "(L" + ENTITY_PARASITE + ";)Z", false));
        sight.instructions.add(new JumpInsnNode(Opcodes.IFNE, loyal));
        sight.instructions.add(new InsnNode(Opcodes.ICONST_1));
        sight.instructions.add(new InsnNode(Opcodes.IRETURN));
        sight.instructions.add(loyal);
        sight.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        sight.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY_PARASITE, "func_70635_at",
                "()Lnet/minecraft/entity/ai/EntitySenses;", false));
        sight.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        sight.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/ai/EntitySenses", "func_75522_a",
                "(Lnet/minecraft/entity/Entity;)Z", false));
        sight.instructions.add(new JumpInsnNode(Opcodes.IFNE, trueResult));
        sight.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        sight.instructions.add(new org.objectweb.asm.tree.LdcInsnNode(32.0D));
        sight.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/srpcotesia/util/ParasiteInteractions", "getManager",
                "(L" + ENTITY_PARASITE + ";D)Lnet/minecraft/entity/player/EntityPlayer;", false));
        sight.instructions.add(new VarInsnNode(Opcodes.ASTORE, 4));
        sight.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        sight.instructions.add(new JumpInsnNode(Opcodes.IFNULL, trueResult));
        sight.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        sight.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        sight.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/player/EntityPlayer", "func_70685_l",
                "(Lnet/minecraft/entity/Entity;)Z", false));
        sight.instructions.add(new JumpInsnNode(Opcodes.IFNE, trueResult));
        sight.instructions.add(new InsnNode(Opcodes.ICONST_0));
        sight.instructions.add(new InsnNode(Opcodes.IRETURN));
        sight.instructions.add(trueResult);
        sight.instructions.add(new InsnNode(Opcodes.ICONST_1));
        sight.instructions.add(new InsnNode(Opcodes.IRETURN));
        sight.instructions.add(vanillaFailure);
        sight.instructions.add(new InsnNode(Opcodes.ICONST_0));
        sight.instructions.add(new InsnNode(Opcodes.IRETURN));
        sight.maxStack = 5;
        sight.maxLocals = 5;
        node.methods.add(sight);
        return writeNode(node);
    }

    private static byte[] oldEntityNadeMixin() {
        ClassNode node = simpleMixin(ENTITY_NADE_MIXIN, ENTITY_NADE, "net/minecraft/entity/Entity");
        FieldNode father = new FieldNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE, "father", "L" + ENTITY_PMALLEABLE + ";", null, null);
        father.visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lorg/spongepowered/asm/mixin/Shadow;")));
        node.fields.add(father);
        MethodNode setFather = new MethodNode(Opcodes.ACC_PRIVATE, "srpcotesia$setFatherS",
                "(L" + ENTITY_PMALLEABLE + ";L" + CALLBACK_INFO + ";)V", null, null);
        setFather.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(setFather);
        String minimumDesc = "(L" + ENTITY_PMALLEABLE + ";Lnet/minecraft/entity/EntityLivingBase;L" + OPERATION + ";)Z";
        MethodNode minimum = new MethodNode(Opcodes.ACC_PRIVATE, "srpcotesia$selfExplodeMinimum", minimumDesc,
                "(L" + ENTITY_PMALLEABLE + ";Lnet/minecraft/entity/EntityLivingBase;L" + OPERATION + "<Ljava/lang/Boolean;>;)Z", null);
        minimum.instructions.add(new InsnNode(Opcodes.ICONST_0));
        minimum.instructions.add(new InsnNode(Opcodes.IRETURN));
        minimum.visibleAnnotations = new ArrayList<>(List.of(wrapOperation(
                "L" + ENTITY_PMALLEABLE + ";attackEntityAsMobMinimum(Lnet/minecraft/entity/EntityLivingBase;)Z", "selfExplode", 1)));
        node.methods.add(minimum);
        MethodNode explode = new MethodNode(Opcodes.ACC_PRIVATE, "srpcotesia$selfExplode", "()V", null, null);
        explode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        explode.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "father", "L" + ENTITY_PMALLEABLE + ";"));
        explode.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, POTION_ANTIBODIES, "getDurationForMob",
                "(L" + ENTITY_PMALLEABLE + ";)I", false));
        explode.instructions.add(new InsnNode(Opcodes.POP));
        explode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        explode.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "father", "L" + ENTITY_PMALLEABLE + ";"));
        explode.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, POTION_ANTIBODIES, "getAmpForMob",
                "(L" + ENTITY_PMALLEABLE + ";)I", false));
        explode.instructions.add(new InsnNode(Opcodes.POP));
        explode.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(explode);
        return writeNode(node);
    }

    private static byte[] oldPotionAntibodies() {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = POTION_ANTIBODIES;
        node.superName = "java/lang/Object";
        for (String method : List.of("getDurationForMob", "getAmpForMob")) {
            MethodNode helper = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, method,
                    "(L" + ENTITY_PMALLEABLE + ";)I", null, null);
            helper.instructions.add(new InsnNode(Opcodes.ICONST_0));
            helper.instructions.add(new InsnNode(Opcodes.IRETURN));
            node.methods.add(helper);
        }
        return writeNode(node);
    }

    private static ClassNode simpleMixin(String name, String target, String superName) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT;
        node.name = name;
        node.superName = superName;
        AnnotationNode mixin = new AnnotationNode("Lorg/spongepowered/asm/mixin/Mixin;");
        mixin.values = new ArrayList<>(List.of("value", List.of(Type.getObjectType(target))));
        node.invisibleAnnotations = new ArrayList<>(List.of(mixin));
        return node;
    }

    private static byte[] oldSummonMixin() {
        ClassNode node = parasiteEventEntityMixin();
        MethodNode first = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "srpcotesia$SummonM",
                OLD_SUMMON_M_HANDLER_DESC, null, null);
        first.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        first.instructions.add(new InsnNode(Opcodes.ARETURN));
        first.maxStack = 1;
        first.maxLocals = 5;
        first.visibleAnnotations = new ArrayList<>(List.of(modifyExpressionValue("SummonM" + OLD_SUMMON_M_DESC,
                "L" + ENTITY_LIST + ";createEntityByIDFromName" + SUMMON_FACTORY_DESC)));
        MethodNode second = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "srpcotesia$SummonMTwo",
                SUMMON_M_TWO_HANDLER_DESC, null, null);
        second.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        second.instructions.add(new InsnNode(Opcodes.ARETURN));
        second.maxStack = 1;
        second.maxLocals = 12;
        second.visibleAnnotations = new ArrayList<>(List.of(modifyExpressionValue("SummonM" + SUMMON_M_TWO_DESC,
                "L" + ENTITY_LIST + ";createEntityByIDFromName" + SUMMON_FACTORY_DESC)));
        node.methods.add(first);
        node.methods.add(second);
        return writeNode(node);
    }

    private static byte[] oldFeralGetterMixin() {
        ClassNode node = parasiteEventEntityMixin();
        MethodNode handler = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, GET_FERAL_HANDLER,
                "(L" + ENTITY_P_FERAL + ";Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/nbt/NBTTagCompound;Z[Ljava/lang/String;)L"
                        + ENTITY_P_FERAL + ";", null, null);
        handler.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        handler.instructions.add(new InsnNode(Opcodes.ARETURN));
        handler.maxStack = 1;
        handler.maxLocals = 5;
        handler.visibleAnnotations = new ArrayList<>(List.of(modifyExpressionValue("convertEntityFeral",
                "L" + ENTITY_P_INFECTED + ";getFeral" + OLD_GET_FERAL_DESC)));
        node.methods.add(handler);
        return writeNode(node);
    }

    private static ClassNode parasiteEventEntityMixin() {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT;
        node.name = MERGE_MIXIN;
        node.superName = "java/lang/Object";
        AnnotationNode mixin = new AnnotationNode("Lorg/spongepowered/asm/mixin/Mixin;");
        mixin.values = new ArrayList<>(List.of("value", List.of(Type.getObjectType(PARASITE_EVENT_ENTITY))));
        node.invisibleAnnotations = new ArrayList<>(List.of(mixin));
        return node;
    }

    private static AnnotationNode modifyExpressionValue(String method, String target) {
        AnnotationNode injector = new AnnotationNode("Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;");
        injector.values = new ArrayList<>(List.of("method", List.of(method), "at", List.of(at("INVOKE", target)), "require", 1));
        return injector;
    }

    private static void assertRemappable(byte[] bytes) {
        new ClassReader(bytes).accept(new ClassRemapper(new ClassWriter(0), new Remapper() {}), 0);
    }

    private static boolean hasFieldAnnotation(FieldNode field, String descriptor) {
        if (field.visibleAnnotations != null) {
            for (AnnotationNode annotation : field.visibleAnnotations) if (annotation.desc.equals(descriptor)) return true;
        }
        if (field.invisibleAnnotations != null) {
            for (AnnotationNode annotation : field.invisibleAnnotations) if (annotation.desc.equals(descriptor)) return true;
        }
        return false;
    }

    private static int fieldReadCount(MethodNode method, String owner, String name, String descriptor) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                    && field.owner.equals(owner) && field.name.equals(name) && field.desc.equals(descriptor)) count++;
        }
        return count;
    }

    private static byte[] writeNode(ClassNode node) {
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static Map<String, ClassInfo> movedSummonPeers(LegacyRemapper remapper) {
        Map<String, ClassInfo> peers = new LinkedHashMap<>();
        ClassNode events = new ClassNode();
        events.name = PARASITE_EVENT_ENTITY;
        events.superName = "java/lang/Object";
        peers.put(PARASITE_EVENT_ENTITY, ClassInfo.of(events));
        ClassNode summon = new ClassNode();
        summon.name = PARASITE_SUMMON;
        summon.superName = "java/lang/Object";
        String factory = remapper.mapMethodDesc(SUMMON_FACTORY_DESC);
        for (String descriptor : List.of(remapper.mapMethodDesc(NEW_SUMMON_M_DESC), remapper.mapMethodDesc(SUMMON_M_TWO_DESC))) {
            MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "SummonM", descriptor, null, null);
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, remapper.map(ENTITY_LIST), "createEntityByIDFromName", factory, false));
            method.instructions.add(new InsnNode(Opcodes.POP));
            method.instructions.add(new InsnNode(Opcodes.RETURN));
            summon.methods.add(method);
        }
        peers.put(PARASITE_SUMMON, ClassInfo.of(summon));
        return peers;
    }

    private static Map<String, ClassInfo> movedFeralPeers(LegacyRemapper remapper) {
        Map<String, ClassInfo> peers = new LinkedHashMap<>();
        String getter = remapper.mapMethodDesc(NEW_GET_FERAL_DESC);
        ClassNode infected = new ClassNode();
        infected.name = ENTITY_P_INFECTED;
        infected.superName = "java/lang/Object";
        infected.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "getFeral", getter, null, null));
        peers.put(ENTITY_P_INFECTED, ClassInfo.of(infected));

        String convert = remapper.mapMethodDesc(CONVERT_ENTITY_FERAL_DESC);
        ClassNode events = new ClassNode();
        events.name = PARASITE_EVENT_ENTITY;
        events.superName = "java/lang/Object";
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "convertEntityFeral", convert, null, null);
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY_P_INFECTED, "getFeral", getter, false));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        events.methods.add(method);
        peers.put(PARASITE_EVENT_ENTITY, ClassInfo.of(events));
        return peers;
    }

    private static byte[] oldParasiteEventWorldMixin() {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT;
        node.name = PARASITE_EVENT_WORLD_MIXIN;
        node.superName = "java/lang/Object";
        AnnotationNode mixin = new AnnotationNode("Lorg/spongepowered/asm/mixin/Mixin;");
        mixin.values = new ArrayList<>(List.of("value", List.of(Type.getObjectType(PARASITE_EVENT_WORLD))));
        node.invisibleAnnotations = new ArrayList<>(List.of(mixin));

        MethodNode canInject = oldVenkrolInjector();
        node.methods.add(canInject);

        MethodNode canWrap = movedKillsWrapHandler();
        node.methods.add(canWrap);

        MethodNode blockException = simpleMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "srpcotesia$blockException", "()V", Opcodes.RETURN);
        blockException.visibleAnnotations = new ArrayList<>(List.of(injectHead("blockException")));
        node.methods.add(blockException);

        MethodNode spreadBiome = simpleMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "srpcotesia$SpreadBiome", "()V", Opcodes.RETURN);
        spreadBiome.visibleAnnotations = new ArrayList<>(List.of(wrapOperation("SpreadBiome", at("INVOKE", "Lexample/Range;isInRange()Z"), 1)));
        node.methods.add(spreadBiome);

        MethodNode stain = simpleMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "srpcotesia$spreadBiomeBlockStain", "()V", Opcodes.RETURN);
        stain.visibleAnnotations = new ArrayList<>(List.of(injectHead("spreadBiomeBlockStain")));
        node.methods.add(stain);

        MethodNode trunk = simpleMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "srpcotesia$spreadBiomeBlockTrunk", "()V", Opcodes.RETURN);
        trunk.visibleAnnotations = new ArrayList<>(List.of(injectHead("spreadBiomeBlockTrunk")));
        node.methods.add(trunk);

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode movedKillsWrapHandler() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "srpcotesia$canInfestBlock", OLD_MOVED_KILLS_HANDLER_DESC, OLD_MOVED_KILLS_HANDLER_SIGNATURE, null);
        method.visibleAnnotations = new ArrayList<>(List.of(wrapOperation("canInfestBlock",
                at("INVOKE", "L" + SRP_SAVE_DATA + ";setTotalKills" + OLD_SET_TOTAL_KILLS), 1)));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 6));
        method.instructions.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, 6));
        method.instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, OPERATION, "call",
                "([Ljava/lang/Object;)Ljava/lang/Object;", true));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Boolean"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 3;
        method.maxLocals = 12;
        return method;
    }

    private static MethodNode oldVenkrolInjector() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "srpcotesia$canInfestBlock", OLD_VENKROL_INJECT_DESC, null, null);
        method.visibleAnnotations = new ArrayList<>(List.of(injectAt("canInfestBlock",
                "L" + ENTITY_VENKROL + ";cannotDespawn(Z)V")));
        method.invisibleParameterAnnotations = new List[7];
        method.invisibleParameterAnnotations[6] = new ArrayList<>(List.of(new AnnotationNode(LOCAL_ANNOTATION)));
        method.invisibleAnnotableParameterCount = 7;
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 6));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 7));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 7));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(end);
        method.maxStack = 1;
        method.maxLocals = 8;
        method.localVariables = new ArrayList<>(List.of(
                new LocalVariableNode("rand", "Ljava/util/Random;", null, start, end, 2),
                new LocalVariableNode("stage", "I", null, start, end, 3),
                new LocalVariableNode("fromVenkrol", "Z", null, start, end, 4),
                new LocalVariableNode("ci", "L" + CALLBACK_INFO + ";", null, start, end, 5),
                new LocalVariableNode("out", "L" + ENTITY_VENKROL + ";", null, start, end, 6),
                new LocalVariableNode("possible", "Ljava/lang/Object;", null, start, end, 7)));
        return method;
    }

    private static AnnotationNode injectHead(String method) {
        AnnotationNode inject = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;");
        inject.values = new ArrayList<>(List.of("method", List.of(method), "at", List.of(at("HEAD", null))));
        return inject;
    }

    private static byte[] oldEntityAiBlockInfestMixin() {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT;
        node.name = ENTITY_AI_BLOCK_INFEST_MIXIN;
        node.superName = "java/lang/Object";
        MethodNode handler = simpleMethod(Opcodes.ACC_PUBLIC, "srpcotesia$updateTask", "()V", Opcodes.RETURN);
        String oldTarget = "L" + PARASITE_EVENT_WORLD + ";canInfestBlock" + CAN_INFEST_DESC;
        AnnotationNode inject = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;");
        inject.values = new ArrayList<>(List.of("method", List.of("updateTask()V", "func_75246_d()V"),
                "at", List.of(at("INVOKE", oldTarget)), "require", 1));
        handler.visibleAnnotations = new ArrayList<>(List.of(inject));
        node.methods.add(handler);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static Map<String, ClassInfo> movedParasitePeers(LegacyRemapper remapper) {
        Map<String, ClassInfo> peers = new LinkedHashMap<>();
        ClassNode world = new ClassNode();
        world.name = PARASITE_EVENT_WORLD;
        world.superName = "java/lang/Object";
        peers.put(PARASITE_EVENT_WORLD, ClassInfo.of(world));

        ClassNode spreading = new ClassNode();
        spreading.name = BLOCK_PARASITE_SPREADING;
        spreading.superName = "java/lang/Object";
        spreading.methods.add(new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "canInfestBlock",
                remapper.mapMethodDesc(CAN_INFEST_DESC), null, null));
        spreading.methods.add(new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "spreadBiomeBlockStain",
                remapper.mapMethodDesc(SPREAD_METHOD_DESC), null, null));
        spreading.methods.add(new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "spreadBiomeBlockTrunk",
                remapper.mapMethodDesc(SPREAD_METHOD_DESC), null, null));
        peers.put(BLOCK_PARASITE_SPREADING, ClassInfo.of(spreading));

        ClassNode beckon = new ClassNode();
        beckon.name = BECKON_BLOCK_INFESTATION;
        beckon.superName = "java/lang/Object";
        MethodNode spawnBeckon = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "spawnBeckonFromInfestation",
                remapper.mapMethodDesc(SPAWN_BECKON_DESC), null, null);
        spawnBeckon.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        spawnBeckon.instructions.add(new InsnNode(Opcodes.ICONST_1));
        spawnBeckon.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY_VENKROL, "cannotDespawn", "(Z)V", false));
        spawnBeckon.instructions.add(new InsnNode(Opcodes.RETURN));
        beckon.methods.add(spawnBeckon);
        MethodNode beckonMethod = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "beckonInfestation",
                remapper.mapMethodDesc(CAN_INFEST_DESC), null, null);
        LabelNode noVenkrol = new LabelNode();
        beckonMethod.instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
        beckonMethod.instructions.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFEQ, noVenkrol));
        beckonMethod.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        beckonMethod.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        beckonMethod.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        beckonMethod.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BECKON_BLOCK_INFESTATION,
                "spawnBeckonFromInfestation", remapper.mapMethodDesc(SPAWN_BECKON_DESC), false));
        beckonMethod.instructions.add(noVenkrol);
        for (int i = 0; i < 2; i++) beckonMethod.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                SRP_SAVE_DATA, "setTotalKills", remapper.mapMethodDesc(NEW_SET_TOTAL_KILLS), false));
        beckonMethod.instructions.add(new InsnNode(Opcodes.RETURN));
        beckon.methods.add(beckonMethod);
        peers.put(BECKON_BLOCK_INFESTATION, ClassInfo.of(beckon));

        ClassNode ai = new ClassNode();
        ai.name = ENTITY_AI_BLOCK_INFEST;
        ai.superName = "java/lang/Object";
        MethodNode update = new MethodNode(Opcodes.ACC_PUBLIC, "updateTask", "()V", null, null);
        update.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BLOCK_PARASITE_SPREADING,
                "canInfestBlock", remapper.mapMethodDesc(CAN_INFEST_DESC), false));
        update.instructions.add(new InsnNode(Opcodes.RETURN));
        ai.methods.add(update);
        peers.put(ENTITY_AI_BLOCK_INFEST, ClassInfo.of(ai));
        return peers;
    }

    private static byte[] oldParasiteBaseMixin() {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = PARASITE_BASE_MIXIN;
        node.superName = "java/lang/Object";

        MethodNode apply = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, APPLY_BONUSES_HANDLER,
                OLD_APPLY_BONUSES_HANDLER_DESC, null, null);
        LabelNode applyStart = new LabelNode();
        LabelNode applyEnd = new LabelNode();
        apply.instructions.add(applyStart);
        apply.instructions.add(new InsnNode(Opcodes.ICONST_1));
        apply.instructions.add(new VarInsnNode(Opcodes.ISTORE, 5));
        apply.instructions.add(new VarInsnNode(Opcodes.ILOAD, 5));
        apply.instructions.add(new InsnNode(Opcodes.POP));
        apply.instructions.add(new InsnNode(Opcodes.RETURN));
        apply.instructions.add(applyEnd);
        apply.maxStack = 1;
        apply.maxLocals = 6;
        apply.localVariables = new ArrayList<>(List.of(
                new LocalVariableNode("this", "L" + PARASITE_BASE_MIXIN + ";", null, applyStart, applyEnd, 0),
                new LocalVariableNode("phase", "I", null, applyStart, applyEnd, 1),
                new LocalVariableNode("env", "I", null, applyStart, applyEnd, 2),
                new LocalVariableNode("enabled", "B", null, applyStart, applyEnd, 3),
                new LocalVariableNode("ci", "L" + CALLBACK_INFO + ";", null, applyStart, applyEnd, 4),
                new LocalVariableNode("dendr", "I", null, applyStart, applyEnd, 5)));
        node.methods.add(apply);

        MethodNode block = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, BLOCK_MINI_DAMAGE_HANDLER,
                OLD_BLOCK_MINI_DAMAGE_HANDLER_DESC, "(Lnet/minecraft/entity/EntityLivingBase;L" + CALLBACK_INFO_RETURNABLE
                        + "<Ljava/lang/Boolean;>;L" + LOCAL_FLOAT_REF + ";)V", null);
        LabelNode blockStart = new LabelNode();
        LabelNode blockEnd = new LabelNode();
        block.instructions.add(blockStart);
        block.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        block.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, LOCAL_FLOAT_REF, "get", "()F", true));
        block.instructions.add(new VarInsnNode(Opcodes.FSTORE, 4));
        block.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        block.instructions.add(new InsnNode(Opcodes.ICONST_1));
        block.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false));
        block.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CALLBACK_INFO_RETURNABLE, "setReturnValue", "(Ljava/lang/Object;)V", false));
        block.instructions.add(new InsnNode(Opcodes.RETURN));
        block.instructions.add(blockEnd);
        block.maxStack = 2;
        block.maxLocals = 5;
        block.invisibleParameterAnnotations = new List[3];
        AnnotationNode local = new AnnotationNode(LOCAL_ANNOTATION);
        local.values = new ArrayList<>(List.of("ordinal", 1));
        block.invisibleParameterAnnotations[2] = new ArrayList<>(List.of(local));
        block.localVariables = new ArrayList<>(List.of(
                new LocalVariableNode("this", "L" + PARASITE_BASE_MIXIN + ";", null, blockStart, blockEnd, 0),
                new LocalVariableNode("target", "Lnet/minecraft/entity/EntityLivingBase;", null, blockStart, blockEnd, 1),
                new LocalVariableNode("cir", "L" + CALLBACK_INFO_RETURNABLE + ";", null, blockStart, blockEnd, 2),
                new LocalVariableNode("damage", "L" + LOCAL_FLOAT_REF + ";", null, blockStart, blockEnd, 3),
                new LocalVariableNode("after", "F", null, blockStart, blockEnd, 4)));
        node.methods.add(block);

        MethodNode minimum = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, MIN_DAMAGE_HANDLER,
                OLD_MIN_DAMAGE_HANDLER_DESC, "(Lnet/minecraft/entity/EntityLivingBase;L" + CALLBACK_INFO_RETURNABLE
                        + "<Ljava/lang/Boolean;>;)V", null);
        minimum.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        minimum.instructions.add(new InsnNode(Opcodes.ICONST_0));
        minimum.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false));
        minimum.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CALLBACK_INFO_RETURNABLE, "setReturnValue", "(Ljava/lang/Object;)V", false));
        minimum.instructions.add(new InsnNode(Opcodes.RETURN));
        minimum.maxStack = 2;
        minimum.maxLocals = 3;
        node.methods.add(minimum);

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static int localOrdinal(MethodNode method) {
        return ((Number) annotationValue(method.invisibleParameterAnnotations[3].get(0), "ordinal")).intValue();
    }

    private static int parameterIndex(MethodNode method, String descriptor) {
        for (int i = 0; i < method.invisibleParameterAnnotations.length; i++) {
            List<AnnotationNode> annotations = method.invisibleParameterAnnotations[i];
            if (annotations != null && annotations.stream().anyMatch(annotation -> annotation.desc.equals(descriptor))) return i;
        }
        return -1;
    }

    private static List<Integer> localIndexes(MethodNode method, String... names) {
        List<Integer> indexes = new ArrayList<>();
        for (LocalVariableNode local : method.localVariables) {
            for (String name : names) if (local.name.equals(name)) indexes.add(local.index);
        }
        return indexes;
    }

    private static List<Integer> variableIndexes(MethodNode method, int opcode) {
        List<Integer> indexes = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof VarInsnNode variable && variable.getOpcode() == opcode) indexes.add(variable.var);
        }
        return indexes;
    }

    private static MethodInsnNode superCall(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (var method : node.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(BIOME_DECORATOR)) return call;
            }
        }
        throw new AssertionError("Missing biome decorator constructor call");
    }

    private static LegacyJar cotesiaJar() {
        LegacyJar jar = new LegacyJar(Path.of("srpcotesia.jar"));
        jar.version = LegacyVersion.V1_12_2;
        LegacyModDecl mod = new LegacyModDecl();
        mod.legacyId = "srpcotesia";
        jar.mods.add(mod);
        return jar;
    }

    private static Map<String, ClassInfo> currentGoreApiPeers() {
        Map<String, ClassInfo> peers = new LinkedHashMap<>(currentApiPeers());
        ClassNode oldBiome = new ClassNode();
        oldBiome.name = OLD_BIOME;
        peers.put(OLD_BIOME, ClassInfo.of(oldBiome));

        ClassNode blocks = new ClassNode();
        blocks.name = SRP_BLOCKS;
        for (String field : List.of("goreSim", "gorePri", "goreAda", "gorePur")) {
            blocks.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, field, GORE_DESC, null, null));
        }
        peers.put(SRP_BLOCKS, ClassInfo.of(blocks));

        ClassNode goreEnum = new ClassNode();
        goreEnum.name = GORE_ENUM;
        for (String field : List.of("FLAT", "SMALL", "BIG")) {
            goreEnum.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_ENUM,
                    field, GORE_ENUM_DESC, null, null));
        }
        peers.put(GORE_ENUM, ClassInfo.of(goreEnum));
        return peers;
    }

    private static byte[] oldParasitePlayerGore() {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = PARASITE_PLAYER;
        node.superName = "java/lang/Object";
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "bloom", "I", null, null));
        node.methods.add(oldGoreGetter("getGoreFlat", "SIM_FLAT", "PRI_FLAT", "ADA_FLAT", "PURE_FLAT"));
        node.methods.add(oldGoreGetter("getGoreBig", "SIM_BIG", "PRI_BIG", "ADA_BIG", "PURE_BIG"));

        MethodNode spawn = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "spawnGore",
                "(Lnet/minecraft/entity/player/EntityPlayer;L" + WORLD + ";)V", null, null);
        for (int i = 0; i < 2; i++) {
            spawn.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, SRP_BLOCKS, "Gore", GORE_DESC));
            spawn.instructions.add(new InsnNode(Opcodes.POP));
        }
        spawn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        spawn.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, PARASITE_PLAYER, "getGoreFlat", "()" + GORE_ENUM_DESC, false));
        spawn.instructions.add(new InsnNode(Opcodes.POP));
        spawn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        spawn.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, PARASITE_PLAYER, "getGoreBig", "()" + GORE_ENUM_DESC, false));
        spawn.instructions.add(new InsnNode(Opcodes.POP));
        spawn.instructions.add(new InsnNode(Opcodes.RETURN));
        spawn.maxStack = 1;
        spawn.maxLocals = 3;
        node.methods.add(spawn);

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode oldGoreGetter(String name, String... variants) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, name, "()" + GORE_ENUM_DESC, null, null);
        for (int i = 0; i < variants.length; i++) {
            method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, GORE_ENUM, variants[i], GORE_ENUM_DESC));
            method.instructions.add(new InsnNode(i + 1 == variants.length ? Opcodes.ARETURN : Opcodes.POP));
        }
        method.maxStack = 1;
        method.maxLocals = 1;
        return method;
    }

    private static List<String> enumFieldNames(MethodNode method) {
        List<String> names = new ArrayList<>();
        for (var instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                    && field.owner.equals(GORE_ENUM)) names.add(field.name);
        }
        return names;
    }

    private static int countFieldReads(MethodNode method, String owner, String name) {
        int count = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                    && field.owner.equals(owner) && field.name.equals(name)) count++;
        }
        return count;
    }

    private static int countCalls(MethodNode method, String owner, String name) {
        int count = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) count++;
        }
        return count;
    }

    private static ClassNode currentScaryOrbClass() {
        ClassNode orb = new ClassNode(Opcodes.ASM9);
        orb.name = CURRENT_SCARY_ORB;
        orb.superName = "net/minecraft/entity/Entity";
        String[][] fields = {
                {"prevRenderYawOffset", "F"}, {"renderYawOffset", "F"}, {"prevRotationYawHead", "F"},
                {"rotationYawHead", "F"}, {"prevLimbSwingAmount", "F"}, {"limbSwingAmount", "F"}, {"limbSwing", "F"},
                {"hurtTime", "I"}, {"deathTime", "I"}, {"lastActiveTime", "I"}, {"timeSinceIgnited", "I"},
                {"timerDDD", "I"}, {"father", "L" + ENTITY_PMALLEABLE + ";"}, {"followF", "Z"},
                {"poosX", "D"}, {"poosY", "D"}, {"poosZ", "D"}
        };
        for (String[] field : fields) orb.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, field[0], field[1], null, null));
        orb.methods.add(new MethodNode(Opcodes.ACC_PROTECTED, "dyingBurst", "(ZI)V", null, null));
        orb.methods.add(new MethodNode(Opcodes.ACC_PROTECTED, "selfExplode", "()V", null, null));
        orb.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "getStartState", "()I", null, null));
        orb.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "setSelfeState", "(I)V", null, null));
        orb.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "spawnOrbEffects", "(I)V", null, null));
        return orb;
    }

    private static Map<String, ClassInfo> currentApiPeers() {
        ClassNode base = new ClassNode();
        base.name = BIOME_BASE;
        base.superName = "net/minecraft/world/biome/Biome";

        ClassNode decorator = new ClassNode();
        decorator.name = BIOME_DECORATOR;
        decorator.superName = "net/minecraft/world/biome/BiomeDecorator";
        decorator.methods.add(new org.objectweb.asm.tree.MethodNode(Opcodes.ACC_PUBLIC, "<init>", BASE_CTOR, null, null));

        return Map.of(BIOME_BASE, ClassInfo.of(base), BIOME_DECORATOR, ClassInfo.of(decorator));
    }

    @Test
    void adaptsRemovedSaveDataMethodsToTheCurrentPeerApi() {
        LegacyJar jar = cotesiaJar();
        Map<String, byte[]> classes = new LinkedHashMap<>();
        String caller = "com/srpcotesia/util/EvolutionPhaseData";
        classes.put(caller, oldSaveDataCalls(caller));

        LegacyRemapper remapper = newRemapper();
        assertEquals(4, SrpcotesiaApiCompat.apply(jar, classes, saveDataApiPeers(remapper), remapper));

        ClassNode adapted = new ClassNode();
        new ClassReader(classes.get(caller)).accept(adapted, 0);
        var method = adapted.methods.stream().filter(candidate -> candidate.name.equals("calls")).findFirst().orElseThrow();
        var calls = new java.util.ArrayList<MethodInsnNode>();
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) calls.add(call);
        assertEquals(6, calls.size());
        assertEquals("net/minecraft/world/World", calls.get(0).owner);
        assertEquals(Opcodes.DUP, calls.get(0).getPrevious().getOpcode());
        assertEquals("mcc$get$provider", calls.get(0).name);
        assertEquals("getDimension", calls.get(1).name);
        assertEquals(NEW_GET, calls.get(2).desc);
        assertEquals(calls.get(1), calls.get(2).getPrevious());
        assertEquals(NEW_SET_COOLDOWN, calls.get(3).desc);
        assertEquals(Opcodes.ICONST_0, calls.get(3).getPrevious().getOpcode());
        assertEquals(NEW_SET_PHASE, calls.get(4).desc);
        assertEquals(Opcodes.POP, calls.get(4).getPrevious().getOpcode());
        assertEquals(NEW_SET_TOTAL_KILLS, calls.get(5).desc);
        assertEquals(Opcodes.ICONST_0, calls.get(5).getPrevious().getOpcode());
        assertEquals(7, method.maxStack);
    }

    @Test
    void adaptsCotesiaDespawnRedirectToThePeerSourceIdApi() {
        LegacyJar jar = cotesiaJar();
        Map<String, byte[]> classes = new LinkedHashMap<>();
        byte[] original = oldDespawnRedirect();
        classes.put(DESPAWN_MIXIN, original);
        LegacyRemapper remapper = newRemapper();

        assertEquals(1, SrpcotesiaApiCompat.apply(jar, classes, saveDataApiPeers(remapper), remapper));
        ClassNode adapted = new ClassNode();
        new ClassReader(classes.get(DESPAWN_MIXIN)).accept(adapted, 0);
        var handler = adapted.methods.stream().filter(method -> method.name.equals(DESPAWN_HANDLER)).findFirst().orElseThrow();
        assertEquals(NEW_DESPAWN_REDIRECT, handler.desc);
        assertEquals(8, handler.maxLocals);
        MethodInsnNode call = null;
        for (var instruction : handler.instructions) if (instruction instanceof MethodInsnNode candidate) call = candidate;
        assertNotNull(call);
        assertEquals(NEW_SET_TOTAL_KILLS, call.desc);
        assertEquals(Opcodes.ILOAD, call.getPrevious().getOpcode());
        assertEquals(7, ((org.objectweb.asm.tree.VarInsnNode) call.getPrevious()).var);
        AnnotationNode redirect = handler.visibleAnnotations.stream()
                .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;"))
                .findFirst().orElseThrow();
        AnnotationNode at = (AnnotationNode) redirect.values.get(3);
        assertEquals(NEW_DESPAWN_TARGET, at.values.get(3));

        ClassNode oldSaveData = new ClassNode();
        oldSaveData.name = SRP_SAVE_DATA;
        oldSaveData.superName = "net/minecraft/world/storage/WorldSavedData";
        oldSaveData.methods.add(new org.objectweb.asm.tree.MethodNode(Opcodes.ACC_PUBLIC, "setTotalKills",
                remapper.mapMethodDesc(OLD_SET_TOTAL_KILLS), null, null));
        classes.put(DESPAWN_MIXIN, original);
        assertEquals(0, SrpcotesiaApiCompat.apply(jar, classes, Map.of(SRP_SAVE_DATA, ClassInfo.of(oldSaveData)), newRemapper()));
        assertArrayEquals(original, classes.get(DESPAWN_MIXIN));
    }

    @Test
    void preservesTheDespawnRedirectWhenSharedSrpCallRewritesFollowCotesiaAdaptation() {
        LegacyJar jar = cotesiaJar();
        LegacyRemapper remapper = newRemapper();
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(DESPAWN_MIXIN, oldDespawnRedirect());
        Map<String, ClassInfo> peers = saveDataApiPeers(remapper);

        assertEquals(1, SrpcotesiaApiCompat.apply(jar, classes, peers, remapper));
        assertEquals(0, SrpApiCompat.apply(jar, classes, peers));

        ClassNode adapted = readNode(classes.get(DESPAWN_MIXIN));
        MethodNode handler = findMethod(adapted, DESPAWN_HANDLER, NEW_DESPAWN_REDIRECT);
        assertNotNull(handler);
        MethodInsnNode call = null;
        for (AbstractInsnNode instruction : handler.instructions) if (instruction instanceof MethodInsnNode methodCall) call = methodCall;
        assertNotNull(call);
        assertEquals(NEW_SET_TOTAL_KILLS, call.desc);
        AnnotationNode redirect = methodAnnotation(handler, "Lorg/spongepowered/asm/mixin/injection/Redirect;");
        Object rawAt = annotationValue(redirect, "at");
        assertTrue(rawAt instanceof AnnotationNode at && NEW_DESPAWN_TARGET.equals(annotationValue(at, "target")));
    }

    @Test
    void adaptsCotesiaMergeWrapOperationToForwardTheCurrentSourceId() throws Exception {
        LegacyJar jar = cotesiaJar();
        LegacyRemapper remapper = newRemapper();
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(MERGE_MIXIN, oldMergeWrapOperation());
        ClassInfo saveData = saveDataApiPeers(remapper).get(SRP_SAVE_DATA);
        assertFalse(saveData.hasMethod("setTotalKills", remapper.mapMethodDesc(OLD_SET_TOTAL_KILLS)));
        assertTrue(saveData.hasMethod("setTotalKills", remapper.mapMethodDesc(NEW_SET_TOTAL_KILLS)));

        assertEquals(1, SrpcotesiaApiCompat.apply(jar, classes, saveDataApiPeers(remapper), remapper));

        ClassNode adapted = readNode(classes.get(MERGE_MIXIN));
        MethodNode handler = findMethod(adapted, MERGE_HANDLER, NEW_MERGE_HANDLER_DESC);
        assertNotNull(handler);
        assertEquals(NEW_MERGE_HANDLER_SIGNATURE, handler.signature);
        AnnotationNode wrap = methodAnnotation(handler, "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");
        assertTrue(annotationHasTarget(wrap, NEW_DESPAWN_TARGET));
        org.objectweb.asm.tree.TypeInsnNode array = null;
        MethodInsnNode operationCall = null;
        MethodInsnNode factoryCall = null;
        MethodInsnNode integerValue = null;
        for (AbstractInsnNode instruction : handler.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.TypeInsnNode type && type.getOpcode() == Opcodes.ANEWARRAY) array = type;
            if (instruction instanceof MethodInsnNode call && call.owner.equals(OPERATION) && call.name.equals("call")) operationCall = call;
            if (instruction instanceof MethodInsnNode call && call.owner.equals("com/srpcotesia/util/ParasiteInteractions")
                    && call.name.equals("isFactorySpawned")) factoryCall = call;
            if (instruction instanceof MethodInsnNode call && call.owner.equals("java/lang/Integer")
                    && call.name.equals("valueOf")) integerValue = call;
        }
        assertNotNull(array);
        assertEquals(7, ((org.objectweb.asm.tree.IntInsnNode) array.getPrevious()).operand);
        assertEquals(7, ((VarInsnNode) array.getPrevious().getPrevious()).var);
        assertNotNull(factoryCall);
        assertEquals(8, ((VarInsnNode) factoryCall.getPrevious()).var);
        assertNotNull(integerValue);
        assertEquals(6, ((VarInsnNode) integerValue.getPrevious()).var);
        assertNotNull(operationCall);
        assertEquals(Opcodes.AASTORE, operationCall.getPrevious().getOpcode());
        assertTrue(hasMethodCall(handler, "java/lang/Integer", "valueOf"));
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, handler);
    }

    @Test
    void adaptsCurrentPotionMixinTargetsAndCarriesTheCothSourceId() throws Exception {
        LegacyJar jar = cotesiaJar();
        LegacyRemapper remapper = newRemapper();
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(EFFECT_MIXIN, oldEffectMixin());
        Map<String, byte[]> resources = new LinkedHashMap<>();
        resources.put("mixins.srpcotesia.json", "{\"package\":\"com.srpcotesia.mixin\",\"mixins\":[\"SRPEffectBaseMixin\"]}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Map<String, ClassInfo> peers = new LinkedHashMap<>(currentApiPeers());
        peers.putAll(saveDataApiPeers(remapper));
        peers.put(POTION_COTH, currentCothPeer(remapper));
        peers.put(POTION_NEEDLER, currentNeedlerPeer(remapper));
        peers.put(EFFECT_BASE, currentEffectBasePeer(remapper));

        assertEquals(5, SrpcotesiaApiCompat.apply(jar, classes, resources, peers, remapper));
        assertTrue(resources.get("mixins.srpcotesia.json").length > 0);
        String config = new String(resources.get("mixins.srpcotesia.json"), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(config.contains("compat.LegacyCothEffectsMixin"));
        assertTrue(config.contains("compat.LegacyNeedlerEffectMixin"));

        ClassNode baseMixin = readNode(classes.get(EFFECT_MIXIN));
        assertTrue(baseMixin.methods.stream().noneMatch(method -> method.name.equals(COTH_SPAWN_HANDLER)
                || method.name.equals(COTH_KILLS_HANDLER) || method.name.equals(NEEDLER_HANDLER)));
        MethodNode infect = baseMixin.methods.stream().filter(method -> method.name.equals(INFECT_HANDLER)).findFirst().orElseThrow();
        AnnotationNode infectWrap = methodAnnotation(infect, "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");
        assertEquals(1, annotationValue(infectWrap, "require"));

        ClassNode cothMixin = readNode(classes.get(COTH_MIXIN));
        assertMixinTarget(cothMixin, POTION_COTH);
        MethodNode spawnBridge = findMethod(cothMixin, COTH_SPAWN_HANDLER, NEW_COTH_SPAWN_DESC);
        assertNotNull(spawnBridge);
        assertEquals(List.of("effectCOTHTransform"), annotationValue(methodAnnotation(spawnBridge,
                "Lorg/spongepowered/asm/mixin/injection/Inject;"), "method"));
        assertNotNull(findMethod(cothMixin, COTH_SPAWN_HANDLER + "$legacy", OLD_COTH_SPAWN_DESC));

        MethodNode killsBridge = findMethod(cothMixin, COTH_KILLS_HANDLER, NEW_COTH_KILLS_DESC);
        assertNotNull(killsBridge);
        AnnotationNode wrap = methodAnnotation(killsBridge, "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");
        assertEquals(List.of("effectCOTHextendDuration"), annotationValue(wrap, "method"));
        assertTrue(annotationHasTarget(wrap, NEW_DESPAWN_TARGET));
        assertNotNull(findMethod(cothMixin, COTH_KILLS_HANDLER + "$legacy", OLD_COTH_KILLS_DESC));
        assertFalse(SOURCE_ID_OPERATION.startsWith("com/srpcotesia/mixin/"));
        assertTrue(hasTypeInsn(killsBridge, Opcodes.NEW, SOURCE_ID_OPERATION));

        ClassNode needlerMixin = readNode(classes.get(NEEDLER_MIXIN));
        assertMixinTarget(needlerMixin, POTION_NEEDLER);
        MethodNode needler = findMethod(needlerMixin, NEEDLER_HANDLER, NEEDLER_HANDLER_DESC);
        assertNotNull(needler);
        assertEquals(List.of("effectNeedler"), annotationValue(methodAnnotation(needler,
                "Lorg/spongepowered/asm/mixin/injection/Inject;"), "method"));

        ClassNode operation = readNode(classes.get(SOURCE_ID_OPERATION));
        MethodNode operationCall = findMethod(operation, "call", "([Ljava/lang/Object;)Ljava/lang/Object;");
        assertNotNull(operationCall);
        assertTrue(hasMethodCall(operationCall, "java/util/Arrays", "copyOf"));
        assertTrue(hasMethodCall(operationCall, "com/llamalad7/mixinextras/injector/wrapoperation/Operation", "call"));

        for (var entry : classes.entrySet()) {
            ClassNode node = readNode(entry.getValue());
            for (MethodNode method : node.methods) {
                if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
                    new Analyzer<BasicValue>(new BasicVerifier()).analyze(node.name, method);
                }
            }
        }
        assertSourceIdForwarding(classes.get(SOURCE_ID_OPERATION));
    }

    @Test
    void migratesLegacyPlayerParticleHooksToTheCurrentPacketHandler() throws Exception {
        LegacyRemapper remapper = newRemapper();
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(EFFECT_MIXIN, oldParticleEffectMixin());
        Map<String, byte[]> resources = new LinkedHashMap<>();
        resources.put("mixins.srpcotesia.json", "{\"package\":\"com.srpcotesia.mixin\",\"mixins\":[\"SRPEffectBaseMixin\"]}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Map<String, ClassInfo> peers = currentParticleApiPeers(remapper);

        assertEquals(2, SrpcotesiaApiCompat.apply(cotesiaJar(), classes, resources, peers, remapper));
        String config = new String(resources.get("mixins.srpcotesia.json"), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(config.contains("\"client\":[\"compat.LegacySrpParticleMixin\"]"));

        ClassNode original = readNode(classes.get(EFFECT_MIXIN));
        assertFalse(original.methods.stream().anyMatch(method -> method.name.equals(PARTICLE_INJECT_HANDLER)));
        ClassNode adapted = readNode(classes.get(PARTICLE_MIXIN));
        assertMixinTarget(adapted, PARTICLE_HANDLER);
        assertEquals("java/lang/Object", adapted.superName);

        MethodNode inject = findMethod(adapted, PARTICLE_INJECT_HANDLER, PARTICLE_INJECT_NEW_DESC);
        assertNotNull(inject);
        assertEquals(List.of("spawnParticles"), annotationValue(methodAnnotation(inject,
                "Lorg/spongepowered/asm/mixin/injection/Inject;"), "method"));
        assertNotNull(findCall(inject, PARTICLE_PACKET, "mcc$getParticleEntityId", "()I"));
        assertNotNull(findCall(inject, "net/minecraft/world/World", "getEntityByID", "(I)Lnet/minecraft/entity/Entity;"));
        assertNotNull(findCall(inject, "com/srpcotesia/util/ParasiteInteractions", "getInstance",
                "(Lnet/minecraft/entity/player/EntityPlayer;)Lcom/srpcotesia/capability/ParasitePlayer;"));
        MethodInsnNode cancel = findCall(inject, CALLBACK_INFO, "cancel", "()V");
        assertNotNull(cancel);
        assertEquals(7, ((VarInsnNode) cancel.getPrevious()).var);

        MethodNode wrap = findMethod(adapted, PARTICLE_INJECT_HANDLER, PARTICLE_WRAP_NEW_DESC);
        assertNotNull(wrap);
        AnnotationNode wrapAnnotation = methodAnnotation(wrap,
                "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");
        assertEquals(List.of("spawnParticles"), annotationValue(wrapAnnotation, "method"));
        assertTrue(annotationHasTarget(wrapAnnotation, "Ljava/util/Random;nextFloat()F"));
        AnnotationNode at = (AnnotationNode) ((List<?>) annotationValue(wrapAnnotation, "at")).get(0);
        assertEquals(1, annotationValue(at, "ordinal"));
        assertNotNull(findCall(wrap, PARTICLE_PACKET, "mcc$getParticleEntityId", "()I"));
        assertNotNull(findCall(wrap, "com/srpcotesia/util/ParasiteInteractions", "isParasite",
                "(Lnet/minecraft/entity/EntityLivingBase;)Z"));
        assertTrue(hasFieldRead(wrap, PARTICLE_ENUM, "GCLOUD"));
        boolean hasScale = false;
        for (AbstractInsnNode instruction : wrap.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.LdcInsnNode ldc && Float.valueOf(0.15f).equals(ldc.cst)) hasScale = true;
        }
        assertTrue(hasScale);

        for (MethodNode method : adapted.methods) {
            new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, method);
        }
    }

    private static void assertSourceIdForwarding(byte[] wrapperBytes) throws Exception {
        Map<String, byte[]> definitions = Map.of(OPERATION, operationInterfaceClass(), SOURCE_ID_OPERATION, wrapperBytes);
        ClassLoader loader = new ClassLoader(SrpcotesiaApiCompatTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                byte[] bytes = definitions.get(name.replace('.', '/'));
                if (bytes == null) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) loaded = defineClass(name, bytes, 0, bytes.length);
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        };
        Class<?> operationType = Class.forName(OPERATION.replace('/', '.'), true, loader);
        Object[] forwarded = new Object[1];
        Object delegate = java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[]{operationType}, (proxy, method, args) -> {
            if (method.getName().equals("call")) {
                forwarded[0] = args[0];
                return "delegated-result";
            }
            return null;
        });
        Class<?> wrapperType = Class.forName(SOURCE_ID_OPERATION.replace('/', '.'), true, loader);
        Object wrapper = wrapperType.getConstructor(operationType, int.class).newInstance(delegate, 37);
        Object result = wrapperType.getMethod("call", Object[].class).invoke(wrapper, new Object[]{new Object[]{"original", 9}});
        assertEquals("delegated-result", result);
        assertArrayEquals(new Object[]{"original", 9, 37}, (Object[]) forwarded[0]);
    }

    private static byte[] operationInterfaceClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
                OPERATION, null, "java/lang/Object", null);
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_VARARGS,
                "call", "([Ljava/lang/Object;)Ljava/lang/Object;", null, null).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] oldParticleEffectMixin() {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT;
        node.name = EFFECT_MIXIN;
        node.superName = "net/minecraft/potion/Potion";
        AnnotationNode mixin = new AnnotationNode("Lorg/spongepowered/asm/mixin/Mixin;");
        mixin.values = new ArrayList<>(List.of("value", List.of(Type.getObjectType(EFFECT_BASE))));
        node.invisibleAnnotations = new ArrayList<>(List.of(mixin));

        MethodNode inject = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE, PARTICLE_INJECT_HANDLER,
                PARTICLE_INJECT_OLD_DESC, null, null);
        LabelNode end = new LabelNode();
        inject.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        inject.instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, "net/minecraft/entity/player/EntityPlayer"));
        inject.instructions.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFEQ, end));
        inject.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        inject.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, PARTICLE_ENUM, "GCLOUD", "L" + PARTICLE_ENUM + ";"));
        inject.instructions.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IF_ACMPNE, end));
        inject.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        inject.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/entity/player/EntityPlayer"));
        inject.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/srpcotesia/util/ParasiteInteractions", "getInstance",
                "(Lnet/minecraft/entity/player/EntityPlayer;)L" + PARASITE_PLAYER + ";", false));
        inject.instructions.add(new VarInsnNode(Opcodes.ASTORE, 5));
        inject.instructions.add(new VarInsnNode(Opcodes.ALOAD, 5));
        inject.instructions.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFNULL, end));
        inject.instructions.add(new VarInsnNode(Opcodes.ALOAD, 5));
        inject.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, PARASITE_PLAYER, "isParasite", "()Z", false));
        inject.instructions.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFEQ, end));
        inject.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        inject.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CALLBACK_INFO, "cancel", "()V", false));
        inject.instructions.add(end);
        inject.instructions.add(new InsnNode(Opcodes.RETURN));
        inject.maxStack = 2;
        inject.maxLocals = 6;
        AnnotationNode injectAtHead = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;");
        injectAtHead.values = new ArrayList<>(List.of("method", List.of("spawnParticles"), "at", List.of(at("HEAD", null)),
                "remap", false, "cancellable", true));
        inject.visibleAnnotations = new ArrayList<>(List.of(injectAtHead));
        node.methods.add(inject);

        MethodNode wrap = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE, PARTICLE_INJECT_HANDLER,
                PARTICLE_WRAP_OLD_DESC, null, null);
        wrap.instructions.add(new InsnNode(Opcodes.FCONST_0));
        wrap.instructions.add(new InsnNode(Opcodes.FRETURN));
        wrap.maxStack = 1;
        wrap.maxLocals = 6;
        AnnotationNode at = at("INVOKE", "Ljava/util/Random;nextFloat()F");
        at.values.add("ordinal");
        at.values.add(1);
        AnnotationNode wrapParticle = wrapOperation("spawnParticles", at, 1);
        wrapParticle.values.add("remap");
        wrapParticle.values.add(false);
        wrap.visibleAnnotations = new ArrayList<>(List.of(wrapParticle));
        node.methods.add(wrap);

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] oldEffectMixin() {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT;
        node.name = EFFECT_MIXIN;
        node.superName = "net/minecraft/potion/Potion";
        AnnotationNode mixin = new AnnotationNode("Lorg/spongepowered/asm/mixin/Mixin;");
        mixin.values = new ArrayList<>(List.of("value", List.of(Type.getObjectType(EFFECT_BASE))));
        node.invisibleAnnotations = new ArrayList<>(List.of(mixin));

        MethodNode spawn = simpleMethod(Opcodes.ACC_PRIVATE, COTH_SPAWN_HANDLER, OLD_COTH_SPAWN_DESC, Opcodes.RETURN);
        AnnotationNode injectSpawn = injectAt("effectCOTH", SPAWN_INSIDER_TARGET);
        spawn.visibleAnnotations = new ArrayList<>(List.of(injectSpawn));
        node.methods.add(spawn);

        MethodNode kills = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE, COTH_KILLS_HANDLER, OLD_COTH_KILLS_DESC, null, null);
        kills.instructions.add(new InsnNode(Opcodes.ICONST_0));
        kills.instructions.add(new InsnNode(Opcodes.IRETURN));
        kills.maxStack = 1;
        kills.maxLocals = 10;
        AnnotationNode atKills = at("INVOKE", OLD_DESPAWN_TARGET);
        AnnotationNode wrapKills = wrapOperation("effectCOTH", atKills, 1);
        kills.visibleAnnotations = new ArrayList<>(List.of(wrapKills));
        node.methods.add(kills);

        MethodNode nearby = simpleMethod(Opcodes.ACC_PUBLIC, INFECT_HANDLER,
                "(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/potion/PotionEffect;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lnet/minecraft/entity/EntityLivingBase;I)V",
                Opcodes.RETURN);
        nearby.visibleAnnotations = new ArrayList<>(List.of(wrapOperation(INFECT_TARGET, "InfectNearby", 2)));
        node.methods.add(nearby);

        MethodNode needler = simpleMethod(Opcodes.ACC_PUBLIC, NEEDLER_HANDLER, NEEDLER_HANDLER_DESC, Opcodes.RETURN);
        AnnotationNode injectNeedler = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;");
        injectNeedler.values = new ArrayList<>(List.of("method", List.of("effectNeedler"), "at", List.of(at("HEAD", null)), "cancellable", true));
        needler.visibleAnnotations = new ArrayList<>(List.of(injectNeedler));
        node.methods.add(needler);

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode simpleMethod(int access, String name, String descriptor, int returnOpcode) {
        MethodNode method = new MethodNode(Opcodes.ASM9, access, name, descriptor, null, null);
        if (returnOpcode == Opcodes.IRETURN) method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(returnOpcode));
        method.maxStack = returnOpcode == Opcodes.IRETURN ? 1 : 0;
        method.maxLocals = org.objectweb.asm.Type.getArgumentsAndReturnSizes(descriptor) >>> 2;
        return method;
    }

    private static AnnotationNode injectAt(String method, String target) {
        AnnotationNode inject = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;");
        inject.values = new ArrayList<>(List.of("method", List.of(method), "at", List.of(at("INVOKE", target)), "require", 1));
        return inject;
    }

    private static AnnotationNode wrapOperation(String target, String method, int require) {
        return wrapOperation(method, at("INVOKE", target), require);
    }

    private static AnnotationNode wrapOperation(String method, AnnotationNode at, int require) {
        AnnotationNode wrap = new AnnotationNode("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");
        wrap.values = new ArrayList<>(List.of("method", List.of(method), "at", List.of(at), "require", require));
        return wrap;
    }

    private static AnnotationNode at(String value, String target) {
        AnnotationNode at = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/At;");
        at.values = new ArrayList<>(List.of("value", value));
        if (target != null) {
            at.values.add("target");
            at.values.add(target);
        }
        return at;
    }

    private static Map<String, ClassInfo> currentParticleApiPeers(LegacyRemapper remapper) {
        ClassNode packet = new ClassNode();
        packet.name = PARTICLE_PACKET;
        packet.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "<init>", SrpCotesiaParticleCompat.PARTICLE_CTOR_DESC, null, null));
        packet.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "fromBytes", "(Lio/netty/buffer/ByteBuf;)V", null, null));
        packet.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "toBytes", "(Lio/netty/buffer/ByteBuf;)V", null, null));

        ClassNode handler = new ClassNode();
        handler.name = PARTICLE_HANDLER;
        handler.methods.add(new MethodNode(Opcodes.ACC_PRIVATE, "spawnParticles",
                remapper.mapMethodDesc(SrpCotesiaParticleCompat.HANDLER_SPAWN_DESC), null, null));
        handler.methods.add(new MethodNode(Opcodes.ACC_PRIVATE, "handle",
                remapper.mapMethodDesc(SrpCotesiaParticleCompat.HANDLER_HANDLE_DESC), null, null));

        ClassNode effect = new ClassNode();
        effect.name = EFFECT_BASE;
        effect.methods.add(new MethodNode(Opcodes.ACC_PRIVATE, "sendParticle",
                remapper.mapMethodDesc(SrpCotesiaParticleCompat.SEND_PARTICLE_DESC), null, null));

        List<ClassInfo> infos = new ArrayList<>(List.of(ClassInfo.of(packet), ClassInfo.of(handler), ClassInfo.of(effect)));
        LegacyJar srp = new LegacyJar(Path.of("srparasites.jar"));
        srp.version = LegacyVersion.V1_12_2;
        LegacyModDecl mod = new LegacyModDecl();
        mod.legacyId = "srparasites";
        srp.mods.add(mod);
        SrpApiCompat.addLegacyPeerSignatures(srp, infos);
        Map<String, ClassInfo> peers = new LinkedHashMap<>();
        for (ClassInfo info : infos) peers.put(info.name, info);
        return peers;
    }

    private static ClassInfo currentCothPeer(LegacyRemapper remapper) {
        ClassNode node = new ClassNode();
        node.name = POTION_COTH;
        node.superName = EFFECT_BASE;
        node.methods.add(new MethodNode(Opcodes.ACC_PRIVATE, "effectCOTHTransform", remapper.mapMethodDesc(COTH_TRANSFORM_DESC), null, null));
        node.methods.add(new MethodNode(Opcodes.ACC_PRIVATE, "effectCOTHextendDuration", remapper.mapMethodDesc(COTH_EXTEND_DESC), null, null));
        return ClassInfo.of(node);
    }

    private static ClassInfo currentNeedlerPeer(LegacyRemapper remapper) {
        ClassNode node = new ClassNode();
        node.name = POTION_NEEDLER;
        node.superName = EFFECT_BASE;
        node.methods.add(new MethodNode(Opcodes.ACC_PRIVATE, "effectNeedler", remapper.mapMethodDesc("(Lnet/minecraft/entity/EntityLivingBase;I)V"), null, null));
        return ClassInfo.of(node);
    }

    private static ClassInfo currentEffectBasePeer(LegacyRemapper remapper) {
        ClassNode node = new ClassNode();
        node.name = EFFECT_BASE;
        node.superName = "net/minecraft/potion/Potion";
        MethodNode nearby = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PROTECTED, "InfectNearby",
                remapper.mapMethodDesc("(Lnet/minecraft/entity/EntityLivingBase;I)V"), null, null);
        String living = remapper.map("net/minecraft/entity/EntityLivingBase");
        String potionEffect = remapper.map("net/minecraft/potion/PotionEffect");
        nearby.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        nearby.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        nearby.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, living, "addPotionEffect", "(L" + potionEffect + ";)V", false));
        nearby.instructions.add(new InsnNode(Opcodes.RETURN));
        nearby.maxStack = 2;
        nearby.maxLocals = 3;
        node.methods.add(nearby);
        return ClassInfo.of(node);
    }

    private static ClassNode readNode(byte[] bytes) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static MethodNode findMethod(ClassNode node, String name, String descriptor) {
        return node.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst().orElse(null);
    }

    private static AnnotationNode methodAnnotation(MethodNode method, String descriptor) {
        if (method.visibleAnnotations != null) {
            for (AnnotationNode annotation : method.visibleAnnotations) if (annotation.desc.equals(descriptor)) return annotation;
        }
        if (method.invisibleAnnotations != null) {
            for (AnnotationNode annotation : method.invisibleAnnotations) if (annotation.desc.equals(descriptor)) return annotation;
        }
        return null;
    }

    private static Object annotationValue(AnnotationNode annotation, String key) {
        if (annotation == null || annotation.values == null) return null;
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }

    private static boolean annotationHasTarget(AnnotationNode annotation, String expected) {
        Object rawAt = annotationValue(annotation, "at");
        if (!(rawAt instanceof List<?> atList)) return false;
        for (Object item : atList) {
            if (item instanceof AnnotationNode at && expected.equals(annotationValue(at, "target"))) return true;
        }
        return false;
    }

    private static void assertMixinTarget(ClassNode node, String expected) {
        AnnotationNode mixin = methodAnnotationForClass(node, "Lorg/spongepowered/asm/mixin/Mixin;");
        assertNotNull(mixin);
        Object targets = annotationValue(mixin, "value");
        assertTrue(targets instanceof List<?> list && list.contains(Type.getObjectType(expected)));
    }

    private static AnnotationNode methodAnnotationForClass(ClassNode node, String descriptor) {
        if (node.visibleAnnotations != null) {
            for (AnnotationNode annotation : node.visibleAnnotations) if (annotation.desc.equals(descriptor)) return annotation;
        }
        if (node.invisibleAnnotations != null) {
            for (AnnotationNode annotation : node.invisibleAnnotations) if (annotation.desc.equals(descriptor)) return annotation;
        }
        return null;
    }

    private static boolean hasTypeInsn(MethodNode method, int opcode, String type) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode candidate && candidate.getOpcode() == opcode && candidate.desc.equals(type)) return true;
        }
        return false;
    }

    private static MethodInsnNode findCall(MethodNode method, String owner, String name, String descriptor) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)
                    && call.desc.equals(descriptor)) return call;
        }
        return null;
    }

    private static boolean hasFieldRead(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                    && field.owner.equals(owner) && field.name.equals(name)) return true;
        }
        return false;
    }

    private static boolean hasMethodCall(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return true;
        }
        return false;
    }

    private static int methodCallCount(MethodNode method, String owner, String name) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) count++;
        }
        return count;
    }

    private static Map<String, ClassInfo> saveDataApiPeers(LegacyRemapper remapper) {
        ClassNode saveData = new ClassNode();
        saveData.name = SRP_SAVE_DATA;
        saveData.superName = "net/minecraft/world/storage/WorldSavedData";
        saveData.methods.add(new org.objectweb.asm.tree.MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "get", remapper.mapMethodDesc(NEW_GET), null, null));
        saveData.methods.add(new org.objectweb.asm.tree.MethodNode(Opcodes.ACC_PUBLIC, "setCooldown", remapper.mapMethodDesc(NEW_SET_COOLDOWN), null, null));
        saveData.methods.add(new org.objectweb.asm.tree.MethodNode(Opcodes.ACC_PUBLIC, "setEvolutionPhase", remapper.mapMethodDesc(NEW_SET_PHASE), null, null));
        saveData.methods.add(new org.objectweb.asm.tree.MethodNode(Opcodes.ACC_PUBLIC, "setTotalKills", remapper.mapMethodDesc(NEW_SET_TOTAL_KILLS), null, null));
        return Map.of(SRP_SAVE_DATA, ClassInfo.of(saveData));
    }

    private static byte[] oldSaveDataCalls(String caller) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, caller, null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "calls", "()V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, SRP_SAVE_DATA, "get", OLD_GET, false);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ICONST_2);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SRP_SAVE_DATA, "setCooldown", OLD_SET_COOLDOWN, false);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.ICONST_2);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SRP_SAVE_DATA, "setEvolutionPhase", OLD_SET_PHASE, false);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.ICONST_2);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SRP_SAVE_DATA, "setTotalKills", OLD_SET_TOTAL_KILLS, false);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(6, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] oldMergeWrapOperation() {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT;
        node.name = MERGE_MIXIN;
        node.superName = "java/lang/Object";
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                MERGE_HANDLER, OLD_MERGE_HANDLER_DESC, OLD_MERGE_HANDLER_SIGNATURE, null);
        org.objectweb.asm.tree.LabelNode factorySpawned = new org.objectweb.asm.tree.LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 7));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/srpcotesia/util/ParasiteInteractions",
                "isFactorySpawned", "(L" + ENTITY_PARASITE + ";)Z", false));
        method.instructions.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFNE, factorySpawned));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 6));
        method.instructions.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, 6));
        method.instructions.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        for (int index = 0; index < 6; index++) {
            method.instructions.add(new InsnNode(Opcodes.DUP));
            method.instructions.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, index));
            switch (index) {
                case 0 -> method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                case 1 -> addIntegerArgument(method, 1);
                case 2 -> addIntegerArgument(method, 2);
                case 3 -> addBooleanArgument(method, 3);
                case 4 -> method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
                case 5 -> addBooleanArgument(method, 5);
                default -> throw new AssertionError(index);
            }
            method.instructions.add(new InsnNode(Opcodes.AASTORE));
        }
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, OPERATION, "call",
                "([Ljava/lang/Object;)Ljava/lang/Object;", true));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Boolean"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(factorySpawned);
        method.instructions.add(new org.objectweb.asm.tree.FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 5;
        method.maxLocals = 10;
        AnnotationNode at = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/At;");
        at.values = new ArrayList<>(List.of("value", "INVOKE", "target", OLD_DESPAWN_TARGET, "remap", false));
        AnnotationNode wrap = new AnnotationNode("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");
        wrap.values = new ArrayList<>(List.of("method", List.of("merge"), "at", List.of(at), "require", 1));
        method.visibleAnnotations = new ArrayList<>(List.of(wrap));
        node.methods.add(method);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void addIntegerArgument(MethodNode method, int local) {
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, local));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
    }

    private static void addBooleanArgument(MethodNode method, int local) {
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, local));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false));
    }

    private static byte[] oldDespawnRedirect() {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = DESPAWN_MIXIN;
        node.superName = "java/lang/Object";
        var method = new org.objectweb.asm.tree.MethodNode(Opcodes.ACC_PRIVATE, DESPAWN_HANDLER, OLD_DESPAWN_REDIRECT, null, null);
        method.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, 4));
        method.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 5));
        method.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, 6));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SRP_SAVE_DATA, "setTotalKills", OLD_SET_TOTAL_KILLS, false));
        method.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IRETURN));
        method.maxStack = 6;
        method.maxLocals = 7;
        AnnotationNode at = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/At;");
        at.values = new ArrayList<>();
        at.values.add("value");
        at.values.add("INVOKE");
        at.values.add("target");
        at.values.add(OLD_DESPAWN_TARGET);
        at.values.add("remap");
        at.values.add(false);
        AnnotationNode redirect = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Redirect;");
        redirect.values = new ArrayList<>();
        redirect.values.add("method");
        redirect.values.add(java.util.List.of("despawnEntity()V"));
        redirect.values.add("at");
        redirect.values.add(at);
        redirect.values.add("require");
        redirect.values.add(1);
        method.visibleAnnotations = new ArrayList<>();
        method.visibleAnnotations.add(redirect);
        node.methods.add(method);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] oldSaltDecorator() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, SALT_DECORATOR, null, BIOME_DECORATOR, null);
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, BIOME_DECORATOR, "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static LegacyRemapper newRemapper() {
        return new LegacyRemapper(LegacyVersion.V1_12_2, new McpMappings(Map.of(), Map.of()), null, null);
    }
}
