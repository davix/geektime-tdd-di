package geektime.tdd.di;

import java.lang.annotation.Annotation;
import java.util.*;

public class ContextConfig {
    private Map<Class<?>, Provider<?>> providers = new HashMap<>();
    private Map<Component, Provider<?>> components = new HashMap<>();

    interface Provider<T> {
        T get(Context context);

        default List<Context.Ref> getDependencies() {
            return List.of();
        }
    }

    public <T> void bind(Class<T> type, T instance) {
        providers.put(type, context -> instance);
    }

    public <T> void bind(Class<T> type, T instance, Annotation... qualifiers) {
        for (Annotation qualifier : qualifiers)
            components.put(new Component(type, qualifier), context -> instance);
    }

    public <T, Impl extends T> void bind(Class<T> type, Class<Impl> implementation) {
        providers.put(type, new InjectionProvider<>(implementation));
    }

    public <T, Impl extends T> void bind(Class<T> type, Class<Impl> implementation, Annotation... qualifiers) {
        for (Annotation qualifier : qualifiers)
            components.put(new Component(type, qualifier), new InjectionProvider<>(implementation));
    }

    record Component(Class<?> type, Annotation qualifier) {
    }

    public Context getContext() {
        providers.keySet().forEach(c -> checkDependencies(c, new Stack<>()));
        return new Context() {
            @Override
            public <T> Optional<T> get(Ref<T> ref) {
                if (ref.getQualifier() != null)
                    return Optional.ofNullable(components.get(new Component(ref.getComponent(), ref.getQualifier())))
                            .map(p -> (T) p.get(this));
                if (ref.isContainer()) {
                    if (ref.getContainer() != jakarta.inject.Provider.class)
                        return Optional.empty();
                    return (Optional<T>) Optional.ofNullable(providers.get(ref.getComponent()))
                            .map(provider -> (jakarta.inject.Provider<Object>) () -> provider.get(this));
                } else
                    return Optional.ofNullable(providers.get(ref.getComponent())).map(p -> (T) p.get(this));
            }
        };
    }

    private void checkDependencies(Class<?> c, Stack<Class<?>> visiting) {
        for (Context.Ref ref : providers.get(c).getDependencies()) {
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
