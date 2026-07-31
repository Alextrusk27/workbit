package ru.workbit.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record InterviewVacancyDetailResponse(
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

        @Schema(description = "Интервью по вакансии, старые первыми")
        List<InterviewAttemptResponse> interviews,

        @Schema(description = "Рекомендованные тренировки по отстающим навыкам из отчётов интервью, самые слабые первыми")
        List<RecommendedTrainingResponse> recommendedTrainings
) {
}
