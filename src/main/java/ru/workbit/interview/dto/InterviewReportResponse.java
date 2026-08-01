package ru.workbit.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.workbit.interview.model.InterviewReport;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InterviewReportResponse(
        @Schema(description = "Идентификатор отчёта")
        UUID reportId,

        @Schema(description = "Идентификатор сессии, по которой сформирован отчёт")
        UUID sessionId,

        @Schema(description = "Средний балл по всем оценённым ответам (1.0-5.0)", example = "3.8")
        Double avgScore,

        @Schema(description = "Оценка вероятности оффера по вакансии", example = "Средняя")
        InterviewReport.OfferProbability offerProbability,

        @Schema(description = "Итоговый текстовый фидбэк по интервью от LLM")
        String overallFeedback,

        @Schema(description = "Рекомендации, что проработать перед реальным собеседованием; может отсутствовать")
        String recommendations,

        @Schema(description = "Самый слабый навык по итогам интервью — название темы для тренировки; может отсутствовать",
                example = "Многопоточность")
        String weakestSkill,

        @Schema(description = "Момент формирования отчёта")
        Instant generatedAt,

        @Schema(description = "Отвеченные вопросы сессии с поразборным фидбэком")
        List<InterviewQuestionResponse> questions
) {
}
