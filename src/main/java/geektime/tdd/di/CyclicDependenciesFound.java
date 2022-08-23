package geektime.tdd.di;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CyclicDependenciesFound extends RuntimeException {
    private Set<Component> components = new HashSet<>();

    public CyclicDependenciesFound(List<Component> visiting) {
        components.addAll(visiting);
    }

    public Class<?>[] getComponents() {
        return components.stream().map(c -> c.type()).toArray(Class<?>[]::new);
    }
}
