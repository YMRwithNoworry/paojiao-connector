package org.kylepitcock.legacyconnector.loader.transform.compat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.kylepitcock.legacyconnector.loader.LegacyJar;
import org.kylepitcock.legacyconnector.loader.LegacyModDecl;
import org.kylepitcock.legacyconnector.loader.LegacyVersion;
import org.kylepitcock.legacyconnector.loader.shim.ClassInfo;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;

class SrpApiCompatTest {
    private static final String SRP_BLOCKS = "com/dhanantry/scapeandrunparasites/init/SRPBlocks";
    private static final String BLOCK_GORE = "com/dhanantry/scapeandrunparasites/block/BlockGore";
    private static final String GORE_ENUM = BLOCK_GORE + "$EnumType";
    private static final String GORE_DESC = "L" + BLOCK_GORE + ";";
    private static final String ENUM_DESC = "L" + GORE_ENUM + ";";
    private static final String I_BLOCK_STATE = "net/minecraft/block/state/IBlockState";
    private static final String STATE_DESC = "L" + I_BLOCK_STATE + ";";
    private static final String PROPERTY_ENUM_DESC = "Lnet/minecraft/block/properties/PropertyEnum;";
    private static final String WITH_PROPERTY_DESC = "(Lnet/minecraft/block/properties/IProperty;Ljava/lang/Comparable;)" + STATE_DESC;
    private static final String ENTITY_PARASITE = "com/dhanantry/scapeandrunparasites/entity/ai/misc/EntityParasiteBase";
    private static final String ENTITY_WYMO = "com/dhanantry/scapeandrunparasites/entity/monster/primitive/EntityWymo";
    private static final String SRP_TILE_ENTITY_DOD = "com/dhanantry/scapeandrunparasites/entity/tile/TileEntityDod";
    private static final String SRP_HUMAN_RENDERER = "com/dhanantry/scapeandrunparasites/client/renderer/entity/infected/RenderInfHuman";
    private static final String CURRENT_HUMAN_ALT_TEXTURE = "TEXTURE_ALT";
    private static final String LEGACY_HUMAN_TEXTURE = "TEXTURE3";
    private static final String RESOURCE_LOCATION = "net/minecraft/util/ResourceLocation";
    private static final String HUMAN_TEXTURE_DESC = "L" + RESOURCE_LOCATION + ";";
    private static final String SRP_REINFORCEMENT_HELPER =
            "org/kylepitcock/legacyconnector/v1_12_2/compat/srparasites/SrpLegacyReinforcementChance1122";
    private static final String ENTITY_PINFECTED = "com/dhanantry/scapeandrunparasites/entity/ai/misc/EntityPInfected";
    private static final String ENTITY_PFERAL = "com/dhanantry/scapeandrunparasites/entity/ai/misc/EntityPFeral";
    private static final String SRP_EXTRA_INF_CONVERT_UNIT = "energon/srpextra/init/SRPETransform$InfConvertUnit";
    private static final String SRP_EXTRA_REMAINS_CANISTER = "energon/srpextra/items/SRPEItemRemainsCanister";
    private static final String SRP_GORE_FAMILY_HELPER =
            "org/kylepitcock/legacyconnector/v1_12_2/compat/srparasites/SrpGoreFamily1122";
    private static final String ENTITY_TOXIC_CLOUD = "com/dhanantry/scapeandrunparasites/entity/EntityToxicCloud";
    private static final String ENTITY_LIVING_BASE = "net/minecraft/entity/EntityLivingBase";
    private static final String OLD_MERGE_DESC = "(L" + ENTITY_PARASITE + ";ILjava/lang/String;)Z";
    private static final String OLD_SPAWN_M_DESC = "(L" + ENTITY_PARASITE + ";[Ljava/lang/String;IZLjava/lang/String;)V";
    private static final String SRP_MERGE_HELPER = "org/kylepitcock/legacyconnector/v1_12_2/compat/srparasites/SrpMergeCompat1122";
    private static final String SRP_SPAWN_HELPER = "org/kylepitcock/legacyconnector/v1_12_2/compat/srparasites/SrpSpawnCompat1122";
    private static final String SPAWN_COUNT_DESC = "([Ljava/lang/String;Ljava/util/Random;)I";
    private static final String SRP_CONFIG = "com/dhanantry/scapeandrunparasites/util/config/SRPConfig";
    private static final String SRP_CONFIG_SYSTEMS = "com/dhanantry/scapeandrunparasites/util/config/SRPConfigSystems";
    private static final String SRP_CONFIG_MOBS = "com/dhanantry/scapeandrunparasites/util/config/SRPConfigMobs";
    private static final String[] LEGACY_BECKON_TIMER_FIELDS = {
            "mcc$legacyVenkrolBlockTime", "mcc$legacyVenkrolsiiBlockTime", "mcc$legacyVenkrolsiiiBlockTime"
    };
    private static final String[] LEGACY_BECKON_TIMER_INIT_METHODS = {
            "initbeckonSIConfig", "initbeckonSIIConfig", "initbeckonSIIIConfig"
    };
    private static final String[] LEGACY_BECKON_TIMER_KEYS = {
            "Stage I Beckon Block Infestation Cooldown", "Stage II Beckon Block Infestation Cooldown",
            "Stage III Beckon Block Infestation Cooldown"
    };
    private static final String[] LEGACY_BECKON_TIMER_CATEGORIES = {
            "srparasites:beckon_i", "srparasites:beckon_ii", "srparasites:beckon_iii"
    };
    private static final int[] LEGACY_BECKON_TIMER_DEFAULTS = {60, 55, 50};
    private static final String LEGACY_BECKON_TIMER_METHOD = "getTimer";
    private static final String LEGACY_BECKON_TIMER_HELPER =
            "org/kylepitcock/legacyconnector/v1_12_2/compat/srparasites/SrpLegacyBlockTimer1122";
    private static final String LEGACY_BECKON_TIMER_HELPER_DESC = "(IIII)I";
    private static final String SRP_EVENT_ENTITY = "com/dhanantry/scapeandrunparasites/util/ParasiteEventEntity";
    private static final String AI_AVOID = "com/dhanantry/scapeandrunparasites/entity/ai/EntityAIAvoidOrAttack";
    private static final String AI_MELEE = "com/dhanantry/scapeandrunparasites/entity/ai/EntityAIAttackMeleeStatus";
    private static final String OLD_AVOID_CTOR = "(L" + ENTITY_PARASITE + ";F)V";
    private static final String NEW_AVOID_CTOR = "(L" + ENTITY_PARASITE + ";FII)V";
    private static final String OLD_MELEE_CTOR = "(L" + ENTITY_PARASITE + ";DZDI)V";
    private static final String NEW_MELEE_CTOR = "(L" + ENTITY_PARASITE + ";DZD)V";
    private static final String SRP_CRUX = "com/dhanantry/scapeandrunparasites/entity/monster/crude/EntityCrux";
    private static final String SRP_CRUX_A = SRP_CRUX + "A";
    private static final String SRP_SAVE_DATA = "com/dhanantry/scapeandrunparasites/world/SRPSaveData";
    private static final String SRP_EVENT_WORLD = "com/dhanantry/scapeandrunparasites/util/ParasiteEventWorld";
    private static final String SRP_SPAWNING = "com/dhanantry/scapeandrunparasites/init/SRPSpawning";
    private static final String BECKON_INFESTATION = "com/dhanantry/scapeandrunparasites/util/convert/BeckonBlockInfestation";
    private static final String BLOCK_INFESTED_COUNT = "blockInfestedCount";
    private static final String BLOCK_PARASITE_COUNT = "blockParasiteCount";
    private static final String BLOCK_PARASITE_SPREADING = "com/dhanantry/scapeandrunparasites/block/BlockParasiteSpreading";
    private static final String BIOME_PARASITE_BASE = "com/dhanantry/scapeandrunparasites/world/biome/BiomeParasiteBase";
    private static final String SRP_PACKET_BIOME_CHANGE = "com/dhanantry/scapeandrunparasites/network/SRPPacketBiomeChange";
    private static final String CONFIGURATION = "net/minecraftforge/common/config/Configuration";
    private static final String RANDOM = "java/util/Random";
    private static final String NEW_BIOME_PACKET_CTOR = "(Lnet/minecraft/util/math/BlockPos;ZI)V";
    private static final String OLD_BIOME_PACKET_CTOR = "(Lnet/minecraft/util/math/BlockPos;Z)V";
    private static final String OLD_POSITION_TO_PARASITE_BIOME = "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V";
    private static final String NEW_POSITION_TO_PARASITE_BIOME = "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;I)V";
    private static final String OLD_PARASITE_GENERATOR = "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Ljava/util/Random;)V";
    private static final String BIOME_UPDATE_QUEUE = "com/dhanantry/scapeandrunparasites/util/handlers/BiomeUpdateQueue";
    private static final String BIOME_UPDATE_ENQUEUE_DESC = "(IIIZII)V";
    private static final String BIOMES = "net/minecraft/init/Biomes";
    private static final String BIOME = "net/minecraft/world/biome/Biome";
    private static final String WORLD = "net/minecraft/world/World";
    private static final String WORLD_PROVIDER = "net/minecraft/world/WorldProvider";
    private static final String BLOCK_POS = "net/minecraft/util/math/BlockPos";
    private static final String OLD_DIRECTION_DESC = "(L" + BLOCK_POS + ";I)L" + BLOCK_POS + ";";
    private static final String OLD_INFESTATION_SPAWN_DESC = "(L" + WORLD + ";L" + BLOCK_POS + ";Ljava/util/Random;)V";
    private static final String OLD_SAVE_DATA_GET_DESC = "(L" + WORLD + ";)L" + SRP_SAVE_DATA + ";";
    private static final String NEW_SAVE_DATA_GET_DESC = "(L" + WORLD + ";I)L" + SRP_SAVE_DATA + ";";
    private static final String OLD_SET_TOTAL_KILLS_DESC = "(IIZL" + WORLD + ";Z)Z";
    private static final String NEW_SET_TOTAL_KILLS_DESC = "(IIZL" + WORLD + ";ZI)Z";
    private static final String ENTITY_P_CRUDE = "com/dhanantry/scapeandrunparasites/entity/ai/misc/EntityPCrude";
    private static final String ENTITY_CUSTOM_ATTACK = "com/dhanantry/scapeandrunparasites/entity/ai/misc/EntityCutomAttack";
    private static final String ATTACK_CHECK = "checkAndPerformAttack";
    private static final String ATTACK_CHECK_DESC = "(Lnet/minecraft/entity/EntityLivingBase;D)V";
    private static final String LEGACY_USE_VINNIT = "useVinniT";
    private static final String LEGACY_EPIL_CHANCE_COTH = "epelChanceCOTH";
    private static final String LEGACY_ATTACK_COOLDOWN = "mcc$legacyAttackCooldown";
    private static final String HAS_LEGACY_ATTACK_COOLDOWN = "mcc$hasLegacyAttackCooldown";
    private static final String GET_ATTACK_COOLDOWN = "mcc$getAttackCooldown";

