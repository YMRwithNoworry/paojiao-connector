package org.kylepitcock.legacyconnector.loader.transform.compat;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.kylepitcock.legacyconnector.loader.LegacyJar;
import org.kylepitcock.legacyconnector.loader.LegacyVersion;
import org.kylepitcock.legacyconnector.loader.shim.ClassInfo;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
/**
 * Compatibility for the shared SRParasites API used by older 1.12.2 addons.
 *
 * <p>SRParasites 1.10.9 split the old {@code SRPBlocks.Gore} block into one block per parasite family and reduced
 * {@code BlockGore.EnumType} to the shape suffix ({@code FLAT}, {@code SMALL}, or {@code BIG}). An old state
 * expression therefore carries two pieces of information: the enum prefix selects the block field, while its
 * suffix selects the state on that block. This pass preserves both pieces before normal remapping and linking.</p>
 */
public final class SrpApiCompat {
    private static final String INFESTED_STAIN_STAIRS_MODEL = "assets/srparasites/models/block/infestedstainstairs_outer.json";
    private static final String MALFORMED_STAIN_STAIRS_TOP = "\"top\": \"srparasites:blocks/infestedstain,";
    private static final String FIXED_STAIN_STAIRS_TOP = "\"top\": \"srparasites:blocks/infestedstain\",";
    private static final String SRP_BLOCKS = "com/dhanantry/scapeandrunparasites/init/SRPBlocks";
    private static final String BLOCK_GORE = "com/dhanantry/scapeandrunparasites/block/BlockGore";
    private static final String GORE_ENUM = BLOCK_GORE + "$EnumType";
    private static final String SRP_SAVE_DATA = "com/dhanantry/scapeandrunparasites/world/SRPSaveData";
    private static final String SRP_EVENT_WORLD = "com/dhanantry/scapeandrunparasites/util/ParasiteEventWorld";
    private static final String SRP_EVENT_ENTITY = "com/dhanantry/scapeandrunparasites/util/ParasiteEventEntity";
    private static final String SRP_CONFIG = "com/dhanantry/scapeandrunparasites/util/config/SRPConfig";
    private static final String SRP_CONFIG_SYSTEMS = "com/dhanantry/scapeandrunparasites/util/config/SRPConfigSystems";
    private static final String SRP_CONFIG_MOBS = "com/dhanantry/scapeandrunparasites/util/config/SRPConfigMobs";
    private static final String SRP_TILE_ENTITY_DOD = "com/dhanantry/scapeandrunparasites/entity/tile/TileEntityDod";
    private static final String SRP_HUMAN_RENDERER = "com/dhanantry/scapeandrunparasites/client/renderer/entity/infected/RenderInfHuman";
    private static final String CURRENT_HUMAN_ALT_TEXTURE = "TEXTURE_ALT";
    private static final String LEGACY_HUMAN_TEXTURE = "TEXTURE3";
    private static final String RESOURCE_LOCATION = "net/minecraft/util/ResourceLocation";
    private static final String HUMAN_TEXTURE_DESC = "L" + RESOURCE_LOCATION + ";";
    private static final String SRP_EXTRA_REMAINS_CANISTER = "energon/srpextra/items/SRPEItemRemainsCanister";
    private static final String SRP_GORE_FAMILY_HELPER =
            "org/kylepitcock/legacyconnector/v1_12_2/compat/srparasites/SrpGoreFamily1122";
    private static final String SRP_GORE_FAMILY_HELPER_DESC =
            "(Lnet/minecraft/block/Block;Lnet/minecraft/block/Block;Lnet/minecraft/block/Block;Lnet/minecraft/block/Block;Lnet/minecraft/block/Block;)Ljava/lang/String;";
    private static final String[] REINFORCEMENT_CHANCE_FIELDS = {
            "reinforcementSystemChanceOne", "reinforcementSystemChanceTwo", "reinforcementSystemChanceThree",
            "reinforcementSystemChanceFour", "reinforcementSystemChanceFive", "reinforcementSystemChanceSix",
            "reinforcementSystemChanceSeven", "reinforcementSystemChanceEight", "reinforcementSystemChanceNine",
            "reinforcementSystemChanceTen"
    };
    private static final String REINFORCEMENT_CHANCE_HELPER =
            "org/kylepitcock/legacyconnector/v1_12_2/compat/srparasites/SrpLegacyReinforcementChance1122";
    private static final String REINFORCEMENT_CHANCE_HELPER_DESC = "(BDDDDDDDDDD)D";
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
    private static final String SRP_PACKET_BIOME_CHANGE = "com/dhanantry/scapeandrunparasites/network/SRPPacketBiomeChange";
    private static final String SRP_SPAWNING = "com/dhanantry/scapeandrunparasites/init/SRPSpawning";
    private static final String BECKON_INFESTATION = "com/dhanantry/scapeandrunparasites/util/convert/BeckonBlockInfestation";
    private static final String BIOME_PARASITE_BASE = "com/dhanantry/scapeandrunparasites/world/biome/BiomeParasiteBase";
    private static final String BIOME_UPDATE_QUEUE = "com/dhanantry/scapeandrunparasites/util/handlers/BiomeUpdateQueue";
    private static final String BIOME_PARASITE_SPREADING = "com/dhanantry/scapeandrunparasites/block/BlockParasiteSpreading";
    private static final String BIOME_UPDATE_ENQUEUE_DESC = "(IIIZII)V";
    private static final String CHUNK = "net/minecraft/world/chunk/Chunk";
    private static final String BIOMES = "net/minecraft/init/Biomes";
    private static final String BIOME = "net/minecraft/world/biome/Biome";
    private static final String BLOCK_INFESTED_COUNT = "blockInfestedCount";
    private static final String BLOCK_PARASITE_COUNT = "blockParasiteCount";
    private static final String BLOCK_PARASITE_SPREADING = "com/dhanantry/scapeandrunparasites/block/BlockParasiteSpreading";
    private static final String[] PHASE_LIST_FIELDS = {
            "PHASEZERO", "PHASEONE", "PHASETWO", "PHASETHREE", "PHASEFOUR",
            "PHASEFIVE", "PHASESIX", "PHASESEVEN", "PHASEEIGHT"
    };
    private static final String SPAWN_LIST_DESC = "Ljava/util/List;";
    private static final String BLOCK_POS = "net/minecraft/util/math/BlockPos";
    private static final String WORLD = "net/minecraft/world/World";
    private static final String RANDOM = "java/util/Random";
    private static final String CONFIGURATION = "net/minecraftforge/common/config/Configuration";
    private static final String LEGACY_USE_VINNIT = "useVinniT";
    private static final String LEGACY_EPIL_CHANCE_COTH = "epelChanceCOTH";
    private static final String OLD_BIOME_PACKET_CTOR = "(L" + BLOCK_POS + ";Z)V";
    private static final String NEW_BIOME_PACKET_CTOR = "(L" + BLOCK_POS + ";ZI)V";
    private static final String OLD_POSITION_TO_PARASITE_BIOME = "(L" + WORLD + ";L" + BLOCK_POS + ";)V";
    private static final String NEW_POSITION_TO_PARASITE_BIOME = "(L" + WORLD + ";L" + BLOCK_POS + ";I)V";
    private static final String OLD_PARASITE_GENERATOR = "(L" + WORLD + ";L" + BLOCK_POS + ";L" + RANDOM + ";)V";
    private static final String WORLD_PROVIDER = "net/minecraft/world/WorldProvider";
    private static final String OLD_SAVE_DATA_GET_DESC = "(L" + WORLD + ";)L" + SRP_SAVE_DATA + ";";
    private static final String NEW_SAVE_DATA_GET_DESC = "(L" + WORLD + ";I)L" + SRP_SAVE_DATA + ";";
    private static final String OLD_SET_TOTAL_KILLS_DESC = "(IIZL" + WORLD + ";Z)Z";
    private static final String NEW_SET_TOTAL_KILLS_DESC = "(IIZL" + WORLD + ";ZI)Z";
    private static final String SRP_CRUX = "com/dhanantry/scapeandrunparasites/entity/monster/crude/EntityCrux";
    private static final String SRP_CRUX_A = SRP_CRUX + "A";
    private static final String ENTITY_P_CRUDE = "com/dhanantry/scapeandrunparasites/entity/ai/misc/EntityPCrude";
    private static final String ENTITY_CUSTOM_ATTACK = "com/dhanantry/scapeandrunparasites/entity/ai/misc/EntityCutomAttack";
    private static final String GORE_DESC = "L" + BLOCK_GORE + ";";
    private static final String ENUM_DESC = "L" + GORE_ENUM + ";";
    private static final String I_BLOCK_STATE = "net/minecraft/block/state/IBlockState";
    private static final String STATE_DESC = "L" + I_BLOCK_STATE + ";";
    private static final String PROPERTY_ENUM_DESC = "Lnet/minecraft/block/properties/PropertyEnum;";
    private static final String OLD_GORE = "Gore";
    private static final String[] FAMILIES = {"SIM", "PRI", "ADA", "PURE"};
    private static final String[] SIZES = {"FLAT", "SMALL", "BIG"};
    private static final String ENTITY_PARASITE = "com/dhanantry/scapeandrunparasites/entity/ai/misc/EntityParasiteBase";
    private static final String ENTITY_TOXIC_CLOUD = "com/dhanantry/scapeandrunparasites/entity/EntityToxicCloud";
    private static final String ENTITY_WYMO = "com/dhanantry/scapeandrunparasites/entity/monster/primitive/EntityWymo";
    private static final String ENTITY_PINFECTED = "com/dhanantry/scapeandrunparasites/entity/ai/misc/EntityPInfected";
    private static final String ENTITY_PFERAL = "com/dhanantry/scapeandrunparasites/entity/ai/misc/EntityPFeral";
    private static final String SRP_EXTRA_INF_CONVERT_UNIT = "energon/srpextra/init/SRPETransform$InfConvertUnit";
    private static final String ENTITY_LIVING_BASE = "net/minecraft/entity/EntityLivingBase";
    private static final String LEGACY_MINIMUM_ATTACK_DESC = "(L" + ENTITY_LIVING_BASE + ";)Z";
    private static final String LEGACY_MINIMUM_ATTACK_CURRENT_DESC = "(L" + ENTITY_LIVING_BASE + ";F)Z";
    private static final String MINIMUM_ATTACK_METHOD = "attackEntityAsMobMinimum";
    private static final String MINI_DAMAGE_METHOD = "getMiniDamage";
    private static final String LEGACY_CLOUD_OWNER_FIELD = "mcc$legacyOwner";
    private static final String LEGACY_CLOUD_OWNER_SET_FIELD = "mcc$legacyOwnerSet";
    private static final String LEGACY_CLOUD_GET_OWNER_DESC = "()L" + ENTITY_LIVING_BASE + ";";
    private static final String LEGACY_CLOUD_SET_OWNER_DESC = "(L" + ENTITY_LIVING_BASE + ";)V";
    private static final String OLD_MERGE_DESC = "(L" + ENTITY_PARASITE + ";ILjava/lang/String;)Z";
    private static final String SRP_MERGE_HELPER = "org/kylepitcock/legacyconnector/v1_12_2/compat/srparasites/SrpMergeCompat1122";
    private static final String SRP_SPAWN_HELPER = "org/kylepitcock/legacyconnector/v1_12_2/compat/srparasites/SrpSpawnCompat1122";
    private static final String OLD_SPAWN_M_DESC = "(L" + ENTITY_PARASITE + ";[Ljava/lang/String;IZLjava/lang/String;)V";
    private static final String SPAWN_COUNT_DESC = "([Ljava/lang/String;Ljava/util/Random;)I";
    private static final String AI_AVOID = "com/dhanantry/scapeandrunparasites/entity/ai/EntityAIAvoidOrAttack";
    private static final String AI_MELEE = "com/dhanantry/scapeandrunparasites/entity/ai/EntityAIAttackMeleeStatus";
    private static final String OLD_AVOID_CTOR = "(L" + ENTITY_PARASITE + ";F)V";
    private static final String NEW_AVOID_CTOR = "(L" + ENTITY_PARASITE + ";FII)V";
    private static final String OLD_MELEE_CTOR = "(L" + ENTITY_PARASITE + ";DZDI)V";
    private static final String NEW_MELEE_CTOR = "(L" + ENTITY_PARASITE + ";DZD)V";
    private static final String ATTACKER_FIELD = "attacker";
    private static final String ATTACK_TICK = "attackTick";
    private static final String ATTACK_SPEED = "getAttackSpeed";
    private static final String ATTACK_CHECK = "checkAndPerformAttack";
    private static final String ATTACK_CHECK_DESC = "(Lnet/minecraft/entity/EntityLivingBase;D)V";
    private static final String LEGACY_ATTACK_COOLDOWN = "mcc$legacyAttackCooldown";
    private static final String HAS_LEGACY_ATTACK_COOLDOWN = "mcc$hasLegacyAttackCooldown";
    private static final String GET_ATTACK_COOLDOWN = "mcc$getAttackCooldown";

    private SrpApiCompat() {}

