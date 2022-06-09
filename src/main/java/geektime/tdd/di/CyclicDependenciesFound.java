package geektime.tdd.di;

import java.util.HashSet;
import java.util.Set;

public class CyclicDependenciesFound extends RuntimeException {
    private Set<Class<?>> components = new HashSet<>();

    public CyclicDependenciesFound(Class<?> component) {
        components.add(component);
    }

    public CyclicDependenciesFound(Class<?> type, CyclicDependenciesFound e) {
        components.add(type);
        components.addAll(e.components);
    }

    public Class<?>[] getComponents() {
        return components.toArray(Class<?>[]::new);
    }
}