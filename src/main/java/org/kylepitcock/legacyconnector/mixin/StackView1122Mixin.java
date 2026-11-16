package org.kylepitcock.legacyconnector.mixin;

import org.kylepitcock.legacyconnector.v1_12_2.bridge.StackViewAccess1122;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Stores the legacy stack wrapper on its modern backing object so the object graph can be collected as a cycle. */
@Mixin(net.minecraft.world.item.ItemStack.class)
public abstract class StackView1122Mixin implements StackViewAccess1122 {
    @Unique
    private volatile Object legacyconnector$view1122;

    @Override
    public Object legacyconnector$view1122() {
        return legacyconnector$view1122;
    }

    @Override
    public void legacyconnector$setView1122(Object view) {
        legacyconnector$view1122 = view;
    }
}
