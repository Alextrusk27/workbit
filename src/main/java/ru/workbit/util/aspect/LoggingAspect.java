package ru.workbit.util.aspect;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import ru.workbit.exception.ConflictException;
import ru.workbit.util.annotation.Loggable;
import ru.workbit.util.annotation.Sensitive;

@Aspect
@Slf4j
@Component
public class LoggingAspect {

    private static final String DOMAIN_EXCEPTIONS = ConflictException.class.getPackageName();

    @Around("@annotation(loggable)")
    public Object logMethod(ProceedingJoinPoint pjp, Loggable loggable) throws Throwable {
        Signature sig = pjp.getSignature();
        String method = sig.getDeclaringType().getSimpleName() + "." + sig.getName();
        String level = loggable.level();

        if (loggable.logArgs()) {
            logAt(level, "→ {} | args: {}", method, formatArgs(pjp, sig));
        } else {
            logAt(level, "→ {}", method);
        }
        long start = System.currentTimeMillis();

        try {
            Object result = pjp.proceed();
            long duration = System.currentTimeMillis() - start;
            if (loggable.logResult()) {
                logAt(level, "← {} | result: {} | {}ms", method, result, duration);
            } else {
                logAt(level, "← {} | {}ms", method, duration);
            }
            return result;

        } catch (Throwable ex) {
            if (isExpected(ex)) {
                log.warn("✗ {} | {}: {}", method, ex.getClass().getSimpleName(), ex.getMessage());
            } else {
                log.error("✗ {} | exception: {}", method, ex.getMessage(), ex);
            }
            throw ex;
        }
    }

    private static boolean isExpected(Throwable ex) {
        return DOMAIN_EXCEPTIONS.equals(ex.getClass().getPackageName());
    }

    private String formatArgs(ProceedingJoinPoint pjp, Signature sig) {
        Object[] args = pjp.getArgs();
        Annotation[][] paramAnnotations = ((MethodSignature) sig).getMethod().getParameterAnnotations();
        List<String> rendered = new ArrayList<>(args.length);
        for (int i = 0; i < args.length; i++) {
            if (!isSensitive(paramAnnotations[i])) {
                rendered.add(String.valueOf(args[i]));
            }
        }
        return rendered.toString();
    }

    private boolean isSensitive(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof Sensitive) {
                return true;
            }
        }
        return false;
    }

    private void logAt(String level, String format, Object... args) {
        switch (level.toUpperCase()) {
            case "TRACE" -> log.trace(format, args);
            case "DEBUG" -> log.debug(format, args);
            case "WARN" -> log.warn(format, args);
            case "ERROR" -> log.error(format, args);
            default -> log.info(format, args);
        }
    }
}
