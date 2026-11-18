package legacyconnector.v1_12_2.net.minecraft.nbt;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public class NBTTagCompound extends NBTBase {
    private final CompoundTag mcc$tag;
    /** Child views handed out, so repeated getCompoundTag(key) calls return the same object like in 1.12.2. */
    private final Map<String, NBTBase> mcc$children = new HashMap<>();
    private final Map<String, Tag> mcc$childTags = new HashMap<>();
    private final Set<legacyconnector.v1_12_2.net.minecraft.item.ItemStack> mcc$stackViews =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    public NBTTagCompound() {
        this(new CompoundTag());
    }

    public NBTTagCompound(CompoundTag tag) {
        this.mcc$tag = tag;
    }

    /** Reuses an entity's persistent-data view while the modern capability keeps the same backing compound. */
    public static NBTTagCompound mcc$wrapPersistentData(CompoundTag tag, NBTTagCompound previous) {
        return previous != null && previous.mcc$tag == tag ? previous : new NBTTagCompound(tag);
    }

    @Override
    public CompoundTag mcc$modern() {
        mcc$syncMutableChildren();
        return mcc$tag;
    }

    private void mcc$syncMutableChildren() {
        for (String key : new java.util.ArrayList<>(mcc$children.keySet())) {
            NBTBase child = mcc$children.get(key);
            Tag current = mcc$tag.get(key);
            if (current != null && current == mcc$childTags.get(key)) {
                Tag updated = child.mcc$modern();
                mcc$tag.put(key, updated);
                mcc$childTags.put(key, updated);
            } else {
                mcc$children.remove(key);
                mcc$childTags.remove(key);
            }
        }
    }

    public void mcc$bindStackView(legacyconnector.v1_12_2.net.minecraft.item.ItemStack view) {
        synchronized (mcc$stackViews) {
            boolean wasEmpty = mcc$stackViews.isEmpty();
            mcc$stackViews.add(view);
            if (wasEmpty) mcc$setChangeListener(this::mcc$changed);
        }
    }

    public void mcc$unbindStackView(legacyconnector.v1_12_2.net.minecraft.item.ItemStack view) {
        synchronized (mcc$stackViews) {
            mcc$stackViews.remove(view);
            if (mcc$stackViews.isEmpty()) mcc$setChangeListener(null);
        }
    }

    private void mcc$changed() {
        synchronized (mcc$stackViews) {
            for (var view : mcc$stackViews) org.kylepitcock.legacyconnector.v1_12_2.bridge.Stacks1122.touched(view);
        }
    }

    @Override
    public byte getId() {
        return 10;
    }

    public Set<String> getKeySet() {
        return mcc$tag.getAllKeys();
    }

    public void setTag(String key, NBTBase value) {
        if (value == null) return; // legacy stored null and failed later when writing the tag
        mcc$children.put(key, value);
        value.mcc$parent = this;
        Tag inserted = value.mcc$modern();
        mcc$tag.put(key, inserted);
        mcc$childTags.put(key, inserted);
        mcc$markDirty();
    }

    public void setByte(String key, byte value) {
        mcc$children.remove(key);
        mcc$childTags.remove(key);
        mcc$tag.putByte(key, value);
        mcc$markDirty();
    }

    public void setShort(String key, short value) {
        mcc$children.remove(key);
        mcc$childTags.remove(key);
        mcc$tag.putShort(key, value);
        mcc$markDirty();
    }

    public void setInteger(String key, int value) {
        mcc$children.remove(key);
        mcc$childTags.remove(key);
        mcc$tag.putInt(key, value);
        mcc$markDirty();
    }

    public void setLong(String key, long value) {
        mcc$children.remove(key);
        mcc$childTags.remove(key);
        mcc$tag.putLong(key, value);
        mcc$markDirty();
    }

    public void setFloat(String key, float value) {
        mcc$children.remove(key);
        mcc$childTags.remove(key);
        mcc$tag.putFloat(key, value);
        mcc$markDirty();
    }

    public void setDouble(String key, double value) {
        mcc$children.remove(key);
        mcc$childTags.remove(key);
        mcc$tag.putDouble(key, value);
        mcc$markDirty();
    }

    public void setString(String key, String value) {
        mcc$children.remove(key);
        mcc$childTags.remove(key);
        mcc$tag.putString(key, value);
        mcc$markDirty();
    }

    public void setByteArray(String key, byte[] value) {
        setTag(key, new NBTTagByteArray(value));
    }

    public void setIntArray(String key, int[] value) {
        setTag(key, new NBTTagIntArray(value));
    }

    public void setBoolean(String key, boolean value) {
        setByte(key, (byte) (value ? 1 : 0));
    }

    public NBTBase getTag(String key) {
        NBTBase child = mcc$children.get(key);
        Tag tag = mcc$tag.get(key);
        if (child != null && tag != null && tag == mcc$childTags.get(key)) return child;
        if (tag == null) return null;
        NBTBase view = NBTBase.mcc$wrap(tag, this);
        if (view != null) {
            mcc$children.put(key, view);
            mcc$childTags.put(key, tag);
        }
        return view;
    }

    public byte getTagId(String key) {
        Tag tag = mcc$tag.get(key);
        return tag == null ? 0 : tag.getId();
    }

    public boolean hasKey(String key) {
        return mcc$tag.contains(key);
    }

    public boolean hasKey(String key, int type) {
        return mcc$tag.contains(key, type);
    }

    public byte getByte(String key) {
        return mcc$tag.getByte(key);
    }

    public short getShort(String key) {
        return mcc$tag.getShort(key);
    }

    public int getInteger(String key) {
        return mcc$tag.getInt(key);
    }

    public long getLong(String key) {
        return mcc$tag.getLong(key);
    }

    public float getFloat(String key) {
        return mcc$tag.getFloat(key);
    }

    public double getDouble(String key) {
        return mcc$tag.getDouble(key);
    }

    public String getString(String key) {
        return mcc$tag.getString(key);
    }

    public byte[] getByteArray(String key) {
        NBTBase tag = getTag(key);
        return tag instanceof NBTTagByteArray array ? array.getByteArray() : new byte[0];
    }

    public int[] getIntArray(String key) {
        NBTBase tag = getTag(key);
        return tag instanceof NBTTagIntArray array ? array.getIntArray() : new int[0];
    }

    /** Returns a live view; a missing key yields a detached empty compound, as in 1.12.2. */
    public NBTTagCompound getCompoundTag(String key) {
        NBTBase tag = getTag(key);
        return tag instanceof NBTTagCompound c ? c : new NBTTagCompound();
    }

    public NBTTagList getTagList(String key, int type) {
        NBTBase tag = getTag(key);
        if (tag instanceof NBTTagList list && (list.tagCount() == 0 || list.getTagType() == type)) return list;
        return new NBTTagList();
    }

    public boolean getBoolean(String key) {
        return getByte(key) != 0;
    }

    public void removeTag(String key) {
        mcc$children.remove(key);
        mcc$childTags.remove(key);
        mcc$tag.remove(key);
        mcc$markDirty();
    }

    public boolean hasNoTags() {
        return mcc$tag.isEmpty();
    }

    /** {@link #hasNoTags} under the name later 1.12 mappings gave it. */
    public boolean isEmpty() {
        return hasNoTags();
    }

    @Override
    public NBTTagCompound copy() {
        return new NBTTagCompound(mcc$modern().copy());
    }

    public int getSize() {
        return mcc$tag.size();
    }

    public void setUniqueId(String key, java.util.UUID value) {
        if (value == null) return; // 1.12 threw a NullPointerException here (Galacticraft's laser turret without an owner); an unset id reads back as absent
        setLong(key + "Most", value.getMostSignificantBits());
        setLong(key + "Least", value.getLeastSignificantBits());
    }

    public java.util.UUID getUniqueId(String key) {
        return new java.util.UUID(getLong(key + "Most"), getLong(key + "Least"));
    }

    public boolean hasUniqueId(String key) {
        return hasKey(key + "Most", 4) && hasKey(key + "Least", 4);
    }

    public void merge(NBTTagCompound other) {
        for (String key : other.getKeySet()) {
            NBTBase value = other.getTag(key);
            if (value instanceof NBTTagCompound inner && hasKey(key, 10)) {
                getCompoundTag(key).merge(inner);
            } else {
                setTag(key, value.copy());
            }
        }
    }
}
