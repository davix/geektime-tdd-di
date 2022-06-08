package geektime.tdd.di;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ContainerTest {
    interface Component {

    }
    @Nested
    public class ComponentConstruction {
        @Test
        public void should_bind_to_a_specific_instance() {
            Context context = new Context();

            Component instance = new Component(){
            };
            context.bind(Component.class, instance);

            assertSame(instance, context.get(Component.class));
        }

        @Nested
        public class ConstructionInjection {

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