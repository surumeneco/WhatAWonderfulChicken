package co.surumene.whatawonderfulchicken.config;

import co.surumene.whatawonderfulchicken.genome.*;
import co.surumene.whatawonderfulchicken.breeding.*;
import org.bukkit.configuration.ConfigurationSection;
import java.lang.reflect.*;

final class RecordConfigurationParser {
    private RecordConfigurationParser() {}
    @SuppressWarnings("unchecked")
    static <T> T load(ConfigurationSection config, T defaults, String prefix) {
        Class<?> type = defaults.getClass();
        RecordComponent[] parts = type.getRecordComponents();
        Object[] args = new Object[parts.length];
        Class<?>[] parameterTypes = new Class<?>[parts.length];
        for (int i = 0; i < parts.length; i++) {
            var part = parts[i];
            parameterTypes[i] = part.getType();
            try {
                Object previous = part.getAccessor().invoke(defaults);
                String path = ConfigProfilePaths.path(type, prefix, part.getName());
                if (part.getType().isRecord()) {
                    args[i] = load(config, previous, path);
                } else {
                    Object value = config.get(path);
                    if (value == null) value = previous;
                    if (!(value instanceof Number number)) {
                        throw new IllegalArgumentException(path + " must be numeric");
                    }
                    double raw = number.doubleValue();
                    if (!Double.isFinite(raw)) throw new IllegalArgumentException(path + " must be finite");
                    if (part.getType() == double.class) args[i] = raw;
                    else if (part.getType() == int.class && raw == number.intValue()) args[i] = number.intValue();
                    else throw new IllegalArgumentException(path + " must be an integer");
                }
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot inspect Chicken profile record", failure);
            }
        }
        try {
            Constructor<?> ctor = type.getDeclaredConstructor(parameterTypes);
            return (T) ctor.newInstance(args);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException error) throw error;
            throw new IllegalArgumentException("Invalid Chicken profile settings", failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot instantiate Chicken profile record", failure);
        }
    }
}
