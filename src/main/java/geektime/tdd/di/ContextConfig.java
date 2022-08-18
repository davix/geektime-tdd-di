package geektime.tdd.di;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

public class ContextConfig {
    private Map<Class<?>, Provider<?>> providers = new HashMap<>();

    interface Provider<T> {
        T get(Context context);

        default List<Class<?>> getDependencies() {
            return List.of();
        }

        default List<Type> getDependencyTypes() {
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
            @Override
            public <T> Optional<T> get(Class<T> type) {
                return Optional.ofNullable(providers.get(type)).map(p -> (T) p.get(this));
            }

            @Override
            public Optional get(ParameterizedType type) {
                if (type.getRawType() != jakarta.inject.Provider.class) return Optional.empty();
                Class<?> ComponentType = (Class<?>) type.getActualTypeArguments()[0];
                return Optional.ofNullable(providers.get(ComponentType))
                        .map(provider -> (jakarta.inject.Provider<Object>) () -> provider.get(this));
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

}
