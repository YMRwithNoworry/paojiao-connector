function connectedPlayer(name) {
  var GP = Java.type("com.mojang.authlib.GameProfile");
  var UUID = Java.type("java.util.UUID");
  var profile = new GP(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()), name);
  var ClientInformation = Java.type("net.minecraft.server.level.ClientInformation");
  var ServerPlayer = Java.type("net.minecraft.server.level.ServerPlayer");
  var player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
  var Connection = Java.type("net.minecraft.network.Connection");
  var PacketFlow = Java.type("net.minecraft.network.protocol.PacketFlow");
  var connection = new Connection(PacketFlow.SERVERBOUND);
  var EmbeddedChannel = Java.type("io.netty.channel.embedded.EmbeddedChannel");
  new EmbeddedChannel(connection);
  var Channels = Java.type("net.neoforged.neoforge.network.registration.ChannelAttributes");
  var ResourceLocation = Java.type("net.minecraft.resources.ResourceLocation");
  var adHoc = Channels.getOrCreateAdHocChannels(connection);
  ["legacyconnector:legacy", "legacyconnector:legacy12", "legacyconnector:fml", "neoforge:advanced_open_screen", "neoforge:extended_server_data", "neoforge:advanced_add_entity", "pehkui:scale"]
    .forEach(function (id) { adHoc.add(ResourceLocation.parse(id)); });
  var Cookie = Java.type("net.minecraft.server.network.CommonListenerCookie");
  server.getPlayerList().placeNewPlayer(connection, player,
      new Cookie(profile, 0, ClientInformation.createDefault(), false));
  return player;
}

function onTick() {
  try {
    var player = connectedPlayer("CotesiaFearProbe");
    var EntityBridge = Java.type("org.kylepitcock.legacyconnector.v1_12_2.bridge.EntityBridge1122");
    var view = EntityBridge.wrap(player);
    var FearRandom = Java.type("com.srpcotesia.entity.IFearRandom");
    if (!FearRandom.class.isInstance(view)) throw new Error("player view lacks IFearRandom: " + view.getClass().getName());
    var random = FearRandom.class.getMethod("srpcotesia$getFearRNG").invoke(view);
    if (random == null) throw new Error("fear RNG accessor returned null");

    var LegacyForge = Java.type("legacyconnector.v1_12_2.net.minecraftforge.common.MinecraftForge");
    var LegacyRightClick = Java.type("legacyconnector.v1_12_2.net.minecraftforge.event.entity.player.PlayerInteractEvent$RightClickBlock");
    if (!LegacyForge.EVENT_BUS.mcc$hasListeners(LegacyRightClick.class)) {
      throw new Error("no legacy RightClickBlock listeners are registered");
    }

    var Hand = Java.type("net.minecraft.world.InteractionHand");
    var ModernStack = Java.type("net.minecraft.world.item.ItemStack");
    var Items = Java.type("net.minecraft.world.item.Items");
    player.setItemInHand(Hand.MAIN_HAND, new ModernStack(Items.STICK));
    var before = random.getIndex();
    var BlockPos = Java.type("legacyconnector.v1_12_2.net.minecraft.util.math.BlockPos");
    var Facing = Java.type("legacyconnector.v1_12_2.net.minecraft.util.EnumFacing");
    var LegacyHand = Java.type("legacyconnector.v1_12_2.net.minecraft.util.EnumHand");
    var Vec3d = Java.type("legacyconnector.v1_12_2.net.minecraft.util.math.Vec3d");
    var ForgeHooks = Java.type("legacyconnector.v1_12_2.net.minecraftforge.common.ForgeHooks");
    var event = ForgeHooks.onRightClickBlock(view, LegacyHand.MAIN_HAND,
        new BlockPos(0, 64, 0), Facing.UP, new Vec3d(0.5, 64.5, 0.5));
    var after = random.getIndex();
    if (after !== (before + 1) % random.get().length) {
      throw new Error("legacy right-click did not advance fear RNG: " + before + " -> " + after);
    }
    log.info("COTESIAFEAR playerView=" + view.getClass().getName() + " interface=PASS listener=PASS rngIndex=" + before + "->" + after + " eventCanceled=" + event.isCanceled());
    log.info("COTESIAFEAR final=PASS");
  } catch (e) {
    log.error("COTESIAFEAR final=FAIL " + e);
  }
  return true;
}
