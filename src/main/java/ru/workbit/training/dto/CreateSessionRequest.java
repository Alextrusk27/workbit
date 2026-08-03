package ru.workbit.training.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.workbit.training.model.TrainingSession;

public record CreateSessionRequest(
        @Schema(description = "Навык, который тренируется", example = "Spring Boot")
        @NotBlank
        @Size(max = 100)
        String skill,

        @Schema(description = "Профессия, в контексте которой тренируется навык", example = "Java-разработчик")
        @NotBlank
        @Size(max = 100)
        String profession,

        @Schema(description = "Уровень сложности вопросов", example = "Уверенный")
        @NotNull
        TrainingSession.Level level
) {
}
