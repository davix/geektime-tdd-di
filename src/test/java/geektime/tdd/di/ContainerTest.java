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

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.Stream;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
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

                assertSame(instance, ((Optional) config.getContext().get(ComponentRef.of(Component.class))).get());
            }

            @Test
            public void should_return_empty_if_no_component_defined() {
                Optional<Component> component = (Optional) config.getContext().get(ComponentRef.of(Component.class));
                assertTrue(component.isEmpty());
            }

            @ParameterizedTest(name = "supporting {0}")
            @MethodSource
            public void should_bind_type_to_an_injectable_component(Class<? extends Component> type) {
                Dependency dependency = new Dependency() {
                };
                config.bind(Dependency.class, dependency);
                config.bind(Component.class, type);

                Optional<Component> component = (Optional) config.getContext().get(ComponentRef.of(Component.class));
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
                TestComponent instance = new TestComponent() {
                };
                config.bind(TestComponent.class, instance);
                Context context = config.getContext();

                Provider<TestComponent> provider = context.get(new ComponentRef<Provider<TestComponent>>() {
                }).get();
                assertSame(instance, provider.get());
            }

            @Test
            public void should_not_retrieve_bind_type_as_unsupported_container() {
                TestComponent instance = new TestComponent() {
                };
                config.bind(TestComponent.class, instance);
                Context context = config.getContext();

                assertFalse(context.get(new ComponentRef<List<TestComponent>>() {
                }).isPresent());
            }

            @Nested
            public class WithQualifier {
                @Test
                public void should_bind_instance_with_qualifier() {
                    TestComponent instance = new TestComponent() {
                    };
                    config.bind(TestComponent.class, instance, new NamedLiteral("ChosenOne"));
                    Context context = config.getContext();

                    TestComponent chosenOne = context.get(ComponentRef.of(TestComponent.class, new NamedLiteral("ChosenOne"))).get();
                    assertSame(instance, chosenOne);
                }

                @Test
                public void should_bind_component_with_qualifier() {
                    Dependency dependency = new Dependency() {
                    };
                    config.bind(Dependency.class, dependency);
                    config.bind(InjectConstructor.class,
                            InjectConstructor.class,
                            new NamedLiteral("ChosenOne"));
                    Context context = config.getContext();

                    InjectConstructor chosenOne = context.get(ComponentRef.of(InjectConstructor.class, new NamedLiteral("ChosenOne"))).get();
                    assertSame(dependency, chosenOne.dependency);
                }

                static class InjectConstructor {
                    Dependency dependency;

                    @Inject
                    public InjectConstructor(Dependency dependency) {
                        this.dependency = dependency;
                    }
                }

                @Test
                public void should_bind_instance_with_multi_qualifier() {
                    TestComponent instance = new TestComponent() {
                    };
                    config.bind(TestComponent.class, instance, new NamedLiteral("ChosenOne"), new SkywalkerLiteral());
                    Context context = config.getContext();

                    TestComponent chosenOne = context.get(ComponentRef.of(TestComponent.class, new NamedLiteral("ChosenOne"))).get();
                    TestComponent skywalker = context.get(ComponentRef.of(TestComponent.class, new SkywalkerLiteral())).get();

                    assertSame(instance, chosenOne);
                    assertSame(instance, skywalker);
                }

                @Test
                public void should_bind_component_with_multi_qualifier() {
                    Dependency dependency = new Dependency() {
                    };
                    config.bind(Dependency.class, dependency);
                    config.bind(InjectConstructor.class,
                            InjectConstructor.class,
                            new NamedLiteral("ChosenOne"),
                            new SkywalkerLiteral());
                    Context context = config.getContext();

                    InjectConstructor chosenOne = context.get(ComponentRef.of(InjectConstructor.class, new NamedLiteral("ChosenOne"))).get();
                    InjectConstructor skywalker = context.get(ComponentRef.of(InjectConstructor.class, new SkywalkerLiteral())).get();

                    assertSame(dependency, chosenOne.dependency);
                    assertSame(dependency, skywalker.dependency);
                }

                @Test
                public void should_throw_exception_if_illegal_qualifier_given_to_instance() {
                    TestComponent instance = new TestComponent() {
                    };
                    assertThrows(IllegalComponentException.class, () ->
                            config.bind(TestComponent.class, instance, new TestLiteral()));
                }

                @Test
                public void should_throw_exception_if_illegal_qualifier_given_to_component() {
                    assertThrows(IllegalComponentException.class, () ->
                            config.bind(InjectConstructor.class, InjectConstructor.class, new TestLiteral()));
                }

                //TODO provider
            }
        }

        @Nested
        public class DependencyCheck {

            @ParameterizedTest
            @MethodSource
            public void should_throw_exception_if_dependency_not_found(Class<? extends TestComponent> component) {
                config.bind(TestComponent.class, component);

                DependencyNotFoundException exception = assertThrows(DependencyNotFoundException.class, () -> config.getContext());
                assertEquals(Dependency.class, exception.getDependency().type());
                assertEquals(TestComponent.class, exception.getComponent().type());
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

            static class MissingDependencyConstructor implements TestComponent {
                @Inject
                public MissingDependencyConstructor(Dependency dependency) {
                }
            }

            static class MissingDependencyField implements TestComponent {
                @Inject
                private Dependency dependency;
            }

            static class MissingDependencyMethod implements TestComponent {
                @Inject
                void install(Dependency dependency) {
                }
            }

            static class MissingDependencyProviderConstructor implements TestComponent {
                @Inject
                public MissingDependencyProviderConstructor(Provider<Dependency> dependency) {
                }
            }

            static class MissingDependencyProviderField implements TestComponent {
                @Inject
                public Provider<Dependency> dependency;
            }

            static class MissingDependencyProviderMethod implements TestComponent {
                @Inject
                void install(Provider<Dependency> dependency) {
                }
            }

            @ParameterizedTest(name = "cyclic dependency between {0} and {1}")
            @MethodSource
            public void should_throw_exception_if_cyclic_dependencies_found(Class<? extends TestComponent> component,
                                                                            Class<? extends Dependency> dependency) {
                config.bind(TestComponent.class, component);
                config.bind(Dependency.class, dependency);

                CyclicDependenciesFound exception = assertThrows(CyclicDependenciesFound.class, () -> config.getContext());
                Set<Class<?>> classes = Sets.newSet(exception.getComponents());

                assertEquals(2, classes.size());
                assertTrue(classes.contains(TestComponent.class));
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

            static class CyclicComponentWithInjectConstructor implements TestComponent {
                @Inject
                public CyclicComponentWithInjectConstructor(Dependency dependency) {
                }
            }

            static class CyclicComponentWithInjectField implements TestComponent {
                @Inject
                Dependency dependency;
            }

            static class CyclicComponentWithInjectMethod implements TestComponent {
                @Inject
                void install(Dependency dependency) {
                }
            }

            static class CyclicDependencyComponent implements Dependency {
                @Inject
                public CyclicDependencyComponent(TestComponent component) {
                }
            }

            static class CyclicDependencyWithInjectField implements TestComponent {
                @Inject
                TestComponent component;
            }

            static class CyclicDependencyWithInjectMethod implements TestComponent {
                @Inject
                void install(TestComponent component) {
                }
            }

            @Test
            public void should_throw_exception_if_transitive_cyclic_dependencies_found() {
                config.bind(TestComponent.class, ComponentWithInjectConstructor.class);
                config.bind(Dependency.class, DependencyDependedOnAnotherDependency.class);
                config.bind(AnotherDependency.class, AnotherDependencyDependedOnComponent.class);

                CyclicDependenciesFound exception = assertThrows(CyclicDependenciesFound.class, () -> config.getContext());

                List<Class<?>> classes = Arrays.asList(exception.getComponents());
                assertEquals(3, classes.size());
                assertTrue(classes.contains(TestComponent.class));
                assertTrue(classes.contains(Dependency.class));
                assertTrue(classes.contains(AnotherDependency.class));
            }

            static class CyclicDependencyProviderConstructor implements Dependency {
                @Inject
                public CyclicDependencyProviderConstructor(Provider<TestComponent> component) {
                }
            }

            @Test
            public void should_not_throw_exception_if_cyclic_dependency_via_provider() {
                config.bind(TestComponent.class, CyclicComponentWithInjectConstructor.class);
                config.bind(Dependency.class, CyclicDependencyProviderConstructor.class);

                Context context = config.getContext();
                assertTrue(context.get(ComponentRef.of(TestComponent.class)).isPresent());
            }

            @Nested
            public class WithQualifier {
                //TODO dependency missing if qualifier not match
                @Test
                public void should_throw_exception_if_dependency_with_qualifier_not_found() {
                    config.bind(Dependency.class, new Dependency() {
                    });
                    config.bind(InjectConstructor.class, InjectConstructor.class);

                    assertThrows(DependencyNotFoundException.class, () -> config.getContext());
                }

                static class InjectConstructor {
                    @Inject
                    public InjectConstructor(@Skywalker Dependency dependency) {
                    }
                }
                //TODO check cyclic dependencies with qualifier
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

record NamedLiteral(String value) implements jakarta.inject.Named {
    @Override
    public Class<? extends Annotation> annotationType() {
        return jakarta.inject.Named.class;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof jakarta.inject.Named named)
            return Objects.equals(value, named.value());
        else
            return false;
    }
}

@java.lang.annotation.Documented
@java.lang.annotation.Retention(RUNTIME)
@jakarta.inject.Qualifier
@interface Skywalker {
}

record SkywalkerLiteral() implements Skywalker {

    @Override
    public Class<? extends Annotation> annotationType() {
        return Skywalker.class;
    }
}

record TestLiteral() implements Test {

    @Override
    public Class<? extends Annotation> annotationType() {
        return Test.class;
    }
}

interface TestComponent {
}

interface Dependency {
}

interface AnotherDependency {
}


class ComponentWithDefaultConstructor implements TestComponent {
    public ComponentWithDefaultConstructor() {
    }
}

class ComponentWithInjectConstructor implements TestComponent {
    private Dependency dependency;

    @Inject
    public ComponentWithInjectConstructor(Dependency dependency) {
        this.dependency = dependency;
    }

    public Dependency getDependency() {
        return dependency;
    }
}

class ComponentWithMultipleInjectConstructors implements TestComponent {
    @Inject
    public ComponentWithMultipleInjectConstructors(String name) {
    }

    @Inject
    public ComponentWithMultipleInjectConstructors(String name, Double value) {
    }
}

class ComponentWithoutInjectOrDefaultConstructors implements TestComponent {
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
    private TestComponent component;

    @Inject

    public DependencyDependedOnComponent(TestComponent component) {
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
    private TestComponent component;

    @Inject

    public AnotherDependencyDependedOnComponent(TestComponent component) {
        this.component = component;
    }
}
