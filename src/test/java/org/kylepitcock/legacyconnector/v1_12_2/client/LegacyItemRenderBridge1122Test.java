package org.kylepitcock.legacyconnector.v1_12_2.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import legacyconnector.v1_12_2.net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import legacyconnector.v1_12_2.net.minecraft.item.Item;
import legacyconnector.v1_12_2.net.minecraft.item.ItemStack;
import org.junit.jupiter.api.Test;

class LegacyItemRenderBridge1122Test {
    @Test
    void dispatchesToTheRendererCurrentlyAssignedToTheLegacyItem() {
        Item item = new Item();
        ItemStack stack = new ItemStack(item, 2, 7);
        RecordingRenderer renderer = new RecordingRenderer();

        item.setTileEntityItemStackRenderer(renderer);

        assertTrue(LegacyItemRenderBridge1122.render(stack));
        assertSame(stack, renderer.stack);
        assertTrue(renderer.called);
    }

    @Test
    void doesNotInvokeTheDefaultRendererForOrdinaryItems() {
        assertFalse(LegacyItemRenderBridge1122.render(new ItemStack(new Item())));
        assertFalse(LegacyItemRenderBridge1122.render(ItemStack.EMPTY));
        assertFalse(LegacyItemRenderBridge1122.render(null));
    }

    private static final class RecordingRenderer extends TileEntityItemStackRenderer {
        private ItemStack stack;
        private boolean called;

        @Override
        public void renderByItem(ItemStack itemStackIn, float partialTicks) {
            stack = itemStackIn;
            called = true;
        }
    }
}