    @Test
    void repairsKnownMalformedStainStairsAssetOnlyInSrparasites() {
        String path = "assets/srparasites/models/block/infestedstainstairs_outer.json";
        String malformedJson = "{\"parent\":\"block/outer_stairs\",\"textures\":{\"bottom\":\"srparasites:blocks/infestedstain\","
                + "\"top\": \"srparasites:blocks/infestedstain,\n"
                + "\"side\":\"srparasites:blocks/infestedstain\"}}";
        byte[] malformed = malformedJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Map<String, byte[]> resources = new LinkedHashMap<>();
        resources.put(path, malformed);

        assertEquals(1, SrpApiCompat.repairMalformedAssets(srpParasitesJar(), resources));
        String repaired = new String(resources.get(path), java.nio.charset.StandardCharsets.UTF_8);
        var model = com.google.gson.JsonParser.parseString(repaired).getAsJsonObject();
        assertEquals("block/outer_stairs", model.get("parent").getAsString());
        assertEquals("srparasites:blocks/infestedstain", model.getAsJsonObject("textures").get("top").getAsString());
        assertEquals(0, SrpApiCompat.repairMalformedAssets(srpParasitesJar(), resources));

        Map<String, byte[]> addonResources = new LinkedHashMap<>();
        addonResources.put(path, malformed);
        assertEquals(0, SrpApiCompat.repairMalformedAssets(cotesiaJar(), addonResources));
        assertArrayEquals(malformed, addonResources.get(path));
    }

