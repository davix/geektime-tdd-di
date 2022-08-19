package geektime.tdd.di;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.internal.util.collections.Sets;

import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class ContainerTest {
    ContextConfig config;

    @BeforeEach
    public void setup() {
        config = new ContextConfig();
    }

    @Nested
    public class ComponentConstruction {
        @Nested
        public class TypeBinding {
            @Test
            public void should_bind_to_a_specific_instance() {
                Component instance = new Component() {
                    @Override
                    public Dependency dependency() {
                        return null;
                    }
                };
                config.bind(Component.class, instance);

                assertSame(instance, config.getContext().getType(Component.class).get());
            }

            @Test
            public void should_return_empty_if_no_component_defined() {
                Optional<Component> component = config.getContext().getType(Component.class);
                assertTrue(component.isEmpty());
            }

            @ParameterizedTest(name = "supporting {0}")
            @MethodSource
            public void should_bind_type_to_an_injectable_component(Class<? extends Component> type) {
                Dependency dependency = new Dependency() {
                };
                config.bind(Dependency.class, dependency);
                config.bind(Component.class, type);

                Optional<Component> component = config.getContext().getType(Component.class);
                assertTrue(component.isPresent());
                assertSame(dependency, component.get().dependency());
            }

            public static Stream<Arguments> should_bind_type_to_an_injectable_component() {
                return Stream.of(Arguments.of(Named.of("constructor injection", ConstructorInjection.class)),
                        Arguments.of(Named.of("field injection", FieldInjection.class)),
                        Arguments.of(Named.of("method injection", MethodInjection.class)));
            }

            interface Component {
                Dependency dependency();
            }

            static class ConstructorInjection implements Component {
                Dependency dependency;

                @Inject
                public ConstructorInjection(Dependency dependency) {
                    this.dependency = dependency;
                }

                @Override
                public Dependency dependency() {
                    return dependency;
                }
            }

            static class FieldInjection implements Component {
                @Inject
                Dependency dependency;

                @Override
                public Dependency dependency() {
                    return dependency;
                }
            }

            static class MethodInjection implements Component {
                Dependency dependency;

                @Inject
                public void install(Dependency dependency) {
                    this.dependency = dependency;
                }

                @Override
                public Dependency dependency() {
                    return dependency;
                }
            }

            @Test
            public void should_retrieve_bind_type_as_provider() {
                Component instance = new Component() {
                    @Override
                    public Dependency dependency() {
                        return null;
                    }
                };
                config.bind(Component.class, instance);
                Context context = config.getContext();

                ParameterizedType type = new TypeLiteral<Provider<Component>>() {
                }.getType();
//                assertEquals(Provider.class, type.getRawType());
//                assertEquals(Component.class, type.getActualTypeArguments()[0]);

                Provider<Component> provider = (Provider<Component>) context.getType(type).get();
                assertSame(instance, provider.get());
            }

            @Test
            public void should_not_retrieve_bind_type_as_unsupported_container() {
                Component instance = new Component() {
                    @Override
                    public Dependency dependency() {
                        return null;
                    }
                };
                config.bind(Component.class, instance);
                Context context = config.getContext();

                ParameterizedType type = new TypeLiteral<List<Component>>() {
                }.getType();

                assertFalse(context.getType(type).isPresent());
            }

            static abstract class TypeLiteral<T> {
                public ParameterizedType getType() {
                    return (ParameterizedType) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
                }
            }
        }

        @Nested
        public class DependencyCheck {

            @ParameterizedTest
            @MethodSource
            public void should_throw_exception_if_dependency_not_found(Class<? extends Component> component) {
                config.bind(Component.class, component);

                DependencyNotFoundException exception = assertThrows(DependencyNotFoundException.class, () -> config.getContext());
                assertEquals(Dependency.class, exception.getDependency());
                assertEquals(Component.class, exception.getComponent());
            }

            public static Stream<Arguments> should_throw_exception_if_dependency_not_found() {
                return Stream.of(Arguments.of(Named.of("Inject Constructor", MissingDependencyConstructor.class)),
                        Arguments.of(Named.of("Inject Field", MissingDependencyField.class)),
                        Arguments.of(Named.of("Inject Method", MissingDependencyMethod.class)),
                        Arguments.of(Named.of("Provider in Constructor", MissingDependencyProviderConstructor.class)),
                        Arguments.of(Named.of("Provider in Field", MissingDependencyProviderField.class)),
                        Arguments.of(Named.of("Provider in Method", MissingDependencyProviderMethod.class))
                );
            }

            static class MissingDependencyConstructor implements Component {
                @Inject
                public MissingDependencyConstructor(Dependency dependency) {
                }
            }

            static class MissingDependencyField implements Component {
                @Inject
                private Dependency dependency;
            }

            static class MissingDependencyMethod implements Component {
                @Inject
                void install(Dependency dependency) {
                }
            }

            static class MissingDependencyProviderConstructor implements Component {
                @Inject
                public MissingDependencyProviderConstructor(Provider<Dependency> dependency) {
                }
            }

            static class MissingDependencyProviderField implements Component {
                @Inject
                public Provider<Dependency> dependency;
            }

            static class MissingDependencyProviderMethod implements Component {
                @Inject
                void install(Provider<Dependency> dependency) {
                }
            }

            @ParameterizedTest(name = "cyclic dependency between {0} and {1}")
            @MethodSource
            public void should_throw_exception_if_cyclic_dependencies_found(Class<? extends Component> component,
                                                                            Class<? extends Dependency> dependency) {
                config.bind(Component.class, component);
                config.bind(Dependency.class, dependency);

                CyclicDependenciesFound exception = assertThrows(CyclicDependenciesFound.class, () -> config.getContext());
                Set<Class<?>> classes = Sets.newSet(exception.getComponents());

                assertEquals(2, classes.size());
                assertTrue(classes.contains(Component.class));
                assertTrue(classes.contains(Dependency.class));
            }

            public static Stream<Arguments> should_throw_exception_if_cyclic_dependencies_found() {
                ArrayList<Arguments> arguments = new ArrayList<>();
                for (Named component : List.of(
                        Named.of("Inject Constructor", CyclicComponentWithInjectConstructor.class),
                        Named.of("Inject Field", CyclicComponentWithInjectField.class),
                        Named.of("Inject Method", CyclicComponentWithInjectMethod.class)
                ))
                    for (Named dependency : List.of(
                            Named.of("Inject Constructor", CyclicDependencyComponent.class),
                            Named.of("Inject Field", CyclicDependencyWithInjectField.class),
                            Named.of("Inject Method", CyclicDependencyWithInjectMethod.class)
                    ))
                        arguments.add(Arguments.of(component, dependency));
                return arguments.stream();
            }

            static class CyclicComponentWithInjectConstructor implements Component {
                @Inject
                public CyclicComponentWithInjectConstructor(Dependency dependency) {
                }
            }

            static class CyclicComponentWithInjectField implements Component {
                @Inject
                Dependency dependency;
            }

            static class CyclicComponentWithInjectMethod implements Component {
                @Inject
                void install(Dependency dependency) {
                }
            }

            static class CyclicDependencyComponent implements Dependency {
                @Inject
                public CyclicDependencyComponent(Component component) {
                }
            }

            static class CyclicDependencyWithInjectField implements Component {
                @Inject
                Component component;
            }

            static class CyclicDependencyWithInjectMethod implements Component {
                @Inject
                void install(Component component) {
                }
            }

            @Test
            public void should_throw_exception_if_transitive_cyclic_dependencies_found() {
                config.bind(Component.class, ComponentWithInjectConstructor.class);
                config.bind(Dependency.class, DependencyDependedOnAnotherDependency.class);
                config.bind(AnotherDependency.class, AnotherDependencyDependedOnComponent.class);

                CyclicDependenciesFound exception = assertThrows(CyclicDependenciesFound.class, () -> config.getContext());

                List<Class<?>> classes = Arrays.asList(exception.getComponents());
                assertEquals(3, classes.size());
                assertTrue(classes.contains(Component.class));
                assertTrue(classes.contains(Dependency.class));
                assertTrue(classes.contains(AnotherDependency.class));
            }

            static class CyclicDependencyProviderConstructor implements Dependency {
                @Inject
                public CyclicDependencyProviderConstructor(Provider<Component> component) {
                }
            }

            @Test
            public void should_not_throw_exception_if_cyclic_dependency_via_provider() {
                config.bind(Component.class, CyclicComponentWithInjectConstructor.class);
                config.bind(Dependency.class, CyclicDependencyProviderConstructor.class);

                Context context = config.getContext();
                assertTrue(context.getType(Component.class).isPresent());
            }
        }

    }

    @Nested
    public class DependenciesSelection {

    }

    @Nested
    public class LifecycleManagement {

    }
}

