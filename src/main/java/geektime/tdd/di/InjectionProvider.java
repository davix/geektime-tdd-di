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
    private List<Field> fields;
    private List<Injectable<Method>> methods;
    private List<ComponentRef> dependencies;

    public InjectionProvider(Class<T> component) {
        if (Modifier.isAbstract(component.getModifiers()))
            throw new IllegalComponentException();

        this.constructor = getInjectable(getConstructor(component));
        this.fields = getFields(component);
        this.methods = getMethods(component).stream().map(m -> getInjectable(m)).toList();

        if (fields.stream().anyMatch(f -> Modifier.isFinal(f.getModifiers())))
            throw new IllegalComponentException();
        if (methods.stream().map(Injectable::element).anyMatch(m -> m.getTypeParameters().length != 0))
            throw new IllegalComponentException();

        dependencies = getDependencies();
    }

    private static <E extends Executable> Injectable<E> getInjectable(E element) {
        ComponentRef<?>[] required = stream(element.getParameters()).map(InjectionProvider::toComponentRef).toArray(ComponentRef<?>[]::new);
        Injectable<E> injectable = new Injectable<>(element, required);
        return injectable;
    }

    @Override
    public T get(Context context) {
        try {
            T instance = constructor.element().newInstance(constructor.toDependencies(context));
            for (Field f : fields) {
                f.set(instance, toDependency(context, f));
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
        Object[] toDependencies(Context context) {
            return stream(required).map(context::get).map(Optional::get).toArray();
        }
    }

    @Override
    public List<ComponentRef> getDependencies() {
        return concat(concat(stream(constructor.element().getParameters()).map(InjectionProvider::toComponentRef),
                        fields.stream().map(this::toComponentRef)),
                methods.stream().flatMap(m -> stream(m.required()))
        ).toList();
    }

    private static ComponentRef<?> toComponentRef(Parameter p) {
        Annotation qualifier = getQualifier(p);
        return ComponentRef.of(p.getParameterizedType(), qualifier);
    }

    private static Annotation getQualifier(AnnotatedElement p) {
        List<Annotation> qualifiers = stream(p.getAnnotations()).filter(a ->
                a.annotationType().isAnnotationPresent(Qualifier.class)).toList();
        if (qualifiers.size() > 1)
            throw new IllegalComponentException();
        return qualifiers.stream().findFirst().orElse(null);
    }

    private ComponentRef toComponentRef(Field f) {
        Annotation qualifier = getQualifier(f);
        return ComponentRef.of(f.getGenericType(), qualifier);
    }

    private static <T> List<Field> getFields(Class<T> component) {
        return traverse(component, (fields, cur) -> injectable(cur.getDeclaredFields()).toList());
    }

    private static <T> List<Method> getMethods(Class<T> component) {
        List<Method> methods = traverse(component, (ms, cur) -> injectable(cur.getDeclaredMethods())
                .filter(m -> isOverrideByInjectMethod(ms, m))
                .filter(m -> isOverrideByNoInjectMethod(component, m))
                .toList());
        Collections.reverse(methods);
        return methods;
    }

    private static <T> Constructor<T> getConstructor(Class<T> implementation) {
        List<Constructor<?>> constructors = injectable(implementation.getConstructors()).toList();
        if (constructors.size() > 1) throw new IllegalComponentException();

        return (Constructor<T>) constructors.stream()
                .findFirst().orElseGet(() -> defaultConstructor(implementation));
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

    private Object toDependency(Context context, Field f) {
        return toDependency(context, f.getGenericType(), getQualifier(f));
    }

    private static Object toDependency(Context context, Type type, Annotation qualifier) {
        return context.get(ComponentRef.of(type, qualifier)).get();
    }

}
