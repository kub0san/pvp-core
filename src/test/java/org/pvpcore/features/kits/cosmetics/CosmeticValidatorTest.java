package org.pvpcore.features.kits.cosmetics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.pvpcore.config.kits.CustomizeRules;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmeticValidatorTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void tagPolicyIsStrict() {
        CosmeticValidator validator = new CosmeticValidator(CustomizeRules.parse(null));
        // Adventure's virtual gradient components do not implement structural equals
        // (a default parse is not even equal to itself), so compare serialized forms.
        String gradient = "<gradient:red:blue>Banner</gradient>";
        assertEquals(MiniMessage.miniMessage().serialize(MiniMessage.miniMessage().deserialize(gradient)),
                MiniMessage.miniMessage().serialize(validator.parseName(gradient)));
        assertThrows(IllegalArgumentException.class,
                () -> validator.parseName("<click:run_command:/spawn>bad</click>"));
        assertThrows(IllegalArgumentException.class,
                () -> validator.parseName("<hover:show_text:'tip'>bad</hover>"));
    }

    @Test
    void blockedTagsAreDistinguishableFromOtherInvalidNames() {
        CosmeticValidator validator = new CosmeticValidator(CustomizeRules.parse(null));
        assertThrows(CosmeticValidator.BlockedTagException.class,
                () -> validator.parseName("<click:run_command:/spawn>bad</click>"));
        assertThrows(CosmeticValidator.BlockedTagException.class,
                () -> validator.parseName("<keybind:key.jump>bad</keybind>"));

        MemoryConfiguration shortName = new MemoryConfiguration();
        shortName.set("max-name-length", 2);
        CosmeticValidator strict = new CosmeticValidator(CustomizeRules.parse(shortName));
        IllegalArgumentException tooLong = assertThrows(IllegalArgumentException.class,
                () -> strict.parseName("toolong"));
        assertFalse(tooLong instanceof CosmeticValidator.BlockedTagException);
    }

    @Test
    void clickAndHoverAreOptIn() {
        MemoryConfiguration section = new MemoryConfiguration();
        section.set("allow-click-events", true);
        section.set("allow-hover-events", true);
        CosmeticValidator validator = new CosmeticValidator(CustomizeRules.parse(section));
        assertDoesNotThrow(() -> validator.parseName("<click:run_command:/spawn>go</click>"));
        assertDoesNotThrow(() -> validator.parseName("<hover:show_text:'tip'>hi</hover>"));
    }

    @Test
    void rawLengthIsCheckedBeforeParsing() {
        MemoryConfiguration section = new MemoryConfiguration();
        section.set("max-name-length", 4);
        CosmeticValidator validator = new CosmeticValidator(CustomizeRules.parse(section));
        assertEquals(Component.text("ok"), validator.parseName("ok"));
        assertThrows(IllegalArgumentException.class, () -> validator.parseName("12345"));
    }

    @Test
    void itemTypeChecksUseMeta() {
        CosmeticValidator validator = new CosmeticValidator(CustomizeRules.parse(null));
        assertTrue(validator.isArmor(new ItemStack(Material.DIAMOND_CHESTPLATE)));
        assertFalse(validator.isArmor(new ItemStack(Material.SHIELD)));
    }
}
