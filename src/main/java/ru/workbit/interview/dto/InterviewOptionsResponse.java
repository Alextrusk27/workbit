package ru.workbit.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.workbit.interview.model.CompanyType;
import ru.workbit.interview.model.Level;
import ru.workbit.interview.model.Profession;

import java.util.List;

public record InterviewOptionsResponse(
        @Schema(description = "Допустимые профессии для собеседования", example = "[\"Java-разработчик\", \"Python-разработчик\", \"Инженер по тестированию\"]")
        List<Profession> professions,

        @Schema(description = "Допустимые уровни кандидата", example = "[\"Junior\", \"Middle\", \"Senior\", \"Lead\"]")
        List<Level> levels,

        @Schema(description = "Допустимые типы компании", example = "[\"Банк\", \"Финтех\", \"Стартап\", \"Продуктовая компания\", \"Аутсорс\", \"Государственная компания\"]")
        List<CompanyType> companyTypes,

        @Schema(description = "Минимально допустимое количество вопросов в сессии", example = "10")
        int minQuestions,

        @Schema(description = "Максимально допустимое количество вопросов в сессии", example = "20")
        int maxQuestions
) {
}
