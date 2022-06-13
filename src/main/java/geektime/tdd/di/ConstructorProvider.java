package geektime.tdd.di;

import jakarta.inject.Inject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.stream;

class ConstructorProvider<T> implements ContextConfig.Provider<T> {
    private Constructor<T> constructor;
    private List<Field> fields;

    public ConstructorProvider(Class<T> component) {
        this.constructor = getConstructor(component);
        this.fields = getFields(component);
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
            return instance;
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Class<?>> getDependencies() {
        return Stream.concat(stream(constructor.getParameters()).map(Parameter::getType),
                fields.stream().map(Field::getType)).toList();
    }

    private static <T> List<Field> getFields(Class<T> component) {
        return stream(component.getDeclaredFields()).filter(f -> f.isAnnotationPresent(Inject.class)).toList();
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
