package ru.workbit.util.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Loggable {
    String level() default "INFO";

    boolean logArgs() default false;

    boolean logResult() default false;
}
