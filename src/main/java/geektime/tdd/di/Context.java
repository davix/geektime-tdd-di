package geektime.tdd.di;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Optional;

public interface Context {
    <T> Optional<T> get(Ref<T> ref);

    class Ref<T> {
        private Type container;
        private Class<T> component;
        private Annotation qualifier;

        public Ref(Class<T> component) {
            init(component);
        }

        public Ref(Type container, Annotation qualifier) {
            init(container);
            this.qualifier = qualifier;
        }

        public static Ref of(Type type) {
            return new Ref(type, null);
        }

        public static Ref of(Type type, Annotation qualifier) {
            return new Ref(type, qualifier);
        }

        public static <T> Ref<T> of(Class<T> component) {
            return new Ref(component);
        }

        protected Ref() {
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
            Ref ref = (Ref) o;
            return Objects.equals(container, ref.container) && component.equals(ref.component);
        }

        @Override
        public int hashCode() {
            return Objects.hash(container, component);
        }
    }
}
