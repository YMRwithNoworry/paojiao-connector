package org.kylepitcock.legacyconnector.v1_12_2.client;

import com.mojang.blaze3d.vertex.PoseStack;
import legacyconnector.v1_12_2.net.minecraft.item.ItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import org.kylepitcock.legacyconnector.v1_12_2.bridge.Stacks1122;
import org.kylepitcock.legacyconnector.v1_7_10.client.RenderContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runs a Forge 1.12 TileEntityItemStackRenderer from NeoForge's item custom-renderer hook. */
public final class LegacyItemRenderer1122 extends BlockEntityWithoutLevelRenderer {
    private static final Logger LOG = LoggerFactory.getLogger("LegacyConnector/Render1122");
    private static final java.util.Set<String> LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public LegacyItemRenderer1122() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(net.minecraft.world.item.ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
            int light, int overlay) {
        ItemStack legacy = Stacks1122.fromModern(stack);
        if (legacy.isEmpty()) return;
        pose.pushPose();
        RenderContext.beginWorld(pose, buffers, light, overlay);
        try {
            LegacyItemRenderBridge1122.render(legacy);
        } catch (RuntimeException | LinkageError e) {
            String key = legacy.getItem().getClass().getName();
            if (LOGGED.add(key)) LOG.error("Legacy 1.12 item renderer {} failed (logged once)", key, e);
        } finally {
            RenderContext.end();
            pose.popPose();
        }
    }
}
