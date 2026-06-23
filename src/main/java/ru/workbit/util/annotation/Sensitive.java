package ru.workbit.util.annotation;

import java.lang.annotation.*;

/**
 * Помечает параметр метода как чувствительный: при {@code @Loggable(logArgs = true)}
 * его значение не попадает в лог, а заменяется на {@code ***}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Sensitive {
}
