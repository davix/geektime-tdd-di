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

            private <T> Optional<T> getC(Class<T> type) {
                return Optional.ofNullable(providers.get(type)).map(p -> (T) p.get(this));
            }

            private Optional<?> getT(ParameterizedType type) {
                if (type.getRawType() != jakarta.inject.Provider.class) return Optional.empty();
                Class<?> ComponentType = (Class<?>) type.getActualTypeArguments()[0];
                return Optional.ofNullable(providers.get(ComponentType))
                        .map(provider -> (jakarta.inject.Provider<Object>) () -> provider.get(this));
            }

            @Override
            public Optional getType(Type type) {
                if (type instanceof ParameterizedType)
                    return getT((ParameterizedType) type);
                else
                    return getC((Class<?>) type);
            }
        };
    }

    private void checkDependencies(Class<?> c, Stack<Class<?>> visiting) {
        for (Type d : providers.get(c).getDependencies()) {
            if (d instanceof Class)
                checkDependency(c, visiting, (Class<?>) d);
            if (d instanceof ParameterizedType) {
                Class<?> type = (Class<?>) ((ParameterizedType) d).getActualTypeArguments()[0];
                if (!providers.containsKey(type))
                    throw new DependencyNotFoundException(c, type);
            }
        }
    }

    private void checkDependency(Class<?> c, Stack<Class<?>> visiting, Class<?> d) {
        if (!providers.containsKey(d))
            throw new DependencyNotFoundException(c, d);
        if (visiting.contains(d))
            throw new CyclicDependenciesFound(visiting);
        visiting.push(d);
        checkDependencies(d, visiting);
        visiting.pop();
    }

}
