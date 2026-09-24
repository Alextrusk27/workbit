package ru.workbit.util.aspect;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
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
import ru.workbit.util.annotation.Loggable;
import ru.workbit.util.annotation.Sensitive;

@Aspect
@Slf4j
@Component
public class LoggingAspect {

    @Around("@annotation(loggable)")
    public Object logMethod(ProceedingJoinPoint pjp, Loggable loggable) throws Throwable {
        Signature sig = pjp.getSignature();
        String method = sig.getDeclaringType().getSimpleName() + "." + sig.getName();
        String level = loggable.level();

        String args = loggable.logArgs() ? " | args: " + formatArgs(pjp, sig) : "";

        log.debug("→ {}{}", method, args);
        long start = System.currentTimeMillis();

        Object result = pjp.proceed();
        long duration = System.currentTimeMillis() - start;
        if (loggable.logResult()) {
            logAt(level, "← {}{} | result: {} | {}ms", method, args, result, duration);
        } else {
            logAt(level, "← {}{} | {}ms", method, args, duration);
        }
        return result;
    }

    private String formatArgs(ProceedingJoinPoint pjp, Signature sig) {
        Object[] args = pjp.getArgs();
        MethodSignature signature = (MethodSignature) sig;
        Annotation[][] paramAnnotations = signature.getMethod().getParameterAnnotations();
        Class<?>[] paramTypes = signature.getParameterTypes();
        String[] paramNames = signature.getParameterNames();
        List<String> rendered = new ArrayList<>(args.length);
        for (int i = 0; i < args.length; i++) {
            if (!isSensitive(paramAnnotations[i]) && !isInfrastructure(paramTypes[i])) {
                rendered.add(paramNames[i] + "=" + args[i]);
            }
        }
        return rendered.toString();
    }

    private boolean isInfrastructure(Class<?> type) {
        return ServletRequest.class.isAssignableFrom(type) || ServletResponse.class.isAssignableFrom(type);
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
