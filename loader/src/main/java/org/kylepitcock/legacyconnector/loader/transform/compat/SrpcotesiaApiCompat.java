package org.kylepitcock.legacyconnector.loader.transform.compat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.kylepitcock.legacyconnector.loader.LegacyJar;
import org.kylepitcock.legacyconnector.loader.LegacyVersion;
import org.kylepitcock.legacyconnector.loader.shim.ClassInfo;
import org.kylepitcock.legacyconnector.loader.transform.LegacyRemapper;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.LocalVariableAnnotationNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureWriter;

/**
 * MOD-SPECIFIC FIX (SRP Cotesia), kept isolated and conditional on the peer API shape. SRP 1.10.9 replaced
 * {@code BiomeParasite} with the common parent {@code BiomeParasiteBase}; Cotesia uses the old type only to
 * identify parasite biomes. Its salt decorator also needs the new base constructor, whose argument it never uses.
 * Cotesia's saved-data calls are adapted separately when the peer SRP jar exposes the newer method signatures.
 */
public final class SrpcotesiaApiCompat {
    private static final String MOD_ID = "srpcotesia";
    private static final String COTESIA_SALTINE_TEXTURE = "assets/srpcotesia/textures/blocks/saltine.png";
    private static final String COTESIA_SALTINE_INACTIVE_TEXTURE = "assets/srpcotesia/textures/blocks/saltine_inactive.png";
    private static final String COTESIA_INF_CRAFTING_TABLE_MODEL = "assets/srpcotesia/models/block/inf_crafting_table.json";
    private static final String COTESIA_GREP_ANIMATION = "assets/srpcotesia/animations/grep.animation.json";
    private static final Map<String, String> COTESIA_ANIMATION_PATHS = Map.of(
            "animations/factory.anim.json", "animations/factory.animation.json",
            "animations/hi_enchanting_table.anim.json", "animations/hi_enchanting_table.animation.json",
            "animations/osmosis.anim.json", "animations/osmosis.animation.json",
            "assets/srpcotesia/animations/factory.anim.json", "assets/srpcotesia/animations/factory.animation.json",
            "assets/srpcotesia/animations/hi_enchanting_table.anim.json", "assets/srpcotesia/animations/hi_enchanting_table.animation.json",
            "assets/srpcotesia/animations/osmosis.anim.json", "assets/srpcotesia/animations/osmosis.animation.json",
            "srpcotesia:animations/factory.anim.json", "srpcotesia:animations/factory.animation.json",
            "srpcotesia:animations/hi_enchanting_table.anim.json", "srpcotesia:animations/hi_enchanting_table.animation.json",
            "srpcotesia:animations/osmosis.anim.json", "srpcotesia:animations/osmosis.animation.json");
    private static final String LEGACY_SPEED_QUERY = "speed";
    private static final String MODERN_SPEED_QUERY = "query.ground_speed";
    private static final String LEGACY_YAW_QUERY = "yaw";
    private static final String MODERN_YAW_QUERY = "query.body_y_rotation";
    private static final String LEGACY_PITCH_QUERY = "pitch";
    private static final String MODERN_PITCH_QUERY = "query.head_x_rotation";
    private static final String LEGACY_EYE_Y_QUERY = "eye_y";
    private static final String LEGACY_EYE_Z_QUERY = "eye_z";
    private static final String MODERN_ZERO_QUERY = "0";
    private static final String SRP_TREE_DOWN_TEXTURE = "assets/srparasites/textures/blocks/parasitetrunk_tree_down.png";
    private static final String SRP_TREE_END_TEXTURE = "srparasites:blocks/parasitetrunk_tree";
    private static final String SRP_BLOCKS = "com/dhanantry/scapeandrunparasites/init/SRPBlocks";
    private static final String BLOCK_GORE = "com/dhanantry/scapeandrunparasites/block/BlockGore";
    private static final String GORE_ENUM = BLOCK_GORE + "$EnumType";
    private static final String GORE_DESC = "L" + BLOCK_GORE + ";";
    private static final String GORE_ENUM_DESC = "L" + GORE_ENUM + ";";
    private static final String PARASITE_PLAYER = "com/srpcotesia/capability/ParasitePlayer";
    private static final String GORE_BLOCK_HELPER = "mcc$legacyGoreBlock";
    private static final String[] GORE_FAMILIES = {"SIM", "PRI", "ADA", "PURE"};
    private static final String OLD_BIOME = "com/dhanantry/scapeandrunparasites/world/biome/BiomeParasite";
    private static final String BIOME_BASE = "com/dhanantry/scapeandrunparasites/world/biome/BiomeParasiteBase";
    private static final String BIOME_DECORATOR = "com/dhanantry/scapeandrunparasites/world/biome/BiomeParasiteDecorator";
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
    private static final String ENTITY_ELVIA = "com/dhanantry/scapeandrunparasites/entity/monster/pure/preeminent/EntityElvia";
    private static final String I_PARASITE = "com/srpcotesia/entity/parasites/IParasite";
    private static final String POTION_ANTIBODIES = "com/srpcotesia/potion/PotionAntibodies";
    private static final String PARASITE_BASE_MIXIN = "com/srpcotesia/mixin/entity/EntityParasiteBaseMixin";
    private static final String APPLY_BONUSES_HANDLER = "srpcotesia$applyBonuses";
    private static final String BLOCK_MINI_DAMAGE_HANDLER = "srpcotesia$blockMiniDamage";
    private static final String MIN_DAMAGE_HANDLER = "srpcotesia$minDamageMixin";
    private static final String CALLBACK_INFO_DESC = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
    private static final String CALLBACK_INFO_RETURNABLE_DESC = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;";
    private static final String LOCAL_FLOAT_REF_DESC = "Lcom/llamalad7/mixinextras/sugar/ref/LocalFloatRef;";
    private static final String LOCAL_ANNOTATION_DESC = "Lcom/llamalad7/mixinextras/sugar/Local;";
    private static final String ENTITY_LIVING_BASE = "net/minecraft/entity/EntityLivingBase";
    private static final String MINIMUM_DAMAGE_DESC = "(L" + ENTITY_LIVING_BASE + ";F)Z";
    private static final String OLD_APPLY_BONUSES_HANDLER_DESC = "(IIB" + CALLBACK_INFO_DESC + ")V";
    private static final String NEW_APPLY_BONUSES_HANDLER_DESC = "(" + CALLBACK_INFO_DESC + ")V";
    private static final String OLD_BLOCK_MINI_DAMAGE_HANDLER_DESC = "(Lnet/minecraft/entity/EntityLivingBase;" + CALLBACK_INFO_RETURNABLE_DESC + LOCAL_FLOAT_REF_DESC + ")V";
    private static final String OLD_MIN_DAMAGE_HANDLER_DESC = "(Lnet/minecraft/entity/EntityLivingBase;" + CALLBACK_INFO_RETURNABLE_DESC + ")V";
    private static final String WORLD = "net/minecraft/world/World";
    private static final String APPLY_BONUSES_DESC = "(L" + SRP_SAVE_DATA + ";L" + WORLD + ";)V";
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
    private static final String SRP_EFFECT_BASE = "com/dhanantry/scapeandrunparasites/potion/SRPEffectBase";
    private static final String OPERATION = "com/llamalad7/mixinextras/injector/wrapoperation/Operation";
    private static final String WRAP_METHOD = "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;";
    private static final String NEAREST_TARGET_HANDLER = "srpcotesia$restrictedSight";
    private static final String NEAREST_TARGET_METHOD_DESC = "(L" + ENTITY_LIVING_BASE + ";Z)Z";
    private static final String NEAREST_TARGET_WRAP_DESC = "(L" + ENTITY_LIVING_BASE + ";ZL" + OPERATION + ";)Z";
    private static final String CALLBACK_INFO = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private static final String OLD_MOVED_KILLS_HANDLER_DESC = "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";ZL" + OPERATION + ";L" + WORLD + ";Lnet/minecraft/util/math/BlockPos;Ljava/util/Random;IZ)Z";
    private static final String NEW_MOVED_KILLS_HANDLER_DESC = "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";ZIL" + OPERATION + ";L" + WORLD + ";Lnet/minecraft/util/math/BlockPos;Ljava/util/Random;IZ)Z";
    private static final String OLD_MOVED_KILLS_HANDLER_SIGNATURE = "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";ZL" + OPERATION
            + "<Ljava/lang/Boolean;>;L" + WORLD + ";Lnet/minecraft/util/math/BlockPos;Ljava/util/Random;IZ)Z";
    private static final String NEW_MOVED_KILLS_HANDLER_SIGNATURE = "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";ZIL" + OPERATION
            + "<Ljava/lang/Boolean;>;L" + WORLD + ";Lnet/minecraft/util/math/BlockPos;Ljava/util/Random;IZ)Z";
    private static final String PARASITE_EVENT_WORLD = "com/dhanantry/scapeandrunparasites/util/ParasiteEventWorld";
    private static final String PARASITE_EVENT_WORLD_MIXIN = "com/srpcotesia/mixin/ParasiteEventWorldMixin";
    private static final String BLOCK_PARASITE_SPREADING = "com/dhanantry/scapeandrunparasites/block/BlockParasiteSpreading";
    private static final String BECKON_BLOCK_INFESTATION = "com/dhanantry/scapeandrunparasites/util/convert/BeckonBlockInfestation";
    private static final String SPREADING_MIXIN = "com/srpcotesia/mixin/compat/LegacyParasiteSpreadingMixin";
    private static final String BECKON_INFESTATION_MIXIN = "com/srpcotesia/mixin/compat/LegacyBeckonInfestationMixin";
    private static final String ENTITY_AI_BLOCK_INFEST = "com/dhanantry/scapeandrunparasites/entity/ai/EntityAIBlockInfest";
    private static final String ENTITY_AI_BLOCK_INFEST_MIXIN = "com/srpcotesia/mixin/entity/ai/EntityAIBlockInfestMixin";
    private static final String SUMMON_MIXIN = "com/srpcotesia/mixin/compat/LegacyParasiteSummonMixin";
    private static final String ENTITY_LIST = "net/minecraft/entity/EntityList";
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
    private static final String CAN_INFEST_DESC = "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Ljava/util/Random;IZ)V";
    private static final String SPAWN_BECKON_DESC = "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;I)V";
    private static final String ENTITY_VENKROL = "com/dhanantry/scapeandrunparasites/entity/monster/deterrent/nexus/EntityVenkrol";
    private static final String OLD_VENKROL_INJECT_DESC = "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Ljava/util/Random;IZL" + CALLBACK_INFO + ";L" + ENTITY_VENKROL + ";)V";
    private static final String NEW_VENKROL_INJECT_DESC = "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;IL" + CALLBACK_INFO + ";L" + ENTITY_VENKROL + ";)V";
    private static final String SPREAD_METHOD_DESC = "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Ljava/util/Random;)V";
    private static final String SRP_EFFECT_MIXIN = "com/srpcotesia/mixin/SRPEffectBaseMixin";
    private static final String PARTICLE_MIXIN = "com/srpcotesia/mixin/compat/LegacySrpParticleMixin";
    private static final String PARTICLE_ENUM = "com/dhanantry/scapeandrunparasites/client/particle/SRPEnumParticle";
    private static final String PARTICLE_PACKET = "com/dhanantry/scapeandrunparasites/network/SRPPacketParticle";
    private static final String PARTICLE_HANDLER = PARTICLE_PACKET + "$Handler";
    private static final String PARTICLE_INJECT_HANDLER = "srpcotesia$spawnParticles";
    private static final String PARTICLE_INJECT_OLD_DESC = "(L" + PARTICLE_ENUM + ";Ljava/util/Random;L" + ENTITY_LIVING_BASE + ";L" + CALLBACK_INFO + ";)V";
    private static final String PARTICLE_INJECT_NEW_DESC = "(L" + PARTICLE_ENUM + ";Ljava/util/Random;L" + PARTICLE_PACKET + ";IIIL" + CALLBACK_INFO + ";)V";
    private static final String PARTICLE_WRAP_OLD_DESC = "(Ljava/util/Random;L" + OPERATION + ";L" + PARTICLE_ENUM + ";Ljava/util/Random;L" + ENTITY_LIVING_BASE + ";)F";
    private static final String PARTICLE_WRAP_NEW_DESC = "(Ljava/util/Random;L" + OPERATION + ";L" + PARTICLE_ENUM + ";Ljava/util/Random;L" + PARTICLE_PACKET + ";III)F";
    private static final String PARTICLE_SPAWN_OLD_TARGET_DESC = "(L" + PARTICLE_ENUM + ";Ljava/util/Random;L" + ENTITY_LIVING_BASE + ";)V";
    private static final String POTION_COTH = "com/dhanantry/scapeandrunparasites/potion/PotionCOTH";
    private static final String POTION_NEEDLER = "com/dhanantry/scapeandrunparasites/potion/PotionNeedler";
    private static final String COTH_MIXIN = "com/srpcotesia/mixin/compat/LegacyCothEffectsMixin";
    private static final String NEEDLER_MIXIN = "com/srpcotesia/mixin/compat/LegacyNeedlerEffectMixin";
    private static final String SOURCE_ID_OPERATION = "com/srpcotesia/compat/SourceIdOperation";
    private static final String MINECRAFT = "net/minecraft/client/Minecraft";
    private static final String WORLD_CLIENT = "net/minecraft/client/multiplayer/WorldClient";
    private static final String ENTITY = "net/minecraft/entity/Entity";
    private static final String ENTITY_PLAYER = "net/minecraft/entity/player/EntityPlayer";
    private static final String ENTITY_PLAYER_SP = "net/minecraft/client/entity/EntityPlayerSP";
    private static final String PARTICLE_INTERACTIONS = "com/srpcotesia/util/ParasiteInteractions";
    private static final String DESPAWN_MIXIN = "com/srpcotesia/mixin/entity/EntityPAdaptedMixin";
    private static final String MERGE_MIXIN = "com/srpcotesia/mixin/ParasiteEventEntityMixin";
    private static final String DESPAWN_HANDLER = "srpcotesia$onDespawnMixin";
    private static final String MERGE_HANDLER = "srpcotesia$merge";
    private static final String COTH_SPAWN_HANDLER = "srpcotesia$effectCOTH$spawnInsider";
    private static final String COTH_KILLS_HANDLER = "srpcotesia$effectCOTH$setTotalKills";
    private static final String INFECT_HANDLER = "srpcotesia$InfectNearby";
    private static final String NEEDLER_HANDLER = "srpcotesia$effectNeedler";
    private static final String COTH_TRANSFORM_DESC = "(Lnet/minecraft/entity/EntityLivingBase;IZZZ)Z";
    private static final String COTH_EXTEND_DESC = "(IILnet/minecraft/entity/EntityLivingBase;IZZZ)V";
    private static final String NEEDLER_EFFECT_DESC = "(Lnet/minecraft/entity/EntityLivingBase;I)V";
    private static final String NEEDLER_HANDLER_DESC = "(Lnet/minecraft/entity/EntityLivingBase;ILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    private static final String INFECT_NEARBY_DESC = "(Lnet/minecraft/entity/EntityLivingBase;I)V";
    private static final String OLD_COTH_SPAWN_DESC = "(Lnet/minecraft/entity/EntityLivingBase;ILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    private static final String NEW_COTH_SPAWN_DESC = "(Lnet/minecraft/entity/EntityLivingBase;IZZZLorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V";
    private static final String OLD_COTH_KILLS_DESC = "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";ZLcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lnet/minecraft/entity/EntityLivingBase;I)Z";
    private static final String NEW_COTH_KILLS_DESC = "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";ZILcom/llamalad7/mixinextras/injector/wrapoperation/Operation;IILnet/minecraft/entity/EntityLivingBase;IZZZ)Z";
    private static final String OLD_MERGE_HANDLER_DESC = "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";ZLcom/llamalad7/mixinextras/injector/wrapoperation/Operation;L" + ENTITY_PARASITE + ";ILjava/lang/String;)Z";
    private static final String NEW_MERGE_HANDLER_DESC = "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";ZILcom/llamalad7/mixinextras/injector/wrapoperation/Operation;L" + ENTITY_PARASITE + ";ILjava/lang/String;)Z";
    private static final String OLD_MERGE_HANDLER_SIGNATURE = "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";ZLcom/llamalad7/mixinextras/injector/wrapoperation/Operation"
            + "<Ljava/lang/Boolean;>;L" + ENTITY_PARASITE + ";ILjava/lang/String;)Z";
    private static final String NEW_MERGE_HANDLER_SIGNATURE = "(L" + SRP_SAVE_DATA + ";IIZL" + WORLD + ";ZILcom/llamalad7/mixinextras/injector/wrapoperation/Operation"
            + "<Ljava/lang/Boolean;>;L" + ENTITY_PARASITE + ";ILjava/lang/String;)Z";
    private static final String INFECT_TARGET = "Lnet/minecraft/entity/EntityLivingBase;addPotionEffect(Lnet/minecraft/potion/PotionEffect;)V";
    private static final String MAIN_MIXIN_CONFIG_PACKAGE = "com.srpcotesia.mixin";
    private static final Gson GSON = new Gson();

    private SrpcotesiaApiCompat() {}

    /** Applies only when the translated 1.12.2 peer set proves the newer SRP APIs are present. */
    public static int apply(LegacyJar jar, Map<String, byte[]> classes, Map<String, ClassInfo> peers, LegacyRemapper remapper) {
        return apply(jar, classes, new java.util.LinkedHashMap<>(), peers, remapper);
    }

    public static int apply(LegacyJar jar, Map<String, byte[]> classes, Map<String, byte[]> resources,
            Map<String, ClassInfo> peers, LegacyRemapper remapper) {
        if (jar.version != LegacyVersion.V1_12_2 || jar.mods.stream().map(mod -> mod.legacyId)
                .noneMatch(id -> id != null && id.equalsIgnoreCase(MOD_ID))) return 0;

        int changes = adaptCotesiaAssetReferences(classes, resources, peers.containsKey(BIOME_BASE));
        ClassInfo biomeBase = peers.get(BIOME_BASE);
        if (biomeBase != null) {
            if (!peers.containsKey(OLD_BIOME)) {
                remapper.override(OLD_BIOME, BIOME_BASE);
                changes++;
            }

            ClassInfo decorator = peers.get(BIOME_DECORATOR);
            if (decorator != null && !decorator.hasMethod("<init>", "()V") && decorator.hasMethod("<init>", BASE_CTOR)) {
                byte[] saltDecorator = classes.get(SALT_DECORATOR);
                if (saltDecorator != null) {
                    byte[] adapted = adaptSaltDecorator(saltDecorator);
                    if (adapted != null) {
                        classes.put(SALT_DECORATOR, adapted);
                        changes++;
                    }
                }
            }
        }
        changes += adaptSaveDataCalls(classes, peers.get(SRP_SAVE_DATA), remapper);
        changes += adaptParasiteBaseHandlers(classes, peers.get(ENTITY_PARASITE), remapper);
        changes += adaptMovedParasiteMethods(classes, resources, peers, remapper);
        changes += adaptMovedSummonMethods(classes, resources, peers, remapper);
        changes += adaptGetFeralCall(classes, peers, remapper);
        changes += adaptBloomGore(classes, peers);
        changes += adaptPotionMixins(classes, resources, peers, remapper);
        changes += adaptRemainingEntityMixins(classes, peers, remapper);
        changes += adaptScaryOrbRename(peers, remapper);
        return changes;
    }

    private static int adaptCotesiaAssetReferences(Map<String, byte[]> classes, Map<String, byte[]> resources,
            boolean currentSrpApi) {
        int changes = adaptCotesiaAnimationPaths(classes);
        for (Map.Entry<String, String> entry : COTESIA_ANIMATION_PATHS.entrySet()) {
            if (!entry.getKey().startsWith("assets/")) continue;
            byte[] animation = resources.remove(entry.getKey());
            if (animation == null) continue;
            resources.putIfAbsent(entry.getValue(), animation);
            changes++;
        }
        byte[] inactiveSaltine = resources.get(COTESIA_SALTINE_INACTIVE_TEXTURE);
        if (inactiveSaltine != null && !resources.containsKey(COTESIA_SALTINE_TEXTURE)) {
            resources.put(COTESIA_SALTINE_TEXTURE, inactiveSaltine);
            changes++;
        }

        byte[] grepAnimation = resources.get(COTESIA_GREP_ANIMATION);
        if (grepAnimation != null) {
            String source = new String(grepAnimation, StandardCharsets.UTF_8);
            String adapted = source
                    .replaceAll("(?<![A-Za-z0-9_])" + LEGACY_SPEED_QUERY + "(?![A-Za-z0-9_])", MODERN_SPEED_QUERY)
                    .replaceAll("(?<![A-Za-z0-9_])" + LEGACY_YAW_QUERY + "(?![A-Za-z0-9_])", MODERN_YAW_QUERY)
                    .replaceAll("(?<![A-Za-z0-9_])" + LEGACY_PITCH_QUERY + "(?![A-Za-z0-9_])", MODERN_PITCH_QUERY)
                    // ModelGrep explicitly sets both legacy eye offsets to zero every frame.
                    .replaceAll("(?<![A-Za-z0-9_])" + LEGACY_EYE_Y_QUERY + "(?![A-Za-z0-9_])", MODERN_ZERO_QUERY)
                    .replaceAll("(?<![A-Za-z0-9_])" + LEGACY_EYE_Z_QUERY + "(?![A-Za-z0-9_])", MODERN_ZERO_QUERY);
            if (!adapted.equals(source)) {
                resources.put(COTESIA_GREP_ANIMATION, adapted.getBytes(StandardCharsets.UTF_8));
                changes++;
            }
        }

        byte[] modelBytes = resources.get(COTESIA_INF_CRAFTING_TABLE_MODEL);
        if (!currentSrpApi || modelBytes == null || resources.containsKey(SRP_TREE_DOWN_TEXTURE)) return changes;
        try {
            JsonObject model = GSON.fromJson(new String(modelBytes, StandardCharsets.UTF_8), JsonObject.class);
            if (model == null || !model.has("textures") || !model.get("textures").isJsonObject()) return changes;
            JsonObject textures = model.getAsJsonObject("textures");
            JsonElement down = textures.get("down");
            if (down == null || !down.isJsonPrimitive() || !down.getAsJsonPrimitive().isString()
                    || !"srparasites:blocks/parasitetrunk_tree_down".equals(down.getAsString())) return changes;
            textures.addProperty("down", SRP_TREE_END_TEXTURE);
            resources.put(COTESIA_INF_CRAFTING_TABLE_MODEL, GSON.toJson(model).getBytes(StandardCharsets.UTF_8));
            return changes + 1;
        } catch (RuntimeException ignored) {
            return changes;
        }
    }

    /** GeckoLib 4 only discovers Cotesia animation assets with the modern suffix. */
    private static int adaptCotesiaAnimationPaths(Map<String, byte[]> classes) {
        int changes = 0;
        for (Map.Entry<String, byte[]> entry : List.copyOf(classes.entrySet())) {
            ClassNode node = readClass(entry.getValue());
            if (node == null) continue;
            int classChanges = 0;
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (!(instruction instanceof LdcInsnNode ldc) || !(ldc.cst instanceof String value)) continue;
                    String replacement = COTESIA_ANIMATION_PATHS.get(value);
                    if (replacement == null) continue;
                    ldc.cst = replacement;
                    classChanges++;
                }
            }
            if (classChanges == 0) continue;
            classes.put(entry.getKey(), writeClass(node));
            changes += classChanges;
        }
        return changes;
    }

    private static int adaptRemainingEntityMixins(Map<String, byte[]> classes, Map<String, ClassInfo> peers,
            LegacyRemapper remapper) {
        int changes = adaptObsoleteInfectedMelt(classes, peers);
        changes += adaptInfectedSearchState(classes, peers, remapper);
        changes += adaptNearestAttackableTargetStatusMixin(classes, peers, remapper);
        changes += adaptEntityRofMixins(classes, peers, remapper);
        changes += adaptEntityNadeMixin(classes, peers, remapper);
        return changes;
    }

    private static int adaptScaryOrbRename(Map<String, ClassInfo> peers, LegacyRemapper remapper) {
        if (peers.containsKey(OLD_SCARY_ORB)) return 0;
        ClassInfo orb = peers.get(CURRENT_SCARY_ORB);
        if (!hasCotesiaScaryOrbShape(orb, remapper)) return 0;
        remapper.override(OLD_SCARY_ORB, CURRENT_SCARY_ORB);
        return 1;
    }

    private static boolean hasCotesiaScaryOrbShape(ClassInfo orb, LegacyRemapper remapper) {
        if (orb == null) return false;
        return orb.hasField("prevRenderYawOffset", "F")
                && orb.hasField("renderYawOffset", "F")
                && orb.hasField("prevRotationYawHead", "F")
                && orb.hasField("rotationYawHead", "F")
                && orb.hasField("prevLimbSwingAmount", "F")
                && orb.hasField("limbSwingAmount", "F")
                && orb.hasField("limbSwing", "F")
                && orb.hasField("hurtTime", "I")
                && orb.hasField("deathTime", "I")
                && orb.hasField("lastActiveTime", "I")
                && orb.hasField("timeSinceIgnited", "I")
                && orb.hasField("timerDDD", "I")
                && orb.hasField("father", remapper.mapDesc("L" + ENTITY_PMALLEABLE + ";"))
                && orb.hasField("followF", "Z")
                && orb.hasField("poosX", "D")
                && orb.hasField("poosY", "D")
                && orb.hasField("poosZ", "D")
                && orb.hasMethod("dyingBurst", "(ZI)V")
                && orb.hasMethod("selfExplode", "()V")
                && orb.hasMethod("getStartState", "()I")
                && orb.hasMethod("setSelfeState", "(I)V")
                && orb.hasMethod("spawnOrbEffects", "(I)V");
    }

    private static int adaptObsoleteInfectedMelt(Map<String, byte[]> classes, Map<String, ClassInfo> peers) {
        ClassInfo infected = peers.get(ENTITY_P_INFECTED);
        ClassInfo parasite = peers.get(ENTITY_PARASITE);
        if (infected == null || parasite == null || infected.hasMethod("melt", "()V") || parasite.hasMethod("melt", "()V")
                || !infected.hasMethod("transform", "()V")) return 0;

        ClassNode mixin = readClass(classes.get(ENTITY_P_INFECTED_MIXIN));
        if (mixin == null || !mixinTargets(mixin, ENTITY_P_INFECTED)) return 0;
        MethodNode transform = findMethodByName(mixin, "srpcotesia$transform");
        MethodNode melt = findMethodByName(mixin, "srpcotesia$melt");
        AnnotationNode transformInject = transform == null ? null : findMethodAnnotation(transform,
                "Lorg/spongepowered/asm/mixin/injection/Inject;");
        AnnotationNode meltInject = melt == null ? null : findMethodAnnotation(melt,
                "Lorg/spongepowered/asm/mixin/injection/Inject;");
        if (transformInject == null || meltInject == null || !selectsExactMethod(transformInject, "transform")
                || !selectsExactMethod(meltInject, "melt")
                || !containsCall(transform, "com/srpcotesia/util/ParasiteInteractions", "isSaddled",
                        "(L" + ENTITY_PARASITE + ";)Z")) return 0;

        mixin.methods.remove(melt);
        classes.put(ENTITY_P_INFECTED_MIXIN, writeClass(mixin));
        return 1;
    }

    private static int adaptInfectedSearchState(Map<String, byte[]> classes, Map<String, ClassInfo> peers,
            LegacyRemapper remapper) {
        ClassInfo target = peers.get(ENTITY_AI_INFECTED_SEARCH);
        String parentDesc = remapper.mapDesc("L" + ENTITY_PARASITE + ";");
        if (target == null || target.hasFieldNamed("canmelt") || !target.hasField("parent", parentDesc)
                || !target.hasMethod("LeshCount", "()Z")) return 0;

        ClassNode mixin = readClass(classes.get(ENTITY_AI_INFECTED_SEARCH_MIXIN));
        if (mixin == null || !mixinTargets(mixin, ENTITY_AI_INFECTED_SEARCH)) return 0;
        FieldNode canmelt = null;
        for (FieldNode field : mixin.fields) {
            if (field.name.equals("canmelt") && field.desc.equals("Z")) canmelt = field;
        }
        MethodNode update = findMethodByName(mixin, "srpcotesia$updateTask");
        MethodNode count = findMethodByName(mixin, "srpcotesia$leshCount");
        if (canmelt == null || update == null || count == null
                || findClassAnnotation(canmelt.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Shadow;") == null) return 0;
        int writes = countFieldWrites(mixin, "canmelt", "Z");
        if (writes != 2) return 0;

        removeFieldAnnotation(canmelt, "Lorg/spongepowered/asm/mixin/Shadow;");
        if (canmelt.visibleAnnotations == null) canmelt.visibleAnnotations = new ArrayList<>();
        canmelt.visibleAnnotations.add(new AnnotationNode("Lorg/spongepowered/asm/mixin/Unique;"));
        classes.put(ENTITY_AI_INFECTED_SEARCH_MIXIN, writeClass(mixin));
        return 1;
    }

    private static int adaptNearestAttackableTargetStatusMixin(Map<String, byte[]> classes, Map<String, ClassInfo> peers,
            LegacyRemapper remapper) {
        ClassInfo target = peers.get(ENTITY_AI_NEAREST_TARGET);
        String targetMethodDesc = remapper.mapMethodDesc(NEAREST_TARGET_METHOD_DESC);
        if (target == null || !remapper.map(ENTITY_AI_BASE).equals(target.superName)
                || !target.hasField("taskOwner", remapper.mapDesc("L" + ENTITY_PARASITE + ";"))
                || !target.hasField("shouldCheckSight", "Z")
                || !target.hasField("targetClass", "Ljava/lang/Class;")
                || !target.hasField("targetEntitySelector", "Lcom/google/common/base/Predicate;")
                || !target.hasMethod("isSuitableTarget", targetMethodDesc)) return 0;

        ClassNode mixin = readClass(classes.get(ENTITY_AI_NEAREST_TARGET_MIXIN));
        if (mixin == null || !mixinTargets(mixin, ENTITY_AI_NEAREST_TARGET)
                || !ENTITY_AI_TARGET.equals(mixin.superName)) return 0;
        FieldNode targetClass = null;
        FieldNode selector = null;
        for (FieldNode field : mixin.fields) {
            if (field.name.equals("targetClass") && field.desc.equals("Ljava/lang/Class;")) targetClass = field;
            if (field.name.equals("targetEntitySelector") && field.desc.equals("Lcom/google/common/base/Predicate;")) selector = field;
        }
        MethodNode constructor = findMethodByName(mixin, "<init>");
        MethodNode sight = findMethodByName(mixin, "func_75296_a");
        String oldSuperSignature = "L" + ENTITY_AI_TARGET + ";";
        if (targetClass == null || selector == null || constructor == null || sight == null
                || (mixin.signature != null && !mixin.signature.endsWith(oldSuperSignature))
                || !constructor.desc.equals("(L" + ENTITY_CREATURE + ";Z)V")
                || !sight.desc.equals(NEAREST_TARGET_METHOD_DESC)
                || !hasFieldAnnotation(targetClass, "Lorg/spongepowered/asm/mixin/Shadow;")
                || !hasFieldAnnotation(selector, "Lorg/spongepowered/asm/mixin/Shadow;")
                || !rewriteNearestTargetSightHandler(sight)) return 0;

        removeFieldAnnotation(targetClass, "Lorg/spongepowered/asm/mixin/Final;");
        rewriteMixinDummyConstructor(constructor);
        mixin.superName = ENTITY_AI_BASE;
        if (mixin.signature != null) mixin.signature = mixin.signature.substring(0, mixin.signature.length() - oldSuperSignature.length())
                + "L" + ENTITY_AI_BASE + ";";
        classes.put(ENTITY_AI_NEAREST_TARGET_MIXIN, writeClassWithFrames(mixin));
        return 1;
    }

    static boolean rewriteNearestTargetSightHandler(MethodNode handler) {
        MethodInsnNode superCall = null;
        int superCalls = 0;
        int sightChecks = 0;
        int ownerReads = 0;
        for (AbstractInsnNode instruction : handler.instructions) {
            if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                    && call.owner.equals(ENTITY_AI_TARGET) && call.name.equals("func_75296_a")
                    && call.desc.equals(NEAREST_TARGET_METHOD_DESC)) {
                superCall = call;
                superCalls++;
            } else if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                    && field.owner.equals(ENTITY_AI_NEAREST_TARGET_MIXIN)) {
                if (field.name.equals("field_75297_f") && field.desc.equals("Z")) sightChecks++;
                if (field.name.equals("field_75299_d") && field.desc.equals("L" + ENTITY_CREATURE + ";")) ownerReads++;
            }
        }
        if (superCalls != 1 || sightChecks != 1 || ownerReads != 2) return false;

        AbstractInsnNode loadBoolean = previousCode(superCall.getPrevious());
        AbstractInsnNode loadTarget = loadBoolean == null ? null : previousCode(loadBoolean.getPrevious());
        AbstractInsnNode loadThis = loadTarget == null ? null : previousCode(loadTarget.getPrevious());
        if (!(loadBoolean instanceof VarInsnNode boolArg) || boolArg.getOpcode() != Opcodes.ILOAD || boolArg.var != 2
                || !(loadTarget instanceof VarInsnNode targetArg) || targetArg.getOpcode() != Opcodes.ALOAD || targetArg.var != 1
                || !(loadThis instanceof VarInsnNode thisArg) || thisArg.getOpcode() != Opcodes.ALOAD || thisArg.var != 0) return false;

        shiftMethodLocals(handler, 3, 1);
        InsnList invokeOriginal = new InsnList();
        invokeOriginal.add(new VarInsnNode(Opcodes.ALOAD, 3));
        invokeOriginal.add(new InsnNode(Opcodes.ICONST_2));
        invokeOriginal.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        invokeOriginal.add(new InsnNode(Opcodes.DUP));
        invokeOriginal.add(new InsnNode(Opcodes.ICONST_0));
        invokeOriginal.add(new VarInsnNode(Opcodes.ALOAD, 1));
        invokeOriginal.add(new InsnNode(Opcodes.AASTORE));
        invokeOriginal.add(new InsnNode(Opcodes.DUP));
        invokeOriginal.add(new InsnNode(Opcodes.ICONST_1));
        invokeOriginal.add(new VarInsnNode(Opcodes.ILOAD, 2));
        invokeOriginal.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false));
        invokeOriginal.add(new InsnNode(Opcodes.AASTORE));
        invokeOriginal.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, OPERATION, "call", "([Ljava/lang/Object;)Ljava/lang/Object;", true));
        invokeOriginal.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Boolean"));
        invokeOriginal.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false));
        handler.instructions.insertBefore(loadThis, invokeOriginal);
        handler.instructions.remove(loadThis);
        handler.instructions.remove(loadTarget);
        handler.instructions.remove(loadBoolean);
        handler.instructions.remove(superCall);

        for (AbstractInsnNode instruction : handler.instructions) {
            if (!(instruction instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETFIELD
                    || !field.owner.equals(ENTITY_AI_NEAREST_TARGET_MIXIN)) continue;
            if (field.name.equals("field_75297_f") && field.desc.equals("Z")) {
                field.owner = ENTITY_AI_NEAREST_TARGET;
                field.name = "shouldCheckSight";
            } else if (field.name.equals("field_75299_d") && field.desc.equals("L" + ENTITY_CREATURE + ";")) {
                field.owner = ENTITY_AI_NEAREST_TARGET;
                field.name = "taskOwner";
                field.desc = "L" + ENTITY_PARASITE + ";";
            }
        }

        handler.name = NEAREST_TARGET_HANDLER;
        handler.access = (handler.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC
                | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) | Opcodes.ACC_PRIVATE;
        handler.desc = NEAREST_TARGET_WRAP_DESC;
        handler.signature = "(L" + ENTITY_LIVING_BASE + ";ZL" + OPERATION + "<Ljava/lang/Boolean;>;)Z";
        AnnotationNode wrapMethod = new AnnotationNode(WRAP_METHOD);
        wrapMethod.values = new ArrayList<>(List.of("method", List.of("isSuitableTarget" + NEAREST_TARGET_METHOD_DESC)));
        handler.visibleAnnotations = new ArrayList<>(List.of(wrapMethod));
        handler.invisibleAnnotations = null;
        return true;
    }

    private static void rewriteMixinDummyConstructor(MethodNode constructor) {
        constructor.instructions.clear();
        constructor.tryCatchBlocks.clear();
        constructor.localVariables = null;
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, ENTITY_AI_BASE, "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        constructor.maxStack = 1;
        constructor.maxLocals = Type.getArgumentsAndReturnSizes(constructor.desc) >>> 2;
    }

    private static int adaptEntityRofMixins(Map<String, byte[]> classes, Map<String, ClassInfo> peers,
            LegacyRemapper remapper) {
        ClassInfo target = peers.get(ENTITY_ROF);
        String livingDesc = remapper.mapDesc("Lnet/minecraft/entity/EntityLiving;");
        String getMobDesc = "()" + livingDesc;
        String factoryTarget = "L" + remapper.map(ENTITY_LIST) + ";createEntityByIDFromName"
                + remapper.mapMethodDesc(SUMMON_FACTORY_DESC);
        if (target == null || !target.hasMethod("getMobFromList", getMobDesc)
                || target.instructionSiteCount("getMobFromList" + getMobDesc, "INVOKE", factoryTarget,
                        Opcodes.INVOKESTATIC) != 1) return 0;

        ClassNode mixin = readClass(classes.get(ENTITY_ROF_MIXIN));
        if (mixin == null || !mixinTargets(mixin, ENTITY_ROF)) return 0;
        MethodNode getMob = findMethodByName(mixin, "srpcotesia$getMob");
        MethodNode spawnWaves = findMethodByName(mixin, "srpcotesia$spawnWaves");
        AnnotationNode getMobInject = getMob == null ? null : findMethodAnnotation(getMob,
                "Lorg/spongepowered/asm/mixin/injection/Inject;");
        AnnotationNode spawnWavesWrap = spawnWaves == null ? null : findMethodAnnotation(spawnWaves,
                "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;");
        if (getMobInject == null || spawnWavesWrap == null || !selectsExactMethod(getMobInject, "getMob")
                || !selectsExactMethod(spawnWavesWrap, "spawnWaves")
                || !containsTarget(spawnWavesWrap, "L" + ENTITY_LIST + ";createEntityByIDFromName" + SUMMON_FACTORY_DESC)) return 0;

        rewriteRofGetMobHandler(getMob, livingDesc);
        setStringList(getMobInject, "method", List.of("getMobFromList"));
        setStringList(spawnWavesWrap, "method", List.of("getMobFromList"));
        classes.put(ENTITY_ROF_MIXIN, writeClassWithFrames(mixin));
        return 2;
    }

    private static void rewriteRofGetMobHandler(MethodNode handler, String livingDesc) {
        handler.signature = "(" + CALLBACK_INFO_RETURNABLE_DESC.substring(0, CALLBACK_INFO_RETURNABLE_DESC.length() - 1)
                + "<Lnet/minecraft/entity/EntityLiving;>;)V";
        handler.instructions.clear();
        handler.tryCatchBlocks.clear();
        handler.visibleLocalVariableAnnotations = null;
        handler.invisibleLocalVariableAnnotations = null;
        LabelNode start = new LabelNode();
        LabelNode noExtra = new LabelNode();
        LabelNode end = new LabelNode();
        handler.instructions.add(start);
        handler.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        handler.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, ENTITY_ROF_MIXIN,
                "srpcotesia$getExtraParasite", "()Ljava/lang/String;", false));
        handler.instructions.add(new VarInsnNode(Opcodes.ASTORE, 2));
        handler.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        handler.instructions.add(new JumpInsnNode(Opcodes.IFNULL, noExtra));
        handler.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        handler.instructions.add(new TypeInsnNode(Opcodes.NEW, "net/minecraft/util/ResourceLocation"));
        handler.instructions.add(new InsnNode(Opcodes.DUP));
        handler.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        handler.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "net/minecraft/util/ResourceLocation",
                "<init>", "(Ljava/lang/String;)V", false));
        handler.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        handler.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY, "getEntityWorld",
                "()L" + WORLD + ";", false));
        handler.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_LIST, "createEntityByIDFromName",
                SUMMON_FACTORY_DESC, false));
        handler.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, Type.getType(livingDesc).getInternalName()));
        handler.instructions.add(new VarInsnNode(Opcodes.ASTORE, 3));
        handler.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        handler.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        handler.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, ENTITY_ROF_MIXIN,
                "srpcotesia$spawnWaves", "(Lnet/minecraft/entity/Entity;)Lnet/minecraft/entity/Entity;", false));
        handler.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, Type.getType(livingDesc).getInternalName()));
        handler.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable", "setReturnValue",
                "(Ljava/lang/Object;)V", false));
        handler.instructions.add(noExtra);
        handler.instructions.add(end);
        handler.instructions.add(new InsnNode(Opcodes.RETURN));
        handler.localVariables = new ArrayList<>(List.of(
                new LocalVariableNode("this", "L" + ENTITY_ROF_MIXIN + ";", null, start, end, 0),
                new LocalVariableNode("cir", CALLBACK_INFO_RETURNABLE_DESC, null, start, end, 1),
                new LocalVariableNode("extra", "Ljava/lang/String;", null, start, end, 2),
                new LocalVariableNode("entity", livingDesc, null, start, end, 3)));
        handler.maxLocals = 4;
    }

    private static int adaptEntityNadeMixin(Map<String, byte[]> classes, Map<String, ClassInfo> peers,
            LegacyRemapper remapper) {
        ClassInfo target = peers.get(ENTITY_NADE);
        String baseDesc = remapper.mapDesc("L" + ENTITY_PARASITE + ";");
        String oldMalleableDesc = "L" + ENTITY_PMALLEABLE + ";";
        String oldFieldDesc = oldMalleableDesc;
        String minimumDesc = "(" + baseDesc + "Lnet/minecraft/entity/EntityLivingBase;FL" + OPERATION + ";)Z";
        String targetMinimumDesc = remapper.mapMethodDesc("(Lnet/minecraft/entity/EntityLivingBase;F)Z");
        String targetMinimum = "L" + remapper.map(ENTITY_PARASITE) + ";attackEntityAsMobMinimum" + targetMinimumDesc;
        String peerSetterDesc = remapper.mapMethodDesc("(L" + ENTITY_PARASITE + ";)V");
        String peerFatherDesc = remapper.mapDesc("L" + ENTITY_PARASITE + ";");
        if (target == null || !target.hasField("father", peerFatherDesc)
                || !target.hasMethod("setFatherS", peerSetterDesc)
                || target.instructionSiteCount("selfExplode()V", "INVOKE", targetMinimum, Opcodes.INVOKEVIRTUAL) != 1) return 0;

        ClassNode mixin = readClass(classes.get(ENTITY_NADE_MIXIN));
        if (mixin == null || !mixinTargets(mixin, ENTITY_NADE)) return 0;
        FieldNode father = null;
        for (FieldNode field : mixin.fields) if (field.name.equals("father") && field.desc.equals(oldFieldDesc)) father = field;
        MethodNode setFather = findMethodByName(mixin, "srpcotesia$setFatherS");
        MethodNode minimum = findMethodByName(mixin, "srpcotesia$selfExplodeMinimum");
        AnnotationNode minimumWrap = minimum == null ? null : findMethodAnnotation(minimum,
                "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");
        String oldMinimumTarget = "L" + ENTITY_PMALLEABLE + ";attackEntityAsMobMinimum"
                + "(Lnet/minecraft/entity/EntityLivingBase;)Z";
        String oldSetFatherDesc = "(" + oldMalleableDesc + CALLBACK_INFO_DESC + ")V";
        String oldMinimumDesc = "(" + oldMalleableDesc + "Lnet/minecraft/entity/EntityLivingBase;L" + OPERATION + ";)Z";
        if (father == null || setFather == null || minimum == null || minimumWrap == null
                || !setFather.desc.equals(oldSetFatherDesc) || !minimum.desc.equals(oldMinimumDesc)
                || !containsTarget(minimumWrap, oldMinimumTarget)
                || !hasPotionAntibodyMethods(classes.get(POTION_ANTIBODIES))) return 0;

        int durationCalls = countMethodCalls(mixin, POTION_ANTIBODIES, "getDurationForMob", "(" + oldMalleableDesc + ")I");
        int ampCalls = countMethodCalls(mixin, POTION_ANTIBODIES, "getAmpForMob", "(" + oldMalleableDesc + ")I");
        if (durationCalls < 1 || ampCalls < 1) return 0;
        int overloads = addAntibodyBaseOverloads(classes);
        if (overloads < 0) return 0;

        father.desc = baseDesc;
        int fieldUses = 0;
        for (MethodNode method : mixin.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode fieldInsn && fieldInsn.owner.equals(mixin.name)
                        && fieldInsn.name.equals("father") && fieldInsn.desc.equals(oldFieldDesc)) {
                    fieldInsn.desc = baseDesc;
                    fieldUses++;
                }
            }
        }
        if (fieldUses == 0) return 0;

        setFather.desc = "(" + baseDesc + CALLBACK_INFO_DESC + ")V";
        updateArgumentLocalDescriptor(setFather, 1, baseDesc);
        minimum.desc = minimumDesc;
        minimum.signature = "(" + baseDesc + "Lnet/minecraft/entity/EntityLivingBase;F"
                + "L" + OPERATION + "<Ljava/lang/Boolean;>;)Z";
        updateArgumentLocalDescriptor(minimum, 1, baseDesc);
        replaceTarget(minimumWrap, oldMinimumTarget, "L" + ENTITY_PARASITE + ";attackEntityAsMobMinimum"
                + "(Lnet/minecraft/entity/EntityLivingBase;F)Z");
        rewriteMinimumDamageHandler(minimum);

        String oldHelperDesc = "(" + oldMalleableDesc + ")I";
        String newHelperDesc = "(L" + ENTITY_PARASITE + ";)I";
        int antibodyCalls = 0;
        for (MethodNode method : mixin.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals(POTION_ANTIBODIES)) continue;
                if ((call.name.equals("getDurationForMob") || call.name.equals("getAmpForMob"))
                        && call.desc.equals(oldHelperDesc)) {
                    call.desc = newHelperDesc;
                    antibodyCalls++;
                }
            }
        }
        if (antibodyCalls != durationCalls + ampCalls) return 0;
        classes.put(ENTITY_NADE_MIXIN, writeClassWithFrames(mixin));
        return 1 + overloads + fieldUses + antibodyCalls;
    }

    private static void rewriteMinimumDamageHandler(MethodNode method) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        method.visibleLocalVariableAnnotations = null;
        method.invisibleLocalVariableAnnotations = null;
        LabelNode start = new LabelNode();
        LabelNode fallback = new LabelNode();
        LabelNode end = new LabelNode();
        InsnList code = method.instructions;
        code.add(start);
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new TypeInsnNode(Opcodes.INSTANCEOF, ENTITY_ELVIA));
        code.add(new JumpInsnNode(Opcodes.IFEQ, fallback));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new TypeInsnNode(Opcodes.CHECKCAST, I_PARASITE));
        code.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, I_PARASITE, "srpcotesia$isAugmented", "()Z", true));
        code.add(new JumpInsnNode(Opcodes.IFEQ, fallback));
        emitMinimumDamageOperationCall(code, true);
        code.add(fallback);
        emitMinimumDamageOperationCall(code, false);
        code.add(end);
        method.localVariables = new ArrayList<>(List.of(
                new LocalVariableNode("this", "L" + ENTITY_NADE_MIXIN + ";", null, start, end, 0),
                new LocalVariableNode("mal", "L" + ENTITY_PARASITE + ";", null, start, end, 1),
                new LocalVariableNode("target", "Lnet/minecraft/entity/EntityLivingBase;", null, start, end, 2),
                new LocalVariableNode("damage", "F", null, start, end, 3),
                new LocalVariableNode("original", "L" + OPERATION + ";", null, start, end, 4)));
        method.maxLocals = 5;
    }

    private static void emitMinimumDamageOperationCall(InsnList code, boolean scaleDamage) {
        code.add(new VarInsnNode(Opcodes.ALOAD, 4));
        code.add(new InsnNode(Opcodes.ICONST_2));
        code.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        code.add(new InsnNode(Opcodes.DUP));
        code.add(new InsnNode(Opcodes.ICONST_0));
        code.add(new VarInsnNode(Opcodes.ALOAD, 2));
        code.add(new InsnNode(Opcodes.AASTORE));
        code.add(new InsnNode(Opcodes.DUP));
        code.add(new InsnNode(Opcodes.ICONST_1));
        code.add(new VarInsnNode(Opcodes.FLOAD, 3));
        if (scaleDamage) {
            code.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/srpcotesia/config/ConfigMain", "enchantments",
                    "Lcom/srpcotesia/config/ConfigMain$ConfEnchants;"));
            code.add(new FieldInsnNode(Opcodes.GETFIELD, "com/srpcotesia/config/ConfigMain$ConfEnchants", "acidBath",
                    "Lcom/srpcotesia/config/ConfigMain$ConfEnchants$AcidBath;"));
            code.add(new FieldInsnNode(Opcodes.GETFIELD, "com/srpcotesia/config/ConfigMain$ConfEnchants$AcidBath", "power", "D"));
            code.add(new InsnNode(Opcodes.D2F));
            code.add(new InsnNode(Opcodes.FMUL));
        }
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false));
        code.add(new InsnNode(Opcodes.AASTORE));
        code.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, OPERATION, "call", "([Ljava/lang/Object;)Ljava/lang/Object;", true));
        code.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Boolean"));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false));
        code.add(new InsnNode(Opcodes.IRETURN));
    }

    private static boolean hasPotionAntibodyMethods(byte[] bytes) {
        ClassNode node = readClass(bytes);
        String oldDesc = "(L" + ENTITY_PMALLEABLE + ";)I";
        String newDesc = "(L" + ENTITY_PARASITE + ";)I";
        if (node == null || !node.name.equals(POTION_ANTIBODIES)) return false;
        boolean duration = findMethod(node, "getDurationForMob", oldDesc) != null
                || findMethod(node, "getDurationForMob", newDesc) != null;
        boolean amp = findMethod(node, "getAmpForMob", oldDesc) != null || findMethod(node, "getAmpForMob", newDesc) != null;
        return duration && amp;
    }

    private static int addAntibodyBaseOverloads(Map<String, byte[]> classes) {
        ClassNode node = readClass(classes.get(POTION_ANTIBODIES));
        if (node == null || !node.name.equals(POTION_ANTIBODIES)) return -1;
        String oldDesc = "(L" + ENTITY_PMALLEABLE + ";)I";
        String newDesc = "(L" + ENTITY_PARASITE + ";)I";
        int changes = 0;
        for (String name : List.of("getDurationForMob", "getAmpForMob")) {
            if (findMethod(node, name, newDesc) != null) continue;
            MethodNode oldMethod = findMethod(node, name, oldDesc);
            if (oldMethod == null) return -1;
            MethodNode overload = copyMethod(oldMethod);
            overload.desc = newDesc;
            if (overload.localVariables != null) {
                for (LocalVariableNode local : overload.localVariables) if (local.index == 0) local.desc = "L" + ENTITY_PARASITE + ";";
            }
            node.methods.add(overload);
            changes++;
        }
        if (changes > 0) classes.put(POTION_ANTIBODIES, writeClass(node));
        return changes;
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

    private static void removeFieldAnnotation(FieldNode field, String descriptor) {
        if (field.visibleAnnotations != null) field.visibleAnnotations.removeIf(annotation -> annotation.desc.equals(descriptor));
        if (field.invisibleAnnotations != null) field.invisibleAnnotations.removeIf(annotation -> annotation.desc.equals(descriptor));
    }

    private static boolean containsCall(MethodNode method, String owner, String name, String desc) {
        if (method == null) return false;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)
                    && call.desc.equals(desc)) return true;
        }
        return false;
    }

    private static int countFieldWrites(ClassNode owner, String name, String desc) {
        int count = 0;
        for (MethodNode method : owner.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD
                        && field.owner.equals(owner.name) && field.name.equals(name) && field.desc.equals(desc)) count++;
            }
        }
        return count;
    }

    private static int countMethodCalls(ClassNode owner, String targetOwner, String name, String desc) {
        int count = 0;
        for (MethodNode method : owner.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(targetOwner)
                        && call.name.equals(name) && call.desc.equals(desc)) count++;
            }
        }
        return count;
    }

    private static int adaptMovedParasiteMethods(Map<String, byte[]> classes, Map<String, byte[]> resources,
            Map<String, ClassInfo> peers, LegacyRemapper remapper) {
        ClassInfo world = peers.get(PARASITE_EVENT_WORLD);
        ClassInfo spreading = peers.get(BLOCK_PARASITE_SPREADING);
        ClassInfo beckon = peers.get(BECKON_BLOCK_INFESTATION);
        String canInfestDesc = remapper.mapMethodDesc(CAN_INFEST_DESC);
        String spreadDesc = remapper.mapMethodDesc(SPREAD_METHOD_DESC);
        if (world == null || spreading == null || beckon == null
                || world.hasMethod("spreadBiomeBlockStain", spreadDesc)
                || world.hasMethod("spreadBiomeBlockTrunk", spreadDesc)
                || !spreading.hasMethod("canInfestBlock", canInfestDesc)
                || !spreading.hasMethod("spreadBiomeBlockStain", spreadDesc)
                || !spreading.hasMethod("spreadBiomeBlockTrunk", spreadDesc)
                || !beckon.hasMethod("beckonInfestation", canInfestDesc)) return 0;

        int changes = splitMovedParasiteMethods(classes, resources, beckon, canInfestDesc, remapper);
        changes += adaptBlockInfestAiTarget(classes, peers.get(ENTITY_AI_BLOCK_INFEST), remapper);
        return changes;
    }

    private static int adaptMovedSummonMethods(Map<String, byte[]> classes, Map<String, byte[]> resources,
            Map<String, ClassInfo> peers, LegacyRemapper remapper) {
        ClassInfo summon = peers.get(PARASITE_SUMMON);
        ClassInfo events = peers.get(PARASITE_EVENT_ENTITY);
        String oldFirstDesc = remapper.mapMethodDesc(OLD_SUMMON_M_DESC);
        String newFirstDesc = remapper.mapMethodDesc(NEW_SUMMON_M_DESC);
        String secondDesc = remapper.mapMethodDesc(SUMMON_M_TWO_DESC);
        String factoryTarget = "L" + remapper.map(ENTITY_LIST) + ";createEntityByIDFromName" + remapper.mapMethodDesc(SUMMON_FACTORY_DESC);
        if (summon == null || events == null || events.hasMethod("SummonM", oldFirstDesc)
                || events.hasMethod("SummonM", secondDesc)
                || !summon.hasMethod("SummonM", newFirstDesc) || !summon.hasMethod("SummonM", secondDesc)
                || summon.instructionSiteCount("SummonM" + newFirstDesc, "INVOKE", factoryTarget, -1) == 0
                || summon.instructionSiteCount("SummonM" + secondDesc, "INVOKE", factoryTarget, -1) == 0) return 0;

        String config = findMixinConfig(resources, "ParasiteEventEntityMixin");
        if (config == null) return 0;
        ClassNode source = readClass(classes.get(MERGE_MIXIN));
        if (source == null || !source.name.equals(MERGE_MIXIN) || !mixinTargets(source, PARASITE_EVENT_ENTITY)) return 0;
        MethodNode first = findMethodByName(source, "srpcotesia$SummonM");
        MethodNode second = findMethodByName(source, "srpcotesia$SummonMTwo");
        if (first == null || second == null) return 0;
        AnnotationNode firstInjector = findMethodAnnotation(first, "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;");
        AnnotationNode secondInjector = findMethodAnnotation(second, "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;");
        String oldFirstSelector = "SummonM" + OLD_SUMMON_M_DESC;
        String oldSecondSelector = "SummonM" + SUMMON_M_TWO_DESC;
        if (first == null || second == null || firstInjector == null || secondInjector == null
                || !selectsExactMethod(firstInjector, oldFirstSelector) || !selectsExactMethod(secondInjector, oldSecondSelector)
                || !containsTarget(firstInjector, "L" + ENTITY_LIST + ";createEntityByIDFromName" + SUMMON_FACTORY_DESC)
                || !containsTarget(secondInjector, "L" + ENTITY_LIST + ";createEntityByIDFromName" + SUMMON_FACTORY_DESC)
                || !first.desc.equals(OLD_SUMMON_M_HANDLER_DESC) || !second.desc.equals(SUMMON_M_TWO_HANDLER_DESC)) return 0;

        insertHandlerArgument(first, 4, Type.INT_TYPE, 4);
        if (first.parameters != null) first.parameters.get(4).name = null;
        Type[] firstArguments = Type.getArgumentTypes(first.desc);
        firstArguments[1] = Type.getObjectType("net/minecraft/entity/EntityLivingBase");
        first.desc = Type.getMethodDescriptor(Type.getReturnType(first.desc), firstArguments);
        setStringList(firstInjector, "method", List.of("SummonM" + NEW_SUMMON_M_DESC));
        setStringList(secondInjector, "method", List.of("SummonM" + SUMMON_M_TWO_DESC));
        updateArgumentLocalDescriptor(first, 1, "Lnet/minecraft/entity/EntityLivingBase;");

        ClassNode split = copyMixinTarget(source, SUMMON_MIXIN, PARASITE_SUMMON);
        source.methods.remove(first);
        source.methods.remove(second);
        split.methods.add(first);
        split.methods.add(second);
        appendMixinEntries(resources, config, List.of(SUMMON_MIXIN));
        classes.put(MERGE_MIXIN, writeClass(source));
        classes.put(SUMMON_MIXIN, writeClass(split));
        return 2;
    }

    private static int adaptGetFeralCall(Map<String, byte[]> classes, Map<String, ClassInfo> peers, LegacyRemapper remapper) {
        ClassInfo infected = peers.get(ENTITY_P_INFECTED);
        ClassInfo events = peers.get(PARASITE_EVENT_ENTITY);
        String currentGet = remapper.mapMethodDesc(NEW_GET_FERAL_DESC);
        String convert = remapper.mapMethodDesc(CONVERT_ENTITY_FERAL_DESC);
        String currentTarget = "L" + ENTITY_P_INFECTED + ";getFeral" + currentGet;
        if (infected == null || events == null || !infected.hasMethod("getFeral", currentGet)
                || events.instructionSiteCount("convertEntityFeral" + convert, "INVOKE", currentTarget, -1) != 1) return 0;
        ClassNode mixin = readClass(classes.get(MERGE_MIXIN));
        if (mixin == null || !mixin.name.equals(MERGE_MIXIN)) return 0;
        MethodNode handler = findMethodByName(mixin, GET_FERAL_HANDLER);
        if (handler == null) return 0;
        AnnotationNode injector = findMethodAnnotation(handler, "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;");
        String oldTarget = "L" + ENTITY_P_INFECTED + ";getFeral" + OLD_GET_FERAL_DESC;
        if (injector == null || !mixinTargets(mixin, PARASITE_EVENT_ENTITY) || !containsTarget(injector, oldTarget)) return 0;
        if (!replaceTarget(injector, oldTarget, "L" + ENTITY_P_INFECTED + ";getFeral" + NEW_GET_FERAL_DESC)) return 0;
        classes.put(MERGE_MIXIN, writeClass(mixin));
        return 1;
    }

    private static boolean selectsExactMethod(AnnotationNode injector, String selector) {
        Object methods = annotationValue(injector, "method");
        return methods instanceof Iterable<?> values && java.util.stream.StreamSupport.stream(values.spliterator(), false)
                .anyMatch(selector::equals);
    }

    private static MethodNode findMethodByName(ClassNode node, String name) {
        if (node == null) return null;
        for (MethodNode method : node.methods) if (method.name.equals(name)) return method;
        return null;
    }

    private static void updateArgumentLocalDescriptor(MethodNode method, int localIndex, String descriptor) {
        if (method.localVariables == null) return;
        for (LocalVariableNode local : method.localVariables) if (local.index == localIndex) local.desc = descriptor;
    }

    private static int splitMovedParasiteMethods(Map<String, byte[]> classes, Map<String, byte[]> resources,
            ClassInfo beckon, String canInfestDesc, LegacyRemapper remapper) {
        if (classes.containsKey(SPREADING_MIXIN) || classes.containsKey(BECKON_INFESTATION_MIXIN)) return 0;
        String config = findMixinConfig(resources, "ParasiteEventWorldMixin");
        if (config == null) return 0;
        String newKillsTarget = "L" + SRP_SAVE_DATA + ";setTotalKills" + remapper.mapMethodDesc(NEW_SET_TOTAL_KILLS);
        if (beckon.instructionSiteCount("beckonInfestation" + canInfestDesc, "INVOKE", newKillsTarget, -1) == 0) return 0;
        String spawnBeckonDesc = remapper.mapMethodDesc(SPAWN_BECKON_DESC);
        String cannotDespawnTarget = "L" + ENTITY_VENKROL + ";cannotDespawn(Z)V";
        if (!beckon.hasMethod("spawnBeckonFromInfestation", spawnBeckonDesc)
                || beckon.instructionSiteCount("beckonInfestation" + canInfestDesc, "INVOKE",
                        "L" + BECKON_BLOCK_INFESTATION + ";spawnBeckonFromInfestation" + spawnBeckonDesc, -1) != 1
                || beckon.instructionSiteCount("spawnBeckonFromInfestation" + spawnBeckonDesc,
                        "INVOKE", cannotDespawnTarget, -1) != 1) return 0;
        ClassNode source = readClass(classes.get(PARASITE_EVENT_WORLD_MIXIN));
        if (source == null || !source.name.equals(PARASITE_EVENT_WORLD_MIXIN) || !mixinTargets(source, PARASITE_EVENT_WORLD)) return 0;

        List<MethodNode> spreadingMethods = new ArrayList<>();
        List<MethodNode> beckonMethods = new ArrayList<>();
        int stainHandlers = 0;
        int trunkHandlers = 0;
        int beckonWrappers = 0;
        int beckonInjectors = 0;
        for (MethodNode method : source.methods) {
            if (selectsMethod(method, "spreadBiomeBlockStain")) {
                spreadingMethods.add(method);
                stainHandlers++;
            } else if (selectsMethod(method, "spreadBiomeBlockTrunk")) {
                spreadingMethods.add(method);
                trunkHandlers++;
            } else if (selectsMethod(method, "canInfestBlock")
                    && findMethodAnnotation(method, "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;") != null) {
                AnnotationNode wrapper = findMethodAnnotation(method, "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");
                if (!adaptMovedKillsWrapOperation(method, wrapper, remapper)) continue;
                setStringList(wrapper, "method", List.of("beckonInfestation"));
                beckonMethods.add(method);
                beckonWrappers++;
            } else if (selectsMethod(method, "canInfestBlock")
                    && findMethodAnnotation(method, "Lorg/spongepowered/asm/mixin/injection/Inject;") != null) {
                AnnotationNode inject = findMethodAnnotation(method, "Lorg/spongepowered/asm/mixin/injection/Inject;");
                if (!adaptMovedVenkrolInjector(method, inject)) continue;
                setStringList(inject, "method", List.of("spawnBeckonFromInfestation"));
                beckonMethods.add(method);
                beckonInjectors++;
            }
        }
        if (stainHandlers != 1 || trunkHandlers != 1 || beckonWrappers != 1 || beckonInjectors != 1) return 0;

        ClassNode spreadingMixin = copyMixinTarget(source, SPREADING_MIXIN, BLOCK_PARASITE_SPREADING);
        ClassNode beckonMixin = copyMixinTarget(source, BECKON_INFESTATION_MIXIN, BECKON_BLOCK_INFESTATION);
        for (MethodNode method : spreadingMethods) {
            source.methods.remove(method);
            spreadingMixin.methods.add(method);
        }
        for (MethodNode method : beckonMethods) {
            source.methods.remove(method);
            beckonMixin.methods.add(method);
        }
        appendMixinEntries(resources, config, List.of(SPREADING_MIXIN, BECKON_INFESTATION_MIXIN));
        classes.putIfAbsent(SOURCE_ID_OPERATION, sourceIdOperationClass());
        classes.put(PARASITE_EVENT_WORLD_MIXIN, writeClass(source));
        classes.put(SPREADING_MIXIN, writeClass(spreadingMixin));
        classes.put(BECKON_INFESTATION_MIXIN, writeClass(beckonMixin));
        return 6;
    }

    private static boolean adaptMovedVenkrolInjector(MethodNode method, AnnotationNode inject) {
        String cannotDespawn = "L" + ENTITY_VENKROL + ";cannotDespawn(Z)V";
        if (inject == null || !method.desc.equals(OLD_VENKROL_INJECT_DESC) || method.signature != null
                || !containsTarget(inject, cannotDespawn) || !hasParameterAnnotation(method, 6, LOCAL_ANNOTATION_DESC)
                || (method.parameters != null && method.parameters.size() < 7)) return false;

        int fromVenkrolReads = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof VarInsnNode variable) {
                if (variable.var == 2 || (variable.var == 4 && variable.getOpcode() != Opcodes.ILOAD)) return false;
                if (variable.var == 4) fromVenkrolReads++;
            } else if (instruction instanceof IincInsnNode increment && (increment.var == 2 || increment.var == 4)) {
                return false;
            }
        }
        if (fromVenkrolReads != 1) return false;

        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;) {
            AbstractInsnNode next = instruction.getNext();
            if (instruction instanceof VarInsnNode variable) {
                if (variable.var == 4) method.instructions.set(variable, new InsnNode(Opcodes.ICONST_1));
                else variable.var = remapVenkrolLocal(variable.var);
            } else if (instruction instanceof IincInsnNode increment) {
                increment.var = remapVenkrolLocal(increment.var);
            }
            instruction = next;
        }
        method.desc = NEW_VENKROL_INJECT_DESC;
        method.visibleParameterAnnotations = retainParameters(method.visibleParameterAnnotations, 0, 1, 3, 5, 6);
        method.invisibleParameterAnnotations = retainParameters(method.invisibleParameterAnnotations, 0, 1, 3, 5, 6);
        if (method.visibleParameterAnnotations != null) method.visibleAnnotableParameterCount = 5;
        if (method.invisibleParameterAnnotations != null) method.invisibleAnnotableParameterCount = 5;
        if (method.parameters != null) {
            List<ParameterNode> retained = new ArrayList<>();
            for (int index : new int[] {0, 1, 3, 5, 6}) {
                ParameterNode parameter = method.parameters.get(index);
                retained.add(new ParameterNode(parameter.name, parameter.access));
            }
            method.parameters = retained;
        }
        if (method.localVariables != null) {
            method.localVariables.removeIf(local -> local.index == 2 || local.index == 4);
            for (LocalVariableNode local : method.localVariables) local.index = remapVenkrolLocal(local.index);
        }
        method.maxLocals = Math.max(5, method.maxLocals - 2);
        return true;
    }

    private static int remapVenkrolLocal(int oldIndex) {
        if (oldIndex >= 7) return oldIndex - 2;
        return switch (oldIndex) {
            case 5 -> 3;
            case 6 -> 4;
            case 3 -> 2;
            default -> oldIndex;
        };
    }

    private static boolean hasParameterAnnotation(MethodNode method, int index, String descriptor) {
        return hasParameterAnnotation(method.visibleParameterAnnotations, index, descriptor)
                || hasParameterAnnotation(method.invisibleParameterAnnotations, index, descriptor);
    }

    private static boolean hasParameterAnnotation(List<AnnotationNode>[] parameters, int index, String descriptor) {
        if (parameters == null || index >= parameters.length || parameters[index] == null) return false;
        return parameters[index].stream().anyMatch(annotation -> annotation.desc.equals(descriptor));
    }

    @SuppressWarnings("unchecked")
    private static List<AnnotationNode>[] retainParameters(List<AnnotationNode>[] annotations, int... indexes) {
        if (annotations == null) return null;
        List<AnnotationNode>[] retained = new List[indexes.length];
        for (int i = 0; i < indexes.length; i++) if (indexes[i] < annotations.length) retained[i] = annotations[indexes[i]];
        return retained;
    }

    private static boolean adaptMovedKillsWrapOperation(MethodNode method, AnnotationNode wrapper, LegacyRemapper remapper) {
        if (wrapper == null || !method.desc.equals(OLD_MOVED_KILLS_HANDLER_DESC)
                || (method.signature != null && !method.signature.equals(OLD_MOVED_KILLS_HANDLER_SIGNATURE))) return false;
        String oldTarget = "L" + SRP_SAVE_DATA + ";setTotalKills" + remapper.mapMethodDesc(OLD_SET_TOTAL_KILLS);
        String newTarget = "L" + SRP_SAVE_DATA + ";setTotalKills" + remapper.mapMethodDesc(NEW_SET_TOTAL_KILLS);
        if (!containsTarget(wrapper, oldTarget) && !containsTarget(wrapper, newTarget)) {
            String rawOldTarget = "L" + SRP_SAVE_DATA + ";setTotalKills" + OLD_SET_TOTAL_KILLS;
            String rawNewTarget = "L" + SRP_SAVE_DATA + ";setTotalKills" + NEW_SET_TOTAL_KILLS;
            if (!containsTarget(wrapper, rawOldTarget) && !containsTarget(wrapper, rawNewTarget)) return false;
            oldTarget = rawOldTarget;
            newTarget = rawNewTarget;
        }

        int operationCalls = 0;
        int objectArrays = 0;
        int sixArgumentArrays = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(OPERATION) && call.name.equals("call")
                    && call.desc.equals("([Ljava/lang/Object;)Ljava/lang/Object;")) operationCalls++;
            if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.ANEWARRAY && type.desc.equals("java/lang/Object")) {
                objectArrays++;
                AbstractInsnNode size = previousCode(type.getPrevious());
                if (size instanceof IntInsnNode count && count.getOpcode() == Opcodes.BIPUSH && count.operand == 6) sixArgumentArrays++;
            }
        }
        if (operationCalls != 1 || objectArrays != 1 || sixArgumentArrays != 1) return false;

        if (containsTarget(wrapper, oldTarget)) replaceTarget(wrapper, oldTarget, newTarget);
        insertHandlerArgument(method, 6, Type.INT_TYPE, 6);
        method.signature = method.signature == null ? null : NEW_MOVED_KILLS_HANDLER_SIGNATURE;
        method.instructions.insert(new TypeInsnNode(Opcodes.NEW, SOURCE_ID_OPERATION));
        InsnList operationAdapter = new InsnList();
        operationAdapter.add(new InsnNode(Opcodes.DUP));
        operationAdapter.add(new VarInsnNode(Opcodes.ALOAD, 7));
        operationAdapter.add(new VarInsnNode(Opcodes.ILOAD, 6));
        operationAdapter.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, SOURCE_ID_OPERATION, "<init>",
                "(L" + OPERATION + ";I)V", false));
        operationAdapter.add(new VarInsnNode(Opcodes.ASTORE, 7));
        method.instructions.insert(method.instructions.getFirst(), operationAdapter);
        method.maxStack = Math.max(method.maxStack, 4);
        return true;
    }

    private static boolean mixinTargets(ClassNode node, String target) {
        AnnotationNode mixin = findClassAnnotation(node.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
        if (mixin == null) mixin = findClassAnnotation(node.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
        Object value = annotationValue(mixin, "value");
        if (value instanceof Type type) return type.getInternalName().equals(target);
        if (value instanceof Iterable<?> targets) {
            for (Object entry : targets) if (entry instanceof Type type && type.getInternalName().equals(target)) return true;
        }
        return false;
    }

    private static boolean selectsMethod(MethodNode method, String selector) {
        return selectsMethod(method.visibleAnnotations, selector) || selectsMethod(method.invisibleAnnotations, selector);
    }

    private static boolean selectsMethod(List<AnnotationNode> annotations, String selector) {
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations) {
            if (!isInjectorAnnotation(annotation.desc)) continue;
            Object value = annotationValue(annotation, "method");
            if (value instanceof String name && name.equals(selector)) return true;
            if (value instanceof Iterable<?> names) {
                for (Object name : names) if (selector.equals(name)) return true;
            }
        }
        return false;
    }

    private static int adaptBlockInfestAiTarget(Map<String, byte[]> classes, ClassInfo ai, LegacyRemapper remapper) {
        if (ai == null) return 0;
        if (!ai.hasMethod("updateTask", "()V") && !ai.hasMethod("func_75246_d", "()V")) return 0;
        // SRP 1.10.9 moves the call into BlockParasiteSpreading; gate on both exact peer entry points.

        String desc = remapper.mapMethodDesc(CAN_INFEST_DESC);
        String originalTarget = "L" + PARASITE_EVENT_WORLD + ";canInfestBlock" + CAN_INFEST_DESC;
        String mappedOldTarget = "L" + PARASITE_EVENT_WORLD + ";canInfestBlock" + desc;
        String currentTarget = "L" + BLOCK_PARASITE_SPREADING + ";canInfestBlock" + desc;

        ClassNode mixin = readClass(classes.get(ENTITY_AI_BLOCK_INFEST_MIXIN));
        if (mixin == null || !mixin.name.equals(ENTITY_AI_BLOCK_INFEST_MIXIN)) return 0;
        int changes = 0;
        for (MethodNode method : mixin.methods) {
            List<AnnotationNode> annotations = new ArrayList<>();
            if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
            if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
            for (AnnotationNode annotation : annotations) {
                if (isInjectorAnnotation(annotation.desc)
                        && (replaceTarget(annotation, originalTarget, currentTarget)
                                || replaceTarget(annotation, mappedOldTarget, currentTarget))) changes++;
            }
        }
        if (changes > 0) classes.put(ENTITY_AI_BLOCK_INFEST_MIXIN, writeClass(mixin));
        return changes > 0 ? 1 : 0;
    }

    private static boolean hasInstructionTarget(ClassInfo info, String kind, String selector) {
        String kindMarker = "\0" + kind + "\0";
        String selectorSuffix = "\0" + selector;
        for (var entry : info.instructionSites.entrySet()) {
            if (entry.getValue() > 0 && entry.getKey().contains(kindMarker) && entry.getKey().endsWith(selectorSuffix)) return true;
        }
        return false;
    }

    private static int adaptParasiteBaseHandlers(Map<String, byte[]> classes, ClassInfo parasiteBase, LegacyRemapper remapper) {
        if (parasiteBase == null) return 0;
        boolean currentApplyBonuses = parasiteBase.hasMethod("applyBonuses", remapper.mapMethodDesc(APPLY_BONUSES_DESC));
        boolean currentMinimumDamage = parasiteBase.hasMethod("attackEntityAsMobMinimum", remapper.mapMethodDesc(MINIMUM_DAMAGE_DESC));
        if (!currentApplyBonuses && !currentMinimumDamage) return 0;

        ClassNode mixin = readClass(classes.get(PARASITE_BASE_MIXIN));
        if (mixin == null || !mixin.name.equals(PARASITE_BASE_MIXIN)) return 0;
        int changes = 0;

        if (currentApplyBonuses) {
            MethodNode handler = findMethod(mixin, APPLY_BONUSES_HANDLER, OLD_APPLY_BONUSES_HANDLER_DESC);
            if (handler != null && !referencesLocals(handler, 1, 5)) {
                compactUnusedHandlerArguments(handler, 5, 2, NEW_APPLY_BONUSES_HANDLER_DESC, 3);
                changes++;
            }
        }

        if (currentMinimumDamage) {
            MethodNode blockDamage = findMethod(mixin, BLOCK_MINI_DAMAGE_HANDLER, OLD_BLOCK_MINI_DAMAGE_HANDLER_DESC);
            if (blockDamage != null && countLocalOrdinal(blockDamage, 1) == 1) {
                insertHandlerArgument(blockDamage, 1, Type.FLOAT_TYPE, 2);
                setLocalOrdinal(blockDamage, 1, 2);
                changes++;
            }

            MethodNode minimumDamage = findMethod(mixin, MIN_DAMAGE_HANDLER, OLD_MIN_DAMAGE_HANDLER_DESC);
            if (minimumDamage != null) {
                insertHandlerArgument(minimumDamage, 1, Type.FLOAT_TYPE, 2);
                changes++;
            }
        }

        if (changes > 0) classes.put(PARASITE_BASE_MIXIN, writeClass(mixin));
        return changes;
    }

    private static boolean referencesLocals(MethodNode method, int first, int end) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof VarInsnNode variable && variable.var >= first && variable.var < end) return true;
            if (instruction instanceof IincInsnNode increment && increment.var >= first && increment.var < end) return true;
        }
        return false;
    }

    private static void compactUnusedHandlerArguments(MethodNode method, int oldLocalsStart, int newLocalsStart,
            String newDescriptor, int callbackParameterIndex) {
        int shift = newLocalsStart - oldLocalsStart;
        method.desc = newDescriptor;
        method.signature = null;
        method.parameters = null;
        method.visibleParameterAnnotations = keepParameterAnnotation(method.visibleParameterAnnotations, callbackParameterIndex);
        method.invisibleParameterAnnotations = keepParameterAnnotation(method.invisibleParameterAnnotations, callbackParameterIndex);
        if (method.visibleParameterAnnotations != null) method.visibleAnnotableParameterCount = 1;
        if (method.invisibleParameterAnnotations != null) method.invisibleAnnotableParameterCount = 1;
        removeObsoleteArgumentLocals(method, oldLocalsStart);
        shiftMethodLocals(method, oldLocalsStart, shift);
    }

    @SuppressWarnings("unchecked")
    private static List<AnnotationNode>[] keepParameterAnnotation(List<AnnotationNode>[] annotations, int index) {
        if (annotations == null) return null;
        List<AnnotationNode>[] kept = new List[1];
        if (index < annotations.length) kept[0] = annotations[index];
        return kept;
    }

    private static void insertHandlerArgument(MethodNode method, int argumentIndex, Type argument, int localIndex) {
        Type[] oldArguments = Type.getArgumentTypes(method.desc);
        Type[] newArguments = new Type[oldArguments.length + 1];
        System.arraycopy(oldArguments, 0, newArguments, 0, argumentIndex);
        newArguments[argumentIndex] = argument;
        System.arraycopy(oldArguments, argumentIndex, newArguments, argumentIndex + 1,
                oldArguments.length - argumentIndex);
        method.desc = Type.getMethodDescriptor(Type.getReturnType(method.desc), newArguments);
        if (method.signature != null) method.signature = insertSignatureArgument(method.signature, argumentIndex, argument.getDescriptor().charAt(0));
        method.visibleParameterAnnotations = insertParameterAnnotation(method.visibleParameterAnnotations, argumentIndex);
        method.invisibleParameterAnnotations = insertParameterAnnotation(method.invisibleParameterAnnotations, argumentIndex);
        if (method.visibleParameterAnnotations != null) method.visibleAnnotableParameterCount++;
        if (method.invisibleParameterAnnotations != null) method.invisibleAnnotableParameterCount++;
        if (method.parameters != null) method.parameters.add(argumentIndex, new ParameterNode("legacyTargetAmount", 0));
        shiftMethodLocals(method, localIndex, argument.getSize());
    }

    private static String insertSignatureArgument(String signature, int argumentIndex, char descriptor) {
        SignatureWriter writer = new SignatureWriter() {
            private int parameterIndex;

            @Override
            public org.objectweb.asm.signature.SignatureVisitor visitParameterType() {
                if (parameterIndex++ == argumentIndex) super.visitParameterType().visitBaseType(descriptor);
                return super.visitParameterType();
            }
        };
        new SignatureReader(signature).accept(writer);
        return writer.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<AnnotationNode>[] insertParameterAnnotation(List<AnnotationNode>[] annotations, int index) {
        if (annotations == null) return null;
        List<AnnotationNode>[] updated = new List[annotations.length + 1];
        System.arraycopy(annotations, 0, updated, 0, index);
        System.arraycopy(annotations, index, updated, index + 1, annotations.length - index);
        return updated;
    }

    private static void shiftMethodLocals(MethodNode method, int firstLocal, int delta) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof VarInsnNode variable && variable.var >= firstLocal) variable.var += delta;
            else if (instruction instanceof IincInsnNode increment && increment.var >= firstLocal) increment.var += delta;
        }
        if (method.localVariables != null) {
            for (LocalVariableNode local : method.localVariables) if (local.index >= firstLocal) local.index += delta;
        }
        shiftLocalVariableAnnotations(method.visibleLocalVariableAnnotations, firstLocal, delta);
        shiftLocalVariableAnnotations(method.invisibleLocalVariableAnnotations, firstLocal, delta);
        method.maxLocals += delta;
    }

    private static void shiftLocalVariableAnnotations(List<LocalVariableAnnotationNode> annotations, int firstLocal, int delta) {
        if (annotations == null) return;
        for (LocalVariableAnnotationNode annotation : annotations) {
            for (int i = 0; i < annotation.index.size(); i++) {
                int index = annotation.index.get(i);
                if (index >= firstLocal) annotation.index.set(i, index + delta);
            }
        }
    }

    private static void removeObsoleteArgumentLocals(MethodNode method, int oldLocalsStart) {
        if (method.localVariables != null) method.localVariables.removeIf(local -> local.index > 0 && local.index < oldLocalsStart);
        removeObsoleteArgumentAnnotations(method.visibleLocalVariableAnnotations, oldLocalsStart);
        removeObsoleteArgumentAnnotations(method.invisibleLocalVariableAnnotations, oldLocalsStart);
    }

    private static void removeObsoleteArgumentAnnotations(List<LocalVariableAnnotationNode> annotations, int oldLocalsStart) {
        if (annotations == null) return;
        for (LocalVariableAnnotationNode annotation : annotations) {
            for (int i = annotation.index.size() - 1; i >= 0; i--) {
                if (annotation.index.get(i) > 0 && annotation.index.get(i) < oldLocalsStart) {
                    annotation.index.remove(i);
                    annotation.start.remove(i);
                    annotation.end.remove(i);
                }
            }
        }
        annotations.removeIf(annotation -> annotation.index.isEmpty());
    }

    private static int countLocalOrdinal(MethodNode method, int ordinal) {
        return countLocalOrdinal(method.visibleParameterAnnotations, ordinal)
                + countLocalOrdinal(method.invisibleParameterAnnotations, ordinal);
    }

    private static int countLocalOrdinal(List<AnnotationNode>[] parameters, int ordinal) {
        if (parameters == null) return 0;
        int count = 0;
        for (List<AnnotationNode> annotations : parameters) {
            if (annotations == null) continue;
            for (AnnotationNode annotation : annotations) {
                if (annotation.desc.equals(LOCAL_ANNOTATION_DESC) && Integer.valueOf(ordinal).equals(annotationValue(annotation, "ordinal"))) count++;
            }
        }
        return count;
    }

    private static void setLocalOrdinal(MethodNode method, int oldOrdinal, int newOrdinal) {
        setLocalOrdinal(method.visibleParameterAnnotations, oldOrdinal, newOrdinal);
        setLocalOrdinal(method.invisibleParameterAnnotations, oldOrdinal, newOrdinal);
    }

    private static void setLocalOrdinal(List<AnnotationNode>[] parameters, int oldOrdinal, int newOrdinal) {
        if (parameters == null) return;
        for (List<AnnotationNode> annotations : parameters) {
            if (annotations == null) continue;
            for (AnnotationNode annotation : annotations) {
                if (annotation.desc.equals(LOCAL_ANNOTATION_DESC) && Integer.valueOf(oldOrdinal).equals(annotationValue(annotation, "ordinal"))) {
                    setAnnotationValue(annotation, "ordinal", newOrdinal);
                }
            }
        }
    }

    private static int adaptBloomGore(Map<String, byte[]> classes, Map<String, ClassInfo> peers) {
        ClassInfo blocks = peers.get(SRP_BLOCKS);
        ClassInfo goreEnum = peers.get(GORE_ENUM);
        if (!hasCurrentSplitGoreApi(blocks, goreEnum)) return 0;
        ClassNode node = readClass(classes.get(PARASITE_PLAYER));
        if (node == null || !node.name.equals(PARASITE_PLAYER) || !hasField(node, "bloom", "I")
                || findMethod(node, "getGoreFlat", "()" + GORE_ENUM_DESC) == null
                || findMethod(node, "getGoreBig", "()" + GORE_ENUM_DESC) == null
                || findMethod(node, GORE_BLOCK_HELPER, "()" + GORE_DESC) != null) return 0;

        MethodNode spawn = findMethod(node, "spawnGore",
                "(Lnet/minecraft/entity/player/EntityPlayer;L" + WORLD + ";)V");
        MethodNode flat = findMethod(node, "getGoreFlat", "()" + GORE_ENUM_DESC);
        MethodNode big = findMethod(node, "getGoreBig", "()" + GORE_ENUM_DESC);
        if (spawn == null || countField(spawn, SRP_BLOCKS, "Gore", GORE_DESC) != 2
                || countEnumFields(flat, "FLAT") != 4 || countEnumFields(big, "BIG") != 4
                || countCalls(spawn, "getGoreFlat", "()" + GORE_ENUM_DESC) != 1
                || countCalls(spawn, "getGoreBig", "()" + GORE_ENUM_DESC) != 1) return 0;

        int enumReferences = 0;
        for (MethodNode method : node.methods) {
            for (var instruction : method.instructions) {
                if (!(instruction instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETSTATIC
                        || !field.owner.equals(GORE_ENUM) || !field.desc.equals(GORE_ENUM_DESC)) continue;
                String size = legacyGoreSize(field.name);
                if (size == null || !goreEnum.hasField(size, GORE_ENUM_DESC)) return 0;
                enumReferences++;
            }
        }
        if (enumReferences != 8) return 0;

        int changes = 0;
        for (MethodNode method : node.methods) {
            for (var instruction : method.instructions) {
                if (!(instruction instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETSTATIC
                        || !field.owner.equals(GORE_ENUM) || !field.desc.equals(GORE_ENUM_DESC)) continue;
                field.name = legacyGoreSize(field.name);
                changes++;
            }
        }
        for (var instruction = spawn.instructions.getFirst(); instruction != null; ) {
            var next = instruction.getNext();
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                    && field.owner.equals(SRP_BLOCKS) && field.name.equals("Gore") && field.desc.equals(GORE_DESC)) {
                spawn.instructions.insertBefore(field, new VarInsnNode(Opcodes.ALOAD, 0));
                spawn.instructions.set(field, new MethodInsnNode(Opcodes.INVOKESPECIAL, PARASITE_PLAYER,
                        GORE_BLOCK_HELPER, "()" + GORE_DESC, false));
                changes++;
            }
            instruction = next;
        }
        node.methods.add(goreBlockHelper(blocks));
        classes.put(PARASITE_PLAYER, writeClass(node));
        return changes + 1;
    }

    private static boolean hasCurrentSplitGoreApi(ClassInfo blocks, ClassInfo goreEnum) {
        if (blocks == null || goreEnum == null || blocks.hasField("Gore", GORE_DESC)) return false;
        for (String family : GORE_FAMILIES) {
            String field = switch (family) {
                case "SIM" -> "goreSim";
                case "PRI" -> "gorePri";
                case "ADA" -> "goreAda";
                case "PURE" -> "gorePur";
                default -> null;
            };
            if (field == null || !blocks.hasField(field, GORE_DESC)) return false;
        }
        return goreEnum.hasField("FLAT", GORE_ENUM_DESC) && goreEnum.hasField("BIG", GORE_ENUM_DESC);
    }

    private static String legacyGoreSize(String legacyName) {
        int separator = legacyName.lastIndexOf('_');
        if (separator < 0) return null;
        String family = legacyName.substring(0, separator);
        if (!java.util.Arrays.asList(GORE_FAMILIES).contains(family)) return null;
        String size = legacyName.substring(separator + 1);
        return size.equals("FLAT") || size.equals("BIG") ? size : null;
    }

    private static int countEnumFields(MethodNode method, String suffix) {
        int count = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                    && field.owner.equals(GORE_ENUM) && field.desc.equals(GORE_ENUM_DESC)
                    && field.name.endsWith("_" + suffix) && suffix.equals(legacyGoreSize(field.name))) count++;
        }
        return count;
    }

    private static int countField(MethodNode method, String owner, String name, String descriptor) {
        int count = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                    && field.owner.equals(owner) && field.name.equals(name) && field.desc.equals(descriptor)) count++;
        }
        return count;
    }

    private static int countCalls(MethodNode method, String name, String descriptor) {
        int count = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals(name) && call.desc.equals(descriptor)) count++;
        }
        return count;
    }

    private static boolean hasField(ClassNode node, String name, String descriptor) {
        return node.fields.stream().anyMatch(field -> field.name.equals(name) && field.desc.equals(descriptor));
    }

    private static MethodNode goreBlockHelper(ClassInfo blocks) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC,
                GORE_BLOCK_HELPER, "()" + GORE_DESC, null, null);
        LabelNode primitive = new LabelNode();
        LabelNode adapted = new LabelNode();
        LabelNode pure = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, PARASITE_PLAYER, "bloom", "I"));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPGT, primitive));
        addGoreBlockReturn(method, blocks, "SIM");
        method.instructions.add(primitive);
        method.instructions.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, PARASITE_PLAYER, "bloom", "I"));
        method.instructions.add(new InsnNode(Opcodes.ICONST_2));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPNE, adapted));
        addGoreBlockReturn(method, blocks, "PRI");
        method.instructions.add(adapted);
        method.instructions.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, PARASITE_PLAYER, "bloom", "I"));
        method.instructions.add(new InsnNode(Opcodes.ICONST_3));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPNE, pure));
        addGoreBlockReturn(method, blocks, "ADA");
        method.instructions.add(pure);
        method.instructions.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        addGoreBlockReturn(method, blocks, "PURE");
        method.maxStack = 2;
        method.maxLocals = 1;
        return method;
    }

    private static void addGoreBlockReturn(MethodNode method, ClassInfo blocks, String family) {
        String field = switch (family) {
            case "SIM" -> "goreSim";
            case "PRI" -> "gorePri";
            case "ADA" -> "goreAda";
            case "PURE" -> "gorePur";
            default -> throw new IllegalArgumentException("Unknown gore family " + family);
        };
        if (!blocks.hasField(field, GORE_DESC)) throw new IllegalArgumentException("Missing current SRP gore block " + field);
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, SRP_BLOCKS, field, GORE_DESC));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
    }

    private static int adaptPotionMixins(Map<String, byte[]> classes, Map<String, byte[]> resources,
            Map<String, ClassInfo> peers, LegacyRemapper remapper) {
        String config = findMainMixinConfig(resources);
        if (config == null) return 0;

        int changes = 0;
        List<String> addedMixins = new ArrayList<>();
        List<String> addedClientMixins = new ArrayList<>();
        ClassInfo coth = peers.get(POTION_COTH);
        ClassInfo saveData = peers.get(SRP_SAVE_DATA);
        boolean hasSourceIdApi = saveData != null
                && !saveData.hasMethod("setTotalKills", remapper.mapMethodDesc(OLD_SET_TOTAL_KILLS))
                && saveData.hasMethod("setTotalKills", remapper.mapMethodDesc(NEW_SET_TOTAL_KILLS));
        if (hasSourceIdApi && coth != null
                && coth.hasMethod("effectCOTHTransform", remapper.mapMethodDesc(COTH_TRANSFORM_DESC))
                && coth.hasMethod("effectCOTHextendDuration", remapper.mapMethodDesc(COTH_EXTEND_DESC))) {
            int moved = splitCothMixin(classes);
            if (moved > 0) {
                changes += moved;
                addedMixins.add(COTH_MIXIN);
            }
        }

        ClassInfo needler = peers.get(POTION_NEEDLER);
        if (needler != null && needler.hasMethod("effectNeedler", remapper.mapMethodDesc(NEEDLER_EFFECT_DESC))) {
            int moved = splitNeedlerMixin(classes);
            if (moved > 0) {
                changes += moved;
                addedMixins.add(NEEDLER_MIXIN);
            }
        }

        ClassInfo effectBase = peers.get(SRP_EFFECT_BASE);
        if (effectBase != null && effectBase.hasMethod("InfectNearby", remapper.mapMethodDesc(INFECT_NEARBY_DESC))) {
            String peerSite = remapper.mapString(INFECT_TARGET);
            int siteCount = effectBase.instructionSiteCount("InfectNearby" + remapper.mapMethodDesc(INFECT_NEARBY_DESC),
                    "INVOKE", peerSite, -1);
            if (siteCount == 1) changes += lowerInfectNearbyRequire(classes);
        }

        ClassInfo particlePacket = peers.get(PARTICLE_PACKET);
        if (SrpCotesiaParticleCompat.hasCurrentParticleApi(peers) && particlePacket != null
                && particlePacket.hasMethod(SrpCotesiaParticleCompat.PARTICLE_ID_GETTER, SrpCotesiaParticleCompat.PARTICLE_ID_DESC)
                && effectBase != null && !effectBase.hasMethod("spawnParticles", remapper.mapMethodDesc(PARTICLE_SPAWN_OLD_TARGET_DESC))) {
            int moved = splitParticleMixin(classes);
            if (moved > 0) {
                changes += moved;
                addedClientMixins.add(PARTICLE_MIXIN);
            }
        }

        if (!addedMixins.isEmpty()) appendMixinEntries(resources, config, addedMixins);
        if (!addedClientMixins.isEmpty()) appendMixinEntries(resources, config, "client", addedClientMixins);
        return changes;
    }

    private static String findMainMixinConfig(Map<String, byte[]> resources) {
        return findMixinConfig(resources, "SRPEffectBaseMixin");
    }

    private static String findMixinConfig(Map<String, byte[]> resources, String mixinName) {
        for (var entry : resources.entrySet()) {
            String name = entry.getKey();
            if (!name.endsWith(".json") || name.startsWith("assets/") || name.startsWith("data/")
                    || name.startsWith("META-INF/") || entry.getValue().length > 512 * 1024) continue;
            try {
                JsonElement parsed = GSON.fromJson(new String(entry.getValue(), StandardCharsets.UTF_8), JsonElement.class);
                if (parsed == null || !parsed.isJsonObject()) continue;
                JsonObject cfg = parsed.getAsJsonObject();
                if (!cfg.has("package") || !MAIN_MIXIN_CONFIG_PACKAGE.equals(cfg.get("package").getAsString())
                        || !cfg.has("mixins") || !cfg.getAsJsonArray("mixins").isJsonArray()) continue;
                for (JsonElement mixin : cfg.getAsJsonArray("mixins")) {
                    if (mixin.isJsonPrimitive() && mixinName.equals(mixin.getAsString())) return name;
                }
            } catch (RuntimeException ignored) {
                // A malformed unrelated mixin config is reported by the normal config pass.
            }
        }
        return null;
    }

    private static void appendMixinEntries(Map<String, byte[]> resources, String configName, List<String> mixins) {
        appendMixinEntries(resources, configName, "mixins", mixins);
    }

    private static void appendMixinEntries(Map<String, byte[]> resources, String configName, String section, List<String> mixins) {
        JsonObject cfg = GSON.fromJson(new String(resources.get(configName), StandardCharsets.UTF_8), JsonObject.class);
        JsonArray entries = cfg.has(section) && cfg.get(section).isJsonArray() ? cfg.getAsJsonArray(section) : new JsonArray();
        cfg.add(section, entries);
        String packagePrefix = MAIN_MIXIN_CONFIG_PACKAGE.replace('.', '/') + "/";
        for (String mixin : mixins) {
            String relative = mixin.substring(packagePrefix.length()).replace('/', '.');
            boolean present = false;
            for (JsonElement entry : entries) {
                if (entry.isJsonPrimitive() && relative.equals(entry.getAsString())) {
                    present = true;
                    break;
                }
            }
            if (!present) entries.add(relative);
        }
        resources.put(configName, GSON.toJson(cfg).getBytes(StandardCharsets.UTF_8));
    }

    private static int splitCothMixin(Map<String, byte[]> classes) {
        if (classes.containsKey(COTH_MIXIN)) return 0;
        ClassNode source = readClass(classes.get(SRP_EFFECT_MIXIN));
        if (source == null || !source.name.equals(SRP_EFFECT_MIXIN)) return 0;
        MethodNode spawn = findMethod(source, COTH_SPAWN_HANDLER, OLD_COTH_SPAWN_DESC);
        MethodNode kills = findMethod(source, COTH_KILLS_HANDLER, OLD_COTH_KILLS_DESC);
        AnnotationNode inject = spawn == null ? null : findMethodAnnotation(spawn, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        AnnotationNode wrap = kills == null ? null : findMethodAnnotation(kills, "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");
        if (inject == null || wrap == null || !containsTarget(wrap, OLD_DESPAWN_TARGET)) return 0;

        ClassNode split = copyMixinTarget(source, COTH_MIXIN, POTION_COTH);
        MethodNode oldSpawn = copyMethod(spawn);
        oldSpawn.name = spawn.name + "$legacy";
        removeInjectorAnnotations(oldSpawn);
        MethodNode newSpawn = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE, spawn.name, NEW_COTH_SPAWN_DESC, null, null);
        copyInjectorAnnotations(spawn, newSpawn);
        AnnotationNode newInject = findMethodAnnotation(newSpawn, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        setStringList(newInject, "method", List.of("effectCOTHTransform"));
        newSpawn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        newSpawn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        newSpawn.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        newSpawn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 6));
        newSpawn.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, COTH_MIXIN, oldSpawn.name, OLD_COTH_SPAWN_DESC, false));
        newSpawn.instructions.add(new InsnNode(Opcodes.RETURN));
        newSpawn.maxStack = 4;
        newSpawn.maxLocals = 7;

        MethodNode oldKills = copyMethod(kills);
        oldKills.name = kills.name + "$legacy";
        removeInjectorAnnotations(oldKills);
        MethodNode newKills = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE, kills.name, NEW_COTH_KILLS_DESC, null, null);
        copyInjectorAnnotations(kills, newKills);
        AnnotationNode newWrap = findMethodAnnotation(newKills, "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");
        setStringList(newWrap, "method", List.of("effectCOTHextendDuration"));
        replaceTarget(newWrap, OLD_DESPAWN_TARGET, NEW_DESPAWN_TARGET);
        addSourceIdForwardingBody(newKills, COTH_MIXIN, oldKills.name);

        split.methods.add(oldSpawn);
        split.methods.add(newSpawn);
        split.methods.add(oldKills);
        split.methods.add(newKills);
        source.methods.remove(spawn);
        source.methods.remove(kills);
        classes.put(SRP_EFFECT_MIXIN, writeClass(source));
        classes.put(COTH_MIXIN, writeClass(split));
        classes.putIfAbsent(SOURCE_ID_OPERATION, sourceIdOperationClass());
        return 2;
    }

    private static void addSourceIdForwardingBody(MethodNode method, String owner, String legacyHandler) {
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 5));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 6));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, SOURCE_ID_OPERATION));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 8));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 7));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, SOURCE_ID_OPERATION, "<init>",
                "(L" + OPERATION + ";I)V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 11));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 12));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, owner, legacyHandler, OLD_COTH_KILLS_DESC, false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 11;
        method.maxLocals = 16;
    }

    private static byte[] sourceIdOperationClass() {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER;
        node.name = SOURCE_ID_OPERATION;
        node.superName = "java/lang/Object";
        node.interfaces.add(OPERATION);
        node.fields.add(new org.objectweb.asm.tree.FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "delegate", "L" + OPERATION + ";", null, null));
        node.fields.add(new org.objectweb.asm.tree.FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "sourceId", "I", null, null));

        MethodNode constructor = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "<init>", "(L" + OPERATION + ";I)V", null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        constructor.instructions.add(new org.objectweb.asm.tree.FieldInsnNode(Opcodes.PUTFIELD, SOURCE_ID_OPERATION, "delegate", "L" + OPERATION + ";"));
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        constructor.instructions.add(new org.objectweb.asm.tree.FieldInsnNode(Opcodes.PUTFIELD, SOURCE_ID_OPERATION, "sourceId", "I"));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        constructor.maxStack = 2;
        constructor.maxLocals = 3;
        node.methods.add(constructor);

        MethodNode call = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_VARARGS,
                "call", "([Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        call.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        call.instructions.add(new InsnNode(Opcodes.ARRAYLENGTH));
        call.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        call.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        call.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        call.instructions.add(new InsnNode(Opcodes.ICONST_1));
        call.instructions.add(new InsnNode(Opcodes.IADD));
        call.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Arrays", "copyOf",
                "([Ljava/lang/Object;I)[Ljava/lang/Object;", false));
        call.instructions.add(new VarInsnNode(Opcodes.ASTORE, 3));
        call.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        call.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        call.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        call.instructions.add(new org.objectweb.asm.tree.FieldInsnNode(Opcodes.GETFIELD, SOURCE_ID_OPERATION, "sourceId", "I"));
        call.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
        call.instructions.add(new InsnNode(Opcodes.AASTORE));
        call.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        call.instructions.add(new org.objectweb.asm.tree.FieldInsnNode(Opcodes.GETFIELD, SOURCE_ID_OPERATION, "delegate", "L" + OPERATION + ";"));
        call.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        call.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, OPERATION, "call", "([Ljava/lang/Object;)Ljava/lang/Object;", true));
        call.instructions.add(new InsnNode(Opcodes.ARETURN));
        call.maxStack = 4;
        call.maxLocals = 4;
        node.methods.add(call);
        return writeClass(node);
    }

    private static int splitNeedlerMixin(Map<String, byte[]> classes) {
        if (classes.containsKey(NEEDLER_MIXIN)) return 0;
        ClassNode source = readClass(classes.get(SRP_EFFECT_MIXIN));
        if (source == null || !source.name.equals(SRP_EFFECT_MIXIN)) return 0;
        MethodNode handler = findMethod(source, NEEDLER_HANDLER, NEEDLER_HANDLER_DESC);
        if (handler == null || findMethodAnnotation(handler, "Lorg/spongepowered/asm/mixin/injection/Inject;") == null) return 0;

        ClassNode split = copyMixinTarget(source, NEEDLER_MIXIN, POTION_NEEDLER);
        split.methods.add(copyMethod(handler));
        source.methods.remove(handler);
        classes.put(SRP_EFFECT_MIXIN, writeClass(source));
        classes.put(NEEDLER_MIXIN, writeClass(split));
        return 1;
    }

    private static int splitParticleMixin(Map<String, byte[]> classes) {
        if (classes.containsKey(PARTICLE_MIXIN)) return 0;
        ClassNode source = readClass(classes.get(SRP_EFFECT_MIXIN));
        if (source == null || !source.name.equals(SRP_EFFECT_MIXIN) || !mixinTargets(source, SRP_EFFECT_BASE)) return 0;
        MethodNode oldInject = findMethod(source, PARTICLE_INJECT_HANDLER, PARTICLE_INJECT_OLD_DESC);
        MethodNode oldWrap = findMethod(source, PARTICLE_INJECT_HANDLER, PARTICLE_WRAP_OLD_DESC);
        AnnotationNode inject = oldInject == null ? null : findMethodAnnotation(oldInject, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        AnnotationNode wrap = oldWrap == null ? null
                : findMethodAnnotation(oldWrap, "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");
        Object injectMethods = inject == null ? null : annotationValue(inject, "method");
        Object wrapMethods = wrap == null ? null : annotationValue(wrap, "method");
        if (!(injectMethods instanceof List<?> selectedInject) || !selectedInject.contains("spawnParticles")
                || !(wrapMethods instanceof List<?> selectedWrap) || !selectedWrap.contains("spawnParticles")
                || !containsTarget(wrap, "Ljava/util/Random;nextFloat()F")) return 0;

        ClassNode split = copyMixinTarget(source, PARTICLE_MIXIN, PARTICLE_HANDLER);
        split.superName = "java/lang/Object";
        split.methods.add(adaptParticleInject(oldInject));
        split.methods.add(adaptParticleWrap(oldWrap));
        source.methods.remove(oldInject);
        source.methods.remove(oldWrap);
        classes.put(SRP_EFFECT_MIXIN, writeClass(source));
        classes.put(PARTICLE_MIXIN, writeClassWithFrames(split));
        return 2;
    }

    private static MethodNode adaptParticleInject(MethodNode source) {
        MethodNode method = copyMethod(source);
        method.desc = PARTICLE_INJECT_NEW_DESC;
        method.signature = null;
        method.visibleAnnotations = copyAnnotations(source.visibleAnnotations, true);
        method.invisibleAnnotations = copyAnnotations(source.invisibleAnnotations, true);
        method.localVariables = null;
        method.visibleLocalVariableAnnotations = null;
        method.invisibleLocalVariableAnnotations = null;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof VarInsnNode variable) {
                if (variable.getOpcode() == Opcodes.ALOAD && variable.var == 3) variable.var = 9;
                else if (variable.getOpcode() == Opcodes.ALOAD && variable.var == 4) variable.var = 7;
                else if (variable.var >= 5) variable.var += 5;
            } else if (instruction instanceof IincInsnNode increment && increment.var >= 5) {
                increment.var += 5;
            }
        }
        InsnList resolveEntity = new InsnList();
        LabelNode noWorld = new LabelNode();
        LabelNode resolved = new LabelNode();
        resolveEntity.add(new VarInsnNode(Opcodes.ALOAD, 3));
        resolveEntity.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, PARTICLE_PACKET,
                SrpCotesiaParticleCompat.PARTICLE_ID_GETTER, SrpCotesiaParticleCompat.PARTICLE_ID_DESC, false));
        resolveEntity.add(new VarInsnNode(Opcodes.ISTORE, 8));
        resolveEntity.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MINECRAFT, "func_71410_x", "()L" + MINECRAFT + ";", false));
        resolveEntity.add(new FieldInsnNode(Opcodes.GETFIELD, MINECRAFT, "field_71441_e", "L" + WORLD_CLIENT + ";"));
        resolveEntity.add(new InsnNode(Opcodes.DUP));
        resolveEntity.add(new JumpInsnNode(Opcodes.IFNULL, noWorld));
        resolveEntity.add(new VarInsnNode(Opcodes.ILOAD, 8));
        resolveEntity.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WORLD, "getEntityByID",
                "(I)L" + ENTITY + ";", false));
        resolveEntity.add(new VarInsnNode(Opcodes.ASTORE, 9));
        resolveEntity.add(new JumpInsnNode(Opcodes.GOTO, resolved));
        resolveEntity.add(noWorld);
        resolveEntity.add(new InsnNode(Opcodes.POP));
        resolveEntity.add(new InsnNode(Opcodes.ACONST_NULL));
        resolveEntity.add(new VarInsnNode(Opcodes.ASTORE, 9));
        resolveEntity.add(resolved);
        method.instructions.insert(resolveEntity);
        method.maxLocals = 11;
        return method;
    }

    private static MethodNode adaptParticleWrap(MethodNode source) {
        MethodNode method = new MethodNode(Opcodes.ASM9, source.access, source.name, PARTICLE_WRAP_NEW_DESC,
                "(Ljava/util/Random;L" + OPERATION + "<Ljava/lang/Float;>;L" + PARTICLE_ENUM
                        + ";Ljava/util/Random;L" + PARTICLE_PACKET + ";III)F", null);
        copyInjectorAnnotations(source, method);
        LabelNode original = new LabelNode();
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MINECRAFT, "func_71410_x", "()L" + MINECRAFT + ";", false));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, MINECRAFT, "field_71439_g", "L" + ENTITY_PLAYER_SP + ";"));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 9));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 9));
        method.instructions.add(new JumpInsnNode(Opcodes.IFNULL, original));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 9));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY, "func_145782_y", "()I", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 5));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, PARTICLE_PACKET,
                SrpCotesiaParticleCompat.PARTICLE_ID_GETTER, SrpCotesiaParticleCompat.PARTICLE_ID_DESC, false));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPNE, original));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 9));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PARTICLE_INTERACTIONS, "isParasite",
                "(L" + ENTITY_LIVING_BASE + ";)Z", false));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, original));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, PARTICLE_ENUM, "GCLOUD", "L" + PARTICLE_ENUM + ";"));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ACMPNE, original));
        addParticleOperationCall(method, true);
        method.instructions.add(original);
        addParticleOperationCall(method, false);
        method.maxStack = 5;
        method.maxLocals = 10;
        return method;
    }

    private static void addParticleOperationCall(MethodNode method, boolean scale) {
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.AASTORE));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, OPERATION, "call",
                "([Ljava/lang/Object;)Ljava/lang/Object;", true));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Float"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false));
        if (scale) {
            method.instructions.add(new org.objectweb.asm.tree.LdcInsnNode(0.15f));
            method.instructions.add(new InsnNode(Opcodes.FMUL));
        }
        method.instructions.add(new InsnNode(Opcodes.FRETURN));
    }

    private static int lowerInfectNearbyRequire(Map<String, byte[]> classes) {
        byte[] bytes = classes.get(SRP_EFFECT_MIXIN);
        if (bytes == null) return 0;
        ClassNode node = readClass(bytes);
        if (node == null || !node.name.equals(SRP_EFFECT_MIXIN)) return 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals(INFECT_HANDLER)) continue;
            AnnotationNode wrap = findMethodAnnotation(method, "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");
            if (wrap == null || !containsTarget(wrap, INFECT_TARGET) || !Integer.valueOf(2).equals(annotationValue(wrap, "require"))) continue;
            setAnnotationValue(wrap, "require", 1);
            classes.put(SRP_EFFECT_MIXIN, writeClass(node));
            return 1;
        }
        return 0;
    }

    private static ClassNode copyMixinTarget(ClassNode source, String name, String target) {
        ClassNode copy = new ClassNode(Opcodes.ASM9);
        source.accept(copy);
        copy.name = name;
        copy.fields.clear();
        copy.methods.clear();
        setMixinTarget(copy, target);
        return copy;
    }

    private static boolean setMixinTarget(ClassNode node, String target) {
        AnnotationNode mixin = findClassAnnotation(node.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
        if (mixin == null) mixin = findClassAnnotation(node.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
        if (mixin == null) return false;
        setAnnotationValue(mixin, "value", List.of(Type.getObjectType(target)));
        removeAnnotationValue(mixin, "targets");
        return true;
    }

    private static MethodNode findMethod(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) if (method.name.equals(name) && method.desc.equals(desc)) return method;
        return null;
    }

    private static MethodNode copyMethod(MethodNode source) {
        MethodNode copy = new MethodNode(Opcodes.ASM9, source.access, source.name, source.desc, source.signature,
                source.exceptions == null ? null : source.exceptions.toArray(String[]::new));
        source.accept(copy);
        return copy;
    }

    private static void copyInjectorAnnotations(MethodNode source, MethodNode target) {
        target.visibleAnnotations = copyAnnotations(source.visibleAnnotations, true);
        target.invisibleAnnotations = copyAnnotations(source.invisibleAnnotations, true);
    }

    private static List<AnnotationNode> copyAnnotations(List<AnnotationNode> source, boolean injectorsOnly) {
        if (source == null) return null;
        List<AnnotationNode> copies = new ArrayList<>();
        for (AnnotationNode annotation : source) {
            if (injectorsOnly && !isInjectorAnnotation(annotation.desc)) continue;
            AnnotationNode copy = new AnnotationNode(annotation.desc);
            annotation.accept(copy);
            copies.add(copy);
        }
        return copies;
    }

    private static void removeInjectorAnnotations(MethodNode method) {
        if (method.visibleAnnotations != null) method.visibleAnnotations.removeIf(annotation -> isInjectorAnnotation(annotation.desc));
        if (method.invisibleAnnotations != null) method.invisibleAnnotations.removeIf(annotation -> isInjectorAnnotation(annotation.desc));
    }

    private static boolean isInjectorAnnotation(String descriptor) {
        return descriptor.startsWith("Lorg/spongepowered/asm/mixin/injection/")
                || descriptor.startsWith("Lcom/llamalad7/mixinextras/injector/");
    }

    private static AnnotationNode findClassAnnotation(List<AnnotationNode> annotations, String descriptor) {
        if (annotations == null) return null;
        for (AnnotationNode annotation : annotations) if (annotation.desc.equals(descriptor)) return annotation;
        return null;
    }

    private static AnnotationNode findMethodAnnotation(MethodNode method, String descriptor) {
        AnnotationNode annotation = findClassAnnotation(method.visibleAnnotations, descriptor);
        return annotation == null ? findClassAnnotation(method.invisibleAnnotations, descriptor) : annotation;
    }

    private static Object annotationValue(AnnotationNode annotation, String key) {
        if (annotation.values == null) return null;
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }

    private static void setAnnotationValue(AnnotationNode annotation, String key, Object value) {
        if (annotation.values == null) annotation.values = new ArrayList<>();
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) {
                annotation.values.set(i + 1, value);
                return;
            }
        }
        annotation.values.add(key);
        annotation.values.add(value);
    }

    private static void removeAnnotationValue(AnnotationNode annotation, String key) {
        if (annotation.values == null) return;
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) {
                annotation.values.remove(i + 1);
                annotation.values.remove(i);
                return;
            }
        }
    }

    private static void setStringList(AnnotationNode annotation, String key, List<String> values) {
        setAnnotationValue(annotation, key, new ArrayList<>(values));
    }

    private static ClassNode readClass(byte[] bytes) {
        if (bytes == null) return null;
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] writeClass(ClassNode node) {
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] writeClassWithFrames(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                return "java/lang/Object";
            }
        };
        node.accept(writer);
        return writer.toByteArray();
    }

    private static int adaptSaveDataCalls(Map<String, byte[]> classes, ClassInfo saveData, LegacyRemapper remapper) {
        if (saveData == null) return 0;
        String peerOldGet = remapper.mapMethodDesc(OLD_GET);
        String peerNewGet = remapper.mapMethodDesc(NEW_GET);
        String peerOldCooldown = remapper.mapMethodDesc(OLD_SET_COOLDOWN);
        String peerNewCooldown = remapper.mapMethodDesc(NEW_SET_COOLDOWN);
        String peerOldPhase = remapper.mapMethodDesc(OLD_SET_PHASE);
        String peerNewPhase = remapper.mapMethodDesc(NEW_SET_PHASE);
        String peerOldKills = remapper.mapMethodDesc(OLD_SET_TOTAL_KILLS);
        String peerNewKills = remapper.mapMethodDesc(NEW_SET_TOTAL_KILLS);
        boolean adaptGet = !saveData.hasMethod("get", peerOldGet) && saveData.hasMethod("get", peerNewGet);
        boolean adaptCooldown = !saveData.hasMethod("setCooldown", peerOldCooldown)
                && saveData.hasMethod("setCooldown", peerNewCooldown);
        boolean adaptPhase = !saveData.hasMethod("setEvolutionPhase", peerOldPhase)
                && saveData.hasMethod("setEvolutionPhase", peerNewPhase);
        boolean adaptKills = !saveData.hasMethod("setTotalKills", peerOldKills)
                && (saveData.hasMethod("setTotalKills", peerNewKills) || saveData.hasMethod("setTotalKills", NEW_SET_TOTAL_KILLS));
        if (!adaptGet && !adaptCooldown && !adaptPhase && !adaptKills) return 0;

        int changes = adaptKills ? adaptDespawnRedirect(classes) + adaptMergeWrapOperation(classes) : 0;
        for (var entry : classes.entrySet()) {
            ClassNode node = new ClassNode();
            new ClassReader(entry.getValue()).accept(node, 0);
            int classChanges = 0;
            for (MethodNode method : node.methods) {
                for (var instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                    if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals(SRP_SAVE_DATA)) continue;
                    if (adaptGet && call.getOpcode() == Opcodes.INVOKESTATIC && call.name.equals("get") && call.desc.equals(OLD_GET)) {
                        method.instructions.insertBefore(call, new InsnNode(Opcodes.DUP));
                        method.instructions.insertBefore(call, new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/world/World", "mcc$get$provider",
                                "(Lnet/minecraft/world/World;)Lnet/minecraft/world/WorldProvider;", false));
                        method.instructions.insertBefore(call, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/WorldProvider", "getDimension", "()I", false));
                        call.desc = NEW_GET;
                        method.maxStack = Math.max(method.maxStack, 3);
                        classChanges++;
                    } else if (adaptCooldown && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                            && call.name.equals("setCooldown") && call.desc.equals(OLD_SET_COOLDOWN)) {
                        method.instructions.insertBefore(call, new InsnNode(Opcodes.ICONST_0));
                        call.desc = NEW_SET_COOLDOWN;
                        method.maxStack = Math.max(method.maxStack, 5);
                        classChanges++;
                    } else if (adaptPhase && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                            && call.name.equals("setEvolutionPhase") && call.desc.equals(OLD_SET_PHASE)) {
                        method.instructions.insertBefore(call, new InsnNode(Opcodes.POP));
                        call.desc = NEW_SET_PHASE;
                        classChanges++;
                    } else if (adaptKills && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                            && call.name.equals("setTotalKills") && call.desc.equals(OLD_SET_TOTAL_KILLS)) {
                        method.instructions.insertBefore(call, new InsnNode(Opcodes.ICONST_0));
                        call.desc = NEW_SET_TOTAL_KILLS;
                        method.maxStack = Math.max(method.maxStack, 7);
                        classChanges++;
                    }
                }
            }
            if (classChanges > 0) {
                ClassWriter writer = new ClassWriter(0);
                node.accept(writer);
                entry.setValue(writer.toByteArray());
                changes += classChanges;
            }
        }
        return changes;
    }

    private static int adaptDespawnRedirect(Map<String, byte[]> classes) {
        byte[] bytes = classes.get(DESPAWN_MIXIN);
        if (bytes == null) return 0;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        if (!node.name.equals(DESPAWN_MIXIN)) return 0;

        int changes = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals(DESPAWN_HANDLER) || !method.desc.equals(OLD_DESPAWN_REDIRECT)) continue;
            AnnotationNode redirect = findRedirect(method);
            if (redirect == null || !containsTarget(redirect, OLD_DESPAWN_TARGET)) continue;
            MethodInsnNode targetCall = null;
            for (var instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(SRP_SAVE_DATA)
                        && call.name.equals("setTotalKills") && call.desc.equals(OLD_SET_TOTAL_KILLS)) {
                    targetCall = call;
                    break;
                }
            }
            if (targetCall == null) continue;
            replaceTarget(redirect, OLD_DESPAWN_TARGET, NEW_DESPAWN_TARGET);
            method.instructions.insertBefore(targetCall, new VarInsnNode(Opcodes.ILOAD, 7));
            targetCall.desc = NEW_SET_TOTAL_KILLS;
            method.desc = NEW_DESPAWN_REDIRECT;
            method.maxLocals = Math.max(method.maxLocals, 8);
            method.maxStack = Math.max(method.maxStack, 7);
            changes++;
        }
        if (changes > 0) {
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            classes.put(DESPAWN_MIXIN, writer.toByteArray());
        }
        return changes;
    }

    private static int adaptMergeWrapOperation(Map<String, byte[]> classes) {
        ClassNode node = readClass(classes.get(MERGE_MIXIN));
        if (node == null || !node.name.equals(MERGE_MIXIN)) return 0;
        MethodNode handler = findMethod(node, MERGE_HANDLER, OLD_MERGE_HANDLER_DESC);
        AnnotationNode wrap = handler == null ? null
                : findMethodAnnotation(handler, "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");
        Object methods = wrap == null ? null : annotationValue(wrap, "method");
        if (wrap == null || !(methods instanceof List<?> selected) || !selected.contains("merge")
                || !containsTarget(wrap, OLD_DESPAWN_TARGET)
                || (handler.signature != null && !handler.signature.equals(OLD_MERGE_HANDLER_SIGNATURE))) return 0;

        MethodInsnNode operationCall = null;
        int operationCalls = 0;
        org.objectweb.asm.tree.TypeInsnNode objectArray = null;
        int objectArrays = 0;
        for (AbstractInsnNode instruction : handler.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(OPERATION) && call.name.equals("call")
                    && call.desc.equals("([Ljava/lang/Object;)Ljava/lang/Object;")) {
                operationCall = call;
                operationCalls++;
            }
            if (instruction instanceof org.objectweb.asm.tree.TypeInsnNode type && type.getOpcode() == Opcodes.ANEWARRAY
                    && type.desc.equals("java/lang/Object")) {
                objectArray = type;
                objectArrays++;
            }
        }
        if (operationCalls != 1 || objectArrays != 1) return 0;
        AbstractInsnNode sizeInstruction = previousCode(objectArray.getPrevious());
        AbstractInsnNode operationLoad = sizeInstruction == null ? null : previousCode(sizeInstruction.getPrevious());
        if (!(sizeInstruction instanceof IntInsnNode size) || size.getOpcode() != Opcodes.BIPUSH || size.operand != 6
                || !(operationLoad instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ALOAD || load.var != 6) return 0;

        replaceTarget(wrap, OLD_DESPAWN_TARGET, NEW_DESPAWN_TARGET);
        size.operand = 7;
        for (AbstractInsnNode instruction : handler.instructions) {
            if (instruction instanceof VarInsnNode variable && variable.var >= 6) variable.var++;
            if (instruction instanceof org.objectweb.asm.tree.IincInsnNode increment && increment.var >= 6) increment.var++;
        }
        InsnList forwardedSourceId = new InsnList();
        forwardedSourceId.add(new InsnNode(Opcodes.DUP));
        forwardedSourceId.add(new IntInsnNode(Opcodes.BIPUSH, 6));
        forwardedSourceId.add(new VarInsnNode(Opcodes.ILOAD, 6));
        forwardedSourceId.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
        forwardedSourceId.add(new InsnNode(Opcodes.AASTORE));
        handler.instructions.insertBefore(operationCall, forwardedSourceId);
        handler.desc = NEW_MERGE_HANDLER_DESC;
        if (handler.signature != null) handler.signature = NEW_MERGE_HANDLER_SIGNATURE;
        if (handler.localVariables != null) {
            for (LocalVariableNode local : handler.localVariables) if (local.index >= 6) local.index++;
        }
        handler.maxLocals = Math.max(handler.maxLocals + 1, 11);
        handler.maxStack = Math.max(handler.maxStack, 5);
        classes.put(MERGE_MIXIN, writeClass(node));
        return 1;
    }

    private static AbstractInsnNode previousCode(AbstractInsnNode instruction) {
        while (instruction != null && (instruction instanceof LabelNode || instruction instanceof FrameNode
                || instruction instanceof org.objectweb.asm.tree.LineNumberNode)) instruction = instruction.getPrevious();
        return instruction;
    }

    private static AnnotationNode findRedirect(MethodNode method) {
        AnnotationNode annotation = findRedirect(method.visibleAnnotations);
        return annotation == null ? findRedirect(method.invisibleAnnotations) : annotation;
    }

    private static AnnotationNode findRedirect(java.util.List<AnnotationNode> annotations) {
        if (annotations == null) return null;
        for (AnnotationNode annotation : annotations) {
            if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;")) return annotation;
        }
        return null;
    }

    private static boolean containsTarget(AnnotationNode annotation, String target) {
        if (annotation.values == null) return false;
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            Object value = annotation.values.get(i + 1);
            if ("target".equals(annotation.values.get(i)) && target.equals(value)) return true;
            if (value instanceof AnnotationNode nested && containsTarget(nested, target)) return true;
            if (value instanceof Iterable<?> nested) {
                for (Object item : nested) if (item instanceof AnnotationNode child && containsTarget(child, target)) return true;
            }
        }
        return false;
    }

    private static boolean replaceTarget(AnnotationNode annotation, String oldTarget, String newTarget) {
        boolean changed = false;
        if (annotation.values == null) return false;
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            Object value = annotation.values.get(i + 1);
            if ("target".equals(annotation.values.get(i)) && oldTarget.equals(value)) {
                annotation.values.set(i + 1, newTarget);
                changed = true;
            } else if (value instanceof AnnotationNode nested) {
                changed |= replaceTarget(nested, oldTarget, newTarget);
            } else if (value instanceof Iterable<?> nested) {
                for (Object item : nested) if (item instanceof AnnotationNode child) changed |= replaceTarget(child, oldTarget, newTarget);
            }
        }
        return changed;
    }

    private static byte[] adaptSaltDecorator(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        if (!node.name.equals(SALT_DECORATOR)) return null;

        boolean changed = false;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("<init>") || !method.desc.equals("()V")) continue;
            for (var instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESPECIAL
                        || !call.owner.equals(BIOME_DECORATOR) || !call.name.equals("<init>") || !call.desc.equals("()V")) continue;
                method.instructions.insertBefore(call, new InsnNode(Opcodes.ACONST_NULL));
                call.desc = BASE_CTOR;
                method.maxStack = Math.max(method.maxStack, 2);
                changed = true;
            }
        }
        if (!changed) return null;

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }
}
