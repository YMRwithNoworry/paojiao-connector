if (typeof G === "undefined") G = {};
function T(name) { return Java.type(name); }

function allocateEphemeral(count, Stacks, ModernStack, stone, WeakReference) {
  for (var i = 0; i < count; i++) {
    var modern = new ModernStack(stone, 1);
    var view = Stacks.view(modern);
    var tag = new (T("legacyconnector.v1_12_2.net.minecraft.nbt.NBTTagCompound"))();
    tag.setByteArray("array", Java.to([1], "byte[]"));
    view.setTagCompound(tag);
    G.ephemeralModern.add(new WeakReference(modern));
    G.ephemeralViews.add(new WeakReference(view));
  }
}

function beginProbe() {
  var Stacks = T("org.kylepitcock.legacyconnector.v1_12_2.bridge.Stacks1122");
  var ModernStack = T("net.minecraft.world.item.ItemStack");
  var Items = T("net.minecraft.world.item.Items");
  var LegacyTag = T("legacyconnector.v1_12_2.net.minecraft.nbt.NBTTagCompound");
  var WeakReference = T("java.lang.ref.WeakReference");
  G.ephemeralModern = new (T("java.util.ArrayList"))();
  G.ephemeralViews = new (T("java.util.ArrayList"))();
  allocateEphemeral(10000, Stacks, ModernStack, Items.STONE, WeakReference);
  G.keepModern = new ModernStack(Items.STONE, 1);
  var keepView = Stacks.view(G.keepModern);
  keepView.setTagCompound(new LegacyTag());
  keepView.getTagCompound().setString("probe", "before");
  Stacks.flushViews();
  G.keepTag = keepView.getTagCompound();
  G.keepModernRef = new WeakReference(G.keepModern);
  G.keepViewRef = new WeakReference(keepView);
  G.phaseOneOk = false;
  G.phaseTwoOk = false;
}

function aliveCount(refs) {
  var count = 0;
  for (var i = 0; i < refs.size(); i++) if (refs.get(i).get() != null) count++;
  return count;
}

function collect() {
  var System = T("java.lang.System");
  for (var i = 0; i < 3; i++) System.gc();
}

function readProbeTag(stack) {
  var component = stack.get(T("net.minecraft.core.component.DataComponents").CUSTOM_DATA);
  return component == null ? "<missing>" : component.copyTag().getString("probe");
}

function verifyStackHeldPhase() {
  var Stacks = T("org.kylepitcock.legacyconnector.v1_12_2.bridge.Stacks1122");
  var stack = G.keepModern;
  var cached = G.keepViewRef.get();
  var stable = cached != null && Stacks.view(stack) == cached;
  G.keepTag.setString("probe", "late");
  Stacks.flushViews();
  G.phaseOneOk = stable && readProbeTag(stack) == "late"
      && aliveCount(G.ephemeralModern) == 0 && aliveCount(G.ephemeralViews) == 0;
  log.info("STACKVIEW phase1 stable=" + stable + " delayedTag=" + readProbeTag(stack)
      + " ephemeralModern=" + aliveCount(G.ephemeralModern) + " ephemeralViews=" + aliveCount(G.ephemeralViews));
  G.keepModern = null;
}

function verifyTagHeldPhase() {
  var Stacks = T("org.kylepitcock.legacyconnector.v1_12_2.bridge.Stacks1122");
  var stack = G.keepModernRef.get();
  var view = G.keepViewRef.get();
  var retained = stack != null && view != null;
  if (retained) {
    G.keepTag.setString("probe", "tag-only");
    Stacks.flushViews();
  }
  G.phaseTwoOk = retained && readProbeTag(stack) == "tag-only";
  log.info("STACKVIEW phase2 tagRetainsStack=" + retained + " delayedTag=" + (retained ? readProbeTag(stack) : "<missing>"));
  G.keepModern = null;
  G.keepTag = null;
}

function verifyReleasedPhase() {
  collect();
  var modernAlive = G.keepModernRef.get() != null;
  var viewAlive = G.keepViewRef.get() != null;
  var ephemeralModern = aliveCount(G.ephemeralModern);
  var ephemeralViews = aliveCount(G.ephemeralViews);
  var passed = G.phaseOneOk && G.phaseTwoOk && !modernAlive && !viewAlive && ephemeralModern == 0 && ephemeralViews == 0;
  log.info("STACKVIEW final=" + (passed ? "PASS" : "FAIL") + " modernAlive=" + modernAlive + " viewAlive=" + viewAlive
      + " ephemeralModern=" + ephemeralModern + " ephemeralViews=" + ephemeralViews);
}

function onTick(n) {
  if (!G.started) {
    G.started = true;
    G.startedAt = n;
    beginProbe();
    log.info("STACKVIEW started 10000 ephemeral stacks");
    return false;
  }
  var elapsed = n - G.startedAt;
  if (!G.phaseOneChecked && elapsed >= 200) {
    G.phaseOneChecked = true;
    collect();
    verifyStackHeldPhase();
  } else if (!G.phaseTwoChecked && elapsed >= 400) {
    G.phaseTwoChecked = true;
    verifyTagHeldPhase();
  } else if (!G.phaseThreeChecked && elapsed >= 600) {
    G.phaseThreeChecked = true;
    verifyReleasedPhase();
    return true;
  }
  return false;
}
