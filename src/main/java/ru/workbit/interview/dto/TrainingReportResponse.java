package ru.workbit.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.workbit.interview.model.Level;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TrainingReportResponse(
        @Schema(description = "Идентификатор отчёта")
        UUID reportId,

        @Schema(description = "Идентификатор сессии, по которой сформирован отчёт")
        UUID sessionId,

        @Schema(description = "Профессия, по которой проводилось собеседование", example = "Java-разработчик")
        String profession,

        @Schema(description = "Тема тренировки, null если не задана", example = "Spring Boot")
        String topic,

        @Schema(description = "Целевой уровень кандидата", example = "Middle")
        Level level,

        @Schema(description = "Средний балл по всем оценённым ответам (1.0-5.0)", example = "3.8")
        Double avgScore,

        @Schema(description = "Итоговый текстовый фидбэк по тренировке от LLM")
        String overallFeedback,

        @Schema(description = "Момент формирования отчёта")
        Instant generatedAt,

        @Schema(description = "Отвеченные вопросы сессии с поразборным фидбэком")
        List<TrainingQuestionResponse> questions

) implements ReportResponse {
}
