package ru.workbit.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import ru.workbit.interview.model.CompanyType;
import ru.workbit.interview.model.Level;
import ru.workbit.interview.model.Profession;

public record CreateSessionRequest(
        @Schema(description = "Профессия, по которой проводится собеседование", example = "Java-разработчик")
        @NotNull
        Profession profession,

        @Schema(description = "Целевой уровень кандидата", example = "Middle")
        @NotNull
        Level level,

        @Schema(description = "Тип компании, под который стилизуются вопросы", example = "Продуктовая компания")
        @NotNull
        CompanyType companyType,

        @Schema(description = "Количество вопросов в сессии", example = "10")
        @NotNull
        @Min(MIN_QUESTIONS) @Max(MAX_QUESTIONS)
        Integer totalQuestions
) {
    public static final int MIN_QUESTIONS = 10;
    public static final int MAX_QUESTIONS = 20;
}