package ru.workbit.training.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.workbit.training.model.TrainingSession;

import java.util.List;

public record TrainingOptionsResponse(
        @Schema(description = "Популярные навыки из словаря для быстрого выбора; свободный ввод тоже допустим")
        List<String> skills,

        @Schema(description = "Популярные профессии из словаря для быстрого выбора; свободный ввод тоже допустим")
        List<String> professions,

        @Schema(description = "Доступные уровни сложности вопросов")
        List<TrainingSession.Level> levels,

        @Schema(description = "Максимальное число вопросов в тренировке", example = "10")
        int questionCap,

        @Schema(description = "Минимум отвеченных вопросов для завершения тренировки", example = "3")
        int minAnswersToFinish
) {
}
