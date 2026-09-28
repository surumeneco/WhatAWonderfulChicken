package co.surumene.whatawonderfulchicken.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Chicken;

public final class ChickenDisplayName {
    private ChickenDisplayName() {}

    public static boolean hasCustomName(Chicken chicken) {
        return hasCustomName(chicken.customName());
    }

    public static Component resolve(Chicken chicken, Component fallback) {
        return resolve(chicken.customName(), fallback);
    }

    static boolean hasCustomName(Component name) {
        return name != null && !PlainTextComponentSerializer.plainText().serialize(name).isBlank();
    }

    static Component resolve(Component name, Component fallback) {
        return hasCustomName(name) ? name : fallback;
    }
}
