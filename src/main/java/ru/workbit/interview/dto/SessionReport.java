package ru.workbit.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.workbit.interview.model.CompanyType;
import ru.workbit.interview.model.Level;
import ru.workbit.interview.model.OfferProbability;
import ru.workbit.interview.model.Profession;

import java.time.Instant;
import java.util.UUID;

public record SessionReport(
        @Schema(description = "Идентификатор отчёта")
        UUID reportId,

        @Schema(description = "Идентификатор сессии, по которой сформирован отчёт")
        UUID sessionId,

        @Schema(description = "Профессия, по которой проводилось собеседование", example = "Java-разработчик")
        Profession profession,

        @Schema(description = "Тип компании, под который стилизовались вопросы", example = "Продуктовая компания")
        CompanyType companyType,

        @Schema(description = "Целевой уровень кандидата", example = "Middle")
        Level level,

        @Schema(description = "Общее количество вопросов в сессии", example = "10")
        Integer totalQuestions,

        @Schema(description = "Средний балл по всем оценённым ответам", example = "7.5")
        Double avgScore,

        @Schema(description = "Итоговый текстовый фидбэк по собеседованию от LLM")
        String overallFeedback,

        @Schema(description = "Оценка вероятности получения оффера", example = "Средняя")
        OfferProbability offerProbability,

        @Schema(description = "Момент формирования отчёта")
        Instant generatedAt
) {
}