interface Component {
}

interface Dependency {
}

interface AnotherDependency {
}


class ComponentWithDefaultConstructor implements Component {
    public ComponentWithDefaultConstructor() {
    }
}

class ComponentWithInjectConstructor implements Component {
    private Dependency dependency;

    @Inject
    public ComponentWithInjectConstructor(Dependency dependency) {
        this.dependency = dependency;
    }

    public Dependency getDependency() {
        return dependency;
    }
}

class ComponentWithMultipleInjectConstructors implements Component {
    @Inject
    public ComponentWithMultipleInjectConstructors(String name) {
    }

    @Inject
    public ComponentWithMultipleInjectConstructors(String name, Double value) {
    }
}

class ComponentWithoutInjectOrDefaultConstructors implements Component {
    public ComponentWithoutInjectOrDefaultConstructors(String name) {
    }
}

class DependencyWithInjectConstructor implements Dependency {
    private String dependency;

    @Inject
    public DependencyWithInjectConstructor(String dependency) {
        this.dependency = dependency;
    }

    public String getDependency() {
        return dependency;
    }
}

class DependencyDependedOnComponent implements Dependency {
    private Component component;

    @Inject

    public DependencyDependedOnComponent(Component component) {
        this.component = component;
    }
}

class DependencyDependedOnAnotherDependency implements Dependency {
    private AnotherDependency dependency;

    @Inject

    public DependencyDependedOnAnotherDependency(AnotherDependency dependency) {
        this.dependency = dependency;
    }
}

class AnotherDependencyDependedOnComponent implements AnotherDependency {
    private Component component;

    @Inject

    public AnotherDependencyDependedOnComponent(Component component) {
        this.component = component;
    }
}
