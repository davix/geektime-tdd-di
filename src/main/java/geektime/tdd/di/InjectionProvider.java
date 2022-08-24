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
    private Constructor<T> constructor;
    private List<Field> fields;
    private List<Method> methods;

    public InjectionProvider(Class<T> component) {
        if (Modifier.isAbstract(component.getModifiers()))
            throw new IllegalComponentException();

        this.constructor = getConstructor(component);
        this.fields = getFields(component);
        this.methods = getMethods(component);

        if (fields.stream().anyMatch(f -> Modifier.isFinal(f.getModifiers())))
            throw new IllegalComponentException();
        if (methods.stream().anyMatch(m -> m.getTypeParameters().length != 0))
            throw new IllegalComponentException();
    }

    @Override
    public T get(Context context) {
        try {
            T instance = constructor.newInstance(toDependencies(context, constructor));
            for (Field f : fields) {
                f.set(instance, toDependency(context, f));
            }
            for (Method m : methods) {
                m.invoke(instance, toDependencies(context, m));
            }
            return instance;
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ComponentRef> getDependencies() {
        return concat(concat(stream(constructor.getParameters()).map(this::toComponentRef),
                        fields.stream().map(this::toComponentRef)),
                methods.stream().flatMap(m -> stream(m.getParameters()).map(this::toComponentRef))
        ).toList();
    }

    private ComponentRef<?> toComponentRef(Parameter p) {
        Annotation qualifier = stream(p.getAnnotations()).filter(a ->
                        a.annotationType().isAnnotationPresent(Qualifier.class))
                .findFirst().orElse(null);
        return ComponentRef.of(p.getParameterizedType(), qualifier);
    }

    private ComponentRef toComponentRef(Field f) {
        Annotation qualifier = stream(f.getAnnotations()).filter(a ->
                        a.annotationType().isAnnotationPresent(Qualifier.class))
                .findFirst().orElse(null);
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

    private static Object[] toDependencies(Context context, Executable executable) {
        return stream(executable.getParameters())
                .map(p -> toDependency(context, p.getParameterizedType())).toArray(Object[]::new);
    }

    private Object toDependency(Context context, Field f) {
        return toDependency(context, f.getGenericType());
    }

    private static Object toDependency(Context context, Type type) {
        return ((Optional) context.get(ComponentRef.of(type))).get();
    }

}
