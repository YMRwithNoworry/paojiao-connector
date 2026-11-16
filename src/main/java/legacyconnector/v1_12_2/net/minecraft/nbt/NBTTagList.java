package legacyconnector.v1_12_2.net.minecraft.nbt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;

public class NBTTagList extends NBTBase implements Iterable<NBTBase> {
    private final ListTag mcc$list;
    private final Map<Integer, NBTBase> mcc$children = new HashMap<>();
    private final Map<Integer, Tag> mcc$childTags = new HashMap<>();

    /** Phantom field: accesses are redirected to {@link #mcc$get$tagList} (mods read the tags directly). */
    @Deprecated
    public java.util.List tagList;

    /** A live view of the tags as legacy wrappers; writes go through to the list. */
    public static java.util.List mcc$get$tagList(NBTTagList list) {
        return new java.util.AbstractList<NBTBase>() {
            @Override
            public NBTBase get(int index) {
                return list.get(index);
            }

            @Override
            public int size() {
                return list.mcc$list.size();
            }

            @Override
            public NBTBase set(int index, NBTBase value) {
                return list.mcc$replace(index, value);
            }

            @Override
            public void add(int index, NBTBase value) {
                list.mcc$insert(index, value);
            }

            @Override
            public NBTBase remove(int index) {
                return list.mcc$remove(index);
            }
        };
    }

    public NBTTagList() {
        this(new ListTag());
    }

    public NBTTagList(ListTag list) {
        this.mcc$list = list;
    }

    @Override
    public ListTag mcc$modern() {
        mcc$syncMutableChildren();
        return mcc$list;
    }

    private void mcc$syncMutableChildren() {
        for (int index : new ArrayList<>(mcc$children.keySet())) {
            NBTBase child = mcc$children.get(index);
            if (index >= mcc$list.size()) {
                mcc$children.remove(index);
                mcc$childTags.remove(index);
                continue;
            }
            Tag current = mcc$list.get(index);
            if (current == mcc$childTags.get(index)) {
                Tag updated = child.mcc$modern();
                mcc$list.set(index, updated);
                mcc$childTags.put(index, updated);
            } else {
                mcc$children.remove(index);
                mcc$childTags.remove(index);
            }
        }
    }

    private NBTBase mcc$replace(int index, NBTBase value) {
        NBTBase old = get(index);
        Tag modern = value.mcc$modern();
        mcc$list.set(index, modern);
        value.mcc$parent = this;
        mcc$children.put(index, value);
        mcc$childTags.put(index, modern);
        mcc$markDirty();
        return old;
    }

    private void mcc$insert(int index, NBTBase value) {
        Tag modern = value.mcc$modern();
        mcc$list.add(index, modern);
        shiftChildren(index, 1);
        value.mcc$parent = this;
        mcc$children.put(index, value);
        mcc$childTags.put(index, modern);
        mcc$markDirty();
    }

    private NBTBase mcc$remove(int index) {
        NBTBase old = get(index);
        mcc$list.remove(index);
        mcc$children.remove(index);
        mcc$childTags.remove(index);
        shiftChildren(index + 1, -1);
        mcc$markDirty();
        return old;
    }

    private void shiftChildren(int from, int delta) {
        var indices = new ArrayList<>(mcc$children.keySet());
        indices.sort((a, b) -> delta > 0 ? Integer.compare(b, a) : Integer.compare(a, b));
        for (int index : indices) {
            if (index >= from) {
                mcc$children.put(index + delta, mcc$children.remove(index));
                Tag backing = mcc$childTags.remove(index);
                if (backing != null) mcc$childTags.put(index + delta, backing);
            }
        }
    }

    @Override
    public byte getId() {
        return 9;
    }

    public void appendTag(NBTBase tag) {
        if (mcc$list.isEmpty() || mcc$list.getElementType() == tag.getId()) {
            mcc$insert(mcc$list.size(), tag);
        }
        // 1.12.2 logged "Adding mismatching tag types to tag list" and dropped the tag.
    }

    public void setTag(int index, NBTBase tag) {
        mcc$replace(index, tag);
    }

    public NBTBase removeTag(int index) {
        return mcc$remove(index);
    }

    public NBTTagCompound getCompoundTagAt(int index) {
        NBTBase tag = get(index);
        return tag instanceof NBTTagCompound compound ? compound : new NBTTagCompound();
    }

    public int[] getIntArrayAt(int index) {
        NBTBase tag = get(index);
        return tag instanceof NBTTagIntArray array ? array.getIntArray() : new int[0];
    }

    public double getDoubleAt(int index) {
        return index >= 0 && index < mcc$list.size() && mcc$list.get(index) instanceof NumericTag n ? n.getAsDouble() : 0;
    }

    public float getFloatAt(int index) {
        return index >= 0 && index < mcc$list.size() && mcc$list.get(index) instanceof NumericTag n ? n.getAsFloat() : 0;
    }

    public String getStringTagAt(int index) {
        return index >= 0 && index < mcc$list.size() ? mcc$list.get(index).getAsString() : "";
    }

    public int tagCount() {
        return mcc$list.size();
    }

    public int getTagType() {
        return mcc$list.getElementType();
    }

    @Override
    public NBTTagList copy() {
        return new NBTTagList(mcc$modern().copy());
    }

    /** The tag at {@code index} (a live view for compounds and lists), or an end tag when out of range. */
    public NBTBase get(int index) {
        if (index >= 0 && index < mcc$list.size()) {
            NBTBase cached = mcc$children.get(index);
            Tag current = mcc$list.get(index);
            if (cached != null && current == mcc$childTags.get(index)) return cached;
            NBTBase view = NBTBase.mcc$wrap(current, this);
            if (view != null) {
                mcc$children.put(index, view);
                mcc$childTags.put(index, current);
            }
            return view == null ? new NBTTagEnd() : view;
        }
        return new NBTTagEnd();
    }

    public void set(int index, NBTBase tag) {
        if (index >= 0 && index < mcc$list.size() && (mcc$list.size() == 1 || mcc$list.getElementType() == tag.getId())) {
            mcc$replace(index, tag);
        }
    }

    public int getIntAt(int index) {
        return index >= 0 && index < mcc$list.size() && mcc$list.get(index) instanceof net.minecraft.nbt.IntTag t ? t.getAsInt() : 0;
    }

    @Override
    public boolean isEmpty() {
        return mcc$list.isEmpty();
    }

    @Override
    public java.util.Iterator<NBTBase> iterator() {
        return mcc$get$tagList(this).iterator();
    }

    /** Declared so mods' calls link here (Forge's NBTTagList is Iterable). */
    @Override
    public void forEach(java.util.function.Consumer<? super NBTBase> action) {
        for (NBTBase tag : this) action.accept(tag);
    }

    @Override
    public java.util.Spliterator<NBTBase> spliterator() {
        return mcc$get$tagList(this).spliterator();
    }
}
