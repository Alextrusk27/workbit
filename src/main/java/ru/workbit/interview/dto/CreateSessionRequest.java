package ru.workbit.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.workbit.interview.model.Level;

public record CreateSessionRequest(
        @Schema(description = "Профессия, по которой проводится собеседование", example = "Java-разработчик")
        @NotBlank
        @Size(max = 100)
        String profession,

        @Schema(description = "Тема тренировки: технология или область знаний, null - общие вопросы по профессии", example = "Spring Boot")
        @Size(max = 100)
        String topic,

        @Schema(description = "Целевой уровень кандидата", example = "Middle")
        @NotNull
        Level level
) {
}
