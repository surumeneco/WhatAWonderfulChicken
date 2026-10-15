package co.surumene.whatawonderfulchicken.chickentrap;

import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Trap ownership survives entity unloads and plugin reloads without a global save file. */
public final class ChickenTrapStateStore {
    public static final String CHARGE = "charge";
    public static final String VISUAL = "visual";
    public static final String ARMED = "armed";
    public static final String RIDER = "rider";
    public static final String MOUNT = "mount";

    private final NamespacedKey roleKey;
    private final NamespacedKey pairKey;
    private final NamespacedKey graceKey;
    private final NamespacedKey expiryKey;

    public ChickenTrapStateStore(Plugin plugin) {
        roleKey = new NamespacedKey(plugin, "chicken_trap_role");
        pairKey = new NamespacedKey(plugin, "chicken_trap_partner");
        graceKey = new NamespacedKey(plugin, "chicken_trap_grace");
        expiryKey = new NamespacedKey(plugin, "chicken_trap_expiry");
    }

    public void mark(Entity entity, String role, long expires) {
        var data = entity.getPersistentDataContainer();
        data.set(roleKey, PersistentDataType.STRING, role);
        data.set(expiryKey, PersistentDataType.LONG, expires);
    }

    public String role(Entity entity) {
        return entity.getPersistentDataContainer().get(roleKey, PersistentDataType.STRING);
    }

    public boolean hasRole(Entity entity, String role) {
        return role.equals(role(entity));
    }

    public long expiry(Entity entity) {
        return entity.getPersistentDataContainer().getOrDefault(expiryKey, PersistentDataType.LONG, 0L);
    }

    public void pair(Entity entity, UUID partner) {
        entity.getPersistentDataContainer().set(pairKey, PersistentDataType.STRING, partner.toString());
    }

    public UUID partner(Entity entity) {
        String value = entity.getPersistentDataContainer().get(pairKey, PersistentDataType.STRING);
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    public void graceUntil(Entity entity, long milliseconds) {
        entity.getPersistentDataContainer().set(graceKey, PersistentDataType.LONG, milliseconds);
    }

    public long graceUntil(Entity entity) {
        return entity.getPersistentDataContainer().getOrDefault(graceKey, PersistentDataType.LONG, 0L);
    }

    public void clear(Entity entity) {
        var data = entity.getPersistentDataContainer();
        data.remove(roleKey);
        data.remove(pairKey);
        data.remove(graceKey);
        data.remove(expiryKey);
    }
}
