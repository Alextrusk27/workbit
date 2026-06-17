package ru.workbit.exception.controller;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.workbit.exception.BadCredentialsException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.exception.dto.ApiError;

import java.util.Collections;
import java.util.List;

@RestControllerAdvice
@Slf4j
public class ExceptionController {

    @ExceptionHandler({
            ConstraintViolationException.class,
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<@NotNull ApiError> handleSpringValidation(final Exception e) {
        List<String> errors = switch (e) {
            case ConstraintViolationException cve -> cve.getConstraintViolations().stream()
                    .map(ConstraintViolation::getMessage)
                    .toList();

            case MethodArgumentNotValidException mnv -> mnv.getBindingResult().getFieldErrors().stream()
                    .map(DefaultMessageSourceResolvable::getDefaultMessage)
                    .toList();

            case MethodArgumentTypeMismatchException mtm -> {
                String typeName = mtm.getRequiredType() != null ?
                        mtm.getRequiredType().getName() : "unknown";
                yield List.of("Parameter '%s' should be of type %s".formatted(mtm.getName(), typeName));
            }

            case HttpMessageNotReadableException hmr -> {
                Throwable cause = hmr.getCause();
                switch (cause) {
                    case null -> {
                        yield List.of("Request body is required");
                    }
                    case JsonParseException jpe -> {
                        yield List.of("Invalid JSON: " + jpe.getOriginalMessage());
                    }
                    case InvalidFormatException ife -> {
                        String field = ife.getPath().isEmpty() ? "unknown" : ife.getPath().getLast().getFieldName();
                        yield List.of("Invalid value for field '%s': %s".formatted(field, ife.getValue()));
                    }
                    default -> {
                        yield List.of("Invalid request body format");
                    }
                }
            }
            default -> List.of(e.getMessage());
        };

        log.warn("Spring validation exception: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(HttpStatus.BAD_REQUEST, "Validation Failed", errors));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<@NotNull ApiError> handleBadCredentials(final BadCredentialsException e) {
        log.warn("Bad credentials exception: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(HttpStatus.UNAUTHORIZED, "Bad credentials",
                        Collections.singletonList(e.getMessage())));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<@NotNull ApiError> handleNotFound(final NotFoundException e) {
        log.warn("NotFound exception: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND, "The required object was not found.",
                        Collections.singletonList(e.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<@NotNull ApiError> handleAll(final Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                        List.of("Unexpected error occurred")));
    }

//    HttpRequestMethodNotSupportedException
}
