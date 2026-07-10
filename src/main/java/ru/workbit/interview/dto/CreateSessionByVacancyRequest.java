package ru.workbit.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSessionByVacancyRequest(
        @Schema(description = "Ссылка на вакансию hh.ru", example = "https://hh.ru/vacancy/123456")
        @NotBlank
        String vacancyUrl,

        @Schema(description = "Количество вопросов в сессии", example = "10")
        @NotNull
        @Min(CreateSessionRequest.MIN_QUESTIONS) @Max(CreateSessionRequest.MAX_QUESTIONS)
        Integer totalQuestions
) {
}
