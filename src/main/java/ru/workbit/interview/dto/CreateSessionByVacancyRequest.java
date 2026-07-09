package ru.workbit.interview.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSessionByVacancyRequest(
        @Schema(description = "Ссылка на вакансию hh.ru; взаимоисключима с vacancyText",
                example = "https://hh.ru/vacancy/123456")
        String vacancyUrl,

        @Schema(description = "Текст вакансии для генерации вопросов; взаимоисключим с vacancyUrl",
                example = "Ищем Java-разработчика уровня Middle с опытом Spring Boot и PostgreSQL от 3 лет")
        @Size(min = 50, max = 20000)
        String vacancyText,

        @Schema(description = "Количество вопросов в сессии", example = "10")
        @NotNull
        @Min(CreateSessionRequest.MIN_QUESTIONS) @Max(CreateSessionRequest.MAX_QUESTIONS)
        Integer totalQuestions
) {
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Provide exactly one of vacancyUrl or vacancyText")
    public boolean isExactlyOneInput() {
        boolean hasUrl = vacancyUrl != null && !vacancyUrl.isBlank();
        boolean hasText = vacancyText != null && !vacancyText.isBlank();
        return hasUrl ^ hasText;
    }
}
