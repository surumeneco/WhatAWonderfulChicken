package co.surumene.whatawonderfulchicken.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChickenDisplayNameTest {
    @Test void preservesStyledName() {
        Component name = Component.text("Named", NamedTextColor.AQUA);
        assertTrue(ChickenDisplayName.hasCustomName(name));
        assertSame(name, ChickenDisplayName.resolve(name, Component.text("fallback")));
    }
    @Test void rejectsEmptyOrWhitespaceOnlyName() {
        Component fallback = Component.text("fallback");
        assertFalse(ChickenDisplayName.hasCustomName((Component) null));
        assertFalse(ChickenDisplayName.hasCustomName(Component.empty()));
        assertFalse(ChickenDisplayName.hasCustomName(Component.text(" ")));
        assertSame(fallback, ChickenDisplayName.resolve((Component) null, fallback));
        assertSame(fallback, ChickenDisplayName.resolve(Component.empty(), fallback));
        assertSame(fallback, ChickenDisplayName.resolve(Component.text(" "), fallback));
    }
    @Test void childTextIsVisibleName() {
        Component name = Component.empty().append(Component.text("Visible"));
        assertTrue(ChickenDisplayName.hasCustomName(name));
        assertSame(name, ChickenDisplayName.resolve(name, Component.text("fallback")));
    }
}
