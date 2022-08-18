package geektime.tdd.di;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.ParameterizedType;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;

public class InjectionTest {
    private Dependency dependency = Mockito.mock(Dependency.class);
    private Provider<Dependency> dependencyProvider = Mockito.mock(Provider.class);
    private Context context = Mockito.mock(Context.class);

    @BeforeEach
    public void setup() throws NoSuchFieldException {
        ParameterizedType providerType = (ParameterizedType) InjectionTest.class.getDeclaredField("dependencyProvider").getGenericType();

        Mockito.when(context.get(eq(Dependency.class))).thenReturn(Optional.of(dependency));
        Mockito.when(context.get(eq(providerType))).thenReturn(Optional.of(dependencyProvider));
    }

    @Nested
    public class ConstructionInjection {
        @Nested
        class Injection {
            @Test
            public void should_call_default_constructor_if_no_inject() {
                ComponentWithDefaultConstructor instance = new InjectionProvider<>(ComponentWithDefaultConstructor.class).get(context);
                assertNotNull(instance);
            }

            @Test
            public void should_inject_dependency_via_inject_constructor() {
                ComponentWithInjectConstructor instance = new InjectionProvider<>(ComponentWithInjectConstructor.class).get(context);
                assertNotNull(instance);
                assertEquals(dependency, instance.getDependency());
            }

            @Test
            public void should_include_dependencies_from_inject_constructor() {
                InjectionProvider<ComponentWithInjectConstructor> provider = new InjectionProvider<>(ComponentWithInjectConstructor.class);
                assertArrayEquals(new Class<?>[]{Dependency.class}, provider.getDependencies().toArray(Class<?>[]::new));
            }

            //TODO include dependency type from constructor

            static class ProviderInjectConstructor {
                Provider<Dependency> dependency;

                @Inject
                public ProviderInjectConstructor(Provider<Dependency> dependency) {
                    this.dependency = dependency;
                }
            }

            @Test
            public void should_inject_provider_via_inject_constructor() {
                ProviderInjectConstructor instance = new InjectionProvider<>(ProviderInjectConstructor.class).get(context);

                assertSame(dependencyProvider, instance.dependency);
            }
        }

        @Nested
        class IllegalConstructors {

            abstract class AbstractComponent {
                @Inject
                public AbstractComponent() {
                }
            }

            @Test
            public void should_throw_exception_if_component_is_abstraction() {
                assertThrows(IllegalComponentException.class, () -> new InjectionProvider<>(AbstractComponent.class));
            }

            @Test
            public void should_throw_exception_if_component_is_interface() {
                assertThrows(IllegalComponentException.class, () -> new InjectionProvider<>(Component.class));
            }

            @Test
            public void should_throw_exception_if_multi_inject_constructors_provided() {
                assertThrows(IllegalComponentException.class, () -> new InjectionProvider<>(ComponentWithMultipleInjectConstructors.class));
            }

            @Test
            public void should_throw_exception_if_no_inject_nor_default_constructor_provided() {
                assertThrows(IllegalComponentException.class, () -> new InjectionProvider<>(ComponentWithoutInjectOrDefaultConstructors.class));
            }
        }

    }


    @Nested
    public class FieldInjection {
        @Nested
        class Injection {
            static class ComponentWithFieldInjection {
                @Inject
                Dependency dependency;
            }

            static class SubclassWithFieldInjection extends ComponentWithFieldInjection {
            }

            @Test
            public void should_inject_dependency_via_field() {
                ComponentWithFieldInjection component = new InjectionProvider<>(ComponentWithFieldInjection.class).get(context);
                assertSame(dependency, component.dependency);
            }

            @Test
            public void should_inject_dependency_via_superclass_inject_field() {
                SubclassWithFieldInjection component = new InjectionProvider<>(SubclassWithFieldInjection.class).get(context);
                assertSame(dependency, component.dependency);
            }

            //TODO include dependency type from field

            @Test
            public void should_include_field_dependency_in_dependencies() {
                InjectionProvider<ComponentWithFieldInjection> provider = new InjectionProvider<>(ComponentWithFieldInjection.class);
                assertArrayEquals(new Class<?>[]{Dependency.class}, provider.getDependencies().toArray(Class<?>[]::new));
            }

