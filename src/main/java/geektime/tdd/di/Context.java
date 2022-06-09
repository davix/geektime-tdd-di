package geektime.tdd.di;

import java.util.Optional;

public interface Context {
    <T> Optional<T> get(Class<T> type);
}
