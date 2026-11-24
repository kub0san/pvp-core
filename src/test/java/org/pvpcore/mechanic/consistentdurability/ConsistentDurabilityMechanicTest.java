package org.pvpcore.mechanic.consistentdurability;

import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ConsistentDurabilityMechanicTest {
    private ServerMock server;
    private PluginMock plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Regression for COMBAT2-13: a late cancellation must not leave a phantom durability counter. */
    @Test
    void cancelledDamageDoesNotWriteCounter() throws Exception {
        ConsistentDurabilityMechanic mechanic = new ConsistentDurabilityMechanic();
        Field active = ConsistentDurabilityMechanic.class.getDeclaredField("active");
        active.setAccessible(true);
        active.setBoolean(mechanic, true);
        Bukkit.getPluginManager().registerEvents(mechanic, plugin);
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.HIGHEST)
            public void cancel(PlayerItemDamageEvent event) {
                event.setCancelled(true);
            }
        }, plugin);

        ItemStack item = new ItemStack(Material.DIAMOND_CHESTPLATE);
        item.addEnchantment(Enchantment.UNBREAKING, 3);
        item.setData(DataComponentTypes.MAX_DAMAGE, 100);
        item.setData(DataComponentTypes.DAMAGE, 10);
        PlayerItemDamageEvent event = new PlayerItemDamageEvent(server.addPlayer(), item, 1);
        Bukkit.getPluginManager().callEvent(event);

        assertFalse(item.getItemMeta().getPersistentDataContainer()
                        .has(ConsistentDurabilityMechanic.DURABILITY_KEY, PersistentDataType.DOUBLE),
                "a cancelled damage event must not advance the fractional durability counter");
    }

    @Test
    void uncancelledDamageStillUsesAverageDurability() throws Exception {
        ConsistentDurabilityMechanic mechanic = new ConsistentDurabilityMechanic();
        Field active = ConsistentDurabilityMechanic.class.getDeclaredField("active");
        active.setAccessible(true);
        active.setBoolean(mechanic, true);
        Bukkit.getPluginManager().registerEvents(mechanic, plugin);

        ItemStack item = new ItemStack(Material.DIAMOND_CHESTPLATE);
        item.addEnchantment(Enchantment.UNBREAKING, 3);
        item.setData(DataComponentTypes.MAX_DAMAGE, 100);
        item.setData(DataComponentTypes.DAMAGE, 10);
        PlayerItemDamageEvent event = new PlayerItemDamageEvent(server.addPlayer(), item, 1);
        Bukkit.getPluginManager().callEvent(event);

        org.junit.jupiter.api.Assertions.assertTrue(item.getItemMeta().getPersistentDataContainer()
                        .has(ConsistentDurabilityMechanic.DURABILITY_KEY, PersistentDataType.DOUBLE),
                "normal damage must still apply the Unbreaking average");
    }
}
