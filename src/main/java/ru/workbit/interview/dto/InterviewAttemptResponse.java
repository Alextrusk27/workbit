package ru.workbit.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.workbit.interview.model.InterviewReport;
import ru.workbit.interview.model.InterviewSession;

import java.time.Instant;
import java.util.UUID;

public record InterviewAttemptResponse(
        @Schema(description = "Идентификатор сессии интервью")
        UUID sessionId,

        @Schema(description = "Статус сессии")
        InterviewSession.Status status,

        @Schema(description = "Момент создания сессии")
        Instant created,

        @Schema(description = "Момент завершения сессии, null пока сессия не завершена")
        Instant completedAt,

        @Schema(description = "Средняя оценка по отчёту, null пока сессия не завершена", example = "3.2")
        Double avgScore,

        @Schema(description = "Вероятность оффера по отчёту, null пока сессия не завершена")
        InterviewReport.OfferProbability offerProbability
) {
}
