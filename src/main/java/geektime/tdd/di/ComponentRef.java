package geektime.tdd.di;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

public class ComponentRef<T> {
    private Component component;
    private Type container;

    public ComponentRef(Class<T> componentType) {
        init(componentType, null);
    }

    public ComponentRef(Type container, Annotation qualifier) {
        init(container, qualifier);
    }

    public static ComponentRef of(Type type) {
        return new ComponentRef(type, null);
    }

    public static ComponentRef of(Type type, Annotation qualifier) {
        return new ComponentRef(type, qualifier);
    }

    public static <T> ComponentRef<T> of(Class<T> component, Annotation qualifier) {
        return new ComponentRef(component, qualifier);
    }

    public static <T> ComponentRef<T> of(Class<T> component) {
        return new ComponentRef(component);
    }

    protected ComponentRef() {
        Type type = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        init(type, null);
    }

    private void init(Type type, Annotation qualifier) {
        if (type instanceof ParameterizedType container) {
            this.container = container.getRawType();
            this.component = new Component((Class<T>) container.getActualTypeArguments()[0], qualifier);
        } else {
            this.component = new Component((Class<T>) type, qualifier);
        }
    }

    public Type getContainer() {
        return container;
    }

    public Class<?> getComponentType() {
        return component.type();
    }

    public Component component() {
        return component;
    }

    public boolean isContainer() {
        return container != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComponentRef<?> that = (ComponentRef<?>) o;
        return component.equals(that.component) && Objects.equals(container, that.container);
    }

    @Override
    public int hashCode() {
        return Objects.hash(component, container);
    }
}
