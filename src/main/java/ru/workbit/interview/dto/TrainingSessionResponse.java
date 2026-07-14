package ru.workbit.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.workbit.interview.model.*;

import java.time.Instant;
import java.util.UUID;

public record TrainingSessionResponse(
        @Schema(description = "Идентификатор сессии")
        UUID id,

        @Schema(description = "Профессия, по которой проводится собеседование", example = "Java-разработчик")
        Profession profession,

        @Schema(description = "Тип компании, под который стилизуются вопросы", example = "Продуктовая компания")
        CompanyType companyType,

        @Schema(description = "Целевой уровень кандидата", example = "Middle")
        Level level,

        @Schema(description = "Статус сессии")
        SessionStatus status,

        @Schema(description = "Количество основных вопросов, на которые уже дан ответ (уточняющие не считаются)", example = "3")
        int answeredCount,

        @Schema(description = "Момент создания сессии")
        Instant created,

        @Schema(description = "Момент завершения сессии, null пока сессия не завершена")
        Instant completedAt

) implements SessionResponse {
}
