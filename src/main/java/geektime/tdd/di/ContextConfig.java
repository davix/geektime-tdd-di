package geektime.tdd.di;

import java.util.*;

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

}
