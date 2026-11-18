package legacyconnector.v1_12_2.net.minecraft.nbt;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class NBTTagCompoundPersistentViewTest {
    @Test
    void reusesTheLiveViewForAnUnchangedBackingCompound() {
        CompoundTag backing = new CompoundTag();
        NBTTagCompound view = NBTTagCompound.mcc$wrapPersistentData(backing, null);

        assertSame(view, NBTTagCompound.mcc$wrapPersistentData(backing, view));
        view.setInteger("counter", 7);
        assertEquals(7, backing.getInt("counter"));
    }

    @Test
    void replacesTheCachedViewWhenTheBackingCompoundChanges() {
        CompoundTag original = new CompoundTag();
        CompoundTag replacement = new CompoundTag();
        NBTTagCompound view = NBTTagCompound.mcc$wrapPersistentData(original, null);
        NBTTagCompound next = NBTTagCompound.mcc$wrapPersistentData(replacement, view);

        assertNotSame(view, next);
        next.setInteger("counter", 9);
        assertEquals(0, original.getInt("counter"));
        assertEquals(9, replacement.getInt("counter"));
    }
}
