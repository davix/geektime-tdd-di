package geektime.tdd.di;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;

public class Context {
    private Map<Class<?>, Provider<?>> providers = new HashMap<>();

    public <T> void bind(Class<T> type, T instance) {
        providers.put(type, (Provider<T>) () -> instance);
    }

    public <T, Impl extends T> void bind(Class<T> type, Class<Impl> implementation) {
        Constructor<Impl> constructor = getConstructor(implementation);

        providers.put(type, (Provider<T>) () -> {
            try {
                Object[] dependencies = stream(constructor.getParameters())
                        .map(p -> get(p.getType()).orElseThrow(DependencyNotFoundException::new))
                        .toArray(Object[]::new);
                return (T) constructor.newInstance(dependencies);
            } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private <T> Constructor<T> getConstructor(Class<T> implementation) {
        List<Constructor<?>> constructors = stream(implementation.getConstructors())
                .filter(c -> c.isAnnotationPresent(Inject.class)).collect(Collectors.toList());
        if (constructors.size() > 1) throw new IllegalComponentException();

        return (Constructor<T>) constructors.stream()
                .findFirst().orElseGet(() -> {
                    try {
                        return implementation.getConstructor();
                    } catch (Exception e) {
                        throw new IllegalComponentException();
                    }
                });
    }

    public <T> Optional<T> get(Class<T> type) {
        return Optional.ofNullable(providers.get(type)).map(p -> (T) p.get());
    }

}
