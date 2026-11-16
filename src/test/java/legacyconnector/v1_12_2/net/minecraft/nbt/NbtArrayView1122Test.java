package legacyconnector.v1_12_2.net.minecraft.nbt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.nbt.ByteArrayTag;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class NbtArrayView1122Test {
    @Test
    void preservesArrayAliasesAndFlushesDelayedMutations() {
        NBTTagCompound root = new NBTTagCompound();
        byte[] assignedBytes = {1, 2};
        int[] assignedInts = {3, 4};
        root.setByteArray("assignedBytes", assignedBytes);
        root.setIntArray("assignedInts", assignedInts);

        assignedBytes[0] = 11;
        assignedInts[0] = 12;
        root.getByteArray("assignedBytes")[1] = 13;
        root.getIntArray("assignedInts")[1] = 14;

        CompoundTag modern = root.mcc$modern();
        assertArrayEquals(new byte[] {11, 13}, modern.getByteArray("assignedBytes"));
        assertArrayEquals(new int[] {12, 14}, modern.getIntArray("assignedInts"));
    }

    @Test
    void synchronizesArrayViewsInsideNestedCompoundsAndLists() {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound nested = new NBTTagCompound();
        nested.setByteArray("bytes", new byte[] {1, 2});
        root.setTag("nested", nested);

        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagIntArray(new int[] {3, 4}));
        root.setTag("list", list);

        nested.getByteArray("bytes")[0] = 21;
        list.getIntArrayAt(0)[0] = 22;
        assertEquals(21, root.mcc$modern().getCompound("nested").getByteArray("bytes")[0]);
        assertEquals(22, root.mcc$modern().getList("list", 11).getIntArray(0)[0]);
    }

    @Test
    void modernReplacementDoesNotGetOverwrittenByCachedArray() {
        NBTTagCompound root = new NBTTagCompound();
        root.setByteArray("value", new byte[] {1});
        byte[] old = root.getByteArray("value");
        CompoundTag modern = root.mcc$modern();
        modern.put("value", new ByteArrayTag(new byte[] {7}));
        old[0] = 9;
        assertEquals(7, root.mcc$modern().getByteArray("value")[0]);
        assertEquals(7, root.getByteArray("value")[0]);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listInsertionAndRemovalPreserveArrayAliases() {
        NBTTagList list = new NBTTagList();
        int[] first = {1};
        int[] second = {2};
        list.appendTag(new NBTTagIntArray(first));
        list.appendTag(new NBTTagIntArray(second));
        java.util.List<NBTBase> view = NBTTagList.mcc$get$tagList(list);
        view.add(0, new NBTTagIntArray(new int[] {3}));
        first[0] = 11;
        second[0] = 12;
        assertSame(first, list.getIntArrayAt(1));
        assertEquals(11, list.mcc$modern().getIntArray(1)[0]);
        view.remove(0);
        assertSame(second, list.getIntArrayAt(1));
        assertEquals(12, list.copy().mcc$modern().getIntArray(1)[0]);
    }

    @Test
    void copyIncludesDelayedArrayChanges() {
        NBTTagCompound root = new NBTTagCompound();
        byte[] value = {1};
        root.setByteArray("value", value);
        value[0] = 8;
        NBTTagCompound copy = root.copy();
        value[0] = 9;
        assertEquals(8, copy.getByteArray("value")[0]);
        assertEquals(9, root.mcc$modern().getByteArray("value")[0]);
    }

    @Test
    void replacingArrayKeyDoesNotRestoreTheOldArray() {
        NBTTagCompound root = new NBTTagCompound();
        root.setByteArray("value", new byte[] {1});
        root.getByteArray("value")[0] = 2;
        root.setInteger("value", 7);

        assertEquals(7, root.mcc$modern().getInt("value"));
    }
}