    @Test
    void addsBehaviorEquivalentLegacyAiConstructorsToTheCurrentSrpPeer() throws Exception {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(AI_AVOID, currentAiClass(AI_AVOID, NEW_AVOID_CTOR, false));
        classes.put(AI_MELEE, currentAiClass(AI_MELEE, NEW_MELEE_CTOR, true));
        Map<String, ClassInfo> peers = new LinkedHashMap<>(splitGorePeers());
        peers.put(AI_AVOID, readInfo(classes.get(AI_AVOID)));
        peers.put(AI_MELEE, readInfo(classes.get(AI_MELEE)));

        List<ClassInfo> scanned = new java.util.ArrayList<>(peers.values());
        ClassInfo scannedMelee = findInfo(scanned, AI_MELEE);
        remapMethodDescriptor(scannedMelee, ATTACK_CHECK + ATTACK_CHECK_DESC,
                ATTACK_CHECK + "(Llegacyconnector/v1_12_2/net/minecraft/entity/EntityLivingBase;D)V");
        SrpApiCompat.addLegacyPeerSignatures(srpParasitesJar(), scanned);
        for (ClassInfo info : scanned) peers.put(info.name, info);
        assertTrue(findInfo(scanned, AI_AVOID).hasMethod("<init>", OLD_AVOID_CTOR));
        assertTrue(findInfo(scanned, AI_MELEE).hasMethod("<init>", OLD_MELEE_CTOR));

        assertEquals(2, SrpApiCompat.apply(srpParasitesJar(), classes, peers));

        ClassNode avoid = read(classes.get(AI_AVOID));
        MethodNode avoidBridge = findMethod(avoid, "<init>", OLD_AVOID_CTOR);
        assertTrue(avoidBridge != null);
        MethodInsnNode avoidTarget = findCall(avoidBridge, AI_AVOID, "<init>", NEW_AVOID_CTOR);
        assertTrue(avoidTarget != null);
        assertEquals(Opcodes.ICONST_2, avoidTarget.getPrevious().getOpcode());
        assertEquals(Opcodes.BIPUSH, avoidTarget.getPrevious().getPrevious().getOpcode());
        assertEquals(10, ((org.objectweb.asm.tree.IntInsnNode) avoidTarget.getPrevious().getPrevious()).operand);

        ClassNode melee = read(classes.get(AI_MELEE));
        MethodNode meleeBridge = findMethod(melee, "<init>", OLD_MELEE_CTOR);
        assertTrue(meleeBridge != null);
        MethodInsnNode meleeTarget = findCall(meleeBridge, AI_MELEE, "<init>", NEW_MELEE_CTOR);
        assertTrue(meleeTarget != null);
        FieldInsnNode cooldownWrite = null;
        FieldInsnNode hasCooldownWrite = null;
        for (AbstractInsnNode instruction : meleeBridge.instructions) {
            if (!(instruction instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.PUTFIELD) continue;
            if (field.name.equals(LEGACY_ATTACK_COOLDOWN)) cooldownWrite = field;
            if (field.name.equals(HAS_LEGACY_ATTACK_COOLDOWN)) hasCooldownWrite = field;
        }
        assertTrue(cooldownWrite != null);
        assertEquals("I", cooldownWrite.desc);
        assertEquals(Opcodes.ILOAD, cooldownWrite.getPrevious().getOpcode());
        assertEquals(7, ((VarInsnNode) cooldownWrite.getPrevious()).var);
        assertTrue(hasCooldownWrite != null);
        assertEquals("Z", hasCooldownWrite.desc);

        MethodNode attackCheck = findMethod(melee, ATTACK_CHECK, ATTACK_CHECK_DESC);
        assertTrue(findCall(attackCheck, AI_MELEE, GET_ATTACK_COOLDOWN, "()I") != null);
        assertTrue(findCall(attackCheck, ENTITY_PARASITE, "getAttackSpeed", "()I") == null);
        MethodNode attackCooldownGetter = findMethod(melee, GET_ATTACK_COOLDOWN, "()I");
        assertTrue(attackCooldownGetter != null);
        assertTrue(findCall(attackCooldownGetter, ENTITY_PARASITE, "getAttackSpeed", "()I") != null);
        boolean hasStackMapFrame = false;
        for (AbstractInsnNode instruction : attackCooldownGetter.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.FrameNode) hasStackMapFrame = true;
        }
        assertTrue(hasStackMapFrame);

        for (ClassNode node : List.of(avoid, melee)) {
            for (MethodNode method : node.methods) {
                if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
                    new Analyzer<BasicValue>(new BasicVerifier()).analyze(node.name, method);
                }
            }
        }

    }

    @Test
    void restoresLegacySkinOneTextureAliasOnCurrentSrpRenderer() throws Exception {
        byte[] current = currentHumanRendererClass();
        ClassInfo scanned = readInfo(current);
        Integer textureAccess = scanned.fields.remove(CURRENT_HUMAN_ALT_TEXTURE + ":" + HUMAN_TEXTURE_DESC);
        assertTrue(textureAccess != null);
        String linkedTextureDesc = "L" + LegacyVersion.V1_12_2.linkPrefix() + RESOURCE_LOCATION + ";";
        scanned.fields.put(CURRENT_HUMAN_ALT_TEXTURE + ":" + linkedTextureDesc, textureAccess);
        List<ClassInfo> scannedPeers = new java.util.ArrayList<>(List.of(scanned));

        SrpApiCompat.addLegacyPeerSignatures(srpParasitesJar(), scannedPeers);
        ClassInfo peer = findInfo(scannedPeers, SRP_HUMAN_RENDERER);
        String aliasKey = LEGACY_HUMAN_TEXTURE + ":" + linkedTextureDesc;
        assertTrue(peer.fields.containsKey(aliasKey));
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                peer.fields.get(aliasKey));

        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(SRP_HUMAN_RENDERER, current);
        assertEquals(1, SrpApiCompat.apply(srpParasitesJar(), classes, Map.of(SRP_HUMAN_RENDERER, peer)));

        ClassNode adapted = read(classes.get(SRP_HUMAN_RENDERER));
        FieldNode alias = adapted.fields.stream().filter(field -> field.name.equals(LEGACY_HUMAN_TEXTURE)
                && field.desc.equals(HUMAN_TEXTURE_DESC)).findFirst().orElseThrow();
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                alias.access);
        MethodNode initializer = findMethod(adapted, "<clinit>", "()V");
        FieldInsnNode source = findFieldAccess(initializer, Opcodes.GETSTATIC, SRP_HUMAN_RENDERER,
                CURRENT_HUMAN_ALT_TEXTURE, HUMAN_TEXTURE_DESC);
        FieldInsnNode target = findFieldAccess(initializer, Opcodes.PUTSTATIC, SRP_HUMAN_RENDERER,
                LEGACY_HUMAN_TEXTURE, HUMAN_TEXTURE_DESC);
        assertTrue(source != null);
        assertTrue(target != null);
        assertTrue(initializer.instructions.indexOf(source) < initializer.instructions.indexOf(target));
    }

    @Test
    void restoresTheLegacyToxicCloudOwnerContractOnTheCurrentSrpPeer() throws Exception {
        byte[] current = currentToxicCloudClass();
        List<ClassInfo> scanned = new java.util.ArrayList<>(List.of(readInfo(current)));
        SrpApiCompat.addLegacyPeerSignatures(srpParasitesJar(), scanned);
        ClassInfo peer = findInfo(scanned, ENTITY_TOXIC_CLOUD);
        String linkedLivingBase = "L" + LegacyVersion.V1_12_2.linkPrefix() + ENTITY_LIVING_BASE + ";";
        assertTrue(peer.hasMethod("getOwner", "()" + linkedLivingBase));
        assertTrue(peer.hasMethod("setOwner", "(" + linkedLivingBase + ")V"));

        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(ENTITY_TOXIC_CLOUD, current);
        assertEquals(1, SrpApiCompat.apply(srpParasitesJar(), classes, Map.of(ENTITY_TOXIC_CLOUD, readInfo(current))));

        ClassNode adapted = read(classes.get(ENTITY_TOXIC_CLOUD));
        MethodNode getter = findMethod(adapted, "getOwner", "()L" + ENTITY_LIVING_BASE + ";");
        MethodNode setter = findMethod(adapted, "setOwner", "(L" + ENTITY_LIVING_BASE + ";)V");
        MethodNode currentSetter = findMethod(adapted, "setOwner", "(L" + ENTITY_PARASITE + ";)V");
        assertTrue(getter != null);
        assertTrue(setter != null);
        assertTrue(findCall(getter, ENTITY_TOXIC_CLOUD, "getOwner", "()L" + ENTITY_PARASITE + ";") != null);
        assertTrue(findCall(setter, ENTITY_TOXIC_CLOUD, "setOwner", "(L" + ENTITY_PARASITE + ";)V") != null);
        assertTrue(currentSetter != null);
        for (MethodNode method : List.of(getter, setter, currentSetter)) {
            new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, method);
        }
    }

    @Test
    void restoresTheRemovedSpawnMEntryPointWithItsLegacyCallContract() throws Exception {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(SRP_EVENT_ENTITY, currentSpawnListClass());

        assertEquals(1, SrpApiCompat.apply(srpParasitesJar(), classes, Map.of()));

        ClassNode adapted = read(classes.get(SRP_EVENT_ENTITY));
        MethodNode bridge = findMethod(adapted, "spawnM", OLD_SPAWN_M_DESC);
        assertTrue(bridge != null);
        assertTrue(findCall(bridge, SRP_SPAWN_HELPER, "rollLegacySpawnCount", SPAWN_COUNT_DESC) != null);
        assertTrue(findCall(bridge, "net/minecraft/entity/EntityList", "func_188429_b",
                "(Lnet/minecraft/util/ResourceLocation;L" + WORLD + ";)Lnet/minecraft/entity/Entity;") != null);
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, bridge);

        List<ClassInfo> scanned = new java.util.ArrayList<>(List.of(readInfo(currentSpawnListClass())));
        SrpApiCompat.addLegacyPeerSignatures(srpParasitesJar(), scanned);
        ClassInfo spawnPeer = findInfo(scanned, SRP_EVENT_ENTITY);
        assertTrue(spawnPeer.hasMethod("spawnM", OLD_SPAWN_M_DESC));
        String prefix = LegacyVersion.V1_12_2.linkPrefix();
        String createEntityDesc = "(L" + prefix + "net/minecraft/util/ResourceLocation;L" + prefix
                + "net/minecraft/world/World;)L" + prefix + "net/minecraft/entity/Entity;";
        String createEntitySite = "L" + prefix + "net/minecraft/entity/EntityList;createEntityByIDFromName" + createEntityDesc;
        assertEquals(1, spawnPeer.instructionSiteCount("spawnM" + OLD_SPAWN_M_DESC, "INVOKE",
                createEntitySite, Opcodes.INVOKESTATIC));

        Map<String, byte[]> addonClasses = new LinkedHashMap<>();
        byte[] addonCall = oldSpawnMCall("addon/DeathHook");
        addonClasses.put("addon/DeathHook", addonCall);
        assertEquals(0, SrpApiCompat.apply(cotesiaJar(), addonClasses, Map.of(SRP_EVENT_ENTITY, findInfo(scanned, SRP_EVENT_ENTITY))));
        assertArrayEquals(addonCall, addonClasses.get("addon/DeathHook"));
    }

    @Test
    void restoresTheRemovedMergeEntryPointAgainstTheCurrentSrpApi() throws Exception {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        byte[] original = plainClass(SRP_EVENT_ENTITY);
        classes.put(SRP_EVENT_ENTITY, original);
        Map<String, ClassInfo> peers = currentMergePeers();
        List<ClassInfo> scanned = new java.util.ArrayList<>(peers.values());
        SrpApiCompat.addLegacyPeerSignatures(srpParasitesJar(), scanned);
        peers.clear();
        for (ClassInfo info : scanned) peers.put(info.name, info);
        assertTrue(peers.get(SRP_EVENT_ENTITY).hasMethod("merge", OLD_MERGE_DESC));
        String prefix = LegacyVersion.V1_12_2.linkPrefix();
        String linkedSetTotalKills = "L" + SRP_SAVE_DATA + ";setTotalKills(IIZL" + prefix + WORLD + ";ZI)Z";
        assertEquals(1, peers.get(SRP_EVENT_ENTITY).instructionSiteCount("merge" + OLD_MERGE_DESC,
                "INVOKE", linkedSetTotalKills, Opcodes.INVOKEVIRTUAL));

        assertEquals(1, SrpApiCompat.apply(srpParasitesJar(), classes, peers));

        ClassNode adapted = read(classes.get(SRP_EVENT_ENTITY));
        MethodNode merge = findMethod(adapted, "merge", OLD_MERGE_DESC);
        assertTrue(merge != null);
        assertTrue(findCall(merge, SRP_MERGE_HELPER, "select", "([Ljava/lang/String;IZ)Ljava/lang/String;") != null);
        assertTrue(findCall(merge, SRP_MERGE_HELPER, "createMergedEntity",
                "(Lnet/minecraft/entity/Entity;L" + WORLD + ";Ljava/lang/String;)Lnet/minecraft/entity/EntityLiving;") != null);
        assertTrue(findCall(merge, SRP_SAVE_DATA, "get", NEW_SAVE_DATA_GET_DESC) != null);
        assertTrue(findCall(merge, SRP_SAVE_DATA, "setTotalKills", NEW_SET_TOTAL_KILLS_DESC) != null);
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, merge);
    }

    @Test
    void adaptsLegacySrpSaveDataCallsToTheCurrentDimensionAwareApi() throws Exception {
        String fixture = "energon/srpextra/TestSaveDataCalls";
        byte[] original = oldSaveDataCalls(fixture);
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(fixture, original);
        Map<String, ClassInfo> peers = currentSaveDataPeers();

        assertEquals(2, SrpApiCompat.apply(srpExtraJar(), classes, peers));

        ClassNode adapted = read(classes.get(fixture));
        MethodNode get = findMethod(adapted, "getSaveData", "(L" + WORLD + ";)L" + SRP_SAVE_DATA + ";");
        MethodInsnNode getCall = findCall(get, SRP_SAVE_DATA, "get", NEW_SAVE_DATA_GET_DESC);
        assertTrue(getCall != null);
        assertEquals(Opcodes.INVOKESTATIC, getCall.getOpcode());
        assertEquals(2, get.maxStack);
        MethodInsnNode dimension = (MethodInsnNode) getCall.getPrevious();
        assertEquals(WORLD_PROVIDER, dimension.owner);
        assertEquals("getDimension", dimension.name);
        FieldInsnNode provider = (FieldInsnNode) dimension.getPrevious();
        assertEquals(WORLD, provider.owner);
        assertEquals("field_73011_w", provider.name);
        assertEquals(Opcodes.DUP, provider.getPrevious().getOpcode());

        MethodNode addPoints = findMethod(adapted, "addPoints", "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";Z)Z");
        MethodInsnNode setCall = findCall(addPoints, SRP_SAVE_DATA, "setTotalKills", NEW_SET_TOTAL_KILLS_DESC);
        assertTrue(setCall != null);
        assertEquals(Opcodes.INVOKEVIRTUAL, setCall.getOpcode());
        assertEquals(Opcodes.ICONST_0, setCall.getPrevious().getOpcode());
        assertEquals(7, addPoints.maxStack);
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, get);
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, addPoints);

        classes.put(fixture, original);
        assertEquals(0, SrpApiCompat.apply(srpExtraJar(), classes, Map.of()));
        assertArrayEquals(original, classes.get(fixture));
    }

    @Test
    void redirectsMovedInfestationGeneratorsToTheCurrentBeckonImplementation() throws Exception {
        String fixture = "energon/srpextra/TestInfestationSpawnCalls";
        byte[] original = oldInfestationGeneratorCalls(fixture);
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(fixture, original);

        assertEquals(2, SrpApiCompat.apply(srpExtraJar(), classes, currentBeckonSpawnPeers()));

        ClassNode adapted = read(classes.get(fixture));
        for (String methodName : List.of("spawnGenFeatureInfested", "spawnGenRoofInfested")) {
            MethodNode method = findMethod(adapted, methodName, OLD_INFESTATION_SPAWN_DESC);
            assertTrue(findCall(method, BECKON_INFESTATION, methodName, OLD_INFESTATION_SPAWN_DESC) != null);
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) assertTrue(!call.owner.equals(SRP_EVENT_WORLD));
            }
            new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, method);
        }

        classes.put(fixture, original);
        assertEquals(0, SrpApiCompat.apply(srpExtraJar(), classes, Map.of()));
        assertArrayEquals(original, classes.get(fixture));
    }

    @Test
    void remapsMovedInfestationCountersWithoutCombiningTheirState() throws Exception {
        String fixture = "energon/srpextra/TestInfestationCounters";
        Map<String, byte[]> addonClasses = new LinkedHashMap<>();
        addonClasses.put(fixture, oldInfestationCounterReferences(fixture));
        ClassInfo beckon = currentBeckonSpawnPeers().get(BECKON_INFESTATION);
        ClassInfo parasiteSpreading = currentParasiteBlockCounterPeer();
        ClassInfo eventWorld = new ClassInfo(SRP_EVENT_WORLD, "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC);
        List<ClassInfo> scanned = new java.util.ArrayList<>(List.of(beckon, parasiteSpreading, eventWorld));

        SrpApiCompat.addLegacyPeerSignatures(srpParasitesJar(), scanned);
        assertTrue(!eventWorld.hasField(BLOCK_PARASITE_COUNT, "I"));
        Map<String, ClassInfo> peers = new LinkedHashMap<>();
        for (ClassInfo info : scanned) peers.put(info.name, info);
        assertEquals(3, SrpApiCompat.apply(srpExtraJar(), addonClasses, peers));

        ClassNode adapted = read(addonClasses.get(fixture));
        MethodNode getInfestedCount = findMethod(adapted, "getInfestedCount", "()I");
        FieldInsnNode infestedCount = null;
        for (AbstractInsnNode instruction : getInfestedCount.instructions) {
            if (instruction instanceof FieldInsnNode field) infestedCount = field;
        }
        assertEquals(BECKON_INFESTATION, infestedCount.owner);
        assertEquals(BLOCK_INFESTED_COUNT, infestedCount.name);
        MethodNode incrementParasiteCount = findMethod(adapted, "incrementParasiteCount", "()V");
        int parasiteCounterReferences = 0;
        for (AbstractInsnNode instruction : incrementParasiteCount.instructions) {
            if (!(instruction instanceof FieldInsnNode field) || !field.name.equals(BLOCK_PARASITE_COUNT)) continue;
            assertEquals(BLOCK_PARASITE_SPREADING, field.owner);
            parasiteCounterReferences++;
        }
        assertEquals(2, parasiteCounterReferences);
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, getInfestedCount);
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, incrementParasiteCount);

        Map<String, byte[]> srpClasses = new LinkedHashMap<>();
        srpClasses.put(SRP_EVENT_WORLD, plainClass(SRP_EVENT_WORLD));
        Map<String, ClassInfo> currentPeers = new LinkedHashMap<>(currentBeckonSpawnPeers());
        currentPeers.put(BLOCK_PARASITE_SPREADING, currentParasiteBlockCounterPeer());
        assertEquals(0, SrpApiCompat.apply(srpParasitesJar(), srpClasses, currentPeers));
        ClassNode currentEventWorld = read(srpClasses.get(SRP_EVENT_WORLD));
        assertTrue(currentEventWorld.fields.stream().noneMatch(field -> field.name.equals(BLOCK_PARASITE_COUNT)));
    }

    @Test
    void restoresTheLegacyMutablePhaseSpawnListsOnTheCurrentSrpPeer() throws Exception {
        byte[] original = currentPhaseSpawnListsClass();
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(SRP_SPAWNING, original);
        List<ClassInfo> scanned = new java.util.ArrayList<>(List.of(readInfo(original)));
        SrpApiCompat.addLegacyPeerSignatures(srpParasitesJar(), scanned);
        ClassInfo spawningPeer = findInfo(scanned, SRP_SPAWNING);
        assertTrue(spawningPeer.hasMethod("getSpawns", "(B)Ljava/util/List;"));

        Map<String, ClassInfo> peers = Map.of(SRP_SPAWNING, spawningPeer);
        assertEquals(1, SrpApiCompat.apply(srpParasitesJar(), classes, peers));

        ClassNode adapted = read(classes.get(SRP_SPAWNING));
        MethodNode getter = findMethod(adapted, "getSpawns", "(B)Ljava/util/List;");
        org.objectweb.asm.tree.TableSwitchInsnNode phaseSwitch = null;
        java.util.ArrayList<String> returnedLists = new java.util.ArrayList<>();
        for (AbstractInsnNode instruction : getter.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.TableSwitchInsnNode tableSwitch) phaseSwitch = tableSwitch;
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC) {
                assertEquals(SRP_SPAWNING, field.owner);
                returnedLists.add(field.name);
            }
        }
        assertTrue(phaseSwitch != null);
        assertEquals(0, phaseSwitch.min);
        assertEquals(8, phaseSwitch.max);
        assertEquals(List.of("PHASEZERO", "PHASEONE", "PHASETWO", "PHASETHREE", "PHASEFOUR",
                "PHASEFIVE", "PHASESIX", "PHASESEVEN", "PHASEEIGHT"), returnedLists);
        assertEquals(Opcodes.ACONST_NULL, getter.instructions.getLast().getPrevious().getOpcode());
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, getter);
    }

    @Test
    void redirectsTheLegacySpreadDirectionHelperToTheCurrentBlockSpreadingApi() throws Exception {
        String fixture = "energon/srpextra/TestDirectionCall";
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(fixture, oldDirectionCall(fixture));
        Map<String, ClassInfo> peers = Map.of(BLOCK_PARASITE_SPREADING, currentParasiteBlockCounterPeer());

        assertEquals(1, SrpApiCompat.apply(srpExtraJar(), classes, peers));

        ClassNode adapted = read(classes.get(fixture));
        MethodNode direction = findMethod(adapted, "direction", "(L" + BLOCK_POS + ";I)L" + BLOCK_POS + ";");
        MethodInsnNode call = findCall(direction, BLOCK_PARASITE_SPREADING, "directionToSpread", OLD_DIRECTION_DESC);
        assertTrue(call != null);
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, direction);
    }

    @Test
    void restoresLegacyBiomeEntryPointsOnTheCurrentSrpApi() throws Exception {
        byte[] eventWorldBytes = currentBiomeEventWorld();
        Map<String, ClassInfo> peers = currentBiomePeers(eventWorldBytes);
        ClassInfo spreading = peers.get(BLOCK_PARASITE_SPREADING);
        spreading.methods.put("positionToParasiteBiome(L" + WORLD + ";L" + BLOCK_POS + ";I)V",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        ClassInfo biome = peers.get(BIOME_PARASITE_BASE);
        String generator = "(L" + WORLD + ";L" + BLOCK_POS + ";Ljava/util/Random;)V";
        biome.methods.put("spawnGenFeatureParasite" + generator, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT);
        biome.methods.put("spawnGenRoofParasite" + generator, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT);
        byte[] packetBytes = currentBiomePacket();
        ClassInfo packetPeer = readInfo(packetBytes);
        remapMethodDescriptor(packetPeer, "<init>" + NEW_BIOME_PACKET_CTOR,
                "<init>(L" + LegacyVersion.V1_12_2.linkPrefix() + BLOCK_POS + ";ZI)V");
        peers.put(SRP_PACKET_BIOME_CHANGE, packetPeer);

        List<ClassInfo> scanned = new java.util.ArrayList<>(peers.values());
        SrpApiCompat.addLegacyPeerSignatures(srpParasitesJar(), scanned);
        Map<String, ClassInfo> indexed = new LinkedHashMap<>();
        for (ClassInfo info : scanned) indexed.put(info.name, info);
        String prefix = LegacyVersion.V1_12_2.linkPrefix();
        String oldBiome = "(L" + prefix + WORLD + ";L" + prefix + BLOCK_POS + ";)V";
        String linkedGenerator = "(L" + prefix + WORLD + ";L" + prefix + BLOCK_POS + ";Ljava/util/Random;)V";
        assertTrue(indexed.get(SRP_EVENT_WORLD).hasMethod("positionToParasiteBiome", oldBiome));
        assertTrue(indexed.get(SRP_EVENT_WORLD).hasMethod("spawnGenFeatureParasite", linkedGenerator));
        assertTrue(indexed.get(SRP_EVENT_WORLD).hasMethod("spawnGenRoofParasite", linkedGenerator));
        assertTrue(indexed.get(SRP_PACKET_BIOME_CHANGE).hasMethod("<init>", "(L" + prefix + BLOCK_POS + ";Z)V"));

        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(SRP_EVENT_WORLD, eventWorldBytes);
        classes.put(SRP_PACKET_BIOME_CHANGE, packetBytes);
        assertEquals(6, SrpApiCompat.apply(srpParasitesJar(), classes, indexed));

        ClassNode adapted = read(classes.get(SRP_EVENT_WORLD));
        MethodNode position = findMethod(adapted, "positionToParasiteBiome", OLD_POSITION_TO_PARASITE_BIOME);
        MethodInsnNode positionCall = findCall(position, BLOCK_PARASITE_SPREADING, "positionToParasiteBiome",
                NEW_POSITION_TO_PARASITE_BIOME);
        assertTrue(positionCall != null);
        assertEquals(Opcodes.ICONST_0, positionCall.getPrevious().getOpcode());
        for (String name : List.of("spawnGenFeatureParasite", "spawnGenRoofParasite")) {
            MethodNode dispatch = findMethod(adapted, name, OLD_PARASITE_GENERATOR);
            assertTrue(findCall(dispatch, BIOME_PARASITE_BASE, name, OLD_PARASITE_GENERATOR) != null);
            new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, dispatch);
        }
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, position);
        ClassNode packet = read(classes.get(SRP_PACKET_BIOME_CHANGE));
        MethodNode constructor = findMethod(packet, "<init>", OLD_BIOME_PACKET_CTOR);
        MethodInsnNode target = findCall(constructor, SRP_PACKET_BIOME_CHANGE, "<init>", NEW_BIOME_PACKET_CTOR);
        assertTrue(target != null);
        assertEquals(Opcodes.ICONST_0, target.getPrevious().getOpcode());
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(packet.name, constructor);
    }

    @Test
    void restoresRemovedSrpConfigKeysAtTheirOriginalReadConfigCalls() throws Exception {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(SRP_CONFIG, currentConfigClass(SRP_CONFIG, "initGeneralConfig"));
        classes.put(SRP_CONFIG_SYSTEMS, currentConfigClass(SRP_CONFIG_SYSTEMS, "initstatuseffectsConfig"));
        List<ClassInfo> scanned = new java.util.ArrayList<>();
        scanned.add(readInfo(classes.get(SRP_CONFIG)));
        scanned.add(readInfo(classes.get(SRP_CONFIG_SYSTEMS)));
        ClassInfo configPeer = findInfo(scanned, SRP_CONFIG);
        ClassInfo systemsPeer = findInfo(scanned, SRP_CONFIG_SYSTEMS);
        remapConfigInitializerDescriptor(configPeer, "initGeneralConfig");
        remapConfigInitializerDescriptor(systemsPeer, "initstatuseffectsConfig");
        SrpApiCompat.addLegacyPeerSignatures(srpParasitesJar(), scanned);
        assertTrue(configPeer.hasField(LEGACY_USE_VINNIT, "Z"));
        assertTrue(systemsPeer.hasField(LEGACY_EPIL_CHANCE_COTH, "D"));

        assertEquals(2, SrpApiCompat.apply(srpParasitesJar(), classes, Map.of(SRP_CONFIG, configPeer,
                SRP_CONFIG_SYSTEMS, systemsPeer)));
        ClassNode config = read(classes.get(SRP_CONFIG));
        ClassNode systems = read(classes.get(SRP_CONFIG_SYSTEMS));
        MethodNode configRead = findMethod(config, "readConfig", "()Z");
        MethodNode systemsRead = findMethod(systems, "readConfig", "()Z");
        MethodInsnNode boolRead = findCall(configRead, CONFIGURATION, "getBoolean",
                "(Ljava/lang/String;Ljava/lang/String;ZLjava/lang/String;)Z");
        MethodInsnNode floatRead = findCall(systemsRead, CONFIGURATION, "getFloat",
                "(Ljava/lang/String;Ljava/lang/String;FFFLjava/lang/String;)F");
        assertTrue(boolRead != null);
        assertTrue(floatRead != null);
        assertEquals("Mod Weapons Second Texture", ((org.objectweb.asm.tree.LdcInsnNode) boolRead.getPrevious().getPrevious().getPrevious().getPrevious()).cst);
        assertEquals("Camouflage Chance", ((org.objectweb.asm.tree.LdcInsnNode) floatRead.getPrevious().getPrevious().getPrevious().getPrevious().getPrevious().getPrevious()).cst);
        assertEquals(0.7F, ((Float) ((org.objectweb.asm.tree.LdcInsnNode) floatRead.getPrevious().getPrevious().getPrevious().getPrevious()).cst).floatValue());
        assertTrue(config.fields.stream().anyMatch(field -> field.name.equals(LEGACY_USE_VINNIT) && field.desc.equals("Z")));
        assertTrue(systems.fields.stream().anyMatch(field -> field.name.equals(LEGACY_EPIL_CHANCE_COTH) && field.desc.equals("D")));
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(config.name, configRead);
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(systems.name, systemsRead);
    }

    @Test
    void adaptsLegacyMinimumDamageCallsToTheCurrentSrpPeerSignature() throws Exception {
        String linkedLivingBase = "L" + LegacyVersion.V1_12_2.linkPrefix() + ENTITY_LIVING_BASE + ";";
        ClassInfo parasitePeer = new ClassInfo(ENTITY_PARASITE, "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC);
        parasitePeer.methods.put("attackEntityAsMobMinimum(" + linkedLivingBase + "F)Z", Opcodes.ACC_PUBLIC);
        parasitePeer.methods.put("getMiniDamage()F", Opcodes.ACC_PUBLIC);
        String caller = "srpcotesia/EntityCloudCompatFixture";
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = caller;
        node.superName = "java/lang/Object";
        MethodNode attack = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "attack",
                "(L" + ENTITY_PARASITE + ";L" + ENTITY_LIVING_BASE + ";)Z", null, null);
        attack.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        attack.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        attack.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY_PARASITE,
                "attackEntityAsMobMinimum", "(L" + ENTITY_LIVING_BASE + ";)Z", false));
        attack.instructions.add(new InsnNode(Opcodes.IRETURN));
        attack.maxStack = 2;
        attack.maxLocals = 2;
        node.methods.add(attack);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);

        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(caller, writer.toByteArray());
        assertEquals(1, SrpApiCompat.apply(cotesiaJar(), classes, Map.of(ENTITY_PARASITE, parasitePeer)));

        ClassNode adapted = read(classes.get(caller));
        MethodNode adaptedAttack = findMethod(adapted, "attack", "(L" + ENTITY_PARASITE + ";L" + ENTITY_LIVING_BASE + ";)Z");
        MethodInsnNode miniDamage = findCall(adaptedAttack, ENTITY_PARASITE, "getMiniDamage", "()F");
        MethodInsnNode minimumAttack = findCall(adaptedAttack, ENTITY_PARASITE, "attackEntityAsMobMinimum",
                "(L" + ENTITY_LIVING_BASE + ";F)Z");
        assertTrue(miniDamage != null);
        assertTrue(minimumAttack != null);
        assertEquals(Opcodes.DUP2, miniDamage.getPrevious().getPrevious().getOpcode());
        assertEquals(Opcodes.POP, miniDamage.getPrevious().getOpcode());
        assertTrue(findCall(adaptedAttack, ENTITY_PARASITE, "attackEntityAsMobMinimum",
                "(L" + ENTITY_LIVING_BASE + ";)Z") == null);
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, adaptedAttack);
        assertEquals(0, SrpApiCompat.apply(cotesiaJar(), classes, Map.of(ENTITY_PARASITE, parasitePeer)));
    }

    @Test
    void addsOldFamilySizeAliasesToTheSplitGoreEnumWithoutChangingItsValuesArray() throws Exception {
        ClassNode goreEnum = new ClassNode(Opcodes.ASM9);
        goreEnum.version = Opcodes.V1_8;
        goreEnum.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM;
        goreEnum.name = GORE_ENUM;
        goreEnum.superName = "java/lang/Enum";
        for (String size : List.of("FLAT", "SMALL", "BIG")) {
            goreEnum.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM,
                    size, ENUM_DESC, null, null));
        }
        MethodNode initializer = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        initializer.instructions.add(new InsnNode(Opcodes.RETURN));
        initializer.maxStack = 0;
        initializer.maxLocals = 0;
        goreEnum.methods.add(initializer);
        ClassWriter writer = new ClassWriter(0);
        goreEnum.accept(writer);
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(GORE_ENUM, writer.toByteArray());

        assertEquals(12, SrpApiCompat.apply(srpParasitesJar(), classes, Map.of()));

        ClassNode adapted = read(classes.get(GORE_ENUM));
        MethodNode clinit = findMethod(adapted, "<clinit>", "()V");
        for (String family : List.of("SIM", "PRI", "ADA", "PURE")) {
            for (String size : List.of("FLAT", "SMALL", "BIG")) {
                String alias = family + "_" + size;
                assertTrue(adapted.fields.stream().anyMatch(field -> field.name.equals(alias) && field.desc.equals(ENUM_DESC)));
                assertTrue(findFieldAccess(clinit, Opcodes.PUTSTATIC, GORE_ENUM, alias, ENUM_DESC) != null);
            }
        }
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, clinit);
    }

    @Test
    void adaptsTheSRPExtraGoreFamilyLookupToSplitCurrentBlocks() throws Exception {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = SRP_EXTRA_REMAINS_CANISTER;
        node.superName = "java/lang/Object";
        MethodNode lookup = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getTypeByBlock",
                "(L" + WORLD + ";L" + BLOCK_POS + ";)Ljava/lang/String;", null, null);
        lookup.instructions.add(new org.objectweb.asm.tree.LdcInsnNode(";sim;"));
        lookup.instructions.add(new InsnNode(Opcodes.ARETURN));
        lookup.maxStack = 1;
        lookup.maxLocals = 2;
        node.methods.add(lookup);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(SRP_EXTRA_REMAINS_CANISTER, writer.toByteArray());

        assertEquals(1, SrpApiCompat.apply(srpExtraJar(), classes, splitGorePeers()));

        ClassNode adapted = read(classes.get(SRP_EXTRA_REMAINS_CANISTER));
        MethodNode adaptedLookup = findMethod(adapted, "getTypeByBlock", "(L" + WORLD + ";L" + BLOCK_POS + ";)Ljava/lang/String;");
        assertTrue(findCall(adaptedLookup, WORLD, "func_180495_p", "(L" + BLOCK_POS + ";)L" + I_BLOCK_STATE + ";") != null);
        assertTrue(findCall(adaptedLookup, SRP_GORE_FAMILY_HELPER, "getTypeByBlock",
                "(Lnet/minecraft/block/Block;Lnet/minecraft/block/Block;Lnet/minecraft/block/Block;Lnet/minecraft/block/Block;Lnet/minecraft/block/Block;)Ljava/lang/String;") != null);
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, adaptedLookup);
        assertEquals(0, SrpApiCompat.apply(srpExtraJar(), classes, splitGorePeers()));
    }

    @Test
    void restoresDodChanceLookupByTheRequestedEvolutionPhase() throws Exception {
        ClassInfo dodPeer = new ClassInfo(SRP_TILE_ENTITY_DOD, "net/minecraft/tileentity/TileEntity", List.of(), Opcodes.ACC_PUBLIC);
        ClassInfo eventEntityPeer = new ClassInfo(SRP_EVENT_ENTITY, "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC);
        eventEntityPeer.methods.put("getRSchance(L" + WORLD + ";)D", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        ClassInfo systemsPeer = new ClassInfo(SRP_CONFIG_SYSTEMS, "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC);
        String[] chanceFields = {
                "reinforcementSystemChanceOne", "reinforcementSystemChanceTwo", "reinforcementSystemChanceThree",
                "reinforcementSystemChanceFour", "reinforcementSystemChanceFive", "reinforcementSystemChanceSix",
                "reinforcementSystemChanceSeven", "reinforcementSystemChanceEight", "reinforcementSystemChanceNine",
                "reinforcementSystemChanceTen"
        };
        for (String field : chanceFields) systemsPeer.fields.put(field + ":D", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        ClassNode dod = new ClassNode(Opcodes.ASM9);
        dod.version = Opcodes.V1_8;
        dod.access = Opcodes.ACC_PUBLIC;
        dod.name = SRP_TILE_ENTITY_DOD;
        dod.superName = "net/minecraft/tileentity/TileEntity";
        ClassWriter writer = new ClassWriter(0);
        dod.accept(writer);
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(SRP_TILE_ENTITY_DOD, writer.toByteArray());

        assertEquals(1, SrpApiCompat.apply(srpParasitesJar(), classes,
                Map.of(SRP_TILE_ENTITY_DOD, dodPeer, SRP_EVENT_ENTITY, eventEntityPeer, SRP_CONFIG_SYSTEMS, systemsPeer)));

        ClassNode adapted = read(classes.get(SRP_TILE_ENTITY_DOD));
        MethodNode chance = findMethod(adapted, "getRSChance", "(B)D");
        assertTrue(chance != null);
        assertTrue(findCall(chance, SRP_REINFORCEMENT_HELPER, "getChance", "(BDDDDDDDDDD)D") != null);
        for (String field : chanceFields) {
            assertTrue(findFieldAccess(chance, Opcodes.GETSTATIC, SRP_CONFIG_SYSTEMS, field, "D") != null);
        }
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, chance);
        assertEquals(0, SrpApiCompat.apply(srpParasitesJar(), classes,
                Map.of(SRP_TILE_ENTITY_DOD, dodPeer, SRP_EVENT_ENTITY, eventEntityPeer, SRP_CONFIG_SYSTEMS, systemsPeer)));
        List<ClassInfo> scannedPeers = new java.util.ArrayList<>(List.of(dodPeer, eventEntityPeer, systemsPeer));
        SrpApiCompat.addLegacyPeerSignatures(srpParasitesJar(), scannedPeers);
        assertTrue(dodPeer.hasMethod("getRSChance", "(B)D"));
    }

    @Test
    void passesTheConversionWorldToTheCurrentFeralFactoryApi() throws Exception {
        String linkedWorld = "L" + LegacyVersion.V1_12_2.linkPrefix() + WORLD + ";";
        ClassInfo infectedPeer = new ClassInfo(ENTITY_PINFECTED, "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC);
        infectedPeer.methods.put("getFeral(" + linkedWorld + ")L" + ENTITY_PFERAL + ";", Opcodes.ACC_PUBLIC);
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = SRP_EXTRA_INF_CONVERT_UNIT;
        node.superName = "java/lang/Object";
        String conversionDesc = "(L" + WORLD + ";Z)Lnet/minecraft/entity/Entity;";
        MethodNode conversion = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "getConvertedEntity", conversionDesc, null, null);
        conversion.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        conversion.instructions.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.CHECKCAST, ENTITY_PINFECTED));
        conversion.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY_PINFECTED, "getFeral",
                "()L" + ENTITY_PFERAL + ";", false));
        conversion.instructions.add(new InsnNode(Opcodes.ARETURN));
        conversion.maxStack = 1;
        conversion.maxLocals = 3;
        node.methods.add(conversion);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(SRP_EXTRA_INF_CONVERT_UNIT, writer.toByteArray());

        assertEquals(1, SrpApiCompat.apply(srpExtraJar(), classes, Map.of(ENTITY_PINFECTED, infectedPeer)));

        ClassNode adapted = read(classes.get(SRP_EXTRA_INF_CONVERT_UNIT));
        MethodNode adaptedConversion = findMethod(adapted, "getConvertedEntity", conversionDesc);
        MethodInsnNode feral = findCall(adaptedConversion, ENTITY_PINFECTED, "getFeral",
                "(L" + WORLD + ";)L" + ENTITY_PFERAL + ";");
        assertTrue(feral != null);
        assertEquals(Opcodes.ALOAD, feral.getPrevious().getOpcode());
        assertEquals(1, ((VarInsnNode) feral.getPrevious()).var);
        assertTrue(findCall(adaptedConversion, ENTITY_PINFECTED, "getFeral", "()L" + ENTITY_PFERAL + ";") == null);
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, adaptedConversion);
        assertEquals(0, SrpApiCompat.apply(srpExtraJar(), classes, Map.of(ENTITY_PINFECTED, infectedPeer)));
    }

    @Test
    void restoresWymoBurrowStateWithoutTriggeringDigSoundForRenderCaches() throws Exception {
        ClassNode wymo = new ClassNode(Opcodes.ASM9);
        wymo.version = Opcodes.V1_8;
        wymo.access = Opcodes.ACC_PUBLIC;
        wymo.name = ENTITY_WYMO;
        wymo.superName = "java/lang/Object";
        String entityDataManager = "net/minecraft/network/datasync/EntityDataManager";
        String dataParameter = "net/minecraft/network/datasync/DataParameter";
        wymo.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "field_70180_af", "L" + entityDataManager + ";", null, null));
        wymo.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "DIG", "L" + dataParameter + ";", null, null));
        MethodNode digging = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "setDigging", "(Z)V", null, null);
        digging.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_WYMO, "playDigSound", "()V", false));
        digging.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        digging.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_WYMO, "field_70180_af", "L" + entityDataManager + ";"));
        digging.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, ENTITY_WYMO, "DIG", "L" + dataParameter + ";"));
        digging.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        digging.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false));
        digging.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, entityDataManager, "func_187227_b",
                "(L" + dataParameter + ";Ljava/lang/Object;)V", false));
        digging.instructions.add(new InsnNode(Opcodes.RETURN));
        digging.maxStack = 3;
        digging.maxLocals = 2;
        wymo.methods.add(digging);
        ClassWriter writer = new ClassWriter(0);
        wymo.accept(writer);
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(ENTITY_WYMO, writer.toByteArray());

        assertEquals(1, SrpApiCompat.apply(srpParasitesJar(), classes, Map.of()));

        ClassNode adapted = read(classes.get(ENTITY_WYMO));
        MethodNode burrowed = findMethod(adapted, "setBurrowed", "(Z)V");
        assertTrue(burrowed != null);
        assertTrue(findCall(burrowed, ENTITY_WYMO, "playDigSound", "()V") == null);
        assertTrue(findCall(burrowed, entityDataManager, "func_187227_b", "(L" + dataParameter + ";Ljava/lang/Object;)V") != null);
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, burrowed);
        assertEquals(0, SrpApiCompat.apply(srpParasitesJar(), classes, Map.of()));
    }

    @Test
    void restoresTheRemovedBeckonBlockInfestationTimerAndItsStageConfig() throws Exception {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(SRP_CONFIG_MOBS, currentBeckonConfigMobsClass());
        classes.put(SRP_EVENT_WORLD, plainClass(SRP_EVENT_WORLD));
        ClassInfo configMobs = readInfo(classes.get(SRP_CONFIG_MOBS));
        for (String initializer : LEGACY_BECKON_TIMER_INIT_METHODS) remapConfigInitializerDescriptor(configMobs, initializer);
        List<ClassInfo> scanned = new java.util.ArrayList<>(List.of(configMobs, readInfo(classes.get(SRP_EVENT_WORLD))));
        SrpApiCompat.addLegacyPeerSignatures(srpParasitesJar(), scanned);
        Map<String, ClassInfo> peers = new LinkedHashMap<>();
        for (ClassInfo info : scanned) peers.put(info.name, info);
        assertTrue(peers.get(SRP_EVENT_WORLD).hasMethod(LEGACY_BECKON_TIMER_METHOD, "(I)I"));

        assertTrue(SrpApiCompat.apply(srpParasitesJar(), classes, peers) > 0);
        ClassNode config = read(classes.get(SRP_CONFIG_MOBS));
        for (int i = 0; i < LEGACY_BECKON_TIMER_FIELDS.length; i++) {
            String field = LEGACY_BECKON_TIMER_FIELDS[i];
            assertTrue(config.fields.stream().anyMatch(candidate -> candidate.name.equals(field)
                    && candidate.desc.equals("I") && (candidate.access & Opcodes.ACC_STATIC) != 0));
            MethodNode init = findMethod(config, LEGACY_BECKON_TIMER_INIT_METHODS[i],
                    "(L" + CONFIGURATION + ";)V");
            MethodInsnNode read = findCall(init, CONFIGURATION, "getInt",
                    "(Ljava/lang/String;Ljava/lang/String;IIILjava/lang/String;)I");
            assertTrue(read != null);
            assertTrue(java.util.Arrays.stream(init.instructions.toArray()).anyMatch(instruction ->
                    instruction instanceof FieldInsnNode access && access.getOpcode() == Opcodes.PUTSTATIC
                            && access.owner.equals(SRP_CONFIG_MOBS) && access.name.equals(field)));
            new Analyzer<BasicValue>(new BasicVerifier()).analyze(config.name, init);
        }
        MethodNode timer = findMethod(read(classes.get(SRP_EVENT_WORLD)), LEGACY_BECKON_TIMER_METHOD, "(I)I");
        assertTrue(findCall(timer, LEGACY_BECKON_TIMER_HELPER, LEGACY_BECKON_TIMER_METHOD,
                LEGACY_BECKON_TIMER_HELPER_DESC) != null);
        assertEquals(LEGACY_BECKON_TIMER_FIELDS.length, java.util.Arrays.stream(timer.instructions.toArray())
                .filter(instruction -> instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                        && field.owner.equals(SRP_CONFIG_MOBS) && java.util.Arrays.asList(LEGACY_BECKON_TIMER_FIELDS).contains(field.name))
                .count());
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(SRP_EVENT_WORLD, timer);
        assertEquals(0, SrpApiCompat.apply(srpParasitesJar(), classes, peers));
    }

    @Test
    void addsLegacyBiomeSpreadAndRemovalMethodsToTheCurrentSrpPeer() throws Exception {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(SRP_EVENT_WORLD, currentBiomeEventWorld());
        Map<String, ClassInfo> peers = currentBiomePeers(classes.get(SRP_EVENT_WORLD));
        List<ClassInfo> scanned = new java.util.ArrayList<>(peers.values());
        SrpApiCompat.addLegacyPeerSignatures(srpParasitesJar(), scanned);
        for (ClassInfo info : scanned) peers.put(info.name, info);
        ClassInfo eventWorldPeer = findInfo(scanned, SRP_EVENT_WORLD);
        String linkedLegacyBiomeMethod = "(Llegacyconnector/v1_12_2/" + WORLD + ";Llegacyconnector/v1_12_2/" + BLOCK_POS + ";I)V";
        assertTrue(eventWorldPeer.hasMethod("SpreadBiome", linkedLegacyBiomeMethod));
        assertTrue(eventWorldPeer.hasMethod("killBiome", linkedLegacyBiomeMethod));

        assertEquals(2, SrpApiCompat.apply(srpParasitesJar(), classes, peers));

        ClassNode adapted = read(classes.get(SRP_EVENT_WORLD));
        MethodNode spread = findMethod(adapted, "SpreadBiome", "(L" + WORLD + ";L" + BLOCK_POS + ";I)V");
        assertTrue(findCall(spread, SRP_EVENT_WORLD, "canBiomeStillExistType",
                "(L" + WORLD + ";L" + BLOCK_POS + ";Z)I") != null);
        assertTrue(findCall(spread, BLOCK_PARASITE_SPREADING, "SpreadBiome",
                "(L" + WORLD + ";L" + BLOCK_POS + ";II)V") != null);

        MethodNode kill = findMethod(adapted, "killBiome", "(L" + WORLD + ";L" + BLOCK_POS + ";I)V");
        boolean checksParasiteBiome = false;
        boolean restoresPlains = false;
        boolean queuesClientUpdate = false;
        for (AbstractInsnNode instruction : kill.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.TypeInsnNode type
                    && type.getOpcode() == Opcodes.INSTANCEOF && type.desc.equals(BIOME_PARASITE_BASE)) checksParasiteBiome = true;
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                    && field.owner.equals(BIOMES) && field.name.equals("field_76772_c")) restoresPlains = true;
            if (instruction instanceof MethodInsnNode call && call.owner.equals(BIOME_UPDATE_QUEUE)
                    && call.name.equals("enqueue") && call.desc.equals(BIOME_UPDATE_ENQUEUE_DESC)) queuesClientUpdate = true;
        }
        assertTrue(checksParasiteBiome);
        assertTrue(restoresPlains);
        assertTrue(queuesClientUpdate);
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, spread);
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, kill);
    }

    @Test
    void remapsRemovedCruxTypeOnlyWhenTheCurrentCoreHasTheMatchingCruxAEntity() throws Exception {
        String fixture = "energon/srpextra/TestCruxReference";
        byte[] original = cruxReferenceClass(fixture);
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(fixture, original);
        Map<String, ClassInfo> peers = new LinkedHashMap<>(splitGorePeers());
        peers.put(SRP_CRUX_A, currentCruxAInfo());

        assertTrue(SrpApiCompat.apply(srpExtraJar(), classes, peers) > 0);

        ClassNode adapted = read(classes.get(fixture));
        assertEquals("L" + SRP_CRUX_A + ";", adapted.fields.get(0).desc);
        MethodNode classLiteral = findMethod(adapted, "cruxClass", "()Ljava/lang/Class;");
        org.objectweb.asm.tree.LdcInsnNode cruxType = null;
        for (AbstractInsnNode instruction : classLiteral.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.LdcInsnNode ldc) cruxType = ldc;
        }
        assertTrue(cruxType != null);
        assertEquals(SRP_CRUX_A, ((org.objectweb.asm.Type) cruxType.cst).getInternalName());
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, classLiteral);

        classes.put(fixture, original);
        ClassNode oldCrux = new ClassNode(Opcodes.ASM9);
        oldCrux.name = SRP_CRUX;
        peers.put(SRP_CRUX, ClassInfo.of(oldCrux));
        assertEquals(0, SrpApiCompat.apply(srpExtraJar(), classes, peers));
        assertArrayEquals(original, classes.get(fixture));
    }

    @Test
    void preservesFamilyAndShapeWhenGoreWasSplitIntoSeveralBlocks() throws Exception {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        String fixture = "energon/srpextra/TestGoreStates";
        byte[] original = oldGoreStates(fixture);
        classes.put(fixture, original);

        assertEquals(24, SrpApiCompat.apply(srpExtraJar(), classes, splitGorePeers()));

        ClassNode adapted = read(classes.get(fixture));
        String[] families = {"SIM", "PRI", "ADA", "PURE"};
        String[] sizes = {"FLAT", "SMALL", "BIG"};
        int methodNumber = 0;
        for (String family : families) {
            for (String size : sizes) {
                String methodName = "state" + methodNumber++;
                MethodNode method = adapted.methods.stream().filter(m -> m.name.equals(methodName)).findFirst().orElseThrow();
                java.util.ArrayList<FieldInsnNode> fields = new java.util.ArrayList<>();
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode field) fields.add(field);
                }
                assertEquals("gore" + switch (family) {
                    case "SIM" -> "Sim";
                    case "PRI" -> "Pri";
                    case "ADA" -> "Ada";
                    case "PURE" -> "Pur";
                    default -> throw new AssertionError(family);
                }, fields.get(0).name);
                assertEquals(size, fields.get(2).name);
                assertEquals(SRP_BLOCKS, fields.get(0).owner);
                assertEquals(GORE_ENUM, fields.get(2).owner);
            }
        }

        MethodNode enumOnly = adapted.methods.stream().filter(m -> m.name.equals("enumOnly")).findFirst().orElseThrow();
        FieldInsnNode unchangedEnum = null;
        for (AbstractInsnNode instruction : enumOnly.instructions) {
            if (instruction instanceof FieldInsnNode field) unchangedEnum = field;
        }
        assertEquals("ADA_BIG", unchangedEnum.name);

        for (MethodNode method : adapted.methods) {
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
                new Analyzer<BasicValue>(new BasicVerifier()).analyze(adapted.name, method);
            }
        }
    }

    @Test
    void requiresTheSplitPeerShapeAndLeavesOtherModsUntouched() {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        String fixture = "other/Plain";
        byte[] original = plainClass(fixture);
        classes.put(fixture, original);

        LegacyJar other = srpExtraJar();
        other.mods.get(0).legacyId = "othermod";
        assertEquals(0, SrpApiCompat.apply(other, classes, splitGorePeers()));
        assertArrayEquals(original, classes.get(fixture));

        classes.put(fixture, oldGoreStates(fixture));
        Map<String, ClassInfo> oldPeers = oldGorePeers();
        assertEquals(0, SrpApiCompat.apply(srpExtraJar(), classes, oldPeers));
        assertArrayEquals(oldGoreStates(fixture), classes.get(fixture));
    }

    private static byte[] oldSpawnMCall(String name) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = name;
        node.superName = "java/lang/Object";
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "invoke", OLD_SPAWN_M_DESC, null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, SRP_EVENT_ENTITY, "spawnM", OLD_SPAWN_M_DESC, false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 5;
        method.maxLocals = 5;
        node.methods.add(method);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] currentToxicCloudClass() {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = ENTITY_TOXIC_CLOUD;
        node.superName = ENTITY_LIVING_BASE;
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "owner", "L" + ENTITY_PARASITE + ";", null, null));
        MethodNode setter = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "setOwner", "(L" + ENTITY_PARASITE + ";)V", null, null);
        setter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        setter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        setter.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, ENTITY_TOXIC_CLOUD, "owner", "L" + ENTITY_PARASITE + ";"));
        setter.instructions.add(new InsnNode(Opcodes.RETURN));
        setter.maxStack = 2;
        setter.maxLocals = 2;
        node.methods.add(setter);
        MethodNode getter = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "getOwner", "()L" + ENTITY_PARASITE + ";", null, null);
        getter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getter.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_TOXIC_CLOUD, "owner", "L" + ENTITY_PARASITE + ";"));
        getter.instructions.add(new InsnNode(Opcodes.ARETURN));
        getter.maxStack = 1;
        getter.maxLocals = 1;
        node.methods.add(getter);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] currentSpawnListClass() {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = SRP_EVENT_ENTITY;
        node.superName = "java/lang/Object";
        MethodNode spawnFromList = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "spawnFromList", "(Lnet/minecraft/entity/Entity;[Ljava/lang/String;Lnet/minecraft/entity/EntityLivingBase;)V", null, null);
        spawnFromList.instructions.add(new InsnNode(Opcodes.RETURN));
        spawnFromList.maxLocals = 3;
        node.methods.add(spawnFromList);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static LegacyJar cotesiaJar() {
        LegacyJar jar = new LegacyJar(Path.of("srpcotesia.jar"));
        jar.version = LegacyVersion.V1_12_2;
        LegacyModDecl mod = new LegacyModDecl();
        mod.legacyId = "srpcotesia";
        jar.mods.add(mod);
        return jar;
    }

    private static LegacyJar srpParasitesJar() {
        LegacyJar jar = new LegacyJar(Path.of("srparasites.jar"));
        jar.version = LegacyVersion.V1_12_2;
        LegacyModDecl mod = new LegacyModDecl();
        mod.legacyId = "srparasites";
        jar.mods.add(mod);
        return jar;
    }

    private static byte[] currentHumanRendererClass() {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = SRP_HUMAN_RENDERER;
        node.superName = "java/lang/Object";
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                CURRENT_HUMAN_ALT_TEXTURE, HUMAN_TEXTURE_DESC, null, null));
        MethodNode initializer = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        initializer.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        initializer.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, SRP_HUMAN_RENDERER,
                CURRENT_HUMAN_ALT_TEXTURE, HUMAN_TEXTURE_DESC));
        initializer.instructions.add(new InsnNode(Opcodes.RETURN));
        initializer.maxStack = 1;
        initializer.maxLocals = 0;
        node.methods.add(initializer);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] currentAiClass(String name, String constructor, boolean melee) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = name;
        node.superName = "java/lang/Object";
        if (melee) {
            node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "attacker", "L" + ENTITY_PARASITE + ";", null, null));
            node.fields.add(new FieldNode(Opcodes.ACC_PROTECTED, "attackTick", "I", null, null));
            MethodNode attackCheck = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PROTECTED, ATTACK_CHECK, ATTACK_CHECK_DESC, null, null);
            attackCheck.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            attackCheck.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            attackCheck.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, AI_MELEE, "attacker", "L" + ENTITY_PARASITE + ";"));
            attackCheck.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY_PARASITE, "getAttackSpeed", "()I", false));
            attackCheck.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, AI_MELEE, "attackTick", "I"));
            attackCheck.instructions.add(new InsnNode(Opcodes.RETURN));
            attackCheck.maxStack = 2;
            attackCheck.maxLocals = 4;
            node.methods.add(attackCheck);
        }
        MethodNode current = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "<init>", constructor, null, null);
        current.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        current.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        current.instructions.add(new InsnNode(Opcodes.RETURN));
        current.maxStack = 1;
        current.maxLocals = org.objectweb.asm.Type.getArgumentsAndReturnSizes(constructor) >>> 2;
        node.methods.add(current);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static Map<String, ClassInfo> currentBeckonSpawnPeers() {
        ClassInfo beckon = new ClassInfo(BECKON_INFESTATION, "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC);
        String linkedWorld = "L" + LegacyVersion.V1_12_2.linkPrefix() + WORLD + ";";
        String linkedBlockPos = "L" + LegacyVersion.V1_12_2.linkPrefix() + BLOCK_POS + ";";
        String desc = "(" + linkedWorld + linkedBlockPos + "Ljava/util/Random;)V";
        beckon.methods.put("spawnGenFeatureInfested" + desc, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        beckon.methods.put("spawnGenRoofInfested" + desc, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        beckon.fields.put(BLOCK_INFESTED_COUNT + ":I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        return Map.of(BECKON_INFESTATION, beckon);
    }

    private static void remapConfigInitializerDescriptor(ClassInfo info, String initializer) {
        String oldDescriptor = initializer + "(L" + CONFIGURATION + ";)V";
        Integer access = info.methods.remove(oldDescriptor);
        assertTrue(access != null);
        String linkedDescriptor = initializer + "(L" + LegacyVersion.V1_12_2.linkPrefix() + CONFIGURATION + ";)V";
        info.methods.put(linkedDescriptor, access);
    }

    private static byte[] currentBiomePacket() {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = SRP_PACKET_BIOME_CHANGE;
        node.superName = "java/lang/Object";
        MethodNode constructor = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "<init>", NEW_BIOME_PACKET_CTOR, null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        constructor.maxStack = 1;
        constructor.maxLocals = 4;
        node.methods.add(constructor);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] currentBeckonConfigMobsClass() {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = SRP_CONFIG_MOBS;
        node.superName = "java/lang/Object";
        for (String field : List.of("venkrolCooldown", "venkrolsiiCooldown", "venkrolsiiiCooldown")) {
            node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, field, "I", null, null));
        }
        for (String initializer : LEGACY_BECKON_TIMER_INIT_METHODS) {
            MethodNode init = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                    initializer, "(L" + CONFIGURATION + ";)V", null, null);
            init.instructions.add(new InsnNode(Opcodes.RETURN));
            init.maxLocals = 1;
            node.methods.add(init);
        }
        MethodNode clinit = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(clinit);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] currentConfigClass(String name, String initializer) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = name;
        node.superName = "java/lang/Object";
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "config", "L" + CONFIGURATION + ";", null, null));
        MethodNode init = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, initializer,
                "(L" + CONFIGURATION + ";)V", null, null);
        init.instructions.add(new InsnNode(Opcodes.RETURN));
        init.maxStack = 0;
        init.maxLocals = 1;
        node.methods.add(init);
        MethodNode read = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "readConfig", "()Z", null, null);
        read.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, name, "config", "L" + CONFIGURATION + ";"));
        read.instructions.add(new VarInsnNode(Opcodes.ASTORE, 0));
        read.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        read.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, name, initializer, "(L" + CONFIGURATION + ";)V", false));
        read.instructions.add(new InsnNode(Opcodes.ICONST_1));
        read.instructions.add(new InsnNode(Opcodes.IRETURN));
        read.maxStack = 1;
        read.maxLocals = 1;
        node.methods.add(read);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static Map<String, ClassInfo> currentBiomePeers(byte[] eventWorldBytes) {
        Map<String, ClassInfo> peers = new LinkedHashMap<>();
        peers.put(SRP_EVENT_WORLD, readInfo(eventWorldBytes));
        ClassInfo spreading = currentParasiteBlockCounterPeer();
        spreading.methods.put("SpreadBiome(L" + WORLD + ";L" + BLOCK_POS + ";II)V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        peers.put(BLOCK_PARASITE_SPREADING, spreading);
        peers.put(BIOME_PARASITE_BASE, new ClassInfo(BIOME_PARASITE_BASE, BIOME, List.of(), Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT));
        ClassInfo queue = new ClassInfo(BIOME_UPDATE_QUEUE, "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC);
        queue.methods.put("enqueue" + BIOME_UPDATE_ENQUEUE_DESC, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        peers.put(BIOME_UPDATE_QUEUE, queue);
        return peers;
    }

    private static byte[] currentBiomeEventWorld() {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = SRP_EVENT_WORLD;
        node.superName = "java/lang/Object";
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "canBiomeStillExistType", "(L" + WORLD + ";L" + BLOCK_POS + ";Z)I", null, null);
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 1;
        method.maxLocals = 3;
        node.methods.add(method);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static ClassInfo currentParasiteBlockCounterPeer() {
        ClassInfo spreading = new ClassInfo(BLOCK_PARASITE_SPREADING, "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC);
        spreading.fields.put(BLOCK_PARASITE_COUNT + ":I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        String linkedBlockPos = "L" + LegacyVersion.V1_12_2.linkPrefix() + BLOCK_POS + ";";
        spreading.methods.put("directionToSpread(" + linkedBlockPos + "I)" + linkedBlockPos,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        return spreading;
    }

    private static byte[] currentPhaseSpawnListsClass() {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = SRP_SPAWNING;
        node.superName = "java/lang/Object";
        for (String field : List.of("PHASEZERO", "PHASEONE", "PHASETWO", "PHASETHREE", "PHASEFOUR",
                "PHASEFIVE", "PHASESIX", "PHASESEVEN", "PHASEEIGHT")) {
            node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, field, "Ljava/util/List;", null, null));
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] oldDirectionCall(String name) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = name;
        node.superName = "java/lang/Object";
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "direction", OLD_DIRECTION_DESC, null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, SRP_EVENT_WORLD,
                "directionToSpread", OLD_DIRECTION_DESC, false));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxStack = 2;
        method.maxLocals = 2;
        node.methods.add(method);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] oldInfestationCounterReferences(String name) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = name;
        node.superName = "java/lang/Object";
        MethodNode getInfestedCount = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "getInfestedCount", "()I", null, null);
        getInfestedCount.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, SRP_EVENT_WORLD, BLOCK_INFESTED_COUNT, "I"));
        getInfestedCount.instructions.add(new InsnNode(Opcodes.IRETURN));
        getInfestedCount.maxStack = 1;
        getInfestedCount.maxLocals = 0;
        node.methods.add(getInfestedCount);
        MethodNode incrementParasiteCount = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "incrementParasiteCount", "()V", null, null);
        incrementParasiteCount.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, SRP_EVENT_WORLD, BLOCK_PARASITE_COUNT, "I"));
        incrementParasiteCount.instructions.add(new InsnNode(Opcodes.ICONST_1));
        incrementParasiteCount.instructions.add(new InsnNode(Opcodes.IADD));
        incrementParasiteCount.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, SRP_EVENT_WORLD, BLOCK_PARASITE_COUNT, "I"));
        incrementParasiteCount.instructions.add(new InsnNode(Opcodes.RETURN));
        incrementParasiteCount.maxStack = 2;
        incrementParasiteCount.maxLocals = 0;
        node.methods.add(incrementParasiteCount);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] oldInfestationGeneratorCalls(String name) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = name;
        node.superName = "java/lang/Object";
        for (String methodName : List.of("spawnGenFeatureInfested", "spawnGenRoofInfested")) {
            MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    methodName, OLD_INFESTATION_SPAWN_DESC, null, null);
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, SRP_EVENT_WORLD, methodName,
                    OLD_INFESTATION_SPAWN_DESC, false));
            method.instructions.add(new InsnNode(Opcodes.RETURN));
            method.maxStack = 3;
            method.maxLocals = 3;
            node.methods.add(method);
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static Map<String, ClassInfo> currentSaveDataPeers() {
        ClassInfo saveData = new ClassInfo(SRP_SAVE_DATA, "net/minecraft/world/storage/WorldSavedData", List.of(), Opcodes.ACC_PUBLIC);
        String linkedWorld = "L" + LegacyVersion.V1_12_2.linkPrefix() + WORLD + ";";
        saveData.methods.put("get(L" + LegacyVersion.V1_12_2.linkPrefix() + WORLD + ";I)L" + SRP_SAVE_DATA + ";",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        saveData.methods.put("setTotalKills(IIZ" + linkedWorld + "ZI)Z", Opcodes.ACC_PUBLIC);
        return Map.of(SRP_SAVE_DATA, saveData);
    }

    private static Map<String, ClassInfo> currentMergePeers() {
        Map<String, ClassInfo> peers = new LinkedHashMap<>(currentSaveDataPeers());
        peers.put(SRP_EVENT_ENTITY, readInfo(plainClass(SRP_EVENT_ENTITY)));
        ClassInfo config = new ClassInfo(SRP_CONFIG, "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC);
        config.fields.put("convertedDespawn:Z", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        peers.put(SRP_CONFIG, config);
        ClassInfo systems = new ClassInfo(SRP_CONFIG_SYSTEMS, "java/lang/Object", List.of(), Opcodes.ACC_PUBLIC);
        systems.fields.put("mergeRandom:Z", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        systems.fields.put("mergeMobTable:[Ljava/lang/String;", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        systems.fields.put("useEvolution:Z", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        systems.fields.put("valueMerge:I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        peers.put(SRP_CONFIG_SYSTEMS, systems);
        return peers;
    }

    private static byte[] oldSaveDataCalls(String name) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = name;
        node.superName = "java/lang/Object";
        MethodNode get = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "getSaveData", "(L" + WORLD + ";)L" + SRP_SAVE_DATA + ";", null, null);
        get.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        get.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, SRP_SAVE_DATA, "get", OLD_SAVE_DATA_GET_DESC, false));
        get.instructions.add(new InsnNode(Opcodes.ARETURN));
        get.maxStack = 1;
        get.maxLocals = 1;
        node.methods.add(get);
        MethodNode addPoints = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "addPoints", "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";Z)Z", null, null);
        addPoints.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        addPoints.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        addPoints.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        addPoints.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        addPoints.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        addPoints.instructions.add(new VarInsnNode(Opcodes.ILOAD, 5));
        addPoints.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SRP_SAVE_DATA,
                "setTotalKills", OLD_SET_TOTAL_KILLS_DESC, false));
        addPoints.instructions.add(new InsnNode(Opcodes.IRETURN));
        addPoints.maxStack = 6;
        addPoints.maxLocals = 6;
        node.methods.add(addPoints);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static ClassInfo currentCruxAInfo() {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = SRP_CRUX_A;
        node.superName = ENTITY_P_CRUDE;
        node.interfaces.add(ENTITY_CUSTOM_ATTACK);
        addMethod(node, "<init>", "(Llegacyconnector/v1_12_2/net/minecraft/world/World;)V", Opcodes.ACC_PUBLIC, Opcodes.RETURN);
        addMethod(node, "getBack", "()Z", Opcodes.ACC_PUBLIC, Opcodes.IRETURN);
        addMethod(node, "getAttackTimerM", "()F", Opcodes.ACC_PUBLIC, Opcodes.FRETURN);
        addMethod(node, "getAttackTimerR", "()F", Opcodes.ACC_PUBLIC, Opcodes.FRETURN);
        addMethod(node, "getFinished", "(B)Z", Opcodes.ACC_PUBLIC, Opcodes.IRETURN);
        addMethod(node, "setFinished", "(BZ)V", Opcodes.ACC_PUBLIC, Opcodes.RETURN);
        addMethod(node, "doSpecialSkill", "(B)V", Opcodes.ACC_PUBLIC, Opcodes.RETURN);
        return ClassInfo.of(node);
    }

    private static void addMethod(ClassNode node, String name, String descriptor, int access, int returnOpcode) {
        MethodNode method = new MethodNode(Opcodes.ASM9, access, name, descriptor, null, null);
        if (returnOpcode == Opcodes.IRETURN || returnOpcode == Opcodes.FRETURN) {
            method.instructions.add(new InsnNode(returnOpcode == Opcodes.IRETURN ? Opcodes.ICONST_0 : Opcodes.FCONST_0));
        }
        method.instructions.add(new InsnNode(returnOpcode));
        method.maxStack = 1;
        method.maxLocals = org.objectweb.asm.Type.getArgumentsAndReturnSizes(descriptor) >>> 2;
        node.methods.add(method);
    }

    private static byte[] cruxReferenceClass(String name) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = name;
        node.superName = "java/lang/Object";
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "crux", "L" + SRP_CRUX + ";", null, null));
        MethodNode classLiteral = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "cruxClass", "()Ljava/lang/Class;", null, null);
        classLiteral.instructions.add(new org.objectweb.asm.tree.LdcInsnNode(org.objectweb.asm.Type.getObjectType(SRP_CRUX)));
        classLiteral.instructions.add(new InsnNode(Opcodes.ARETURN));
        classLiteral.maxStack = 1;
        classLiteral.maxLocals = 0;
        node.methods.add(classLiteral);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static ClassInfo readInfo(byte[] bytes) {
        return ClassInfo.of(read(bytes));
    }

    private static ClassInfo findInfo(List<ClassInfo> infos, String name) {
        return infos.stream().filter(info -> info.name.equals(name)).findFirst().orElseThrow();
    }

    private static void remapMethodDescriptor(ClassInfo info, String oldMethodKey, String newMethodKey) {
        Integer access = info.methods.remove(oldMethodKey);
        assertTrue(access != null);
        info.methods.put(newMethodKey, access);
        String oldPrefix = oldMethodKey + '\0';
        String newPrefix = newMethodKey + '\0';
        Map<String, Integer> movedSites = new java.util.HashMap<>();
        info.instructionSites.entrySet().removeIf(entry -> {
            if (!entry.getKey().startsWith(oldPrefix)) return false;
            movedSites.put(newPrefix + entry.getKey().substring(oldPrefix.length()), entry.getValue());
            return true;
        });
        info.instructionSites.putAll(movedSites);
    }

    private static MethodNode findMethod(ClassNode node, String name, String descriptor) {
        return node.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst().orElse(null);
    }

    private static MethodInsnNode findCall(MethodNode method, String owner, String name, String descriptor) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor)) return call;
        }
        return null;
    }

    private static FieldInsnNode findFieldAccess(MethodNode method, int opcode, String owner, String name, String descriptor) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == opcode && field.owner.equals(owner)
                    && field.name.equals(name) && field.desc.equals(descriptor)) return field;
        }
        return null;
    }

    private static LegacyJar srpExtraJar() {
        LegacyJar jar = new LegacyJar(Path.of("srpextra.jar"));
        jar.version = LegacyVersion.V1_12_2;
        LegacyModDecl mod = new LegacyModDecl();
        mod.legacyId = "srpextra";
        jar.mods.add(mod);
        return jar;
    }

    private static Map<String, ClassInfo> splitGorePeers() {
        ClassNode blocks = new ClassNode();
        blocks.name = SRP_BLOCKS;
        for (String field : List.of("goreSim", "gorePri", "goreAda", "gorePur")) {
            blocks.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, field, GORE_DESC, null, null));
        }

        ClassNode goreEnum = new ClassNode();
        goreEnum.name = GORE_ENUM;
        for (String size : List.of("FLAT", "SMALL", "BIG")) {
            goreEnum.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, size, ENUM_DESC, null, null));
        }
        return Map.of(SRP_BLOCKS, ClassInfo.of(blocks), GORE_ENUM, ClassInfo.of(goreEnum));
    }

    private static Map<String, ClassInfo> oldGorePeers() {
        ClassNode blocks = new ClassNode();
        blocks.name = SRP_BLOCKS;
        blocks.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "Gore", GORE_DESC, null, null));
        ClassNode goreEnum = new ClassNode();
        goreEnum.name = GORE_ENUM;
        for (String family : List.of("SIM", "PRI", "ADA", "PURE")) {
            for (String size : List.of("FLAT", "SMALL", "BIG")) {
                String name = family + "_" + size;
                goreEnum.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, name, ENUM_DESC, null, null));
            }
        }
        return Map.of(SRP_BLOCKS, ClassInfo.of(blocks), GORE_ENUM, ClassInfo.of(goreEnum));
    }

    private static byte[] oldGoreStates(String name) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = name;
        node.superName = "java/lang/Object";
        int number = 0;
        for (String family : List.of("SIM", "PRI", "ADA", "PURE")) {
            for (String size : List.of("FLAT", "SMALL", "BIG")) {
                MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "state" + number++, "()V", null, null);
                method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, SRP_BLOCKS, "Gore", GORE_DESC));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCK_GORE, "func_176223_P", "()" + STATE_DESC, false));
                method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, BLOCK_GORE, "VARIANT", PROPERTY_ENUM_DESC));
                method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, GORE_ENUM, family + "_" + size, ENUM_DESC));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, I_BLOCK_STATE, "func_177226_a", WITH_PROPERTY_DESC, true));
                method.instructions.add(new InsnNode(Opcodes.POP));
                method.instructions.add(new InsnNode(Opcodes.RETURN));
                method.maxStack = 3;
                method.maxLocals = 0;
                node.methods.add(method);
            }
        }
        MethodNode enumOnly = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "enumOnly", "()V", null, null);
        enumOnly.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, GORE_ENUM, "ADA_BIG", ENUM_DESC));
        enumOnly.instructions.add(new InsnNode(Opcodes.POP));
        enumOnly.instructions.add(new InsnNode(Opcodes.RETURN));
        enumOnly.maxStack = 1;
        enumOnly.maxLocals = 0;
        node.methods.add(enumOnly);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] plainClass(String name) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }
    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        return node;
    }
}
