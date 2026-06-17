package ru.workbit.util.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import ru.workbit.util.annotation.Loggable;

import java.util.Arrays;

@Aspect
@Slf4j
@Component
public class LoggingAspect {

    @Around("@annotation(loggable)")
    public Object logMethod(ProceedingJoinPoint pjp, Loggable loggable) throws Throwable {
        Signature sig = pjp.getSignature();
        String method = sig.getDeclaringType().getSimpleName() + "." + sig.getName();
        String level = loggable.level();

        if (loggable.logArgs()) {
            logAt(level, "→ {} | args: {}", method, Arrays.toString(pjp.getArgs()));
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
            log.error("✗ {} | exception: {}", method, ex.getMessage(), ex);
            throw ex;
        }
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
