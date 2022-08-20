package geektime.tdd.di;

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

            @Override
            public Optional get(Type type) {
                return get(Ref.of(type));
            }

            @Override
            public Optional<?> get(Ref ref) {
                if (ref.isContainer()) {
                    if (ref.getContainer() != jakarta.inject.Provider.class)
                        return Optional.empty();
                    return Optional.ofNullable(providers.get(ref.getComponent()))
                            .map(provider -> (jakarta.inject.Provider<Object>) () -> provider.get(this));
                } else
                    return Optional.ofNullable(providers.get(ref.getComponent())).map(p -> p.get(this));
            }
        };
    }

    private void checkDependencies(Class<?> c, Stack<Class<?>> visiting) {
        for (Type d : providers.get(c).getDependencies()) {
            Context.Ref ref = Context.Ref.of(d);
            Class<?> comp = ref.getComponent();
            if (!providers.containsKey(comp))
                throw new DependencyNotFoundException(c, comp);
            if (!ref.isContainer()) {
                if (visiting.contains(comp))
                    throw new CyclicDependenciesFound(visiting);
                visiting.push(comp);
                checkDependencies(comp, visiting);
                visiting.pop();
            }
        }
    }

}
