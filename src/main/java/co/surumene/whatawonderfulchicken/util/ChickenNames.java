package co.surumene.whatawonderfulchicken.util;

import net.kyori.adventure.text.Component;

public final class ChickenNames {
    private ChickenNames() {}
    public static boolean isNamed(Component name) { return name != null && !name.equals(Component.empty()); }
    public static Component display(Component name, String fallback) { return isNamed(name) ? name : Component.text(fallback); }
}
