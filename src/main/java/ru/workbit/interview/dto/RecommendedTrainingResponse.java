package ru.workbit.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record RecommendedTrainingResponse(
        @Schema(description = "Отстающий навык из отчёта интервью", example = "Многопоточность в Java")
        String skill,

        @Schema(description = "Средняя оценка интервью, в котором навык признан самым слабым", example = "1.8")
        Double interviewScore,

        @Schema(description = "Идентификатор тренировки по навыку, null если тренировка не начата")
        UUID trainingSessionId,

        @Schema(description = "Статус тренировки, null если тренировка не начата",
                allowableValues = {"CREATED", "IN_PROGRESS", "COMPLETED"})
        String trainingStatus,

        @Schema(description = "Средняя оценка завершённой тренировки, null пока тренировка не завершена", example = "4.2")
        Double trainingScore,

        @Schema(description = "Количество отвеченных основных вопросов тренировки, null если тренировка не начата", example = "4")
        Integer answeredCount,

        @Schema(description = "Общее количество основных вопросов тренировки, null если тренировка не начата", example = "10")
        Integer totalQuestions
) {
}
