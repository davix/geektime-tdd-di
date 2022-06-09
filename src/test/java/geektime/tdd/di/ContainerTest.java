package geektime.tdd.di;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.internal.util.collections.Sets;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ContainerTest {
    ContextConfig config;

    @BeforeEach
    public void setup() {
        config = new ContextConfig();
    }

    @Nested
    public class ComponentConstruction {
        @Test
        public void should_bind_to_a_specific_instance() {
            Component instance = new Component() {
            };
            config.bind(Component.class, instance);

            assertSame(instance, config.getContext().get(Component.class).get());
        }

        @Test
        public void should_return_empty_if_no_component_defined() {
            Optional<Component> component = config.getContext().get(Component.class);
            assertTrue(component.isEmpty());
        }

        @Nested
        public class ConstructionInjection {
            @Test
            public void should_bind_type_to_a_class_with_default_constructor() {
                config.bind(Component.class, ComponentWithDefaultConstructor.class);

                Component instance = config.getContext().get(Component.class).get();
                assertNotNull(instance);
                assertTrue(instance instanceof ComponentWithDefaultConstructor);
            }

            @Test
            public void should_bind_type_to_a_class_with_inject_constructor() {
                Dependency dependency = new Dependency() {
                };
                config.bind(Component.class, ComponentWithInjectConstructor.class);
                config.bind(Dependency.class, dependency);

                Component instance = config.getContext().get(Component.class).get();
                assertNotNull(instance);
                assertEquals(dependency, ((ComponentWithInjectConstructor) instance).getDependency());
            }

            @Test
            public void should_bind_type_to_a_class_with_transitive_dependencies() {
                config.bind(Component.class, ComponentWithInjectConstructor.class);
                config.bind(Dependency.class, DependencyWithInjectConstructor.class);
                String dependency2 = "indirect dependency";
                config.bind(String.class, dependency2);

                Component instance = config.getContext().get(Component.class).get();
                assertNotNull(instance);

                Dependency dependency1 = ((ComponentWithInjectConstructor) instance).getDependency();
                assertNotNull(dependency1);

                assertEquals(dependency2, ((DependencyWithInjectConstructor) dependency1).getDependency());
            }

            @Test
            public void should_throw_exception_if_multi_inject_constructors_provided() {
                assertThrows(IllegalComponentException.class, () -> {
                    config.bind(Component.class, ComponentWithMultipleInjectConstructors.class);
                });
            }

            @Test
            public void should_throw_exception_if_no_inject_nor_default_constructor_provided() {
                assertThrows(IllegalComponentException.class, () -> {
                    config.bind(Component.class, ComponentWithoutInjectOrDefaultConstructors.class);
                });
            }

            @Test
            public void should_throw_exception_if_dependency_not_found() {
                config.bind(Component.class, ComponentWithInjectConstructor.class);

                DependencyNotFoundException exception = assertThrows(DependencyNotFoundException.class, () -> config.getContext());
                assertEquals(Dependency.class, exception.getDependency());
            }

            @Test
            public void should_throw_exception_if_transitive_dependency_not_found() {
                config.bind(Component.class, ComponentWithInjectConstructor.class);
                config.bind(Dependency.class, DependencyWithInjectConstructor.class);

                DependencyNotFoundException exception = assertThrows(DependencyNotFoundException.class, () -> config.getContext().get(Component.class));
                assertEquals(String.class, exception.getDependency());
                assertEquals(Dependency.class, exception.getComponent());
            }

            @Test
            public void should_throw_exception_if_cyclic_dependencies_found() {
                config.bind(Component.class, ComponentWithInjectConstructor.class);
                config.bind(Dependency.class, DependencyDependedOnComponent.class);

                CyclicDependenciesFound exception = assertThrows(CyclicDependenciesFound.class, () -> config.getContext().get(Component.class));
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

                CyclicDependenciesFound exception = assertThrows(CyclicDependenciesFound.class, () -> config.getContext().get(Component.class));

                List<Class<?>> classes = Arrays.asList(exception.getComponents());
                assertEquals(3, classes.size());
                assertTrue(classes.contains(Component.class));
                assertTrue(classes.contains(Dependency.class));
                assertTrue(classes.contains(AnotherDependency.class));
            }
        }

        @Nested
        public class FieldInjection {

        }

        @Nested
        public class MethodInjection {

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