            static class ProviderInjectField {
                @Inject
                Provider<Dependency> dependency;
            }

            @Test
            public void should_inject_provider_via_inject_field() {
                ProviderInjectField instance = new InjectionProvider<>(ProviderInjectField.class).get(context);

                assertSame(dependencyProvider, instance.dependency);
            }
        }

        @Nested
        class IllegalFields {
            static class FinalInjectField {
                @Inject
                final Dependency dependency = null;
            }

            @Test
            public void should_throw_exception_if_inject_field_is_final() {
                assertThrows(IllegalComponentException.class, () -> new InjectionProvider<>(FinalInjectField.class));
            }
        }

    }

    @Nested
    public class MethodInjection {
        @Nested
        class Injection {

            static class InjectMethodWithNoDependency {
                boolean called = false;

                @Inject
                void install() {
                    this.called = true;
                }
            }

            @Test
            public void should_call_inject_method_even_if_no_dependency_declared() {
                InjectMethodWithNoDependency component = new InjectionProvider<>(InjectMethodWithNoDependency.class).get(context);
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
                InjectMethodWithDependency component = new InjectionProvider<>(InjectMethodWithDependency.class).get(context);
                assertSame(dependency, component.dependency);
            }

            //TODO include dependency type from method

            static class SuperclassWithInjectMethod {
                int supperCalled = 0;

                @Inject
                void install() {
                    this.supperCalled++;
                }
            }

            static class SubclassWithInjectMethod extends SuperclassWithInjectMethod {
                int subCalled = 0;

                @Inject
                void installAnother() {
                    this.subCalled = supperCalled + 1;
                }
            }

            @Test
            public void should_inject_dependencies_via_inject_from_superclass() {
                SubclassWithInjectMethod component = new InjectionProvider<>(SubclassWithInjectMethod.class).get(context);
                assertEquals(1, component.supperCalled);
                assertEquals(2, component.subCalled);
            }

            static class SubclassOverrideSuperclassWithInject extends SuperclassWithInjectMethod {
                @Inject
                void install() {
                    super.install();
                }
            }

            @Test
            public void should_only_call_once_if_subclass_override_inject_method_with_inject() {
                SubclassOverrideSuperclassWithInject component = new InjectionProvider<>(SubclassOverrideSuperclassWithInject.class).get(context);
                assertEquals(1, component.supperCalled);
            }

            static class SubclassOverrideSuperclassWithoutInject extends SuperclassWithInjectMethod {
                void install() {
                    super.install();
                }
            }

            @Test
            public void should_not_call_inject_method_if_override_without_inject() {
                SubclassOverrideSuperclassWithoutInject component = new InjectionProvider<>(SubclassOverrideSuperclassWithoutInject.class).get(context);
                assertEquals(0, component.supperCalled);
            }

            @Test
            public void should_include_method_dependency_in_dependencies() {
                InjectionProvider<InjectMethodWithDependency> provider = new InjectionProvider<>(InjectMethodWithDependency.class);
                assertArrayEquals(new Class<?>[]{Dependency.class}, provider.getDependencies().toArray(Class<?>[]::new));
            }

            static class ProviderInjectMethod {
                Provider<Dependency> dependency;

                @Inject
                void install(Provider<Dependency> dependency) {
                    this.dependency = dependency;
                }
            }

            @Test
            public void should_inject_provider_via_inject_method() {
                ProviderInjectMethod instance = new InjectionProvider<>(ProviderInjectMethod.class).get(context);

                assertSame(dependencyProvider, instance.dependency);
            }

        }

        @Nested
        class IllegalMethods {
            static class InjectMethodWithTypeParameter {
                @Inject
                <T> void install() {
                }
            }

            @Test
            public void should_throw_exception_if_inject_method_has_type_parameter() {
                assertThrows(IllegalComponentException.class, () -> new InjectionProvider<>(InjectMethodWithTypeParameter.class));
            }
        }
    }
}
