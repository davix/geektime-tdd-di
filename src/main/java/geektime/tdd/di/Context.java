package geektime.tdd.di;

import java.lang.reflect.ParameterizedType;
import java.util.Optional;

public interface Context {
    <T> Optional<T> get(Class<T> type);

    Optional get(ParameterizedType type);
}
