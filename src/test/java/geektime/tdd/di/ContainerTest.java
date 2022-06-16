package geektime.tdd.di;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.internal.util.collections.Sets;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

                assertSame(instance, config.getContext().get(Component.class).get());
            }

            @Test
            public void should_return_empty_if_no_component_defined() {
                Optional<Component> component = config.getContext().get(Component.class);
                assertTrue(component.isEmpty());
            }

            @ParameterizedTest(name = "supporting {0}")
            @MethodSource
            public void should_bind_type_to_an_injectable_component(Class<? extends Component> type) {
                Dependency dependency = new Dependency() {
                };
                config.bind(Dependency.class, dependency);
                config.bind(Component.class, type);

                Optional<Component> component = config.getContext().get(Component.class);
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
        }

        @Nested
        public class DependencyCheck {

            @Test
            public void should_throw_exception_if_dependency_not_found() {
                config.bind(Component.class, ComponentWithInjectConstructor.class);

                DependencyNotFoundException exception = assertThrows(DependencyNotFoundException.class, () -> config.getContext());
                assertEquals(Dependency.class, exception.getDependency());
            }

            @Test
            public void should_throw_exception_if_cyclic_dependencies_found() {
                config.bind(Component.class, ComponentWithInjectConstructor.class);
                config.bind(Dependency.class, DependencyDependedOnComponent.class);

                CyclicDependenciesFound exception = assertThrows(CyclicDependenciesFound.class, () -> config.getContext());
                Set<Class<?>> classes = Sets.newSet(exception.getComponents());
                assertEquals(2, classes.size());
                assertTrue(classes.contains(Component.class));
                assertTrue(classes.contains(Dependency.class));
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
