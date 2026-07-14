package ru.workbit.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.workbit.interview.model.CompanyType;
import ru.workbit.interview.model.Level;
import ru.workbit.interview.model.Profession;

import java.util.List;

public record TrainingOptionsResponse(
        @Schema(description = "Доступные профессии")
        List<Profession> professions,

        @Schema(description = "Доступные уровни кандидата")
        List<Level> levels,

        @Schema(description = "Доступные типы компаний")
        List<CompanyType> companyTypes,

        @Schema(description = "Максимальное число основных вопросов в тренировке", example = "10")
        int questionCap,

        @Schema(description = "Минимум отвеченных основных вопросов для завершения тренировки", example = "3")
        int minAnswersToFinish
) {
}
