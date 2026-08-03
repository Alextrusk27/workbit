package ru.workbit.training.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.workbit.training.model.*;

import java.time.Instant;
import java.util.UUID;

public record TrainingSessionResponse(
        @Schema(description = "Идентификатор сессии")
        UUID id,

        @Schema(description = "Навык, который тренируется", example = "Spring Boot")
        String skill,

        @Schema(description = "Профессия, в контексте которой тренируется навык", example = "Java-разработчик")
        String profession,

        @Schema(description = "Уровень сложности вопросов", example = "Уверенный")
        TrainingSession.Level level,

        @Schema(description = "Статус сессии")
        TrainingSession.Status status,

        @Schema(description = "Количество вопросов, на которые уже дан ответ", example = "3")
        int answeredCount,

        @Schema(description = "Момент создания сессии")
        Instant created,

        @Schema(description = "Момент завершения сессии, null пока сессия не завершена")
        Instant completedAt

) {
}
