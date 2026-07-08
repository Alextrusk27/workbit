package ru.workbit.exception.controller;

import ru.workbit.exception.*;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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
            MissingServletRequestParameterException.class,
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
                    case StreamReadException sre -> {
                        yield List.of("Invalid JSON: " + sre.getOriginalMessage());
                    }
                    case InvalidFormatException ife -> {
                        String field = ife.getPath().isEmpty() ? "unknown" : ife.getPath().getLast().getPropertyName();
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

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<@NotNull ApiError> handleForbidden(final ForbiddenException e) {
        log.warn("Forbidden exception: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(HttpStatus.FORBIDDEN, "Forbidden.",
                        Collections.singletonList(e.getMessage())));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<@NotNull ApiError> handleConflict(final ConflictException e) {
        log.warn("Conflict exception: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT, "Conflict.",
                        Collections.singletonList(e.getMessage())));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<@NotNull ApiError> handleIllegalArgument(final IllegalArgumentException e) {
        log.warn("Illegal argument exception: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(HttpStatus.BAD_REQUEST, "Bad request.",
                        Collections.singletonList(e.getMessage())));
    }

    @ExceptionHandler(VacancyFetchException.class)
    public ResponseEntity<@NotNull ApiError> handleVacancyFetch(final VacancyFetchException e) {
        log.warn("Vacancy fetch exception: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of(HttpStatus.SERVICE_UNAVAILABLE, "Vacancy service unavailable.",
                        Collections.singletonList(e.getMessage())));
    }

    @ExceptionHandler(LlmException.class)
    public ResponseEntity<@NotNull ApiError> handleLlm(final LlmException e) {
        log.warn("LLM exception: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of(HttpStatus.SERVICE_UNAVAILABLE, "AI service unavailable.",
                        Collections.singletonList(e.getMessage())));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<@NotNull ApiError> handleMethodNotSupported(final HttpRequestMethodNotSupportedException e) {
        log.warn("Method not supported exception: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiError.of(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed.",
                        Collections.singletonList(e.getMessage())));
    }

    @ExceptionHandler(InternalServerException.class)
    public ResponseEntity<@NotNull ApiError> handleInternalServer(final InternalServerException e) {
        log.error("Internal Server Error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                        List.of("Unexpected error occurred")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<@NotNull ApiError> handleAll(final Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                        List.of("Unexpected error occurred")));
    }
}
