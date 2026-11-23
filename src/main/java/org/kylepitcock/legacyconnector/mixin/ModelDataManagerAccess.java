package org.kylepitcock.legacyconnector.mixin;

import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.model.data.ModelDataManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ModelDataManager.class, remap = false)
public interface ModelDataManagerAccess {
    @Accessor("level") Level legacyconnector$level();
}
