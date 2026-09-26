package ru.workbit.exception.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;

@Schema(description = "Тело ответа с ошибкой")
public record ApiError(
        @Schema(description = "Момент ошибки по времени сервера, ISO-8601 без часового пояса",
                example = "2026-09-26T12:34:56.789")
        String timestamp,

        @Schema(description = "Имя HTTP-статуса", example = "BAD_REQUEST")
        String status,

        @Schema(description = "Общее описание ошибки", example = "Validation Failed")
        String message,

        @Schema(description = "Подробности: нарушения валидации или текст исключения",
                example = "[\"email: must be a well-formed email address\"]")
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
