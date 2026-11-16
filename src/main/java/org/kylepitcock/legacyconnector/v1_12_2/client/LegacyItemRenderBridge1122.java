package org.kylepitcock.legacyconnector.v1_12_2.client;

import legacyconnector.v1_12_2.net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import legacyconnector.v1_12_2.net.minecraft.item.ItemStack;

/** Dispatches a legacy item stack to the Forge renderer assigned to its legacy item. */
public final class LegacyItemRenderBridge1122 {
    private LegacyItemRenderBridge1122() {}

    public static boolean render(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        TileEntityItemStackRenderer renderer = stack.getItem().getTileEntityItemStackRenderer();
        if (renderer == null || renderer == TileEntityItemStackRenderer.instance) return false;
        renderer.renderByItem(stack);
        return true;
    }
}
