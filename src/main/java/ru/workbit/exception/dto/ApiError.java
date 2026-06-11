package ru.workbit.exception.dto;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ApiError(
        String timestamp,
        HttpStatus status,
        String message,
        List<String> errors
) {

    public static ApiError of(HttpStatus status, String message, List<String> errors) {
        return new ApiError(
                LocalDateTime.now().toString(),
                status,
                message,
                errors
        );
    }
}
