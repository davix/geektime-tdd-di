package geektime.tdd.di;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

public class ComponentRef<T> {
    private Type container;
    private Class<T> component;
    private Annotation qualifier;

    public ComponentRef(Class<T> component) {
        init(component);
    }

    public ComponentRef(Type container, Annotation qualifier) {
        init(container);
        this.qualifier = qualifier;
    }

    public static ComponentRef of(Type type) {
        return new ComponentRef(type, null);
    }

    public static <T> ComponentRef<T> of(Class<T> component, Annotation qualifier) {
        return new ComponentRef(component, qualifier);
    }

    public static <T> ComponentRef<T> of(Class<T> component) {
        return new ComponentRef(component);
    }

    protected ComponentRef() {
        Type type = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        init(type);
    }

    private void init(Type type) {
        if (type instanceof ParameterizedType container) {
            this.container = container.getRawType();
            this.component = (Class<T>) container.getActualTypeArguments()[0];
        } else {
            this.component = (Class<T>) type;
        }
    }

    public Type getContainer() {
        return container;
    }

    public Class<?> getComponent() {
        return component;
    }

    public Annotation getQualifier() {
        return qualifier;
    }

    public boolean isContainer() {
        return container != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComponentRef ref = (ComponentRef) o;
        return Objects.equals(container, ref.container) && component.equals(ref.component);
    }

    @Override
    public int hashCode() {
        return Objects.hash(container, component);
    }
}
