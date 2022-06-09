package geektime.tdd.di;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static java.util.Arrays.stream;

public class Context {
    private Map<Class<?>, Provider<?>> providers = new HashMap<>();

    public <T> void bind(Class<T> type, T instance) {
        providers.put(type, (Provider<T>) () -> instance);
    }

    public <T, Impl extends T> void bind(Class<T> type, Class<Impl> implementation) {
        providers.put(type, (Provider<T>) () -> {
            try {
                Constructor<Impl> constructor = getConstructor(implementation);
                Object[] dependencies = stream(constructor.getParameters())
                        .map(p -> get(p.getType()))
                        .toArray(Object[]::new);
                return (T) constructor.newInstance(dependencies);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private <T> Constructor<T> getConstructor(Class<T> implementation) {
        return (Constructor<T>) stream(implementation.getConstructors())
                .filter(c -> c.isAnnotationPresent(Inject.class))
                .findFirst().orElseGet(() -> {
                    try {
                        return implementation.getConstructor();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    public <T> T get(Class<T> type) {
        return (T) providers.get(type).get();
    }
}
