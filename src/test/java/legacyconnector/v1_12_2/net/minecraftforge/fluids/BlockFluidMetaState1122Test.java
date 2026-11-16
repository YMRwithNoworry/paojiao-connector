package legacyconnector.v1_12_2.net.minecraftforge.fluids;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import legacyconnector.v1_12_2.net.minecraft.block.Block;
import legacyconnector.v1_12_2.net.minecraft.block.material.Material;
import legacyconnector.v1_12_2.net.minecraft.block.properties.IProperty;
import legacyconnector.v1_12_2.net.minecraft.block.properties.PropertyBool;
import legacyconnector.v1_12_2.net.minecraft.block.state.BlockStateContainer;
import legacyconnector.v1_12_2.net.minecraft.block.state.IBlockState;
import legacyconnector.v1_12_2.net.minecraftforge.common.property.ExtendedBlockState;
import legacyconnector.v1_12_2.net.minecraftforge.common.property.IExtendedBlockState;
import legacyconnector.v1_12_2.net.minecraftforge.common.property.IUnlistedProperty;
import legacyconnector.v1_12_2.net.minecraftforge.common.property.PropertyFloat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockFluidMetaState1122Test {
    private static final PropertyBool EXTRA = PropertyBool.create("extra");
    private static final PropertyFloat RENDER = new PropertyFloat("render");
    private static final AtomicInteger CUSTOM_CALLS = new AtomicInteger();

    @BeforeAll
    static void bootstrap() {
        net.neoforged.fml.loading.LoadingModList.of(
                List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void cachesEachMaskedLevelForEachFluid() {
        BlockFluidClassic first = new BlockFluidClassic(fluid("first"), Material.WATER);
        BlockFluidFinite second = new BlockFluidFinite(fluid("second"), Material.WATER);
        for (int level = 0; level < 16; level++) {
            IBlockState state = first.getStateFromMeta(level);
            assertEquals(level, state.getValue(BlockFluidBase.LEVEL));
            assertEquals(level, first.getMetaFromState(state));
            assertSame(state, first.getStateFromMeta(level + 16));
            assertNotSame(state, second.getStateFromMeta(level));
            assertSame(first, state.getBlock());
        }
        assertSame(first.getStateFromMeta(15), first.getStateFromMeta(-1));
        assertSame(first.getStateFromMeta(0), first.getStateFromMeta(Integer.MIN_VALUE));
    }

    @Test
    void cachedStatesKeepUnlistedValuesIsolatedAndFollowNewDefaults() {
        ExtraFluid block = new ExtraFluid();
        IExtendedBlockState original = (IExtendedBlockState) block.getStateFromMeta(3);
        IExtendedBlockState rendered = original.withProperty(RENDER, 0.25F);
        assertNull(original.getValue(RENDER));
        assertEquals(0.25F, rendered.getValue(RENDER));
        assertNull(((IExtendedBlockState) block.getStateFromMeta(3)).getValue(RENDER));
        assertNull(((IExtendedBlockState) block.getStateFromMeta(4)).getValue(RENDER));

        boolean before = original.getValue(EXTRA);
        block.replaceDefault(!before, 0.75F);
        IExtendedBlockState updated = (IExtendedBlockState) block.getStateFromMeta(3);
        assertNotSame(original, updated);
        assertEquals(!before, updated.getValue(EXTRA));
        assertEquals(3, updated.getValue(BlockFluidBase.LEVEL));
        assertEquals(0.75F, updated.getValue(RENDER));
        assertEquals(before, original.getValue(EXTRA));
        assertNull(original.getValue(RENDER));
        assertSame(updated, block.getStateFromMeta(3));
    }

    @Test
    void customListedStatesAreStillAskedOnEveryCall() {
        TrackingFluid block = new TrackingFluid();
        CUSTOM_CALLS.set(0);
        assertEquals(5, block.getStateFromMeta(5).getValue(BlockFluidBase.LEVEL));
        assertEquals(5, block.getStateFromMeta(5).getValue(BlockFluidBase.LEVEL));
        assertEquals(2, CUSTOM_CALLS.get());
    }

    @Test
    void aStandardBaseDoesNotCacheCustomNeighbourStates() {
        TrackingFluid block = new MixedTrackingFluid();
        assertSame(block.getStateFromMeta(0), block.getStateFromMeta(0));
        IBlockState first = block.getStateFromMeta(1);
        IBlockState second = block.getStateFromMeta(1);
        assertNotSame(first, second);
        assertEquals(1, second.getValue(BlockFluidBase.LEVEL));
    }

    @Test
    void concurrentReadsReturnCorrectStates() throws Exception {
        ExtraFluid block = new ExtraFluid();
        var executor = Executors.newFixedThreadPool(8);
        var start = new CountDownLatch(1);
        try {
            var tasks = new ArrayList<java.util.concurrent.Future<?>>();
            for (int worker = 0; worker < 8; worker++) {
                tasks.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < 2000; i++) {
                        IExtendedBlockState state = (IExtendedBlockState) block.getStateFromMeta(i);
                        assertEquals(i & 15, state.getValue(BlockFluidBase.LEVEL));
                        assertNull(state.getValue(RENDER));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var task : tasks) task.get();
        } finally {
            executor.shutdownNow();
        }
    }

    private static Fluid fluid(String name) {
        return new Fluid(name, null, null);
    }

    private static final class ExtraFluid extends BlockFluidClassic {
        ExtraFluid() {
            super(fluid("extra"), Material.WATER);
        }

        @Override
        public BlockStateContainer createBlockState() {
            return new ExtendedBlockState(this, new IProperty<?>[] {LEVEL, EXTRA}, new IUnlistedProperty<?>[] {RENDER});
        }

        void replaceDefault(boolean extra, float render) {
            IBlockState next = getDefaultState().withProperty(EXTRA, extra);
            setDefaultState(((IExtendedBlockState) next).withProperty(RENDER, render));
        }
    }

    private static class TrackingFluid extends BlockFluidClassic {
        TrackingFluid() {
            super(fluid("tracking"), Material.WATER);
        }

        @Override
        public BlockStateContainer createBlockState() {
            return trackingStates(this, false);
        }
    }

    private static final class MixedTrackingFluid extends TrackingFluid {
        @Override
        public BlockStateContainer createBlockState() {
            return trackingStates(this, true);
        }
    }

    private static BlockStateContainer trackingStates(Block block, boolean mixed) {
        return new ExtendedBlockState(block, new IProperty<?>[] {BlockFluidBase.LEVEL}, new IUnlistedProperty<?>[0]) {
            @Override
            public StateImplementation createState(Block owner, ImmutableMap<IProperty<?>, Comparable<?>> properties) {
                if (mixed && Integer.valueOf(0).equals(properties.get(BlockFluidBase.LEVEL))) return super.createState(owner, properties);
                return new TrackingState(owner, properties);
            }
        };
    }

    private static final class TrackingState extends BlockStateContainer.StateImplementation {
        TrackingState(Block block, ImmutableMap<IProperty<?>, Comparable<?>> properties) {
            super(block, properties);
        }

        @Override
        public <T extends Comparable<T>, V extends T> IBlockState withProperty(IProperty<T> property, V value) {
            CUSTOM_CALLS.incrementAndGet();
            return super.withProperty(property, value);
        }
    }
}