    /** Repairs the known missing quote in SRParasites 1.10.9's outer stain-stairs model. */
    public static int repairMalformedAssets(LegacyJar jar, Map<String, byte[]> resources) {
        if (!isSrparasites(jar)) return 0;
        byte[] bytes = resources.get(INFESTED_STAIN_STAIRS_MODEL);
        if (bytes == null) return 0;
        String source = new String(bytes, StandardCharsets.UTF_8);
        int start = source.indexOf(MALFORMED_STAIN_STAIRS_TOP);
        if (start < 0 || source.indexOf(MALFORMED_STAIN_STAIRS_TOP, start + MALFORMED_STAIN_STAIRS_TOP.length()) >= 0) return 0;
        String fixed = source.substring(0, start) + FIXED_STAIN_STAIRS_TOP
                + source.substring(start + MALFORMED_STAIN_STAIRS_TOP.length());
        try {
            if (!JsonParser.parseString(fixed).isJsonObject()) return 0;
        } catch (RuntimeException ignored) {
            return 0;
        }
        resources.put(INFESTED_STAIN_STAIRS_MODEL, fixed.getBytes(StandardCharsets.UTF_8));
        return 1;
    }

    /** Applies only to 1.12.2 jars when the peer set proves the split 1.10.9 SRP API. */
    public static int apply(LegacyJar jar, Map<String, byte[]> classes, Map<String, ClassInfo> peers) {
        if (jar.version != LegacyVersion.V1_12_2) return 0;

        ClassInfo blocks = peers.get(SRP_BLOCKS);
        ClassInfo goreEnum = peers.get(GORE_ENUM);
        ClassInfo spawning = peers.get(SRP_SPAWNING);
        ClassInfo eventWorld = peers.get(SRP_EVENT_WORLD);
        ClassInfo eventEntity = peers.get(SRP_EVENT_ENTITY);
        if (isSrparasites(jar) && classes.containsKey(SRP_EVENT_ENTITY)) {
            ClassNode currentEventEntity = new ClassNode(Opcodes.ASM9);
            new ClassReader(classes.get(SRP_EVENT_ENTITY)).accept(currentEventEntity, 0);
            eventEntity = ClassInfo.of(currentEventEntity);
        }
        ClassInfo beckon = peers.get(BECKON_INFESTATION);
        ClassInfo parasiteSpreading = peers.get(BLOCK_PARASITE_SPREADING);
        if (isSrparasites(jar) && classes.containsKey(SRP_SPAWNING)) {
            ClassNode currentSpawning = new ClassNode(Opcodes.ASM9);
            new ClassReader(classes.get(SRP_SPAWNING)).accept(currentSpawning, 0);
            spawning = ClassInfo.of(currentSpawning);
        }
        if (isSrparasites(jar) && classes.containsKey(BECKON_INFESTATION)) {
            ClassNode currentBeckon = new ClassNode(Opcodes.ASM9);
            new ClassReader(classes.get(BECKON_INFESTATION)).accept(currentBeckon, 0);
            beckon = ClassInfo.of(currentBeckon);
        }
        if (isSrparasites(jar) && classes.containsKey(BLOCK_PARASITE_SPREADING)) {
            ClassNode currentSpreading = new ClassNode(Opcodes.ASM9);
            new ClassReader(classes.get(BLOCK_PARASITE_SPREADING)).accept(currentSpreading, 0);
            parasiteSpreading = ClassInfo.of(currentSpreading);
        }
        boolean splitGoreApi = hasSplitGoreApi(blocks, goreEnum);
        boolean currentSaveDataApi = hasCurrentSaveDataApi(peers.get(SRP_SAVE_DATA), jar.version);
        boolean currentPhaseSpawnLists = hasCurrentPhaseSpawnLists(spawning);
        boolean currentSpreadBiomeApi = hasCurrentSpreadBiomeApi(eventWorld, parasiteSpreading, jar.version);
        boolean currentKillBiomeApi = hasCurrentKillBiomeApi(peers);
        boolean currentBeckonSpawnApi = hasCurrentBeckonSpawnApi(beckon, jar.version);
        boolean currentBeckonCounterApi = hasCurrentBeckonCounterApi(beckon, jar.version);
        boolean currentParasiteBlockCounterApi = hasCurrentParasiteBlockCounterApi(parasiteSpreading);
        boolean currentDirectionApi = hasCurrentDirectionApi(parasiteSpreading, jar.version);
        boolean currentMergeApi = isSrparasites(jar) && hasCurrentMergeApi(peers, jar.version);
        boolean currentSpawnListApi = hasCurrentSpawnListApi(eventEntity, jar.version);
        boolean currentLegacyBiomeMethods = isSrparasites(jar) && hasCurrentLegacyBiomeMethods(peers, jar.version);
        boolean currentBiomePacket = isSrparasites(jar) && hasCurrentBiomePacket(peers.get(SRP_PACKET_BIOME_CHANGE), jar.version);
        boolean currentLegacyConfig = isSrparasites(jar) && hasCurrentLegacyConfig(peers, jar.version);
        boolean currentLegacyBeckonBlockTimer = isSrparasites(jar)
                && hasCurrentLegacyBeckonBlockTimerApi(peers.get(SRP_CONFIG_MOBS), jar.version)
                && lacksLegacyBeckonBlockTimerMethod(classes.get(SRP_EVENT_WORLD));
        boolean currentToxicCloudOwnerApi = isSrparasites(jar) && hasCurrentToxicCloudOwnerApi(peers.get(ENTITY_TOXIC_CLOUD));
        Map<String, ClassInfo> srpCompatibilityPeers = peers;
        if (isSrparasites(jar)) {
            srpCompatibilityPeers = new java.util.HashMap<>(peers);
            for (String name : new String[] {SRP_TILE_ENTITY_DOD, SRP_EVENT_ENTITY, SRP_CONFIG_SYSTEMS}) {
                byte[] classBytes = classes.get(name);
                if (classBytes == null) continue;
                ClassNode currentClass = new ClassNode(Opcodes.ASM9);
                new ClassReader(classBytes).accept(currentClass, 0);
                srpCompatibilityPeers.put(name, ClassInfo.of(currentClass));
            }
        }
        boolean currentReinforcementChanceApi = isSrparasites(jar)
                && hasCurrentReinforcementChanceApi(srpCompatibilityPeers);
        int changes = SrpCotesiaParticleCompat.applyToSrp(jar, classes, peers);
        if (splitGoreApi) changes += remapSrpExtraGoreTypeLookup(classes);
        if (jar.version == LegacyVersion.V1_12_2 && hasCurrentMinimumAttackApi(peers.get(ENTITY_PARASITE), jar.version)) {
            changes += remapLegacyMinimumAttackCalls(classes);
        }
        if (hasCurrentFeralWorldApi(peers.get(ENTITY_PINFECTED), jar.version)) {
            changes += remapLegacyFeralConversionCalls(classes, jar.version);
        }
        if (isSrparasites(jar) && currentPhaseSpawnLists) changes += addLegacySpawnGetter(classes);
        if (isSrparasites(jar) && currentLegacyBiomeMethods) changes += addLegacyParasiteBiomeMethods(classes);
        if (isSrparasites(jar) && currentBiomePacket) changes += addLegacyBiomePacketConstructor(classes);
        if (isSrparasites(jar) && currentLegacyConfig) changes += addLegacyConfigAliases(classes);
        if (currentLegacyBeckonBlockTimer) changes += addLegacyBeckonBlockTimerApi(classes);
        if (currentToxicCloudOwnerApi) changes += addLegacyToxicCloudOwnerApi(classes);
        if (isSrparasites(jar)) changes += addLegacyWymoBurrowMethod(classes);
        if (currentReinforcementChanceApi) changes += addLegacyReinforcementChanceApi(classes);
        if (isSrparasites(jar)) changes += addLegacyGoreEnumAliases(classes);
        if (isSrparasites(jar)) changes += addLegacyHumanTextureAlias(classes);
        if (isSrparasites(jar) && (currentSpreadBiomeApi || currentKillBiomeApi)) {
            changes += addLegacyBiomeMethods(classes, currentSpreadBiomeApi, currentKillBiomeApi);
        }
        if (currentMergeApi) changes += addLegacyMergeMethod(classes);
        if (isSrparasites(jar) && currentSpawnListApi) changes += addLegacySpawnMMethod(classes);
        if (hasCurrentCruxAApi(peers)) changes += remapRemovedCruxReferences(classes);
        if (splitGoreApi || currentSaveDataApi || currentBeckonSpawnApi
                || currentBeckonCounterApi || currentParasiteBlockCounterApi || currentDirectionApi) {
            for (var entry : classes.entrySet()) {
                ClassNode node = new ClassNode(Opcodes.ASM9);
                new ClassReader(entry.getValue()).accept(node, 0);
                int classChanges = 0;
                if (splitGoreApi) classChanges += adaptGoreReferences(node, blocks, goreEnum);
                if (currentSaveDataApi) classChanges += adaptSaveDataCalls(node);
                if (currentBeckonSpawnApi) classChanges += remapMovedInfestationCalls(node);
                if (currentBeckonCounterApi) classChanges += remapInfestedCounterFields(node);
                if (currentParasiteBlockCounterApi) classChanges += remapParasiteCounterFields(node);
                if (currentDirectionApi) classChanges += remapDirectionCalls(node);
                if (classChanges == 0) continue;
                entry.setValue(writeClass(node));
                changes += classChanges;
            }
        }
        changes += addLegacyAiConstructors(jar, classes, peers);
        return changes;
    }

