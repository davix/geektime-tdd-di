package geektime.tdd.di;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class InjectionTest {
    ContextConfig config;

    @BeforeEach
    public void setup() {
        config = new ContextConfig();
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

        abstract class AbstractComponent {
            @Inject
            public AbstractComponent() {
            }
        }

        @Test
        public void should_throw_exception_if_component_is_abstraction() {
            assertThrows(IllegalComponentException.class, () -> new ConstructorProvider<>(ConstructionInjection.AbstractComponent.class));
        }

        @Test
        public void should_throw_exception_if_component_is_interface() {
            assertThrows(IllegalComponentException.class, () -> new ConstructorProvider<>(Component.class));
        }

        @Test
        public void should_throw_exception_if_multi_inject_constructors_provided() {
            assertThrows(IllegalComponentException.class, () -> new ConstructorProvider<>(ComponentWithMultipleInjectConstructors.class));
        }

        @Test
        public void should_throw_exception_if_no_inject_nor_default_constructor_provided() {
            assertThrows(IllegalComponentException.class, () -> new ConstructorProvider<>(ComponentWithoutInjectOrDefaultConstructors.class));
        }

        @Test
        public void should_include_dependencies_from_inject_constructor() {
            ConstructorProvider<ComponentWithInjectConstructor> provider = new ConstructorProvider<>(ComponentWithInjectConstructor.class);
            assertArrayEquals(new Class<?>[]{Dependency.class}, provider.getDependencies().toArray(Class<?>[]::new));
        }
    }


    @Nested
    public class FieldInjection {
        static class ComponentWithFieldInjection {
            @Inject
            Dependency dependency;
        }

        static class SubclassWithFieldInjection extends FieldInjection.ComponentWithFieldInjection {
        }

        @Test
        public void should_inject_dependency_via_field() {
            Dependency dependency = new Dependency() {
            };
            config.bind(Dependency.class, dependency);
            config.bind(FieldInjection.ComponentWithFieldInjection.class, FieldInjection.ComponentWithFieldInjection.class);

            FieldInjection.ComponentWithFieldInjection component = config.getContext().get(FieldInjection.ComponentWithFieldInjection.class).get();
            assertSame(dependency, component.dependency);
        }

        @Test
        public void should_inject_dependency_via_superclass_inject_field() {
            Dependency dependency = new Dependency() {
            };
            config.bind(Dependency.class, dependency);
            config.bind(FieldInjection.SubclassWithFieldInjection.class, FieldInjection.SubclassWithFieldInjection.class);

            FieldInjection.SubclassWithFieldInjection component = config.getContext().get(FieldInjection.SubclassWithFieldInjection.class).get();
            assertSame(dependency, component.dependency);
        }

        static class FinalInjectField {
            @Inject
            final Dependency dependency = null;
        }

        @Test
        public void should_throw_exception_if_inject_field_is_final() {
            assertThrows(IllegalComponentException.class, () -> new ConstructorProvider<>(FieldInjection.FinalInjectField.class));
        }

        @Test
        public void should_include_field_dependency_in_dependencies() {
            ConstructorProvider<FieldInjection.ComponentWithFieldInjection> provider = new ConstructorProvider<>(FieldInjection.ComponentWithFieldInjection.class);
            assertArrayEquals(new Class<?>[]{Dependency.class}, provider.getDependencies().toArray(Class<?>[]::new));
        }
    }

    @Nested
    public class MethodInjection {
        static class InjectMethodWithNoDependency {
            boolean called = false;

            @Inject
            void install() {
                this.called = true;
            }
        }

        @Test
        public void should_call_inject_method_even_if_no_dependency_declared() {
            config.bind(MethodInjection.InjectMethodWithNoDependency.class, MethodInjection.InjectMethodWithNoDependency.class);
            MethodInjection.InjectMethodWithNoDependency component = config.getContext().get(MethodInjection.InjectMethodWithNoDependency.class).get();
            assertTrue(component.called);
        }

        static class InjectMethodWithDependency {
            private Dependency dependency;

            @Inject
            public void install(Dependency dependency) {
                this.dependency = dependency;
            }
        }

        @Test
        public void should_inject_dependency_via_method() {
            Dependency dependency = new Dependency() {
            };
            config.bind(Dependency.class, dependency);
            config.bind(MethodInjection.InjectMethodWithDependency.class, MethodInjection.InjectMethodWithDependency.class);

            MethodInjection.InjectMethodWithDependency component = config.getContext().get(MethodInjection.InjectMethodWithDependency.class).get();
            assertSame(dependency, component.dependency);
        }

        static class SuperclassWithInjectMethod {
            int supperCalled = 0;

            @Inject
            void install() {
                this.supperCalled++;
            }
        }

        static class SubclassWithInjectMethod extends MethodInjection.SuperclassWithInjectMethod {
            int subCalled = 0;

            @Inject
            void installAnother() {
                this.subCalled = supperCalled + 1;
            }
        }

        @Test
        public void should_inject_dependencies_via_inject_from_superclass() {
            config.bind(MethodInjection.SuperclassWithInjectMethod.class, MethodInjection.SuperclassWithInjectMethod.class);
            config.bind(MethodInjection.SubclassWithInjectMethod.class, MethodInjection.SubclassWithInjectMethod.class);
            MethodInjection.SubclassWithInjectMethod component = config.getContext().get(MethodInjection.SubclassWithInjectMethod.class).get();
            assertEquals(1, component.supperCalled);
            assertEquals(2, component.subCalled);
        }

        static class SubclassOverrideSuperclassWithInject extends MethodInjection.SuperclassWithInjectMethod {
            @Inject
            void install() {
                super.install();
            }
        }

        @Test
        public void should_only_call_once_if_subclass_override_inject_method_with_inject() {
            config.bind(MethodInjection.SubclassOverrideSuperclassWithInject.class, MethodInjection.SubclassOverrideSuperclassWithInject.class);
            MethodInjection.SubclassOverrideSuperclassWithInject component = config.getContext().get(MethodInjection.SubclassOverrideSuperclassWithInject.class).get();
            assertEquals(1, component.supperCalled);
        }

        static class SubclassOverrideSuperclassWithoutInject extends MethodInjection.SuperclassWithInjectMethod {
            void install() {
                super.install();
            }
        }

        @Test
        public void should_not_call_inject_method_if_override_without_inject() {
            config.bind(MethodInjection.SubclassOverrideSuperclassWithoutInject.class, MethodInjection.SubclassOverrideSuperclassWithoutInject.class);
            MethodInjection.SubclassOverrideSuperclassWithoutInject component = config.getContext().get(MethodInjection.SubclassOverrideSuperclassWithoutInject.class).get();
            assertEquals(0, component.supperCalled);
        }

        @Test
        public void should_include_method_dependency_in_dependencies() {
            ConstructorProvider<MethodInjection.InjectMethodWithDependency> provider = new ConstructorProvider<>(MethodInjection.InjectMethodWithDependency.class);
            assertArrayEquals(new Class<?>[]{Dependency.class}, provider.getDependencies().toArray(Class<?>[]::new));
        }

        static class InjectMethodWithTypeParameter {
            @Inject
            <T> void install() {
            }
        }

        @Test
        public void should_throw_exception_if_inject_method_has_type_parameter() {
            assertThrows(IllegalComponentException.class, () -> new ConstructorProvider<>(MethodInjection.InjectMethodWithTypeParameter.class));
        }
    }
}
