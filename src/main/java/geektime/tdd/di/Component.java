package geektime.tdd.di;

import java.lang.annotation.Annotation;

record Component(Class<?> type, Annotation qualifier) {
}