    /** Adds current SRP aliases to the scanned peer view only when the same SRP jar gets them at transform time. */
    public static void addLegacyPeerSignatures(LegacyJar jar, java.util.List<ClassInfo> classes) {
        if (!isSrparasites(jar)) return;
        Map<String, ClassInfo> byName = new java.util.HashMap<>();
        for (ClassInfo info : classes) byName.put(info.name, info);
        SrpCotesiaParticleCompat.addPeerSignatures(jar, classes);
        ClassInfo avoid = byName.get(AI_AVOID);
        ClassInfo melee = byName.get(AI_MELEE);
        if (hasCurrentAvoidApi(avoid)) avoid.methods.putIfAbsent("<init>" + OLD_AVOID_CTOR, Opcodes.ACC_PUBLIC);
        if (hasCurrentMeleeApi(melee)) melee.methods.putIfAbsent("<init>" + OLD_MELEE_CTOR, Opcodes.ACC_PUBLIC);
        ClassInfo spawning = byName.get(SRP_SPAWNING);
        if (hasCurrentPhaseSpawnLists(spawning)) {
            spawning.methods.putIfAbsent("getSpawns(B)Ljava/util/List;", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        }
        ClassInfo goreEnum = byName.get(GORE_ENUM);
        if (hasCurrentSplitGoreEnum(goreEnum)) addLegacyGoreEnumPeerFields(goreEnum);
        ClassInfo humanRenderer = byName.get(SRP_HUMAN_RENDERER);
        if (hasCurrentHumanTextureApi(humanRenderer)) {
            String linkedTextureDesc = "L" + LegacyVersion.V1_12_2.linkPrefix() + RESOURCE_LOCATION + ";";
            humanRenderer.fields.putIfAbsent(LEGACY_HUMAN_TEXTURE + ":" + linkedTextureDesc,
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC);
        }
        ClassInfo eventWorld = byName.get(SRP_EVENT_WORLD);
        ClassInfo eventEntity = byName.get(SRP_EVENT_ENTITY);
        ClassInfo wymo = byName.get(ENTITY_WYMO);
        if (hasCurrentWymoDiggingApi(wymo)) wymo.methods.putIfAbsent("setBurrowed(Z)V", Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC);
        if (hasCurrentReinforcementChanceApi(byName)) {
            ClassInfo dod = byName.get(SRP_TILE_ENTITY_DOD);
            dod.methods.putIfAbsent("getRSChance(B)D", Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC);
        }
        ClassInfo toxicCloud = byName.get(ENTITY_TOXIC_CLOUD);
        ClassInfo spreading = byName.get(BLOCK_PARASITE_SPREADING);
        if (hasCurrentToxicCloudOwnerApi(toxicCloud)) {
            String livingBase = "L" + LegacyVersion.V1_12_2.linkPrefix() + ENTITY_LIVING_BASE + ";";
            toxicCloud.methods.putIfAbsent("getOwner()" + livingBase, Opcodes.ACC_PUBLIC);
            toxicCloud.methods.putIfAbsent("setOwner(" + livingBase + ")V", Opcodes.ACC_PUBLIC);
        }
        if (hasCurrentSpawnListApi(eventEntity, LegacyVersion.V1_12_2)) {
            String prefix = LegacyVersion.V1_12_2.linkPrefix();
            String createEntityDesc = "(L" + prefix + "net/minecraft/util/ResourceLocation;L" + prefix
                    + "net/minecraft/world/World;)L" + prefix + "net/minecraft/entity/Entity;";
            eventEntity.methods.putIfAbsent("spawnM" + OLD_SPAWN_M_DESC, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
            eventEntity.addInstructionSite("spawnM" + OLD_SPAWN_M_DESC, "INVOKE", Opcodes.INVOKESTATIC,
                    "L" + prefix + "net/minecraft/entity/EntityList;createEntityByIDFromName" + createEntityDesc);
        }
        if (hasCurrentMergeApi(byName, LegacyVersion.V1_12_2)) {
            eventEntity.methods.putIfAbsent("merge" + OLD_MERGE_DESC, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
            String prefix = LegacyVersion.V1_12_2.linkPrefix();
            eventEntity.addInstructionSite("merge" + OLD_MERGE_DESC, "INVOKE", Opcodes.INVOKEVIRTUAL,
                    "L" + SRP_SAVE_DATA + ";setTotalKills(IIZL" + prefix + WORLD + ";ZI)Z");
        }
        if (hasCurrentSpreadBiomeApi(eventWorld, spreading, LegacyVersion.V1_12_2)) {
            eventWorld.methods.putIfAbsent("SpreadBiome" + linkedWorldPosDesc(LegacyVersion.V1_12_2, "I") + "V",
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        }
        ClassInfo config = byName.get(SRP_CONFIG);
        if (hasLegacyConfigReadApi(config, "initGeneralConfig")) {
            config.fields.putIfAbsent(LEGACY_USE_VINNIT + ":Z", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC);
        }
        ClassInfo systems = byName.get(SRP_CONFIG_SYSTEMS);
        if (hasLegacyConfigReadApi(systems, "initstatuseffectsConfig")) {
            systems.fields.putIfAbsent(LEGACY_EPIL_CHANCE_COTH + ":D", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC);
        }
        if (hasCurrentLegacyBiomeMethods(byName, LegacyVersion.V1_12_2)) {
            String oldBiomeMethod = linkedWorldPosDesc(LegacyVersion.V1_12_2, "") + "V";
            eventWorld = byName.get(SRP_EVENT_WORLD);
            eventWorld.methods.putIfAbsent("positionToParasiteBiome" + oldBiomeMethod,
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
            String generatorDesc = linkedWorldPosDesc(LegacyVersion.V1_12_2, "L" + RANDOM + ";") + "V";
            eventWorld.methods.putIfAbsent("spawnGenFeatureParasite" + generatorDesc,
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
            eventWorld.methods.putIfAbsent("spawnGenRoofParasite" + generatorDesc,
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        }
        ClassInfo biomePacket = byName.get(SRP_PACKET_BIOME_CHANGE);
        if (hasCurrentBiomePacket(biomePacket, LegacyVersion.V1_12_2)) {
            biomePacket.methods.putIfAbsent("<init>" + linkedBiomePacketCtor(LegacyVersion.V1_12_2), Opcodes.ACC_PUBLIC);
        }
        if (hasCurrentKillBiomeApi(byName)) {
            eventWorld.methods.putIfAbsent("killBiome" + linkedWorldPosDesc(LegacyVersion.V1_12_2, "I") + "V",
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        }
        if (hasCurrentLegacyBeckonBlockTimerApi(byName.get(SRP_CONFIG_MOBS), LegacyVersion.V1_12_2)
                && eventWorld != null && !eventWorld.hasMethod(LEGACY_BECKON_TIMER_METHOD, "(I)I")) {
            eventWorld.methods.putIfAbsent(LEGACY_BECKON_TIMER_METHOD + "(I)I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        }
    }

    private static int addLegacyAiConstructors(LegacyJar jar, Map<String, byte[]> classes, Map<String, ClassInfo> peers) {
        if (!isSrparasites(jar)) return 0;
        ClassInfo avoidPeer = peers.get(AI_AVOID);
        ClassInfo meleePeer = peers.get(AI_MELEE);
        int changes = 0;
        if (hasCurrentAvoidApi(avoidPeer)) {
            byte[] avoidBytes = classes.get(AI_AVOID);
            if (avoidBytes != null) {
                byte[] adapted = addAvoidConstructor(avoidBytes);
                if (adapted != null) {
                    classes.put(AI_AVOID, adapted);
                    changes++;
                }
            }
        }
        if (hasCurrentMeleeApi(meleePeer)) {
            byte[] meleeBytes = classes.get(AI_MELEE);
            if (meleeBytes != null) {
                byte[] adapted = addMeleeConstructor(meleeBytes);
                if (adapted != null) {
                    classes.put(AI_MELEE, adapted);
                    changes++;
                }
            }
        }
        return changes;
    }

    private static boolean isSrparasites(LegacyJar jar) {
        return jar.version == LegacyVersion.V1_12_2 && jar.mods.stream().anyMatch(mod -> mod.legacyId != null
                && mod.legacyId.equalsIgnoreCase("srparasites"));
    }

    private static boolean hasCurrentReinforcementChanceApi(Map<String, ClassInfo> peers) {
        ClassInfo dod = peers.get(SRP_TILE_ENTITY_DOD);
        ClassInfo eventEntity = peers.get(SRP_EVENT_ENTITY);
        ClassInfo systems = peers.get(SRP_CONFIG_SYSTEMS);
        String rawWorld = "(L" + WORLD + ";)D";
        String linkedWorld = "(L" + LegacyVersion.V1_12_2.linkPrefix() + WORLD + ";)D";
        if (dod == null || dod.hasMethod("getRSChance", "(B)D") || eventEntity == null || systems == null
                || !eventEntity.hasMethod("getRSchance", rawWorld) && !eventEntity.hasMethod("getRSchance", linkedWorld)) return false;
        for (String field : REINFORCEMENT_CHANCE_FIELDS) {
            Integer access = systems.fields.get(field + ":D");
            if (access == null || (access & Opcodes.ACC_STATIC) == 0) return false;
        }
        return true;
    }

    private static int addLegacyReinforcementChanceApi(Map<String, byte[]> classes) {
        byte[] bytes = classes.get(SRP_TILE_ENTITY_DOD);
        if (bytes == null) return 0;
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        if (!node.name.equals(SRP_TILE_ENTITY_DOD) || findMethod(node, "getRSChance", "(B)D") != null) return 0;
        MethodNode bridge = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                "getRSChance", "(B)D", null, null);
        bridge.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        for (String field : REINFORCEMENT_CHANCE_FIELDS) {
            bridge.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, SRP_CONFIG_SYSTEMS, field, "D"));
        }
        bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, REINFORCEMENT_CHANCE_HELPER,
                "getChance", REINFORCEMENT_CHANCE_HELPER_DESC, false));
        bridge.instructions.add(new InsnNode(Opcodes.DRETURN));
        bridge.maxStack = 21;
        bridge.maxLocals = 2;
        node.methods.add(bridge);
        classes.put(SRP_TILE_ENTITY_DOD, writeClass(node));
        return 1;
    }

    /** Current SRP routes skin 1 through TEXTURE_ALT; older SRPExtra renderers still read TEXTURE3. */
    private static boolean hasCurrentHumanTextureApi(ClassInfo info) {
        if (info == null) return false;
        String linkedTextureDesc = "L" + LegacyVersion.V1_12_2.linkPrefix() + RESOURCE_LOCATION + ";";
        return info.hasField(CURRENT_HUMAN_ALT_TEXTURE, linkedTextureDesc)
                && !info.hasField(LEGACY_HUMAN_TEXTURE, linkedTextureDesc);
    }

    private static int addLegacyHumanTextureAlias(Map<String, byte[]> classes) {
        byte[] bytes = classes.get(SRP_HUMAN_RENDERER);
        if (bytes == null) return 0;
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        if (!node.name.equals(SRP_HUMAN_RENDERER)
                || !hasField(node, CURRENT_HUMAN_ALT_TEXTURE, HUMAN_TEXTURE_DESC)
                || hasField(node, LEGACY_HUMAN_TEXTURE, HUMAN_TEXTURE_DESC)) return 0;

        MethodNode initializer = findMethod(node, "<clinit>", "()V");
        if (initializer == null) {
            initializer = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            initializer.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, SRP_HUMAN_RENDERER,
                    CURRENT_HUMAN_ALT_TEXTURE, HUMAN_TEXTURE_DESC));
            initializer.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, SRP_HUMAN_RENDERER,
                    LEGACY_HUMAN_TEXTURE, HUMAN_TEXTURE_DESC));
            initializer.instructions.add(new InsnNode(Opcodes.RETURN));
            initializer.maxStack = 1;
            node.methods.add(initializer);
        } else {
            int returns = 0;
            for (AbstractInsnNode instruction : initializer.instructions.toArray()) {
                if (instruction.getOpcode() != Opcodes.RETURN) continue;
                initializer.instructions.insertBefore(instruction, new FieldInsnNode(Opcodes.GETSTATIC,
                        SRP_HUMAN_RENDERER, CURRENT_HUMAN_ALT_TEXTURE, HUMAN_TEXTURE_DESC));
                initializer.instructions.insertBefore(instruction, new FieldInsnNode(Opcodes.PUTSTATIC,
                        SRP_HUMAN_RENDERER, LEGACY_HUMAN_TEXTURE, HUMAN_TEXTURE_DESC));
                returns++;
            }
            if (returns == 0) return 0;
            initializer.maxStack = Math.max(initializer.maxStack, 1);
        }

        node.fields.add(new org.objectweb.asm.tree.FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC
                | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, LEGACY_HUMAN_TEXTURE, HUMAN_TEXTURE_DESC, null, null));
        classes.put(SRP_HUMAN_RENDERER, writeClass(node));
        return 1;
    }

    private static boolean hasCurrentSplitGoreEnum(ClassInfo info) {
        if (info == null) return false;
        for (String size : SIZES) if (!info.hasField(size, ENUM_DESC)) return false;
        for (String family : FAMILIES) {
            for (String size : SIZES) if (info.hasField(family + "_" + size, ENUM_DESC)) return false;
        }
        return true;
    }

    private static void addLegacyGoreEnumPeerFields(ClassInfo info) {
        for (String family : FAMILIES) {
            for (String size : SIZES) {
                info.fields.putIfAbsent(family + "_" + size + ":" + ENUM_DESC,
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC);
            }
        }
    }

