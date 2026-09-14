package ru.workbit.exception.dto;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;

public record ApiError(
        String timestamp,
        String status,
        String message,
        List<String> errors
) {

    public static ApiError of(HttpStatus status, String message, List<String> errors) {
        return new ApiError(
                LocalDateTime.now().toString(),
                status.name(),
                message,
                errors
        );
    }
}
