package geektime.tdd.di;

import jakarta.inject.Inject;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Arrays.stream;

public class ContextConfig {
    private Map<Class<?>, Provider<?>> providers = new HashMap<>();
    private Map<Class<?>, List<Class<?>>> dependencies = new HashMap<>();

    interface Provider<T> {
        T get(Context context);
    }

    public <T> void bind(Class<T> type, T instance) {
        providers.put(type, (Provider<T>) context -> instance);
        dependencies.put(type, asList());
    }

    public <T, Impl extends T> void bind(Class<T> type, Class<Impl> implementation) {
        Constructor<Impl> constructor = getConstructor(implementation);
        providers.put(type, new ConstructorProvider<>(type, constructor));
        dependencies.put(type, stream(constructor.getParameters()).map(Parameter::getType).collect(Collectors.toList()));
    }

    public Context getContext() {
        for (Class<?> c : dependencies.keySet()) {
            checkDependencies(c, new Stack<>());
        }
        return new Context() {
            @Override
            public <T> Optional<T> get(Class<T> type) {
                return Optional.ofNullable(providers.get(type)).map(p -> (T) p.get(this));
            }
        };
    }

    private void checkDependencies(Class<?> c, Stack<Class<?>> visiting) {
        for (Class<?> d : dependencies.get(c)) {
            if (!dependencies.containsKey(d))
                throw new DependencyNotFoundException(c, d);
            if (visiting.contains(d))
                throw new CyclicDependenciesFound(visiting);
            visiting.push(d);
            checkDependencies(d, visiting);
            visiting.pop();
        }
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