    private static int addLegacyGoreEnumAliases(Map<String, byte[]> classes) {
        byte[] bytes = classes.get(GORE_ENUM);
        if (bytes == null) return 0;
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        if (!node.name.equals(GORE_ENUM) || !hasCurrentSplitGoreEnum(ClassInfo.of(node))) return 0;
        MethodNode initializer = findMethod(node, "<clinit>", "()V");
        if (initializer == null) return 0;
        AbstractInsnNode returnInsn = initializer.instructions.getLast();
        while (returnInsn != null && returnInsn.getOpcode() < 0) returnInsn = returnInsn.getPrevious();
        if (returnInsn == null || returnInsn.getOpcode() != Opcodes.RETURN) return 0;
        org.objectweb.asm.tree.InsnList aliases = new org.objectweb.asm.tree.InsnList();
        for (String family : FAMILIES) {
            for (String size : SIZES) {
                String oldName = family + "_" + size;
                if (hasField(node, oldName)) return 0;
                node.fields.add(new org.objectweb.asm.tree.FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC
                        | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, oldName, ENUM_DESC, null, null));
                aliases.add(new FieldInsnNode(Opcodes.GETSTATIC, GORE_ENUM, size, ENUM_DESC));
                aliases.add(new FieldInsnNode(Opcodes.PUTSTATIC, GORE_ENUM, oldName, ENUM_DESC));
            }
        }
        initializer.instructions.insertBefore(returnInsn, aliases);
        initializer.maxStack = Math.max(initializer.maxStack, 1);
        classes.put(GORE_ENUM, writeClass(node));
        return FAMILIES.length * SIZES.length;
    }

    private static boolean hasCurrentWymoDiggingApi(ClassInfo info) {
        return info != null && info.hasMethod("getDigging", "()Z") && info.hasMethod("setDigging", "(Z)V");
    }

    private static boolean hasCurrentFeralWorldApi(ClassInfo info, LegacyVersion version) {
        return info != null && info.hasMethod("getFeral", "(L" + version.linkPrefix() + WORLD + ";)L" + ENTITY_PFERAL + ";");
    }

    private static int remapLegacyFeralConversionCalls(Map<String, byte[]> classes, LegacyVersion version) {
        String oldDescriptor = "()L" + ENTITY_PFERAL + ";";
        String world = WORLD;
        String entity = "net/minecraft/entity/Entity";
        String newDescriptor = "(L" + world + ";)L" + ENTITY_PFERAL + ";";
        String conversionDescriptor = "(L" + world + ";Z)L" + entity + ";";
        byte[] bytes = classes.get(SRP_EXTRA_INF_CONVERT_UNIT);
        if (bytes == null) return 0;
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        if (!node.name.equals(SRP_EXTRA_INF_CONVERT_UNIT)) return 0;
        MethodNode conversion = findMethod(node, "getConvertedEntity", conversionDescriptor);
        if (conversion == null) return 0;
        int changes = 0;
        for (AbstractInsnNode instruction : conversion.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !call.owner.equals(ENTITY_PINFECTED) || !call.name.equals("getFeral")
                    || !call.desc.equals(oldDescriptor)) continue;
            conversion.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 1));
            call.desc = newDescriptor;
            conversion.maxStack = Math.max(conversion.maxStack, 2);
            changes++;
        }
        if (changes != 0) classes.put(SRP_EXTRA_INF_CONVERT_UNIT, writeClass(node));
        return changes;
    }

    private static boolean hasCurrentMinimumAttackApi(ClassInfo info, LegacyVersion version) {
        if (info == null || !info.hasMethod(MINIMUM_ATTACK_METHOD, LEGACY_MINIMUM_ATTACK_CURRENT_DESC)
                && !info.hasMethod(MINIMUM_ATTACK_METHOD, "(L" + version.linkPrefix() + ENTITY_LIVING_BASE + ";F)Z")) {
            return false;
        }
        return info.hasMethod(MINI_DAMAGE_METHOD, "()F");
    }

    private static int remapLegacyMinimumAttackCalls(Map<String, byte[]> classes) {
        int changes = 0;
        for (var entry : classes.entrySet()) {
            ClassNode node = new ClassNode(Opcodes.ASM9);
            new ClassReader(entry.getValue()).accept(node, 0);
            int classChanges = 0;
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (!(instruction instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKEVIRTUAL
                            || !call.owner.equals(ENTITY_PARASITE) || !call.name.equals(MINIMUM_ATTACK_METHOD)
                            || !(call.desc.equals(LEGACY_MINIMUM_ATTACK_DESC)
                                    || call.desc.equals("(L" + LegacyVersion.V1_12_2.linkPrefix() + ENTITY_LIVING_BASE + ";)Z"))) {
                        continue;
                    }
                    String livingBase = call.desc.contains(LegacyVersion.V1_12_2.linkPrefix())
                            ? LegacyVersion.V1_12_2.linkPrefix() + ENTITY_LIVING_BASE : ENTITY_LIVING_BASE;
                    org.objectweb.asm.tree.InsnList damageArgument = new org.objectweb.asm.tree.InsnList();
                    damageArgument.add(new InsnNode(Opcodes.DUP2));
                    damageArgument.add(new InsnNode(Opcodes.POP));
                    damageArgument.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY_PARASITE, MINI_DAMAGE_METHOD, "()F", false));
                    method.instructions.insertBefore(call, damageArgument);
                    call.desc = "(L" + livingBase + ";F)Z";
                    method.maxStack = Math.max(method.maxStack, 4);
                    classChanges++;
                }
            }
            if (classChanges == 0) continue;
            entry.setValue(writeClass(node));
            changes += classChanges;
        }
        return changes;
    }

    private static boolean hasCurrentAvoidApi(ClassInfo info) {
        return info != null && info.hasMethod("<init>", NEW_AVOID_CTOR);
    }

    private static boolean hasCurrentMeleeApi(ClassInfo info) {
        if (info == null || !info.hasMethod("<init>", NEW_MELEE_CTOR)
                || !info.hasField(ATTACKER_FIELD, "L" + ENTITY_PARASITE + ";") || !info.hasField(ATTACK_TICK, "I")) return false;
        String attackMethod = null;
        for (String methodKey : info.methods.keySet()) {
            if (!methodKey.startsWith(ATTACK_CHECK + "(") || !methodKey.endsWith("D)V")) continue;
            if (info.instructionSiteCount(methodKey, "INVOKE", "L" + ENTITY_PARASITE + ";" + ATTACK_SPEED + "()I",
                    Opcodes.INVOKEVIRTUAL) != 1) continue;
            if (attackMethod != null) return false;
            attackMethod = methodKey;
        }
        return attackMethod != null;
    }

    private static boolean hasCurrentToxicCloudOwnerApi(ClassInfo info) {
        return info != null && info.hasMethod("getOwner", "()L" + ENTITY_PARASITE + ";")
                && info.hasMethod("setOwner", "(L" + ENTITY_PARASITE + ";)V");
    }

    private static byte[] addAvoidConstructor(byte[] bytes) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        if (!node.name.equals(AI_AVOID) || findMethod(node, "<init>", OLD_AVOID_CTOR) != null
                || findMethod(node, "<init>", NEW_AVOID_CTOR) == null) return null;
        MethodNode bridge = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "<init>", OLD_AVOID_CTOR, null, null);
        bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        bridge.instructions.add(new VarInsnNode(Opcodes.FLOAD, 2));
        bridge.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 10));
        bridge.instructions.add(new InsnNode(Opcodes.ICONST_2));
        bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, AI_AVOID, "<init>", NEW_AVOID_CTOR, false));
        bridge.instructions.add(new InsnNode(Opcodes.RETURN));
        bridge.maxStack = 5;
        bridge.maxLocals = 3;
        node.methods.add(bridge);
        return writeClass(node);
    }

    private static byte[] addMeleeConstructor(byte[] bytes) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        MethodNode attackCheck = findMethod(node, ATTACK_CHECK, ATTACK_CHECK_DESC);
        if (!node.name.equals(AI_MELEE) || findMethod(node, "<init>", OLD_MELEE_CTOR) != null
                || findMethod(node, "<init>", NEW_MELEE_CTOR) == null || attackCheck == null
                || hasField(node, LEGACY_ATTACK_COOLDOWN) || hasField(node, HAS_LEGACY_ATTACK_COOLDOWN)
                || findMethod(node, GET_ATTACK_COOLDOWN, "()I") != null || !redirectAttackCooldown(attackCheck)) return null;

        node.fields.add(new org.objectweb.asm.tree.FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC,
                LEGACY_ATTACK_COOLDOWN, "I", null, null));
        node.fields.add(new org.objectweb.asm.tree.FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC,
                HAS_LEGACY_ATTACK_COOLDOWN, "Z", null, null));
        MethodNode bridge = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "<init>", OLD_MELEE_CTOR, null, null);
        bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        bridge.instructions.add(new VarInsnNode(Opcodes.DLOAD, 2));
        bridge.instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
        bridge.instructions.add(new VarInsnNode(Opcodes.DLOAD, 5));
        bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, AI_MELEE, "<init>", NEW_MELEE_CTOR, false));
        bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        bridge.instructions.add(new VarInsnNode(Opcodes.ILOAD, 7));
        bridge.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, AI_MELEE, LEGACY_ATTACK_COOLDOWN, "I"));
        bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        bridge.instructions.add(new InsnNode(Opcodes.ICONST_1));
        bridge.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, AI_MELEE, HAS_LEGACY_ATTACK_COOLDOWN, "Z"));
        bridge.instructions.add(new InsnNode(Opcodes.RETURN));
        bridge.maxStack = 7;
        bridge.maxLocals = 8;
        node.methods.add(bridge);
        node.methods.add(attackCooldownGetter());
        return writeClass(node);
    }

    private static int addLegacyWymoBurrowMethod(Map<String, byte[]> classes) {
        byte[] bytes = classes.get(ENTITY_WYMO);
        if (bytes == null) return 0;
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        MethodNode digging = findMethod(node, "setDigging", "(Z)V");
        if (!node.name.equals(ENTITY_WYMO) || digging == null || findMethod(node, "setBurrowed", "(Z)V") != null) return 0;

        FieldInsnNode dataManager = null;
        FieldInsnNode dataParameter = null;
        MethodInsnNode setData = null;
        for (AbstractInsnNode instruction : digging.instructions) {
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                    && field.desc.endsWith("/EntityDataManager;")) {
                dataManager = field;
            } else if (dataManager != null && instruction instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.GETSTATIC && field.desc.endsWith("/DataParameter;")) {
                dataParameter = field;
            } else if (dataParameter != null && instruction instanceof MethodInsnNode call
                    && call.getOpcode() == Opcodes.INVOKEVIRTUAL && call.desc.endsWith("/DataParameter;Ljava/lang/Object;)V")) {
                setData = call;
                break;
            }
        }
        if (dataManager == null || dataParameter == null || setData == null) return 0;

        MethodNode legacy = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                "setBurrowed", "(Z)V", null, null);
        legacy.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        legacy.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, dataManager.owner, dataManager.name, dataManager.desc));
        legacy.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, dataParameter.owner, dataParameter.name, dataParameter.desc));
        legacy.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        legacy.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf",
                "(Z)Ljava/lang/Boolean;", false));
        legacy.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, setData.owner, setData.name, setData.desc, false));
        legacy.instructions.add(new InsnNode(Opcodes.RETURN));
        legacy.maxStack = 3;
        legacy.maxLocals = 2;
        node.methods.add(legacy);
        classes.put(ENTITY_WYMO, writeClass(node));
        return 1;
    }

    private static int addLegacyToxicCloudOwnerApi(Map<String, byte[]> classes) {
        byte[] bytes = classes.get(ENTITY_TOXIC_CLOUD);
        if (bytes == null) return 0;
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        String currentGetOwner = "()L" + ENTITY_PARASITE + ";";
        String currentSetOwner = "(L" + ENTITY_PARASITE + ";)V";
        MethodNode getOwner = findMethod(node, "getOwner", currentGetOwner);
        MethodNode setOwner = findMethod(node, "setOwner", currentSetOwner);
        if (!node.name.equals(ENTITY_TOXIC_CLOUD) || getOwner == null || setOwner == null
                || findMethod(node, "getOwner", LEGACY_CLOUD_GET_OWNER_DESC) != null
                || findMethod(node, "setOwner", LEGACY_CLOUD_SET_OWNER_DESC) != null
                || hasField(node, LEGACY_CLOUD_OWNER_FIELD) || hasField(node, LEGACY_CLOUD_OWNER_SET_FIELD)) return 0;

        node.fields.add(new org.objectweb.asm.tree.FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC,
                LEGACY_CLOUD_OWNER_FIELD, "L" + ENTITY_LIVING_BASE + ";", null, null));
        node.fields.add(new org.objectweb.asm.tree.FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC,
                LEGACY_CLOUD_OWNER_SET_FIELD, "Z", null, null));
        org.objectweb.asm.tree.InsnList clearOverride = new org.objectweb.asm.tree.InsnList();
        clearOverride.add(new VarInsnNode(Opcodes.ALOAD, 0));
        clearOverride.add(new InsnNode(Opcodes.ICONST_0));
        clearOverride.add(new FieldInsnNode(Opcodes.PUTFIELD, ENTITY_TOXIC_CLOUD, LEGACY_CLOUD_OWNER_SET_FIELD, "Z"));
        setOwner.instructions.insert(clearOverride);

        node.methods.add(legacyToxicCloudGetOwner(currentGetOwner));
        node.methods.add(legacyToxicCloudSetOwner(currentSetOwner));
        classes.put(ENTITY_TOXIC_CLOUD, writeClass(node));
        return 1;
    }

    private static MethodNode legacyToxicCloudGetOwner(String currentGetOwner) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "getOwner", LEGACY_CLOUD_GET_OWNER_DESC, null, null);
        LabelNode currentOwner = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_TOXIC_CLOUD, LEGACY_CLOUD_OWNER_SET_FIELD, "Z"));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, currentOwner));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_TOXIC_CLOUD, LEGACY_CLOUD_OWNER_FIELD, "L" + ENTITY_LIVING_BASE + ";"));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.instructions.add(currentOwner);
        method.instructions.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY_TOXIC_CLOUD, "getOwner", currentGetOwner, false));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxStack = 1;
        method.maxLocals = 1;
        return method;
    }

    private static MethodNode legacyToxicCloudSetOwner(String currentSetOwner) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "setOwner", LEGACY_CLOUD_SET_OWNER_DESC, null, null);
        LabelNode notParasite = new LabelNode();
        LabelNode storeOwner = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, ENTITY_PARASITE));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, notParasite));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, ENTITY_PARASITE));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY_TOXIC_CLOUD, "setOwner", currentSetOwner, false));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, storeOwner));
        method.instructions.add(notParasite);
        method.instructions.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY_TOXIC_CLOUD, "setOwner", currentSetOwner, false));
        method.instructions.add(storeOwner);
        method.instructions.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, ENTITY_TOXIC_CLOUD, LEGACY_CLOUD_OWNER_FIELD,
                "L" + ENTITY_LIVING_BASE + ";"));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, ENTITY_TOXIC_CLOUD, LEGACY_CLOUD_OWNER_SET_FIELD, "Z"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 2;
        method.maxLocals = 2;
        return method;
    }

    private static boolean hasField(ClassNode node, String name) {
        return node.fields.stream().anyMatch(field -> field.name.equals(name));
    }

    private static boolean redirectAttackCooldown(MethodNode attackCheck) {
        MethodInsnNode target = null;
        for (AbstractInsnNode instruction : attackCheck.instructions) {
            if (!(instruction instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !call.owner.equals(ENTITY_PARASITE) || !call.name.equals(ATTACK_SPEED) || !call.desc.equals("()I")) continue;
            if (target != null) return false;
            target = call;
        }
        if (target == null) return false;
        AbstractInsnNode fieldInstruction = previousCode(target.getPrevious());
        AbstractInsnNode receiver = fieldInstruction == null ? null : previousCode(fieldInstruction.getPrevious());
        if (!(fieldInstruction instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETFIELD
                || !field.owner.equals(AI_MELEE) || !field.name.equals(ATTACKER_FIELD)
                || !field.desc.equals("L" + ENTITY_PARASITE + ";")
                || !(receiver instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ALOAD || load.var != 0) return false;
        attackCheck.instructions.remove(field);
        attackCheck.instructions.set(target, new MethodInsnNode(Opcodes.INVOKESPECIAL, AI_MELEE, GET_ATTACK_COOLDOWN, "()I", false));
        return true;
    }

    private static MethodNode attackCooldownGetter() {
        MethodNode getter = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC,
                GET_ATTACK_COOLDOWN, "()I", null, null);
        LabelNode useEntityAttackSpeed = new LabelNode();
        getter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getter.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, AI_MELEE, HAS_LEGACY_ATTACK_COOLDOWN, "Z"));
        getter.instructions.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFEQ, useEntityAttackSpeed));
        getter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getter.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, AI_MELEE, LEGACY_ATTACK_COOLDOWN, "I"));
        getter.instructions.add(new InsnNode(Opcodes.IRETURN));
        getter.instructions.add(useEntityAttackSpeed);
        getter.instructions.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        getter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getter.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, AI_MELEE, ATTACKER_FIELD, "L" + ENTITY_PARASITE + ";"));
        getter.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY_PARASITE, ATTACK_SPEED, "()I", false));
        getter.instructions.add(new InsnNode(Opcodes.IRETURN));
        getter.maxStack = 1;
        getter.maxLocals = 1;
        return getter;
    }

    private static AbstractInsnNode previousCode(AbstractInsnNode instruction) {
        while (instruction != null && (instruction instanceof LabelNode || instruction instanceof LineNumberNode
                || instruction instanceof FrameNode)) instruction = instruction.getPrevious();
        return instruction;
    }

    private static MethodNode findMethod(ClassNode node, String name, String descriptor) {
        for (MethodNode method : node.methods) if (method.name.equals(name) && method.desc.equals(descriptor)) return method;
        return null;
    }

    private static byte[] writeClass(ClassNode node) {
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean hasCall(MethodNode method, String owner, String name, String descriptor) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)
                    && call.desc.equals(descriptor)) return true;
        }
        return false;
    }

    private static boolean hasCurrentPhaseSpawnLists(ClassInfo info) {
        if (info == null) return false;
        for (String fieldName : PHASE_LIST_FIELDS) {
            Integer access = info.fields.get(fieldName + ":" + SPAWN_LIST_DESC);
            if (access == null || (access & Opcodes.ACC_STATIC) == 0) return false;
        }
        return true;
    }

    private static String linkedWorldPosDesc(LegacyVersion version, String tail) {
        String world = "L" + version.linkPrefix() + WORLD + ";";
        String blockPos = "L" + version.linkPrefix() + BLOCK_POS + ";";
        return "(" + world + blockPos + tail + ")";
    }

    private static boolean hasCurrentSpreadBiomeApi(ClassInfo eventWorld, ClassInfo spreading, LegacyVersion version) {
        String rawSpreadDesc = "(L" + WORLD + ";L" + BLOCK_POS + ";II)V";
        String linkedSpreadDesc = linkedWorldPosDesc(version, "II") + "V";
        String rawTypeDesc = "(L" + WORLD + ";L" + BLOCK_POS + ";Z)I";
        String linkedTypeDesc = linkedWorldPosDesc(version, "Z") + "I";
        return hasStaticMethod(eventWorld, "canBiomeStillExistType", rawTypeDesc, linkedTypeDesc)
                && hasStaticMethod(spreading, "SpreadBiome", rawSpreadDesc, linkedSpreadDesc);
    }

    private static boolean hasCurrentKillBiomeApi(Map<String, ClassInfo> peers) {
        ClassInfo eventWorld = peers.get(SRP_EVENT_WORLD);
        ClassInfo parasiteBiome = peers.get(BIOME_PARASITE_BASE);
        ClassInfo queue = peers.get(BIOME_UPDATE_QUEUE);
        Integer enqueue = queue == null ? null : queue.methods.get("enqueue" + BIOME_UPDATE_ENQUEUE_DESC);
        return eventWorld != null && parasiteBiome != null && enqueue != null && (enqueue & Opcodes.ACC_STATIC) != 0;
    }

    private static boolean hasCurrentLegacyBiomeMethods(Map<String, ClassInfo> peers, LegacyVersion version) {
        ClassInfo eventWorld = peers.get(SRP_EVENT_WORLD);
        ClassInfo spreading = peers.get(BIOME_PARASITE_SPREADING);
        ClassInfo biome = peers.get(BIOME_PARASITE_BASE);
        String position = "(L" + WORLD + ";L" + BLOCK_POS + ";I)V";
        String linkedPosition = linkedWorldPosDesc(version, "I") + "V";
        String generator = "(L" + WORLD + ";L" + BLOCK_POS + ";L" + RANDOM + ";)V";
        String linkedGenerator = linkedWorldPosDesc(version, "L" + RANDOM + ";") + "V";
        return eventWorld != null && hasStaticMethod(spreading, "positionToParasiteBiome", position, linkedPosition)
                && hasInstanceMethod(biome, "spawnGenFeatureParasite", generator, linkedGenerator)
                && hasInstanceMethod(biome, "spawnGenRoofParasite", generator, linkedGenerator);
    }

    private static boolean hasInstanceMethod(ClassInfo info, String name, String... descriptors) {
        if (info == null) return false;
        for (String descriptor : descriptors) {
            Integer access = info.methods.get(name + descriptor);
            if (access != null && (access & Opcodes.ACC_STATIC) == 0) return true;
        }
        return false;
    }

    private static boolean hasCurrentBiomePacket(ClassInfo info, LegacyVersion version) {
        String linkedDescriptor = "(L" + version.linkPrefix() + BLOCK_POS + ";ZI)V";
        return info != null && (info.hasMethod("<init>", NEW_BIOME_PACKET_CTOR)
                || info.hasMethod("<init>", linkedDescriptor));
    }

    private static String linkedBiomePacketCtor(LegacyVersion version) {
        return "(L" + version.linkPrefix() + BLOCK_POS + ";Z)V";
    }

    private static boolean hasCurrentLegacyConfig(Map<String, ClassInfo> peers, LegacyVersion version) {
        return hasLegacyConfigReadApi(peers.get(SRP_CONFIG), "initGeneralConfig", version)
                && hasLegacyConfigReadApi(peers.get(SRP_CONFIG_SYSTEMS), "initstatuseffectsConfig", version);
    }

    private static boolean hasLegacyConfigReadApi(ClassInfo info, String initializer) {
        return hasLegacyConfigReadApi(info, initializer, LegacyVersion.V1_12_2);
    }

    private static boolean hasLegacyConfigReadApi(ClassInfo info, String initializer, LegacyVersion version) {
        String config = "L" + version.linkPrefix() + CONFIGURATION + ";";
        Integer read = info == null ? null : info.methods.get("readConfig()Z");
        Integer init = info == null ? null : info.methods.get(initializer + "(" + config + ")V");
        return read != null && (read & Opcodes.ACC_STATIC) != 0 && init != null && (init & Opcodes.ACC_STATIC) != 0;
    }

    private static boolean hasCurrentLegacyBeckonBlockTimerApi(ClassInfo configMobs, LegacyVersion version) {
        if (configMobs == null) return false;
        String linkedConfig = "(L" + version.linkPrefix() + CONFIGURATION + ";)V";
        String[] currentCooldownFields = {"venkrolCooldown", "venkrolsiiCooldown", "venkrolsiiiCooldown"};
        for (int i = 0; i < LEGACY_BECKON_TIMER_INIT_METHODS.length; i++) {
            Integer init = configMobs.methods.get(LEGACY_BECKON_TIMER_INIT_METHODS[i] + linkedConfig);
            Integer cooldown = configMobs.fields.get(currentCooldownFields[i] + ":I");
            if (init == null || (init & Opcodes.ACC_STATIC) == 0
                    || cooldown == null || (cooldown & Opcodes.ACC_STATIC) == 0
                    || configMobs.hasFieldNamed(LEGACY_BECKON_TIMER_FIELDS[i])) return false;
        }
        return true;
    }

    private static boolean lacksLegacyBeckonBlockTimerMethod(byte[] eventWorldBytes) {
        if (eventWorldBytes == null) return false;
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(eventWorldBytes).accept(node, 0);
        return node.name.equals(SRP_EVENT_WORLD) && findMethod(node, LEGACY_BECKON_TIMER_METHOD, "(I)I") == null;
    }

    private static int addLegacyBeckonBlockTimerApi(Map<String, byte[]> classes) {
        byte[] configBytes = classes.get(SRP_CONFIG_MOBS);
        byte[] eventWorldBytes = classes.get(SRP_EVENT_WORLD);
        if (configBytes == null || eventWorldBytes == null) return 0;

        ClassNode config = new ClassNode(Opcodes.ASM9);
        new ClassReader(configBytes).accept(config, 0);
        ClassNode eventWorld = new ClassNode(Opcodes.ASM9);
        new ClassReader(eventWorldBytes).accept(eventWorld, 0);
        if (!config.name.equals(SRP_CONFIG_MOBS) || !eventWorld.name.equals(SRP_EVENT_WORLD)
                || findMethod(eventWorld, LEGACY_BECKON_TIMER_METHOD, "(I)I") != null) return 0;

        boolean[] addedFields = new boolean[LEGACY_BECKON_TIMER_FIELDS.length];
        int changes = 0;
        for (int i = 0; i < LEGACY_BECKON_TIMER_FIELDS.length; i++) {
            String fieldName = LEGACY_BECKON_TIMER_FIELDS[i];
            if (hasField(config, fieldName)) return 0;
            config.fields.add(new org.objectweb.asm.tree.FieldNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, fieldName, "I", null, null));
            addedFields[i] = true;
            changes++;
            MethodNode init = findMethod(config, LEGACY_BECKON_TIMER_INIT_METHODS[i],
                    "(L" + CONFIGURATION + ";)V");
            if (init == null || !addLegacyBeckonTimerConfigRead(init, i)) return 0;
            changes++;
        }
        boolean addedAnyField = false;
        for (boolean addedField : addedFields) addedAnyField |= addedField;
        if (addedAnyField) {
            MethodNode initializer = findMethod(config, "<clinit>", "()V");
            if (initializer == null) {
                initializer = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
                initializer.instructions.add(new InsnNode(Opcodes.RETURN));
                initializer.maxStack = 0;
                initializer.maxLocals = 0;
                config.methods.add(initializer);
            }
            for (AbstractInsnNode instruction : initializer.instructions.toArray()) {
                if (instruction.getOpcode() != Opcodes.RETURN) continue;
                org.objectweb.asm.tree.InsnList defaults = new org.objectweb.asm.tree.InsnList();
                for (int i = 0; i < addedFields.length; i++) {
                    if (!addedFields[i]) continue;
                    defaults.add(new IntInsnNode(Opcodes.BIPUSH, LEGACY_BECKON_TIMER_DEFAULTS[i]));
                    defaults.add(new FieldInsnNode(Opcodes.PUTSTATIC, SRP_CONFIG_MOBS, LEGACY_BECKON_TIMER_FIELDS[i], "I"));
                }
                initializer.instructions.insertBefore(instruction, defaults);
            }
            initializer.maxStack = Math.max(initializer.maxStack, 1);
            changes++;
        }
        MethodNode timer = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                LEGACY_BECKON_TIMER_METHOD, "(I)I", null, null);
        timer.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        for (String field : LEGACY_BECKON_TIMER_FIELDS) {
            timer.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, SRP_CONFIG_MOBS, field, "I"));
        }
        timer.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LEGACY_BECKON_TIMER_HELPER,
                LEGACY_BECKON_TIMER_METHOD, LEGACY_BECKON_TIMER_HELPER_DESC, false));
        timer.instructions.add(new InsnNode(Opcodes.IRETURN));
        timer.maxStack = 4;
        timer.maxLocals = 1;
        eventWorld.methods.add(timer);
        classes.put(SRP_CONFIG_MOBS, writeClass(config));
        classes.put(SRP_EVENT_WORLD, writeClass(eventWorld));
        return changes + 1;
    }

    private static boolean addLegacyBeckonTimerConfigRead(MethodNode init, int stage) {
        for (AbstractInsnNode instruction : init.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(CONFIGURATION)
                    && call.name.equals("getInt") && call.desc.equals("(Ljava/lang/String;Ljava/lang/String;IIILjava/lang/String;)I")
                    && instruction.getNext() instanceof FieldInsnNode field
                    && field.owner.equals(SRP_CONFIG_MOBS) && field.name.equals(LEGACY_BECKON_TIMER_FIELDS[stage])) return false;
        }
        String stageName = LEGACY_BECKON_TIMER_KEYS[stage].replace(" Block Infestation Cooldown", "");
        boolean inserted = false;
        for (AbstractInsnNode instruction : init.instructions.toArray()) {
            if (instruction.getOpcode() != Opcodes.RETURN) continue;
            org.objectweb.asm.tree.InsnList read = new org.objectweb.asm.tree.InsnList();
            read.add(new VarInsnNode(Opcodes.ALOAD, 0));
            read.add(new org.objectweb.asm.tree.LdcInsnNode(LEGACY_BECKON_TIMER_KEYS[stage]));
            read.add(new org.objectweb.asm.tree.LdcInsnNode(LEGACY_BECKON_TIMER_CATEGORIES[stage]));
            read.add(new IntInsnNode(Opcodes.BIPUSH, LEGACY_BECKON_TIMER_DEFAULTS[stage]));
            read.add(new InsnNode(Opcodes.ICONST_4));
            read.add(new IntInsnNode(Opcodes.SIPUSH, 10000));
            read.add(new org.objectweb.asm.tree.LdcInsnNode(
                    "Minimum time required (in seconds) to infest nearby block for " + stageName + "."));
            read.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CONFIGURATION, "getInt",
                    "(Ljava/lang/String;Ljava/lang/String;IIILjava/lang/String;)I", false));
            read.add(new FieldInsnNode(Opcodes.PUTSTATIC, SRP_CONFIG_MOBS, LEGACY_BECKON_TIMER_FIELDS[stage], "I"));
            init.instructions.insertBefore(instruction, read);
            inserted = true;
        }
        if (inserted) init.maxStack = Math.max(init.maxStack, 7);
        return inserted;
    }

    private static int addLegacyParasiteBiomeMethods(Map<String, byte[]> classes) {
        byte[] bytes = classes.get(SRP_EVENT_WORLD);
        if (bytes == null) return 0;
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        if (!node.name.equals(SRP_EVENT_WORLD)) return 0;
        int changes = 0;
        if (findMethod(node, "positionToParasiteBiome", OLD_POSITION_TO_PARASITE_BIOME) == null) {
            node.methods.add(legacyPositionToParasiteBiome());
            changes++;
        }
        if (findMethod(node, "spawnGenFeatureParasite", OLD_PARASITE_GENERATOR) == null) {
            node.methods.add(legacyParasiteGenerator("spawnGenFeatureParasite"));
            changes++;
        }
        if (findMethod(node, "spawnGenRoofParasite", OLD_PARASITE_GENERATOR) == null) {
            node.methods.add(legacyParasiteGenerator("spawnGenRoofParasite"));
            changes++;
        }
        if (changes != 0) classes.put(SRP_EVENT_WORLD, writeClass(node));
        return changes;
    }

    private static MethodNode legacyPositionToParasiteBiome() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "positionToParasiteBiome", OLD_POSITION_TO_PARASITE_BIOME, null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BIOME_PARASITE_SPREADING,
                "positionToParasiteBiome", NEW_POSITION_TO_PARASITE_BIOME, false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 3;
        method.maxLocals = 2;
        return method;
    }

    private static MethodNode legacyParasiteGenerator(String name) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name,
                OLD_PARASITE_GENERATOR, null, null);
        LabelNode done = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WORLD, "func_180494_b",
                "(L" + BLOCK_POS + ";)L" + BIOME + ";", false));
        method.instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, BIOME_PARASITE_BASE));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, done));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WORLD, "func_180494_b",
                "(L" + BLOCK_POS + ";)L" + BIOME + ";", false));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, BIOME_PARASITE_BASE));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BIOME_PARASITE_BASE, name,
                OLD_PARASITE_GENERATOR, false));
        method.instructions.add(done);
        method.instructions.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 4;
        method.maxLocals = 3;
        return method;
    }

    private static int addLegacyBiomePacketConstructor(Map<String, byte[]> classes) {
        byte[] bytes = classes.get(SRP_PACKET_BIOME_CHANGE);
        if (bytes == null) return 0;
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        if (!node.name.equals(SRP_PACKET_BIOME_CHANGE) || findMethod(node, "<init>", NEW_BIOME_PACKET_CTOR) == null
                || findMethod(node, "<init>", OLD_BIOME_PACKET_CTOR) != null) return 0;
        MethodNode bridge = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "<init>", OLD_BIOME_PACKET_CTOR, null, null);
        bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        bridge.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        bridge.instructions.add(new InsnNode(Opcodes.ICONST_0));
        bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, SRP_PACKET_BIOME_CHANGE,
                "<init>", NEW_BIOME_PACKET_CTOR, false));
        bridge.instructions.add(new InsnNode(Opcodes.RETURN));
        bridge.maxStack = 4;
        bridge.maxLocals = 3;
        node.methods.add(bridge);
        classes.put(SRP_PACKET_BIOME_CHANGE, writeClass(node));
        return 1;
    }

    private static int addLegacyConfigAliases(Map<String, byte[]> classes) {
        int changes = addLegacyBooleanConfigAlias(classes);
        changes += addLegacyFloatConfigAlias(classes);
        return changes;
    }

    private static int addLegacyBooleanConfigAlias(Map<String, byte[]> classes) {
        byte[] bytes = classes.get(SRP_CONFIG);
        if (bytes == null) return 0;
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        if (!node.name.equals(SRP_CONFIG) || hasField(node, LEGACY_USE_VINNIT, "Z")) return 0;
        MethodNode read = findMethod(node, "readConfig", "()Z");
        MethodInsnNode initialize = findSingleConfigInitializer(read, SRP_CONFIG, "initGeneralConfig");
        if (initialize == null) return 0;
        node.fields.add(new org.objectweb.asm.tree.FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                LEGACY_USE_VINNIT, "Z", null, null));
        org.objectweb.asm.tree.InsnList readLegacyValue = new org.objectweb.asm.tree.InsnList();
        readLegacyValue.add(new VarInsnNode(Opcodes.ALOAD, 0));
        readLegacyValue.add(new org.objectweb.asm.tree.LdcInsnNode("Mod Weapons Second Texture"));
        readLegacyValue.add(new org.objectweb.asm.tree.LdcInsnNode("configuration_general"));
        readLegacyValue.add(new InsnNode(Opcodes.ICONST_0));
        readLegacyValue.add(new org.objectweb.asm.tree.LdcInsnNode("Set to true if you want to change texture for weapons."));
        readLegacyValue.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CONFIGURATION, "getBoolean",
                "(Ljava/lang/String;Ljava/lang/String;ZLjava/lang/String;)Z", false));
        readLegacyValue.add(new FieldInsnNode(Opcodes.PUTSTATIC, SRP_CONFIG, LEGACY_USE_VINNIT, "Z"));
        read.instructions.insert(initialize, readLegacyValue);
        read.maxStack = Math.max(read.maxStack, 5);
        classes.put(SRP_CONFIG, writeClass(node));
        return 1;
    }

    private static int addLegacyFloatConfigAlias(Map<String, byte[]> classes) {
        byte[] bytes = classes.get(SRP_CONFIG_SYSTEMS);
        if (bytes == null) return 0;
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        if (!node.name.equals(SRP_CONFIG_SYSTEMS) || hasField(node, LEGACY_EPIL_CHANCE_COTH, "D")) return 0;
        MethodNode read = findMethod(node, "readConfig", "()Z");
        MethodInsnNode initialize = findSingleConfigInitializer(read, SRP_CONFIG_SYSTEMS, "initstatuseffectsConfig");
        if (initialize == null) return 0;
        node.fields.add(new org.objectweb.asm.tree.FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                LEGACY_EPIL_CHANCE_COTH, "D", null, null));
        org.objectweb.asm.tree.InsnList readLegacyValue = new org.objectweb.asm.tree.InsnList();
        readLegacyValue.add(new VarInsnNode(Opcodes.ALOAD, 0));
        readLegacyValue.add(new org.objectweb.asm.tree.LdcInsnNode("Camouflage Chance"));
        readLegacyValue.add(new org.objectweb.asm.tree.LdcInsnNode("status_effects"));
        readLegacyValue.add(new org.objectweb.asm.tree.LdcInsnNode(0.7F));
        readLegacyValue.add(new InsnNode(Opcodes.FCONST_0));
        readLegacyValue.add(new InsnNode(Opcodes.FCONST_1));
        readLegacyValue.add(new org.objectweb.asm.tree.LdcInsnNode("(1 = 100%) Chance to avoid getting infected by COTH."));
        readLegacyValue.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CONFIGURATION, "getFloat",
                "(Ljava/lang/String;Ljava/lang/String;FFFLjava/lang/String;)F", false));
        readLegacyValue.add(new InsnNode(Opcodes.F2D));
        readLegacyValue.add(new FieldInsnNode(Opcodes.PUTSTATIC, SRP_CONFIG_SYSTEMS, LEGACY_EPIL_CHANCE_COTH, "D"));
        read.instructions.insert(initialize, readLegacyValue);
        read.maxStack = Math.max(read.maxStack, 7);
        classes.put(SRP_CONFIG_SYSTEMS, writeClass(node));
        return 1;
    }

    private static boolean hasField(ClassNode node, String name, String descriptor) {
        return node.fields.stream().anyMatch(field -> field.name.equals(name) && field.desc.equals(descriptor));
    }

    private static MethodInsnNode findSingleConfigInitializer(MethodNode read, String owner, String name) {
        if (read == null) return null;
        MethodInsnNode found = null;
        String descriptor = "(L" + CONFIGURATION + ";)V";
        for (AbstractInsnNode instruction : read.instructions) {
            if (!(instruction instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESTATIC
                    || !call.owner.equals(owner) || !call.name.equals(name) || !call.desc.equals(descriptor)) continue;
            if (found != null) return null;
            found = call;
        }
        return found;
    }

    private static int addLegacyBiomeMethods(Map<String, byte[]> classes, boolean addSpread, boolean addKill) {
        byte[] bytes = classes.get(SRP_EVENT_WORLD);
        if (bytes == null) return 0;
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        if (!node.name.equals(SRP_EVENT_WORLD)) return 0;
        int changes = 0;
        String oldSpreadDesc = "(L" + WORLD + ";L" + BLOCK_POS + ";I)V";
        if (addSpread && findMethod(node, "SpreadBiome", oldSpreadDesc) == null) {
            node.methods.add(legacySpreadBiomeMethod());
            changes++;
        }
        if (addKill && findMethod(node, "killBiome", oldSpreadDesc) == null) {
            node.methods.add(legacyKillBiomeMethod());
            changes++;
        }
        if (changes != 0) classes.put(SRP_EVENT_WORLD, writeClass(node));
        return changes;
    }

    private static int addLegacyMergeMethod(Map<String, byte[]> classes) {
        byte[] bytes = classes.get(SRP_EVENT_ENTITY);
        if (bytes == null) return 0;
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        if (!node.name.equals(SRP_EVENT_ENTITY) || findMethod(node, "merge", OLD_MERGE_DESC) != null) return 0;
        node.methods.add(legacyMergeMethod());
        classes.put(SRP_EVENT_ENTITY, writeClass(node));
        return 1;
    }

    private static MethodNode legacyMergeMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "merge", OLD_MERGE_DESC, null, null);
        LabelNode noParticle = new LabelNode();
        LabelNode selected = new LabelNode();
        LabelNode afterSpawn = new LabelNode();
        LabelNode spawnEntity = new LabelNode();
        LabelNode evolve = new LabelNode();
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, SRP_CONFIG_SYSTEMS, "mergeRandom", "Z"));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 3));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, noParticle));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 7));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY_PARASITE, "particleStatus", "(B)V", false));
        method.instructions.add(noParticle);
        method.instructions.add(new FrameNode(Opcodes.F_FULL, 4,
                new Object[]{ENTITY_PARASITE, Opcodes.INTEGER, "java/lang/String", Opcodes.INTEGER}, 0, null));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_PARASITE, "field_70170_p", "L" + WORLD + ";"));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 4));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, SRP_CONFIG_SYSTEMS, "mergeMobTable", "[Ljava/lang/String;"));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, SRP_MERGE_HELPER, "select",
                "([Ljava/lang/String;IZ)Ljava/lang/String;", false));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 5));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 5));
        method.instructions.add(new JumpInsnNode(Opcodes.IFNONNULL, selected));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(selected);
        method.instructions.add(new FrameNode(Opcodes.F_FULL, 6,
                new Object[]{ENTITY_PARASITE, Opcodes.INTEGER, "java/lang/String", Opcodes.INTEGER, WORLD, "java/lang/String"}, 0, null));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 5));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, SRP_MERGE_HELPER, "createMergedEntity",
                "(Lnet/minecraft/entity/Entity;L" + WORLD + ";Ljava/lang/String;)Lnet/minecraft/entity/EntityLiving;", false));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 6));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 6));
        method.instructions.add(new JumpInsnNode(Opcodes.IFNULL, afterSpawn));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 6));
        method.instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, ENTITY_PARASITE));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, spawnEntity));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 6));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, ENTITY_PARASITE));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, SRP_CONFIG, "convertedDespawn", "Z"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY_PARASITE, "cannotDespawn", "(Z)V", false));
        method.instructions.add(spawnEntity);
        method.instructions.add(new FrameNode(Opcodes.F_FULL, 7,
                new Object[]{ENTITY_PARASITE, Opcodes.INTEGER, "java/lang/String", Opcodes.INTEGER, WORLD, "java/lang/String", "net/minecraft/entity/EntityLiving"}, 0, null));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 6));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WORLD, "func_72838_d",
                "(Lnet/minecraft/entity/Entity;)Z", false));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(afterSpawn);
        method.instructions.add(new FrameNode(Opcodes.F_FULL, 7,
                new Object[]{ENTITY_PARASITE, Opcodes.INTEGER, "java/lang/String", Opcodes.INTEGER, WORLD, "java/lang/String", "net/minecraft/entity/EntityLiving"}, 0, null));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, SRP_CONFIG_SYSTEMS, "useEvolution", "Z"));
        method.instructions.add(new JumpInsnNode(Opcodes.IFNE, evolve));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(evolve);
        method.instructions.add(new FrameNode(Opcodes.F_FULL, 7,
                new Object[]{ENTITY_PARASITE, Opcodes.INTEGER, "java/lang/String", Opcodes.INTEGER, WORLD, "java/lang/String", "net/minecraft/entity/EntityLiving"}, 0, null));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, WORLD, "field_73011_w", "L" + WORLD_PROVIDER + ";"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WORLD_PROVIDER, "getDimension", "()I", false));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 7));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 7));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, SRP_SAVE_DATA, "get", NEW_SAVE_DATA_GET_DESC, false));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 7));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, SRP_CONFIG_SYSTEMS, "valueMerge", "I"));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SRP_SAVE_DATA, "setTotalKills", NEW_SET_TOTAL_KILLS_DESC, false));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 7;
        method.maxLocals = 8;
        return method;
    }

    private static MethodNode legacySpreadBiomeMethod() {
        String desc = "(L" + WORLD + ";L" + BLOCK_POS + ";I)V";
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "SpreadBiome", desc, null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, SRP_EVENT_WORLD,
                "canBiomeStillExistType", "(L" + WORLD + ";L" + BLOCK_POS + ";Z)I", false));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BLOCK_PARASITE_SPREADING,
                "SpreadBiome", "(L" + WORLD + ";L" + BLOCK_POS + ";II)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 6;
        method.maxLocals = 3;
        return method;
    }

    private static MethodNode legacyKillBiomeMethod() {
        String desc = "(L" + WORLD + ";L" + BLOCK_POS + ";I)V";
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "killBiome", desc, null, null);
        LabelNode xLoop = new LabelNode();
        LabelNode zLoop = new LabelNode();
        LabelNode nextZ = new LabelNode();
        LabelNode endZ = new LabelNode();
        LabelNode end = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCK_POS, "func_177958_n", "()I", false));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.ISUB));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 3));
        method.instructions.add(xLoop);
        method.instructions.add(new FrameNode(Opcodes.F_APPEND, 1, new Object[]{Opcodes.INTEGER}, 0, null));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCK_POS, "func_177958_n", "()I", false));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPGT, end));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCK_POS, "func_177952_p", "()I", false));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.ISUB));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 4));
        method.instructions.add(zLoop);
        method.instructions.add(new FrameNode(Opcodes.F_APPEND, 1, new Object[]{Opcodes.INTEGER}, 0, null));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCK_POS, "func_177952_p", "()I", false));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPGT, endZ));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, BLOCK_POS));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCK_POS, "func_177956_o", "()I", false));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, BLOCK_POS, "<init>", "(III)V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 5));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 5));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WORLD, "func_180494_b", "(L" + BLOCK_POS + ";)L" + BIOME + ";", false));
        method.instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, BIOME_PARASITE_BASE));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, nextZ));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 5));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WORLD, "func_175726_f", "(L" + BLOCK_POS + ";)L" + CHUNK + ";", false));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNK, "func_76605_m", "()[B", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 5));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCK_POS, "func_177952_p", "()I", false));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 15));
        method.instructions.add(new InsnNode(Opcodes.IAND));
        method.instructions.add(new InsnNode(Opcodes.ICONST_4));
        method.instructions.add(new InsnNode(Opcodes.ISHL));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 5));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCK_POS, "func_177958_n", "()I", false));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 15));
        method.instructions.add(new InsnNode(Opcodes.IAND));
        method.instructions.add(new InsnNode(Opcodes.IOR));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, BIOMES, "field_76772_c", "L" + BIOME + ";"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BIOME, "func_185362_a", "(L" + BIOME + ";)I", false));
        method.instructions.add(new InsnNode(Opcodes.I2B));
        method.instructions.add(new InsnNode(Opcodes.BASTORE));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 5));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCK_POS, "func_177956_o", "()I", false));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, WORLD, "field_73011_w", "L" + WORLD_PROVIDER + ";"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WORLD_PROVIDER, "getDimension", "()I", false));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BIOME_UPDATE_QUEUE, "enqueue", BIOME_UPDATE_ENQUEUE_DESC, false));
        method.instructions.add(nextZ);
        method.instructions.add(new FrameNode(Opcodes.F_APPEND, 1, new Object[]{BLOCK_POS}, 0, null));
        method.instructions.add(new IincInsnNode(4, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, zLoop));
        method.instructions.add(endZ);
        method.instructions.add(new FrameNode(Opcodes.F_CHOP, 1, null, 0, null));
        method.instructions.add(new IincInsnNode(3, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, xLoop));
        method.instructions.add(end);
        method.instructions.add(new FrameNode(Opcodes.F_CHOP, 1, null, 0, null));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 6;
        method.maxStack = 7;
        return method;
    }

    private static int addLegacySpawnGetter(Map<String, byte[]> classes) {
        byte[] bytes = classes.get(SRP_SPAWNING);
        if (bytes == null) return 0;
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        if (!node.name.equals(SRP_SPAWNING) || !hasCurrentPhaseSpawnLists(ClassInfo.of(node))
                || findMethod(node, "getSpawns", "(B)Ljava/util/List;") != null) return 0;
        MethodNode getter = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "getSpawns", "(B)Ljava/util/List;", null, null);
        LabelNode defaultCase = new LabelNode();
        LabelNode[] cases = new LabelNode[PHASE_LIST_FIELDS.length];
        for (int index = 0; index < cases.length; index++) cases[index] = new LabelNode();
        getter.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        getter.instructions.add(new org.objectweb.asm.tree.TableSwitchInsnNode(0, cases.length - 1, defaultCase, cases));
        for (int index = 0; index < cases.length; index++) {
            getter.instructions.add(cases[index]);
            getter.instructions.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            getter.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, SRP_SPAWNING, PHASE_LIST_FIELDS[index], SPAWN_LIST_DESC));
            getter.instructions.add(new InsnNode(Opcodes.ARETURN));
        }
        getter.instructions.add(defaultCase);
        getter.instructions.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        getter.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        getter.instructions.add(new InsnNode(Opcodes.ARETURN));
        getter.maxStack = 1;
        getter.maxLocals = 1;
        node.methods.add(getter);
        classes.put(SRP_SPAWNING, writeClass(node));
        return 1;
    }

    private static boolean hasCurrentBeckonSpawnApi(ClassInfo info, LegacyVersion version) {
        if (info == null) return false;
        String rawDesc = "(L" + WORLD + ";L" + BLOCK_POS + ";Ljava/util/Random;)V";
        String linkedDesc = "(L" + version.linkPrefix() + WORLD + ";L" + version.linkPrefix() + BLOCK_POS + ";Ljava/util/Random;)V";
        return hasStaticMethod(info, "spawnGenFeatureInfested", rawDesc, linkedDesc)
                && hasStaticMethod(info, "spawnGenRoofInfested", rawDesc, linkedDesc);
    }

    private static boolean hasCurrentSpawnListApi(ClassInfo info, LegacyVersion version) {
        if (info == null || hasStaticMethod(info, "spawnM", OLD_SPAWN_M_DESC,
                "(L" + version.linkPrefix() + ENTITY_PARASITE + ";[Ljava/lang/String;IZLjava/lang/String;)V")) return false;
        String rawDesc = "(Lnet/minecraft/entity/Entity;[Ljava/lang/String;Lnet/minecraft/entity/EntityLivingBase;)V";
        String linkedDesc = "(L" + version.linkPrefix() + "net/minecraft/entity/Entity;[Ljava/lang/String;L"
                + version.linkPrefix() + "net/minecraft/entity/EntityLivingBase;)V";
        return hasStaticMethod(info, "spawnFromList", rawDesc, linkedDesc);
    }

    private static boolean hasStaticMethod(ClassInfo info, String name, String... descriptors) {
        if (info == null) return false;
        for (String descriptor : descriptors) {
            Integer access = info.methods.get(name + descriptor);
            if (access != null && (access & Opcodes.ACC_STATIC) != 0) return true;
        }
        return false;
    }

    private static boolean hasCurrentBeckonCounterApi(ClassInfo info, LegacyVersion version) {
        if (!hasCurrentBeckonSpawnApi(info, version)) return false;
        Integer access = info.fields.get(BLOCK_INFESTED_COUNT + ":I");
        return access != null && (access & Opcodes.ACC_STATIC) != 0;
    }

    private static boolean hasCurrentParasiteBlockCounterApi(ClassInfo info) {
        if (info == null) return false;
        Integer access = info.fields.get(BLOCK_PARASITE_COUNT + ":I");
        return access != null && (access & Opcodes.ACC_STATIC) != 0;
    }

    private static int remapInfestedCounterFields(ClassNode node) {
        int changes = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof FieldInsnNode field)
                        || (field.getOpcode() != Opcodes.GETSTATIC && field.getOpcode() != Opcodes.PUTSTATIC)
                        || !field.owner.equals(SRP_EVENT_WORLD) || !field.name.equals(BLOCK_INFESTED_COUNT)
                        || !field.desc.equals("I")) continue;
                field.owner = BECKON_INFESTATION;
                changes++;
            }
        }
        return changes;
    }

    private static int remapParasiteCounterFields(ClassNode node) {
        int changes = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof FieldInsnNode field)
                        || (field.getOpcode() != Opcodes.GETSTATIC && field.getOpcode() != Opcodes.PUTSTATIC)
                        || !field.owner.equals(SRP_EVENT_WORLD) || !field.name.equals(BLOCK_PARASITE_COUNT)
                        || !field.desc.equals("I")) continue;
                field.owner = BLOCK_PARASITE_SPREADING;
                changes++;
            }
        }
        return changes;
    }

    private static boolean hasCurrentDirectionApi(ClassInfo info, LegacyVersion version) {
        if (info == null) return false;
        String rawDesc = "(L" + BLOCK_POS + ";I)L" + BLOCK_POS + ";";
        String linkedBlockPos = "L" + version.linkPrefix() + BLOCK_POS + ";";
        String linkedDesc = "(" + linkedBlockPos + "I)" + linkedBlockPos;
        return hasStaticMethod(info, "directionToSpread", rawDesc, linkedDesc);
    }

    private static int remapDirectionCalls(ClassNode node) {
        String desc = "(L" + BLOCK_POS + ";I)L" + BLOCK_POS + ";";
        int changes = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESTATIC
                        || !call.owner.equals(SRP_EVENT_WORLD) || !call.name.equals("directionToSpread")
                        || !call.desc.equals(desc)) continue;
                call.owner = BLOCK_PARASITE_SPREADING;
                changes++;
            }
        }
        return changes;
    }

    private static int remapMovedInfestationCalls(ClassNode node) {
        String desc = "(L" + WORLD + ";L" + BLOCK_POS + ";Ljava/util/Random;)V";
        int changes = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESTATIC
                        || !call.owner.equals(SRP_EVENT_WORLD) || !call.desc.equals(desc)) continue;
                if (!call.name.equals("spawnGenFeatureInfested") && !call.name.equals("spawnGenRoofInfested")) continue;
                call.owner = BECKON_INFESTATION;
                changes++;
            }
        }
        return changes;
    }

    private static int addLegacySpawnMMethod(Map<String, byte[]> classes) {
        byte[] bytes = classes.get(SRP_EVENT_ENTITY);
        if (bytes == null) return 0;
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        if (!node.name.equals(SRP_EVENT_ENTITY) || findMethod(node, "spawnM", OLD_SPAWN_M_DESC) != null
                || !hasCurrentSpawnListApi(ClassInfo.of(node), LegacyVersion.V1_12_2)) return 0;
        node.methods.add(legacySpawnMMethod());
        classes.put(SRP_EVENT_ENTITY, writeClass(node));
        return 1;
    }

    private static MethodNode legacySpawnMMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "spawnM", OLD_SPAWN_M_DESC, null, null);
        LabelNode serverSide = new LabelNode();
        LabelNode loop = new LabelNode();
        LabelNode innerLoop = new LabelNode();
        LabelNode nextEntry = new LabelNode();
        LabelNode entityReady = new LabelNode();
        LabelNode doSpawn = new LabelNode();
        LabelNode innerEnd = new LabelNode();
        LabelNode end = new LabelNode();
        Object[] locals = {
                ENTITY_PARASITE, "[Ljava/lang/String;", Opcodes.INTEGER, Opcodes.INTEGER, "java/lang/String",
                "[Ljava/lang/String;", "java/util/Random", Opcodes.DOUBLE, Opcodes.DOUBLE, Opcodes.DOUBLE,
                Opcodes.INTEGER, Opcodes.INTEGER, Opcodes.INTEGER, Opcodes.INTEGER, "net/minecraft/util/ResourceLocation",
                Opcodes.INTEGER, "net/minecraft/entity/EntityLiving"
        };

        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_PARASITE, "field_70170_p", "L" + WORLD + ";"));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, WORLD, "field_72995_K", "Z"));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, serverSide));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(serverSide);
        method.instructions.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.add(new InsnNode(Opcodes.ICONST_3));
        method.instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/String"));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 5));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, "java/util/Random"));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/util/Random", "<init>", "()V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 6));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_PARASITE, "field_70165_t", "D"));
        method.instructions.add(new VarInsnNode(Opcodes.DSTORE, 7));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_PARASITE, "field_70163_u", "D"));
        method.instructions.add(new VarInsnNode(Opcodes.DSTORE, 9));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_PARASITE, "field_70161_v", "D"));
        method.instructions.add(new VarInsnNode(Opcodes.DSTORE, 11));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 13));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 14));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 15));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 16));
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 17));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 18));
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 19));
        method.instructions.add(loop);
        method.instructions.add(new FrameNode(Opcodes.F_FULL, locals.length, locals, 0, null));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 13));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.ARRAYLENGTH));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPGE, end));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 13));
        method.instructions.add(new InsnNode(Opcodes.AALOAD));
        method.instructions.add(new JumpInsnNode(Opcodes.IFNULL, nextEntry));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 13));
        method.instructions.add(new InsnNode(Opcodes.AALOAD));
        method.instructions.add(new org.objectweb.asm.tree.LdcInsnNode(";"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "split",
                "(Ljava/lang/String;)[Ljava/lang/String;", false));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 5));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 5));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 6));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, SRP_SPAWN_HELPER, "rollLegacySpawnCount",
                SPAWN_COUNT_DESC, false));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 16));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, "net/minecraft/util/ResourceLocation"));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 5));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.AALOAD));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "net/minecraft/util/ResourceLocation", "<init>",
                "(Ljava/lang/String;)V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 17));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 18));
        method.instructions.add(innerLoop);
        method.instructions.add(new FrameNode(Opcodes.F_FULL, locals.length, locals, 0, null));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 18));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 16));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPGE, innerEnd));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 17));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_PARASITE, "field_70170_p", "L" + WORLD + ";"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/entity/EntityList", "func_188429_b",
                "(Lnet/minecraft/util/ResourceLocation;L" + WORLD + ";)Lnet/minecraft/entity/Entity;", false));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/entity/EntityLiving"));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 19));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 19));
        method.instructions.add(new JumpInsnNode(Opcodes.IFNONNULL, entityReady));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(entityReady);
        method.instructions.add(new FrameNode(Opcodes.F_FULL, locals.length, locals, 0, null));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 19));
        method.instructions.add(new VarInsnNode(Opcodes.DLOAD, 7));
        method.instructions.add(new VarInsnNode(Opcodes.DLOAD, 9));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_PARASITE, "field_70131_O", "F"));
        method.instructions.add(new InsnNode(Opcodes.FCONST_2));
        method.instructions.add(new InsnNode(Opcodes.FDIV));
        method.instructions.add(new InsnNode(Opcodes.F2D));
        method.instructions.add(new InsnNode(Opcodes.DADD));
        method.instructions.add(new org.objectweb.asm.tree.LdcInsnNode(0.5D));
        method.instructions.add(new InsnNode(Opcodes.DADD));
        method.instructions.add(new VarInsnNode(Opcodes.DLOAD, 11));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_PARASITE, "field_70177_z", "F"));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_PARASITE, "field_70125_A", "F"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/EntityLiving", "func_70012_b",
                "(DDDFF)V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 19));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_PARASITE, "field_70170_p", "L" + WORLD + ";"));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, BLOCK_POS));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 19));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, BLOCK_POS, "<init>", "(Lnet/minecraft/entity/Entity;)V", false));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WORLD, "func_175649_E",
                "(L" + BLOCK_POS + ";)Lnet/minecraft/world/DifficultyInstance;", false));
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/EntityLiving", "func_180482_a",
                "(Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/entity/IEntityLivingData;)Lnet/minecraft/entity/IEntityLivingData;", false));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 19));
        method.instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, ENTITY_PARASITE));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, doSpawn));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, doSpawn));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 19));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, ENTITY_PARASITE));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, SRP_CONFIG, "convertedDespawn", "Z"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY_PARASITE, "cannotDespawn", "(Z)V", false));
        method.instructions.add(doSpawn);
        method.instructions.add(new FrameNode(Opcodes.F_FULL, locals.length, locals, 0, null));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_PARASITE, "field_70170_p", "L" + WORLD + ";"));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 19));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WORLD, "func_72838_d", "(Lnet/minecraft/entity/Entity;)Z", false));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new IincInsnNode(18, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, innerLoop));
        method.instructions.add(innerEnd);
        method.instructions.add(new FrameNode(Opcodes.F_FULL, locals.length, locals, 0, null));
        method.instructions.add(new IincInsnNode(13, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, loop));
        method.instructions.add(nextEntry);
        method.instructions.add(new FrameNode(Opcodes.F_FULL, locals.length, locals, 0, null));
        method.instructions.add(new IincInsnNode(13, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, loop));
        method.instructions.add(end);
        method.instructions.add(new FrameNode(Opcodes.F_FULL, locals.length, locals, 0, null));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 9;
        method.maxLocals = 20;
        return method;
    }

    private static boolean hasCurrentMergeApi(Map<String, ClassInfo> peers, LegacyVersion version) {
        ClassInfo eventEntity = peers.get(SRP_EVENT_ENTITY);
        ClassInfo config = peers.get(SRP_CONFIG);
        ClassInfo systems = peers.get(SRP_CONFIG_SYSTEMS);
        if (eventEntity == null || config == null || systems == null
                || !hasStaticField(config, "convertedDespawn", "Z")
                || !hasStaticField(systems, "mergeRandom", "Z")
                || !hasStaticField(systems, "mergeMobTable", "[Ljava/lang/String;")
                || !hasStaticField(systems, "useEvolution", "Z")
                || !hasStaticField(systems, "valueMerge", "I")) return false;
        return hasCurrentSaveDataApi(peers.get(SRP_SAVE_DATA), version);
    }

    private static boolean hasStaticField(ClassInfo info, String name, String descriptor) {
        Integer access = info.fields.get(name + ":" + descriptor);
        return access != null && (access & Opcodes.ACC_STATIC) != 0;
    }

    private static boolean hasCurrentSaveDataApi(ClassInfo info, LegacyVersion version) {
        if (info == null) return false;
        String linkedWorld = "L" + version.linkPrefix() + WORLD + ";";
        String getDesc = "(" + linkedWorld + "I)L" + SRP_SAVE_DATA + ";";
        String setTotalKillsDesc = "(IIZ" + linkedWorld + "ZI)Z";
        Integer getter = info.methods.get("get" + getDesc);
        Integer setter = info.methods.get("setTotalKills" + setTotalKillsDesc);
        return getter != null && (getter & Opcodes.ACC_STATIC) != 0
                && setter != null && (setter & Opcodes.ACC_STATIC) == 0;
    }

    private static int adaptSaveDataCalls(ClassNode node) {
        int changes = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals(SRP_SAVE_DATA)) continue;
                if (call.getOpcode() == Opcodes.INVOKESTATIC && call.name.equals("get")
                        && call.desc.equals(OLD_SAVE_DATA_GET_DESC)) {
                    org.objectweb.asm.tree.InsnList dimension = new org.objectweb.asm.tree.InsnList();
                    dimension.add(new InsnNode(Opcodes.DUP));
                    dimension.add(new FieldInsnNode(Opcodes.GETFIELD, WORLD, "field_73011_w", "L" + WORLD_PROVIDER + ";"));
                    dimension.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WORLD_PROVIDER, "getDimension", "()I", false));
                    method.instructions.insertBefore(call, dimension);
                    call.desc = NEW_SAVE_DATA_GET_DESC;
                    method.maxStack++;
                    changes++;
                } else if (call.getOpcode() == Opcodes.INVOKEVIRTUAL && call.name.equals("setTotalKills")
                        && call.desc.equals(OLD_SET_TOTAL_KILLS_DESC)) {
                    method.instructions.insertBefore(call, new InsnNode(Opcodes.ICONST_0));
                    call.desc = NEW_SET_TOTAL_KILLS_DESC;
                    method.maxStack++;
                    changes++;
                }
            }
        }
        return changes;
    }

    private static boolean hasCurrentCruxAApi(Map<String, ClassInfo> peers) {
        if (peers.containsKey(SRP_CRUX)) return false;
        ClassInfo info = peers.get(SRP_CRUX_A);
        if (info == null || !ENTITY_P_CRUDE.equals(info.superName) || !info.interfaces.contains(ENTITY_CUSTOM_ATTACK)) return false;
        boolean worldConstructor = info.methods.keySet().stream().anyMatch(method -> method.startsWith("<init>(L")
                && method.endsWith("/net/minecraft/world/World;)V"));
        return worldConstructor && info.hasMethod("getBack", "()Z") && info.hasMethod("getAttackTimerM", "()F")
                && info.hasMethod("getAttackTimerR", "()F") && info.hasMethod("getFinished", "(B)Z")
                && info.hasMethod("setFinished", "(BZ)V") && info.hasMethod("doSpecialSkill", "(B)V");
    }

    private static int remapRemovedCruxReferences(Map<String, byte[]> classes) {
        int changes = 0;
        for (var entry : classes.entrySet()) {
            if (entry.getKey().equals(SRP_CRUX)) continue;
            int[] references = {0};
            Remapper remapper = new Remapper() {
                @Override
                public String map(String internalName) {
                    if (!internalName.equals(SRP_CRUX)) return internalName;
                    references[0]++;
                    return SRP_CRUX_A;
                }
            };
            ClassNode rewritten = new ClassNode(Opcodes.ASM9);
            new ClassReader(entry.getValue()).accept(new ClassRemapper(rewritten, remapper), 0);
            if (references[0] == 0) continue;
            entry.setValue(writeClass(rewritten));
            changes += references[0];
        }
        return changes;
    }

    private static int remapSrpExtraGoreTypeLookup(Map<String, byte[]> classes) {
        byte[] bytes = classes.get(SRP_EXTRA_REMAINS_CANISTER);
        if (bytes == null) return 0;
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        String descriptor = "(L" + WORLD + ";L" + BLOCK_POS + ";)Ljava/lang/String;";
        MethodNode method = findMethod(node, "getTypeByBlock", descriptor);
        if (!node.name.equals(SRP_EXTRA_REMAINS_CANISTER) || method == null
                || hasCall(method, SRP_GORE_FAMILY_HELPER, "getTypeByBlock", SRP_GORE_FAMILY_HELPER_DESC)) return 0;

        method.instructions.clear();
        method.tryCatchBlocks.clear();
        method.localVariables = null;
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WORLD, "func_180495_p",
                "(L" + BLOCK_POS + ";)L" + I_BLOCK_STATE + ";", false));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, I_BLOCK_STATE,
                "func_177230_c", "()Lnet/minecraft/block/Block;", true));
        for (String family : FAMILIES) {
            method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, SRP_BLOCKS, blockFieldForFamily(family), GORE_DESC));
        }
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, SRP_GORE_FAMILY_HELPER,
                "getTypeByBlock", SRP_GORE_FAMILY_HELPER_DESC, false));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxStack = 5;
        method.maxLocals = 2;
        classes.put(SRP_EXTRA_REMAINS_CANISTER, writeClass(node));
        return 1;
    }

    private static boolean hasSplitGoreApi(ClassInfo blocks, ClassInfo goreEnum) {
        if (blocks == null || goreEnum == null) return false;
        for (String family : FAMILIES) {
            if (!blocks.hasField(blockFieldForFamily(family), GORE_DESC)) return false;
        }
        for (String size : SIZES) if (!goreEnum.hasField(size, ENUM_DESC)) return false;
        return !blocks.hasField(OLD_GORE, GORE_DESC);
    }

    private static int adaptGoreReferences(ClassNode node, ClassInfo blocks, ClassInfo goreEnum) {
        int changes = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETSTATIC
                        || !field.owner.equals(SRP_BLOCKS) || !field.name.equals(OLD_GORE) || !GORE_DESC.equals(field.desc)) continue;
                FieldInsnNode variant = pairedVariant(field);
                if (variant == null) continue;
                String blockField = blockField(variant.name);
                String size = size(variant.name);
                if (blockField == null || size == null || !blocks.hasField(blockField, GORE_DESC)
                        || !goreEnum.hasField(size, ENUM_DESC)) continue;
                field.name = blockField;
                variant.name = size;
                changes += 2;
            }
        }
        return changes;
    }

    /** Returns the old full enum name consumed by the state expression immediately after a Gore read. */
    private static FieldInsnNode pairedVariant(FieldInsnNode gore) {
        AbstractInsnNode state = nextCode(gore.getNext());
        if (!(state instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKEVIRTUAL
                || !call.owner.equals(BLOCK_GORE) || !STATE_DESC.equals(returnType(call.desc))) return null;
        AbstractInsnNode property = nextCode(state.getNext());
        if (!(property instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETSTATIC
                || !field.owner.equals(BLOCK_GORE) || !field.name.equals("VARIANT") || !PROPERTY_ENUM_DESC.equals(field.desc)) return null;
        AbstractInsnNode variant = nextCode(property.getNext());
        if (!(variant instanceof FieldInsnNode enumField) || enumField.getOpcode() != Opcodes.GETSTATIC
                || !enumField.owner.equals(GORE_ENUM) || !ENUM_DESC.equals(enumField.desc)) return null;
        return enumField;
    }

    private static String returnType(String descriptor) {
        int close = descriptor.lastIndexOf(')');
        return close < 0 ? "" : descriptor.substring(close + 1);
    }

    private static String blockField(String variant) {
        if (variant == null) return null;
        for (String family : FAMILIES) {
            if (variant.startsWith(family + "_")) {
                String size = size(variant);
                if (size != null) return blockFieldForFamily(family);
            }
        }
        return null;
    }

    private static String blockFieldForFamily(String family) {
        return switch (family) {
            case "SIM" -> "goreSim";
            case "PRI" -> "gorePri";
            case "ADA" -> "goreAda";
            case "PURE" -> "gorePur";
            default -> null;
        };
    }

    private static String size(String variant) {
        if (variant == null) return null;
        for (String size : SIZES) if (variant.equals(size) || variant.endsWith("_" + size)) return size;
        return null;
    }

    private static AbstractInsnNode nextCode(AbstractInsnNode instruction) {
        while (instruction != null && (instruction instanceof LabelNode || instruction instanceof LineNumberNode
                || instruction instanceof FrameNode)) instruction = instruction.getNext();
        return instruction;
    }
}
