package geektime.tdd.di;

import jakarta.inject.Inject;
import jakarta.inject.Qualifier;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Arrays.stream;
import static java.util.stream.Stream.concat;

class InjectionProvider<T> implements ContextConfig.Provider<T> {
    private Injectable<Constructor<T>> constructor;
    private List<Injectable<Field>> fields;
    private List<Injectable<Method>> methods;

    public InjectionProvider(Class<T> component) {
        if (Modifier.isAbstract(component.getModifiers()))
            throw new IllegalComponentException();

        this.constructor = getConstructor(component);
        this.fields = getFields(component);
        this.methods = getMethods(component);

        if (fields.stream().map(Injectable::element).anyMatch(f -> Modifier.isFinal(f.getModifiers())))
            throw new IllegalComponentException();
        if (methods.stream().map(Injectable::element).anyMatch(m -> m.getTypeParameters().length != 0))
            throw new IllegalComponentException();

    }

    @Override
    public T get(Context context) {
        try {
            T instance = constructor.element().newInstance(constructor.toDependencies(context));
            for (Injectable<Field> f : fields) {
                f.element().set(instance, f.toDependencies(context)[0]);
            }
            for (Injectable<Method> m : methods) {
                m.element().invoke(instance, m.toDependencies(context));
            }
            return instance;
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    static record Injectable<E extends AccessibleObject>(E element, ComponentRef<?>[] required) {
        static <E extends Executable> Injectable<E> of(E element) {
            ComponentRef<?>[] required = stream(element.getParameters()).map(Injectable::toComponentRef).toArray(ComponentRef<?>[]::new);
            return new Injectable<>(element, required);
        }

        static Injectable<Field> of(Field f) {
            return new Injectable<>(f, new ComponentRef<?>[]{toComponentRef(f)});
        }

        private static Annotation getQualifier(AnnotatedElement p) {
            List<Annotation> qualifiers = stream(p.getAnnotations()).filter(a ->
                    a.annotationType().isAnnotationPresent(Qualifier.class)).toList();
            if (qualifiers.size() > 1)
                throw new IllegalComponentException();
            return qualifiers.stream().findFirst().orElse(null);
        }

        private static ComponentRef<?> toComponentRef(Parameter p) {
            Annotation qualifier = getQualifier(p);
            return ComponentRef.of(p.getParameterizedType(), qualifier);
        }

        private static ComponentRef toComponentRef(Field f) {
            Annotation qualifier = getQualifier(f);
            return ComponentRef.of(f.getGenericType(), qualifier);
        }

        Object[] toDependencies(Context context) {
            return stream(required).map(context::get).map(Optional::get).toArray();
        }
    }

    @Override
    public List<ComponentRef<?>> getDependencies() {
        return concat(concat(Stream.of(constructor), fields.stream()), methods.stream())
                .flatMap(i -> stream(i.required())).toList();
    }

    private static <T> Constructor<T> defaultConstructor(Class<T> implementation) {
        try {
            return implementation.getDeclaredConstructor();
        } catch (Exception e) {
            throw new IllegalComponentException();
        }
    }

    private static <T> List<T> traverse(Class<?> component, BiFunction<List<T>, Class<?>, List<T>> finder) {
        List<T> members = new ArrayList<>();
        for (Class<?> cur = component; cur != Object.class; cur = cur.getSuperclass()) {
            members.addAll(finder.apply(members, cur));
        }
        return members;
    }

    private static <T extends AnnotatedElement> Stream<T> injectable(T[] elements) {
        return stream(elements).filter(f -> f.isAnnotationPresent(Inject.class));
    }

    private static <T> Injectable<Constructor<T>> getConstructor(Class<T> component) {
        List<Constructor<?>> constructors = injectable(component.getConstructors()).toList();
        if (constructors.size() > 1) throw new IllegalComponentException();

        return Injectable.of((Constructor<T>) constructors.stream()
                .findFirst().orElseGet(() -> defaultConstructor(component)));
    }

    private static List<Injectable<Field>> getFields(Class<?> component) {
        List<Field> injectFields = traverse(component, (fields1, cur) -> injectable(cur.getDeclaredFields()).toList());
        return injectFields.stream().map(Injectable::of).toList();
    }

    private static List<Injectable<Method>> getMethods(Class<?> component) {
        List<Method> methods1 = traverse(component, (ms, cur) -> injectable(cur.getDeclaredMethods())
                .filter(m -> isOverrideByInjectMethod(ms, m))
                .filter(m -> isOverrideByNoInjectMethod(component, m))
                .toList());
        Collections.reverse(methods1);
        return methods1.stream().map(Injectable::of).toList();
    }

    private static <T> boolean isOverrideByNoInjectMethod(Class<T> component, Method m) {
        return stream(component.getDeclaredMethods()).filter(m1 -> !m1.isAnnotationPresent(Inject.class))
                .noneMatch(isSameMethod(m));
    }

    private static boolean isOverrideByInjectMethod(List<Method> methods, Method m) {
        return methods.stream().noneMatch(isSameMethod(m));
    }

    private static Predicate<Method> isSameMethod(Method m) {
        return o -> o.getName().equals(m.getName()) &&
                Arrays.equals(o.getParameterTypes(), m.getParameterTypes());
    }
}
