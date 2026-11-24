package org.pvpcore.util.kits;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Regression for KIT2-15: one corrupt item length must not wipe the rest of the kit. */
class SerializerTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void corruptItemLengthKeepsEarlierItems() throws Exception {
        ItemStack[] original = {
                new ItemStack(Material.DIAMOND_SWORD),
                new ItemStack(Material.IRON_PICKAXE),
                new ItemStack(Material.GOLDEN_APPLE)
        };
        byte[] bytes = Base64.getDecoder().decode(Serializer.itemStackArrayToBase64(original));

        DataInputStream reader = new DataInputStream(new ByteArrayInputStream(bytes));
        reader.readInt(); // array length
        int firstLength = reader.readInt();
        int secondLengthOffset = 4 + 4 + firstLength;
        ByteBuffer.wrap(bytes).putInt(secondLengthOffset, 10 * 1024 * 1024 + 1);

        ItemStack[] decoded = Serializer.itemStackArrayFromBase64(Base64.getEncoder().encodeToString(bytes));

        assertEquals(3, decoded.length);
        assertEquals(Material.DIAMOND_SWORD, decoded[0].getType(),
                "valid items before the corruption must survive");
    }
}
