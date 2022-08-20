package geektime.tdd.di;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Optional;

public interface Context {
    Optional<?> get(Ref ref);

    class Ref<T> {
        private Type container;
        private Class<T> component;

        public Ref(Class<T> component) {
            this.component = component;
        }

        public Ref(ParameterizedType container) {
            this.container = container.getRawType();
            this.component = (Class<T>) container.getActualTypeArguments()[0];
        }

        public static Ref of(Type type) {
            if (type instanceof ParameterizedType container) return new Ref(container);
            return new Ref((Class<?>) type);
        }

        public static <T> Ref<T> of(Class<T> component) {
            return new Ref(component);
        }

        public Type getContainer() {
            return container;
        }

        public Class<?> getComponent() {
            return component;
        }

        public boolean isContainer() {
            return container != null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Ref ref = (Ref) o;
            return Objects.equals(container, ref.container) && component.equals(ref.component);
        }

        @Override
        public int hashCode() {
            return Objects.hash(container, component);
        }
    }
}
