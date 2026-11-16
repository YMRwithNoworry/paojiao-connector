// Server-side audit of the two Cotesia creative tabs and the legacy stacks each tab exposes.
function onTick() {
  try {
    var ModelLoader = Java.type("legacyconnector.v1_12_2.net.minecraftforge.client.model.ModelLoader");
    var modelType = Java.type("java.lang.Class").forName(ModelLoader.class.getName() + "$WeightedRandomModel", false, ModelLoader.class.getClassLoader());
    var ListType = Java.type("java.util.List").class;
    var SetType = Java.type("java.util.Set").class;
    var StateType = Java.type("legacyconnector.v1_12_2.net.minecraftforge.common.model.IModelState").class;
    var constructor = modelType.getDeclaredConstructor(ListType, ListType, SetType, ListType, StateType);
    constructor.setAccessible(true);
    log.info("COTESIAWRM final=PASS type=" + modelType.getName() + " parameters=" + constructor.getParameterCount());
    var Tabs = Java.type("legacyconnector.v1_12_2.net.minecraft.creativetab.CreativeTabs");
    var Items = Java.type("org.kylepitcock.legacyconnector.v1_12_2.bridge.ItemBridge1122").modItems();
    var NonNullList = Java.type("legacyconnector.v1_12_2.net.minecraft.util.NonNullList");
    var Bridge = Java.type("org.kylepitcock.legacyconnector.v1_12_2.bridge.CreativeTabBridge1122");
    var ResourceLocation = Java.type("net.minecraft.resources.ResourceLocation");
    var Registry = Java.type("net.minecraft.core.registries.BuiltInRegistries").CREATIVE_MODE_TAB;
    var contentsMethod = Bridge.class.getDeclaredMethod("contents", Tabs.class);
    contentsMethod.setAccessible(true);
    var labels = ["srpcotesia", "srpcotesia_threat"];
    var found = 0;
    var failed = false;
    for (var ti = 0; ti < Tabs.mcc$TABS.size(); ti++) {
      var tab = Tabs.mcc$TABS.get(ti);
      var label = String(tab.getTabLabel());
      if (labels.indexOf(label) < 0) continue;
      found++;
      var assigned = 0;
      var listed = 0;
      var failures = 0;
      var first = [];
      for (var ii = 0; ii < Items.size(); ii++) {
        var item = Items.get(ii);
        var itemTabs = item.getCreativeTabs();
        var matches = false;
        for (var ai = 0; ai < itemTabs.length; ai++) if (itemTabs[ai] === tab) matches = true;
        if (!matches) continue;
        assigned++;
        var stacks = NonNullList.create();
        try {
          item.getSubItems(tab, stacks);
          listed += stacks.size();
          if (first.length < 8 && stacks.size() > 0) first.push(String(item.getRegistryName()));
        } catch (e) {
          failures++;
        }
      }
      var key = ResourceLocation.fromNamespaceAndPath("legacyconnector", "legacy12_" + label);
      var registered = Registry.containsKey(key);
      var modernStacks = contentsMethod.invoke(null, tab);
      var modernCount = modernStacks.size();
      if (assigned === 0 || listed === 0 || failures !== 0 || !registered || modernCount === 0) failed = true;
      log.info("COTESIATAB label=" + label + " index=" + tab.getTabIndex() + " assignedItems=" + assigned
          + " listedStacks=" + listed + " listingFailures=" + failures + " modernRegistered=" + registered
          + " modernStacks=" + modernCount + " sample=" + first.join(","));
    }
    if (found !== labels.length) failed = true;
    if (failed) log.error("COTESIATAB final=FAIL tabsFound=" + found + " expected=" + labels.length);
    else log.info("COTESIATAB final=PASS");
  } catch (e) {
    log.error("COTESIATAB final=FAIL " + e);
  }
  return true;
}
