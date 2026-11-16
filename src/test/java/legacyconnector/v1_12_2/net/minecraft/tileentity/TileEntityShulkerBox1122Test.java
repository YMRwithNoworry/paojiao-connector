package legacyconnector.v1_12_2.net.minecraft.tileentity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import legacyconnector.v1_12_2.net.minecraft.item.EnumDyeColor;
import org.junit.jupiter.api.Test;

class TileEntityShulkerBox1122Test {
    @Test
    void preservesTheConfiguredLegacyColor() {
        TileEntityShulkerBox box = new TileEntityShulkerBox(EnumDyeColor.RED);

        assertEquals(EnumDyeColor.RED, box.getColor());
    }
}
