package geektime.tdd.di;

import java.lang.annotation.Annotation;
import java.util.*;

public class ContextConfig {
    private Map<Component, Provider<?>> components = new HashMap<>();

    interface Provider<T> {
        T get(Context context);

        default List<ComponentRef> getDependencies() {
            return List.of();
        }
    }

    public <T> void bind(Class<T> type, T instance) {
        components.put(new Component(type, null), context -> instance);
    }

    public <T> void bind(Class<T> type, T instance, Annotation... qualifiers) {
        for (Annotation qualifier : qualifiers)
            components.put(new Component(type, qualifier), context -> instance);
    }

    public <T, Impl extends T> void bind(Class<T> type, Class<Impl> implementation) {
        components.put(new Component(type, null), new InjectionProvider<>(implementation));
    }

    public <T, Impl extends T> void bind(Class<T> type, Class<Impl> implementation, Annotation... qualifiers) {
        for (Annotation qualifier : qualifiers)
            components.put(new Component(type, qualifier), new InjectionProvider<>(implementation));
    }

    record Component(Class<?> type, Annotation qualifier) {
    }

    public Context getContext() {
        components.keySet().forEach(c -> checkDependencies(c, new Stack<>()));
        return new Context() {
            @Override
            public <T> Optional<T> get(ComponentRef<T> ref) {
                if (ref.getQualifier() != null)
                    return Optional.ofNullable(components.get(new Component(ref.getComponent(), ref.getQualifier())))
                            .map(p -> (T) p.get(this));
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
        return components.get(new Component(ref.getComponent(), ref.getQualifier()));
    }

    private void checkDependencies(Component c, Stack<Class<?>> visiting) {
        for (ComponentRef ref : components.get(c).getDependencies()) {
            Component comp = new Component(ref.getComponent(), ref.getQualifier());
            if (!components.containsKey(comp))
                throw new DependencyNotFoundException(c.type(), ref.getComponent());
            if (!ref.isContainer()) {
                if (visiting.contains(ref.getComponent()))
                    throw new CyclicDependenciesFound(visiting);
                visiting.push(ref.getComponent());
                checkDependencies(comp, visiting);
                visiting.pop();
            }
        }
    }

}
