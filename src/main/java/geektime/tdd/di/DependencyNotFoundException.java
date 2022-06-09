package geektime.tdd.di;

public class DependencyNotFoundException extends RuntimeException {
    public DependencyNotFoundException(Class<?> dependency) {
        this.dependency = dependency;
    }

    public Class<?> getDependency() {
        return dependency;
    }

    private Class<?> dependency;
}
