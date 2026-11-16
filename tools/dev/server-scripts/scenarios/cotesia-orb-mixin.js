function onTick() {
  try {
    var Orb = Java.type("com.dhanantry.scapeandrunparasites.entity.EntityOrbScary");
    var ModifiedOrb = Java.type("com.srpcotesia.entity.IModifiedOrb");
    if (!ModifiedOrb.class.isAssignableFrom(Orb.class)) {
      throw new Error("EntityOrbScary was not mixed with IModifiedOrb");
    }
    Orb.class.getMethod("srpcotesia$getPlayerFollowing");
    Orb.class.getMethod("srpcotesia$setPlayerFollowing",
        Java.type("legacyconnector.v1_12_2.net.minecraft.entity.player.EntityPlayer").class,
        Java.type("java.lang.Boolean").TYPE);
    Orb.class.getMethod("srpcotesia$isFollowing");
    log.info("COTESIAORB target=" + Orb.class.getName() + " interface=PASS accessors=PASS");
    log.info("COTESIAORB final=PASS");
  } catch (e) {
    log.error("COTESIAORB final=FAIL " + e);
  }
  return true;
}
