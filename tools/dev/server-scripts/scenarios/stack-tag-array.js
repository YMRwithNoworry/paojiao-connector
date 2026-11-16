// Run on a dedicated server through -PserverScript=<dir>. Tests late mutation of the raw 1.12 NBT arrays.
if (typeof G === "undefined") G = {};
function T(name) { return Java.type(name); }
function modernTag() {
  var data = G.stack.get(T("net.minecraft.core.component.DataComponents").CUSTOM_DATA);
  return data == null ? null : data.copyTag();
}
function report(label) {
  var m = modernTag();
  var actual = m == null ? "missing" : [m.getByteArray("setter")[0], m.getIntArray("ints")[0],
      m.getCompound("nested").getByteArray("getter")[0], m.getByteArray("boxed")[0]].join(",");
  var legacyBoxed = G.tag.getByteArray("boxed")[0];
  log.info("STACKARRAY " + label + " modern=" + actual + " legacyBoxed=" + legacyBoxed);
  return actual;
}
function onTick(n) {
  var Stacks = T("org.kylepitcock.legacyconnector.v1_12_2.bridge.Stacks1122");
  if (!G.started) {
    G.started = n;
    var ModernStack = T("net.minecraft.world.item.ItemStack");
    var Compound = T("legacyconnector.v1_12_2.net.minecraft.nbt.NBTTagCompound");
    var Bytes = T("legacyconnector.v1_12_2.net.minecraft.nbt.NBTTagByteArray");
    G.stack = new ModernStack(T("net.minecraft.world.item.Items").STONE, 1);
    G.view = Stacks.view(G.stack);
    G.tag = new Compound();
    G.inputBytes = Java.to([1, 2], "byte[]");
    G.inputInts = Java.to([3, 4], "int[]");
    G.boxedBytes = Java.to([5, 6], "byte[]");
    G.tag.setByteArray("setter", G.inputBytes);
    G.tag.setIntArray("ints", G.inputInts);
    var child = new Compound();
    child.setByteArray("getter", Java.to([7, 8], "byte[]"));
    G.tag.setTag("nested", child);
    G.returnedBytes = child.getByteArray("getter");
    G.tag.setTag("boxed", new Bytes(G.boxedBytes));
    G.view.setTagCompound(G.tag);
    Stacks.flushViews();
    report("initial");
    return false;
  }
  if (n - G.started < 20) return false;
  if (!G.firstChecked) {
    G.firstChecked = true;
    G.inputBytes[0] = 21;
    G.inputInts[0] = 22;
    G.returnedBytes[0] = 23;
    G.boxedBytes[0] = 24;
    Stacks.flushViews();
    G.firstPassed = report("delayed") == "21,22,23,24" && G.tag.getByteArray("boxed")[0] == 24;
    return false;
  }
  if (n - G.started < 40) return false;
  G.inputBytes[0] = 31;
  Stacks.flushViews();
  var repeated = modernTag().getByteArray("setter")[0] == 31;
  var replacement = new (T("net.minecraft.nbt.CompoundTag"))();
  replacement.putString("modern", "preserved");
  G.stack.set(T("net.minecraft.core.component.DataComponents").CUSTOM_DATA,
      T("net.minecraft.world.item.component.CustomData").of(replacement));
  G.inputBytes[0] = 41;
  Stacks.flushViews();
  var preserved = modernTag().getString("modern") == "preserved";
  var refreshed = G.view.getTagCompound().getString("modern") == "preserved";
  var passed = G.firstPassed && repeated && preserved && refreshed;
  log.info("STACKARRAY repeated=" + repeated + " modernReplacement=" + preserved + " viewRefreshed=" + refreshed);
  log.info("STACKARRAY final=" + (passed ? "PASS" : "FAIL"));
  return true;
}
