package geektime.tdd.di;

import jakarta.inject.Qualifier;

import java.lang.annotation.Annotation;
import java.util.*;

public class ContextConfig {
    private Map<Component, Provider<?>> components = new HashMap<>();

    interface Provider<T> {
        T get(Context context);

        default List<ComponentRef<?>> getDependencies() {
            return List.of();
        }
    }

    public <T> void bind(Class<T> type, T instance) {
        components.put(new Component(type, null), context -> instance);
    }

    public <T> void bind(Class<T> type, T instance, Annotation... qualifiers) {
        if (Arrays.stream(qualifiers).anyMatch(q -> !q.annotationType().isAnnotationPresent(Qualifier.class)))
            throw new IllegalComponentException();
        for (Annotation qualifier : qualifiers)
            components.put(new Component(type, qualifier), context -> instance);
    }

    public <T, Impl extends T> void bind(Class<T> type, Class<Impl> implementation) {
        components.put(new Component(type, null), new InjectionProvider<>(implementation));
    }

    public <T, Impl extends T> void bind(Class<T> type, Class<Impl> implementation, Annotation... qualifiers) {
        if (Arrays.stream(qualifiers).anyMatch(q -> !q.annotationType().isAnnotationPresent(Qualifier.class)))
            throw new IllegalComponentException();
        for (Annotation qualifier : qualifiers)
            components.put(new Component(type, qualifier), new InjectionProvider<>(implementation));
    }

    public Context getContext() {
        components.keySet().forEach(c -> checkDependencies(c, new Stack<>()));
        return new Context() {
            @Override
            public <T> Optional<T> get(ComponentRef<T> ref) {
                if (ref.isContainer()) {
                    if (ref.getContainer() != jakarta.inject.Provider.class)
                        return Optional.empty();
                    return (Optional<T>) Optional.ofNullable(getProvider(ref))
                            .map(provider -> (jakarta.inject.Provider<Object>) () -> provider.get(this));
                } else
                    return Optional.ofNullable(getProvider(ref)).map(p -> (T) p.get(this));
            }
        };
    }

    private <T> Provider<?> getProvider(ComponentRef<T> ref) {
        return components.get(ref.component());
    }

    private void checkDependencies(Component c, Stack<Component> visiting) {
        for (ComponentRef ref : components.get(c).getDependencies()) {
            Component comp = ref.component();
            if (!components.containsKey(comp))
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
