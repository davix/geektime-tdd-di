package geektime.tdd.di;

import jakarta.inject.Inject;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;

public class ContextConfig {
    private Map<Class<?>, Provider<?>> providers = new HashMap<>();

    interface Provider<T> {
        T get(Context context);

        List<Class<?>> getDependencies();
    }

    public <T> void bind(Class<T> type, T instance) {
        providers.put(type, new Provider<T>() {

            @Override
            public T get(Context context) {
                return instance;
            }

            @Override
            public List<Class<?>> getDependencies() {
                return List.of();
            }
        });
    }

    public <T, Impl extends T> void bind(Class<T> type, Class<Impl> implementation) {
        providers.put(type, new ConstructorProvider<>(implementation));
    }

    public Context getContext() {
        providers.keySet().forEach(c -> checkDependencies(c, new Stack<>()));
        return new Context() {
            @Override
            public <T> Optional<T> get(Class<T> type) {
                return Optional.ofNullable(providers.get(type)).map(p -> (T) p.get(this));
            }
        };
    }

    private void checkDependencies(Class<?> c, Stack<Class<?>> visiting) {
        for (Class<?> d : providers.get(c).getDependencies()) {
            if (!providers.containsKey(d))
                throw new DependencyNotFoundException(c, d);
            if (visiting.contains(d))
                throw new CyclicDependenciesFound(visiting);
            visiting.push(d);
            checkDependencies(d, visiting);
            visiting.pop();
        }
    }

    class ConstructorProvider<T> implements Provider<T> {
        private Constructor<T> constructor;

        public ConstructorProvider(Class<T> component) {
            this.constructor = getConstructor(component);
        }

        private static <T> Constructor<T> getConstructor(Class<T> implementation) {
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

        @Override
        public T get(Context context) {
            try {
                Object[] dependencies = stream(constructor.getParameters())
                        .map(p -> context.get(p.getType()).get())
                        .toArray(Object[]::new);
                return (T) constructor.newInstance(dependencies);
            } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public List<Class<?>> getDependencies() {
            return stream(constructor.getParameters()).map(Parameter::getType).collect(Collectors.toList());
        }
    }

}
