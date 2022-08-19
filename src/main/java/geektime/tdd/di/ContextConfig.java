package geektime.tdd.di;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

public class ContextConfig {
    private Map<Class<?>, Provider<?>> providers = new HashMap<>();

    interface Provider<T> {
        T get(Context context);

        default List<Type> getDependencies() {
            return List.of();
        }

    }

    public <T> void bind(Class<T> type, T instance) {
        providers.put(type, (Provider<T>) context -> instance);
    }

    public <T, Impl extends T> void bind(Class<T> type, Class<Impl> implementation) {
        providers.put(type, new InjectionProvider<>(implementation));
    }

    public Context getContext() {
        providers.keySet().forEach(c -> checkDependencies(c, new Stack<>()));
        return new Context() {

            private <T> Optional<T> getComponent(Class<T> type) {
                return Optional.ofNullable(providers.get(type)).map(p -> (T) p.get(this));
            }

            private Optional<?> getContainer(ParameterizedType type) {
                if (type.getRawType() != jakarta.inject.Provider.class) return Optional.empty();
                Class<?> ComponentType = getComponentType(type);
                return Optional.ofNullable(providers.get(ComponentType))
                        .map(provider -> (jakarta.inject.Provider<Object>) () -> provider.get(this));
            }

            @Override
            public Optional get(Type type) {
                if (isContainerType(type))
                    return getContainer((ParameterizedType) type);
                else
                    return getComponent((Class<?>) type);
            }
        };
    }

    private static Class<?> getComponentType(Type type) {
        return (Class<?>) ((ParameterizedType) type).getActualTypeArguments()[0];
    }

    private static boolean isContainerType(Type type) {
        return type instanceof ParameterizedType;
    }

    private void checkDependencies(Class<?> c, Stack<Class<?>> visiting) {
        for (Type d : providers.get(c).getDependencies()) {
            if (isContainerType(d)) checkContainerDependency(c, d);
            else checkComponentDependency(c, visiting, (Class<?>) d);
        }
    }

    private void checkContainerDependency(Class<?> c, Type d) {
        if (!providers.containsKey(getComponentType(d)))
            throw new DependencyNotFoundException(c, getComponentType(d));
    }

    private void checkComponentDependency(Class<?> c, Stack<Class<?>> visiting, Class<?> d) {
        if (!providers.containsKey(d))
            throw new DependencyNotFoundException(c, d);
        if (visiting.contains(d))
            throw new CyclicDependenciesFound(visiting);
        visiting.push(d);
        checkDependencies(d, visiting);
        visiting.pop();
    }

}
