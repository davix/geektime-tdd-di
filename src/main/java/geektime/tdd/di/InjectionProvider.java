package geektime.tdd.di;

import jakarta.inject.Inject;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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
            Object[] dependencies = stream(constructor.getParameters())
                    .map(p -> context.get(p.getType()).get())
                    .toArray(Object[]::new);
            T instance = constructor.newInstance(dependencies);
            for (Field f : fields) {
                f.set(instance, context.get(f.getType()).get());
            }
//            fields.forEach(f -> f.set(instance, context.get(f.getType())).get());
            for (Method m : methods) {
                m.invoke(instance, stream(m.getParameterTypes()).map(t -> context.get(t).get()).toArray(Object[]::new));
            }
            return instance;
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Class<?>> getDependencies() {
        Stream<Class<?>> fromCons = stream(constructor.getParameters()).map(Parameter::getType);
        Stream<Class<?>> fromField = fields.stream().map(Field::getType);
        Stream<Class<?>> fromMethod = methods.stream().flatMap(m -> stream(m.getParameterTypes()));
        return concat(concat(fromCons, fromField), fromMethod).toList();
    }

    private static <T> List<Field> getFields(Class<T> component) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> cur = component; cur != Object.class; cur = cur.getSuperclass())
            fields.addAll(stream(cur.getDeclaredFields()).filter(f -> f.isAnnotationPresent(Inject.class)).toList());
        return fields;
    }

    private static <T> List<Method> getMethods(Class<T> component) {
        List<Method> methods = new ArrayList<>();
        for (Class<?> cur = component; cur != Object.class; cur = cur.getSuperclass())
            methods.addAll(stream(cur.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(Inject.class))
                    .filter(m -> methods.stream().noneMatch(isSameMethod(m)))
                    .filter(m -> stream(component.getDeclaredMethods()).filter(m1 -> !m1.isAnnotationPresent(Inject.class))
                            .noneMatch(isSameMethod(m)))
                    .toList());
        Collections.reverse(methods);
        return methods;
    }

    private static Predicate<Method> isSameMethod(Method m) {
        return o -> o.getName().equals(m.getName()) &&
                Arrays.equals(o.getParameterTypes(), m.getParameterTypes());
    }

    private static <T> Constructor<T> getConstructor(Class<T> implementation) {
        List<Constructor<?>> constructors = stream(implementation.getConstructors())
                .filter(c -> c.isAnnotationPresent(Inject.class)).collect(Collectors.toList());
        if (constructors.size() > 1) throw new IllegalComponentException();

        return (Constructor<T>) constructors.stream()
                .findFirst().orElseGet(() -> {
                    try {
                        return implementation.getDeclaredConstructor();
                    } catch (Exception e) {
                        throw new IllegalComponentException();
                    }
                });
    }
}
