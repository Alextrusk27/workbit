package ru.workbit.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateInterviewSessionRequest(
        @Schema(description = "Ссылка на вакансию hh.ru, по которой проводится интервью", example = "https://hh.ru/vacancy/123456")
        @NotBlank
        String vacancyUrl
) {
}
