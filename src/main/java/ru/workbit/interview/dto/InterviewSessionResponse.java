package ru.workbit.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.workbit.interview.model.InterviewSession;

import java.time.Instant;
import java.util.UUID;

public record InterviewSessionResponse(
        @Schema(description = "Идентификатор сессии")
        UUID id,

        @Schema(description = "Название вакансии", example = "Java-разработчик")
        String vacancyName,

        @Schema(description = "Название работодателя", example = "ООО Ромашка")
        String employer,

        @Schema(description = "Статус сессии")
        InterviewSession.Status status,

        @Schema(description = "Количество основных вопросов, на которые уже дан ответ (уточняющие не считаются)", example = "3")
        int answeredCount,

        @Schema(description = "Общее количество основных вопросов интервью", example = "10")
        int totalQuestions,

        @Schema(description = "Момент создания сессии")
        Instant created,

        @Schema(description = "Момент завершения сессии, null пока сессия не завершена")
        Instant completedAt
) {
}
