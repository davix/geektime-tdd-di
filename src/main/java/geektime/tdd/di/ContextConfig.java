package geektime.tdd.di;

import jakarta.inject.Inject;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;

public class ContextConfig {
    private Map<Class<?>, Provider<?>> providers = new HashMap<>();

    interface Provider<T> {
        T get(Context context);
    }

    public <T> void bind(Class<T> type, T instance) {
        providers.put(type, (Provider<T>) context -> instance);
    }

    public <T, Impl extends T> void bind(Class<T> type, Class<Impl> implementation) {
        Constructor<Impl> constructor = getConstructor(implementation);

        providers.put(type, new ConstructorProvider<>(type, constructor));
    }

    public Context getContext() {
        return new Context() {
            @Override
            public <T> Optional<T> get(Class<T> type) {
                return Optional.ofNullable(providers.get(type)).map(p -> (T) p.get(this));
            }
        };
    }

    class ConstructorProvider<T> implements Provider<T> {
        private Class<?> type;
        private Constructor<T> constructor;
        private boolean constructing = false;

        public ConstructorProvider(Class<?> type, Constructor<T> constructor) {
            this.type = type;
            this.constructor = constructor;
        }

        @Override
        public T get(Context context) {
            if (constructing) throw new CyclicDependenciesFound(type);
            try {
                constructing = true;
                Object[] dependencies = stream(constructor.getParameters())
                        .map(p -> context.get(p.getType()).orElseThrow(() -> new DependencyNotFoundException(type, p.getType())))
                        .toArray(Object[]::new);
                return (T) constructor.newInstance(dependencies);
            } catch (CyclicDependenciesFound e) {
                throw new CyclicDependenciesFound(type, e);
            } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            } finally {
                constructing = false;
            }
        }
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
}
