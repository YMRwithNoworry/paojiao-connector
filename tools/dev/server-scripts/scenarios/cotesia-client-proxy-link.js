// Run with the legacy SRP, Cotesia and GeckoLib 3 jars using -PserverScript=<dir> -PserverScriptMod=srpcotesia.
// This verifies that the client sided proxy is linkable and constructible without invoking its client-only lifecycle hooks.
function onTick() {
  try {
    var ClientProxy = Java.type("com.srpcotesia.proxy.ClientProxySRPCotesia");
    var proxy = new ClientProxy();
    log.info("COTESIAPROXY clientConstructor=" + proxy.getClass().getName());
    log.info("COTESIAPROXY final=PASS");
  } catch (e) {
    log.error("COTESIAPROXY final=FAIL " + e);
  }
  return true;
}
