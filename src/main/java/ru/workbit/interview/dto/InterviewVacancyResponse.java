package ru.workbit.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.workbit.interview.model.InterviewReport;
import ru.workbit.interview.model.InterviewSession;

import java.time.Instant;

public record InterviewVacancyResponse(
        @Schema(description = "Идентификатор вакансии", example = "123456")
        String vacancyId,

        @Schema(description = "Название вакансии", example = "Java-разработчик")
        String vacancyName,

        @Schema(description = "Название работодателя", example = "ООО Ромашка")
        String employer,

        @Schema(description = "Ссылка на вакансию", example = "https://hh.ru/vacancy/123456")
        String vacancyUrl,

        @Schema(description = "Требуемый опыт работы из вакансии, null если не указан", example = "От 1 года до 3 лет")
        String experience,

        @Schema(description = "Статус последнего интервью по вакансии")
        InterviewSession.Status status,

        @Schema(description = "Количество завершённых интервью по вакансии", example = "3")
        int completedCount,

        @Schema(description = "Лучшая средняя оценка среди завершённых интервью, null если завершённых нет", example = "4.0")
        Double bestScore,

        @Schema(description = "Вероятность оффера лучшей попытки, null если завершённых интервью нет")
        InterviewReport.OfferProbability bestOffer,

        @Schema(description = "Момент создания последнего интервью по вакансии")
        Instant lastActivity
) {
}
